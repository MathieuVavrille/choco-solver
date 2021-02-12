/*
 * This file is part of choco-solver, http://choco-solver.org/
 *
 * Copyright (c) 2020, IMT Atlantique. All rights reserved.
 *
 * Licensed under the BSD 4-clause license.
 *
 * See LICENSE file in the project root for full license information.
 */
package org.chocosolver.solver.variables.view;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.variables.delta.IIntDeltaMonitor;
import org.chocosolver.util.procedure.IntProcedure;
import org.chocosolver.util.procedure.SafeIntProcedure;

/**
 * A delta monitor dedicated to views
 * <br/>
 *
 * @author Charles Prud'homme
 * @since 11/01/13
 */
public abstract class ViewDeltaMonitor implements IIntDeltaMonitor {

    private class Filler implements SafeIntProcedure {

        @Override
        public void execute(int i) {
            values.add(i);
        }
    }

    private final IIntDeltaMonitor deltamonitor;
    private final IntArrayList values;
    private final Filler filler;

    public ViewDeltaMonitor(IIntDeltaMonitor deltamonitor) {
        this.deltamonitor = deltamonitor;
        values = new IntArrayList(8);
        filler = new Filler();
    }

    @Override
    public void forEachRemVal(SafeIntProcedure proc) {
        values.clear();
        deltamonitor.forEachRemVal(filler);
        for (int v = 0; v < values.size(); v++) {
            proc.execute(transform(values.getInt(v)));
        }
    }

    @Override
    public void forEachRemVal(IntProcedure proc) throws ContradictionException {
        values.clear();
        deltamonitor.forEachRemVal(filler);
        for (int v = 0; v < values.size(); v++) {
            proc.execute(transform(values.getInt(v)));
        }
    }

    @Override
   	public int sizeApproximation(){
   		return deltamonitor.sizeApproximation();
   	}

    protected abstract int transform(int value);
}
