package edu.isi.tiburon;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Stack;
import java.util.Vector;

import edu.isi.tiburon.KBest.KBestHeapKey;
import edu.isi.tiburon.KBest.ReverseKBestHeapKeyComparator;







public class SlimEarleyState implements Serializable {
	
	// earley state and vector of symbols
	
	
	// chart edge. has weight = product of betas 
	static class SESPair implements Serializable {
		// separate tables for:
		// two tails 
		
		// one tail 
		
		// two tails 
		private static Hashtable<SlimEarleyState, Hashtable<SlimEarleyState, SESPair>> twoTailsTable = new Hashtable<SlimEarleyState, Hashtable<SlimEarleyState, SESPair>>();
		// one tail complete
		private static Hashtable<SlimEarleyState,  SESPair> oneTailTable = new Hashtable<SlimEarleyState, SESPair>();
		
		static int twoTailsCompAddCount=0;
		static int twoTailsCompAccCount=0;
		static int twoTailsInAddCount=0;
		static int twoTailsInAccCount=0;
		static int oneTailCompAddCount=0;
		static int oneTailCompAccCount=0;
		static int oneTailInAddCount=0;
		static int oneTailInAccCount=0;
		
		boolean added=false;
		SlimEarleyState left;
		SlimEarleyState right;
		
		
		
		static private int edgeCount=0;
		static void resetEdgeCount() { edgeCount = 0;}
		static public int getEdgeCount() { return edgeCount; }
		// equality stuff
		private Hash hsh=null;
		public int hashCode() { 
			if (hsh == null) {
				setHashCode();
			}
			return hsh.bag(Integer.MAX_VALUE);
		}

		private void setHashCode() {
			hsh = new Hash();
			hsh = hsh.sag(left.getHash());
			hsh = hsh.sag(right.getHash());

		
		}

		public Hash getHash() {
			if (hsh == null) {
				setHashCode();
			}
			return hsh;
		}
		public boolean equals(Object o) {
			if (!o.getClass().equals(this.getClass()))
				return false;
			SESPair r = (SESPair)o;
			if (!r.left.equals(left))
				return false;
			if (r.right == null && right != null)
				return false;
			if (r.right != null && right == null)
				return false;
			if (r.right != null && !r.right.equals(right))
				return false;
		
			return true;
		}
		public static SESPair get(SlimEarleyState a, SlimEarleyState b) {
			if (!twoTailsTable.containsKey(a))
				twoTailsTable.put(a, new  Hashtable<SlimEarleyState, SESPair>());
			if (!twoTailsTable.get(a).containsKey(b))
				twoTailsTable.get(a).put(b, new SESPair(a, b));
			return twoTailsTable.get(a).get(b);
		}
		private SESPair(SlimEarleyState a, SlimEarleyState b) {
			left = a;
			right = b;
			edgeCount++;
		}
		public SESPair(SlimEarleyState a) {
			left = a;
			right = null;
			edgeCount++;

		}
	
		public String toString() {
			StringBuffer str = new StringBuffer(left+":");
			if (right == null)
				str.append("<NULL>");
			else
				str.append(right);
			
			return str.toString();
		}

		
	}

	
		
	
	static private Hashtable<Integer, Hashtable<Short, Hashtable<Short, Hashtable<Short, SlimEarleyState>>>> states = new Hashtable<Integer, Hashtable<Short, Hashtable<Short, Hashtable<Short, SlimEarleyState>>>>();
	// states that have transducer rule constraints
	// TODO: generalize this to vectors
	// TODO: merge this with the above table
	// will distinct but identical trees match the same here? should i index on gorn address instead?

	static private int statecount=0;
	static private int callcount=0;
	//static private int combedgecount=0;
	//static private int shiftedgecount = 0;
	static public int getCount() { return statecount; }
	static public SlimEarleyState getState(CFGRuleSet rs, CFGRule r, short rp, short ssp, short sep) {
		int ruleid = r.index;
		
		if (!states.containsKey(ruleid))
			states.put(ruleid, new Hashtable<Short, Hashtable<Short, Hashtable<Short, SlimEarleyState>>>());
		if (!states.get(ruleid).containsKey(rp))
			states.get(ruleid).put(rp, new Hashtable<Short, Hashtable<Short, SlimEarleyState>>());
		if (!states.get(ruleid).get(rp).containsKey(ssp)) {
			states.get(ruleid).get(rp).put(ssp, new Hashtable<Short, SlimEarleyState>());
			statecount++;
//			if (statecount % 100 == 0)
//				Debug.prettyDebug("Added "+statecount+" states");
		}
		if (!states.get(ruleid).get(rp).get(ssp).containsKey(sep))
			states.get(ruleid).get(rp).get(ssp).put(sep, new SlimEarleyState(rs, r, rp, ssp, sep));

		callcount++;
//		if (callcount % 10000 == 0)
//			Debug.prettyDebug("Called "+callcount+" states");
		return states.get(ruleid).get(rp).get(ssp).get(sep);
	}
	
