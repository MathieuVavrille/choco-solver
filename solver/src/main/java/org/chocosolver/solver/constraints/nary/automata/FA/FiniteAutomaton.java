/*
 * This file is part of choco-solver, http://choco-solver.org/
 *
 * Copyright (c) 2020, IMT Atlantique. All rights reserved.
 *
 * Licensed under the BSD 4-clause license.
 *
 * See LICENSE file in the project root for full license information.
 */
package org.chocosolver.solver.constraints.nary.automata.FA;

import dk.brics.automaton.*;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.chocosolver.solver.exception.SolverException;
import org.chocosolver.util.tools.StringUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: julien
 * Date: Mar 15, 2010
 * Time: 12:53:23 PM
 */
public class FiniteAutomaton implements IAutomaton {

    //***********************************************************************************
   	// VARIABLES
   	//***********************************************************************************

    private Automaton representedBy;
    private Object2IntMap<State> stateToIndex;
    private ArrayList<State> states;
    private IntSet alphabet;
    private int nbStates;
    private final HashSet<State> nexts = new HashSet<>();
    private int min = Character.MIN_VALUE;
    private int max = Character.MAX_VALUE;

    private final static Int2IntMap charFromIntMap = new Int2IntOpenHashMap();
    private final static Int2IntMap intFromCharMap = new Int2IntOpenHashMap();

    static {
        int delta = 0;
        for (int i = Character.MIN_VALUE; i <= Character.MAX_VALUE; i++) {
            while ((char) (i + delta) == '"' || (char) (i + delta) == '{' || (char) (i + delta) == '}' || (char) (i + delta) == '<' ||
                    (char) (i + delta) == '>' || (char) (i + delta) == '[' || (char) (i + delta) == ']' ||
                    (char) (i + delta) == '(' || (char) (i + delta) == ')') delta++;
            charFromIntMap.put(i, i + delta);
            intFromCharMap.put(i + delta, i);
        }

    }

    //***********************************************************************************
   	// CONSTRUCTORS
   	//***********************************************************************************

    public FiniteAutomaton() {
        this.representedBy = new Automaton();
        this.stateToIndex = new Object2IntOpenHashMap<>();
        this.states = new ArrayList<>();

        this.alphabet = new IntOpenHashSet();

    }

    /**
     * Create a finite automaton based on a regular expression.
     * The regexp accepts digits  and numbers, in [0,65535].
     * However, to distinguish a number from a suite of digits, the former must be surrounded by '<' and '>'.
     * For instance, "12<34>" stands for a '1' (digit), followed by a '2' (digit) followed by a '34' (number).
     *
     * @param regexp the regular expression
     * @param min    an overall minimum value for transitions
     * @param max    an overall maximum value for transitions
     */
    public FiniteAutomaton(String regexp, int min, int max) {
        this();
        String correct = StringUtils.toCharExp(regexp);
        this.representedBy = new RegExp(correct).toAutomaton();
        this.min = Math.max(Character.MIN_VALUE, min);
        this.max = Math.min(Character.MAX_VALUE, max);
        syncStates();
    }

    /**
     * Create a finite automaton based on a regular expression.
     * The regexp accepts digits  and numbers, in [0,65535].
     * However, to distinguish a number from a suite of digits, the former must be surrounded by '<' and '>'.
     * For instance, "12<34>" stands for a '1' (digit), followed by a '2' (digit) followed by a '34' (number).
     *
     * @param regexp the regular expression
     */
    public FiniteAutomaton(String regexp) {
        this(regexp, Character.MIN_VALUE, Character.MAX_VALUE);
    }

    public FiniteAutomaton(FiniteAutomaton other) {
        perfectCopy(other);
    }

    private FiniteAutomaton(Automaton a, IntSet alphabet) {
        this();
        fill(a, alphabet);
    }

    //***********************************************************************************
   	// STATIC METHODS
   	//***********************************************************************************

    public static int getIntFromChar(char c) {
        return intFromCharMap.get(c);
    }

    public static char getCharFromInt(int i) {
        int c = charFromIntMap.get(i);
        if (c > -1) {
            return (char) charFromIntMap.get(i);
        } else {
            throw new SolverException("Unknown value \"" + i + "\". Note that only integers in [" +
                    (int) Character.MIN_VALUE + "," + (int) (Character.MAX_VALUE) + "] are allowed by FiniteAutomaton.");
        }
    }

