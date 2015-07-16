/*
 * Copyright (c) 1999-2015, Ecole des Mines de Nantes
 * All rights reserved.
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the Ecole des Mines de Nantes nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE REGENTS AND CONTRIBUTORS ``AS IS'' AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE REGENTS AND CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.chocosolver.parser.flatzinc.layout;

import org.chocosolver.solver.ParallelPortfolio;
import org.chocosolver.solver.Portfolio;
import org.chocosolver.solver.SequentialPortfolio;
import org.chocosolver.solver.search.solution.Solution;

/**
 * Created by cprudhom on 18/06/15.
 * Project: choco-parsers.
 */
public class SharedSolutionPrinter extends ASolutionPrinter {

    Portfolio prtfl;
    boolean fo = false;

    public SharedSolutionPrinter(Portfolio prtfl, boolean printAll, boolean printStat) {
        super(prtfl.getSolutionRecorder(), printAll, printStat);
        this.prtfl = prtfl;
        for (int i = 0; i < prtfl.workers.length; i++) {
            prtfl.workers[i].plugMonitor(this);
        }
    }

    @Override
    public synchronized void onSolution() {
        super.onSolution();
        if (printStat) {
            statistics();
        }
    }

    @Override
    public synchronized void printSolution(Solution solution) {
        synchronized (solution) {
            super.printSolution(solution);
        }
    }

    public synchronized void doFinalOutPut() {
        if (!fo) {
            fo = true;
            userinterruption = false;
            boolean complete = isComplete();
            if (nbSolution == 0) {
                if (complete) {
                    System.out.printf("=====UNSATISFIABLE=====\n");
                } else {
                    System.out.printf("=====UNKNOWN=====\n");
                }
            } else { // at least one solution
                if (!printAll) { // print the first/best solution when -a is not enabled
                    printSolution(solrecorder.getLastSolution());
                }
                if (complete) {
                    System.out.printf("==========\n");
                } else {
                    if ((prtfl._fes_().getObjectiveManager().isOptimization())) {
                        System.out.printf("=====UNBOUNDED=====\n");
                    }
                }
            }
            if (printStat) {
                statistics();
            }
        }
    }

    private boolean isComplete() {
        boolean complete = false;
        if (prtfl instanceof SequentialPortfolio) {
            // if none has reached limit
            boolean hrl = false;
            boolean heu = true;
            for (int i = 0; i < prtfl.getNbWorkers(); i++) {
                hrl |= prtfl.workers[i].getSearchLoop().hasReachedLimit();
                heu &= prtfl.workers[i].getSearchLoop().hasEndedUnexpectedly();
            }
            complete = !hrl && !heu;
        } else {
            // if one has reached
            int f = ((ParallelPortfolio) prtfl).getFinisher();
            complete = !prtfl.workers[f].getSearchLoop().hasReachedLimit() && !prtfl.workers[f].getSearchLoop().hasReachedLimit();
        }
        return complete;
    }

    private void statistics() {
        StringBuilder st = new StringBuilder(256);
        st.append(String.format("%d Solutions, ", nbSolution));
        switch (prtfl._fes_().getObjectiveManager().getPolicy()) {
            case MINIMIZE: {
                st.append("Minimize ");
                st.append(prtfl.workers[0].getObjectiveManager().getObjective().getName()).append(" = ");
                int best = prtfl.workers[0].getObjectiveManager().getBestUB().intValue();
                for (int i = 1; i < prtfl.getNbWorkers(); i++) {
                    if (best > prtfl.workers[i].getObjectiveManager().getBestUB().intValue()) {
                        best = prtfl.workers[i].getObjectiveManager().getBestUB().intValue();
                    }
                }
                st.append(best).append(", ");
            }
            break;
            case MAXIMIZE: {
                st.append("Maximize ");
                st.append(prtfl.workers[0].getObjectiveManager().getObjective().getName()).append(" = ");
                int best = prtfl.workers[0].getObjectiveManager().getBestLB().intValue();
                for (int i = 1; i < prtfl.getNbWorkers(); i++) {
                    if (best < prtfl.workers[i].getObjectiveManager().getBestLB().intValue()) {
                        best = prtfl.workers[i].getObjectiveManager().getBestLB().intValue();
                    }
                }
                st.append(best).append(", ");
            }
            break;

        }
        long[] stats;
        if (prtfl instanceof SequentialPortfolio) {
            stats = statistics((SequentialPortfolio) prtfl);
        } else {
            stats = statistics((ParallelPortfolio) prtfl);
        }
        st.append(String.format("Resolution %.3fs, %d Nodes (%,.1f n/s), %d Backtracks, %d Fails, %d Restarts",
                stats[0] / 1000f, stats[1], stats[1] / (stats[0] / 1000f), stats[2], stats[3], stats[4]));
        System.out.printf("%% %s\n", st.toString());
    }

    public long[] statistics(SequentialPortfolio prtfl) {
        long[] stats = new long[]{0, 0, 0, 0, 0};
        for (int i = 0; i < prtfl.getNbWorkers(); i++) {
            stats[0] = Math.max(stats[0], (long) (prtfl.workers[i].getMeasures().getTimeCount() * 1000));
            stats[1] += prtfl.workers[i].getMeasures().getNodeCount();
            stats[2] += prtfl.workers[i].getMeasures().getBackTrackCount();
            stats[3] += prtfl.workers[i].getMeasures().getFailCount();
            stats[4] += prtfl.workers[i].getMeasures().getRestartCount();
        }
        return stats;
    }

    public long[] statistics(ParallelPortfolio prtfl) {
        long[] stats = new long[]{0, 0, 0, 0, 0};
        for (int i = 0; i < prtfl.getNbWorkers(); i++) {
            stats[0] = Math.max(stats[0], (long) (prtfl.workers[i].getMeasures().getTimeCount() * 1000));
            stats[1] = Math.max(stats[1], prtfl.workers[i].getMeasures().getNodeCount());
            stats[2] = Math.max(stats[2], prtfl.workers[i].getMeasures().getBackTrackCount());
            stats[3] = Math.max(stats[3], prtfl.workers[i].getMeasures().getFailCount());
            stats[4] = Math.max(stats[4], prtfl.workers[i].getMeasures().getRestartCount());
        }
        return stats;
    }
}