	static public SlimEarleyState getState(StringTransducerRuleSet rs, StringTransducerRule r, short rp, short ssp, short sep) {
		int ruleid = r.getIndex();

		SlimEarleyState ret = null;
		
			if (!states.containsKey(ruleid))
				states.put(ruleid, new Hashtable<Short, Hashtable<Short, Hashtable<Short, SlimEarleyState>>>());
			if (!states.get(ruleid).containsKey(rp))
				states.get(ruleid).put(rp, new Hashtable<Short, Hashtable<Short, SlimEarleyState>>());
			if (!states.get(ruleid).get(rp).containsKey(ssp)) {
				states.get(ruleid).get(rp).put(ssp, new Hashtable<Short, SlimEarleyState>());
				statecount++;
				//			if (statecount % 100 == 0)
				//				Debug.prettyDebug("Added "+statecount+" states");
			}
			if (!states.get(ruleid).get(rp).get(ssp).containsKey(sep))
				states.get(ruleid).get(rp).get(ssp).put(sep, new SlimEarleyState(rs, r, rp, ssp, sep));

			callcount++;
			//		if (callcount % 10000 == 0)
			//			Debug.prettyDebug("Called "+callcount+" states");
			ret = states.get(ruleid).get(rp).get(ssp).get(sep);

		return ret;
	}

//	}
	static public void clear() {
		
		states = new Hashtable<Integer, Hashtable<Short, Hashtable<Short, Hashtable<Short, SlimEarleyState>>>>();

		statecount = 0;
		callcount = 0;
	}
	
	
	
	int rule;
	short rulepos;
	short stringStartPos;
	short stringEndPos;
	// integer alias for src and dst states
	// keyed on s2i, i2s
	short src;
	private short[] dst;
	// isFinished means have we moved the pointer through the rule
	boolean isFinished;
	// avoid checking lists with these flags
	// isDone means have we expanded this state
	boolean isDone;
	boolean isTodo;
	
	// track to see if isAdded. Set during agenda adding
	boolean isAdded;
	
	Vector<SESPair> next;
	// equality stuff
	private Hash hsh=null;

	// states for Earley parsing
	static int nextauto = 0;
	
	static int statesMade=0;
	static int statesKilled=0;
	
	
	
	// make sure we're really empty
	// disabled because this is probably evil
//	protected void finalize() throws Throwable {
//		try {
//			statesKilled++;
//		}
//		finally {
//			super.finalize();
//		}
//	}

	public int hashCode() { 
		if (hsh == null) {
			setHashCode();
		}
		return hsh.bag(Integer.MAX_VALUE);
	}

	private void setHashCode() {
//		hsh = new Hash(rule.getHash());
		hsh = new Hash(rule);
		hsh = hsh.sag(new Hash(rulepos));
		hsh = hsh.sag(new Hash(stringStartPos));
		hsh = hsh.sag(new Hash(stringEndPos));
	}

	public Hash getHash() {
		if (hsh == null) {
			setHashCode();
		}
		return hsh;
	}
	public boolean equals(Object o) {
		if (!o.getClass().equals(this.getClass()))
			return false;
		SlimEarleyState r = (SlimEarleyState)o;
		if (rule != r.rule)
			return false;
		if (rulepos != r.rulepos)
			return false;
		if (stringStartPos != r.stringStartPos)
			return false;
		if (stringEndPos != r.stringEndPos)
			return false;
		return true;
	}
	
	
	
