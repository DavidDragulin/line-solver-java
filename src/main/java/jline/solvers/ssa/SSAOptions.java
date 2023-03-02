package jline.solvers.ssa;

import jline.lang.JobClass;
import jline.lang.nodes.Node;
import jline.util.Interval;

import java.util.HashMap;
import java.util.Map;

public class SSAOptions {
    /*
        Internal class for setting the configuration of SolverSSA.
     */

    // simulation parameters
    public int samples;
    public int seed;
    public Interval timeInterval;
    public Map<Node, Map<JobClass, Double>> cutoffMatrix;
    public Map<Node, Double> nodeCutoffMatrix;
    public Double cutoff;
    public double timeout;

    // tau leaping configuration
    public TauLeapingType tauLeapingType;
    public boolean useTauLeap;

    // metrics configurations
    public boolean useMSER5;
    public boolean useR5;
    public int r5value;
    public boolean recordMetricTimeline;
    public double steadyStateTime;
    public boolean disableResTime;
    public boolean disableTransientState;

    public SSAOptions() {
        this.disableResTime = false;
        this.samples = 10000;
        this.seed = 1;
        this.timeout = Double.POSITIVE_INFINITY;
        this.timeInterval = new Interval(0, Double.POSITIVE_INFINITY);
        cutoff = Double.POSITIVE_INFINITY;
//        cutoff = 2.0;
        cutoffMatrix = new HashMap<Node, Map<JobClass, Double>>();

        this.tauLeapingType = null;
        this.useTauLeap = false;
        this.useMSER5 = false;
        this.useR5 = false;
        this.recordMetricTimeline = true;
        this.r5value = 19;
        this.steadyStateTime = -1;
        this.disableTransientState = false;
    }

    public SSAOptions samples(int samples) {
        this.samples = samples;
        return this;
    }

    public SSAOptions seed(int seed) {
        this.seed = seed;
        return this;
    }

    public SSAOptions MSER5() {
        this.useMSER5 = true;
        this.useR5 = false;
        return this;
    }

    public SSAOptions R5(int k) {
        this.useR5 = true;
        this.useMSER5 = false;
        this.r5value = k;
        return this;
    }

    public SSAOptions recordMetricTimeline(boolean recordMetricTimeline) {
        this.recordMetricTimeline = recordMetricTimeline;

        return this;
    }

    public SSAOptions setTimeInterval(Interval timeInterval) {
        this.timeInterval = timeInterval;
        return this;
    }

    public SSAOptions setTimeout(double timeout) {
        this.timeout = timeout;
        return this;
    }

    public SSAOptions setStartTime(double startTime) {
        this.timeInterval.setLeft(startTime);
        return this;
    }

    public SSAOptions setEndTime(double endTime) {
        this.timeInterval.setRight(endTime);
        return this;
    }

    public SSAOptions steadyStateTime(double sTime) {
        this.steadyStateTime = sTime;
        return this;
    }

    public SSAOptions disableTransientState() {
        this.disableTransientState = true;
        return this;
    }

    public void setCutoff(Node node, JobClass jobClass, Double cutoff) {
        if (!this.cutoffMatrix.containsKey(node)) {
            this.cutoffMatrix.put(node, new HashMap<JobClass, Double>());
        }
        this.cutoffMatrix.get(node).put(jobClass, cutoff);
    }

    public void setCutoff(Double cutoff) {
        this.cutoff = cutoff;
        this.cutoffMatrix = new HashMap<Node, Map<JobClass, Double>>();
    }

    public void setCutoff(Node node, Double cutoff) {
        this.nodeCutoffMatrix.put(node, cutoff);
    }

    public int getCutoff(Node node, JobClass jobClass) {
        if (this.cutoffMatrix.containsKey(node)) {
            if (this.cutoffMatrix.get(node).containsKey(jobClass)) {
                return this.cutoffMatrix.get(node).get(jobClass).intValue();
            }
        }

        return cutoff.intValue();
    }

    public void configureTauLeap(TauLeapingType tauLeapingType) {
        this.tauLeapingType = tauLeapingType;
        this.useTauLeap = true;
    }

    public void disableTauLeap() {
        this.useTauLeap = false;
    }
}
