package jline.solvers.ssa.state;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FCFSClassBuffer extends StateCell {
    /*
            FCFSClassBuffer -
                Handles the state for an individual node with a First-Come-First-Served Scheduling Policy
     */
	public Deque<Integer> deque; // Tracks the class of each element
    protected int nServers;
    protected int[] inQueue;
    protected int totalInQueue;
    protected int nClasses;

    protected PhaseList phaseList;

    public FCFSClassBuffer(int nClasses, int nServers, PhaseList phaseList) {
        this.deque = new ArrayDeque<Integer>();
        this.nServers = nServers;
        this.inQueue = new int[nClasses];
        this.nClasses = nClasses;

        for (int i = 0; i < this.nClasses; i++) {
            this.inQueue[i] = 0;
        }
        this.totalInQueue = 0;

        this.phaseList = phaseList;
    }

    public FCFSClassBuffer(int nClasses, int nServers) {
        this(nClasses, nServers, null);
    }

    public void addToBuffer(int classIdx) {
        if (this.totalInQueue < this.nServers) {
            this.phaseList.addToService(classIdx);
        }
        this.inQueue[classIdx]++;
        this.totalInQueue++;

        this.deque.addLast(classIdx);
    }

    public void addNToBuffer(int classIdx, int n) {
        if (this.totalInQueue < this.nServers) {
            int shortfall = this.nServers - this.totalInQueue;
            this.phaseList.addToServiceN(classIdx,  n);
        }
        this.inQueue[classIdx] += n;
        this.totalInQueue += n;

        for (int i = 0; i < n; i++) {
            this.deque.addLast(classIdx);
        }
    }

    public int getInService(int classIdx) {
        Iterator<Integer> dequeIterator = deque.iterator();
        int nCt = 0;
        int acc = 0;
        while ((dequeIterator.hasNext()) && (nCt < this.nServers)) {
            if (dequeIterator.next() == classIdx) {
                acc++;
            }
            nCt++;
        }
        return acc;
    }

    public boolean isEmpty() {
        return this.totalInQueue == 0;
    }

    public void removeFirstOfClass(int classIdx) {
        Iterator<Integer> dequeIterator = deque.iterator();
        while (dequeIterator.hasNext()) {
            if (dequeIterator.next() == classIdx) {
                dequeIterator.remove();
                this.totalInQueue--;
                this.inQueue[classIdx]--;

                break;
            }
        }

        // replace lost service.. (we assume we removed classIdx from service)
        dequeIterator = deque.iterator();
        for (int i = 0; i < this.nServers-1; i++) {
            if (!dequeIterator.hasNext()) {
                return;
            }
            dequeIterator.next();
        }

        if (!dequeIterator.hasNext()) {
            return;
        }

        this.phaseList.addToService(dequeIterator.next());
    }

    public void removeNClass(int n, int classIdx) {
        Iterator<Integer> dequeIterator = deque.iterator();
        int nRemoved = 0;

        while ((dequeIterator.hasNext()) && (n > 0)) {
            if (dequeIterator.next() == classIdx) {
                dequeIterator.remove();
                n--;
                nRemoved++;
            }
        }

        this.totalInQueue -= nRemoved;
        this.inQueue[classIdx] -= nRemoved;


        int serviceReplacement = Math.min(this.getInService(classIdx),nRemoved);
        dequeIterator = deque.iterator();
        for (int i = 0; i < this.nServers-serviceReplacement; i++) {
            if (!dequeIterator.hasNext()) {
                return;
            }
            dequeIterator.next();
        }

        for (int i = 0; i < serviceReplacement; i++) {
            if (!dequeIterator.hasNext()) {
                return;
            }

            this.phaseList.addToService(dequeIterator.next());
        }
    }

    public StateCell createCopy() {
        FCFSClassBuffer copyBuffer = new FCFSClassBuffer(this.inQueue.length, this.nServers, this.phaseList.createCopy());

        copyBuffer.deque = new ArrayDeque<>(this.deque);
        copyBuffer.inQueue = Arrays.copyOf(this.inQueue, this.nClasses);
        copyBuffer.nClasses = this.nClasses;
        copyBuffer.totalInQueue = this.totalInQueue;

        return copyBuffer;
    }

    public int getInQueue(int classIdx) {
        return this.inQueue[classIdx];
    }

    public boolean incrementPhase(int classIdx) {
        return this.phaseList.incrementPhase(classIdx, this.getInService(classIdx));
    }

    public boolean updatePhase(int classIdx, int startingPhase, int newPhase) {
        return this.phaseList.updatePhase(classIdx, startingPhase, newPhase);
    }

    public boolean updateGlobalPhase(int classIdx, int newPhase) {
        this.phaseList.updateGlobalPhase(classIdx, newPhase);
        return true;
    }

    public int incrementPhaseN(int n, int classIdx) {
        return this.phaseList.incrementPhaseN(n, classIdx, this.getInService(classIdx));
    }

    public int getGlobalPhase(int classIdx) {
        return this.phaseList.getGlobalPhase(classIdx);
    }

    public PhaseList getPhaseList() {
        return this.phaseList;
    }

    public List<Integer> stateVector() {
        return Stream.concat(this.deque.stream(), this.phaseList.getStream()).collect(Collectors.toList());
    }
}