	SlimEarleyState(CFGRuleSet rs, CFGRule r, short rp, short ssp, short sep) {
		statesMade++;
		rule = r.index;
		rulepos = rp;
		stringStartPos = ssp;
		stringEndPos = sep;
		// create src and dst items for later quick access
		src = rs.s2i(r.lhs);
		dst = new short[r.getRHS().getSize()];
		int counter = 0;
		for (Symbol i : r.getRHS().getLeaves()) {
			if (rs.states.contains(i))
				dst[counter] = rs.s2i(i);
			else
				dst[counter] = -1;
			counter++;
		}
		if (rulepos >= (r.getRHS().getSize()))
			isFinished = true;
		isDone=isTodo=false;
		next = null;
	
		// point top-down
		
	}

	SlimEarleyState(StringTransducerRuleSet rs, StringTransducerRule r, short rp, short ssp, short sep) {
		statesMade++;
		rule = r.getIndex();
		rulepos = rp;
		stringStartPos = ssp;
		stringEndPos = sep;
		// create src and dst items for later quick access
		src = (short)rs.s2i(r.getState());
		dst = new short[r.getRHS().getSize()];
		int counter = 0;
		for (Symbol i : r.getRHS().getLeaves()) {
			if (rs.states.contains(i))
				dst[counter] = (short)rs.s2i(i);
			else
				dst[counter] = -1;
			counter++;
		}
		if (rulepos >= (r.getRHS().getSize()))
			isFinished = true;
		isDone=isTodo=false;
		next = null;
	}

	
	
	// which, if any, nonterms should come next?
	int predict() {
		if (isFinished)
			return -1;
		// are we pointing at a member with a state?
		if (dst[rulepos] >= 0)
			return dst[rulepos];
		else
			return -1;

	}
	// attempt to shift based on provided symbol
	// edge gets added in calling method
	// also known as "scan"
	SlimEarleyState shift(Symbol sym, CFGRule[] ruleset, CFGRuleSet rs) {
		boolean debug = false;
		if (isFinished)
			return null;
		// handle epsilon rules too
		if (ruleset[rule].rhs.isEmptyString()) {
			if (debug) Debug.debug(debug, "Found epsilon rule "+rule+"; acting accordingly");
			return SlimEarleyState.getState(rs, ruleset[rule], (short)(rulepos+1), stringStartPos, stringEndPos);
		}
		if (sym == Symbol.getEpsilon())
			return null;
		Symbol cmp = ((StringItem)ruleset[rule].getRHS()).getSym(rulepos);
		if (cmp == sym)
			return SlimEarleyState.getState(rs, ruleset[rule], (short)(rulepos+1), stringStartPos, (short)(stringEndPos+1));
		return null;
	}
	
	// attempt to shift based on provided symbol
	// edge gets added in calling method
	// also known as "scan"
	SlimEarleyState shift(Symbol sym, StringTransducerRule[] ruleset, StringTransducerRuleSet rs) {
		boolean debug = false;
		if (isFinished)
			return null;
		// handle epsilon rules too
		if (ruleset[rule].rhs.isEpsilon()) {
			if (debug) Debug.debug(debug, "Found epsilon rule "+rule+"; acting accordingly");
			return SlimEarleyState.getState(rs, ruleset[rule], (short)(rulepos+1), stringStartPos, stringEndPos);
		}
		if (sym == Symbol.getEpsilon())
			return null;
		Symbol cmp = ruleset[rule].getRHS().getLabelSym(rulepos);
		if (cmp == null)
			return null;
		if (cmp == sym)
			return SlimEarleyState.getState(rs, ruleset[rule], (short)(rulepos+1), stringStartPos, (short)(stringEndPos+1));
		return null;
	}
	// add an edge to the state
	public void update(SlimEarleyState edge) {
		boolean debug = false;
		if (next != null) {
			Debug.debug(true, "WARNING: adding predict edge "+edge+" to non-empty state "+this);
		}
		// we assume it was null
		if (next == null) {
			next = new Vector<SESPair>();
			if (debug) Debug.debug(debug," created queue");			
		}
		SESPair esp = new SESPair(edge);

//		if (next.contains(v)) {
//			Debug.debug(true, " on shift, "+this+" already contains "+v);
//		}
		if (debug) Debug.debug(debug, "Adding "+esp+" to "+this);
		next.add(esp);
	}
	
	
	
	
	// assumption that if there are external rules, they are in edge2!
	
