package jline.lang;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

public class OutputStrategy implements Serializable {
    private JobClass jobClass;
    private RoutingStrategy routingStrategy;
    private double probability;
    private Node destination;


    public static List<RoutingStrategy> legalStrategies = Arrays.asList(new RoutingStrategy[]{RoutingStrategy.DISABLED, RoutingStrategy.PROB, RoutingStrategy.RAND});

    public OutputStrategy (JobClass jobClass, RoutingStrategy routingStrategy, Node destination, double probability) {
        this.jobClass = jobClass;
        this.routingStrategy = routingStrategy;
        this.destination = destination;
        this.probability = probability;

        if (!legalStrategies.contains(routingStrategy)) {
            throw new RuntimeException("Unsupported Routing Strategy!");
        }
    }

    public OutputStrategy (JobClass jobClass, RoutingStrategy routingStrategy) {
        this(jobClass, routingStrategy, null, 1);
    }

    public JobClass getJobClass() {
        return this.jobClass;
    }

    public RoutingStrategy getRoutingStrategy() {
        return this.routingStrategy;
    }

    public void setRoutingStrategy(RoutingStrategy routingStrategy) {
        this.routingStrategy = routingStrategy;
    }

    public double getProbability() {
        return this.probability;
    }

    public void setProbability(double probability) {
        this.probability = probability;
    }

    public Node getDestination() {
        return this.destination;
    }

}
