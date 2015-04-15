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







public class EarleyState implements Serializable {
	
	// earley state and vector of symbols
	// should only be constructed through factory
	static class ESStateList {
		private static Hashtable<EarleyState, Hashtable<Vector<Symbol>, ESStateList>> stateset = new Hashtable<EarleyState, Hashtable<Vector<Symbol>, ESStateList>> ();
		EarleyState es;
		Vector<Symbol> ex;
		private Symbol sym;
		boolean isTodo=false;
		boolean isDone=false;
		private ESStateList(EarleyState e, Vector<Symbol> x) {
			es = e;
			ex = x;
		}
		public static ESStateList get(EarleyState e, Vector<Symbol> x) {
			if (!stateset.containsKey(e))
				stateset.put(e, new Hashtable<Vector<Symbol>, ESStateList>());
			if (!stateset.get(e).containsKey(x))
				stateset.get(e).put(x, new ESStateList(e, x));
			return stateset.get(e).get(x);
		}
		
		// sym is just the state info we care about: the chain of external states
		// and the indices
		// thus, more than one unique ESStateList goes to the same symbol representation!
		// TODO: not actually have this many esstatelists!
		public Symbol getSym() {
			if (sym == null) {
				StringBuffer exStates = new StringBuffer();
				for (Symbol s : ex)
					exStates.append(s+":");
//				exStates.append(es.toString());
				//exStates.append(es.src+":[");
				//for (int i=0; i< es.dst.length; i++)
				//	exStates.append(es.dst[i]+",");
				exStates.append(es.stringStartPos+":"+es.stringEndPos);
				sym = SymbolFactory.getSymbol(exStates.toString());
			}
			return sym;
		}
		public String toString() { return es+":"+ex; }
	}
	
	// chart edge. has weight = product of betas 
	static class ESPair implements Serializable, Comparable<ESPair> {
		// separate tables for:
		// two tails complete
		// two tails incomplete
		// one tail complete
		// one tail incomplete
		
		// two tails complete
		private static Hashtable<EarleyState, Hashtable<EarleyState, Hashtable<StateSeq, ESPair>>> twoTailsCompTable = new Hashtable<EarleyState, Hashtable<EarleyState, Hashtable<StateSeq, ESPair>>>();
		// two tails incomplete
		private static Hashtable<EarleyState, Hashtable<EarleyState, Hashtable<Vector<StateSeq>, ESPair>>> twoTailsInTable = new Hashtable<EarleyState, Hashtable<EarleyState, Hashtable<Vector<StateSeq>, ESPair>>>();
		// one tail complete
		private static Hashtable<EarleyState, Hashtable<StateSeq, ESPair>> oneTailCompTable = new Hashtable<EarleyState, Hashtable<StateSeq, ESPair>>();
		// one tail incomplete
		private static Hashtable<EarleyState, Hashtable<Vector<StateSeq>, ESPair>> oneTailInTable = new Hashtable<EarleyState, Hashtable<Vector<StateSeq>, ESPair>>();

		static int twoTailsCompAddCount=0;
		static int twoTailsCompAccCount=0;
		static int twoTailsInAddCount=0;
		static int twoTailsInAccCount=0;
		static int oneTailCompAddCount=0;
		static int oneTailCompAccCount=0;
		static int oneTailInAddCount=0;
		static int oneTailInAccCount=0;
		
		boolean added=false;
		EarleyState left;
		EarleyState right;
		
		// for parser-directed composition:
		// optionally incorporate a vector of rules and node in the rhs of the rule (tree) 
		// matched. 
		// for incomplete edges this represents the states of children. for complete edges this represents
		// the states of the lhs
		
		//headMatches = the rule sequence generated here. only filled on finished items
		// tailMatches = the rule sequence of each fully-expanded tail. passed up the chain
		StateSeq headMatches=null;
		//Symbol headMatchSym = null;
		Vector<StateSeq> tailMatches = null;
		//Vector<Symbol> tailMatchSyms=null;
		ESStateList headStateList=null;
		Vector<ESStateList> tailStateLists=null;
		// optionally track child sequences when updating
		// each state in the top rule has a vector. all the states together forms another vector
		// and there could be multiple versions of this, forming the third
		// reduced to symbols, to avoid too much repetition
	//	Vector<Vector<Vector<Symbol>>> childMatches = null;
		double weight;
		
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

