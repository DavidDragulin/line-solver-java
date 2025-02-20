package jline.lang;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.DoubleStream;
import java.util.stream.Collectors;
import org.ejml.data.DMatrixRMaj;
import org.ejml.ops.DConvertMatrixStruct;
import org.qore.KPC.MAP;

import jline.lang.constant.*;
import jline.lang.distributions.*;
import jline.lang.nodes.*;
import jline.lang.sections.ClassSwitcher;
import jline.lang.sections.Forker;
import jline.lang.sections.Joiner;
import jline.lang.sections.ServiceSection;
import jline.lang.sections.ServiceTunnel;
import jline.lang.sections.StatefulClassSwitcher;
import jline.lang.sections.StatelessClassSwitcher;
import jline.lang.state.NetworkState;
import jline.util.GetRoutingMatrixReturnType;
import jline.util.Pair;
import jline.util.Sync;
import jline.util.NodeParam;

import static java.lang.Double.isFinite;

import jline.api.MAM;
import jline.api.CTMC;
import jline.api.SN;
import jline.api.UTIL;

public class Network extends Model implements Serializable {
    private boolean doChecks;
    private boolean hasState;
    private boolean logPath;
    private boolean usedFeatures;

    private List<Node> nodes;
    private List<JobClass> jobClasses;
    private List<Station> stations;

    private boolean hasStruct;
    private NetworkStruct sn;
    private JLineMatrix csMatrix;
    private JLineMatrix connections;

    private List<JLineMatrix> handles;

    // caches
    private Map<Node, Map<JobClass, List<Node>>> classLinks;

    public Network(String modelName) {
        super(modelName);

        this.hasState = false;
        this.doChecks = true;

        this.nodes = new ArrayList<Node>();
        this.jobClasses = new ArrayList<JobClass>();
        this.stations = new ArrayList<Station>();

        this.classLinks = new HashMap<Node, Map<JobClass, List<Node>>>();

        this.hasStruct = false;
        this.csMatrix = null;
        this.sn = null;
        this.connections = null;
    }

    public void setDoChecks(boolean doChecks) {
        this.doChecks = doChecks;
    }

    public int[] getSize() {
        int[] outInt = new int[2];
        outInt[0] = this.getNumberOfNodes();
        outInt[1] = this.getNumberOfClasses();
        return outInt;
    }

    public boolean hasOpenClasses() {
        for (JobClass temp : this.jobClasses) {
            if (temp instanceof OpenClass) {
                return true;
            }
        }

        return false;
    }

    public int getJobClassIndex (JobClass jobClass) {
        return this.jobClasses.indexOf(jobClass);
    }

    public JobClass getJobClassFromIndex(int inIdx) {
        return this.jobClasses.get(inIdx);
    }

    public List<Integer> getIndexOpenClasses() {
        List<Integer> outList = new ArrayList<Integer>();
        for (int i = 0; i < this.jobClasses.size(); i++) {
            if (this.jobClasses.get(i) instanceof OpenClass) {
                outList.add(this.getJobClassIndex(this.jobClasses.get(i)));
            }
        }
        return outList;
    }

    public List<Integer> getIndexStatefulNodes() {
    	List<Integer> outList = new ArrayList<Integer>();
    	for(int i = 0; i < this.nodes.size(); i++) {
    		if (this.nodes.get(i) instanceof StatefulNode) {
    			outList.add(i);
    		}
    	}
    	return outList;
    }

    public boolean hasClosedClasses() {
        for (JobClass temp : this.jobClasses) {
            if (temp instanceof ClosedClass) {
                return true;
            }
        }
        return false;
    }

    public List<Integer> getIndexClosedClasses() {
        List<Integer> outList = new ArrayList<Integer>();
        for (int i = 0; i < this.jobClasses.size(); i++) {
            if (this.jobClasses.get(i) instanceof ClosedClass) {
                outList.add(this.getJobClassIndex(this.jobClasses.get(i)));
            }
        }
        return outList;
    }

    public boolean hasClasses() {
        return !this.jobClasses.isEmpty();
    }

    public List<JobClass> getClasses() {
        return this.jobClasses;
    }

    public List<Node> getNodes() { return this.nodes; }

    public List<Station> getStations() {return this.stations; }

    public void addNode(Node node) {
        nodes.add(node);
        if (node instanceof Station)
        	stations.add((Station) node);
    }

    public void setInitialized(boolean initStatus) {
        this.hasState = initStatus;
    }

    public int getNumberOfNodes() {
        return this.nodes.size();
    }

    public int getNumberOfStations() {
        return this.stations.size();
    }

    public int getNumberOfStatefulNodes() {
        int ct = 0;
        for (Node node : this.nodes) {
            if (node instanceof StatefulNode) {
                ct++;
            }
        }
        return ct;
    }

    public int getNumberOfClasses() {
        return this.jobClasses.size();
    }

    public JobClass getClassByName(String name) {
        for (JobClass jobClass : this.jobClasses) {
            if (jobClass.getName().equals(name)) {
                return jobClass;
            }
        }
        return null;
    }

    public JobClass getClassByIndex(int index) {
        for (JobClass jobClass : this.jobClasses) {
            if (this.getJobClassIndex(jobClass) == index) {
                return jobClass;
            }
        }
        return null;
    }

    public void addJobClass(JobClass jobClass) {
        this.jobClasses.add(jobClass);
    }

    public Node getNodeByName(String name) {
        for (Node node : this.nodes) {
            if (node.getName().equals(name)) {
                return node;
            }
        }

        return null;
    }

    public Node getNodeByStatefulIndex(int idx) {
        int nodesPassed = 0;
        for (Node nodeIter : this.nodes) {
            if (nodeIter instanceof StatefulNode) {
                if (nodesPassed == idx) {
                    return nodeIter;
                }
                nodesPassed++;
            }
        }

        return null;
    }

    public int getNodeIndex(Node node) {
        return this.nodes.indexOf(node);
    }

    public int getNodeIndex(String name) {
    	for(Node node : this.nodes) {
    		if (node.getName().equals(name))
    			return getNodeIndex(node);
    	}
    	return -1;
    }

    public int getStatefulNodeIndex(Node node) {
    	if (!(node instanceof StatefulNode))
    		return -1;

        int outIdx = 0;
        for (Node nodeIter : this.nodes) {
            if (nodeIter == node) {
                return outIdx;
            } else if (nodeIter instanceof StatefulNode) {
                outIdx++;
            }
        }

        return -1;
    }

    public Node getStatefulNodeFromIndex(int inIdx) {
        int outIdx = 0;
        for (Node nodeIter : this.nodes) {
            if (outIdx == inIdx) {
                return nodeIter;
            } else if (nodeIter instanceof StatefulNode) {
                outIdx++;
            }
        }

        return null;
    }

    public int getStationIndex(Node node) {
    	return this.stations.indexOf(node);
    }

    public Node getStationFromIndex(int inIdx) {
    	return this.stations.get(inIdx);
    }

    public RoutingMatrix serialRouting(List<JobClass> jobClasses, Node... nodes) {
        if (nodes.length == 0) {
            return new RoutingMatrix();
        }

        Network network = nodes[0].model;
        RoutingMatrix outMatrix = new RoutingMatrix(this, jobClasses, network.nodes);

        for (int i = 1; i < nodes.length; i++) {
            //System.out.format("Loading connection %s->%s\n", nodes[i-1].getName(), nodes[i].getName());
            outMatrix.addConnection(nodes[i-1], nodes[i]);
        }

        if(!(nodes[nodes.length-1] instanceof Sink)) {
            outMatrix.addConnection(nodes[nodes.length-1], nodes[0]);
        }

        return outMatrix;
    }

    public RoutingMatrix serialRouting(JobClass jobClass, Node... nodes) {
        List<JobClass> jobClasses = new ArrayList<JobClass>();
        jobClasses.add(jobClass);

        return this.serialRouting(jobClasses, nodes);
    }

    public RoutingMatrix serialRouting(Node... nodes) {
        if (nodes.length == 0) {
            return new RoutingMatrix();
        }
        Network network = nodes[0].model;
        return this.serialRouting(network.jobClasses, nodes);
    }

    public void link(RoutingMatrix routing) {
        /*
             Input:
                routing: row: source, column: dest
         */

        routing.setRouting(this);
    }

    public void unLink() {
        for (Node node : this.nodes) {
            node.resetRouting();
        }
    }

    public void addLink(Node sourceNode, Node destNode) {
    	int sourceNodeIdx = this.getNodeIndex(sourceNode);
    	int destNodeIdx = this.getNodeIndex(destNode);
    	this.addLink(sourceNodeIdx, destNodeIdx);
    }

    public void addLink(int sourceNodeIdx, int destNodeIdx) {
    	if (this.connections == null || this.connections.isEmpty())
    		this.connections = new JLineMatrix(nodes.size(), nodes.size());

    	if (this.connections.getNumRows() != this.nodes.size())
    		this.connections.expandMatrix(this.nodes.size(), this.nodes.size(), this.nodes.size()*this.nodes.size());

    	this.connections.set(sourceNodeIdx, destNodeIdx, 1.0);
    }

    // Get initial state
    public NetworkState getState() {
        if (!this.hasInitState()) {
            this.initDefault();
        }

        for (int i = 0; i < this.getNumberOfNodes(); i++) {
            if (this.nodes.get(i).isStateful()) {
                int index = this.nodes.get(i).getStatefulIdx();
                JLineMatrix initialState = this.stations.get(index).getState();
                JLineMatrix priorInitialState = this.stations.get(index).getStatePrior();
                this.sn.state.put(this.stations.get(index), initialState);
                this.sn.statePrior.put(this.stations.get(index), priorInitialState);
            }
        }

        return new NetworkState(this.sn.state, this.sn.statePrior);
    }

    public boolean hasInitState() {
        boolean output = true;
        if (!this.hasState) { // check if all stations are initialized
            for (int i = 0; i < this.getNumberOfNodes(); i++) {
                if (this.nodes.get(i) instanceof StatefulNode) {
                    int index = this.nodes.get(i).getStatefulIdx();
                    if (this.stations.get(index).getState().isEmpty()) {
                        output = false;
                    }
                }
            }
        }
        return output;
    }

    public void initDefault() {
        // TODO: is it necessary to have a version where, per LINE, nodes can be passed in as a parameter?
        // open classes are empty
        // closed classes are initialized at reference station
        // running jobs are allocated in class id order until all servers are busy

        NetworkStruct sn = this.getStruct(false);
        int R = sn.nClasses;
        JLineMatrix N = sn.njobs.transpose();

        for (int i = 0; i < this.getNumberOfNodes(); i++) {
            if (sn.isstation.get(i, 0) == 1) {
                JLineMatrix n0 = new JLineMatrix(1, N.length());
                n0.zero();
                JLineMatrix s0 = new JLineMatrix(1, N.length());
                s0.zero();
                double s = sn.nservers.get((int) sn.nodeToStation.get(0, i), 0); // allocate

                for (int r = 0; r < N.getNumRows(); r++) {
                     if (isFinite(N.get(r, 0))) { // for all closed classes
                         if (sn.nodeToStation.get(0, i) == sn.refstat.get(r, 0)) {
                             n0.set(0, r, N.get(r, 0));
                         }
                     }
                     s0.set(0, r, Math.min(n0.get(0, r), s));
                     s -= s0.get(0, r);
                }

                JLineMatrix state_i = NetworkState.fromMarginalAndStarted(sn, i, n0, s0);

                switch (sn.nodetypes.get(i)) {
                    case Cache:
                        int nVarsInt = (int) sn.nvars.get(i, 2 * R);
                        JLineMatrix newState_i = new JLineMatrix(1, state_i.getNumCols() + nVarsInt);
                        for (int p = 0; p < state_i.length(); p++) {
                            newState_i.set(0, p, state_i.get(0, p));
                        }
                        int addition = 0;
                        for (int p = state_i.length(); p < newState_i.length(); p++) {
                            newState_i.set(0, p, addition);
                            addition++;
                        }
                        state_i = newState_i.clone();
                        break;

                    case Place:
                        state_i.zero(); // for now PNs are single class
                        break;

                    default:
                        if (sn.isstation.get(i, 0) == 1) {
                            for (int r = 0; r < sn.nClasses; r++) {
                                if (sn.proctype.get(sn.stations.get(i)).get(sn.jobClasses.get(r)) == ProcessType.MAP) {
                                    JLineMatrix one = new JLineMatrix(1, 1,1);
                                    one.set(0, 0, 1);
                                    state_i = NetworkState.decorate(state_i, one);
                                }
                            }
                        }
                }

                for (int r = 0; r < sn.nClasses; r++) {
                    if ((sn.routing.get(sn.stations.get(i)).get(sn.jobClasses.get(r))
                          == RoutingStrategy.RROBIN)
                        || (sn.routing.get(sn.stations.get(i)).get(sn.jobClasses.get(r))
                          == RoutingStrategy.WRROBIN)) {
                        // Start from first connected queue
                        List<Integer> findSnRt = new ArrayList<>();
                        for (int p = 0; p < sn.rt.getNumCols(); p++) {
                            if (sn.rt.get(i, p) == 1) {
                                findSnRt.add(p);
                            }
                        }
                        JLineMatrix newState_i = new JLineMatrix(1, state_i.getNumCols() + findSnRt.size());
                        for (int p = 0; p < state_i.length(); p++) {
                            newState_i.set(0, p, state_i.get(0, p));
                        }
                        for (int p = state_i.length(); p < newState_i.length(); p++) {
                            newState_i.set(0, p, findSnRt.get(p - state_i.length()));
                        }
                        state_i = newState_i.clone();
                        break;
                    }
                }

                if (state_i.isEmpty()) {
                    System.err.format("Default initialisation failed on station %d", i);
                } else {
                    this.stations.get(this.nodes.get(i).getStatefulIdx()).setState(state_i);
                    JLineMatrix prior_state_i = new JLineMatrix(1, state_i.getNumRows());
                    prior_state_i.zero();
                    prior_state_i.set(0, 0, 1);
                    this.stations.get(this.nodes.get(i).getStatefulIdx()).setStatePrior(prior_state_i);
                }
            } else if (sn.isstateful.get(i, 0) == 1) { // Not a station
                if (this.nodes.get(i) instanceof Cache) {
                    JLineMatrix state_i = new JLineMatrix(1, this.getNumberOfClasses());
                    state_i.zero();
                    // TODO: state_i = [state_i, 1:sum(self.nodes{ind}.itemLevelCap)];
                    System.out.println("Warning: unimplemented code reached in initDefault - Cache.itemLevelCap");
                    this.stations.get(this.nodes.get(i).getStatefulIdx()).setState(state_i);
                } else if (this.nodes.get(i) instanceof Router) {
                  JLineMatrix one = new JLineMatrix(1, 1, 1);
                  one.set(0, 0, 1);
                  this.stations.get(this.nodes.get(i).getStatefulIdx()).setState(one);
                } else {
                    this.stations.get(this.nodes.get(i).getStatefulIdx()).setState(new JLineMatrix(0, 0));
                }
            }
        }

        if (this.isStateValid()) {
            this.hasState = true;
        } else {
            System.err.println("Default initialisation failed.");
        }
    }