    public static int max(IntSet hs) {
        int max = Integer.MIN_VALUE;
        for (IntIterator it = hs.iterator(); it.hasNext(); ) {
            int n = it.nextInt();
            if (n > max)
                max = n;
        }
        return max;
    }

    private static int min(IntSet hs) {
        int min = Integer.MAX_VALUE;
        for (IntIterator it = hs.iterator(); it.hasNext(); ) {
            int n = it.nextInt();
            if (n < min)
                min = n;
        }
        return min;
    }

    //***********************************************************************************
   	// API METHODS
   	//***********************************************************************************

    public void fill(Automaton a, IntSet alphabet) {

        int max = max(alphabet);
        int min = min(alphabet);


        this.setDeterministic(a.isDeterministic());

        HashMap<State, State> m = new HashMap<>();
        Set<State> states = a.getStates();
        for (State s : states) {
            this.addState();
            State ns = this.states.get(this.states.size() - 1);
            m.put(s, ns);

        }
        for (State s : states) {
            State p = m.get(s);
            int source = stateToIndex.getInt(p);
            p.setAccept(s.isAccept());
            if (a.getInitialState().equals(s))
                representedBy.setInitialState(p);
            for (Transition t : s.getTransitions()) {
                int tmin = getIntFromChar(t.getMin());
                int tmax = getIntFromChar(t.getMax());
                State dest = m.get(t.getDest());
                int desti = stateToIndex.getInt(dest);
                int minmax = Math.min(max, tmax);
                for (int i = Math.max(min, tmin); i <= minmax; i++) {
                    if (alphabet.contains(i))
                        this.addTransition(source, desti, i);
                }
            }

        }
    }

    public int getNbStates() {
        return nbStates;
    }

    public int getNbSymbols() {
        return alphabet.size();
    }

    public int addState() {
        int idx = states.size();
        State s = new State();
        states.add(s);
        stateToIndex.put(s, idx);
        nbStates++;
        return idx;
    }

    @SuppressWarnings("unused")
    public void removeSymbolFromAutomaton(int symbol) {
        char c = getCharFromInt(symbol);
        ArrayList<Triple> triples = new ArrayList<>();
        for (int i = 0; i < states.size(); i++) {
            State s = states.get(i);
            for (Transition t : s.getTransitions()) {

                if (t.getMin() <= c && t.getMax() >= c) {
                    triples.add(new Triple(i, stateToIndex.getInt(t.getDest()), symbol));
                }
            }
            for (Triple t : triples) {
                this.deleteTransition(t.a, t.b, t.c);
            }
            triples.clear();
        }
        alphabet.remove(symbol);
        //this.representedBy.reduce();
        // this.syncStates();
    }

    public void addTransition(int source, int destination, int... symbols) {
        for (int symbol : symbols) {
            try {
                checkState(source, destination);
            } catch (StateNotInAutomatonException e) {
//                LOGGER.warn("Unable to addTransition : " + e);
            }
            alphabet.add(symbol);
            State s = states.get(source);
            State d = states.get(destination);
            s.addTransition(new Transition(getCharFromInt(symbol), d));
        }
    }

    public void deleteTransition(int source, int destination, int symbol) {
        try {
            checkState(source, destination);
        } catch (StateNotInAutomatonException e) {
//            LOGGER.warn("Unable to delete transition : " + e);
        }
        State s = states.get(source);
        State d = states.get(destination);
        Set<Transition> transitions = s.getTransitions();
        Set<Transition> nTrans = new HashSet<>();
        char c = getCharFromInt(symbol);
        Iterator<Transition> it = transitions.iterator();
        while (it.hasNext()) {
            Transition t = it.next();
            if (t.getDest().equals(d) && t.getMin() <= c && t.getMax() >= c) {
                it.remove();

                if (t.getMin() == c && c < t.getMax()) {
                    nTrans.add(new Transition((char) (c + 1), t.getMax(), d));
                } else if (t.getMin() > c && c == t.getMax()) {
                    nTrans.add(new Transition(t.getMin(), (char) (c - 1), d));
                } else if (t.getMin() < c && c < t.getMax()) {
                    nTrans.add(new Transition(t.getMin(), (char) (c - 1), d));
                    nTrans.add(new Transition((char) (c + 1), t.getMax(), d));
                }
            }
        }
        transitions.addAll(nTrans);
    }

