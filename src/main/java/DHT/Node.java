package DHT;

public class Node {
    private int id;
    private String nodeHash;

    public Node(int id, String nodeHash) {
        this.id = id;
        this.nodeHash = nodeHash;
    }

    public int getId() {
        return id;
    }

    public int getNum() {
        return Integer.parseInt(nodeHash, 16);
    }
}