    private boolean isStateValid() {
        // This code in LINE is found in api/sn/snIsStateValid.m
        // It is only ever called by isStateValid method in Network
        // For that reason, and to avoid unnecessary complication, I've consolidated into one method in JLINE

        // Modified so not using this.sn
        NetworkStruct snTmp = this.getStruct(true);
        JLineMatrix nir = new JLineMatrix(snTmp.nstations, snTmp.nClasses);
        JLineMatrix sir = new JLineMatrix(snTmp.nstations, snTmp.nClasses);

        for (int ist = 0; ist < snTmp.nstations; ist++) {
          int isf = (int) snTmp.stationToStateful.get(0, ist);
          if (snTmp.state.get(snTmp.stations.get(isf)).getNumRows() > 1) {
              System.err.format("isStateValid will ignore some states of station %d, define a unique initial state to address this problem.", ist);
              JLineMatrix initialState = new JLineMatrix(1, snTmp.state.get(snTmp.stations.get(isf)).getNumCols());
              JLineMatrix.extractRows(snTmp.state.get(snTmp.stations.get(isf)), 0, 1, initialState);
              snTmp.state.put(snTmp.stations.get(isf), initialState);
          }

          NetworkState.StateMarginalStatistics stats = NetworkState.toMarginal(snTmp,
                  (int) snTmp.stationToNode.get(0, ist),
                  snTmp.state.get(snTmp.stations.get(isf)),
                  null, null, null, null, null);

          for (int i = 0; i < snTmp.nClasses; i++) {
              nir.set(ist, i, stats.nir.get(0, i));
              sir.set(ist, i, stats.sir.get(0, i));
          }
        }

        return NetworkState.isValid(snTmp, nir, sir);
    }

    public void printSummary() {
        System.out.format("jline.Network model: %s\n", this.getName());
        System.out.format("--------------------------------------------------------\n");
        System.out.format("Job classes: \n");
        for (JobClass jobClass : this.jobClasses) {
            jobClass.printSummary();
        }
        System.out.format("--------------------------------------------------------\n");
        System.out.format("Nodes: \n");
        for (Node node : this.nodes) {
            node.printSummary();
            System.out.format("--------\n");
        }
    }

    public void clearCaches() {
        this.classLinks = new HashMap<Node, Map<JobClass, List<Node>>>();
    }

    protected void generateClassLinks() {
        this.classLinks = new HashMap<Node, Map<JobClass, List<Node>>>();
        for (Node node : this.nodes) {
            Map<JobClass, List<Node>> nodeMap = new HashMap<JobClass, List<Node>>();

            for (JobClass jobClass : this.jobClasses) {
                nodeMap.put(jobClass, new ArrayList<Node>());
            }
            classLinks.put(node, nodeMap);
        }

        for (Node node : this.nodes) {
            for (final OutputStrategy outputStrategy : node.getOutputStrategies()) {
                Node destNode = outputStrategy.getDestination();
                if (destNode == null) {
                    continue;
                }
                JobClass jobClass = outputStrategy.getJobClass();
                this.classLinks.get(destNode).get(jobClass).add(node);
            }
        }
    }

    public int getClassLinks(Node node, JobClass jobClass) {
        if (this.classLinks.isEmpty()) {
            this.generateClassLinks();
        }
        return this.classLinks.get(node).get(jobClass).size();
    }

    public double minRate() {
        double acc = Double.POSITIVE_INFINITY;
        for (Node node : this.nodes) {
            if (node instanceof HasSchedStrategy) {
                double accVal = ((HasSchedStrategy)node).minRate();
                if (accVal != 0) {
                    acc = Math.min(acc, accVal);
                }
            }
        }
        return acc;
    }

    public double maxRate() {
        double acc = 0;
        for (Node node : this.nodes) {
            if (node instanceof HasSchedStrategy) {
                double accVal = ((HasSchedStrategy)node).maxRate();
                if (accVal != Double.POSITIVE_INFINITY) {
                    acc = Math.max(acc, accVal);
                }
            }
        }
        return acc;
    }

    public double avgRate() {
        double acc = 0;
        int accCt = 0;
        for (Node node : this.nodes) {
            if (node instanceof HasSchedStrategy) {
                double accVal = ((HasSchedStrategy)node).avgRate();
                int valCt = ((HasSchedStrategy)node).rateCt();
                if ((accVal != Double.POSITIVE_INFINITY) && (accVal != 0)) {
                    acc += accVal;
                    accCt += valCt;
                }
            }
        }
        return acc/accCt;
    }

	public JLineMatrix getCsMatrix() {
		return this.csMatrix;
	}

	public void setCsMatrix(JLineMatrix csMatrix) {
		this.csMatrix = csMatrix;
	}

    public JLineMatrix getConnectionMatrix() {
    	if (this.connections.getNumCols() < this.getNumberOfNodes() ||
    			this.connections.getNumRows() < this.getNumberOfNodes())
    		this.connections.expandMatrix(getNumberOfNodes(), getIndexSourceNode(), getNumberOfNodes()*getNumberOfNodes());
    	return this.connections;
    }

    public void setConnectionMatrix(JLineMatrix connection) {
    	this.connections = connection;
    }

    public JLineMatrix getForkJoins() {
    	int I = this.getNumberOfNodes();
    	JLineMatrix fjPairs = new JLineMatrix(I,I);
    	
    	for(int i = 0; i < I; i++) {
    		Node node = this.nodes.get(i);
    		if (node instanceof Fork) {
    			//no-op
    		} else if (node instanceof Join) {
    			fjPairs.set(((Join)node).joinOf.getNodeIdx(), node.getNodeIdx(), 1.0);
    		}
    	}
    	return fjPairs;
    }

    public boolean getHasStruct() {
    	return this.hasStruct;
    }

    public void setHasStruct(boolean hasStruct) {
    	this.hasStruct = hasStruct;
    }

    public void setStruct(NetworkStruct sn) {
    	this.sn = sn;
    }

    public NetworkStruct getStructWithoutRecompute() {
    	return this.sn;
    }

    public NetworkStruct getStruct(boolean wantInitialState) {
    	if (!this.hasStruct)
    		refreshStruct(true);

    	if (wantInitialState)
    		getState();

    	return this.sn;
    }

    public void refreshStruct(boolean hardRefresh) {

    	sanitize();

        List<NodeType> nodetypes;
        List<String> classnames;
        List<String> nodenames;
        JLineMatrix refstat;
        JLineMatrix conn;
        JLineMatrix njobs;
        JLineMatrix numservers;
        JLineMatrix lldscaling;
        Map<Station, Function<JLineMatrix, Double>> cdscaling;
        Map<Node, Map<JobClass, RoutingStrategy>> routing;


    	if (this.hasStruct && !hardRefresh) {
    		nodetypes = sn.nodetypes;
    		classnames = sn.classnames;
    		nodenames = sn.nodenames;
    		refstat = sn.refstat;
    	} else {
    		nodetypes = getNodeTypes();
    		classnames = getClassNames();
    		nodenames = getNodeNames();
    		refstat = getReferenceStations();
    	}

    	conn = getConnectionMatrix();
    	njobs = getNumberOfJobs();
    	numservers = getStationServers();
    	lldscaling = getLimitedLoadDependence();
    	cdscaling = getLimitedClassDependence();

    	if (sn == null)
    		sn = new NetworkStruct();

    	sn.nNodes = nodenames.size();
    	sn.nClasses = classnames.size();
    	sn.stations = this.stations;
    	sn.jobClasses = this.jobClasses;
    	sn.nodes = this.nodes;

    	routing = new HashMap<Node, Map<JobClass, RoutingStrategy>>();
    	for(Node node : this.nodes) {
    		Map<JobClass, RoutingStrategy> map = new HashMap<JobClass, RoutingStrategy>();
    		for(JobClass jobclass : this.jobClasses) {
    			map.put(jobclass, getRoutingStrategyFromNodeAndClassPair(node, jobclass));
    		}
    		routing.put(node, map);
    	}

    	sn.nclosedjobs = DoubleStream.of(njobs.nz_values).boxed().filter(val -> !Double.isInfinite(val)).reduce(0.0, (a,b) -> a+b);
    	sn.nservers = numservers;
    	sn.isstation = getIsStationArray();
    	sn.nstations = stations.size();
    	sn.nodetypes = nodetypes;
    	sn.scv = new JLineMatrix(sn.nstations, sn.nClasses, sn.nstations * sn.nClasses);
    	sn.scv.fill(1.0);
    	sn.njobs = njobs.transpose();
    	sn.refstat = refstat;
    	sn.space = new HashMap<Station, JLineMatrix>();
    	for(int i = 0; i < sn.nstations; i++)
    		sn.space.put(stations.get(i), new JLineMatrix(0,0,0));
    	sn.routing = routing;
    	sn.chains = new JLineMatrix(0,0);
    	sn.lst = null;
    	sn.lldscaling = lldscaling;
    	sn.cdscaling = cdscaling;
    	sn.nodetypes = nodetypes;
    	sn.isstateful = getIsStatefulArray();
    	sn.isstatedep = new JLineMatrix(sn.nNodes, 3, 3*sn.nNodes);
    	for(int i = 0; i < sn.nNodes; i++) {
    		//Line 72-80 is ignored since JLine not support cache node
    		Node node = this.nodes.get(i);
    		for(int j = 0; j < sn.nClasses; j++) {
    			JobClass jobclass = this.jobClasses.get(j);
    			switch (sn.routing.get(node).get(jobclass)) {
					case RROBIN:
					case WRROBIN:
					case JSQ:
						sn.isstatedep.set(i, 2, 1.0);
					default:
						continue;
    			}
    		}
    	}
    	sn.nStateful = getNumberOfStatefulNodes();
    	sn.state = new HashMap<Station, JLineMatrix>(sn.nstations);
        sn.statePrior = new HashMap<Station, JLineMatrix>(sn.nstations);
        sn.space = new HashMap<Station, JLineMatrix>(sn.nstations);
    	for(int i = 0; i < sn.nstations; i++)
    		sn.state.put(stations.get(i), new JLineMatrix(0,0,0));
    	sn.nodenames = nodenames;
    	sn.classnames = classnames;
    	sn.connmatrix = conn;
    	//line 97-108 is ignored since for transition node

    	sn.nodeToStateful = new JLineMatrix(1, nodes.size(), nodes.size());
    	sn.nodeToStation = new JLineMatrix(1, nodes.size(), nodes.size());
    	sn.stationToNode = new JLineMatrix(1, stations.size(), stations.size());
    	sn.stationToStateful = new JLineMatrix(1, stations.size(), stations.size());
    	sn.statefulToNode = new JLineMatrix(1, sn.nStateful, sn.nStateful);
        for(int i = 0; i < nodes.size(); i++) {
        	sn.nodeToStateful.set(0, i, nodes.get(i).getStatefulIdx());
        	sn.nodeToStation.set(0, i, nodes.get(i).getStationIdx());
        }
        for(int i = 0; i < stations.size(); i++) {
        	sn.stationToNode.set(0, i, stations.get(i).getNodeIdx());
        	sn.stationToStateful.set(0, i, stations.get(i).getStatefulIdx());
        }
        for(int i = 0; i < sn.nStateful; i++) {
        	sn.statefulToNode.set(0, i, getStatefulNodeFromIndex(i).getNodeIdx());
        }

        refreshPriorities();
        refreshService(null, null);

        if (sn.nodetypes.contains(NodeType.Cache))
        	refreshChains(false);
        else
        	refreshChains(true);

        JLineMatrix refclasses = this.getReferenceClasses();
        JLineMatrix refclass = new JLineMatrix(1, sn.nchains);
        for(int c = 0; c < sn.nchains; c++) {
        	JLineMatrix inchain_c = sn.inchain.get(c);
        	JLineMatrix find_refclasses = refclasses.find();
        	//The following works since inchain_c is sorted and find always return a sorted matrix
        	double isect = -1;
        	int left = 0, right = 0;
        	while (left < inchain_c.getNumCols() && right < find_refclasses.getNumRows()) {
        		double left_val = inchain_c.get(left);
        		double right_val = find_refclasses.get(right);
        		if (left_val == right_val) {
        			isect = left_val;
        			break;
        		} else if (left_val < right_val) {
        			left++;
        		} else {
        			right++;
        		}
        	}
        	refclass.set(0, c, isect);
        }
        this.sn.refclass = refclass;
        this.sn.fj = this.getForkJoins();

        refreshLocalVars();
        refreshSync();
        //refreshPetriNetNodes()
        this.hasStruct = true;
    }