    public int delta(int source, int symbol) throws NonDeterministicOperationException {
        if (!representedBy.isDeterministic()) {
            throw new NonDeterministicOperationException();
        }
        try {
            checkState(source);
        } catch (StateNotInAutomatonException e) {
//            LOGGER.warn("Unable to perform delta lookup, state not in automaton : " + e);
        }
        State s = states.get(source);
        State d = s.step(getCharFromInt(symbol));
        if (d != null) {
            return stateToIndex.getInt(d);
        } else
            return -1;

    }

    public void delta(int source, int symbol, IntSet states) {
        try {
            checkState(source);
        } catch (StateNotInAutomatonException e) {
//            LOGGER.warn("Unable perform delta lookup, state not in automaton : " + e);
        }
        State s = this.states.get(source);
        nexts.clear();
        s.step(getCharFromInt(symbol), nexts);
        for (State to : nexts) {
            states.add(stateToIndex.getInt(to));
        }
    }

    @SuppressWarnings("unused")
    public void addToAlphabet(int a) {
        alphabet.add(a);
    }

    public void removeFromAlphabet(int a) {
        alphabet.remove(a);
    }

    public int getInitialState() {
        State s = representedBy.getInitialState();
        if (s == null)
            return -1;
        else
            return stateToIndex.getInt(s);
    }

    public boolean isFinal(int state) {
        try {
            checkState(state);
        } catch (StateNotInAutomatonException e) {
//            LOGGER.warn("Unable to check if this state is final : " + e);
        }
        return states.get(state).isAccept();
    }

    public boolean isNotFinal(int state) {
        try {
            checkState(state);
        } catch (StateNotInAutomatonException e) {
//            LOGGER.warn("Unable to check if this state is final : " + e);
        }
        return !states.get(state).isAccept();
    }

    public void setInitialState(int state) {
        try {
            checkState(state);
        } catch (StateNotInAutomatonException e) {
//            LOGGER.warn("Unable to set initial state, state is not in automaton : " + e);
        }
        representedBy.setInitialState(states.get(state));
    }

    public void setFinal(int state) {
        try {
            checkState(state);
        } catch (StateNotInAutomatonException e) {
//            LOGGER.warn("Unable to set final state, state is not in automaton : " + e);
        }
        states.get(state).setAccept(true);
    }

    public void setFinal(int... states) {
        for (int s : states) setFinal(s);
    }

    public void setNonFinal(int state) {
        try {
            checkState(state);
        } catch (StateNotInAutomatonException e) {
//            LOGGER.warn("Unable to set non final state, state is not in automaton : " + e);
        }
        states.get(state).setAccept(false);
    }

    @SuppressWarnings("unused")
    public void setNonFInal(int... states) {
        for (int s : states) setNonFinal(s);
    }

    public boolean run(int[] word) {
        StringBuilder b = new StringBuilder();
        for (int i : word) {
            char c = getCharFromInt(i);
            b.append(c);
        }
        return representedBy.run(b.toString());
    }

    @SuppressWarnings("unused")
    public Automaton makeBricsAutomaton() {
        return representedBy.clone();
    }

    public IAutomaton repeat() {
        return new FiniteAutomaton(this.representedBy.repeat(), alphabet);
    }

    public IAutomaton repeat(int min) {
        return new FiniteAutomaton(this.representedBy.repeat(min), alphabet);
    }

    public IAutomaton repeat(int min, int max) {
        return new FiniteAutomaton(this.representedBy.repeat(min, max), alphabet);
    }

    public void minimize() {
        this.representedBy.minimize();
        syncStates();
    }

    public void reduce() {
        this.representedBy.reduce();
        syncStates();
    }

    public void removeDeadTransitions() {
        this.representedBy.removeDeadTransitions();
        syncStates();
    }

    public FiniteAutomaton union(FiniteAutomaton otherI) {
        Automaton union = this.representedBy.union(otherI.representedBy);
        IntSet alphabet = new IntOpenHashSet(this.alphabet);
        alphabet.addAll(otherI.alphabet);
        return new FiniteAutomaton(union, alphabet);

    }

