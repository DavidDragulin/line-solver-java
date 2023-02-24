package jline.solvers.ctmc;

import jline.api.CTMC;
import jline.lang.*;
import jline.solvers.ssa.SSAData;
import jline.solvers.ssa.SSAOptions;
import jline.solvers.ssa.SSAStruct;
import jline.solvers.ssa.Timeline;
import jline.solvers.ssa.events.Event;
import jline.solvers.ssa.events.OutputEvent;
import jline.solvers.ssa.state.StateMatrix;
import jline.solvers.ssa.strategies.TauLeapingStateStrategy;

import jline.util.Pair;
import org.javatuples.Quartet;

import java.util.*;

public class SolverCTMC {

    protected SSAOptions simOptions;
    protected Network network;
    protected SSAStruct simStruct;
    protected SSAData simCache;
    protected Random random;

    public SolverCTMC() {
        this.network = null;
        this.simStruct = null;
        this.simOptions = new SSAOptions();
        this.random = new Random();
    }

    public SSAOptions setOptions() {
        return this.simOptions;
    }

    public void compile(Network network) {
        this.network = network;
        this.simCache = new SSAData(this.network);
    }

    public void compile(SSAStruct simStruct) {
        this.simStruct = simStruct;
        this.simCache = new SSAData(this.simStruct);
        throw new RuntimeException("SSA structs not supported");
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

        this.random = new Random(this.simOptions.seed);
        int samplesCollected = 1;
        int maxSamples = simOptions.samples;
        double curTime = simOptions.timeInterval.getLeft();
        double maxTime = simOptions.timeInterval.getRight();

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

        if (simOptions.disableResTime) {
            timeline.disableResidenceTime();
        }

        if (simOptions.disableTransientState) {
            timeline.disableTransientState();
        }

        if (simOptions.useMSER5) {
            timeline.useMSER5();
        } else if (simOptions.useR5) {
            timeline.useR5(simOptions.r5value);
        }

        if (!simOptions.recordMetricTimeline) {
            timeline.setMetricRecord(false);
        }

        if (simOptions.useTauLeap) {
            this.simCache.eventStack.configureTauLeap(simOptions.tauLeapingType);
            if ((simOptions.tauLeapingType.getStateStrategy() == TauLeapingStateStrategy.TimeWarp) ||
                    (simOptions.tauLeapingType.getStateStrategy() == TauLeapingStateStrategy.TauTimeWarp)) {
                timeline.cacheRecordings();
            }
        }

        double sysTime = 0;
        double startTime = System.currentTimeMillis();

        boolean beforeSState = false;

        // collect samples and update states
        while ((samplesCollected < maxSamples) && (curTime < maxTime) && (sysTime < this.simOptions.timeout)) {
            beforeSState = curTime < this.simOptions.steadyStateTime;

            if (simOptions.useTauLeap) {
                curTime = this.simCache.eventStack.tauLeapUpdate(stateMatrix, timeline, curTime, random);
            } else {
                curTime = this.simCache.eventStack.updateState(stateMatrix, timeline, curTime, random);
            }

            if (beforeSState && (curTime > this.simOptions.steadyStateTime)) {
                timeline.resetHistory();
            }

            samplesCollected++;
            sysTime = (System.currentTimeMillis() - startTime)/1000.0;

        }

        //System.out.format("Solver finished. %d samples in %f time\n", samplesCollected, curTime);

        timeline.taper(curTime);
        //timeline.printSummary(this.network);

        return timeline;
    }

