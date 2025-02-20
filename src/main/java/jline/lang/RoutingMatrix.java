package jline.lang;

import java.io.Serializable;
import java.util.*;


import jline.lang.constant.RoutingStrategy;
import jline.lang.nodes.ClassSwitch;
import jline.lang.nodes.Node;
import jline.lang.sections.ClassSwitcher;

public class RoutingMatrix implements Serializable {
    private List<JobClass> jobClasses;
    private List<Node> nodes;
    private Map<JobClass, Integer> classIndexMap;
    private Map<Node, Integer> nodeIndexMap;
    private boolean hasUnappliedConnections;
    
    //In order not to modify most part of the code, we will use list. But when set it to NetworkStruct, it will be transferred to map.
    private List<List<JLineMatrix>> routings;
    private JLineMatrix csMatrix;
    private boolean hasClassSwitches;
    private Network model;
    
    private JLineMatrix generateEmptyNodeOrClassRouting(int size) {
    	return new JLineMatrix(size, size, size * size);
    }

    private Map<JobClass, Map<JobClass, Double>> matrix2Hashmap(JLineMatrix classRouting){
    	Map<JobClass, Map<JobClass, Double>> csm = new HashMap<JobClass, Map<JobClass, Double>>();
		int[] col_idx = classRouting.col_idx;
		int[] nz_rows = classRouting.nz_rows;
		double[] nz_values = classRouting.nz_values;
		for(int colIdx = 0; colIdx < classRouting.numCols; colIdx++) {
			int col1 = col_idx[colIdx];
			int col2 = col_idx[colIdx+1];
			
			for(int m = col1; m < col2; m++) {
				int rowIdx = nz_rows[m];
				double values = nz_values[m];
				Map<JobClass, Double> map = csm.getOrDefault(jobClasses.get(rowIdx), new HashMap<JobClass, Double>());
				map.put(jobClasses.get(colIdx), values);
				csm.put(jobClasses.get(rowIdx), map);
			}
		}
		return csm;
    }
    
    private Map<JobClass, Map<JobClass, JLineMatrix>> routingListToMap(){
    	Map<JobClass, Map<JobClass, JLineMatrix>> routingMap = new HashMap<JobClass, Map<JobClass, JLineMatrix>>();
    	for (int i = 0; i < routings.size(); i++) {
    		JobClass jobClass = jobClasses.get(i);
    		List<JLineMatrix> routingList = routings.get(i);
    		Map<JobClass, JLineMatrix> map = new HashMap<JobClass, JLineMatrix>();
    		for(int j = 0; j < routingList.size(); j++) {
    			map.put(jobClasses.get(j), routingList.get(j).clone());
    		}
    		routingMap.put(jobClass, map);
    	}
    	return routingMap;
    }
     
    public RoutingMatrix() {
        this.jobClasses = new ArrayList<JobClass>();
        this.nodes = new ArrayList<Node>();
        this.hasUnappliedConnections = false;
        
        int I = this.nodes.size();
        int K = this.jobClasses.size();
        this.csMatrix = new JLineMatrix(K,K,K*K);
        for(int i = 0; i < K; i++)
        	this.csMatrix.set(i, i, 1.0);
        this.routings = new ArrayList<List<JLineMatrix>>();
        this.model = new Network("");
        this.hasClassSwitches = false;
    }

    public RoutingMatrix(Network model,List<JobClass> jobClasses, List<Node> nodes) {
        int nJobClasses = jobClasses.size();
        int nNodes = nodes.size();
        this.jobClasses = new ArrayList<JobClass>(jobClasses);	
        this.nodes = new ArrayList<Node>(nodes);
        this.classIndexMap = new HashMap<JobClass, Integer>();
        this.nodeIndexMap = new HashMap<Node, Integer>();
        this.hasUnappliedConnections = false;

        for (int j = 0; j < this.nodes.size(); j++) {
            this.nodeIndexMap.put(this.nodes.get(j), j);
        }
        
        this.model = model;
        this.csMatrix = new JLineMatrix(nJobClasses, nJobClasses, nJobClasses*nJobClasses);
        for(int i = 0; i < nJobClasses; i++)
        	this.csMatrix.set(i, i, 1.0);
        this.hasClassSwitches = false;
        
        routings = new ArrayList<List<JLineMatrix>>(nJobClasses);
        for(int i = 0; i < nJobClasses; i++) {
        	List<JLineMatrix> frame = new ArrayList<JLineMatrix>(nJobClasses);
        	for(int j = 0; j < nJobClasses; j++)
        		frame.add(generateEmptyNodeOrClassRouting(nNodes));
        	
        	this.routings.add(frame);
        	this.classIndexMap.put(this.jobClasses.get(i), i);
        }
    }