    public FiniteAutomaton intersection(IAutomaton otherI) {
        FiniteAutomaton other = (FiniteAutomaton) otherI;
        Automaton inter = this.representedBy.intersection(other.representedBy);
        IntSet alphabet = new IntOpenHashSet();
        for (int a : this.alphabet.toIntArray()) {
            if (other.alphabet.contains(a))
                alphabet.add(a);
        }
        return new FiniteAutomaton(inter, alphabet);
    }

    public FiniteAutomaton complement(IntSet alphabet) {
        Automaton comp = this.representedBy.complement();
        return new FiniteAutomaton(comp, alphabet);
    }

    public FiniteAutomaton complement() {
        return complement(alphabet);
    }

    public FiniteAutomaton concatenate(FiniteAutomaton otherI) {
        Automaton conc = this.representedBy.concatenate(otherI.representedBy);
        IntSet alphabet = new IntOpenHashSet(this.alphabet);
        alphabet.addAll(otherI.alphabet);
        return new FiniteAutomaton(conc, alphabet);
    }

    public void addEpsilon(int source, int destination) {
        try {
            checkState(source, destination);
        } catch (StateNotInAutomatonException e) {
//            LOGGER.warn("Unable to add epsilon transition, a state is not in the automaton : " + e);

        }
        State s = states.get(source);
        State d = states.get(destination);


        ArrayList<StatePair> pairs = new ArrayList<>();
        pairs.add(new StatePair(s, d));
        this.representedBy.addEpsilons(pairs);
    }

    public boolean isDeterministic() {
        return this.representedBy.isDeterministic();
    }

    public void setDeterministic(boolean deterministic) {
        this.representedBy.setDeterministic(deterministic);
    }

    public IntSet getFinalStates() {
        IntSet finals = new IntOpenHashSet();
        for (int i = 0; i < states.size(); i++) {
            if (states.get(i).isAccept())
                finals.add(i);
        }
        return finals;
    }

