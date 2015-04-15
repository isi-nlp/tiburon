package edu.isi.tiburon;

import java.util.Vector;

// a sequence of variables represented as state, tree pairs paired with a TrainingString substring that indicates
// these variables must explain (derive to) this string. Used by xRs training.

// this also can be a sequence of single symbols, representing states. Used by CFG training

public class CascadeVariableCluster {
	private Vector<Symbol> variables;
	private TrainingString string;
	private Symbol sym=null;

	// hashcode and equals stuff
	// hash made to be able to match StateTreeString - each state tree, then the string
	// and add the components of the state tree, not the state tree pair itself
	private Hash hsh=null;

	private static int nextIndex = 1;
	public static void resetNextIndex() { nextIndex = 1; }
	public static int getNextIndex() { return nextIndex++; }

	private void setHashCode() {
		hsh = null;

		// note the unusual construction!

		for (Symbol r : variables) {
			hsh = hsh == null ? new Hash(r.getHash()) : hsh.sag(r.getHash());
		}
		if (string != null)
			hsh = hsh.sag(string.getHash());
	}

	public int hashCode() {
		if (hsh == null)
			setHashCode();
		return  hsh.bag(Integer.MAX_VALUE);
	}
	public Hash getHash() {
		if (hsh == null)
			setHashCode();
		return hsh;
	}
	public boolean equals(Object o) {
		if (!o.getClass().equals(this.getClass()))
			return false;
		CascadeVariableCluster s = (CascadeVariableCluster)o;
		if (variables.size() != s.variables.size())
			return false;

		for (int i = 0; i < variables.size(); i++) {
			Object r = variables.get(i);
			Object sr = s.variables.get(i);
			if (!r.equals(sr))
				return false;
		}
		return string.equals(s.string);
	}
	public CascadeVariableCluster() {
		variables = new Vector();
		string = null;
	}
	public CascadeVariableCluster(TrainingString t) {
		string = t;
		variables = new Vector();
	}
	public CascadeVariableCluster(TrainingString t, Vector v) {
		string = t;
		variables = v;
	}
	public CascadeVariableCluster(Symbol s, TrainingString to) {
		string = to;
		variables = new Vector<Symbol>();
		//IndexSymbol is = IndexSymbol.get(s, Index.get(to.getStartIndex(), to.getEndIndex()));
		variables.add(s);
	}
//	// for xRs training
//	public CascadeVariableCluster(StateTreePair st, TrainingString to) {
//		this(st.getState(), st.getTree(), st.getStart(), st.getEnd(), to, to.getStartIndex(), to.getEndIndex());
//	}
//	// for CFG training
//	public CascadeVariableCluster(Symbol st, TrainingString to) {
//		this(st, null, 0, 0, to, to.getStartIndex(), to.getEndIndex());
//	}

	// copy constructor
	public CascadeVariableCluster(CascadeVariableCluster old) {
		variables = new Vector(old.variables);
		string = old.string;
	}
	// settors - only set hash code if it already exists; this prevents
	// setting during construction
	public void addVariable(StateTreePair v) {
		variables.add(v);
		if (hsh != null)
			setHashCode();
	}

	// symbols can be members instead of stateTreePairs (for RuleSetTraining)
	public void addVariable(Symbol v) {
		variables.add(v);
		if (hsh != null)
			setHashCode();
	}

	public void setString(TrainingString s) {
		string = s;
		if (hsh != null)
			setHashCode();
	}
	// accessors
	public int numVariables() {
		return variables.size();
	}

	// for when variablecluster is a statestring
	public Symbol state() {
		return variables.get(0);
	}
	
	public TrainingString out() {
		return string;
	}
	public int[] align() {
		int [] alint = new int[4];
		if (variables.get(0) instanceof StateTreePair) {
			alint[0] = ((StateTreePair)variables.get(0)).getStart();
			alint[1] = ((StateTreePair)variables.get(0)).getEnd();
		}
		else {
			alint[0] = 0;
			alint[1] = 0;
		}
		alint[2] = string.getStartIndex();
		alint[3] = string.getEndIndex();
		return alint;
	}

	// use sparingly!
	public Symbol getSymbol() {
		if (sym == null)
			deriveSymbol();
		return sym;
	}
	private void deriveSymbol() {
		StringBuffer ret = new StringBuffer();
		for (int i = 0; i < variables.size(); i++) {
			ret.append(variables.get(i).toString());
			if (i < (variables.size() - 1))
				ret.append(";");
		}
		ret.append(":"+string.toString()+"("+string.getStartIndex()+", "+string.getEndIndex()+")");
		sym = SymbolFactory.getSymbol(ret.toString());
	}

	public TrainingString getString() { return string; }
	// for transducer training
	//public StateTreePair getVariable(int i) { return (StateTreePair)variables.get(i);}
	// for ruleset training
	public Symbol getVariable(int i) { return (Symbol)variables.get(i); }

	// subcluster - given an index, lop off the first variable and truncate the training string to the point after the index
	// error if there are no more variables (this should only fire when there's 2 or more)
	// return null if the training string is empty
	public CascadeVariableCluster getSubCluster(int i) {
		try {
			TrainingString nexts = string.getSubString(i);
			if (nexts == null)
				return null;
			Vector nextVar = new Vector(variables);
			nextVar.remove(0);
			if (nextVar.size() < 1)
				throw new Exception("CascadeVariableCluster:getSubCluster: attempted to get a subcluster with no variables");
			return new CascadeVariableCluster(nexts, nextVar);
		}
		catch (Exception e) {
			System.err.println(e.getMessage());
		}
		Debug.debug(true, "CascadeVariableCluster:getSubCluster: shouldn't be at this point!");
		return null;
	}

	// prevents temptation
	public String toString() {
//		return "not available";
		return string+":"+variables;
		//if (sym == null)
		// 	    deriveSymbol();
		// 	return sym.toString();
	}

}
