package DHT;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public class Chord {
    private int numOfNodes;
    private List<Integer> nodes;
    private Map<Integer, Integer> dataStore;
    private static final int NUM_OF_POINTS = 1000;
    private static final int NUM_OF_QUERIES = 100000;

    public Chord(int numOfNodes) {
        this.numOfNodes = numOfNodes;
        this.nodes = new ArrayList<>();
        this.dataStore = new HashMap<>();
    }

    public String hashInt(int n) {
        String name = Integer.toString(n);
        try {
            MessageDigest m = MessageDigest.getInstance("SHA-1");
            m.update(name.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(m.digest()).substring(0, 6);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    public static String bytesToHex(byte[] bytes) {
        byte[] hexArr = "0123456789ABCDEF".getBytes(StandardCharsets.US_ASCII);
        byte[] hexChars = new byte[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArr[v >>> 4];
            hexChars[j * 2 + 1] = hexArr[v & 0x0F];
        }
        return new String(hexChars, StandardCharsets.UTF_8);
    }

    public void initNetwork(Network network, int numOfNodes) {
        var numAdded = 0;
        for (int i = 0; i < 2 * numOfNodes; i++) {
            System.out.println("Adding node" + i);
            var nodeHash = hashInt(i);
            System.out.println(nodeHash);

            var pn = new ChordNode(i, nodeHash, network, 24);
            var isAdded = network.addNode(pn);

            if (isAdded) {
                pn.join();
                numAdded++;
                nodes.add(i);
            }

            if (numAdded == numOfNodes) {
                break;
            }
        }
        System.out.println();
    }

    public void LookupQueries(Network network, int numOfQueries) {
        Map<Integer, Integer> hopsHist = new HashMap<>();
        var numEpoch = 0;
        var flag = 0;
        var count = 0;
        for (int i = 0; i < 100; i++) {
            for (var entry : dataStore.entrySet()) {
                flag = 0;
                count++;
                if (count % 10000 == 0) {
                    numEpoch++;
                    System.out.println(numEpoch + " epochs completed");
                }

                var hitNode = Integer.parseInt(hashInt(nodes.get((int)(Math.random() * nodes.size()))), 16);
                var node = network.getNode(hitNode);
                var cur = node.search(entry.getKey());
                System.out.println("Lookup " + entry.getKey() + ": " + cur.getRight());
                var hops = Math.min(cur.getLeft(), 12);
                hopsHist.put(hops, hopsHist.getOrDefault(hops, 0) + 1);
                var chordValue = cur.getMiddle();
                if (chordValue == -1) {
                    flag = 1;
                    System.out.println(entry.getKey() + ": Found " + entry.getValue() + " when not stored");
                } else {
                    if (!chordValue.equals(entry.getValue())) {
                        flag = 1;
                        System.out.println(entry.getKey() + ": Found " + entry.getValue() + " when " + chordValue + " not stored");
                    }
                }

                if (flag == 1) {
                    System.out.println("Cannot find node " + entry.getKey() + " correctly");
                }
                if (count >= numOfQueries) {
                    break;
                }
            }
            if (count >= numOfQueries) {
                break;
            }
        }

        if (flag == 0) {
            System.out.println("\nAll lookup queries ran successfully");
        }

        var avgHops = 0.0d;
        for (var entry : hopsHist.entrySet()) {
            avgHops += (entry.getValue() + 0.0d) / numOfQueries * entry.getKey();
        }
        System.out.println("Average of total hops across all queries: " + avgHops + "\n");

    }

    public void storeKeys(Network network, int numOfKeys) {
        var count = 0;
        for (var key = 0; key < 2 * numOfKeys; key++) {
            var value = (int) (Math.random() * (2 * numOfKeys));
            var randNode = Integer.parseInt(hashInt(nodes.get((int)(nodes.size() * Math.random()))), 16);
            var node = network.getNode(randNode);
            var isStored = node.storeKey(key, value);

            if (isStored == 0) {
                count++;
                dataStore.put(key, value);
                if (count == NUM_OF_POINTS) {
                    break;
                }
            }
        }
    }

    public void deleteNodes(Network network, int numOfDelNodes) {
        var numDeleted = 0;
        while (numDeleted < numOfDelNodes) {
            if (nodes.isEmpty()) {
                break;
            }
            var chosenNode = nodes.get((int) (Math.random() * nodes.size()));
            var delNode = (network.getNode(Integer.parseInt(hashInt(chosenNode), 16)));
            var removed = delNode.departNetwork();
            if (removed) {
                numDeleted++;
                nodes.remove(chosenNode);
            }
        }
    }


    public static void main(String[] args) {
        System.out.println(Arrays.toString(args));
        int numOfNodes = Integer.parseInt(args[0]);
        boolean readFromFile = Boolean.parseBoolean(args[1]);
        Chord chord = new Chord(numOfNodes);
        Network network = new Network(numOfNodes, readFromFile);
        chord.initNetwork(network, numOfNodes);
        chord.storeKeys(network, NUM_OF_POINTS);
        chord.LookupQueries(network, NUM_OF_QUERIES);

        System.out.println("Deleting half of the available chord nodes...\n");
        chord.deleteNodes(network, numOfNodes / 2);

        chord.LookupQueries(network, NUM_OF_QUERIES);

        System.out.println("Total number of nodes: " + numOfNodes);
        System.out.println("Total number of data points (keys): " + NUM_OF_POINTS);
        System.out.println("Total number of data add queries: " + NUM_OF_QUERIES);
        System.out.println("Total number of lookup queries: " + NUM_OF_QUERIES);
        System.out.println("Total number of node add queries: " + numOfNodes);
        System.out.println("Total number of node delete queries: " + numOfNodes / 2);
    }
}
