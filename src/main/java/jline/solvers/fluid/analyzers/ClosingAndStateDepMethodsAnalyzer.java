// Copyright (c) 2012-2022, Imperial College London
// All rights reserved.

package jline.solvers.fluid.analyzers;

import jline.lang.JLineMatrix;
import jline.lang.JobClass;
import jline.lang.NetworkStruct;
import jline.lang.distributions.Distribution;
import jline.lang.nodes.Station;
import jline.solvers.SolverOptions;
import jline.solvers.SolverResult;
import jline.solvers.fluid.odes.ClosingAndStateDepMethodsODE;
import jline.solvers.fluid.odes.TransientDataHandler;
import org.apache.commons.math3.ode.FirstOrderDifferentialEquations;
import org.apache.commons.math3.ode.FirstOrderIntegrator;
import org.ejml.data.DMatrixRMaj;
import org.qore.KPC.MAP;

import java.util.*;

import static java.lang.Double.POSITIVE_INFINITY;
import static java.lang.Math.*;
import static jline.lang.constant.SchedStrategy.*;

public class ClosingAndStateDepMethodsAnalyzer implements MethodAnalyzer {

  public JLineMatrix xvec_t;
  public JLineMatrix xvec_it;

  private void solver_fluid_iteration(
      NetworkStruct sn,
      Map<Station, Map<JobClass, JLineMatrix>> mu,
      Map<Station, Map<JobClass, JLineMatrix>> phi,
      JLineMatrix S,
      double[] yDefault,
      JLineMatrix slowrate,
      SolverOptions options,
      SolverResult result) {

    boolean goOn = true; // max stiff solver
    int iter = 0;
    int totalSteps = 0;

    // Heuristic to select stiff or non-stiff ODE solver
    int slowrateRows = slowrate.getNumRows();
    int slowrateCols = slowrate.getNumCols();
    double minNonZeroRate = POSITIVE_INFINITY;
    for (int i = 0; i < slowrateRows; i++) {
      for (int j = 0; j < slowrateCols; j++) {
        if ((slowrate.get(i, j) > Distribution.tolerance)
            && Double.isFinite(slowrate.get(i, j))
            && (slowrate.get(i, j) < minNonZeroRate)) {
          minNonZeroRate = slowrate.get(i, j);
        }
      }
    }

    // Initialise ODE
    FirstOrderDifferentialEquations ode =
        new ClosingAndStateDepMethodsODE(sn, mu, phi, sn.proc, sn.rt, S, options);

    double T0 = options.timespan[0];
    int T = 0;

    List<JLineMatrix> tIterations = new LinkedList<>();
    List<JLineMatrix> xVecIterations = new LinkedList<>();
    while (((Double.isFinite(options.timespan[1])) && T < options.timespan[1])
        || (goOn && iter < options.iter_max)) {

      iter++;

      // Determine entry state vector in e
      double[] initialState = new double[yDefault.length];
      double[] nextState = new double[yDefault.length];
      for (int i = 0; i < yDefault.length; i++) {
        initialState[i] = xvec_it.get(0, i);
        nextState[i] = 0;
      }

      // Solve ode until T = 1 event with slowest exit rate 
      if (iter == 1) {
	      T = (int) min(options.timespan[1], abs(10 / minNonZeroRate));
      } else { 
	      T = (int) min(options.timespan[1], abs(10 * iter / minNonZeroRate));
      }
      double[] tRange = {T0, T};

      FirstOrderIntegrator odeSolver;
      if (options.stiff && (options.verbose == SolverOptions.VerboseLevel.DEBUG)) {
        System.err.println(
            "Stiff solvers are not yet available in JLINE. Using non-stiff solver instead.");
	    options.stiff = false;
      }
      if (options.tol > 0.001 && (options.verbose == SolverOptions.VerboseLevel.DEBUG)) {
        System.err.println(
            "Fast, non-stiff ODE solver is not yet available in JLINE. Using accurate non-stiff ODE solver instead.");
      }
      odeSolver = options.odeSolvers.accurateODESolver;

      odeSolver.clearStepHandlers();
      TransientDataHandler stepHandler = new TransientDataHandler(initialState.length);
      odeSolver.addStepHandler(stepHandler);

      try {
        //System.out.print("Start ODE integration cycle...");
        odeSolver.integrate(ode, tRange[0], initialState, tRange[1], nextState);
	//System.out.println("done.");
      } catch (RuntimeException e) {
        if (options.verbose != SolverOptions.VerboseLevel.SILENT) {
          System.out.println(
              "The initial point is invalid, Fluid solver switching to default initialization.");
        }
        odeSolver.integrate(ode, tRange[0], yDefault, tRange[1], nextState);
      }

      // Retrieve Transient Data
      tIterations.add(stepHandler.tVec);
      xVecIterations.add(stepHandler.xVec);
      int Tmax = stepHandler.tVec.getNumRows();
      totalSteps += Tmax;
      this.xvec_it = JLineMatrix.extractRows(stepHandler.xVec, Tmax - 1, Tmax, null);

      T0 = T; // for next iteration
      if (T >= options.timespan[1]) {
        goOn = false;
      }
    }

    // Migrating tIterations and xVecIterations from Lists to concatenated JLineMatrix objects
    // Note this is done manually for performance purposes - concatRows is not as efficient
    int nextRow = 0;
    int cols = xVecIterations.get(0).getNumCols();
    this.xvec_t = new JLineMatrix(totalSteps, cols);
    result.t = new JLineMatrix(totalSteps, 1);
    for (int i = 0; i < iter; i++) {
      JLineMatrix tIter = tIterations.get(i);
      JLineMatrix xVecIter = xVecIterations.get(i);
      int stepsPerIter = tIter.getNumRows();
      for (int j = nextRow; j < nextRow + stepsPerIter; j++) {
        result.t.set(j, 0, tIter.get(j - nextRow, 0));
        for (int k = 0; k < cols; k++) {
          xvec_t.set(j, k, xVecIter.get(j - nextRow, k));
        }
      }
      nextRow += stepsPerIter;
    }
  }