    public void sanitize() {
    	//THIS FUNCTION IS NOT TESTED
    	if (this.sn == null) {
    		int M = this.stations.size();
    		int K = this.jobClasses.size();
    		for(int i = 0; i < this.nodes.size(); i++) {
    			Node node = this.nodes.get(i);
    			if (node instanceof Cache) {
    				throw new RuntimeException("Cache is not supported in JLINE");
    			} else if (node instanceof Logger) {
    				//do nothing
    			} else if (node instanceof ClassSwitch) {
    				//do nothing
    			} else if (node instanceof Join) {
    				Join join = (Join)node;
    				for(int k = 0; k < K; k++) {
    					JobClass jobclass = this.jobClasses.get(k);
    					join.setClassCap(jobclass, Double.POSITIVE_INFINITY);
    					join.setDropRule(jobclass, DropStrategy.WaitingQueue);
    				}
    			} else if (node instanceof Delay) {
    				Delay delay = (Delay)node;
    				for(int k = 0; k < K; k++) {
    					JobClass jobclass = this.jobClasses.get(k);
    					if (!delay.getServer().containsJobClass(jobclass)) {
    						delay.setService(jobclass, new DisabledDistribution(), 0);
    						delay.setClassCap(jobclass, 0);
    						delay.getInput().setInputJobProcess(new InputBinding(jobclass, SchedStrategyType.NP, DropStrategy.WaitingQueue));
    					}
    				}

    				switch (delay.getSchedStrategy()) {
    					case SEPT:
	    					ArrayList<Double> svcTime = new ArrayList<Double>();
	    					for(int k = 0; k < K; k++)
	    						svcTime.add(delay.getServiceProcess(this.jobClasses.get(k)).getMean());
	    					Collections.sort(svcTime);

	    					for(int k = 0; k < K; k++)
	    						delay.setSchedStrategyPar(this.jobClasses.get(k), svcTime.get(k));
	    					break;
    					default:
    						continue;
    				}

    			} else if (node instanceof Queue) {
    				Queue queue = (Queue)node;
    				for(int k = 0; k < K; k++) {
    					JobClass jobclass = this.jobClasses.get(k);
    					if (!queue.getServer().containsJobClass(jobclass)) {
    						queue.setService(jobclass, new DisabledDistribution(), 0);
    						queue.setClassCap(jobclass, 0);
    				        queue.getInput().setInputJobProcess(new InputBinding(jobclass, SchedStrategyType.NP, DropStrategy.WaitingQueue));
    					}
    				}

    				switch (queue.getSchedStrategy()) {
	    				case SEPT:
	    					ArrayList<Double> svcTime = new ArrayList<Double>();
	    					for(int k = 0; k < K; k++)
	    						svcTime.add(queue.getServiceProcess(this.jobClasses.get(k)).getMean());

	    					if (svcTime.stream().distinct().collect(Collectors.toList()).size() != K)
	    						throw new RuntimeException("SEPT does not support identical service time means.");

	    					ArrayList<Double> svcTimeSorted = (ArrayList<Double>) svcTime.clone();
	    					Collections.sort(svcTimeSorted);
	    					for(int k = 0; k < K; k++)
	    						queue.setSchedStrategyPar(this.jobClasses.get(k), svcTimeSorted.indexOf(svcTime.get(k)) + 1);
	    					break;
	    				case LEPT:
	    					svcTime = new ArrayList<Double>();
	    					for(int k = 0; k < K; k++)
	    						svcTime.add(queue.getServiceProcess(this.jobClasses.get(k)).getMean());

	    					if (svcTime.stream().distinct().collect(Collectors.toList()).size() != K)
	    						throw new RuntimeException("SEPT does not support identical service time means.");

	    					svcTimeSorted = (ArrayList<Double>) svcTime.clone();
	    					Collections.sort(svcTimeSorted, Collections.reverseOrder());
	    					for(int k = 0; k < K; k++)
	    						queue.setSchedStrategyPar(this.jobClasses.get(k), svcTimeSorted.indexOf(svcTime.get(k)) + 1);
	    					break;
    					default:
    						continue;
    				}
    			} else if (node instanceof Sink) {
    				//do nothing
    			} else if (node instanceof Source) {
    				Source source = (Source)node;
    				for(int k = 0; k < K; k++) {
    					JobClass jobclass = this.jobClasses.get(k);
    					if (!source.containsJobClass(jobclass))
    						source.setArrival(jobclass, new DisabledDistribution());
    				}
    			} else {
    				throw new RuntimeException("Transition and Place node are not supported in JLINE");
    			}
    		}

			int sourceIdx = this.getIndexSourceNode();
    		for(int i = 0; i < M; i++) {
    			if ((sourceIdx == -1) || (i != sourceIdx)) {
    				for(int r = 0; r < K; r++) {
    					ServiceSection server = this.stations.get(i).getServer();
    					if (server instanceof ServiceTunnel) {
    						//do nothing
    					} //else if (server instanceof CacheClassSwitcher) {}
    					else {
    						if (!this.stations.get(i).getServer().containsJobClass(this.jobClasses.get(r)))
    							this.stations.get(i).getServer().setServiceProcesses(new ServiceBinding(this.jobClasses.get(r), ServiceStrategy.LI, new DisabledDistribution()));
    					}
    				}
    			}
    		}
    	}
    }

	public List<NodeType> getNodeTypes() {
    	int M = getNumberOfNodes();
    	List<NodeType> nodetypes = new ArrayList<NodeType>(M);

    	try {
    		for (int i = 0; i < M; i++) {
        		Node nodeIter = this.nodes.get(i);
        		if (nodeIter instanceof Logger)
        			nodetypes.add(NodeType.Logger);
        		else if (nodeIter instanceof ClassSwitch)
        			nodetypes.add(NodeType.ClassSwitch);
        		else if (nodeIter instanceof Join)
        			nodetypes.add(NodeType.Join);
        		else if (nodeIter instanceof Sink)
        			nodetypes.add(NodeType.Sink);
        		else if (nodeIter instanceof Router)
        			nodetypes.add(NodeType.Router);
        		else if (nodeIter instanceof Delay)
        			nodetypes.add(NodeType.Delay);
        		else if (nodeIter instanceof Fork)
        			nodetypes.add(NodeType.Fork);
        		else if (nodeIter instanceof Queue)
        			nodetypes.add(NodeType.Queue);
        		else if (nodeIter instanceof Source)
        			nodetypes.add(NodeType.Source);
//				Below node types are not supported in JLine
//        		else if (nodeIter instanceof Place)
//        			nodetypes.add(NodeType.Place);
//        		else if (nodeIter instanceof Transition)
//        			nodetypes.add(NodeType.Transition);
//        		else if (nodeIter instanceof Cache)
//        			nodetypes.add(NodeType.Cache);
        		else
        			throw new Exception("Unknown node type.");
        	}
    	} catch (Exception e){
    		e.printStackTrace();
    		System.exit(1);
    	}

    	return nodetypes;
    }

    public List<String> getClassNames() {
    	if (hasStruct && sn.classnames != null)
    		return sn.classnames;

    	int K = getNumberOfClasses();
    	List<String> classnames = new ArrayList<String>();
    	for (int i = 0; i < K; i++)
    		classnames.add(jobClasses.get(i).getName());

    	return classnames;
    }

    public List<String> getNodeNames() {
    	if (hasStruct && sn.classnames != null)
    		return sn.nodenames;

    	int M = getNumberOfNodes();
    	List<String> nodenames = new ArrayList<String>();
    	for (int i = 0; i < M; i++)
    		nodenames.add(nodes.get(i).getName());

    	return nodenames;
    }

    public JLineMatrix getReferenceStations() {
    	int K = getNumberOfClasses();
    	JLineMatrix refstat = new JLineMatrix(K, 1, K);

    	for (int i = 0; i < K; i++) {
    		if (jobClasses.get(i).type == JobClassType.Open) {
    			refstat.set(i, 0, getIndexSourceNode());
    		} else {
    			ClosedClass cc = (ClosedClass) jobClasses.get(i);
    			refstat.set(i, 0, getNodeIndex(cc.getRefstat()));
    		}
    	}


    	return refstat;
    }

    public JLineMatrix getReferenceClasses() {
    	int K = this.jobClasses.size();
    	JLineMatrix refclass = new JLineMatrix(K,1);
    	for(int i = 0; i < K; i++) {
    		if (this.jobClasses.get(i).isReferenceClass())
    			refclass.set(i, 0, 1.0);
    	}
    	return refclass;
    }

    public int getIndexSourceNode() {
        int res = 0;
        for (Node nodeIter : this.nodes) {
        	if (nodeIter instanceof Source)
        		return res;
        	res++;
        }

        return -1;
    }

    public int getIndexSinkNode() {
        int res = 0;
        for (Node nodeIter : this.nodes) {
        	if (nodeIter instanceof Sink)
        		return res;
        	res++;
        }

        return -1;
    }

    public JLineMatrix getNumberOfJobs() {
    	int K = getNumberOfClasses();
    	JLineMatrix njobs = new JLineMatrix(K, 1, K);
    	for(int i = 0; i < K; i++) {
    		if (jobClasses.get(i).type == JobClassType.Open)
    			njobs.set(i, 0, Double.POSITIVE_INFINITY);
    		else if (jobClasses.get(i).type == JobClassType.Closed)
    			njobs.set(i, 0, jobClasses.get(i).getNumberOfJobs());
    	}
    	return njobs;
    }

    public JLineMatrix getStationServers() {
    	int I = stations.size();
    	JLineMatrix numservers = new JLineMatrix(I, 1, I);
    	for(int i = 0; i < I; i++) {
    		if (stations.get(i).getNumberOfServers() == Integer.MAX_VALUE)
    			numservers.set(i, 0, Double.POSITIVE_INFINITY);
    		else
    			numservers.set(i, 0, stations.get(i).getNumberOfServers());
    	}

    	return numservers;
    }

    public JLineMatrix getLimitedLoadDependence() {
    	List<JLineMatrix> mus = new ArrayList<JLineMatrix>();
    	int maxsize = 0;

    	for (Station station : this.stations) {
    		mus.add(station.getLimitedLoadDependence());
    		maxsize = Math.max(maxsize, station.getLimitedLoadDependence().length());
    	}

    	int M = this.stations.size();
    	JLineMatrix alpha = new JLineMatrix(M, maxsize);
    	alpha.fill(1.0);
    	for(int i = 0; i < M; i++) {
    		JLineMatrix mu = mus.get(i);
    		if(mu.length() > 0) {
    			JLineMatrix.extract(mu, 0, 1, 0, mu.length(), alpha, i, 0);
    			for(int j = 0; j < mu.length(); j++) {
    				if (alpha.get(i,j) == 0)
    					alpha.set(i, j, 1.0);
    			}
    		}
    	}
		return alpha;
    }

    public Map<Station, Function<JLineMatrix, Double>> getLimitedClassDependence() {
    	Map<Station, Function<JLineMatrix, Double>> gamma = new HashMap<Station, Function<JLineMatrix, Double>>();

    	for(Station station : this.stations) {
    		if (station.getLimitedClassDependence() != null)
    			gamma.put(station, station.getLimitedClassDependence());
    	}

    	if (gamma.size() > 0) {
    		for (Station station : this.stations) {
    			if (gamma.getOrDefault(station, null) == null) {
    				gamma.put(station, (nvec) -> {
    					return 1.0;
    				});
    			}
    		}
    		return gamma;
    	} else {
    		//Set to null which represents the cell(nstations, 0) in matlab
    		return null;
    	}
    }