    public ArrayList<StateMatrix> getStateSpace(){
        if (this.simCache == null) {
            if (this.simStruct == null) {
                this.compile(this.simStruct);
            } else if (this.network == null) {
                this.compile(this.network);
            } else {
                throw new RuntimeException("Network data not provided!");
            }
        }

        this.random = new Random(this.simOptions.seed);
        double curTime = simOptions.timeInterval.getLeft();
        double maxTime = simOptions.timeInterval.getRight();

        // Add ClosedClass instances to the reference station
        StateMatrix stateMatrix = new StateMatrix(this.simCache.simStruct,this.random);
        ArrayList<StateMatrix> stateSpace = new ArrayList<>();
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

        if (simOptions.disableResTime) {
            timeline.disableResidenceTime();
        }

        if (simOptions.useMSER5) {
            timeline.useMSER5();
        } else if (simOptions.useR5) {
            timeline.useR5(simOptions.r5value);
        }

        if (!simOptions.recordMetricTimeline) {
            timeline.setMetricRecord(false);
        }

        if (simOptions.useTauLeap) {
            this.simCache.eventStack.configureTauLeap(simOptions.tauLeapingType);
            if ((simOptions.tauLeapingType.getStateStrategy() == TauLeapingStateStrategy.TimeWarp) ||
                    (simOptions.tauLeapingType.getStateStrategy() == TauLeapingStateStrategy.TauTimeWarp)) {
                timeline.cacheRecordings();
            }
        }
//        JLineMatrix jLineMatrix = new JLineMatrix(stateMatrix.state.length, stateMatrix.state[0].length);
        stateSpace.add(stateMatrix);
        stateMatrix.printStateVector();
//        jLineMatrix.array2DtoJLineMatrix(stateMatrix.state);
//        jLineMatrix.print();

        double sysTime = 0;
        double startTime = System.currentTimeMillis();

        boolean beforeSState = false;

        Queue<StateMatrix> queue = new LinkedList<>();
        queue.add(stateMatrix);

        while (!queue.isEmpty() && (curTime < maxTime) && (sysTime < this.simOptions.timeout)) {
            beforeSState = curTime < this.simOptions.steadyStateTime;

            if (simOptions.useTauLeap) {

                curTime = this.simCache.eventStack.tauLeapUpdate(stateMatrix, timeline, curTime, random);
            } else {
                curTime = this.simCache.eventStack.updateStateSpace(timeline, curTime, random,stateSpace,queue);

            }

            if (beforeSState && (curTime > this.simOptions.steadyStateTime)) {
                timeline.resetHistory();
            }

            sysTime = (System.currentTimeMillis() - startTime)/1000.0;

        }
        //System.out.format("Solver finished. %d samples in %f time\n", samplesCollected, curTime);

        timeline.taper(curTime);
        //timeline.printSummary(this.network);

        return stateSpace;
    }

