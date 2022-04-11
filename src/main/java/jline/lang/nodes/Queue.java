package jline.lang.nodes;

import java.io.Serializable;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import jline.lang.*;
import jline.lang.constant.DropStrategy;
import jline.lang.constant.SchedStrategy;
import jline.lang.constant.SchedStrategyType;
import jline.lang.constant.ServiceStrategy;
import jline.lang.distributions.*;
import jline.lang.nodes.*;
import jline.lang.sections.*;

public class Queue extends Station implements HasSchedStrategy, Serializable {
    protected SchedStrategy schedStrategy;
    protected SchedStrategyType schedPolicy;
    protected List<ServiceBinding> serviceProcesses;
    protected HashMap<JobClass, Double> schedStrategyPar;

    public Queue(Network model, String name, SchedStrategy schedStrategy) {
        super(name);

        this.serviceProcesses = new ArrayList<ServiceBinding>();
        this.schedStrategyPar = new HashMap<JobClass, Double>();

        this.setModel(model);
        this.model.addNode(this);
        this.schedStrategy = schedStrategy;
        this.input = new Buffer(model.getClasses());
        this.output = new Dispatcher(model.getClasses());

        this.schedStrategy = schedStrategy;
        this.numberOfServers = 1;

        switch (this.schedStrategy) {
            case FCFS:
            case LCFS:
            /*case SIRO:
            case SEPT:
            case LEPT:
            case SJF:
            case LJF:*/
                this.schedPolicy = SchedStrategyType.NP;
                this.server = new Server(model.getClasses());
                break;
            case INF:
                this.schedPolicy = SchedStrategyType.NP;
                this.server = new InfiniteServer(model.getClasses());
                break;
            /*case HOL:
                this.schedPolicy = SchedStrategyType.NP;
                this.server = new Server(model.getClasses());
                break;*/
            case PS:
            //case DPS:
            //case GPS:
            default:
                this.schedPolicy = SchedStrategyType.PR;
                this.server = new Server(model.getClasses());
                break;
        }
    }

    public Queue(Network model, String name) {
        this(model, name, SchedStrategy.PS);
    }

    public void setNumberOfServers(int numberOfServers) {
        /*if (this.schedStrategy == SchedStrategy.DPS || this.schedStrategy == SchedStrategy.GPS) {
            if (numberOfServers != 1) {
                throw new InvalidParameterException("Invalid number of servers for scheduling strategy");
            }
        }*/
        this.numberOfServers = numberOfServers;
    }

    public void setSchedStrategyPar(JobClass jobClass, double weight) {
        this.schedStrategyPar.put(jobClass, weight);
    }

    public final Distribution getServiceProcess(JobClass jobClass) {
        for (ServiceBinding serviceProcess : this.serviceProcesses) {
            if (serviceProcess.getJobClass() == jobClass) {
                return serviceProcess.getDistribution();
            }        }

        return new Immediate();
    }

    public Distribution getService(JobClass jobClass) {
        return this.server.getServiceDistribution(jobClass);
    }

    public void setService(JobClass jobClass, Distribution distribution, double weight) {
        boolean resetState = false;
        if (this.hasJobClass(jobClass)) {
            resetState = true;
            this.removeServiceProcess(jobClass);
            //clearState();
        }
        this.input.setInputJobProcess(new InputBinding(jobClass, this.schedPolicy, DropStrategy.WaitingQueue));
        if (distribution.isImmediate()) {
            this.serviceProcesses.add(new ServiceBinding(jobClass, ServiceStrategy.LI, new Immediate()));
        } else {
            this.serviceProcesses.add(new ServiceBinding(jobClass, ServiceStrategy.LI, distribution));
        }

        this.classCap.put(jobClass, Double.POSITIVE_INFINITY);
        this.schedStrategyPar.put(jobClass, weight);

        if (resetState) {
            this.model.setInitialized(false);
        }
    }

    private boolean hasJobClass(JobClass jobClass) {
        return this.schedStrategyPar.containsKey(jobClass);
    }

    private void removeServiceProcess(JobClass jobClass) {
        this.classCap.remove(jobClass);
        this.schedStrategyPar.remove(jobClass);
        this.serviceProcesses.removeIf(serviceProcess -> serviceProcess.getJobClass() == jobClass);
    }

    public void setService(JobClass jobClass, Distribution distribution) {
        setService(jobClass, distribution, 1);
    }

    @Override
    public void printSummary() {
        System.out.format("jline.Queue:\n");
        System.out.format("--Name: %s\n", this.getName());
        System.out.format("--Service Processes:\n");
        for (JobClass jobClass : this.model.getClasses()) {
            System.out.format("----%s: %s\n", jobClass.getName(), this.getServiceProcess(jobClass).toString());
        }
        System.out.format("--Number of Servers: %d\n", this.getNumberOfServers());
        this.output.printSummary();
    }

    public SchedStrategy getSchedStrategy() {
        return this.schedStrategy;
    }

    public SchedStrategyType getSchedPolicy() {
        return this.schedPolicy;
    }

    public double minRate() {
        double acc = Double.POSITIVE_INFINITY;
        for (ServiceBinding serviceProcess : this.serviceProcesses) {
            double dRate = serviceProcess.getDistribution().getRate();
            if (dRate != 0) {
                acc = Math.min(acc, dRate);
            }
        }
        return acc;
    }
    public double maxRate() {
        double acc = 0;
        for (ServiceBinding serviceProcess : this.serviceProcesses) {
            if (serviceProcess.getDistribution().getRate() == Double.POSITIVE_INFINITY) {
                continue;
            }
            acc = Math.max(acc, serviceProcess.getDistribution().getRate() * this.getNumberOfServers());
        }
        return acc;
    }
    public double avgRate() {
        double acc = 0;
        for (ServiceBinding serviceProcess : this.serviceProcesses) {
            double accVal = serviceProcess.getDistribution().getRate();
            if ((accVal == Double.POSITIVE_INFINITY) || (accVal == 0)) {
                continue;
            }
            acc += accVal;
        }
        return acc;
    }

    public int rateCt() {
        int acc = 0;
        for (ServiceBinding serviceProcess : this.serviceProcesses) {
            double accVal = serviceProcess.getDistribution().getRate();
            if ((accVal == Double.POSITIVE_INFINITY) || (accVal == 0)) {
                continue;
            }
            acc++;
        }
        return acc;
    }
}