    public RoutingStrategy getRoutingStrategyFromNodeAndClassPair(Node node, JobClass c) {
    	//Another approach is to use the last routing strategy
    	if (node.getOutput().getOutputStrategyByClass(c).size() == 0)
    		return RoutingStrategy.DISABLED;
    	RoutingStrategy res = null;
    	try {
            for (OutputStrategy outputStrategy : node.getOutputStrategies()) {
                if (outputStrategy.getJobClass().equals(c)) {
                	if (res == null)
                		res = outputStrategy.getRoutingStrategy();
                	else if (!res.equals(outputStrategy.getRoutingStrategy()))
                		throw new Exception("Routing Strategy inconsistent.");
                }
            }
    	} catch (Exception e) {
    		e.printStackTrace();
    		System.exit(1);
    	}

    	if (res != null)
    		return res;
    	else if (c instanceof OpenClass)
    		return RoutingStrategy.RAND;
    	else
    		return RoutingStrategy.DISABLED;
    }

    private JLineMatrix getIsStationArray() {
    	JLineMatrix isStation = new JLineMatrix(nodes.size(), 1, nodes.size());
    	for(int i = 0; i < nodes.size(); i++) {
    		if (nodes.get(i) instanceof Station)
    			isStation.set(i, 0, 1);
    	}
    	return isStation;
	}

    private JLineMatrix getIsStatefulArray(){
    	JLineMatrix isStateful = new JLineMatrix(nodes.size(), 1, nodes.size());
    	for(int i = 0; i < nodes.size(); i++) {
    		if (nodes.get(i) instanceof StatefulNode)
    			isStateful.set(i, 0, 1);
    	}
    	return isStateful;
    }

    public void refreshPriorities() {
    	int K = this.jobClasses.size();
    	JLineMatrix classprio = new JLineMatrix(1, K, K);
    	for(int i = 0; i < K; i++) {
    		classprio.set(0, i, jobClasses.get(i).priority);
    	}

    	if (this.sn != null)
    		sn.classprio = classprio;
    }

    public void refreshService(List<Integer> statSet, List<Integer> classSet) {
    	boolean[] status = refreshRates(statSet, classSet);
    	boolean hasSCVChanged = status[1];
    	boolean hasRateChanged = status[0];

    	if (hasSCVChanged) {
    		refreshServiceTypes(statSet, classSet);
    		refreshServicePhases(statSet, classSet);
    		refreshLST(statSet, classSet);
    	}

    	if (this.sn.sched == null) {
    		refreshScheduling();
    	} else {
    		for(Station station : this.stations) {
    			SchedStrategy schedStrategy = this.sn.sched.getOrDefault(station, null);
    			if (schedStrategy == SchedStrategy.SEPT || schedStrategy == SchedStrategy.LEPT) {
    				refreshScheduling();
    				break;
    			}
    		}
    	}
    }

	public boolean[] refreshRates(List<Integer> statSet, List<Integer> classSet){
    	boolean hasRateChanged = false;
    	boolean hasSCVChanged = false;
    	int M = this.stations.size();
    	int K = this.jobClasses.size();
    	JLineMatrix rates = null;
    	JLineMatrix scv = null;
    	JLineMatrix rates_orig = null;
    	JLineMatrix scv_orig = null;

    	if (statSet == null && classSet == null) {
        	statSet = new ArrayList<Integer>();
        	for(int i = 0; i < M; i++)
        		statSet.add(i);

        	classSet = new ArrayList<Integer>();
        	for(int i = 0; i < K; i++)
        		classSet.add(i);

        	rates = new JLineMatrix(M, K, M*K);
        	scv = new JLineMatrix(M, K, M*K);
        	scv.fill(Double.NaN);
        	hasRateChanged = true;
        	hasSCVChanged = true;
    	} else {
    		if (statSet == null) {
            	statSet = new ArrayList<Integer>();
            	for(int i = 0; i < M; i++)
            		statSet.add(i);
    		}

    		if (classSet == null) {
            	classSet = new ArrayList<Integer>();
            	for(int i = 0; i < K; i++)
            		classSet.add(i);
    		}

    		rates = this.sn.rates.clone();
    		scv = this.sn.scv.clone();
    		rates_orig = this.sn.rates.clone();
    		scv_orig = this.sn.scv.clone();
    	}
    	boolean hasOpenClasses = this.hasOpenClasses();
    	int sourceIdx = getIndexSourceNode();

    	for(Integer i : statSet) {
    		Station station = stations.get(i);
    		for(Integer r : classSet) {
    			if (station.getServer() instanceof ServiceTunnel) {
    				if (station instanceof Source) {
    					if (!((Source) station).containsJobClass(this.jobClasses.get(r))) {
    						rates.set(i, r, Double.NaN);
    						scv.set(i, r, Double.NaN);
    					} else {
    						Distribution distr = ((Source) station).getArrivalDistribution(this.jobClasses.get(r));
    						rates.set(i, r, distr.getRate());
    						scv.set(i, r, distr.getSCV());
    					}
    				} else if (station instanceof Join) {
    					rates.set(i, r, Double.POSITIVE_INFINITY);
    					scv.set(i, r, 0.0);
    				}
    				//Line 55-57 is ignored since no support of Place in JLINE
    			} else {
    				if (!hasOpenClasses || i != sourceIdx) {
    					if (!station.getServer().containsJobClass(this.jobClasses.get(r))) {
    						rates.set(i, r, Double.NaN);
    						scv.set(i, r, Double.NaN);
    					} else {
    						Distribution distr = station.getServer().getServiceDistribution(this.jobClasses.get(r));
    						rates.set(i, r, distr.getRate());
    						scv.set(i, r, distr.getSCV());
    					}
    				}
    			}
    		}
    	}

    	if (!hasRateChanged) {
    		JLineMatrix tmp = rates.sub(1, rates_orig);
    		tmp.abs();
    		if(tmp.elementSum() > 0)
    			hasRateChanged = true;
    	}

    	if (!hasSCVChanged) {
    		JLineMatrix tmp = scv.sub(1, scv_orig);
    		tmp.abs();
    		if(tmp.elementSum() > 0)
    			hasSCVChanged = true;
    	}

    	if (hasRateChanged) {
    		this.sn.rates = rates;
    	}

    	if (hasSCVChanged) {
    		this.sn.scv = scv;
    	}

    	return new boolean[] {hasRateChanged, hasSCVChanged};
    }

    private void refreshServiceTypes(List<Integer> statSet, List<Integer> classSet) {
		int M = this.stations.size();
		int K = this.jobClasses.size();
		Map<Station, Map<JobClass, ProcessType>> proctype = null;

		if (statSet == null && classSet == null) {
        	statSet = new ArrayList<Integer>();
        	for(int i = 0; i < M; i++)
        		statSet.add(i);

        	classSet = new ArrayList<Integer>();
        	for(int i = 0; i < K; i++)
        		classSet.add(i);

        	proctype = new HashMap<Station, Map<JobClass, ProcessType>>();
		} else if(statSet == null || classSet == null) {
			try { throw new Exception("refreshServiceTypes requires either both null or not null parameters"); } catch (Exception e) {e.printStackTrace();}
		} else {
			proctype = this.sn.proctype;
		}
		boolean hasOpenClass = this.hasOpenClasses();
		int sourceIdx = getIndexSourceNode();

		for(Integer i : statSet) {
			Station station = this.stations.get(i);
			Map<JobClass, ProcessType> map = new HashMap<JobClass, ProcessType>();
			for(Integer r : classSet) {
				JobClass jobclass = this.jobClasses.get(r);
				if (station.getServer() instanceof ServiceTunnel) {
					if (station instanceof Source) {
						Distribution distr = ((Source) station).getArrivalDistribution(jobclass);
						map.put(jobclass, getProcessType(distr));
					} else if (station instanceof Join) {
						map.put(jobclass, ProcessType.IMMEDIATE);
					}
				} else {
					if (!hasOpenClass || i != sourceIdx) {
						if (!station.getServer().containsJobClass(jobclass)) {
							map.put(jobclass, ProcessType.DISABLED);
						} else {
							Distribution distr = station.getServer().getServiceDistribution(jobclass);
							map.put(jobclass, getProcessType(distr));
						}
					}
				}
			}
			proctype.put(station, map);
		}

		if (this.sn != null)
			this.sn.proctype = proctype;
	}

    public ProcessType getProcessType(Distribution distr) {
    	if (distr instanceof Erlang) {
    		return ProcessType.ERLANG;
    	} else if (distr instanceof Exp) {
    		return ProcessType.EXP;
    	} else if (distr instanceof HyperExp) {
    		return ProcessType.HYPEREXP;
    	} else if (distr instanceof APH) {
    		return ProcessType.APH;
    	} else if (distr instanceof Coxian) {
    		return ProcessType.COXIAN;
    	} else if (distr instanceof PoissonDistribution) {
    		return ProcessType.POISSON;
    	} else if (distr instanceof BinomialDistribution) {
    		return ProcessType.BINOMIAL;
    	} else if (distr instanceof Immediate) {
    		return ProcessType.IMMEDIATE;
    	} else {
    		return ProcessType.DISABLED;
    	}
    }

    @SuppressWarnings("unchecked")
	public void refreshServicePhases(List<Integer> statSet, List<Integer> classSet) {
    	int M = this.stations.size();
    	int K = this.jobClasses.size();
    	Map<Station, Map<JobClass, JLineMatrix>> mu;
    	Map<Station, Map<JobClass, JLineMatrix>> phi;
    	JLineMatrix phases;

    	if (statSet != null && classSet != null && this.sn.mu != null && this.sn.phi != null && this.sn.phases != null) {
    		mu = this.sn.mu;
    		phi = this.sn.phi;
    		phases = this.sn.phases;
    	} else {
    		mu = new HashMap<Station, Map<JobClass, JLineMatrix>>();
    		phi = new HashMap<Station, Map<JobClass, JLineMatrix>>();
    		phases = new JLineMatrix(stations.size(), jobClasses.size(), stations.size() * jobClasses.size());
    		for(Station station : this.stations) {
    			mu.put(station, new HashMap<JobClass, JLineMatrix>());
    			phi.put(station, new HashMap<JobClass, JLineMatrix>());
    		}
    	}

		if (statSet == null) {
        	statSet = new ArrayList<Integer>();
        	for(int i = 0; i < M; i++)
        		statSet.add(i);
		}
		if (classSet == null) {
        	classSet = new ArrayList<Integer>();
        	for(int i = 0; i < K; i++)
        		classSet.add(i);
		}
		int sourceIdx = this.getIndexSourceNode();

		for(Integer i : statSet) {
			Station station = stations.get(i);
			Map<JobClass, JLineMatrix> mu_i = null;
			Map<JobClass, JLineMatrix> phi_i = null;
			if (i == sourceIdx) {
				List<Object> res = station.getMarkovianSourceRates();
				mu_i = (Map<JobClass, JLineMatrix>) res.get(1);
				phi_i = (Map<JobClass, JLineMatrix>) res.get(2);
			} else {
				//Line 56 - 63 is ignored since fork is not station
				if (station instanceof Join) {
					mu_i = new HashMap<JobClass, JLineMatrix>();
					phi_i = new HashMap<JobClass, JLineMatrix>();
					for(Integer r : classSet) {
						JLineMatrix mu_i_val = new JLineMatrix(1, 1, 1);
						JLineMatrix phi_i_val = new JLineMatrix(1, 1, 1);
						mu_i_val.set(0, 0, Double.NaN);
						phi_i_val.set(0, 0, Double.NaN);
						mu_i.put(this.jobClasses.get(r), mu_i_val);
						phi_i.put(this.jobClasses.get(r), phi_i_val);
					}
				} else {
					List<Object> res = station.getMarkovianServiceRates();
					mu_i = (Map<JobClass, JLineMatrix>) res.get(1);
					phi_i = (Map<JobClass, JLineMatrix>) res.get(2);
				}
			}

			mu.put(station, mu_i);
			phi.put(station, phi_i);
			for(Integer r : classSet) {
				double[] mu_val = mu_i.get(this.jobClasses.get(r)).nz_values;
				boolean flag = true;
				for(int idx = 0; idx < mu_val.length; idx++)
					flag = flag && Double.isNaN(mu_val[idx]);

				if (!flag)
					phases.set(i, r, mu_val.length);
			}
		}

		if (this.sn != null) {
			this.sn.mu = mu;
			this.sn.phi = phi;
			this.sn.phases = phases;
			this.sn.phasessz = new JLineMatrix(stations.size(), jobClasses.size(), stations.size() * jobClasses.size());
			this.sn.phaseshift = new JLineMatrix(0,0);
			for(int i = 0; i < stations.size(); i++) {
				for(int j = 0; j < jobClasses.size(); j++) {
					this.sn.phasessz.set(i,j,Math.max(1.0, phases.get(i, j)));
				}
			}
			JLineMatrix.concatColumns(new JLineMatrix(this.sn.phases.getNumRows(), 1), this.sn.phasessz.cumsumViaRow(), this.sn.phaseshift);
		}
		refreshMarkovianService();
    }