    public ArrayList<Quartet<Event, Pair<OutputEvent,Double>,StateMatrix,StateMatrix>> getAllEvents(){
        if (this.simCache == null) {
            if (this.simStruct == null) {
                this.compile(this.simStruct);
            } else if (this.network == null) {
                this.compile(this.network);
            } else {
                throw new RuntimeException("Network data not provided!");
            }
        }

        this.random = new Random(this.simOptions.seed);
        double curTime = simOptions.timeInterval.getLeft();
        double maxTime = simOptions.timeInterval.getRight();

        // Add ClosedClass instances to the reference station
        StateMatrix stateMatrix = new StateMatrix(this.simCache.simStruct,this.random);
        ArrayList<Quartet<Event, Pair<OutputEvent,Double>,StateMatrix,StateMatrix>>  eventSpace = new ArrayList<>();
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

        if (simOptions.disableResTime) {
            timeline.disableResidenceTime();
        }

        if (simOptions.useMSER5) {
            timeline.useMSER5();
        } else if (simOptions.useR5) {
            timeline.useR5(simOptions.r5value);
        }

        if (!simOptions.recordMetricTimeline) {
            timeline.setMetricRecord(false);
        }

        if (simOptions.useTauLeap) {
            this.simCache.eventStack.configureTauLeap(simOptions.tauLeapingType);
            if ((simOptions.tauLeapingType.getStateStrategy() == TauLeapingStateStrategy.TimeWarp) ||
                    (simOptions.tauLeapingType.getStateStrategy() == TauLeapingStateStrategy.TauTimeWarp)) {
                timeline.cacheRecordings();
            }
        }
        JLineMatrix jLineMatrix = new JLineMatrix(stateMatrix.state.length, stateMatrix.state[0].length);
        eventSpace.add(Quartet.with(null,null,stateMatrix,null));
//        jLineMatrix.array2DtoJLineMatrix(stateMatrix.state);
//        jLineMatrix.print();

        double sysTime = 0;
        double startTime = System.currentTimeMillis();

        boolean beforeSState = false;

        Queue<StateMatrix> queue = new LinkedList<>();
        queue.add(stateMatrix);

        while (!queue.isEmpty() && (curTime < maxTime) && (sysTime < this.simOptions.timeout)) {
            beforeSState = curTime < this.simOptions.steadyStateTime;

            if (simOptions.useTauLeap) {
                curTime = this.simCache.eventStack.tauLeapUpdate(stateMatrix, timeline, curTime, random);
            } else {
                curTime = this.simCache.eventStack.updateEventSpace(timeline, curTime, random,eventSpace,queue);

            }

            if (beforeSState && (curTime > this.simOptions.steadyStateTime)) {
                timeline.resetHistory();
            }

            sysTime = (System.currentTimeMillis() - startTime)/1000.0;

        }



        //System.out.format("Solver finished. %d samples in %f time\n", samplesCollected, curTime);

        timeline.taper(curTime);
        //timeline.printSummary(this.network);

        eventSpace.remove(0);

        ArrayList<StateMatrix> stateMatrices = getStateSpace();
        int size = stateMatrices.size();

        Map<StateMatrix,Integer> indexMap = new HashMap<>();
        for(int i =0;i< stateMatrices.size();i++){
            indexMap.put(stateMatrices.get(i),i);
        }

        double[][] rateMatrix = new double[size][size];
        for(int i =0;i<size;i++){
            for(int j=0;j<size;j++){
                rateMatrix[i][j]=0.0;
            }
        }

//        for(int i=0;i<eventSpace.size();i++){
//            for(int j=i+1;j< eventSpace.size();j++){
//                if(StateMatrix.multipleEventSameState(eventSpace.get(i),eventSpace.get(j))){
//                    System.out.println(true);
//                }
//            }
//        }

        for(Quartet<Event, Pair<OutputEvent, Double>, StateMatrix, StateMatrix> quartet : eventSpace){
            double rate = quartet.getValue0().getRate(quartet.getValue2())*quartet.getValue1().getRight();
            rateMatrix[indexMap.get(quartet.getValue2())][indexMap.get(quartet.getValue3())] = rate;
        }

        for(int i = 0; i<size;i++){
            double sum = 0;
            for(int j=0;j<size;j++){
                if(j!=i){
                    sum+=rateMatrix[i][j];
                }
            }
            rateMatrix[i][i]= -sum;
        }
        System.out.println("\nRate Matrix");

        for(int i = 0; i<size;i++){
            for(int j=0;j<size;j++){
                System.out.print(rateMatrix[i][j]+"  ");
            }
            System.out.println();
        }

        JLineMatrix rateLineMatrix = new JLineMatrix(size,size);
        rateLineMatrix.array2DtoJLineMatrix(rateMatrix);

        JLineMatrix piVector = CTMC.ctmc_solve(rateLineMatrix);
        System.out.println("\nProbability Vector");
        piVector.print();

        double[][] U = piVector.toArray2D();
        double[] utilisation = new double[stateMatrix.state.length];
        double[] qLength = new double[stateMatrix.state.length];
        for (int i=0;i<size;i++){
            if(U[0][i]>0){
                int[][] state = stateMatrices.get(i).state;
                for(int j = 0;j<state.length;j++){
                    int tempSum =0;
                    for(int k=0;k<state[j].length;k++){
                        tempSum+=state[j][k];
                    }
                    if(tempSum>0){
                        utilisation[j]+=U[0][i];
                        qLength[j]+=U[0][i]*tempSum;
                    }
                }
            }
        }
        for(int i=0;i<utilisation.length;i++){
            System.out.println(utilisation[i]+" "+qLength[i]);
        }

        return eventSpace;
    }

}