	public void update(SlimEarleyState edge1, SlimEarleyState edge2, int beam) {
		boolean debug = false;

		if (next == null) {
			next = new Vector<SESPair>();
			if (debug) Debug.debug(debug," created queue");			

		}
		//		SlimEarleyState[] v = SlimEarleyStateFactory.getCombEdge(edge1, edge2);
		SESPair esp = SESPair.get(edge1, edge2);
		if (debug) Debug.debug(debug, "Adding "+esp+" to "+this);
		next.add(esp);
	}
	
	
	
	
	
	public String toString() {
		StringBuffer str = new StringBuffer(rule+":"+rulepos+":"+stringStartPos+":"+stringEndPos);
		if (next != null) {
			//str.append("["+next.size()+" children "+"]");
			str.append("["+next.size()+" edges]");
//			for (SESPair nes : next) {
//				// dangerous!
////				str.append(nes[0].toString());
//				// less dangerous!
//				str.append(nes.left.rule+":"+nes.left.rulepos+":"+nes.left.stringStartPos+":"+nes.left.stringEndPos);
//				if (nes.right != null)
//// dangerous!
////					str.append(nes[1].toString());
//// less dangerous!
//					str.append(","+nes.right.rule+":"+nes.right.rulepos+":"+nes.right.stringStartPos+":"+nes.right.stringEndPos);
//				str.append(" ;; ");
//			}
//			str.append("]");
		}
		else {
			str.append("[null]");
		}
		str.append(isFinished);
		return str.toString();
	}


	// traverse through the chart from a single state, creating new states etc. as need be
	public Vector<CFGRule> buildCFG(
			CFGRuleSet rs,
			Symbol[][][] newSyms, 
			ArrayList<SlimEarleyState> todoList,
			CFGRuleSet parent, CFGRule[] ruleset) throws UnusualConditionException {
		boolean debug = false;
		// there used to be a done list, but this flag is good enough
		this.isDone = true;

		// get the new lhs symbol, should be by lookup
		Symbol newlhs;
		if (newSyms[src][stringStartPos][stringEndPos] != null)	{
			newlhs = newSyms[src][stringStartPos][stringEndPos];
			if (debug) Debug.debug(debug, "Found "+rule+"->"+stringStartPos+"->"+stringEndPos+"->"+newlhs);
		}
		else {
			throw new UnusualConditionException ("Couldn't find state for "+rule+":"+stringStartPos+":"+stringEndPos+
					"; these should be created in getPossibleSymbols or top level constructor");	
		}

		if (debug) Debug.debug(debug, "Gathering CFG rules to explain "+rule+" from "+stringStartPos+" to "+stringEndPos);
		Vector<CFGRule> ret = new Vector<CFGRule>();
		
		//Vector<Vector<Symbol>> stateseqs = new Vector<Vector<Symbol>>();
		//stateseqs.add(new Vector<Symbol>());
		//debug = true;
		if (debug) Debug.debug(debug, "Gathering possible symbols for "+this);
//		innermemo = new Hashtable<SlimEarleyState, Vector<Vector<Symbol>>>();
		//stateseqs = getPossibleSymbols(this, stateseqs, newSyms, todoList, 0);
		
		Vector<Vector<Symbol>> stateseqs = getPossibleSymbols(this, newSyms, todoList, 0);
		if (debug) Debug.debug(debug, "For "+this+" got "+stateseqs);


		// add new cfg rules
		Symbol[] oldrhs = ruleset[rule].getRHS().getLeaves();
		for (Vector<Symbol> newrhsstatesmaster : stateseqs) {
			Vector<Symbol> newrhsstates = new Vector<Symbol>(newrhsstatesmaster);
			// build up the new rhs by inserting literals
			Vector<Symbol> newrhs = new Vector<Symbol>();
			for (int i = 0; i < oldrhs.length; i++) {
				if (rs.states.contains(oldrhs[i]))
					newrhs.add(i, newrhsstates.remove(0));
				else
					newrhs.add(i, oldrhs[i]);
			}
			CFGRule newRule=null;
			try {
				if (debug) Debug.debug(debug, "Building from "+rule+"("+ruleset[rule]+") and "+newrhs+" and "+newlhs);
				newRule = new CFGRule(parent, newlhs, new StringItem(newrhs), ruleset[rule].weight, ruleset[rule].semiring);
				if (debug) Debug.debug(debug, "Built "+newRule);
				ret.add(newRule);
			}
			catch (DataFormatException e) {
				throw new UnusualConditionException("Can't convert "+rule+" to rtg: "+e.getMessage());
			}
			

		}
		if (debug) Debug.debug(debug, "Done building rules from "+this+"; built "+ret);
		return ret;		
	}


	
	private static Hashtable<SlimEarleyState, Vector<Vector<Symbol>>> innermemo = new Hashtable<SlimEarleyState, Vector<Vector<Symbol>>>();
	static void resetParseMemo() {
		innermemo = new Hashtable<SlimEarleyState, Vector<Vector<Symbol>>>();
	}
	