    public void toDotty(String f) {
        String s = this.toDot();
        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter(new File(f)));
            bw.write(s);
            bw.close();
        } catch (IOException e) {
//            System.err.println("Unable to write dotty file " + f);
        }
    }

    public String toDot() {
        StringBuilder b = new StringBuilder("digraph Automaton {\n");
        b.append("  rankdir = LR;\n");
        Set<State> states = this.representedBy.getStates();
        // setStateNumbers(states);
        for (State s : states) {
            int idx = stateToIndex.getInt(s);
            b.append("  ").append(idx);
            if (s.isAccept())
                b.append(" [shape=doublecircle];\n");
            else
                b.append(" [shape=circle];\n");
            if (s == this.representedBy.getInitialState()) {
                b.append("  initial [shape=plaintext,label=\"\"];\n");
                b.append("  initial -> ").append(idx).append("\n");
            }
            for (Transition t : s.getTransitions()) {
                b.append("  ").append(idx);
                appendDot(t, b);
            }
        }
        return b.append("}\n").toString();
    }

    public IntSet getAlphabet() {
        return alphabet;
    }

    public List<int[]> getTransitions() {
        List<int[]> transitions = new ArrayList<>();
        for (int i = 0; i < states.size(); i++) {
            transitions.addAll(getTransitions(i));
        }
        return transitions;
    }

    public List<int[]> getTransitions(int state) {
        List<int[]> transitions = new ArrayList<>();
        for (Transition t : states.get(state).getTransitions()) {
            //noinspection DuplicatedCode
            int dest = stateToIndex.getInt(t.getDest());
            char m = (char) Math.max(min, t.getMin());
            char M = (char) Math.min(max, t.getMax());
            for (char c = m; c <= M; c++) {
                int symbol = getIntFromChar(c);
                transitions.add(new int[]{state, dest, symbol});
            }
        }
        return transitions;
    }

    @SuppressWarnings("unused")
    public ArrayList<int[]> _removeSymbolFromAutomaton(int alpha) {
        char c = getCharFromInt(alpha);
        IntSet setOfRemoved = new IntOpenHashSet();
        ArrayList<Triple> triples = new ArrayList<>();
        for (int i = 0; i < states.size(); i++) {
            State s = states.get(i);
            for (Transition t : s.getTransitions()) {

                if (t.getMin() <= c && t.getMax() >= c) {
                    triples.add(new Triple(i, stateToIndex.getInt(t.getDest()), alpha));
                    setOfRemoved.add(i);
                }
            }
            for (Triple t : triples) {
                this.deleteTransition(t.a, t.b, t.c);
            }
            triples.clear();

        }
        alphabet.remove(alpha);
        // this.removeDeadTransitions();
        ArrayList<int[]> couple = new ArrayList<>();
        for (int i = 0; i < states.size(); i++) {
            State s = states.get(i);
            for (Transition t : s.getTransitions()) {
                int dest = stateToIndex.getInt(t.getDest());
                if (setOfRemoved.contains(dest)) {
                    for (char d = t.getMin(); d <= t.getMax(); d++)
                        couple.add(new int[]{i, getIntFromChar(d)});
                }

            }


        }
        return couple;


        //this.representedBy.reduce();
        // this.syncStates();


    }

    public FiniteAutomaton clone() throws CloneNotSupportedException {
        FiniteAutomaton auto = (FiniteAutomaton) super.clone();
        auto.representedBy = new Automaton();
        auto.states = new ArrayList<>();
        auto.stateToIndex = new Object2IntOpenHashMap<>();
        auto.alphabet = new IntOpenHashSet();
        auto.nbStates = this.nbStates;
        for (int i = 0; i < this.nbStates; i++) {
            State s = new State();
            auto.states.add(s);
            auto.stateToIndex.put(s, i);
            if (!this.isNotFinal(i))
                s.setAccept(true);
            if (this.getInitialState() == i)
                auto.representedBy.setInitialState(s);
        }
        List<int[]> transitions = this.getTransitions();
        for (int[] t : transitions) {
            auto.addTransition(t[0], t[1], t[2]);
        }
        return auto;
    }

    @Override
    public String toString() {
        return representedBy.toString();
    }

    //***********************************************************************************
   	// PRIVATE METHODS
   	//***********************************************************************************

    private void perfectCopy(FiniteAutomaton other) {
        this.representedBy = new Automaton();
        this.states = new ArrayList<>();
        this.stateToIndex = new Object2IntOpenHashMap<>();
        this.alphabet = new IntOpenHashSet();
        this.nbStates = other.nbStates;
        this.min = Math.max(Character.MIN_VALUE, other.min);
        this.max = Math.min(Character.MAX_VALUE, other.max);
        for (int i = 0; i < other.nbStates; i++) {
            State s = new State();
            this.states.add(s);
            this.stateToIndex.put(s, i);
            if (!other.isNotFinal(i))
                s.setAccept(true);
            if (other.getInitialState() == i)
                this.representedBy.setInitialState(s);
        }
        List<int[]> transitions = other.getTransitions();
        for (int[] t : transitions) {
            this.addTransition(t[0], t[1], t[2]);
        }

    }

    private void checkState(int... state) throws StateNotInAutomatonException {
        int sz = states.size();
        for (int s : state)
            if (s >= sz) {
                throw new StateNotInAutomatonException(s);
            }
    }

    private void syncStates() {
        this.alphabet.clear();
        this.states.clear();
        this.stateToIndex.clear();
        int idx = 0;
        for (State s : representedBy.getStates()) {
            states.add(s);
            stateToIndex.put(s, idx++);
            for (Transition t : s.getTransitions()) {
                char m = (char) Math.max(min, t.getMin());
                char M = (char) Math.min(max, t.getMax());
                for (char c = m; c <= M; c++) {
                    alphabet.add(getIntFromChar(c));
                }
            }
        }
        nbStates = states.size();
    }

    private void appendDot(Transition t, StringBuilder b) {
        int destIdx = stateToIndex.getInt(t.getDest());

        b.append(" -> ").append(destIdx).append(" [label=\"");
        b.append("{");
        b.append(getIntFromChar(t.getMin()));
        if (t.getMin() != t.getMax()) {
            char m = (char) (Math.max(min, t.getMin())+1);
            char M = (char) Math.min(max, t.getMax());
            for (char c = m; c <= M; c++) {
                b.append(",");
                b.append(getIntFromChar(c));
            }
        }
        b.append("}");
        b.append("\"]\n");
    }
}