  private void solver_fluid(NetworkStruct sn, SolverOptions options, SolverResult result) {

    int M = sn.nstations; // Number of stations
    int K = sn.nClasses; // Number of classes
    JLineMatrix S = sn.nservers.clone();

    // Making deep copies as going forwards, mu and phi are amended
    Map<Station, Map<JobClass, JLineMatrix>> mu = new HashMap<>();
    Map<Station, Map<JobClass, JLineMatrix>> phi = new HashMap<>();
    for (int i = 0; i < M; i++) {
      Station station = sn.stations.get(i);
      Map<JobClass, JLineMatrix> muCopy = new HashMap<>();
      Map<JobClass, JLineMatrix> phiCopy = new HashMap<>();
      for (int k = 0; k < K; k++) {
        JobClass jobClass = sn.jobClasses.get(k);
        muCopy.put(jobClass, sn.mu.get(station).get(jobClass).clone());
        phiCopy.put(jobClass, sn.phi.get(station).get(jobClass).clone());
      }
      mu.put(station, muCopy);
      phi.put(station, phiCopy);
    }

    JLineMatrix match = new JLineMatrix(M, K); // Indicates whether a class is served at a station
    JLineMatrix phases = new JLineMatrix(M, K);
    JLineMatrix slowrate = new JLineMatrix(M, K);
    for (int i = 0; i < M; i++) {
      Station station = sn.stations.get(i);
      for (int k = 0; k < K; k++) {
        JobClass jobClass = sn.jobClasses.get(k);

        if (mu.get(station).get(jobClass).hasNaN()) {
          mu.get(station).get(jobClass).reshape(0, 0);
          phi.get(station).get(jobClass).reshape(0, 0);
        }

        if (sn.rt.sumCols((i * K) + k) > 0) {
          match.set(i, k, 1);
        }

        // Set number of servers in delay station = population
        if (Double.isInfinite(S.get(i, 0))) {
          S.set(i, 0, sn.nclosedjobs);
        }

        phases.set(i, k, mu.get(station).get(jobClass).length());

        if (mu.get(station).get(jobClass).isEmpty()) {
          slowrate.set(i, k, POSITIVE_INFINITY);
        } else {
          // service completion (exit) rates in each phase
          slowrate.set(i, k, mu.get(station).get(jobClass).elementMin());
        }
      }
    }

    JLineMatrix y0 = new JLineMatrix(1, 0);
    JLineMatrix assigned = new JLineMatrix(1, K); // number of jobs of each class already assigned
    double toAssign;
    for (int i = 0; i < M; i++) {
      for (int k = 0; k < K; k++) {
        if (match.get(i, k) > 0) { // indicates whether a class is served at a station
          if (Double.isInfinite(sn.njobs.get(0, k))) {
            if (sn.sched.get(sn.stations.get(i)) == EXT) {
              toAssign = 1; // open job pool
            } else {
              toAssign = 0; // set to zero open jobs everywhere
            }
          } else {
            toAssign = floor(sn.njobs.get(0, k) / match.sumCols(k));
            // If this is the last station for this job class
            if (match.sumSubMatrix(i + 1, match.getNumRows(), k, k + 1) == 0) {
              toAssign = sn.njobs.get(0, k) - assigned.get(0, k);
            }
          }

          // This is just for PS
          int originalY0Length = y0.getNumCols();
          int newY0Length = originalY0Length + 1 + (int) (phases.get(i, k) - 1);
          y0.expandMatrix(1, newY0Length, newY0Length);
          y0.set(0, originalY0Length, toAssign);
          for (int col = 0; col < phases.get(i, k) - 1; col++) {
            y0.set(0, originalY0Length + 1 + col, 0);
          }
          assigned.set(0, k, assigned.get(0, k) + toAssign);
        } else {
          int originalY0Length = y0.getNumCols();
          int newY0Length = originalY0Length + (int) phases.get(i, k);
          y0.expandMatrix(1, newY0Length, newY0Length);
          for (int col = 0; col < phases.get(i, k); col++) {
            y0.set(0, originalY0Length + col, 0);
          }
        }
      }
    }

    int y0cols = y0.getNumCols();
    double[] yDefault = new double[y0cols];
    if (options.init_sol.isEmpty()) {
      xvec_it = y0; // average state embedded at stage change transitions out of e
    } else {
      xvec_it = options.init_sol;
      for (int i = 0; i < y0cols; i++) {
        yDefault[i] = y0.get(0, i); // default solution if init_sol fails
      }
    }

    // Solve ODE
    solver_fluid_iteration(sn, mu, phi, S, yDefault, slowrate, options, result);

    // This part assumes PS, DPS, GPS scheduling
    result.QN = new JLineMatrix(M, K);
    result.QNt = new JLineMatrix[M][K];
    JLineMatrix[] Qt = new JLineMatrix[M];
    result.UNt = new JLineMatrix[M][K];
    int Tmax = xvec_t.getNumRows();

    for (int i = 0; i < M; i++) {
      Qt[i] = new JLineMatrix(Tmax, 1);
      for (int k = 0; k < K; k++) {
        int shift =
            (int) phases.sumSubMatrix(0, i, 0, phases.getNumCols())
                + (int) phases.sumSubMatrix(i, i + 1, 0, k);
        result.QN.set(i, k, xvec_it.sumSubMatrix(0, 1, shift, shift + (int) phases.get(i, k)));

        result.QNt[i][k] = new JLineMatrix(Tmax, 1);
        for (int step = 0; step < Tmax; step++) {
          result.QNt[i][k].set(
              step, 0, xvec_t.sumSubMatrix(step, step + 1, shift, shift + (int) phases.get(i, k)));
        }

        Qt[i] = Qt[i].add(1, result.QNt[i][k]);
        // computes queue length in each node and stage, summing the total number in service and
        // waiting in that station results are weighted with the stat prob of the stage
      }
    }

    for (int i = 0; i < M; i++) {
      if (sn.nservers.get(i, 0) > 0) { // Not INF
        for (int k = 0; k < K; k++) {
          result.UNt[i][k] = new JLineMatrix(Tmax, 1);
          for (int step = 0; step < Tmax; step++) {
            // If not an infinite server then this is a number between 0 and 1
            result.UNt[i][k].set(
                step,
                0,
                min(
                    result.QNt[i][k].get(step, 0) / S.get(i, 0),
                    result.QNt[i][k].get(step, 0) / Qt[i].get(step, 0)));
            if (Double.isNaN(result.UNt[i][k].get(step, 0))) {
              result.UNt[i][k].set(step, 0, 0); // fix cases where QLen is 0
            }
          }
        }
      } else {
        // Infinite server
        System.arraycopy(result.QNt[i], 0, result.UNt[i], 0, K);
      }
    }
  }

