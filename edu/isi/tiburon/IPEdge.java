package edu.isi.tiburon;

import java.util.HashMap;
import java.util.Vector;

// hyper edge connecting left and possible right state to top
public class IPEdge {

	private IPUnfState left;
	private IPFinState right;
	private IPUnfState dst;
	
	// default constructor
	private IPEdge(IPUnfState d, IPUnfState l, IPFinState r) {
		dst = d;
		left = l;
		right = r;
	}
	private IPEdge(IPUnfState d, IPUnfState l) {
		this(d, l, null);
	}
	
	// memoized based on objects
	
	private static HashMap<IPUnfState, HashMap<IPUnfState, HashMap<IPFinState, IPEdge>>> memo_ipfulledge;
	private static HashMap<IPUnfState, HashMap<IPUnfState, IPEdge>> memo_ippartedge;

	static int createcount = 0;
	static int memocount = 0;
	public static IPEdge get(IPUnfState d, IPUnfState l) {
		boolean ismemo=true;
		if (ismemo) {
			if (memo_ippartedge == null)
				memo_ippartedge = new HashMap<IPUnfState, HashMap<IPUnfState, IPEdge>>();
			if (!memo_ippartedge.containsKey(d))
				memo_ippartedge.put(d, new HashMap<IPUnfState, IPEdge>());
			if (!memo_ippartedge.get(d).containsKey(l))
				memo_ippartedge.get(d).put(l, new IPEdge(d, l));
			return memo_ippartedge.get(d).get(l);
		}
		else
			return new IPEdge(d, l);
	}
	public static IPEdge get(IPUnfState d, IPUnfState l, IPFinState r) {
		boolean ismemo=true;
		if (ismemo) {
			if (memo_ipfulledge == null)
				memo_ipfulledge = new HashMap<IPUnfState, HashMap<IPUnfState, HashMap<IPFinState, IPEdge>>>();
			if (!memo_ipfulledge.containsKey(d))
				memo_ipfulledge.put(d, new HashMap<IPUnfState, HashMap<IPFinState, IPEdge>>());
			if (!memo_ipfulledge.get(d).containsKey(l))
				memo_ipfulledge.get(d).put(l, new HashMap<IPFinState, IPEdge>());
			if (!memo_ipfulledge.get(d).get(l).containsKey(r)) {
				createcount++;			
				memo_ipfulledge.get(d).get(l).put(r, new IPEdge(d, l, r));
			}
			else
				memocount++;
			if (createcount % 1000 == 0)
				Debug.debug(true, "Created "+createcount+" full edges");
			if (memocount % 1000 == 0 && memocount > 0)
				Debug.debug(true, "Avoided creating "+memocount+" full edges");
			return memo_ipfulledge.get(d).get(l).get(r);
		}
		else				
			return new IPEdge(d, l, r);
	}
	
	
	public boolean isSingle() {
		return right==null;
	}
	public IPUnfState getLeft() { return left; }
	public IPFinState getRight() { return right; }
	public IPUnfState getDst() { return dst; }
	public String toString() { return dst+" -> "+left+":"+right; }
	
	// get possible sequences of IPFinState children by, for each edge, taking the
	// product of the sequences on the left with the state on the right (or promoting
	// the seqs on the left). No memoization here.
	 
	public Vector<Vector<IPFinState>> getSequences() {
		boolean debug = false;
		Vector<Vector<IPFinState>> ret = new Vector<Vector<IPFinState>>();
		
		if (isSingle())
			ret.addAll(getLeft().getSequences());
		else {
			for (Vector<IPFinState> lvec : getLeft().getSequences()) {
				Vector<IPFinState> vec = new Vector<IPFinState>(lvec);
				vec.add(getRight());
				ret.add(vec);
			}
		}
		if (ret.size() == 0)
			ret.add(new Vector<IPFinState>());
		return ret;
	}
}