    public void refreshLST(List<Integer> statSet, List<Integer> classSet) {
		int M = this.stations.size();
		int K = this.jobClasses.size();
		Map<Station, Map<JobClass, Function<Double, Double>>> lst;

		if (statSet == null) {
        	statSet = new ArrayList<Integer>();
        	for(int i = 0; i < M; i++)
        		statSet.add(i);
		}
		if (classSet == null) {
        	classSet = new ArrayList<Integer>();
        	for(int i = 0; i < K; i++)
        		classSet.add(i);
		}

		if (this.sn.lst != null) {
			lst = this.sn.lst;
		} else {
        	lst = new HashMap<Station, Map<JobClass, Function<Double, Double>>>();
        	for(Station station : stations) {
        		lst.put(station, new HashMap<JobClass, Function<Double, Double>>());
        	}
		}
		int sourceIdx = this.getIndexSourceNode();

		for(Integer i : statSet) {
			Station station = this.stations.get(i);
			Map<JobClass, Function<Double, Double>> map = new HashMap<JobClass, Function<Double, Double>>();
			for(Integer r : classSet) {
				JobClass jobclass = this.jobClasses.get(r);
				if (i == sourceIdx) {
					Distribution distr = ((Source) station).getArrivalDistribution(jobclass);
					if (distr instanceof DisabledDistribution)
						map.put(jobclass, null);
					else
						map.put(jobclass, e -> distr.evalLST(e));
				} else {
					//line 45-46 is ignored since Fork is not station
					if (station instanceof Join)
						map.put(jobclass, null);
					else
						map.put(jobclass, e -> station.getServer().getServiceDistribution(jobclass).evalLST(e));
				}
			}
			lst.put(station, map);
		}

		if (this.sn != null)
			this.sn.lst = lst;
    }

    @SuppressWarnings("unchecked")
    //Current this function not implement the return value
	public void refreshMarkovianService() {
    	int M = this.stations.size();
    	int K = this.jobClasses.size();
    	Map<Station, Map<JobClass, Map<Integer, JLineMatrix>>> ph = new HashMap<Station, Map<JobClass, Map<Integer, JLineMatrix>>>();
    	for(int i = 0; i < M; i++)
    		ph.put(this.stations.get(i), new HashMap<JobClass, Map<Integer, JLineMatrix>>());
    	JLineMatrix phases = new JLineMatrix(M, K, M*K);
    	int sourceIdx = this.getIndexSourceNode();

    	for(int i = 0; i < M; i++) {
    		Station station = this.stations.get(i);
    		Map<JobClass, Map<Integer, JLineMatrix>> ph_i = new HashMap<JobClass, Map<Integer, JLineMatrix>>();
    		if (i == sourceIdx) {
    			ph_i = (Map<JobClass, Map<Integer, JLineMatrix>>) station.getMarkovianSourceRates().get(0);
    		} else {
    			if (station instanceof Join) {
    				Coxian coxian = new Coxian(new ArrayList<Double>(Arrays.asList(Double.NaN)), new ArrayList<Double>(Arrays.asList(Double.NaN)));
    				for(JobClass jobclass : this.jobClasses)
    					ph_i.put(jobclass, coxian.getRepres());
    			} else {
    				ph_i = (Map<JobClass, Map<Integer, JLineMatrix>>) station.getMarkovianServiceRates().get(0);
    			}
    		}
    		ph.put(station, ph_i);

        	for(int r = 0; r < K; r++) {
        		Map<Integer, JLineMatrix> ph_i_r = ph_i.get(this.jobClasses.get(r));
        		if (ph_i_r == null)
        			phases.set(i, r, 1.0);
        		else if (!(ph_i_r.get(0).hasNaN() || ph_i_r.get(1).hasNaN()))
        			phases.set(i, r, ph_i_r.get(0).numCols);
        		//Other situation set to 0 (The matrix initial value is 0)
        	}
    	}

    	if (this.sn != null) {
    		Map<Station, Map<JobClass, JLineMatrix>> pie = new HashMap<Station, Map<JobClass, JLineMatrix>>();
    		for(int i = 0; i < M; i++) {
    			Station station = this.stations.get(i);
    			Map<JobClass, JLineMatrix> pie_i = new HashMap<JobClass, JLineMatrix>();
    			for(int r = 0; r < K; r++) {
    				 JobClass jobclass = this.jobClasses.get(r);
    				 Map<Integer, JLineMatrix> map_ir = ph.get(station).get(jobclass);
    				 if (map_ir != null) {
    					 pie_i.put(jobclass, MAM.map_pie(new MAP(DConvertMatrixStruct.convert(map_ir.get(0), (DMatrixRMaj)null), DConvertMatrixStruct.convert(map_ir.get(1), (DMatrixRMaj)null))));
    				 } else {
    					 JLineMatrix tmp = new JLineMatrix(1,1,1);
    					 tmp.set(0,0,Double.NaN);
    					 pie_i.put(jobclass, tmp);
    				 }
    			}
    			pie.put(station, pie_i);
    		}

    		this.sn.proc = ph;
    		this.sn.pie = pie;
    		this.sn.phases = phases;
			this.sn.phasessz = new JLineMatrix(M, K, M*K);
			this.sn.phaseshift = new JLineMatrix(0,0);
			for(int i = 0; i < stations.size(); i++) {
				for(int j = 0; j < jobClasses.size(); j++) {
					this.sn.phasessz.set(i,j,Math.max(1.0, phases.get(i, j)));
				}
			}
			//self.sn.phasessz(self.sn.nodeToStation(self.sn.nodetype == NodeType.Join),:)=phases(self.sn.nodeToStation(self.sn.nodetype == NodeType.Join),:);
			//Not tested, since current JLine does not support Join Node
			if(this.sn.nodeToStation != null) {
				for(int i = 0; i < this.sn.nodeToStation.numCols; i++) {
					int idx = (int) this.sn.nodeToStation.get(0,i);
					if (idx != -1 && this.sn.nodetypes.get(i).equals(NodeType.Join)) {
						for(int j = 0; j < jobClasses.size(); j++)
							this.sn.phasessz.set(i, j, this.sn.phases.get(i, j));
					}
				}
			}
			JLineMatrix.concatColumns(new JLineMatrix(this.sn.phases.getNumRows(), 1), this.sn.phasessz.cumsumViaRow(), this.sn.phaseshift);
    	}
    }

    public void refreshScheduling() {
    	int M = this.stations.size();
    	int K = this.jobClasses.size();
    	Map<Station, SchedStrategy> sched = getStationScheduling();
    	JLineMatrix schedparam = new JLineMatrix(M, K, M*K);
    	int sourceIdx = this.getIndexSourceNode();

    	for(int i = 0; i < M; i++) {
    		Station station = this.stations.get(i);
    		if (sourceIdx == -1 || i != sourceIdx) {
    			if (!(station.getServer() instanceof ServiceTunnel)) {
    				Queue queue = (Queue)station;
    				boolean NaNFlag = false;
    				for(int r = 0; r < K; r++) {
    					double val = queue.getSchedStrategyPar(this.jobClasses.get(r));
    					if (Double.isNaN(val)) {
    						NaNFlag = true;
    						for(int idx = 0; idx < r; idx++)
    							schedparam.remove(i, idx);
    						break;
    					} else {
    						schedparam.set(i, r, val);
    					}
    				}

    				if (NaNFlag) {
    					//Current JLine not support SEPT and LEPT schedule strategy.
    					continue;
    				}
    			}
    		}
    	}

    	if (this.sn != null) {
    		this.sn.sched = sched;
    		this.sn.schedparam = schedparam;
    		//No schedid in JLine
    	}
    }

    public Map<Station, SchedStrategy> getStationScheduling(){
    	Map<Station, SchedStrategy> res = new HashMap<Station, SchedStrategy>();
    	for(Station station : this.stations) {
    		if (station.getNumberOfServers() == Integer.MAX_VALUE) {
    			res.put(station, SchedStrategy.INF);
    		} else {
    			if (station instanceof Source) {
    				res.put(station, SchedStrategy.EXT);
    			} else {
    				HasSchedStrategy s = (HasSchedStrategy) station;
    				res.put(station, s.getSchedStrategy());
    			}
    		}
    	}
    	return res;
    }

    public void refreshChains(boolean propagate) {
    	refreshRoutingMatrix(this.sn.rates);
    	JLineMatrix rt = this.sn.rt;
    	JLineMatrix rtnodes = this.sn.rtnodes;

    	JLineMatrix stateful = this.sn.isstateful.find();
    	int K = this.sn.nClasses;
    	if (this.csMatrix == null) {
    		JLineMatrix csmask = new JLineMatrix(K,K);
    		for(int r = 0; r < K; r++) {
    			for(int s = 0; s < K; s++) {
    				for(int isf = 0; isf < stateful.getNumRows(); isf++) {
    					for(int jsf = 0; jsf < stateful.getNumRows(); jsf++) {
    						if (rt.get(isf*K+r, jsf*K+s) > 0)
    							csmask.set(r, s, 1.0);
    					}
    				}
    			}
    		}

    		for(int isf = 0; isf < stateful.getNumRows(); isf++) {
    			int ind = (int) this.sn.statefulToNode.get(0, isf);
    			boolean isCS = (this.sn.nodetypes.get(ind) == NodeType.Cache) || (this.sn.nodetypes.get(ind) == NodeType.ClassSwitch);
    			for(int r = 0; r < K; r++) {
    				csmask.set(r, r, 1.0);
    				for(int s = 0; s < K; s++) {
    					if (r != s) {
    						if (isCS) {
    							ClassSwitcher classSwitcher = (ClassSwitcher) this.nodes.get(ind).getServer();
    							if (classSwitcher.applyCsFun(r, s) > 0)
    								csmask.set(r, s, 1.0);
    						}
    					}
    				}
    			}
    		}
    		this.sn.csmask = csmask;
    	} else {
    		this.sn.csmask = this.csMatrix;
    	}

    	if (((sn.refclass != null) && (!sn.refclass.isEmpty())) && (sn.refclass.length() < sn.nchains)) {
    		sn.refclass.expandMatrix(1,sn.nchains,sn.nchains);
    	}

    	if (propagate) {
    		//Compute visits
    		SN.snRefreshVisits(this.sn, this.sn.chains, rt, rtnodes);
    		//Call dependent capacity refresh
    		refreshCapacity();
    	}
    }