  @Override
  public void analyze(NetworkStruct sn, SolverOptions options, SolverResult result) {

    int M = sn.nstations; // Number of stations
    int K = sn.nClasses; // Number of classes
    Map<Station, Map<JobClass, JLineMatrix>> lambda = sn.mu;

    // Inner iteration of fluid analysis
    solver_fluid(sn, options, result);

    // Assumes the existence of a delay node through which all classes pass
    JLineMatrix delayNodes = new JLineMatrix(1, M);
    for (int i = 0; i < M; i++) {
      if (sn.sched.get(sn.stations.get(i)) == INF) {
        delayNodes.set(0, i, 1);
      }
    }

    // THROUGHPUT - for all classes in each station
    result.TN = new JLineMatrix(M, K);
    result.TNt = new JLineMatrix[M][K];
    int Tmax = result.t.getNumRows();
    for (int i = 0; i < M; i++) {
      for (int k = 0; k < K; k++) {
        result.TNt[i][k] = new JLineMatrix(Tmax, 1);
      }
    }

    JLineMatrix[][] Xservice = new JLineMatrix[M][K]; // Throughput per class, station and phase

    for (int i = 0; i < M; i++) {
      if (delayNodes.get(0, i) == 1) {
        for (int k = 0; k < K; k++) {
          int idx =
              (int) sn.phases.sumSubMatrix(0, i, 0, K)
                  + (int) sn.phases.sumSubMatrix(i, i + 1, 0, k);
          Xservice[i][k] = new JLineMatrix((int) sn.phases.get(i, k), 1);
          for (int f = 0; f < (int) sn.phases.get(i, k); f++) {
            double lambdaIKF = lambda.get(sn.stations.get(i)).get(sn.jobClasses.get(k)).get(f, 0);
            double phiIKF = sn.phi.get(sn.stations.get(i)).get(sn.jobClasses.get(k)).get(f, 0);

            result.TN.set(
                i, k, result.TN.get(i, k) + (xvec_it.get(0, idx + f) * lambdaIKF * phiIKF));

            JLineMatrix tmpTNt = result.QNt[i][k].clone();
            tmpTNt.scale(lambdaIKF * phiIKF, tmpTNt);
            result.TNt[i][k] = result.TNt[i][k].add(1.0, tmpTNt);

            Xservice[i][k].set(f, 0, (xvec_it.get(0, idx + f) * lambdaIKF));
          }
        }
      } else {
        double xi = result.QN.sumRows(i); // Number of jobs in the station
        JLineMatrix xi_t = result.QNt[i][0].clone();
        for (int r = 1; r < K; r++) {
          xi_t = xi_t.add(1.0, result.QNt[i][r]);
        }

        double wni = 0.01;
        JLineMatrix wi = new JLineMatrix(1, K);
        if (xi > 0) {
          if (sn.sched.get(sn.stations.get(i)) == FCFS) {
            for (int k = 0; k < K; k++) {
              int idx =
                  (int) sn.phases.sumSubMatrix(0, i, 0, K)
                      + (int) sn.phases.sumSubMatrix(i, i + 1, 0, k);
              wi.set(
                  0,
                  k,
                  new MAP(
                          new DMatrixRMaj(
                              sn.proc.get(sn.stations.get(i)).get(sn.jobClasses.get(k)).get(0)),
                          new DMatrixRMaj(
                              sn.proc.get(sn.stations.get(i)).get(sn.jobClasses.get(k)).get(1)))
                      .getMean());

              wni +=
                  wi.get(0, k) * xvec_it.sumSubMatrix(0, 1, idx, idx + (int) sn.phases.get(i, k));
            }
          }

          for (int k = 0; k < K; k++) {
            int idx =
                (int) sn.phases.sumSubMatrix(0, i, 0, K)
                    + (int) sn.phases.sumSubMatrix(i, i + 1, 0, k);
            Xservice[i][k] = new JLineMatrix((int) sn.phases.get(i, k), 1);

            for (int f = 0; f < (int) sn.phases.get(i, k); f++) {
              double lambdaIKF = lambda.get(sn.stations.get(i)).get(sn.jobClasses.get(k)).get(f, 0);
              double phiIKF = sn.phi.get(sn.stations.get(i)).get(sn.jobClasses.get(k)).get(f, 0);

              switch (sn.sched.get(sn.stations.get(i))) {
                case EXT:
                  if (f == 0) {
                    result.TN.set(
                        i,
                        k,
                        result.TN.get(i, k)
                            + (lambdaIKF
                                * phiIKF
                                * (1
                                    - xvec_it.sumSubMatrix(
                                        0, 1, idx + 1, idx + (int) sn.phases.get(i, k)))));

                    JLineMatrix tmpTNt = xvec_t.sumRows(idx + 1, idx + (int) sn.phases.get(i, k));
                    JLineMatrix ones = new JLineMatrix(tmpTNt.getNumRows(), tmpTNt.getNumCols());
                    ones.ones();
                    tmpTNt = ones.sub(1, tmpTNt);
                    tmpTNt.scale(lambdaIKF * phiIKF, tmpTNt);
                    result.TNt[i][k] = result.TNt[i][k].add(1, tmpTNt);

                    Xservice[i][k].set(
                        f,
                        0,
                        lambdaIKF
                            * (1
                                - xvec_it.sumSubMatrix(
                                    0, 1, idx + 1, idx + (int) sn.phases.get(i, k))));
                  } else {
                    result.TN.set(
                        i, k, result.TN.get(i, k) + (lambdaIKF * phiIKF * xvec_it.get(0, idx + f)));
                    JLineMatrix tmpTNt = xvec_t.sumRows(idx + f, idx + f + 1);
                    tmpTNt.scale(lambdaIKF * phiIKF, tmpTNt);
                    result.TNt[i][k] = result.TNt[i][k].add(1, tmpTNt);
                    Xservice[i][k].set(f, 0, lambdaIKF * xvec_it.get(0, idx + f));
                  }
                  break;

                case INF:
                case PS:
                  result.TN.set(
                      i,
                      k,
                      result.TN.get(i, k)
                          + (lambdaIKF
                              * phiIKF
                              / xi
                              * xvec_it.get(0, idx + f)
                              * min(xi, sn.nservers.get(i, 0))));

                  JLineMatrix tmpTNT = new JLineMatrix(xvec_t.getNumRows(), 1);
                  JLineMatrix.extractColumn(xvec_t, idx + f, tmpTNT);
                  tmpTNT.scale(lambdaIKF * phiIKF, tmpTNT);
                  int rows = tmpTNT.getNumRows();
                  for (int row = 0; row < rows; row++) {
                    result.TNt[i][k].set(
                        row,
                        0,
                        result.TNt[i][k].get(row, 0)
                            + (tmpTNT.get(row, 0)
                                / xi_t.get(row, 0)
                                * min(xi_t.get(row, 0), sn.nservers.get(i, 0))));
                  }

                  Xservice[i][k].set(
                      f,
                      0,
                      lambdaIKF / xi * min(xi, sn.nservers.get(i, 0)) * xvec_it.get(0, idx + f));
                  break;

                case DPS:
                  JLineMatrix w = new JLineMatrix(1, K);
                  for (int p = 0; p < K; p++) {
                    w.set(0, k, sn.schedparam.get(i, k));
                  }

                  JLineMatrix tmpQ = new JLineMatrix(1, result.QN.getNumCols());
                  JLineMatrix.extractRows(result.QN, i, i + 1, tmpQ);
                  tmpQ = tmpQ.transpose();
                  JLineMatrix wxi = new JLineMatrix(1, 1); // number of jobs in the station
                  wxi = w.mult(tmpQ, wxi);

                  JLineMatrix wxi_t = result.QNt[i][0].clone();
                  wxi_t.scale(w.get(0, 0), wxi_t);
                  for (int r = 1; r < result.QNt[i][0].getNumCols(); r++) {
                    JLineMatrix tmpWxi_t = result.QNt[i][r].clone();
                    tmpWxi_t.scale(w.get(0, r), tmpWxi_t);
                    wxi_t = wxi_t.add(1, tmpWxi_t);
                  }

                  result.TN.set(
                      i,
                      k,
                      result.TN.get(i, k)
                          + (lambdaIKF
                              * phiIKF
                              * w.get(0, k)
                              / wxi.get(0, 0)
                              * xvec_it.get(0, idx + f)
                              * min(xi, sn.nservers.get(i, 0))));

                  tmpTNT = new JLineMatrix(xvec_t.getNumRows(), 1);
                  JLineMatrix.extractColumn(xvec_t, idx + f, tmpTNT);
                  tmpTNT.scale(lambdaIKF * phiIKF * w.get(0, k), tmpTNT);
                  rows = tmpTNT.getNumRows();
                  for (int row = 0; row < rows; row++) {
                    result.TNt[i][k].set(
                        row,
                        0,
                        result.TNt[i][k].get(row, 0)
                            + (tmpTNT.get(row, 0)
                                / wxi_t.get(row, 0)
                                * min(xi_t.get(row, 0), sn.nservers.get(i, 0))));
                  }

                  Xservice[i][k].set(
                      f,
                      0,
                      lambdaIKF
                          * w.get(0, k)
                          / wxi.get(0, 0)
                          * min(xi, sn.nservers.get(i, 0))
                          * xvec_it.get(0, idx + f));
                  break;

                case FCFS:
                case SIRO:
                  tmpTNT = new JLineMatrix(xvec_t.getNumRows(), 1);
                  JLineMatrix.extractColumn(xvec_t, idx + f, tmpTNT);
                  tmpTNT.scale(lambdaIKF * phiIKF, tmpTNT);
                  rows = tmpTNT.getNumRows();
                  for (int row = 0; row < rows; row++) {
                    result.TNt[i][k].set(
                        row,
                        0,
                        result.TNt[i][k].get(row, 0)
                            + (tmpTNT.get(row, 0)
                                / xi_t.get(row, 0)
                                * min(xi_t.get(row, 0), sn.nservers.get(i, 0))));
                  }

                  if (Objects.equals(options.method, "default")
                      || Objects.equals(options.method, "closing")) {
                    result.TN.set(
                        i,
                        k,
                        result.TN.get(i, k)
                            + (lambdaIKF
                                * phiIKF
                                / xi
                                * xvec_it.get(0, idx + f)
                                * Math.min(xi, sn.nservers.get(i, 0))));
                    Xservice[i][k].set(
                        f,
                        0,
                        lambdaIKF / xi * min(xi, sn.nservers.get(i, 0)) * xvec_it.get(0, idx + f));
                    break;
                  } else if (Objects.equals(options.method, "statedep")) {
                    result.TN.set(
                        i,
                        k,
                        result.TN.get(i, k)
                            + (lambdaIKF
                                * phiIKF
                                * wi.get(0, k)
                                / wni
                                * xvec_it.get(0, idx + f)
                                * min(xi, sn.nservers.get(i, 0))));

                    Xservice[i][k].set(
                        f,
                        0,
                        lambdaIKF
                            * wi.get(0, k)
                            / wni
                            * min(xi, sn.nservers.get(i, 0))
                            * xvec_it.get(0, idx + f));
                  }
                  break;
                default:
                  throw new RuntimeException("Unsupported scheduling policy.");
              }
            }
          }
        }
      }
    }

    // Response Times - this is approximate, Little's law does not hold in transient
    result.RN = new JLineMatrix(M, K);
    for (int i = 0; i < M; i++) {
      for (int k = 0; k < K; k++) {
        if (result.TN.get(i, k) > 0) {
          result.RN.set(i, k, result.QN.get(i, k) / result.TN.get(i, k));
        }
      }
    }

    // Utilisation
    result.UN = new JLineMatrix(M, K);
    for (int i = 0; i < M; i++) {
      for (int k = 0; k < K; k++) {
        double sumForUN = 0;
        double sumForTN = 0;
        int rows = Xservice[i][k].getNumRows();
        for (int row = 0; row < rows; row++) {
          if (Xservice[i][k].get(row, 0) > 0) {
            sumForUN +=
                Xservice[i][k].get(row, 0)
                    / lambda.get(sn.stations.get(i)).get(sn.jobClasses.get(k)).get(row, 0);
            sumForTN += Xservice[i][k].get(row, 0);
          }
        }
        result.UN.set(i, k, sumForUN);
        if ((sn.sched.get(sn.stations.get(i)) == FCFS)
            && (Objects.equals(options.method, "statedep"))) {
          result.TN.set(i, k, sumForTN);
        }
      }
    }

    for (int i = 0; i < M; i++) {
      for (int k = 0; k < K; k++) {
        if (delayNodes.get(0, i) == 0) {
          result.UN.set(i, k, result.UN.get(i, k) / sn.nservers.get(i, 0));
        }
      }
    }
  }

  @Override
  public JLineMatrix getXVecIt() {
    return this.xvec_it;
  }
}
