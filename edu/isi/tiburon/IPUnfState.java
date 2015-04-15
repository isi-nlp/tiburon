package edu.isi.tiburon;

import java.util.HashMap;
import java.util.Vector;

// unfinished integrated parsing state. lhs state, rhs sequence, pointer to next item

// integers used for states and literals -- lastLiteral must be set before any processing
// or all symbols will be assumed states

public class IPUnfState implements IPState {
	private int lhs;
	private Vector<Integer> rhs;
	private int start;
	private int end;
	private int pos;
	private Vector<IPEdge> edges;
	// only do finishes on the last edge
	private IPEdge lastEdge;
	private double weight;
	// easiest way to tell if we've processed this state
	private boolean isAdded;
	
	private static int LAST_LITERAL=0;
	// anything lower is state
	
	private static int EPSILON=-1;
	
	static int createcount = 0;
	static int memocount = 0;
	
	public static void setLastLiteral(int i) { LAST_LITERAL=i;}
	public static int getLastLiteral() { return LAST_LITERAL; }
	public static void setEpsilon(int i) { EPSILON=i; } 
	// simple constructor
	private IPUnfState(
			int l, 
			Vector<Integer> r,  
			int s, int e, int p) {
		lhs = l;
		rhs = r;
		start = s;
		end = e;
		pos = p;
		edges = new Vector<IPEdge>();
		lastEdge = null;
		isAdded=false;
	}
	// memoizer
	// memoized based on string rep
	private static HashMap<String, IPUnfState> memo_ipunfstate;
	public static IPUnfState get(
			int l, 
			Vector<Integer> r, 
			int s, int e, int p) {
		boolean ismemo = true;
		boolean debug = true;
		if (ismemo) {
			if (memo_ipunfstate == null)
				memo_ipunfstate = new HashMap<String, IPUnfState>();
			String str = new String(s+":"+e+":"+l+r+p);
			if (!memo_ipunfstate.containsKey(str)) {
				createcount++;
				if (debug && createcount % 1000 == 0)
					Debug.debug(debug, "Created "+createcount+" unf states");
				memo_ipunfstate.put(str, new IPUnfState(l, r, s, e, p));
			}
			else {
				memocount++;
				if (debug && memocount % 1000 == 0)
					Debug.debug(debug, "Reused "+memocount+" unf states");
			}
			return memo_ipunfstate.get(str);
		}
		else {
			return new IPUnfState(l, r, s, e, p);
		}
	}
	
	
	
	// base state is just lhs
	public int getBaseState() {
		return lhs;
	}

	public int getPos() { 
		return pos;
	}
	public int getEnd() {
		return end;
	}

	public int getStart() {
		return start;
	}
	public int getNext() {
		return rhs.get(pos);
	}
	public Vector<Integer> getRHS() {
		return rhs;
	}
	public IPEdge getLastEdge() { return lastEdge; }
	public boolean isNextLit() {
		return (rhs.get(pos) <= LAST_LITERAL);
	}
	// is finished if we're at the end of the rhs and ready to be finished
	public boolean isFinished() {
		return (pos >= rhs.size());
	}
	
	public boolean isAdded() { return isAdded; }
	public void setAdded() { isAdded=true; }
	// edge affects getSequences. Hopefully none will be added after it is called
	public void addEdge(IPEdge edge) {
		edges.add(edge);
		lastEdge = edge;
		if (memo_sequences != null) {
			Debug.debug(true, "Warning: adding edge "+edge+" to "+this+" after getting sequences");
			memo_sequences = null;
		}
	}
	
	// init: get all states given a symbol
	public static Vector<IPUnfState> init(int state, IPTemplateAccess templates) {
		Vector<IPUnfState> ret = new Vector<IPUnfState>();
		for (Vector<Integer> newrhs : templates.getTemplates(state)) {
				ret.add(get(state, newrhs, 0, 0, 0));
			}
		return ret;
	}
	
	// predict: return all extant states from this one
	// return nothing (an empty vector) if not pointing to a state 
	public Vector<IPUnfState> predict(IPTemplateAccess templates) {
		boolean debug = false;
		Vector<IPUnfState> ret = new Vector<IPUnfState>();
		if (!isFinished() && !isNextLit()) {
			for (Vector<Integer> newrhs : templates.getTemplates(rhs.get(pos))) {
				if (debug) Debug.debug(debug, "Found in templates starting with "+rhs.get(pos)+" in "+this+", "+newrhs);
				ret.add(get(rhs.get(pos), newrhs, end, end, 0));
			}
		}
		return ret;
	}
	
	// shift: if the passed in symbol matches what we're pointing to, 
	// return the shifted version of this state
	// also okay if we're pointing at epsilon, though indices don't change
	// add in an edge from this to the return
	
	// otherwise return null
	public IPUnfState shift(int word) {
		
		IPUnfState ret = null;
		if (!isFinished() && 
				isNextLit()) {
			int currsym = rhs.get(pos);
			if (currsym == EPSILON) {
				ret = get(lhs, rhs, start, end, pos+1);
				ret.addEdge(IPEdge.get(ret, this));
			}
			else if (word == currsym) {
				ret = get(lhs, rhs, start, end+1, pos+1);
				ret.addEdge(IPEdge.get(ret, this));
			}
		}
		return ret;		
	}
	
	// complete: if the base state of the IPEdge matches the state we're pointing to,
	// change indices and move pointer
	
	// this is on left
	
	public IPUnfState complete(IPFinState right) {
		IPUnfState ret = null;
		// we must be pointing at something that is a state
		// our last index must equal right's first index
		// and the states must match
		if (!isFinished() && 
				!isNextLit() &&
				rhs.get(pos) == right.getBaseState() &&
				getEnd() == right.getStart()) {
			ret = get(lhs, rhs, getStart(), right.getEnd(), pos+1);
			ret.addEdge(IPEdge.get(ret, this, right));
		}
		return ret;
	}
	
	// get possible sequences of IPFinState children by, for each edge, taking the
	// product of the sequences on the left with the state on the right (or promoting
	// the seqs on the left). This is memoized.
	private Vector<Vector<IPFinState>> memo_sequences = null;
	public Vector<Vector<IPFinState>> getSequences() {
		boolean debug = false;
		boolean isMemo = false;
		if (isMemo) {
		if (memo_sequences != null) {
			if (debug) Debug.debug(debug, "Returning memoized sequences");
			return memo_sequences;
		}
		memo_sequences = new Vector<Vector<IPFinState>>();
		for (IPEdge edge : edges) {
			memo_sequences.addAll(edge.getSequences());
		}
		if (memo_sequences.size() == 0)
			memo_sequences.add(new Vector<IPFinState>());
		return memo_sequences;
		}
		else {
			Vector<Vector<IPFinState>> ret = new Vector<Vector<IPFinState>>();
			for (IPEdge edge : edges) {
				ret.addAll(edge.getSequences());
			}
			if (ret.size() == 0)
				ret.add(new Vector<IPFinState>());
			return ret;
		}
	}
	
	public String toString() {
		StringBuffer ret = new StringBuffer(lhs+" -> ");
		for (int i = 0; i < rhs.size(); i++) {
			if (pos == i)
				ret.append('*');
			ret.append(rhs.get(i)+" ");
		}
		if (pos == rhs.size())
			ret.append('*');
		ret.append("["+getStart()+":"+getEnd()+"]");
		return ret.toString();
	}
	
}
