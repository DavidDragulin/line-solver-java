package TauSSA;
import Line.Node;

public interface NodeEvent {
    public Node getNode();
    public int getNodeStatefulIdx();
    public int getClassIdx();
    public boolean isStateful();
}