    public void refreshRoutingMatrix(JLineMatrix rates) {
    	if (rates == null)
    		throw new RuntimeException("refreshRoutingMatrix cannot retrieve station rates, pass them as an input parameters.");

    	int M = this.getNumberOfNodes();
    	int K = this.getNumberOfClasses();
    	JLineMatrix arvRates = new JLineMatrix(1, K, K);
    	List<Integer> stateful = this.getIndexStatefulNodes();

    	for(Integer i : this.getIndexOpenClasses()) {
    		arvRates.set(0, i, rates.get(this.getIndexSourceNode(), i));
    	}

    	GetRoutingMatrixReturnType res = getRoutingMatrix(arvRates, 4);
    	JLineMatrix rt = res.rt;
    	JLineMatrix rtnodes = res.rtnodes;
    	JLineMatrix linksmat = res.linksmat;
    	JLineMatrix chains = res.chains;

    	if (this.doChecks) {
    		outerloop:
    		for(JobClass jobclass : this.jobClasses) {
    			for(Map<JobClass, RoutingStrategy> nodeRoutingMap : this.sn.routing.values()) {
    				if (nodeRoutingMap.get(jobclass) != RoutingStrategy.DISABLED)
    					continue outerloop;
    			}
    			throw new RuntimeException("Routing strategy is unspecified at all nodes");
    		}
    	}
    	
    	boolean isStateDep = (JLineMatrix.extractColumn(this.sn.isstatedep, 2, null).getNonZeroLength() > 0);
    	Map<Integer, Map<Integer, Function<Pair<Map<Node, JLineMatrix>, Map<Node, JLineMatrix>>, Double>>> rtnodefuncell = 
    			new HashMap<Integer, Map<Integer, Function<Pair<Map<Node, JLineMatrix>, Map<Node, JLineMatrix>>, Double>>>();

    	if(isStateDep) {
    		for(int ind = 0; ind < M; ind++) {
    			final int ind_final = ind;
    			for(int jnd = 0; jnd < M; jnd++) {
    				final int jnd_final = jnd;
    				for(int r = 0; r < K; r++) {
    					final int r_final = r;
    					for(int s = 0; s < K; s++) {
    						final int s_final = s;
    						Map<Integer, Function<Pair<Map<Node, JLineMatrix>, Map<Node, JLineMatrix>>, Double>> map =
    								rtnodefuncell.getOrDefault(ind*K+r, new HashMap<Integer, Function<Pair<Map<Node, JLineMatrix>, Map<Node, JLineMatrix>>, Double>>());
    						if (this.sn.isstatedep.get(ind,2) > 0) {
    							switch (this.sn.routing.get(this.nodes.get(ind)).get(this.jobClasses.get(r))) {
    							case RROBIN:
    								map.put(jnd*K+s, (pair) -> sub_rr_wrr(ind_final, jnd_final, r_final, s_final, linksmat, pair.getLeft(), pair.getRight()));
    							case WRROBIN:
    								map.put(jnd*K+s, (pair) -> sub_rr_wrr(ind_final, jnd_final, r_final, s_final, linksmat, pair.getLeft(), pair.getRight()));
    							case JSQ:
    								map.put(jnd*K+s, (pair) -> sub_jsq(ind_final, jnd_final, r_final, s_final, linksmat, pair.getLeft(), pair.getRight()));
    							default:
    								map.put(jnd*K+s, (pair) -> rtnodes.get(ind_final*K+r_final, jnd_final*K+s_final));
    							}
    						} else {
    							map.put(jnd*K+s, (pair) -> rtnodes.get(ind_final*K+r_final, jnd_final*K+s_final));
    						}
        					rtnodefuncell.put(ind*K+r, map);
    					}
    				}
    			}
    		}
    	}

    	/* we now generate the node routing matrix for the given state and then
    	 * lump the states for non-stateful nodes so that run gives the routing
    	 *  table for stateful nodes only */
		List<Integer> statefulNodeClasses = new ArrayList<Integer>(); //Not using JLineMatrix for performance consideration
		for(int i = 0; i < stateful.size(); i++) {
			for(int j = 0; j < K; j++) {
				statefulNodeClasses.add(stateful.get(i)*K+j);
			}
		}

		Function<Pair<Map<Node, JLineMatrix>, Map<Node, JLineMatrix>>, JLineMatrix> rtfun = null;
		if (isStateDep) {
			rtfun = (pair) -> {
				JLineMatrix cellfunnodes = new JLineMatrix(M*K, M*K);
				for(int ind = 0; ind < M; ind++) {
	    			for(int jnd = 0; jnd < M; jnd++) {
	    				for(int r = 0; r < K; r++) {
	    					for(int s = 0; s < K; s++) {
	    						int row = ind*K+r;
	    						int col = jnd*K+s;
	    						double val = rtnodefuncell.get(row).get(col).apply(pair);
	    						if (val != 0)
	    							cellfunnodes.set(row, col, val);
	    					}
	    				}
	    			}
				}
				return CTMC.dtmc_stochcomp(cellfunnodes, statefulNodeClasses);
			};
		} else {
			rtfun = ((pair) -> CTMC.dtmc_stochcomp(rtnodes, statefulNodeClasses));
		}

		int nchains = chains.getNumRows();
		Map<Integer, JLineMatrix> inchain = new HashMap<Integer, JLineMatrix>();
		for(int c = 0; c < nchains; c++) {
			JLineMatrix chains_c = new JLineMatrix(1,chains.getNumCols());
			JLineMatrix.extract(chains, c, c+1, 0, chains.getNumCols(), chains_c, 0, 0);
			JLineMatrix chains_c_t = chains_c.find().transpose();
			inchain.put(c, chains_c_t);
		}

		this.sn.rt = rt;
		this.sn.rtnodes = rtnodes;
		this.sn.rtfun = rtfun;
		this.sn.chains = chains;
		this.sn.nchains = nchains;
		this.sn.inchain = inchain;
		for(int c = 0; c < nchains; c++) {
			JLineMatrix inchain_c = inchain.get(c);
			double val = this.sn.refstat.get((int) inchain_c.get(0,0), 0);
			for(int col = 1; col < inchain_c.getNumCols(); col++) {
				if (val != this.sn.refstat.get((int) inchain_c.get(0,col), 0))
					throw new RuntimeException("Classes within chain have different reference station");
			}
		}
    }

    public GetRoutingMatrixReturnType getRoutingMatrix(JLineMatrix arvRates, int returnVal) {

    	int idxSource, idxSink, I, K;
        List<Integer> idxOpenClasses;
        boolean hasOpen;
        JLineMatrix conn, NK;

        if (this.hasStruct) {
         idxSource = this.sn.nodetypes.indexOf(NodeType.Source);
         idxSink = this.sn.nodetypes.indexOf(NodeType.Sink);
         idxOpenClasses = new ArrayList<Integer>();
         for(int col = 0; col < this.sn.njobs.numCols; col++) {
          if (Double.isInfinite((this.sn.njobs.get(0, col))))
           idxOpenClasses.add(col);
         }
         hasOpen = idxOpenClasses.isEmpty();
         if ((arvRates == null) || arvRates.isEmpty()) {
             arvRates = new JLineMatrix(1, idxOpenClasses.size(), idxOpenClasses.size());
             for(int i = 0; i < idxOpenClasses.size(); i++) {
              arvRates.set(0, i, this.sn.rates.get(idxSource, idxOpenClasses.get(i)));
             }
         }
         conn = this.sn.connmatrix;
         I = this.sn.nNodes;
         K = this.sn.nClasses;
         NK = this.sn.njobs;
        } else {
         idxSource = this.getIndexSourceNode();
         idxSink = this.getIndexSinkNode();
         idxOpenClasses = this.getIndexOpenClasses();
         conn = this.getConnectionMatrix();
         hasOpen = this.hasOpenClasses();
         I = this.getNumberOfNodes();
         K = this.getNumberOfClasses();
         NK = this.getNumberOfJobs().transpose();
         
         if (this.sn == null)
          this.sn = new NetworkStruct();
         this.sn.connmatrix = conn;
            sn.routing = new HashMap<Node, Map<JobClass, RoutingStrategy>>();
            for(Node node : this.nodes) {
             Map<JobClass, RoutingStrategy> map = new HashMap<JobClass, RoutingStrategy>();
             for(JobClass jobclass : this.jobClasses) {
              map.put(jobclass, getRoutingStrategyFromNodeAndClassPair(node, jobclass));
             }
             sn.routing.put(node, map);
            }
        }

    	JLineMatrix rtnodes = new JLineMatrix(I*K, I*K, 0);
    	JLineMatrix chains = null;

    	// The first loop considers the class at which a job enters the
    	for(int i = 0; i < I; i++) {
    		Node node = this.nodes.get(i);
    		if (node.getOutput() instanceof Forker) {
    			//Line 1294-1314 not test
    			for(int j = 0; j < I; j++) {
    				for(int k = 0; k < K; k++) {
    					if (conn.get(i,j) > 0) {
    						JobClass jobclass = this.jobClasses.get(k);    						
    						List<OutputStrategy> outputStrategy_k = node.getOutput().getOutputStrategyByClass(jobclass);
    						if (outputStrategy_k.size() > 0)
    							rtnodes.set(i*K+k, j*K+k, 1.0/outputStrategy_k.size());
    						else
    							rtnodes.set(i*K+k, j*K+k, 1.0);
    						if (this.sn.routing.get(node).get(jobclass) == RoutingStrategy.PROB) {
    							//Check the number of outgoing links
    							int sum = (int) conn.sumRows(i);
    							//Fork must have all the output strategy towards all outgoing links.
    							if (outputStrategy_k.size() != sum)
    								throw new RuntimeException("Fork must have all the output strategy towards all outgoing links.");
    							//Fork must have 1.0 routing probability towards all outgoing links.
    							for(OutputStrategy ops : outputStrategy_k) {
    								if (ops.getProbability() != 1.0)
    									throw new RuntimeException("Fork must have 1.0 routing probability towards all outgoing links.");
    							}
    						}
    					}
    				}
    			}
    		} else {
    			boolean isSink_i = (i == idxSink);
    			boolean isSource_i = (i == idxSource);
    			for(int k = 0; k < K; k++) {
    				JobClass jobclass = this.jobClasses.get(k);
    				List<OutputStrategy> outputStrategy_k = node.getOutput().getOutputStrategyByClass(jobclass);
    				switch (this.sn.routing.get(node).get(jobclass)) {
	    				case PROB:
	    					if (Double.isInfinite((NK.get(0, k))) || !isSink_i){
	    						for(OutputStrategy ops : outputStrategy_k) {
	    							int j = ops.getDestination().getNodeIdx();
	    							rtnodes.set(i*K+k, j*K+k, ops.getProbability());
	    						}
	    					}
	    					break;
	    				//Not tested the following situation
	    				case DISABLED:
							double sum = conn.sumRows(i);
	    					for(int j = 0; j < I; j++) {
	    						if (conn.get(i, j) > 0)
	    							rtnodes.set(i*K+k, j*K+k, 1.0/sum);
	    					}
	    					break;
	    				case RAND:
	    				case RROBIN:
	    				case WRROBIN:
	    				case JSQ:
	    					if (Double.isInfinite((NK.get(0, k)))) {
	    						sum = conn.sumRows(i);
		    					for(int j = 0; j < I; j++) {
		    						if (conn.get(i, j) > 0)
		    							rtnodes.set(i*K+k, j*K+k, 1.0/sum);
		    					}
	    					} else if (!isSource_i && ! isSink_i) {
	    						JLineMatrix connectionClosed = conn.clone();
	    						if (connectionClosed.get(i, idxSink) > 0)
	    							connectionClosed.remove(i, idxSink);
	    						sum = connectionClosed.sumRows(i);
	    						for(int j = 0; j < I; j++) {
	    							if (connectionClosed.get(i, j) > 0)
	    								rtnodes.set(i*K+k, j*K+k, 1.0/sum);
	    						}
	    					}
	    					break;
    					default:
    						for(int j = 0; j < I; j++) {
    							if (conn.get(i, j) > 0)
    								rtnodes.set(i*K+k, j*K+k, Distribution.zeroRn);
    						}
    				}

    			}
    		}
    	}

    	// The second loop corrects the first one at nodes that change the class of the job in the service section.
    	for(int i = 0; i < I; i++) {
    		Node node = this.nodes.get(i);
    		if (node.getServer() instanceof StatelessClassSwitcher) {
    			JLineMatrix Pi = new JLineMatrix(K, rtnodes.getNumCols(), 0);
    			JLineMatrix.extract(rtnodes, i*K, (i+1)*K, 0, rtnodes.getNumCols(), Pi, 0, 0);
    			JLineMatrix Pcs = new JLineMatrix(K,K);
    			ClassSwitcher classSwitcher = (ClassSwitcher) node.getServer();
    			for(int r = 0; r < K; r++) {
    				for(int s = 0; s < K; s++) {
    					Pcs.set(r, s, classSwitcher.applyCsFun(r,s));
    				}
    			}

    			for(int jnd = 0; jnd < I; jnd++) {
    				JLineMatrix Pij = new JLineMatrix(K, K, K*K);
    				JLineMatrix.extract(Pi, 0, K, K*jnd, K*(jnd+1), Pij, 0, 0);
    				//diag(Pij)'
    				JLineMatrix diagPij = new JLineMatrix(1, K, K);
    				JLineMatrix.extractDiag(Pij, diagPij);
    				//repmat(diag(Pij)', K, 1)
    				JLineMatrix repmatPij = diagPij.repmat(K, 1);
    				//rtnodes(((ind-1)*K+1) : ((ind-1)*K+K),(jnd-1)*K+(1:K)) = Pcs.*repmat(diag(Pij)',K,1);
    				for(int row = 0; row < K; row++) {
    					for(int col = 0; col < K; col++) {
    						double val = repmatPij.get(row, col) * Pcs.get(row, col);
    						if (val != 0)
    							rtnodes.set(i*K+row, jnd*K+col, val);
    						else
    							rtnodes.remove(i*K+row, jnd*K+col);
    					}
    				}
    			}
    		} else if (node.getServer() instanceof StatefulClassSwitcher) {
    			//This part of code is ignored since JLine not support StatefulClassSwitcher and CacheClassSwitcher
    		}
    	}

        // ignore all chains containing a Pnodes column that sums to 0, since these are classes that cannot arrive to the node unless this column belongs to the source
    	JLineMatrix sumRtnodesCols = rtnodes.sumCols();
		Set<Integer> colsToIgnore = new HashSet<Integer>();
		for(int col = 0; col < sumRtnodesCols.getNumCols(); col++) {
			if (sumRtnodesCols.get(col) == 0)
				colsToIgnore.add(col);
		}
		if (hasOpen) {
			for(int i = idxSource*K; i < (idxSource+1)*K; i++)
				colsToIgnore.remove(i);
		}

	    /* We route back from the sink to the source. Since open classes
	     * have an infinite population, if there is a class switch QN
	     * with the following chains
	     * Source -> (A or B) -> C -> Sink
	     * Source -> D -> Sink
	     * We can re-route class C into the source either as A or B or C.
	     * We here re-route back as C and leave for the chain analyzer
	     * to detect that C is in a chain with A and B and change this
	     * part.
	     */
		if (this.csMatrix == null) {
			JLineMatrix param = rtnodes.add(1, rtnodes.transpose());
			Set<Set<Integer>> chainCandidates = UTIL.weaklyConnect(param, colsToIgnore);
			
			JLineMatrix chainstmp = new JLineMatrix(chainCandidates.size(), K);
			Iterator<Set<Integer>> it = chainCandidates.iterator();
			int tmax = 0;
			while (it.hasNext()) {
				Set<Integer> set = it.next();
				if (set.size() > 1) {
					for(Integer num : set)
						chainstmp.set(tmax, num%K, 1.0);
					tmax++;
				}
			}
			chains = new JLineMatrix(tmax, K);
			JLineMatrix.extract(chainstmp, 0, tmax, 0, chainstmp.getNumCols(), chains, 0, 0);
		} else {
			Set<Set<Integer>> chainCandidates = UTIL.weaklyConnect(this.csMatrix, colsToIgnore);

			chains = new JLineMatrix(chainCandidates.size(), K);
			Iterator<Set<Integer>> it = chainCandidates.iterator();
			int tmax = 0;
			while (it.hasNext()) {
				Set<Integer> set = it.next();
				for(Integer num : set)
					chains.set(tmax, num, 1.0);
				tmax++;
			}
		}
		this.sn.chains = chains;

	//Split chains block
		List<Integer> splitChains = new ArrayList<Integer>();
		JLineMatrix sumCol = chains.sumCols();
		for(int i = 0; i < sumCol.numCols; i++) {
			if (sumCol.get(i) > 1) {
				//rows = find(chains(:,col));
				List<Integer> rows = new ArrayList<Integer>();
				for(int j = 0; j < chains.getNumRows(); j++) {
					if (chains.get(j, i) == 1)
						rows.add(j);
				}
				
				if (rows.size() > 1) {
					int row = rows.get(0);
					for(int j = 1; j < rows.size(); j++) {
						//chains(rows(1),:) = chains(row(1),:) | chains(r,:);
						for(int k = 0; k < chains.getNumCols(); k++) {
							if (chains.get(row, k) == 1 || chains.get(rows.get(j), k) == 1)
								chains.set(row, k, 1);
							else
								chains.set(row, k, 0);
						}
						splitChains.add(j);
					}
				}
			}
		}
		
		if (splitChains.size() > 0) {
			JLineMatrix newChains = new JLineMatrix(chains.numRows - splitChains.size(), chains.numCols);
			for(int i = 0; i < chains.numRows; i++) {
				if (!splitChains.contains(i)) {
					for(int j = 0; j < chains.numCols; j++)
						newChains.set(i, j, chains.get(i, j));
				}
			}
			chains = newChains;
		}
		
	    /* We now obtain the routing matrix P by ignoring the non-stateful
	     * nodes and calculating by the stochastic complement method the
	     * correct transition probabilities, that includes the effects
	     * of the non-stateful nodes (e.g., ClassSwitch)
	     */
		List<Integer> statefulNodes = this.getIndexStatefulNodes();
		List<Integer> statefulNodeClasses = new ArrayList<Integer>(); //Not using JLineMatrix for performance consideration
		for(int i = 0; i < statefulNodes.size(); i++) {
			for(int j = 0; j < K; j++) {
				statefulNodeClasses.add(statefulNodes.get(i)*K+j);
			}
		}

	    /* this routes open classes back from the sink into the source
	     * it will not work with non-renewal arrivals as it chooses in which open
	     * class to reroute a job with probability depending on the arrival rates
	     */
		if (hasOpen) {
			arvRates.removeNaN();
			for(int i = 0; i < idxOpenClasses.size(); i++) {
				//s_chain = find(chains(:,s));
				JLineMatrix s_chain = new JLineMatrix(chains.getNumRows(), 1, 0);
				JLineMatrix.extract(chains, 0, chains.getNumRows(), idxOpenClasses.get(i), idxOpenClasses.get(i)+1, s_chain, 0, 0);
				s_chain = s_chain.find();
				//others_in_chain = find(chains(s_chain,:));
				JLineMatrix others_in_chain = new JLineMatrix(s_chain.getNumRows(), chains.getNumCols(), 0);
				for(int row = 0; row < s_chain.getNumRows(); row++)
					JLineMatrix.extract(chains, (int)s_chain.get(row, 0), (int)s_chain.get(row, 0) + 1, 0, chains.getNumCols(), others_in_chain, row, 0);
				others_in_chain = others_in_chain.find();
				//arvRates(others_in_chain)/sum(arvRates(others_in_chain))
				JLineMatrix arv_rates_others_in_chain = new JLineMatrix(1, others_in_chain.getNumRows(), 0);
				for(int row = 0; row < others_in_chain.getNumRows(); row++)
					arv_rates_others_in_chain.set(0, row, arvRates.get(0, (int) others_in_chain.get(row,0)));
				arv_rates_others_in_chain.divide(arv_rates_others_in_chain.sumRows(0), arv_rates_others_in_chain, true);
				//repmat(arvRates(others_in_chain)/sum(arvRates(others_in_chain)),length(others_in_chain),1);
				JLineMatrix rep_res = arv_rates_others_in_chain.repmat(others_in_chain.getNumRows(), 1);
				//rtnodes((idxSink-1)*K+others_in_chain,(idxSource-1)*K+others_in_chain) = rep_res
				for(int row1 = 0; row1 < others_in_chain.getNumRows(); row1++) {
					for(int row2 = 0; row2 < others_in_chain.getNumRows(); row2++) {
						rtnodes.set(idxSink*K + (int) others_in_chain.get(row1, 0), idxSource*K + (int) others_in_chain.get(row2, 0), rep_res.get(row1, row2));
					}
				}
			}
		}

		/* Hide the nodes that are not stateful */
		JLineMatrix rt = CTMC.dtmc_stochcomp(rtnodes, statefulNodeClasses);
		this.sn.rt = rt;

		/* Compute the optional outputs */
    	Map<JobClass, Map<JobClass, JLineMatrix>> rtNodesByClass = null;
		if (returnVal >= 5) {
			rtNodesByClass = new HashMap<JobClass, Map<JobClass, JLineMatrix>>();
			for(int r = 0; r < K; r++) {
				Map<JobClass, JLineMatrix> map = new HashMap<JobClass, JLineMatrix>();
				for(int s = 0; s < K; s++) {
					JLineMatrix matrix = new JLineMatrix(I,I,I*I);
					for(int i = 0; i < I; i++) {
						for(int j = 0; j < I; j++) {
							matrix.set(i, j, rtnodes.get(i*K+s, j*K+r));
						}
					}
					map.put(this.jobClasses.get(s), matrix);
				}
				rtNodesByClass.put(this.jobClasses.get(r), map);
			}
		}

		Map<Node, Map<Node, JLineMatrix>> rtNodesByStation = null;
		if (returnVal >= 6) {
			rtNodesByStation = new HashMap<Node, Map<Node, JLineMatrix>>();
			for(int i = 0; i < I; i++) {
				Map<Node, JLineMatrix> map = new HashMap<Node, JLineMatrix>();
				for(int j = 0; j < I; j++) {
					JLineMatrix matrix = new JLineMatrix(K,K,K*K);
					for(int r = 0; r < K; r++) {
						for(int s = 0; s < K; s++) {
							matrix.set(r, s, rtnodes.get(i*K+s, j*K+r));
						}
					}
					map.put(this.nodes.get(j), matrix);
				}
				rtNodesByStation.put(this.nodes.get(i), map);
			}
		}

		//Return
		return new GetRoutingMatrixReturnType(rt, rtnodes, this.sn.connmatrix, chains, rtNodesByClass, rtNodesByStation);
    }

