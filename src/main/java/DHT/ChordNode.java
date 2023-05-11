package DHT;

import javafx.util.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;


public class ChordNode extends Node {
    private Network networkApi;
    private int m;
    private List<Map<String, Integer>> fingerTable;
    private int predecessor = -1;
    private Map<Integer, Integer> dataStore;

    public ChordNode(int id, String nodeHash, Network network, int m) {
        super(id, nodeHash);
        this.networkApi = network;
        this.m = m;
        this.dataStore = new HashMap<>();
        this.fingerTable = new ArrayList<>();

        for (int i = 0; i < m; i++) {
            int start = ((int) ((getNum() + Math.pow(2, i)))) % ((int)(Math.pow(2, m)));
            Map<String, Integer> map = new HashMap<>();
            map.put("start", start);
            map.put("node", 0);
            fingerTable.add(map);
        }

    }

    public int getSuccessor() {
        return fingerTable.get(0).get("node");
    }

    public int getPredecessor() {
        return predecessor;
    }

    public void setSuccessor(int nodeId) {
        fingerTable.get(0).put("node", nodeId);
    }

    public void setPredecessor(int nodeId) {
        predecessor = nodeId;
    }

    public Pair<Integer, Integer> findClosetPrecedingFinger(int key) {
        for (int i = m; i > 0; i--) {
            if (isCircularBetween(getNum(), fingerTable.get(i - 1).get("node"), key)) {
                return new Pair<>(fingerTable.get(i - 1).get("node"), i - 1);
            }
        }
        return new Pair<>(getNum(), 0);
    }

    public int findPredecessor(int key) {
        if (getNum() == key) {
            return predecessor;
        }

        return (networkApi.getNode(findSuccessor(key).getLeft())).getPredecessor();

    }

    public Triple<Integer, Integer, List<Integer>> findSuccessor(int key) {
        if (getNum() == key) {
//            System.out.println("getNum: " + getNum());
            return Triple.of(getNum(), 0, new ArrayList<>(List.of(getNum())));
        }

        if (isCircularBetween(getNum(), key, getSuccessor()) || key == getSuccessor() || getNum() == getSuccessor()) {
            return Triple.of(getSuccessor(), 1, new ArrayList<>(List.of(getSuccessor())));
        } else {
            var pair = findClosetPrecedingFinger(key);
            int originalId = pair.getValue();
            while (!networkApi.isAlive(pair.getKey())) {
                pair = findClosetPrecedingFinger(pair.getKey() - 1);
            }

            for (int i = originalId; i <= pair.getValue(); i++) {
                fingerTable.get(i).put("node", pair.getKey());
            }
            var nDash = networkApi.getNode(pair.getKey());
            List<Integer> leftPath = new ArrayList<>();
            leftPath.add(pair.getKey());
            var succ = nDash.findSuccessor(key);
            leftPath.addAll(succ.getRight());
            return Triple.of(succ.getLeft(), succ.getMiddle() + 1, leftPath);
        }

    }

