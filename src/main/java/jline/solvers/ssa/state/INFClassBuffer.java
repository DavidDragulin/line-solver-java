package jline.solvers.ssa.state;

import java.util.*;
import java.util.stream.Collectors;

public class INFClassBuffer extends StateCell {
    protected int nServers;
    protected int[] inQueue;
    protected int totalInQueue;

    protected PhaseList phaseList;
    protected Random random;

    public INFClassBuffer(Random random, int nClasses, PhaseList phaseList) {
        this.inQueue = new int[nClasses];

        for (int i = 0; i < nClasses; i++) {
            this.inQueue[i] = 0;
        }
        this.totalInQueue = 0;

        this.phaseList = phaseList;

        this.random = random;
    }

    public INFClassBuffer(int nClasses) {
        this(new Random(), nClasses, null);
    }


    public void addToBuffer(int classIdx) {
        this.inQueue[classIdx]++;
        this.totalInQueue++;

        this.phaseList.addToService(classIdx);
    }

    public void addNToBuffer(int classIdx, int n) {
        this.inQueue[classIdx] += n;
        this.totalInQueue += n;

    }

    public int getInService(int classIdx) {
        return this.inQueue[classIdx];
    }

    public boolean isEmpty() {
        return this.totalInQueue == 0;
    }

    public void removeFirstOfClass(int classIdx) {
        this.inQueue[classIdx] -= 1;
    }

    public void removeNClass(int n, int classIdx) {
        this.inQueue[classIdx] -= n;
    }

    public StateCell createCopy() {
        INFClassBuffer copyBuffer = new INFClassBuffer(this.random, this.inQueue.length, this.phaseList.createCopy());
        copyBuffer.totalInQueue = this.totalInQueue;
        copyBuffer.inQueue = Arrays.copyOf(this.inQueue,this.inQueue.length);
        return copyBuffer;
    }

    public int getInQueue(int classIdx) {
        return this.inQueue[classIdx];
    }

    public boolean incrementPhase(int classIdx) {
        return this.phaseList.incrementPhase(classIdx, this.inQueue[classIdx]);
    }

    public boolean updatePhase(int classIdx, int startingPhase, int newPhase) {
        return this.phaseList.updatePhase(classIdx, startingPhase, newPhase);
    }

    public boolean updateGlobalPhase(int classIdx, int newPhase) {
        this.phaseList.updateGlobalPhase(classIdx, newPhase);
        return true;
    }

    public int incrementPhaseN(int n, int classIdx) {
        return this.phaseList.incrementPhaseN(n, classIdx, this.inQueue[classIdx]);
    }


    public int getGlobalPhase(int classIdx) {
        return this.phaseList.getGlobalPhase(classIdx);
    }

    public PhaseList getPhaseList() {
        return this.phaseList;
    }


    public List<Integer> stateVector() {
        return Arrays.stream(this.phaseList.getVector()).collect(Collectors.toList());
    }
}