    public void refreshCapacity() {
    	int M = this.stations.size();
    	int K = this.jobClasses.size();
    	int C = this.sn.nchains;

    	JLineMatrix classcap = new JLineMatrix(M,K);
    	classcap.fill(Double.POSITIVE_INFINITY);
    	JLineMatrix chaincap = new JLineMatrix(M,K);
    	chaincap.fill(Double.POSITIVE_INFINITY);
    	JLineMatrix capacity = new JLineMatrix(M,1);
    	//Something wrong with dropRule in LINE
    	Map<Station, Map<JobClass, DropStrategy>> dropRule = new HashMap<Station, Map<JobClass, DropStrategy>>();
    	for(Station station : this.stations) {
    		Map<JobClass, DropStrategy> dropRule_station = new HashMap<JobClass, DropStrategy>();
    		for(JobClass jobclass : this.jobClasses)
    			dropRule_station.put(jobclass, DropStrategy.WaitingQueue);
    		dropRule.put(station, dropRule_station);
    	}

    	JLineMatrix njobs = this.sn.njobs;
    	JLineMatrix rates = this.sn.rates;

    	for(int c = 0; c < C; c++) {
    		JLineMatrix inchain_c = this.sn.inchain.get(c);
    		//chainCap = sum(njobs(inchain));
    		double chainCap = 0;
    		for(int idx = 0; idx < inchain_c.length(); idx++)
    			chainCap += njobs.get(0, (int) inchain_c.get(0, idx));

    		for(int idx = 0; idx < inchain_c.length(); idx++) {
    			int r = (int) inchain_c.get(idx);
				JobClass jobclass = this.jobClasses.get(r);
    			for(int i = 0; i < M; i++) {
    				Station station = this.stations.get(i);
    				if (!(station instanceof Source)) {
    					dropRule.get(station).put(jobclass, station.getDropRule(jobclass));
    				}
    				if (Double.isNaN(rates.get(i,r))) {
    					classcap.remove(i, r);
    					chaincap.remove(i, c);
    				} else {
    					classcap.set(i, r, chainCap);
    					chaincap.set(i, c, chainCap);
    					if (station.getClassCap(jobclass) >= 0)
    						classcap.set(i, r, Math.min(classcap.get(i,r), station.getClassCap(jobclass)));
    					if (station.getCap() >= 0)
    						classcap.set(i, r, Math.min(classcap.get(i,r), station.getCap()));
    				}
    			}
    		}
    	}

    	for(int i = 0; i < M; i++)
    		capacity.set(i, 0, Math.min(chaincap.sumRows(i), classcap.sumRows(i)));

    	this.sn.cap = capacity;
    	this.sn.classcap = classcap;
    	this.sn.dropRule = dropRule;
    }

    public void refreshLocalVars() {
    	int R = this.jobClasses.size();
    	int I = this.nodes.size();
    	JLineMatrix nvars = new JLineMatrix(I, 2*R+1);
    	Map<Node, NodeParam> nodeparam = new HashMap<Node, NodeParam>();

    	for(int i = 0; i < I; i++) {
    		Node node = this.nodes.get(i);
    		NodeParam param = new NodeParam();
    		switch (this.sn.nodetypes.get(i)) {
	    		case Cache:
	    			throw new RuntimeException("Cache node is not supported in JLINE");
	    		case Fork:
	    			param.fanout = ((Forker) node.getOutput()).taskPerLink;
	    			break;
	    		case Join:
	    			Joiner joiner = (Joiner)node.getInput();
	    			param.joinStrategy = joiner.joinStrategy;
	    			param.fanIn = joiner.joinRequired;
	    			break;
	    		case Logger:
	    			throw new RuntimeException("Logger node is not supported in JLINE");
	    		case Queue:
	    		case Delay:
	    		case Transition:
//	    			for(int r = 0; r < R; r++) {
//	    				Distribution distrb = node.getServer().getServiceDistribution(this.jobClasses.get(r));
//	    				if (distrb instanceof MAP)
//	    					throw new RuntimeException("MAP is not supported in JLINE");
//	    				else if (distrb instanceof Replayer)
//	    					throw new RuntimeException("Replayer is not supported in JLINE");
//	    			}
	    			break;
				default:
					break;
    		}

        	for(int r = 0; r < R; r++) {
        		JobClass jobclass = this.jobClasses.get(r);
        		switch (this.sn.routing.get(node).get(jobclass)) {
		    		case KCHOICES:
		    			throw new RuntimeException("Routing Strategy KCHOICES is not supported in JLINE");
		    		case WRROBIN:
		    			param.weights = new HashMap<JobClass, JLineMatrix>();
		    			param.outlinks = new HashMap<JobClass, JLineMatrix>();
		    			nvars.set(i, R+r, nvars.get(i,R+r) + 1);

		    			//varsparam{ind}{r}.weights = zeros(1,self.sn.nnodes);
		    			param.weights.put(jobclass, new JLineMatrix(1, this.sn.nNodes));
		    			//varsparam{ind}{r}.outlinks = find(self.sn.connmatrix(ind,:));
		    			JLineMatrix conn_i = new JLineMatrix(0,0);
		    			JLineMatrix.extractRows(this.sn.connmatrix, i, i+1, conn_i);
		    			JLineMatrix conn_i_transpose = conn_i.find().transpose();
		    			param.outlinks.put(jobclass, conn_i_transpose);

		    			List<OutputStrategy> outputStrategy_r = node.getOutput().getOutputStrategyByClass(jobclass);
		    			for(int c = 0; c < outputStrategy_r.size(); c++) {
		    				Node destination = outputStrategy_r.get(c).getDestination();
		    				Double weight = outputStrategy_r.get(c).getProbability();
		    				param.weights.get(jobclass).set(0, destination.getNodeIdx(), weight);
		    			}
		    			break;
		    		case RROBIN:
		    			param.outlinks = new HashMap<JobClass, JLineMatrix>();
		    			nvars.set(i, R+r, nvars.get(i,R+r) + 1);

		    			//varsparam{ind}{r}.outlinks = find(self.sn.connmatrix(ind,:));
		    			conn_i = new JLineMatrix(0,0);
		    			JLineMatrix.extractRows(this.sn.connmatrix, i, i+1, conn_i);
		    			conn_i_transpose = conn_i.find().transpose();
		    			param.outlinks.put(jobclass, conn_i_transpose);
		    			break;
        			default:
        				break;
        		}
        	}
        	nodeparam.put(node, param);
    	}

    	if (this.sn != null) {
    		this.sn.nvars = nvars;
    		this.sn.nodeparam = nodeparam;
    	}
    }