    public void addClass(JobClass jobClass) {
        if (this.jobClasses.contains(jobClass)) {
            // idempotent
            return;
        }

        int classIdx = this.jobClasses.size();
        this.jobClasses.add(jobClass);
        this.classIndexMap.put(jobClass, classIdx);
        
        int nJobClasses = jobClasses.size();
        int nNodes = nodes.size();
        
        this.csMatrix.expandMatrix(nJobClasses, nJobClasses, nJobClasses*nJobClasses);
        this.csMatrix.set(nJobClasses - 1, nJobClasses - 1, 1);
        
        List<JLineMatrix> frame = new ArrayList<JLineMatrix>();
        for(int i = 0; i < nJobClasses - 1; i++) {
        	this.routings.get(i).add(this.generateEmptyNodeOrClassRouting(nNodes)); // Old class to the new class
        	frame.add(this.generateEmptyNodeOrClassRouting(nNodes)); // New class to old class
        }
        
        frame.add(this.generateEmptyNodeOrClassRouting(nNodes));  // New class to New class
        routings.add(frame);
    }

    public int getClassIndex(JobClass jobClass) {
        return this.classIndexMap.get(jobClass);
    }

    public int getNodeIndex(Node node) {
        return this.nodeIndexMap.get(node);
    }

    public void addNode(Node node) {
        if (this.nodes.contains(node)) {
            return;
        }

        int nodeIdx = this.nodes.size();

        this.nodes.add(node);
        this.nodeIndexMap.put(node, nodeIdx);
        
        int I = this.nodes.size();
        
        for (List<JLineMatrix> classArray : this.routings) {
        	for(int i = 0; i < classArray.size(); i++)
        		classArray.get(i).expandMatrix(I, I, I*I);
        }
    }

    public void addConnection(Node sourceNode, Node destNode) {
        for (JobClass jobClass : this.jobClasses) {
            this.addConnection(sourceNode, destNode, jobClass, jobClass);
        }
    }
    
    public void addConnection(Node sourceNode, Node destNode, JobClass jobClass) {
        if (sourceNode.getRoutingStrategy(jobClass) == RoutingStrategy.DISABLED) {
            return;
        }

        this.hasUnappliedConnections = true;
        this.addConnection(sourceNode, destNode, jobClass, jobClass, Double.NaN);
    }
    
    public void addConnection(Node sourceNode, Node destNode, double probability) {
    	for (JobClass jobClass : this.jobClasses) {
    		this.addConnection(sourceNode, destNode, jobClass, probability);
    	}
    }
    
    public void addConnection(Node sourceNode, Node destNode, JobClass originClass, JobClass targetClass) {
    	if (sourceNode.getRoutingStrategy(originClass) == RoutingStrategy.DISABLED) {
    		return;
    	}
    	
    	this.hasUnappliedConnections = true;
    	this.addConnection(sourceNode, destNode, originClass, targetClass, Double.NaN);
    }

    public void addConnection(Node sourceNode, Node destNode, JobClass jobClass, double probability) {
        if (sourceNode.getRoutingStrategy(jobClass) == RoutingStrategy.DISABLED) {
            return;
        }
        
        if (Double.isNaN(probability)) {
        	this.hasUnappliedConnections = true;
        }
        
        this.addConnection(sourceNode, destNode, jobClass, jobClass, probability);
    }
    
    public void addConnection(Node sourceNode, Node destNode, JobClass originClass, JobClass targetClass, double probability) {
    	   
    	int originClassIdx = getClassIndex(originClass);
    	int targetClassIdx = getClassIndex(targetClass);
    	int sourceNodeIdx = getNodeIndex(sourceNode);
    	int destNodeIdx = getNodeIndex(destNode);
    	
        if (!originClass.equals(targetClass)) {
        	this.hasClassSwitches = true;
        }
    	
    	csMatrix.set(originClassIdx, targetClassIdx, 1);
    	routings.get(originClassIdx).get(targetClassIdx).unsafe_set(sourceNodeIdx, destNodeIdx, probability);
    	
    	if (sourceNode instanceof ClassSwitch) {
    		ClassSwitcher server = ((ClassSwitcher) sourceNode.getServer());
    		for(int r = 0; r < this.jobClasses.size(); r++) {
    			for(int s = 0; s < this.jobClasses.size(); s++) {
    				if (server.applyCsFun(r, s) > 0)
    					csMatrix.set(r, s, 1.0);
    			}
    		}		
    	}
    }

    public void resolveUnappliedConnections() {
    	 
        int I = nodes.size();
        for (List<JLineMatrix> jobClassRouting : this.routings) {
        	for (JLineMatrix nodeRouting : jobClassRouting) {
        		for(int row = 0; row < I; row++) {
        			double residProb = 1;
        			int nUnapplied = 0;
        			for(int col = 0; col < I; col++) {
        				Double routingAmount = nodeRouting.get(row, col);
        				if (routingAmount.isNaN()) {
        					nUnapplied++;
        				} else {
        					residProb -= routingAmount;
        				}
        			}
                    if (nUnapplied == 0) {
                        continue;
                    }
                    double unitProb = residProb / nUnapplied;
                    for(int col = 0; col < I; col++) {
                    	if (Double.isNaN(nodeRouting.get(row, col)))
                    		nodeRouting.set(row, col, unitProb);
                    }
        		}
        	}
        }
        this.hasUnappliedConnections = false;
    }

