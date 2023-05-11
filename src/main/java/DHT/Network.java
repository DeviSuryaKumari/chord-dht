package DHT;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class Network {
    private int numOfNodes = 0;
    private int numOfSwitches;
    private boolean isReadFromFile;
    private String fileName;
    private Map<Integer, Set<Integer>> graph;
    private Map<Integer, ChordNode> nodes;
    private Map<Integer, Integer> switchToNode;


    public Network(int numOfSwitches, boolean isReadFromFile, String fileName) {
        this.numOfSwitches = numOfSwitches;
        this.isReadFromFile = isReadFromFile;
        this.fileName = fileName;
        this.nodes = new HashMap<>();
        this.switchToNode = new HashMap<>();

        List<List<Integer>> links = new ArrayList<>();
        if (isReadFromFile) {
            BufferedReader objReader = null;
            try {
                String strCurrentLine;
                objReader = new BufferedReader(new FileReader(fileName));

                while ((strCurrentLine = objReader.readLine()) != null) {
                    links.add(Arrays.stream(strCurrentLine.strip().split(",")).map(Integer::parseInt).collect(Collectors.toList()));
                }

            } catch (IOException e) {

                e.printStackTrace();

            } finally {

                try {
                    if (objReader != null)
                        objReader.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        } else {
            int maxLinks = (int) (Math.random() * (16 * numOfSwitches - 8 * numOfSwitches) + 8 * numOfSwitches);
            for (int i = 0; i < numOfSwitches; i++) {
                links.add(List.of(i, (i + 1) % numOfSwitches));
            }

            for (int i = 0; i < maxLinks - numOfSwitches; i++) {
                int src = (int) (Math.random() * numOfSwitches);
                int dest = src;
                while (src == dest) {
                    dest = (int) (Math.random() * numOfSwitches);
                }
                links.add(List.of(src, dest));
            }

            FileWriter fileWriter;
            try
            {
                fileWriter = new FileWriter(fileName);

                // Initializing BufferedWriter
                BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
                for (var link : links) {
                    bufferedWriter.write(link.get(0) + "," + link.get(1) + "\n");
                }
                bufferedWriter.close();
                System.out.println("Written node links to links.dat successfully\n");
            }
            catch (IOException except)
            {
                except.printStackTrace();
            }

        }

        graph = buildGraph(links);

    }

    public Network(int numOfSwitches, boolean isReadFromFile) {
        this(numOfSwitches, isReadFromFile, "links.dat");
    }

    public Network(int numOfSwitches) {
        this(numOfSwitches, false, "links.dat");
    }

    public boolean addNode(ChordNode n) {
        if (!nodes.containsKey(n.getNum())) {
            nodes.put(n.getNum(), n);
            int swh = (int) (Math.random() * numOfSwitches);
            while (switchToNode.containsValue(swh)) {
                swh = (int) (Math.random() * numOfSwitches);
            }
            switchToNode.put(n.getNum(), swh);
            return true;
        }
        return false;
    }

    public boolean removeNode(int nodeId) {
        if (!nodes.containsKey(nodeId)) {
            return false;
        }
        nodes.remove(nodeId);
        switchToNode.remove(nodeId);
        return true;
    }

    public ChordNode getNode(int nodeId) {
        return nodes.get(nodeId);
    }

    public boolean isAlive(int nodeId) {
        return nodes.containsKey(nodeId);
    }


    public int hop(int nodeId, int maxDepth) {
        var swh = switchToNode.get(nodeId);
        Map<Integer, Integer> visited = new HashMap<>();
        var queue = graph.getOrDefault(swh, new HashSet<>());
        queue.forEach(q -> visited.put(q, 1));
        visited.put(swh, 1);
        var foundSwitch = -1;
        var depth = 1;

        while (depth <= maxDepth) {
            var nextQueue = new HashSet<Integer>();
            for (var nextSwitch : queue) {
                if (switchToNode.containsValue(nextSwitch)) {
                    foundSwitch = nextSwitch;
                    break;
                }
                var temp = graph.get(nextSwitch);
                for (var s : temp) {
                    if (!visited.containsKey(s)) {
                        nextQueue.add(s);
                        visited.put(s, 1);
                    }
                }
            }
            if (foundSwitch != -1) {
                break;
            }
            depth++;
            queue = nextQueue;
        }
        if (foundSwitch == -1) {
            return -1;
        }
        int finalFoundSwitch = foundSwitch;
        return switchToNode.entrySet().stream().filter(entry -> entry.getValue() == finalFoundSwitch).findAny().get().getKey();
    }

    private Map<Integer, Set<Integer>> buildGraph(List<List<Integer>> links) {
        Map<Integer, Set<Integer>> graph = new HashMap<>();
        for (var link : links) {
            graph.computeIfAbsent(link.get(0), k -> new HashSet<>()).add(link.get(1));
            graph.computeIfAbsent(link.get(1), k -> new HashSet<>()).add(link.get(0));

        }
        return graph;
    }
}