    public void refreshSync() {
    	int local = this.nodes.size();
    	int nclasses = this.sn.nClasses;
    	Map<Integer, Sync> sync = new HashMap<Integer, Sync>();	//Index starts from 0
    	Map<Node, JLineMatrix> emptystate = new HashMap<Node, JLineMatrix>();
    	for(Node node : this.nodes)
    		emptystate.put(node, new JLineMatrix(0,0));

    	JLineMatrix rtmask;
    	if(this.sn.isstatedep.getNonZeroLength() > 0) {
    		rtmask = this.sn.rtfun.apply(new Pair<Map<Node, JLineMatrix>, Map<Node, JLineMatrix>>(emptystate, emptystate));
    	} else {
    		//ceil(self.sn.rt);
    		JLineMatrix rt = this.sn.rt;
    		rtmask = new JLineMatrix(rt.getNumRows(), rt.getNumCols());
    		for(int colIdx = 0; colIdx < rt.numCols; colIdx++) {
    			int col1 = rt.col_idx[colIdx];
    			int col2 = rt.col_idx[colIdx+1];

    			for(int i = col1; i < col2; i++) {
    				int rowIdx = rt.nz_rows[i];
    				double value = rt.nz_values[i];
    				rtmask.set(rowIdx, colIdx, Math.ceil(value));
    			}
    		}
    	}

    	for(int i = 0; i < sn.nNodes; i++) {
    		for(int r = 0; r < nclasses; r++) {
    			if (sn.isstation.get(i, 0) > 0 && sn.phases.get((int)sn.nodeToStation.get(0, i),r) > 1) {
    				Sync synct = new Sync();
    				synct.active.put(0, new NetworkEvent(EventType.PHASE, i, r, Double.NaN, new JLineMatrix(0,0), Double.NaN, Double.NaN));
    				synct.passive.put(0, new NetworkEvent(EventType.LOCAL, local, r, 1.0, new JLineMatrix(0,0), Double.NaN, Double.NaN));
    				sync.put(sync.size(), synct);
    			}
    			if (sn.isstateful.get(i, 0) > 0) {
    				//Line 24 - 29 is ignored since cache node
    				int isf = (int) sn.nodeToStateful.get(0, i);
    				for(int j = 0; j < sn.nNodes; j++) {
    					if (sn.isstateful.get(j, 0) > 0) {
    						int jsf = (int) sn.nodeToStateful.get(0,j);
    						for(int s = 0; s < nclasses; s++) {
    							double p = rtmask.get(isf*nclasses+r, jsf*nclasses+s);
    							if (p > 0) {
    								Sync synct = new Sync();
    								synct.active.put(0, new NetworkEvent(EventType.DEP, i, r, Double.NaN, new JLineMatrix(0,0), Double.NaN, Double.NaN));
    								switch (sn.routing.get(this.nodes.get(i)).get(this.jobClasses.get(s))) {
	    								case RROBIN:
	    								case WRROBIN:
	    								case JSQ:
	    									final int i_final = i, j_final = j, r_final = r, s_final = s;
	    									synct.passive.put(0, new NetworkEvent(EventType.ARV, j, s,
	    											((pair) -> sn.rtfun.apply(pair).get(i_final*nclasses+r_final, j_final*nclasses+s_final)),
	    											new JLineMatrix(0,0), Double.NaN, Double.NaN));
	    									break;
	    								default:
    										synct.passive.put(0, new NetworkEvent(EventType.ARV, j, s,
    												sn.rt.get(i*nclasses+r, j*nclasses+s),
    												new JLineMatrix(0,0), Double.NaN, Double.NaN));
    								}
    								sync.put(sync.size(), synct);
    							}
    						}
    					}
    				}
    			}
    		}
    	}

    	if (this.sn != null)
    		this.sn.sync = sync;
    }

    public double sub_rr_wrr(int ind, int jnd, int r, int s, JLineMatrix linksmat, Map<Node, JLineMatrix> state_before, Map<Node, JLineMatrix> state_after) {
    	int R = this.sn.nClasses;
    	int isf = (int) this.sn.nodeToStateful.get(0, ind);
    	Node statefulNode = this.getStatefulNodeFromIndex(isf);
    	if (!state_before.containsKey(statefulNode)) {
    		return Math.min(linksmat.get(ind, jnd), 1.0);
    	} else {
    		if (r == s) {
    			JLineMatrix jm = state_after.get(statefulNode);
    			return ((int)jm.get(jm.getNumCols() - 1 - R + r) == jnd) ? 1.0 : 0.0;
    		} else {
    			return 0.0;
    		}
    	}
    }

    public double sub_jsq(int ind, int jnd, int r, int s, JLineMatrix linksmat, Map<Node, JLineMatrix> state_before, Map<Node, JLineMatrix> state_after) {
    	int isf = (int) this.sn.nodeToStateful.get(0, ind);
    	Node statefulNode = this.getStatefulNodeFromIndex(isf);
    	if (!state_before.containsKey(statefulNode)) {
    		return Math.min(linksmat.get(ind, jnd), 1.0);
    	} else {
    		if (r == s) {
    			JLineMatrix n = new JLineMatrix(1, this.sn.nNodes);
    			n.fill(Double.POSITIVE_INFINITY);
    			for(int knd = 0; knd < this.sn.nNodes; knd++) {
    				if (linksmat.get(ind, knd) > 0) {
    					Node statefulNode_knd = this.getStatefulNodeFromIndex((int) this.sn.nodeToStateful.get(0,knd));
    					n.set(0, knd, NetworkState.toMarginal(this.sn, knd, state_before.get(statefulNode), null, null, null, null, null).ni.get(0,0));
    				}
    			}
				double min = n.elementMin();
				if (n.get(jnd) == min) 
					return 1.0 / n.count(min);
				else
					return 0.0;
    		} else {
    			return 0.0;
    		}
    	}
    }

    public List<Map<Station, Map<JobClass, Boolean>>> getAvgHandles(){
		int M = this.stations.size();
		int K = this.jobClasses.size();

		JLineMatrix isSource = new JLineMatrix(M, 1);
		JLineMatrix isSink = new JLineMatrix(M, 1);
		JLineMatrix hasServiceTunnel = new JLineMatrix(M, 1);
		JLineMatrix isServiceDefined = new JLineMatrix(M, K);
		isServiceDefined.fill(1.0);
		
		for(int i = 0; i < M; i++) {
			if (this.stations.get(i) instanceof Source)
				isSource.set(i, 0, 1);
			if (((Node)this.stations.get(i)) instanceof Sink)
				isSink.set(i, 0, 1);

			if (this.stations.get(i).getServer() instanceof ServiceTunnel)
				hasServiceTunnel.set(i, 0, 1);
			else {
				for(int r = 0; r < K; r++) {
					if(!this.stations.get(i).getServer().containsJobClass(this.jobClasses.get(r)))
						isServiceDefined.remove(i, r);
				}
			}
		}

		//Calculate Q
		Map<Station, Map<JobClass, Boolean>> Q = new HashMap<Station, Map<JobClass, Boolean>>();
		for(int i = 0; i < M; i++) {
			Map<JobClass, Boolean> map = new HashMap<JobClass, Boolean>();
			for(int r = 0; r < K; r++) {
				if (isSource.get(i,0) > 0)
					map.put(this.jobClasses.get(r), true);
				else if (isSink.get(i,0) > 0)
					map.put(this.jobClasses.get(r), true);
				else if (hasServiceTunnel.get(i,0) == 0 && isServiceDefined.get(i, r) == 0)
					map.put(this.jobClasses.get(r), true);
				else
					map.put(this.jobClasses.get(r), false);
			}
			Q.put(this.stations.get(i), map);
		}

		//Calculate U
		Map<Station, Map<JobClass, Boolean>> U = new HashMap<Station, Map<JobClass, Boolean>>();
		for(int i = 0; i < M; i++) {
			Map<JobClass, Boolean> map = new HashMap<JobClass, Boolean>();
			for(int r = 0; r < K; r++) {
				if (isSource.get(i,0) > 0)
					map.put(this.jobClasses.get(r), true);
				else if (isSink.get(i,0) > 0)
					map.put(this.jobClasses.get(r), true);
				else if (this.stations.get(i) instanceof Join)
					map.put(this.jobClasses.get(r), true);
				else if (hasServiceTunnel.get(i,0) == 0 && isServiceDefined.get(i, r) == 0)
					map.put(this.jobClasses.get(r), true);
				else
					map.put(this.jobClasses.get(r), false);
			}
			U.put(this.stations.get(i), map);
		}

		//Calculate R
		Map<Station, Map<JobClass, Boolean>> R = new HashMap<Station, Map<JobClass, Boolean>>();
		for(int i = 0; i < M; i++) {
			Map<JobClass, Boolean> map = new HashMap<JobClass, Boolean>();
			for(int r = 0; r < K; r++) {
				if (isSource.get(i,0) > 0)
					map.put(this.jobClasses.get(r), true);
				else if (isSink.get(i,0) > 0)
					map.put(this.jobClasses.get(r), true);
				else if (hasServiceTunnel.get(i,0) == 0 && isServiceDefined.get(i, r) == 0)
					map.put(this.jobClasses.get(r), true);
				else
					map.put(this.jobClasses.get(r), false);
			}
			R.put(this.stations.get(i), map);
		}

		//Calculate T
		Map<Station, Map<JobClass, Boolean>> T = new HashMap<Station, Map<JobClass, Boolean>>();
		for(int i = 0; i < M; i++) {
			Map<JobClass, Boolean> map = new HashMap<JobClass, Boolean>();
			for(int r = 0; r < K; r++) {
				if (hasServiceTunnel.get(i,0) == 0 && isServiceDefined.get(i, r) == 0)
					map.put(this.jobClasses.get(r), true);
				else
					map.put(this.jobClasses.get(r), false);
			}
			T.put(this.stations.get(i), map);
		}

		//Calculate A
		Map<Station, Map<JobClass, Boolean>> A = new HashMap<Station, Map<JobClass, Boolean>>();
		for(int i = 0; i < M; i++) {
			Map<JobClass, Boolean> map = new HashMap<JobClass, Boolean>();
			for(int r = 0; r < K; r++) {
				if (hasServiceTunnel.get(i,0) == 0 && isServiceDefined.get(i, r) == 0)
					map.put(this.jobClasses.get(r), true);
				else
					map.put(this.jobClasses.get(r), false);
			}
			A.put(this.stations.get(i), map);
		}

    	return new ArrayList<Map<Station, Map<JobClass, Boolean>>>(Arrays.asList(Q, U, R, T, A));
    }

    public void initFromMarginal(JLineMatrix n) {

        if (!hasStruct) {
            refreshStruct(true);
        }
        getStruct(true);

        if (!NetworkState.isValid(sn, n, new JLineMatrix(0, 0))) {
            System.err.println("Initial state not contained in the state spac. Trying to recover.");
            for (int row = 0; row < n.getNumRows(); row++) {
                for (int col = 0; col < n.getNumCols(); col++) {
                    n.set(row, col, Math.round(n.get(row, col)));
                }
            }
            if (!NetworkState.isValid(sn, n, new JLineMatrix(0, 0))) {
              throw new RuntimeException("Cannot recover - stopping.");
            }
          }

        for (int i = 0; i < sn.nNodes; i++) {
          if (sn.isstateful.get(i) == 1) {
            int ist = (int) sn.nodeToStation.get(i);
            if (sn.nodetypes.get(i) == NodeType.Place) {
              // Must be single class token
              stations.get(i).setState(n.sumRows(0, n.getNumCols()));
            } else {
              stations.get(i).setState(NetworkState.fromMarginal(
                      sn,
                      i,
                      new JLineMatrix(JLineMatrix.extractRows(n, ist, ist + 1, null))));
            }
            if (stations.get(i).getState().isEmpty()) {
              throw new RuntimeException("Invalid state assignment for a station.");
            }
          }
        }

        hasState = true;
    }

    public boolean hasFork() {
        for (NodeType type : this.getNodeTypes()) {
            if (type == NodeType.Fork) {
                return true;
            }
        }
        return false;
    }

    public boolean hasJoin() {
        for (NodeType type : this.getNodeTypes()) {
            if (type == NodeType.Join) {
                return true;
            }
        }
        return false;
    }

}
