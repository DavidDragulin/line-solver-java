package jline.solvers.ssa;

import jline.lang.*;
import jline.solvers.ssa.state.StateMatrix;
import jline.solvers.ssa.strategies.TauLeapingStateStrategy;
//import jline.util.JLineAPI;

import java.util.*;

public class SolverSSA {

    protected SSAOptions ssaOptions;
    protected Network network;
    protected SSAStruct simStruct;
    protected SSAData simCache;
    protected Random random;

    public SolverSSA() {
        this.network = null;
        this.simStruct = null;
        this.ssaOptions = new SSAOptions();
        this.random = new Random();
    }

    public SSAOptions setOptions() {
        return this.ssaOptions;
    }

    public void compile(Network network) {
        this.network = network;
        this.simCache = new SSAData(this.network);
    }

    public void compile(SSAStruct networkStruct) {
        this.simStruct = networkStruct;
        this.simCache = new SSAData(this.simStruct);
        throw new RuntimeException("Network structs not supported");
    }

    public Timeline solve() {
        if (this.simCache == null) {
            if (this.simStruct == null) {
                this.compile(this.simStruct);
            } else if (this.network == null) {
                this.compile(this.network);
            } else {
                throw new RuntimeException("Network data not provided!");
            }
        }

        this.random = new Random(this.ssaOptions.seed);
        int samplesCollected = 1;
        int maxSamples = ssaOptions.samples;
        double curTime = ssaOptions.timeInterval.getLeft();
        double maxTime = ssaOptions.timeInterval.getRight();

        // Add ClosedClass instances to the reference station
        StateMatrix stateMatrix = new StateMatrix(this.simCache.simStruct, this.random);
        for (JobClass jobClass : this.network.getClasses()) {
            if (jobClass instanceof ClosedClass) {
                int classIdx = this.network.getJobClassIndex(jobClass);
                ClosedClass cClass = (ClosedClass) jobClass;
                int stationIdx = this.network.getStatefulNodeIndex(cClass.getRefstat());
                stateMatrix.setState(stationIdx, classIdx, (int)cClass.getPopulation());
                for (int i = 0; i < cClass.getPopulation(); i++) {
                    stateMatrix.addToBuffer(stationIdx, classIdx);
                }
            }
        }

        Timeline timeline = new Timeline(this.simCache.simStruct);

        if (ssaOptions.disableResTime) {
            timeline.disableResidenceTime();
        }

        if (ssaOptions.disableTransientState) {
            timeline.disableTransientState();
        }

        if (ssaOptions.useMSER5) {
            timeline.useMSER5();
        } else if (ssaOptions.useR5) {
            timeline.useR5(ssaOptions.r5value);
        }

        if (!ssaOptions.recordMetricTimeline) {
            timeline.setMetricRecord(false);
        }

        if (ssaOptions.useTauLeap) {
            this.simCache.eventStack.configureTauLeap(ssaOptions.tauLeapingType);
            if ((ssaOptions.tauLeapingType.stateStrategy == TauLeapingStateStrategy.TimeWarp) ||
                    (ssaOptions.tauLeapingType.stateStrategy == TauLeapingStateStrategy.TauTimeWarp)) {
                timeline.cacheRecordings();
            }
        }

        double sysTime = 0;
        double startTime = System.currentTimeMillis();

        boolean beforeSState = false;

        // collect samples and update states
        while ((samplesCollected < maxSamples) && (curTime < maxTime) && (sysTime < this.ssaOptions.timeout)) {
            beforeSState = curTime < this.ssaOptions.steadyStateTime;

            if (ssaOptions.useTauLeap) {
                curTime = this.simCache.eventStack.tauLeapUpdate(stateMatrix, timeline, curTime, random);
            } else {
                curTime = this.simCache.eventStack.updateState(stateMatrix, timeline, curTime, random);
            }

            if (beforeSState && (curTime > this.ssaOptions.steadyStateTime)) {
                timeline.resetHistory();
            }

            samplesCollected++;
            sysTime = (System.currentTimeMillis() - startTime)/1000.0;

        }

        System.out.format("Solver finished. %d samples in %f time\n", samplesCollected, curTime);

        timeline.taper(curTime);
        timeline.printSummary(this.network);

        return timeline;
    }
}