	private static Hashtable<SESPair, Vector<SlimStateSeq>> transinnermemo = new Hashtable<SESPair, Vector<SlimStateSeq>>();
	static void resetTransParseMemo() {
		transinnermemo = new Hashtable<SESPair, Vector<SlimStateSeq>>();
	}
	
	// reversed insertion order
	private static Vector<Vector<Symbol>> getPossibleSymbols(
			SlimEarleyState currState, 
			Symbol[][][] newSyms, 
			ArrayList<SlimEarleyState> todoList,
			int level) {
		boolean debug = false;
		boolean memoize = true;

//		if (debug) Debug.debug(debug, level, "Exploring "+currState);
		if (currState.next == null)
			return null;
		HashSet<Vector<Symbol>> ret = new HashSet<Vector<Symbol>>();
		//			int oldrhssym = currState.src;
		// only memoize complete objects
		if (memoize && innermemo.containsKey(currState)) {
			Vector<Vector<Symbol>> memret = innermemo.get(currState);
			if (debug) Debug.debug(debug, level, "Obtained memoized "+memret+" from "+currState);
			return memret;
		}
		for (SESPair choice : currState.next) {
			// tempret gets passed downward recursively
			Vector<Vector<Symbol>> tempret; 
			// recursive call!
			if (choice.left != null && choice.left.next != null)
				tempret = getPossibleSymbols(choice.left, newSyms, todoList, level+1);				
			else {
				tempret = new Vector<Vector<Symbol>>();
				tempret.add(new Vector<Symbol>());
			}
			if (debug) Debug.debug(debug, level, "After recursion, vectors for "+currState+" at "+tempret);

			
//			if (choice[1] == null) {
//				tempret = existing;
//			}
//			else if (memoize && innermemo.containsKey(choice[1])) {
//				tempret = innermemo.get(choice[1]);
//				if (debug) Debug.debug(debug, level, "Obtained memoized "+tempret+" from "+choice[1]);
//			}
				
			Vector<Vector<Symbol>> nextret = new Vector<Vector<Symbol>>();; 
			if (choice.right != null) {
				for (Vector<Symbol> oldmember : tempret) {
					Vector<Symbol> newmember = new Vector<Symbol>(oldmember);
					SlimEarleyState child = choice.right;
					int rhssym = child.src;
					// access symbol related to state, start, end
					if (newSyms[rhssym][child.stringStartPos][child.stringEndPos] == null) 
						newSyms[rhssym][child.stringStartPos][child.stringEndPos] = SymbolFactory.getStateSymbol(rhssym+":"+child.stringStartPos+":"+child.stringEndPos);						
					newmember.add(newSyms[rhssym][child.stringStartPos][child.stringEndPos]);
					if (!child.isDone && !child.isTodo) {
						//						if (debug) Debug.debug(debug, level, "Adding "+child+" to todo list");
						child.isTodo = true;
						todoList.add(child);
					}
					//					if (debug) Debug.debug(debug, level, "Adding "+newmember+" to vector");

					nextret.add(newmember);
					if (debug) Debug.debug(debug, level, "Vectors for "+currState+" now at "+nextret);
				}
			}

			ret.addAll(nextret);
			if (debug) Debug.debug(debug, level, "FINALLY, vectors for "+currState+" at "+nextret);

		}
		Vector<Vector<Symbol>> memret = new Vector<Vector<Symbol>>(ret);
		if (memoize) {
			if (debug) Debug.debug(debug, level, "Placing memoized "+memret+" keyed on "+currState);
			innermemo.put(currState, memret);
		}
				
		if (memret.size() == 0)
			memret.add(new Vector<Symbol>());
		return memret;
	}
}

