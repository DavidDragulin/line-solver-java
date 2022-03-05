package jline.lang.sections;

import java.io.Serializable;
import java.util.*;

import jline.solvers.ssa.OutputEvent;
import jline.util.Cdf;
import jline.lang.*;
import jline.lang.constant.RoutingStrategy;
import jline.lang.constant.SchedStrategyType;
import jline.lang.distributions.*;
import jline.lang.nodes.*;
import jline.lang.sections.*;


public class OutputSection extends Section implements Serializable {
    protected SchedStrategyType schedPolicy;
    protected List<OutputStrategy> outputStrategies;
    protected Map<OutputStrategy, OutputEvent> outputEvents;
    protected boolean isClassSwitch;

    protected void probabilityUpdate() {
        Map<JobClass, Double> totalNonRandProbability = new HashMap<JobClass, Double>();
        Map<JobClass, Integer> totalProbServers = new HashMap<JobClass, Integer>();
        for (OutputStrategy outputStrategy : this.outputStrategies) {
            JobClass jobClassIter = outputStrategy.getJobClass();
            if (outputStrategy.getRoutingStrategy() == RoutingStrategy.PROB) {
                double cProb = 0;
                if (totalNonRandProbability.containsKey(jobClassIter)) {
                    cProb = totalNonRandProbability.get(jobClassIter);
                }
                totalNonRandProbability.put(jobClassIter, cProb+outputStrategy.getProbability());
            } else if (outputStrategy.getRoutingStrategy() == RoutingStrategy.RAND) {
                int serverCt = 0;
                if (totalProbServers.containsKey(jobClassIter)) {
                    serverCt = totalProbServers.get(jobClassIter);
                }
                totalProbServers.put(jobClassIter, serverCt+1);
            } else if (outputStrategy.getRoutingStrategy() == RoutingStrategy.DISABLED) {
                outputStrategy.setProbability(0);
            }
        }

        for (OutputStrategy outputStrategy : this.outputStrategies) {
            if (outputStrategy.getRoutingStrategy() == RoutingStrategy.RAND) {
                JobClass jobClassIter = outputStrategy.getJobClass();
                double randProb = 1;
                if (totalNonRandProbability.containsKey(jobClassIter)) {
                    randProb = 1-totalNonRandProbability.get(jobClassIter);
                }
                if (totalProbServers.containsKey(jobClassIter)) {
                    randProb /= totalProbServers.get(jobClassIter);
                }
                outputStrategy.setProbability(randProb);
            }
        }
    }

    public OutputSection(String className) {
        super(className);
        this.isClassSwitch = false;
        outputStrategies = new ArrayList<OutputStrategy>();
        outputEvents = new HashMap<OutputStrategy, OutputEvent>();
    }

    public void setOutputStrategy(JobClass jobClass, RoutingStrategy routingStrategy, Node destination, double probability) {
        for (OutputStrategy outputStrategy : this.outputStrategies) {
            if ((outputStrategy.getJobClass() == jobClass) && (outputStrategy.getDestination() == destination)) {
                outputStrategy.setRoutingStrategy(routingStrategy);
                outputStrategy.setProbability(probability);
                this.probabilityUpdate();
                return;
            }
        }

        OutputStrategy outputStrategy = new OutputStrategy(jobClass, routingStrategy, destination, probability);
        outputStrategies.add(outputStrategy);
        outputEvents.put(outputStrategy, new OutputEvent(this, destination, jobClass, this.isClassSwitch));
        this.probabilityUpdate();
    }

    public void resetRouting() {
        List<OutputStrategy> newOutputStrategies = new ArrayList<OutputStrategy>();
        for (OutputStrategy outputStrategy : this.outputStrategies) {
            if (outputStrategy.getDestination() == null) {
                newOutputStrategies.add(outputStrategy);
            }
        }
        this.outputStrategies = newOutputStrategies;
    }

    public void printSummary() {
        System.out.println("Outputs:");
        for (OutputStrategy outputStrategies : outputStrategies) {
            if (outputStrategies.getDestination() != null) {
                System.out.format("-%s (%s)\n", outputStrategies.getDestination().getName(),
                        outputStrategies.getDestination().getClass());
            }
        }
    }

    public final List<OutputStrategy> getOutputStrategies() {
        return this.outputStrategies;
    }

    public OutputEvent getOutputEvent(JobClass jobClass, Random random) {
        Cdf<OutputStrategy> outputStrategyCdf = new Cdf<OutputStrategy>(random);

        if (this.outputStrategies.size() == 0) {
            throw new RuntimeException("No output strategies found!");
        }

        for (OutputStrategy outputStrategy : this.outputStrategies) {
            if (outputStrategy.getDestination() == null) {
                continue;
            } else if (outputStrategy.getJobClass() != jobClass) {
                continue;
            }

            outputStrategyCdf.addElement(outputStrategy, outputStrategy.getProbability());
        }

        return this.outputEvents.get(outputStrategyCdf.generate());
    }

    public OutputEvent getOutputEvent(JobClass jobClass) {
        return this.getOutputEvent(jobClass, new Random());
    }
}