    public void resolveClassSwitches() {
    	//line 163 - 168
    	int nNodes = nodes.size();
    	int nClasses = jobClasses.size();
    	List<List<JLineMatrix>> csnodematrix = new ArrayList<List<JLineMatrix>>(nNodes);
    	for(int i = 0; i < nNodes; i++) {
    		List<JLineMatrix> classMatrix = new ArrayList<JLineMatrix>(nNodes);
    		for(int j = 0; j < nNodes; j++)
    			classMatrix.add(generateEmptyNodeOrClassRouting(nClasses));
    		csnodematrix.add(classMatrix);
    	}
    	
    	//line 170 - 179
    	for(int row = 0; row < nClasses; row++) {
    		for(int col = 0; col < nClasses; col++) {
    			JLineMatrix nodeRouting = routings.get(row).get(col);
    			if (nodeRouting.getNonZeroLength() > 0) {
    				int[] col_idx = nodeRouting.col_idx;
    				int[] nz_rows = nodeRouting.nz_rows;
    				double[] nz_values = nodeRouting.nz_values;
    				
    				for(int colIdx = 0; colIdx < nodeRouting.numCols; colIdx++) {
    					int col1 = col_idx[colIdx];
    					int col2 = col_idx[colIdx+1];
    					
    					for(int i = col1; i < col2; i++) {
    						int rowIdx = nz_rows[i];
    						double values = nz_values[i];
    						csnodematrix.get(rowIdx).get(colIdx).set(row, col, values);
    					}
    				}
    			}
    		}
    	}
    	
    	//line 196 - 207
    	for(int i = 0; i < nNodes; i++) {
    		for (int j = 0; j < nNodes; j++) {
    			JLineMatrix classRouting = csnodematrix.get(i).get(j);
    			JLineMatrix res = classRouting.sumRows();
				classRouting.divideRows(res.nz_values, 0);
    			for(int r = 0; r < nClasses; r++) {
    				if (res.get(r) == 0)
    					classRouting.set(r, r, 1.0);
    			}
    		}
    	}
    	
    	//line 209 - 220
    	int[][] csid = new int[nNodes][nNodes];
    	for(int i = 0; i < nNodes; i++) {
    		for(int j = 0; j < nNodes; j++) {
    			JLineMatrix classRouting = csnodematrix.get(i).get(j);
    			if (!classRouting.isDiag()) {
    				String csname = "CS_" + nodes.get(i).getName() + "_to_" + nodes.get(j).getName();
    				this.addNode(new ClassSwitch(model, csname, matrix2Hashmap(classRouting), classRouting)); //line 239 - 243
    				csid[i][j] = model.getNumberOfNodes() - 1;
    			}
    		}
    	}
    	
    	//line 245 - 260
    	for(int i = 0; i < nNodes; i++) {
    		for (int j = 0; j < nNodes; j++) {
    			if (csid[i][j] > 0) {
    				for(int r = 0; r < nClasses; r++) {
    					for (int s = 0; s < nClasses; s++) {
    						JLineMatrix nodeRouting = routings.get(r).get(s);
    						if (nodeRouting.get(i, j) > 0) {
    							JLineMatrix from = routings.get(r).get(r);
    							from.set(i, csid[i][j], from.get(i, csid[i][j]) + nodeRouting.get(i, j));
    							nodeRouting.remove(i, j);
    						}
    						JLineMatrix to = routings.get(s).get(s);
    						to.set(csid[i][j], j, 1.0);
    					}
    				}
    			}
    		}
    	}
    	this.hasClassSwitches = false;
    }
    
    public void setRouting(Network model) {
        if (this.hasUnappliedConnections) {
            this.resolveUnappliedConnections();
        }
        NetworkStruct sn = new NetworkStruct();
        sn.rtorig = routingListToMap();
        
        if (this.hasClassSwitches) {
        	this.resolveClassSwitches();
        }
        
        //line 262-273
        for (int r = 0; r < this.jobClasses.size(); r++) {
        	JLineMatrix routing = routings.get(r).get(r);
        
    		int[] col_idx = routing.col_idx;
    		int[] nz_rows = routing.nz_rows;
    		double[] nz_values = routing.nz_values;
    		
    		for(int colIdx = 0; colIdx < routing.numCols; colIdx++) {
    			int col1 = col_idx[colIdx];
    			int col2 = col_idx[colIdx+1];
    			
    			for(int i = col1; i < col2; i++) {
    				int rowIdx = nz_rows[i];
    				double values = nz_values[i];
    				model.addLink(rowIdx, colIdx);
    				this.nodes.get(rowIdx).setRouting(this.jobClasses.get(r), RoutingStrategy.PROB, this.nodes.get(colIdx), values);
    			}
    		}
        	
        }
        this.model.setStruct(sn);
        this.model.setCsMatrix(this.csMatrix);
    }
}