    public Map<Integer, Integer> fetchKeys(int start, int end) {
        Map<Integer, Integer> res = new HashMap<>();
        Iterator<Map.Entry<Integer,Integer>> iter = dataStore.entrySet().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            if (isCircularBetween(start, entry.getKey(), end) || entry.getKey() == end) {
                res.put(entry.getKey(), entry.getValue());
                iter.remove();
            }
        }
        return res;
    }

    private void initFingerTable(int nodeId) {
        var nDash =  networkApi.getNode(nodeId);
        fingerTable.get(0).put("node", nDash.findSuccessor(fingerTable.get(0).get("start")).getLeft());

        var successor = networkApi.getNode(getSuccessor());
        this.predecessor = successor.getPredecessor();
        var predecessor = networkApi.getNode(getPredecessor());
        predecessor.setSuccessor(getNum());
        successor.setPredecessor(getNum());

        fillFingerTable(nodeId);
    }

    public void fillFingerTable(int nodeId) {
        var nDash = networkApi.getNode(nodeId);
        for (int i = 0; i < m - 1; i++) {
            if (isCircularBetween(getNum(), fingerTable.get(i + 1).get("start"), fingerTable.get(i).get("node"))) {
                fingerTable.get(i + 1).put("node", fingerTable.get(i).get("node"));
            } else {
                fingerTable.get(i + 1).put("node", nDash.findSuccessor(fingerTable.get(i + 1).get("start")).getLeft());
            }
        }
    }

    public void updateFingerTable(int x, int i) {
        if (isCircularBetween(fingerTable.get(i).get("start"), x, fingerTable.get(i).get("node")) || fingerTable.get(i).get("start") == x) {
            fingerTable.get(i).put("node", x);
            (networkApi.getNode(predecessor)).updateFingerTable(x, i);
        }
    }

    private void updateOthers() {
        for (int i = 0; i < m; i++) {
            int prevId = getCircularDiff(getNum(), (int) Math.pow(2, i));
            if (networkApi.isAlive(prevId)) {
                networkApi.getNode(prevId).updateFingerTable(getNum(), i);
            }
            networkApi.getNode(findPredecessor(prevId)).updateFingerTable(getNum(), i);
        }
    }

    public void transfer() {
        ChordNode predecessor = networkApi.getNode(getPredecessor());
        var fetchMap = predecessor.fetchKeys(predecessor.getPredecessor(), predecessor.getNum());
        fetchMap.forEach((key, value) -> dataStore.put(key, value));
    }

    public boolean departNetwork() {
        ChordNode successor = networkApi.getNode(getSuccessor());
        successor.transfer();

        ChordNode predecessor = networkApi.getNode(getPredecessor());
        predecessor.setSuccessor(getSuccessor());
        successor.setPredecessor(getPredecessor());

        predecessor.fillFingerTable(successor.getNum());
        successor.fillFingerTable(predecessor.getNum());

        return networkApi.removeNode(getNum());
    }

    public void join() {
        var foundNode = -1;
        for (int depth = 0; depth < 500; depth++) {
            foundNode = networkApi.hop(getNum(), depth + 1);
            if (foundNode != -1) break;
        }

        if (foundNode != -1) {
            initFingerTable(foundNode);
            updateOthers();
            networkApi.getNode(getSuccessor()).fetchKeys(predecessor, getNum()).forEach((k, v) -> dataStore.put(k, v));
        } else {
            for (int i = 0; i < m; i++) {
                fingerTable.get(i).put("node", getNum());
            }
            predecessor = getNum();
        }
    }

    public Triple<Integer, Integer, List<Integer>> search(int key) {
        var storeKey = hashKey(key);
        var successor = findSuccessor(storeKey);
        var node = networkApi.getNode(successor.getLeft());
        if (node.dataStore.containsKey(storeKey)) {
            return Triple.of(successor.getMiddle(), node.dataStore.get(storeKey), successor.getRight());
        }
        System.out.println("Key: " + key + ", Storekey: " + storeKey);
        return Triple.of(successor.getMiddle(), -1, new ArrayList<>());
    }

    public int storeKey(int k, int v) {
        var storedKey = hashKey(k);
        var successor = findSuccessor(storedKey);
        var node = networkApi.getNode(successor.getLeft());
        if (node.dataStore.containsKey(storedKey)) {
            return -1;
        }
        node.dataStore.put(storedKey, v);
        return 0;
    }


    private boolean isCircularBetween(int start, int between, int end) {
        if (start < end) {
            return start < between && between < end;
        }
        return start < between || between < end;
    }

    private int getCircularDiff(int x, int y) {
        if (x > y) {
            return x - y;
        }
        return (int) Math.pow(2, m) + x - y;
    }

    public int hashKey(int n) {
        String name = Integer.toString(n);
        try {
            MessageDigest m = MessageDigest.getInstance("SHA-1");
            m.update(name.getBytes(StandardCharsets.UTF_8));
            String nodeHash = bytesToHex(m.digest()).substring(0, this.m / 4);
            return Integer.parseInt(nodeHash, 16);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
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

    @Override
    public String toString() {
        return "id: " + getId() + ", nodeHash: " + getNum();
    }
}