			if (headMatches != null) 
				hsh = hsh.sag(headMatches.getHash());
			if (tailMatches != null) {
				for (StateSeq s : tailMatches)
					hsh = hsh.sag(s.getHash());
			}
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
			ESPair r = (ESPair)o;
			if (!r.left.equals(left))
				return false;
			if (r.right == null && right != null)
				return false;
			if (r.right != null && right == null)
				return false;
			if (r.right != null && !r.right.equals(right))
				return false;
			if (headMatches != null && r.headMatches == null)
				return false;
			if (headMatches == null && r.headMatches != null)
				return false;
			if (headMatches != null && !headMatches.equals(r.headMatches))
				return false;
			if (tailMatches != null && r.tailMatches == null)
				return false;
			if (tailMatches == null && r.tailMatches != null)
				return false;
			if (tailMatches != null && !tailMatches.equals(r.tailMatches))
				return false;
			return true;
		}
		private ESPair(EarleyState a, EarleyState b) {
			left = a;
			right = b;
			weight = a.semiring.times(a.beta, b.beta);
			edgeCount++;
		}
		private ESPair(EarleyState a) {
			left = a;
			right = null;
			weight = a.beta;
			edgeCount++;

		}
		public static ESPair get(EarleyState a, StateSeq hm, Vector<StateSeq> tm) {
			boolean debug = false;
			if (hm != null) {
				if (!oneTailCompTable.containsKey(a))
					oneTailCompTable.put(a, new Hashtable<StateSeq, ESPair>());
				if (!oneTailCompTable.get(a).containsKey(hm)) {
					oneTailCompTable.get(a).put(hm, new ESPair(a, hm, tm));
					oneTailCompAddCount++;
				}
				else
					oneTailCompAccCount++;
				if (debug) Debug.debug(debug, "One tail complete: "+oneTailCompAddCount+" adds, "+oneTailCompAccCount+" accs");
				return oneTailCompTable.get(a).get(hm);
			}
			else {
				if (!oneTailInTable.containsKey(a))
					oneTailInTable.put(a, new Hashtable<Vector<StateSeq>, ESPair>());
				if (!oneTailInTable.get(a).containsKey(tm)) {
					oneTailInTable.get(a).put(tm, new ESPair(a, hm, tm));
					oneTailInAddCount++;
				}
				else
					oneTailInAccCount++;
				if (debug) Debug.debug(debug, "One tail incomplete: "+oneTailInAddCount+" adds, "+oneTailInAccCount+" accs");
				return oneTailInTable.get(a).get(hm);
			}
		}
		private ESPair(EarleyState a, StateSeq hm, Vector<StateSeq> tm) {
			this(a);
			headMatches = hm;
			tailMatches = tm;
			if (hm != null) {
				weight = a.semiring.times(hm.heur, hm.known);
				if (hm.stateChain != null) {
					headStateList = ESStateList.get(hm.es, hm.stateChain);
					tailStateLists = new Vector<ESStateList>();
					if (hm.childStateChain != null) {
						for (int i = 0; i < hm.childStateChain.size(); i++) {
							tailStateLists.add(ESStateList.get(hm.childVec.get(i), hm.childStateChain.get(i)));
						}
					}
				}
			}
			else if (tm != null) {
//				tailStateLists = new Vector<ESStateList>();
				weight = a.semiring.ONE();
				for (StateSeq s : tm) {
					weight = a.semiring.times(weight, a.semiring.times(s.heur, s.known));
	//				tailStateLists.add(ESStateList.get(s.es, s.stateChain));
				}
			}
		}
		public static ESPair get(EarleyState a, EarleyState b, StateSeq hm, Vector<StateSeq> tm) {
			boolean debug = false;
			if (hm != null) {
				if (!twoTailsCompTable.containsKey(a))
					twoTailsCompTable.put(a, new Hashtable<EarleyState, Hashtable<StateSeq, ESPair>>());
				if (!twoTailsCompTable.get(a).containsKey(b))
					twoTailsCompTable.get(a).put(b, new Hashtable<StateSeq, ESPair>());
				if (!twoTailsCompTable.get(a).get(b).containsKey(hm)) {
					twoTailsCompTable.get(a).get(b).put(hm, new ESPair(a, b, hm, tm));
					twoTailsCompAddCount++;
				}
				else
					twoTailsCompAccCount++;
				if (debug) Debug.debug(debug, "Two tail complete: "+twoTailsCompAddCount+" adds, "+twoTailsCompAccCount+" accs");

				return twoTailsCompTable.get(a).get(b).get(hm);
			}
			else {
				if (!twoTailsInTable.containsKey(a))
					twoTailsInTable.put(a, new Hashtable<EarleyState, Hashtable<Vector<StateSeq>, ESPair>>());
				if (!twoTailsInTable.get(a).containsKey(b))
					twoTailsInTable.get(a).put(b, new Hashtable<Vector<StateSeq>, ESPair>());
				if (!twoTailsInTable.get(a).get(b).containsKey(tm)) {
					twoTailsInTable.get(a).get(b).put(tm, new ESPair(a, b, hm, tm));
					twoTailsInAddCount++;
				}
				else
					twoTailsInAccCount++;
				if (debug) Debug.debug(debug, "Two tail incomplete: "+twoTailsInAddCount+" adds, "+twoTailsInAccCount+" accs");

				return twoTailsInTable.get(a).get(b).get(tm);
			}
		}
		private ESPair(EarleyState a, EarleyState b, StateSeq hm, Vector<StateSeq> tm) {
			this(a, b);
			headMatches = hm;
			tailMatches = tm;
			if (hm != null) {
				weight = a.semiring.times(hm.heur, hm.known);
				if (hm.stateChain != null) {
					headStateList = ESStateList.get(hm.es, hm.stateChain);
					tailStateLists = new Vector<ESStateList>();
					if (hm.childStateChain != null) {
						for (int i = 0; i < hm.childStateChain.size(); i++) {
							tailStateLists.add(ESStateList.get(hm.childVec.get(i), hm.childStateChain.get(i)));
						}
					}
				}
			}
			else if (tm != null) {
				weight = a.semiring.ONE();
				for (StateSeq s : tm) {
					weight = a.semiring.times(weight, a.semiring.times(s.heur, s.known));
				}
			}
		}

		// for comparable. natural order.
		public int compareTo(ESPair o) {
			boolean debug = false;
			if (left.semiring.better(weight, o.weight)) {
				if (debug) Debug.debug(debug, weight+" better than "+o.weight);			
				return 1;
			}
			else if (left.semiring.betteroreq(weight, o.weight)) {
				if (debug) Debug.debug(debug, weight+" equal to "+o.weight);
				return 0;
			}
			else {
				if (debug) Debug.debug(debug, weight+" worse than "+o.weight);			
				return -1;
			}
		}
		public String toString() {
			StringBuffer str = new StringBuffer(left+":");
			if (right == null)
				str.append("<NULL>:"+weight);
			else
				str.append(right+":"+weight);
			if (headMatches == null)
				str.append(":<NULL>");
			else
				str.append(":"+headMatches);
			if (tailMatches == null)
				str.append(":<NULL>");
			else
				str.append(":"+tailMatches);
			return str.toString();
		}
		public Symbol getStateSym()  {
			return headStateList.getSym();
		}

		public Symbol getStateSym(int i)  {
			return tailStateLists.get(i).getSym();
		}
		
	}

	
	// special comparator for reverse order
	private static class ReverseESPComparator implements Serializable, Comparator<ESPair> {

		/* (non-Javadoc)
		 * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
		 */
		public int compare(ESPair o1, ESPair o2) {			
			if (o1.left.semiring.better(o1.weight, o2.weight))
				return -1;
			else if (o1.left.semiring.betteroreq(o1.weight, o2.weight))
				return 0;
			else
				return 1;
		}
	}
		
	
	int rule;
	int rulepos;
	int stringStartPos;
	int stringEndPos;
	// integer alias for src and dst states
	// keyed on s2i, i2s
	int src;
	private int[] dst;
	// isFinished means have we moved the pointer through the rule
	boolean isFinished;
	// avoid checking lists with these flags
	// isDone means have we expanded this state
	boolean isDone;
	boolean isTodo;
	// alphas and betas as set due to stolcke propagation algorithm
	double beta;
	double alpha;
	// allow memoization
	boolean betaset;
	// track reset for alpha, not set
	boolean alphareset;
	// track to see if isAdded. Set during agenda adding
	boolean isAdded;
	
	Semiring semiring;
	PriorityQueue<ESPair> next;
	// edges divided by state seq
	Hashtable<Vector<Symbol>, PriorityQueue<ESPair>> nextBySeq;
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
		EarleyState r = (EarleyState)o;
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
	
	
	
	EarleyState(CFGRuleSet rs, CFGRule r, int rp, int ssp, int sep) {
		statesMade++;
		rule = r.index;
		rulepos = rp;
		stringStartPos = ssp;
		stringEndPos = sep;
		// create src and dst items for later quick access
		src = rs.s2i(r.lhs);
		dst = new int[r.getRHS().getSize()];
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
		semiring = rs.semiring;
		// initial beta is rule weight
		beta = r.weight;
		// point top-down
		
	}

	EarleyState(StringTransducerRuleSet rs, StringTransducerRule r, int rp, int ssp, int sep) {
		statesMade++;
		rule = r.getIndex();
		rulepos = rp;
		stringStartPos = ssp;
		stringEndPos = sep;
		// create src and dst items for later quick access
		src = rs.s2i(r.getState());
		dst = new int[r.getRHS().getSize()];
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
		semiring = rs.semiring;
		// initial beta is rule weight
		beta = r.weight;
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
	EarleyState shift(Symbol sym, CFGRule[] ruleset, CFGRuleSet rs) {
		boolean debug = false;
		if (isFinished)
			return null;
		// handle epsilon rules too
		if (ruleset[rule].rhs.isEmptyString()) {
			if (debug) Debug.debug(debug, "Found epsilon rule "+rule+"; acting accordingly");
			return EarleyStateFactory.getState(rs, ruleset[rule], rulepos+1, stringStartPos, stringEndPos);
		}
		if (sym == Symbol.getEpsilon())
			return null;
		Symbol cmp = ((StringItem)ruleset[rule].getRHS()).getSym(rulepos);
		if (cmp == sym)
			return EarleyStateFactory.getState(rs, ruleset[rule], rulepos+1, stringStartPos, stringEndPos+1);
		return null;
	}
	
	// attempt to shift based on provided symbol
	// edge gets added in calling method
	// also known as "scan"
	EarleyState shift(Symbol sym, StringTransducerRule[] ruleset, StringTransducerRuleSet rs) {
		boolean debug = false;
		if (isFinished)
			return null;
		// handle epsilon rules too
		if (ruleset[rule].rhs.isEpsilon()) {
			if (debug) Debug.debug(debug, "Found epsilon rule "+rule+"; acting accordingly");
			return EarleyStateFactory.getState(rs, ruleset[rule], rulepos+1, stringStartPos, stringEndPos);
		}
		if (sym == Symbol.getEpsilon())
			return null;
		Symbol cmp = ruleset[rule].getRHS().getLabelSym(rulepos);
		if (cmp == null)
			return null;
		if (cmp == sym)
			return EarleyStateFactory.getState(rs, ruleset[rule], rulepos+1, stringStartPos, stringEndPos+1);
		return null;
	}
	// add an edge to the state
	public void update(EarleyState edge) {
		boolean debug = false;
		if (next != null) {
			Debug.debug(true, "WARNING: adding predict edge "+edge+" to non-empty state "+this);
		}
		// we assume it was null
		if (next == null) {
			beta = semiring.ZERO();
			if (semiring.ONE() > semiring.ZERO()) {
				next = new PriorityQueue<ESPair>();
				if (debug) Debug.debug(debug," created queue");			

			}
			else {
				next = new PriorityQueue<ESPair>(11, new ReverseESPComparator());
				if (debug) Debug.debug(debug," created reverse queue");			

			}
		}
		ESPair esp = new ESPair(edge);

//		if (next.contains(v)) {
//			Debug.debug(true, " on shift, "+this+" already contains "+v);
//		}
		if (debug) Debug.debug(debug, "Adding "+esp+" to "+this);
		next.add(esp);
		// technically this should be added to the previous beta, but we assume next was null
		beta = esp.weight;
	}
	
	
	// add an edge to the state, and track externals too

	public void update(EarleyState right, 
			int beam,
			Vector<Hashtable<Symbol, Hashtable<Integer, Vector<TreeRuleTuple>>>> rhsidx,
			Symbol matchsym,
			int matchrank,
			StringTransducerRule theRule) throws UnusualConditionException {
		boolean debug = false;
		if (next != null) {
			Debug.debug(true, "WARNING: adding predict edge "+right+" to non-empty state "+this);
		}
		// we assume it was null
		if (next == null) {
			nextBySeq = new Hashtable<Vector<Symbol>, PriorityQueue<ESPair>>();
			//beta = semiring.ZERO();
			if (semiring.ONE() > semiring.ZERO()) {
				next = new PriorityQueue<ESPair>();
				if (debug) Debug.debug(debug," created queue");			

			}
			else {
				next = new PriorityQueue<ESPair>(11, new ReverseESPComparator());
				if (debug) Debug.debug(debug," created reverse queue");			

			}
		}
		if (right.next != null) {
			if (debug) Debug.debug(debug, "Looking for a state seq from "+right.next.size()+" vectors");
			for (ESPair rightedge : right.next) {
				Vector<StateSeq> crossprod = rightedge.tailMatches;
				// if finished, use crossprod to get a new sequence
				// each element returned goes in its own size-1 vector
				if (isFinished) {
					// fix crossprod to the rule's order
					Vector<StateSeq> reorder = reorderStateSeq(crossprod, theRule);
					
					for (StateSeq matchvecitem : internalGetMatches(rhsidx, 0, matchsym, matchrank, theRule.getWeight(), reorder)) {
						if (debug) Debug.debug(debug, "\tFound state seq "+matchvecitem);
						matchvecitem.left = stringStartPos;
						matchvecitem.right = stringEndPos;
						ESPair esp = ESPair.get(right, matchvecitem, crossprod);
						if (esp.added)
							continue;
						esp.added = true;
						if (beam > 0 && next.size() >= beam && semiring.betteroreq(next.peek().weight, esp.weight)) {
							if (debug) Debug.debug(debug, "Beamed out "+esp+" from "+this);
						}
						else {
							if (debug) Debug.debug(debug, "Adding "+esp+" to "+this);
							next.add(esp);
							// store the item by its state list for later access
							if (esp.headStateList != null) {
								if (!nextBySeq.containsKey(esp.headMatches.stateChain)) {
									if (semiring.ONE() > semiring.ZERO()) {
										nextBySeq.put(esp.headMatches.stateChain, new PriorityQueue<ESPair>());
									}
									else {
										nextBySeq.put(esp.headMatches.stateChain, new PriorityQueue<ESPair>(11, new ReverseESPComparator()));
									}
								}
								nextBySeq.get(esp.headMatches.stateChain).add(esp);
							}
							//						double oldestbeta = beta;
							//beta = semiring.plus(beta, esp.weight);

							if (beam > 0 && next.size() > beam) {
								ESPair reject = next.poll();
								if (reject.headStateList != null) {
									nextBySeq.get(reject.headMatches.stateChain).poll();
								}
								//double oldbeta = beta;
								//beta = semiring.minus(beta, reject.weight);
								//if (debug) Debug.debug(debug, "Beamed out "+reject+" while adding "+esp+"; beta from "+oldestbeta+" to "+oldbeta+" to "+beta);
							}
						}
					}	
				}
				// if not finished, crossprod IS the sequence
				else {
					ESPair esp = ESPair.get(right, null, crossprod);
					if (esp.added)
						continue;
					esp.added = true;
					if (beam > 0 && next.size() >= beam && semiring.betteroreq(next.peek().weight, esp.weight)) {
						if (debug) Debug.debug(debug, "Beamed out "+esp+" from "+this);
					}
					else {
						if (debug) Debug.debug(debug, "Adding "+esp+" to "+this);
						next.add(esp);
						//					double oldestbeta = beta;
						//beta = semiring.plus(beta, esp.weight);

						if (beam > 0 && next.size() > beam) {
							ESPair reject = next.poll();
							//double oldbeta = beta;
							//beta = semiring.minus(beta, reject.weight);
							//if (debug) Debug.debug(debug, "Beamed out "+reject+" while adding "+esp+"; beta from "+oldestbeta+" to "+oldbeta+" to "+beta);
						}
					}
				}
			}
		}
		// no descendants
		else {
			if (debug) Debug.debug(debug, "Looking for a state seq from empty vec");
			for (StateSeq matchvecitem : internalGetMatches(rhsidx, 0, matchsym, matchrank, theRule.getWeight(), new Vector<StateSeq>())) {
				if (debug) Debug.debug(debug, "\tFound state seq "+matchvecitem);
				matchvecitem.left = stringStartPos;
				matchvecitem.right = stringEndPos;
				ESPair esp = ESPair.get(right, matchvecitem, null);
				if (esp.added)
					continue;
				esp.added = true;
				if (beam > 0 && next.size() >= beam && semiring.betteroreq(next.peek().weight, esp.weight)) {
					if (debug) Debug.debug(debug, "Beamed out "+esp+" from "+this);
				}
				else {
					if (debug) Debug.debug(debug, "Adding "+esp+" to "+this);
					next.add(esp);
					// store the item by its state list for later access
					if (esp.headStateList != null) {
						if (!nextBySeq.containsKey(esp.headMatches.stateChain)) {
							if (semiring.ONE() > semiring.ZERO()) {
								nextBySeq.put(esp.headMatches.stateChain, new PriorityQueue<ESPair>());
							}
							else {
								nextBySeq.put(esp.headMatches.stateChain, new PriorityQueue<ESPair>(11, new ReverseESPComparator()));
							}
						}
						nextBySeq.get(esp.headMatches.stateChain).add(esp);
					}
					//						double oldestbeta = beta;
					//beta = semiring.plus(beta, esp.weight);

					if (beam > 0 && next.size() > beam) {
						ESPair reject = next.poll();
						if (reject.headStateList != null) {
							nextBySeq.get(reject.headMatches.stateChain).poll();
						}
						//double oldbeta = beta;
						//beta = semiring.minus(beta, reject.weight);
						//if (debug) Debug.debug(debug, "Beamed out "+reject+" while adding "+esp+"; beta from "+oldestbeta+" to "+oldbeta+" to "+beta);
					}
				}
			}	
			
		}
	}
	
	// assumption that if there are external rules, they are in edge2!
	
	public void update(EarleyState edge1, EarleyState edge2, int beam) {
		boolean debug = false;

		if (next == null) {
//			beta = semiring.ZERO();
			if (semiring.ONE() > semiring.ZERO()) {
				next = new PriorityQueue<ESPair>();
				if (debug) Debug.debug(debug," created queue");			

			}
			else {
				next = new PriorityQueue<ESPair>(11, new ReverseESPComparator());
				if (debug) Debug.debug(debug," created reverse queue");			

			}
		}
//		EarleyState[] v = EarleyStateFactory.getCombEdge(edge1, edge2);
		ESPair esp = new ESPair(edge1, edge2);
//		if (next.contains(v)) {
//		Debug.debug(true, " on merge, "+this+" already contains "+v);
//	}
			// if there is a beam, and we're over the limit, do nothing
		if (beam > 0 && next.size() >= beam && semiring.betteroreq(next.peek().weight, esp.weight)) {
			if (debug) Debug.debug(debug, "Beamed out "+esp+" from "+this);
		}
		else {
			if (debug) Debug.debug(debug, "Adding "+esp+" to "+this);
			next.add(esp);
		//	double oldestbeta = beta;
			//beta = semiring.plus(beta, esp.weight);
		
			if (beam > 0 && next.size() > beam) {
				ESPair reject = next.poll();
				//double oldbeta = beta;
				//beta = semiring.minus(beta, reject.weight);
				//if (debug) Debug.debug(debug, "Beamed out "+reject+" while adding "+esp+"; beta from "+oldestbeta+" to "+oldbeta+" to "+beta);
			}
		}
	}
	
	
	
	public void update(EarleyState left, EarleyState right, int beam,
			Vector<Hashtable<Symbol, Hashtable<Integer, Vector<TreeRuleTuple>>>> rhsidx,
			Symbol matchsym,
			int matchrank,
			StringTransducerRule theRule) throws UnusualConditionException {
		boolean debug = false;

		if (next == null) {
//			beta = semiring.ZERO();
			nextBySeq = new Hashtable<Vector<Symbol>, PriorityQueue<ESPair>>();
			if (semiring.ONE() > semiring.ZERO()) {
				next = new PriorityQueue<ESPair>();
				if (debug) Debug.debug(debug," created queue");			

			}
			else {
				next = new PriorityQueue<ESPair>(11, new ReverseESPComparator());
				if (debug) Debug.debug(debug," created reverse queue");			

			}
		}
		// regular complete: cross the match vectors from each edge under left with each edge under right
		
		if (left.next != null) {
			// left or right could be the same as this, so dump contents to vectors first
			Vector<ESPair> leftedges = new Vector<ESPair>();
			leftedges.addAll(left.next);
			Vector<ESPair> rightedges = new Vector<ESPair>();
			rightedges.addAll(right.next);
			if (debug) Debug.debug(debug, "Looking for a state seq from "+leftedges.size()+" * "+rightedges.size()+" = "+(leftedges.size()*rightedges.size())+" vectors");
			for (ESPair leftedge : leftedges) {
				for (ESPair rightedge : rightedges) {
					// join the two together: leftedge = <<a,b,c>, <d,e,f>>, rightedege = <<g,h,i>>, combine = <<a,b,c,> <d,e,f>, <g,h,i>>
					Vector<StateSeq> crossprod = new Vector<StateSeq>();
					crossprod.addAll(leftedge.tailMatches);
					crossprod.add(rightedge.headMatches);
					// crossprod might be shorter than the rank but it shouldn't be longer
					if (crossprod.size() > matchrank)
						continue;
					// if finished, use crossprod to get a new sequence
					// each element returned goes in its own size-1 vector
					if (isFinished) {
						// fix crossprod to the rule's order
						Vector<StateSeq> reorder = reorderStateSeq(crossprod, theRule);
						
						for (StateSeq matchvecitem : internalGetMatches(rhsidx, 0, matchsym, matchrank, theRule.getWeight(), reorder)) {
							if (debug) Debug.debug(debug, "\tFound state seq "+matchvecitem);
							matchvecitem.left = stringStartPos;
							matchvecitem.right = stringEndPos;
							ESPair esp = ESPair.get(left, right, matchvecitem, crossprod);
							if (esp.added)
								continue;
							esp.added = true;
							if (beam > 0 && next.size() >= beam && semiring.betteroreq(next.peek().weight, esp.weight)) {
								if (debug) Debug.debug(debug, "Beamed out "+esp+" from "+this);
							}
							else {
								if (debug) Debug.debug(debug, "Adding "+esp+" to "+this);
								next.add(esp);
								// store the item by its state list for later access
								if (esp.headStateList != null) {
									if (!nextBySeq.containsKey(esp.headMatches.stateChain)) {
										if (semiring.ONE() > semiring.ZERO()) {
											nextBySeq.put(esp.headMatches.stateChain, new PriorityQueue<ESPair>());
										}
										else {
											nextBySeq.put(esp.headMatches.stateChain, new PriorityQueue<ESPair>(11, new ReverseESPComparator()));
										}
									}
									nextBySeq.get(esp.headMatches.stateChain).add(esp);
								}
//								double oldestbeta = beta;
								//beta = semiring.plus(beta, esp.weight);
							
								if (beam > 0 && next.size() > beam) {
									ESPair reject = next.poll();
									if (reject.headStateList != null) {
										nextBySeq.get(reject.headMatches.stateChain).poll();
									}
									//double oldbeta = beta;
									//beta = semiring.minus(beta, reject.weight);
									//if (debug) Debug.debug(debug, "Beamed out "+reject+" while adding "+esp+"; beta from "+oldestbeta+" to "+oldbeta+" to "+beta);
								}
							}
						}	
					}
					// if not finished, crossprod IS the sequence
					else {
						ESPair esp = ESPair.get(left, right, null, crossprod);
						if (esp.added)
							continue;
						esp.added = true;
						if (beam > 0 && next.size() >= beam && semiring.betteroreq(next.peek().weight, esp.weight)) {
							if (debug) Debug.debug(debug, "Beamed out "+esp+" from "+this);
						}
						else {
							if (debug) Debug.debug(debug, "Adding "+esp+" to "+this);
							next.add(esp);
//							double oldestbeta = beta;
							//beta = semiring.plus(beta, esp.weight);
						
							if (beam > 0 && next.size() > beam) {
								ESPair reject = next.poll();
								//double oldbeta = beta;
								//beta = semiring.minus(beta, reject.weight);
								//if (debug) Debug.debug(debug, "Beamed out "+reject+" while adding "+esp+"; beta from "+oldestbeta+" to "+oldbeta+" to "+beta);
							}
						}
					}
				}
			}
		}
		else {
			// left-is-null case : no merging needed
			// right could be the same as this, so dump contents to vectors first

			Vector<ESPair> rightedges = new Vector<ESPair>();
			rightedges.addAll(right.next);
			if (debug) Debug.debug(debug, "Looking for a state seq from "+rightedges.size()+" vectors");

			for (ESPair rightedge : rightedges) {

				Vector<StateSeq> crossprod = new Vector<StateSeq>();
				crossprod.add(rightedge.headMatches);
				// if finished, use crossprod to get a new sequence
				// each element returned goes in its own size-1 vector
				if (isFinished) {
					// fix crossprod to the rule's order
					Vector<StateSeq> reorder = reorderStateSeq(crossprod, theRule);
					for (StateSeq matchvecitem : internalGetMatches(rhsidx, 0, matchsym, matchrank, theRule.getWeight(), reorder)) {
						if (debug) Debug.debug(debug, "\tFound state seq "+matchvecitem);
						matchvecitem.left = stringStartPos;
						matchvecitem.right = stringEndPos;
						ESPair esp = ESPair.get(left, right, matchvecitem, crossprod);
						if (esp.added)
							continue;
						esp.added = true;
						if (beam > 0 && next.size() >= beam && semiring.betteroreq(next.peek().weight, esp.weight)) {
							if (debug) Debug.debug(debug, "Beamed out "+esp+" from "+this);
						}
						else {
							if (debug) Debug.debug(debug, "Adding "+esp+" to "+this);
							next.add(esp);
							// store the item by its state list for later access
							if (esp.headStateList != null) {
								if (!nextBySeq.containsKey(esp.headMatches.stateChain)) {
									if (semiring.ONE() > semiring.ZERO()) {
										nextBySeq.put(esp.headMatches.stateChain, new PriorityQueue<ESPair>());
									}
									else {
										nextBySeq.put(esp.headMatches.stateChain, new PriorityQueue<ESPair>(11, new ReverseESPComparator()));
									}
								}
								nextBySeq.get(esp.headMatches.stateChain).add(esp);
							}
							//						double oldestbeta = beta;
							//beta = semiring.plus(beta, esp.weight);

							if (beam > 0 && next.size() > beam) {
								ESPair reject = next.poll();
								if (reject.headStateList != null) {
									nextBySeq.get(reject.headMatches.stateChain).poll();
								}
								//double oldbeta = beta;
								//beta = semiring.minus(beta, reject.weight);
								//if (debug) Debug.debug(debug, "Beamed out "+reject+" while adding "+esp+"; beta from "+oldestbeta+" to "+oldbeta+" to "+beta);
							}
						}
					}	
				}
				// if not finished, crossprod IS the sequence
				else {
					ESPair esp = ESPair.get(left, right, null, crossprod);
					if (esp.added)
						continue;
					esp.added = true;
					if (beam > 0 && next.size() >= beam && semiring.betteroreq(next.peek().weight, esp.weight)) {
						if (debug) Debug.debug(debug, "Beamed out "+esp+" from "+this);
					}
					else {
						if (debug) Debug.debug(debug, "Adding "+esp+" to "+this);
						next.add(esp);
						//					double oldestbeta = beta;
						//beta = semiring.plus(beta, esp.weight);

						if (beam > 0 && next.size() > beam) {
							ESPair reject = next.poll();
							//double oldbeta = beta;
							//beta = semiring.minus(beta, reject.weight);
							//if (debug) Debug.debug(debug, "Beamed out "+reject+" while adding "+esp+"; beta from "+oldestbeta+" to "+oldbeta+" to "+beta);
						}
					}
				}
			}		
		}
	}
	
	public String toString() {
		StringBuffer str = new StringBuffer(rule+":"+rulepos+":"+stringStartPos+":"+stringEndPos);
		if (next != null) {
			//str.append("["+next.size()+" children "+"]");
			str.append("["+next.size()+" edges]");
//			for (ESPair nes : next) {
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
			ArrayList<EarleyState> todoList,
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
//		innermemo = new Hashtable<EarleyState, Vector<Vector<Symbol>>>();
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

	// traverse through the chart from a single state, creating new states etc. as need be
	public Vector<RTGRule> buildRTG(
			Symbol[][][] newSyms, 
			ArrayList<EarleyState> todoList,
			RTGRuleSet parent, StringTransducerRule[] ruleset) throws ImproperConversionException {
		boolean debug = false;
		// there used to be a done list, but this flag is good enough
		this.isDone = true;

		// get the new new state symbol, either by lookup or recreation
		Symbol newstate;
		if (newSyms[src][stringStartPos][stringEndPos] != null)	{
			newstate = newSyms[src][stringStartPos][stringEndPos];
			if (debug) Debug.debug(debug, "Found "+rule+"->"+stringStartPos+"->"+stringEndPos+"->"+newstate);
		}
		else {
			throw new ImproperConversionException ("Couldn't find state for "+rule+":"+stringStartPos+":"+stringEndPos+
					"; these should be created in getPossibleSymbols or top level constructor");	
		}

		if (debug) Debug.debug(debug, "Gathering RTG rules to explain "+rule+" from "+stringStartPos+" to "+stringEndPos);
		Vector<RTGRule> ret = new Vector<RTGRule>();
		
		//Vector<Vector<Symbol>> stateseqs = new Vector<Vector<Symbol>>();
		//stateseqs.add(new Vector<Symbol>());
		//debug = true;
		if (debug) Debug.debug(debug, "Gathering possible symbols for "+this);
//		innermemo = new Hashtable<EarleyState, Vector<Vector<Symbol>>>();
		//stateseqs = getPossibleSymbols(this, stateseqs, newSyms, todoList, 0);
		
		Vector<Vector<Symbol>> stateseqs = getPossibleSymbols(this, newSyms, todoList, 0);
		if (debug) Debug.debug(debug, "For "+this+" got "+stateseqs);
		



		// so that we can appropriately map between rhs and lhs, get all rhs variables in the order they occur
		ArrayList<TransducerRightSide> rhsvars = ruleset[rule].getTRVM().getRHSInOrder();

		
		
		// add new rtg rules

		for (Vector<Symbol> newrhs : stateseqs) {

			RTGRule newRule=null;
			Hashtable<TransducerRightSide, Symbol> varMap = new Hashtable<TransducerRightSide, Symbol>();
			// newrhsctr
			int j = 0;
			for (int i = 0; i < rhsvars.size(); i++) {
				if (rhsvars.get(i).hasState()) {
					if (debug) Debug.debug(debug, "Mapping "+rhsvars.get(i)+" to "+newrhs.get(j)+ " in "+rule);
					varMap.put(rhsvars.get(i), newrhs.get(j++));
				}
			}
			try {
				if (debug) Debug.debug(debug, "Building from "+rule+" and "+varMap+" and "+newstate);
				newRule = new RTGRule(parent, newstate, ruleset[rule], varMap);
				if (debug) Debug.debug(debug, "Built "+newRule);
				ret.add(newRule);
			}
			catch (ImproperConversionException e) {
				throw new ImproperConversionException("Can't convert "+rule+" to rtg: "+e.getMessage());
			}
		
		}
		if (debug) Debug.debug(debug, "Done building rules from "+this+"; built "+ret);
		return ret;		
	}

	
	

	
	// state sequence and a weight two can be multiplied to form a third
	// <a,b,c>:x x <d,e,f>:y = <a,b,c,d,e,f>:xy
//	private static class StateSeq {
//		Vector<Symbol> states;
//		double weight;
//		StateSeq(Vector<Symbol> s, double w) { states = s; weight = w; }
//		StateSeq(StateSeq a, StateSeq b, Semiring s) {
//			states = new Vector<Symbol>(a.states);
//			states.addAll(b.states);
//			weight = s.times(a.weight, b.weight);
//		}
//		public String toString() { return states+":"+weight; }
//	}
	
	// traverse through the chart from a single state, creating new states etc. as need be
	// use external transducer info to build
	public Vector<RTGRule> buildTransRTG(
			ESStateList exstate, 
			ArrayList<ESStateList> todoList,
			RTGRuleSet parent,
			StringTransducerRule[] ruleset) throws ImproperConversionException {
		boolean debug = false;
		// done list now applies to the exstate, not to this
		exstate.isDone = true;


		if (debug) Debug.debug(debug, "Gathering RTG rules to explain "+exstate);
		Vector<RTGRule> ret = new Vector<RTGRule>();


		// get rule from top edge. for each edge, iterate down
		for (ESPair edge : nextBySeq.get(exstate.ex)) {
			StateSeq seq = edge.headMatches;
			if (!seq.states.get(seq.states.size()-1).containsKey(StateSeq.SOLO) ||
					seq.states.get(seq.states.size()-1).get(StateSeq.SOLO) instanceof StateSeq)
				throw new ImproperConversionException("Tried to build rule from incomplete state: "+seq);
			TreeTransducerRule ruleMatch = ((TreeRuleTuple)seq.states.get(seq.states.size()-1).get(StateSeq.SOLO)).rule;
			// getStateSym idiotically does not have lowest-level state, so add it on here
			// TODO: rewrite to avoid this!
			Symbol srcState = SymbolFactory.getSymbol(src+":"+edge.getStateSym().toString());
			// dst states formed from the left and right edges
			Vector<Symbol> dstStates =new Vector<Symbol>();
			for (int i = 0; i < edge.tailStateLists.size(); i++) {
				
				dstStates.add(SymbolFactory.getSymbol(edge.tailStateLists.get(i).es.src+":"+edge.getStateSym(i).toString()));
				if (!edge.tailStateLists.get(i).isDone && !edge.tailStateLists.get(i).isTodo) {
					edge.tailStateLists.get(i).isTodo = true;
					todoList.add(edge.tailStateLists.get(i));
				}
			}
		

			// variables should already be in order


			// add new rtg rules

			//			Vector<Symbol> newrhs = seq.states;
			double wgt = seq.known;
			RTGRule newRule=null;
			try {
				if (debug) Debug.debug(debug, "Building from "+ruleMatch+" and "+srcState+" and "+dstStates);
				newRule = new RTGRule(parent, srcState, ruleMatch, wgt, dstStates);
				if (debug) Debug.debug(debug, "Built "+newRule);
				ret.add(newRule);
			}
			catch (ImproperConversionException e) {
				throw new ImproperConversionException("Can't convert "+ruleMatch+" to rtg: "+e.getMessage());
			}


		}
		
		if (debug) Debug.debug(debug, "Done building rules from "+this+"; built "+ret);
		return ret;		
	}

	
	private static Hashtable<EarleyState, Vector<Vector<Symbol>>> innermemo = new Hashtable<EarleyState, Vector<Vector<Symbol>>>();
	static void resetParseMemo() {
		innermemo = new Hashtable<EarleyState, Vector<Vector<Symbol>>>();
	}
	
	private static Hashtable<ESPair, Vector<StateSeq>> transinnermemo = new Hashtable<ESPair, Vector<StateSeq>>();
	static void resetTransParseMemo() {
		transinnermemo = new Hashtable<ESPair, Vector<StateSeq>>();
	}
	
	// reversed insertion order
	private static Vector<Vector<Symbol>> getPossibleSymbols(
			EarleyState currState, 
			Symbol[][][] newSyms, 
			ArrayList<EarleyState> todoList,
			int level) {
		boolean debug = false;
		boolean memoize = true;

		if (debug) Debug.debug(debug, level, "Exploring "+currState);
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
		for (ESPair choice : currState.next) {
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
				if (debug) Debug.debug(debug, "Adding child "+choice.right+" to vectors");
				for (Vector<Symbol> oldmember : tempret) {
					Vector<Symbol> newmember = new Vector<Symbol>(oldmember);
					EarleyState child = choice.right;
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
			else {
				if (debug) Debug.debug(debug, "Porting recursive discovery "+tempret);
				nextret = tempret;
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


	
	// beta propagation from Stolcke 1995
	public void resetBeta() {
		if (!betaset)
			return;
		betaset = false;
		if (next != null) {
			for (ESPair es : next) {
				es.left.resetBeta();
				if (es.right != null)
					es.right.resetBeta();
			}
		}
	}
	// collect list of states for alpha setting while doing beta setting
	public void setBeta(Semiring semiring, Rule[] ruleset, Stack<EarleyState> alphaList) {
		boolean debug = false;
		if (betaset)
			return;
		// to avoid stack overflows, pretend beta is already set. strictly speaking, though, 
		// this is wrong!
		betaset = true;
		// prediction
		if (next == null) {
			beta = ruleset[rule].getWeight();
			if (debug) Debug.debug(debug, "Beta for "+this+" is "+beta+" (prediction)");
			alphaList.push(this);
			return;
		}
		// scan / shift
		if (next.size() == 1 && next.peek().right == null) {
			next.peek().left.setBeta(semiring, ruleset, alphaList);
			beta = next.peek().left.beta;
			alphaList.push(this);
			if (debug) Debug.debug(debug, "Beta for "+this+" is "+beta+" (scanning)");
			return;
		}

		// completion. heap might not be very heap-y but rebuilding would take time!
		double temp = semiring.ZERO();
		for (ESPair es : next) {
			es.left.setBeta(semiring, ruleset, alphaList);
			es.right.setBeta(semiring, ruleset, alphaList);
			es.weight = semiring.times(es.left.beta, es.right.beta);
			temp = semiring.plus(temp, es.weight);
		}
		beta = temp;
		alphaList.push(this);
		if (debug) Debug.debug(debug, "Beta for "+this+" is "+beta+" (completion)");
	}
	
//	// alpha propagation from Stolcke 1995
	public void resetAlpha(Semiring semiring) {
		if (alphareset)
			return;
		alpha = semiring.ZERO();
		alphareset = true;
		if (next != null) {
			for (ESPair es : next) {
				es.left.resetAlpha(semiring);
				if (es.right != null)
					es.right.resetAlpha(semiring);
			}
		}
	}
	
	
	// alphas of children are actually set
	// no recursion here, as order is dictated by list
	public void setAlpha(Semiring semiring, Rule[] ruleset) {
		boolean debug = false;
//		if (alphaset) {
//			if (debug) Debug.debug(debug, "Alpha propagation already complete at "+this);
//			return;
//		}
		alphareset = false;
		if (debug) Debug.debug(debug, "Alpha of "+this+" is "+alpha);
		// prediction -- bottom of the pile
		if (next == null) {
			return;
		}
		// scan / shift -- alpha of child is alpha of parent
		if (next.size() == 1 && next.peek().right == null) {
			next.peek().left.alpha = alpha;
//			if (debug) Debug.debug(debug, "Sending alpha for "+this+" = "+alpha+" down (scanning)");
//			next.get(0)[0].setAlpha(semiring, ruleset);
			return;
		}
		// completion
		// jg assertion: alpha times rule weight!
//		double alphaweight = semiring.times(alpha, ruleset[rule].getWeight());
		double alphaweight = alpha;

		for (ESPair es : next) {
			es.left.alpha = semiring.plus(es.left.alpha, semiring.times(alphaweight, es.right.beta));
			if (debug) Debug.debug(debug, "Increasing alpha of "+es.left+" to "+es.left.alpha);
			es.right.alpha = semiring.plus(es.right.alpha, semiring.times(alphaweight, es.left.beta));
			if (debug) Debug.debug(debug, "Increasing alpha of "+es.right+" to "+es.right.alpha);
		}			
	}
	
	// traverse chart, adding beta*alpha of non-virtuals
	// incorporate inv, the inverse of the beta of the whole thing
	public void collectCounts(Semiring semiring, double[] rulecounts, double[] zcounts, Rule[] ruleset, double inv) {
		boolean debug = false;
		if (alphareset || !betaset) {
			if (debug) Debug.debug(debug, "Alpha or beta not set; returning from "+this);
			return;
		}
		betaset = false;
		// only add to counts if we're at the beginning of a rule's derivation
		if (rulepos == 0) {
			double gamma = semiring.times(alpha, beta);
			gamma = semiring.times(gamma, inv);
			double oldrule = rulecounts[rule];
			double oldz = zcounts[src];
			double newrule = semiring.plus(oldrule, gamma);
			double newz = semiring.plus(oldz, gamma);
			if (debug) System.err.println("Gamma for "+ruleset[rule]+":"+this+
					" is alpha "+alpha+" x beta "+beta+
					" = gamma "+gamma);
			if (debug) Debug.debug(debug,"Estimating counts of "+ruleset[rule]+" at "+newrule);
			if (debug) Debug.debug(debug,"Adding "+gamma+" to z of "+src);
			if (debug) Debug.debug(debug,"Setting z of "+src+" to "+newz);
			rulecounts[rule] = newrule;
			zcounts[src] = newz;
		}
//		else
//			if (debug) Debug.debug(debug, "Not updating for incomplete "+this);
		if (next == null)
			return;
		// descend
		for (ESPair es : next) {
			es.left.collectCounts(semiring, rulecounts, zcounts, ruleset, inv);
			if (es.right != null)
				es.right.collectCounts(semiring, rulecounts, zcounts, ruleset, inv);
		}
		
	}
	
	private static Hashtable<Integer, 
		Hashtable<Symbol, 
		Hashtable<Integer, 
		Hashtable<Vector<StateSeq>,
		Vector<StateSeq>>>>> igmmemo = new Hashtable<Integer, 
														Hashtable<Symbol, 
														Hashtable<Integer,
														Hashtable<Vector<StateSeq>,
														Vector<StateSeq>>>>>();
	// recursively traverse through sequences of rules
	// look for match to level, sym, rank. memoize
	// only run on completed structures. may use a previous vector of treeRuleTuple vectors to inform whether new construction
	// is valid
	// note that this returns MORE THAN ONE StateSeq, while the childMatch input is a set of parallel items!
	// N.B.: childMatch has current context at level 0
	private Vector<StateSeq> internalGetMatches(
			Vector<Hashtable<Symbol, Hashtable<Integer, Vector<TreeRuleTuple>>>> rhsidx,
			int level,  
			Symbol matchsym,
			int matchrank,
			double topweight,
			Vector<StateSeq> childMatch) throws UnusualConditionException {
		boolean debug = true;
		// warning: memoization needs to be fixed before it can be enabled!
		boolean isMemo = false;
//		double prevWeight = semiring.ONE();
		// find the matches			
		Hashtable<Symbol, Hashtable<Integer, Vector<TreeRuleTuple>>> idx = rhsidx.get(level);
		if (isMemo) {
			if (!igmmemo.containsKey(level))
				igmmemo.put(level, new Hashtable<Symbol, 
						Hashtable<Integer,
						Hashtable<Vector<StateSeq>,
						Vector<StateSeq>>>>());
			if (!igmmemo.get(level).containsKey(matchsym))
				igmmemo.get(level).put(matchsym, new Hashtable<Integer,
						Hashtable<Vector<StateSeq>,
						Vector<StateSeq>>>());
			if (!igmmemo.get(level).get(matchsym).containsKey(matchrank))
				igmmemo.get(level).get(matchsym).put(matchrank,  new Hashtable<Vector<StateSeq>,
						Vector<StateSeq>>());

			if (!igmmemo.get(level).get(matchsym).get(matchrank).containsKey(childMatch)) {

				// if nothing found, insert empty and return
				if (!idx.containsKey(matchsym) || !idx.get(matchsym).containsKey(matchrank)) {
					Vector<StateSeq> b = new Vector<StateSeq>();
					igmmemo.get(level).get(matchsym).get(matchrank).put(childMatch, b);
					return b;
				}
			}
		}
		if (isMemo && igmmemo.get(level).get(matchsym).get(matchrank).containsKey(childMatch)) {
			if (debug) Debug.debug(debug, "Using memoize for "+level+"->"+matchsym+"->"+matchrank+"->"+childMatch);
			return igmmemo.get(level).get(matchsym).get(matchrank).get(childMatch);
		}
		// non-memo and non-found case
		// recurse down and stitch rule on the front, extracting weights of covered rules

		
		Vector<StateSeq> memret = new Vector<StateSeq>();
		// if nothing, abandon
		if (!idx.containsKey(matchsym) || !idx.get(matchsym).containsKey(matchrank)) {
			return memret;				
		}
		for (TreeRuleTuple tuple : idx.get(matchsym).get(matchrank)) {
			// covered weights accumulated here
			double coveredWeight = semiring.ONE();
			//VarMap used when we have some states
			// mapping to variables here
			// either bind them to higher level or use them right away
			Hashtable<Symbol, StateSeqMember> varMap = null;
			// check suitability of this tuple given the state sequence beneath
			if (matchrank > 0) {

				varMap = new Hashtable<Symbol, StateSeqMember>();
				boolean isOkay = true;
				for (int i = 0; i < matchrank; i++) {
					// if ith child is a label, ith vector at this depth 
					// should not be at the top of the tree and should be the same tree
					// if the label is covering mappings, add them to the variable set
					TreeRuleTuple matchTuple = (TreeRuleTuple)childMatch.get(i).states.get(0).get(StateSeq.SOLO);
					if (tuple.tree.getChild(i).hasLabel()) {
						if (matchTuple.tree.parent == null || 
								tuple.tree.getChild(i) != matchTuple.tree) {
							isOkay = false;
							break;
						}
						else {
							// look one level below label for bound variable mappings that can be promoted
							// take in covered weight here
							coveredWeight = semiring.times(coveredWeight, childMatch.get(i).heur);
							if (childMatch.get(i).states.size() > 1) {							
								varMap.putAll(childMatch.get(i).states.get(1));
							}
						}
					}
					// if ith child is a state, stateposth vector at this depth should have the same state
					// should be at the top of the tree and should have the same state
					// a weight is also covered, so extract it
					// add in the mapping in case we need it
					else {
						if (matchTuple.tree.parent != null ||
								tuple.tree.getChild(i).getState() != matchTuple.rule.getState()) {
							isOkay = false;
							break;
						}
						else {
							varMap.put(tuple.tree.getChild(i).getVariable(), childMatch.get(i).shift(1));
//							prevWeight = semiring.times(prevWeight, matchTuple.rule.getWeight());
						}
					}
					//							statepos++;
				}
				// tuple no good?
				if (!isOkay)
					continue;
			}
			
			
			
			// regardless of rank, now form the vector
			Vector<StateSeq> base = null;
			// if we're at the bottom of the chain, no recursion. add an empty cell as a blank template
			// but since we're at the bottom, put the child vector in here -- it'll percolate to the top!
			if (rhsidx.size() < level+2) {
				base = new Vector<StateSeq>();
				// only track vec if we're at the top of the rule. save state symbol too and progressively
				// add in 
				if (tuple.tree == tuple.rule.getRHS()) {
					Vector<EarleyState> childVec = new Vector<EarleyState>();
					Vector<Vector<Symbol>> childSymVec = new Vector<Vector<Symbol>>();
					for (int i = 0; i < tuple.rule.getLHS().getNumChildren(); i++) {
						if (!varMap.containsKey(tuple.rule.getTRVM().getVariableByIndex(i)))
							throw new UnusualConditionException("No mapping for "+tuple.rule.getTRVM().getVariableByIndex(i)+" in "+varMap);
						StateSeqMember seqmem = varMap.get(tuple.rule.getTRVM().getVariableByIndex(i));
						if (seqmem instanceof StateSeq) {
							StateSeq seq = (StateSeq)seqmem;
							childVec.add(seq.es);
							childSymVec.add(seq.stateChain);
						}
						else {
							throw new UnusualConditionException("Found raw tuple at "+seqmem+" in "+varMap);
						}
					}
					
					// at top, and done with chain, so carried-along weight is put into real
					// any child holding weights are included here
//					double carryweight = topweight;
//					for (StateSeq child : childMatch)
//						carryweight = semiring.times(carryweight, child.heur);
					base.add(new StateSeq(this, tuple.rule.getState(), childVec, childSymVec, topweight, semiring.ONE()));
				}
				else {
					// not at top, but done with chain, so carried along is put into
					// holding here 
					// any child holding weights are included here
//					double carryweight = topweight;
//					for (StateSeq child : childMatch)
//						carryweight = semiring.times(carryweight, child.heur);
					if (varMap == null)
						base.add(new StateSeq(this, semiring.ONE(), topweight));
					else
						base.add(new StateSeq(this, varMap, semiring.ONE(), topweight));
				}
			}
			// if we're not at the top of our rule, no recursion. if there is state info left, 
			// add it into the remainder
			// hold onto all variables, promoted and newly found

			else if (tuple.tree != tuple.rule.getRHS()) {
				base = new Vector<StateSeq>();
				// not at top, but done with chain (for now), so carried along is be put into
				// holding here 
				// any child holding weights are included here
//				double carryweight = topweight;
//				for (StateSeq child : childMatch)
//					carryweight = semiring.times(carryweight, child.heur);
				if (varMap == null)
					base.add(new StateSeq(this, semiring.ONE(), topweight));
				else
					base.add(new StateSeq(this, varMap, semiring.ONE(), topweight));
			}
			// if we're at the top of our rule and the chain's not done, find paths leading from the other side of this rule
			else {

				//						// TODO: get the weights right!

				Vector<StateSeq> reorderedChild = new Vector<StateSeq>();

				// TODO: weight properly!

				for (int i = 0; i < tuple.rule.getLHS().getNumChildren(); i++) {
					if (!varMap.containsKey(tuple.rule.getTRVM().getVariableByIndex(i)))
						throw new UnusualConditionException("No mapping for "+tuple.rule.getTRVM().getVariableByIndex(i)+" in "+varMap);
					// promote solo tuples into full stateseq here!
					StateSeqMember seqmem = varMap.get(tuple.rule.getTRVM().getVariableByIndex(i));
					if (seqmem instanceof StateSeq)
						reorderedChild.add((StateSeq)seqmem);
					else {
						StateSeq seq = new StateSeq(this, (TreeRuleTuple)seqmem, semiring.ONE(), semiring.ONE());
						reorderedChild.add(seq);
					}

				}


				// pass along tuple weight to handle in below case

				base = internalGetMatches(rhsidx, level+1, tuple.rule.getLHS().getLabel(), tuple.rule.getLHS().getNumChildren(), topweight, reorderedChild);
				// if there aren't any, we have no vector (incomplete path)
				if (base.size() == 0)
					continue;
				// if they have stateChain, add it on
				for (StateSeq cdr : base)
					if (cdr.stateChain != null)
						cdr.stateChain.add(0, tuple.rule.getState());
			}
			
			// form this element into a one-element stateseq with covered weight, including weight in true if it's finished, continuing to carry otherwise
			StateSeq car = null;
			if (tuple.tree.parent == null)
				car = new StateSeq(this, tuple, semiring.times(coveredWeight, tuple.rule.getWeight()), semiring.ONE());
			else
				car = new StateSeq(this, tuple, semiring.ONE(), coveredWeight);
			// join front with base, and add to memret. also save children

			for (StateSeq cdr : base) {
				// keep earley state vec and state symbol vec from cdr
				// these should only exist if we are at top the whole way through
				// for constructing rtg later
				// if any of cdr is in heur(holding), all weight goes into holding.
				
				
				StateSeq whole =  new StateSeq(this, car, cdr, semiring);		
				memret.add(whole);
			
			}
		}
		if (isMemo) {
			// memoize memret and return
			igmmemo.get(level).get(matchsym).get(matchrank).put(childMatch, memret);				
			
		}
		return memret;
	}

	// given a transducer rule and a vector of anything, assume the anything is in order based on rhs
	// shuffle the items around based on lhs and return
	private <T> Vector<T> reorderStateSeq(Vector<T> invec, TransducerRule r) throws UnusualConditionException {
		boolean debug = false;
		if (debug) Debug.debug(debug, "Reordering "+invec+" on "+r);
		Vector<T> outvec = new Vector<T>();
		Hashtable<Symbol, T> map = new Hashtable<Symbol, T>();
		int i = 0;
		for (TransducerRightSide trs : r.getTRVM().getRHSInOrder()) {
			map.put(trs.getVariable(), invec.get(i++));
		}
		if (i != invec.size())
			throw new UnusualConditionException("Different number of variables in "+r+" as items in "+invec);
		for (int j = 0; j < i; j++)
			outvec.add(map.get(r.getTRVM().getVariableByIndex(j)));
		
		return outvec;
	}
	
	
}

