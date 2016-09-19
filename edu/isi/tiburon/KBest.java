package edu.isi.tiburon;

// implementation of lazy k-best as described in huang/chiang 05

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Vector;

import mjr.heap.Heap;
import mjr.heap.Heapable;
import gnu.trove.TObjectDoubleHashMap;
public class KBest {

	// for getting k-best list
	// val dictates primary value. if vals are equal, look at kidcount.
	// fewer state children is "better".
	static class KBestHeapKey implements Heapable, Comparable<KBestHeapKey> {
		public double val;
		public double kidcount;
		public int rule;
		public int[] choices;
		private String sval = null;
		private int pos;
		private Hash hsh;
		public KBestHeapKey() {
			val = 0;
			kidcount = 0;
			rule = 0;
			choices = new int[1];
			sval = null;
			pos = 0;
			hsh = hsh.sag(new Hash(0));
		}
		public KBestHeapKey(int r, int[] c, double v, double kc) {
			val = v;
			kidcount = kc;
			rule = r;
			choices = c;
			pos = -1;
			hsh = new Hash();
			hsh = hsh.sag(new Hash(r));
			for (int i = 0; i < choices.length; i++)
				hsh = hsh.sag(new Hash(choices[i]));
			hsh = hsh.sag(new Hash(v));
		}
		public String toString() {
			if (sval == null) {
				StringBuffer ret = new StringBuffer(val+":"+rule+":[");

//				StringBuffer ret = new StringBuffer(val+":"+i2r.get(rule)+":[");
				for (int i = 0; i < choices.length; i++)
					ret.append(choices[i]+" ");
				ret.append("]:");
				ret.append(kidcount);
				ret.append(":");
				sval = ret.toString();
			}
			return sval;
		}
		
		// kidcounts ONLY checked in compareTo now!
		
		public boolean equalTo(Object o) {
			return (val == ((KBestHeapKey)o).val); 
		}
		// kidcount always less because these functions are used differently depending which semiring
		// we're in

		public boolean greaterThan(Object o) {
			return (val > ((KBestHeapKey)o).val); 
		}
		public boolean lessThan(Object o) {
			return (val < ((KBestHeapKey)o).val); 
		}
		// for comparable. natural order. if tied, fewer kids means lessThan
		public int compareTo(KBestHeapKey o) {
			boolean debug = false;
			if (greaterThan(o))
				return 1;
			else if (lessThan(o))
				return -1;
			else if (kidcount < o.kidcount) {
				if (debug) Debug.debug(debug,kidcount+"<"+o.kidcount+" so "+this+"<"+o);			
				return -1;
			}
			else if (kidcount > o.kidcount) {
				if (debug) Debug.debug(debug,kidcount+">"+o.kidcount+" so "+this+">"+o);			
				return 1;
			}
			return 0;

		}
		
		
		public void setPos(int i) {
			pos = i;
		}
		public int getPos() {
			return pos;
		}
		// 	// equals, not to be confused with equalTo, is for
		// 	// hashset equality
		public boolean equals(Object o) {
			if (!o.getClass().equals(this.getClass()))
				return false;
			KBestHeapKey k = (KBestHeapKey)o;
			//	    if (!k.toString().equals(toString()))
			//		return false;
			if (val != k.val)
				return false;
			if (rule !=k.rule)
				return false;
			if (choices.length != k.choices.length)
				return false;
			for (int i = 0; i < choices.length; i++) {
				if (choices[i] != k.choices[i])
					return false;
			}
			return true;
		}
		public int hashCode() {
			return  hsh.bag(Integer.MAX_VALUE);
		}
	}
	
	/**
	 * @author jonmay
	 * reverse value compareTo version of the above
	 */
	static class ReverseKBestHeapKeyComparator implements Comparator<KBestHeapKey> {

		/* (non-Javadoc)
		 * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
		 */
		public int compare(KBestHeapKey o1, KBestHeapKey o2) {
			if (o1.greaterThan(o2))
				return -1;
			else if (o1.lessThan(o2))
				return 1;
			else if (o1.kidcount < o2.kidcount)
				return -1;
			else if (o1.kidcount > o2.kidcount)
				return 1;
			return 0;
		}

	}


	// heap key for outside algorithm
	class OutsideHeapKey implements Heapable, Comparable<OutsideHeapKey> {
		public double val;
		public Symbol key;
		private int pos;
		private String sval=null;
		public OutsideHeapKey(Symbol k, double v) {
			key = k;
			val = v;
			pos = -1;
		}
		public String toString() {
			if (sval == null)
				sval = val+":"+key.toString();
			return sval;
		}
		public boolean equalTo(Object o) {
			return (val == ((OutsideHeapKey)o).val);
		}
		public boolean greaterThan(Object o) {
			return (val > ((OutsideHeapKey)o).val);
		}
		public boolean lessThan(Object o) {
			return (val < ((OutsideHeapKey)o).val);
		}
		
		// for comparable
		public int compareTo(OutsideHeapKey o) {
			if (greaterThan(o))
				return 1;
			else if (lessThan(o))
				return -1;
			else if (equalTo(o))
				return 0;
			else {
				Debug.debug(true, "Not able to compare "+this+" and "+o);
				return 0;
			}
		}
		public void setPos(int i) {
			pos = i;
		}
		public int getPos() {
			return pos;
		}
		public void setVal(double v) {
			val = v;
		}
		// equals, not to be confused with equalTo, is for
		// hashset equality
		public boolean equals(Object o) {
			if (!o.getClass().equals(this.getClass()))
				return false;
			OutsideHeapKey k = (OutsideHeapKey)o;
			if (val != k.val)
				return false;
			if (key != k.key)
				return false;
			return true;
		}
		public int hashCode() {
			return toString().hashCode();
		}	
	}

	// ruleset is part of the kbest - it's associated
	private RuleSet rs;
	
	// new way of doing things: map symbols to ints and vice versa, then
	// do all downstream processing on ints only, storing results in arrays
	private Hashtable<Symbol, Integer> s2i;
	private Hashtable<Integer, Symbol> i2s;
	
	// map rules to ints and vice versa
	private Hashtable<Rule, Integer> r2i;
	private Hashtable<Integer, Rule> i2r;
	
	// track state sequence of rules
	private Vector<Vector<Integer>> rulekids;
	
	// heaps, indexed by lhs, of weighted candidate derivations stored in heaps
	private PriorityQueue<KBestHeapKey>[] candidates;
	
	// hashtable of hashsets, indexed by lhs, to prevent duplicate entry in lazy traversal
	private HashSet<KBestHeapKey>[] candidateSets;
	// vectors, indexed by lhs, of the kth best derivation

	private Vector<KBestHeapKey>[] derivs;
	// how large is each deriv? cheaper than doing vector size queries
	private int[] derivssize;

	// the maximum size of this deriv that has been processed. Allows us to not recalculate negatives
	private int[] derivcheck;
	

	// for stochastic generation
	private Random rand;
	// symbol -> double: 1-bests only. used for beta, too
	private HashMap<Symbol, Double> oneBests;
	// breaking ties in one bests = number of rules needed to hit leaves: shorter is better
	private HashMap<Symbol, Integer> oneBestDepths;
	// symbol -> double: alpha costs. initialized on demand
	private TObjectDoubleHashMap alphas = null;
	// symbol -> rule: 1-best rules. used to avoid infinite derivations
	// on unweighted grammars
	private Hashtable<Symbol, Rule> bestRules;

	// global highest k used
	private int globalK;

	// is this a tree production or a string production?
	private boolean isTree;

	public KBest(RuleSet ruleSet) throws UnusualConditionException {
		this(ruleSet, null);
	}

	public KBest(RuleSet ruleSet, Long seed) throws UnusualConditionException {
		rs = ruleSet;
		boolean debug = false;
		try {
			if (ruleSet instanceof RTGRuleSet)
				isTree = true;
			else if (ruleSet instanceof CFGRuleSet)
				isTree = false;
			else
				throw new UnexpectedCaseException("Tried to build Kbest without an RTGRuleSet or CFGRuleSet");
		}
		catch (UnexpectedCaseException e) {
			System.err.println("Other RuleSet defined but not handled in Kbest");
			System.exit(1);
		}
		
		// assign an integer to each symbol
		int nextint = 0;
		s2i = new Hashtable<Symbol, Integer>();
		i2s = new Hashtable<Integer, Symbol>();
		for (Symbol s : ruleSet.states) {
			s2i.put(s, nextint);
			if (debug) Debug.debug(debug, "Storing state "+s+" as "+nextint);
			i2s.put(nextint++, s);
		}
		
		
		
		candidates = new PriorityQueue[nextint];
		candidateSets = new HashSet[nextint];
		derivs = new Vector[nextint];
		derivssize = new int[nextint];
		derivcheck = new int[nextint];
		
		// assign an integer to each rule
		nextint = 0;
		r2i = new Hashtable<Rule, Integer>();
		i2r = new Hashtable<Integer, Rule>();
		rulekids = new Vector<Vector<Integer>>();
		for (Rule r : ruleSet.rules) {
			r2i.put(r, nextint);
			if (debug) Debug.debug(debug, "Storing rule "+r+" as "+nextint);
			i2r.put(nextint, r);
			Vector<Integer> kids = new Vector<Integer>();
			for (Symbol s : rs.getLeafChildren(r))
				kids.add(s2i.get(s));
			rulekids.add(nextint++, kids);
		}
		
		if (seed == null)
			rand = new Random();
		else
			rand = new Random(seed);
		oneBests = new HashMap<Symbol, Double>();
		oneBestDepths = new HashMap<Symbol, Integer>();
		bestRules = new Hashtable<Symbol, Rule>();
		globalK = 1;
		buildOneBest();
	}

	// bottom up pass initializes the top member of derivs
	// build until you don't build any more
	private void buildOneBest() throws UnusualConditionException {
		boolean debug = false;
		Semiring semiring = rs.getSemiring();
		int currentCount = 0;
		Iterator stit = null;
		do {
			currentCount = 0;
			if (debug) Debug.debug(debug, rs.states.size()+" states");
			Date startTime = new Date();
			stit = rs.states.iterator();
			while (stit.hasNext()) {
				Symbol s = (Symbol)stit.next();
				if (debug) Debug.debug(debug, "Considering state "+s.toString());
				boolean added = false;
				double currentBestScore = semiring.ZERO();
				ArrayList<Rule> currRules = rs.getRulesOfType(s);
				if (currRules == null)
					continue;
				Rule currBestRule = null;
				int currBestDepth = -1;
				for (Rule r : currRules) {
//					if (debug) Debug.debug(debug, "\tConsidering rule "+r.toString());
					double currentScore = r.getWeight();
					int currentDepth = 1;
					// check for validity (no children we haven't processed yet)
					boolean isOkay = true;
					Vector v = rs.getLeafChildren(r);
					for (int i = 0; i < v.size(); i++) {
						Symbol child = (Symbol)v.get(i);
						if (!oneBests.containsKey(child)) {
//							if (debug) Debug.debug(debug, "\t\t"+child.toString()+" makes rule invalid");
							isOkay = false;
							break;
						}
	//					if (debug) Debug.debug(debug, "Incorporating "+oneBests.get(child));
						currentScore = semiring.times(currentScore, oneBests.get(child));
						currentDepth += oneBestDepths.get(child);
					}
					if (!isOkay)
						continue;
					if (semiring.better(currentScore, currentBestScore) || 
							(semiring.betteroreq(currentScore,currentBestScore) && 
									(currBestDepth < 0 || currentDepth < currBestDepth))) {
					//	if (debug) Debug.debug(debug, "\t\tnew best rule with score of "+currentScore);
						currentBestScore = currentScore;
						added = true;
						currBestRule = r;
						currBestDepth = currentDepth;
					}
					else {
		//				if (debug) Debug.debug(debug, "\t\t"+currentScore+" doesn't exceed "+currentBestScore);
					}
				}
				// after all rules have been checked, if something has won, we've built a state
				// as long as its better than something that's already there
				if (added) {
					if (oneBests.containsKey(s)) {
						if (semiring.better(oneBests.get(s), currentBestScore)) {
					//		if (debug) Debug.debug(debug, "New best doesn't beat old best of "+currentBestScore+" for "+s.toString());
							continue;
						}
						if (semiring.betteroreq(oneBests.get(s),currentBestScore) && currBestDepth >= oneBestDepths.get(s)) {
					//		if (debug) Debug.debug(debug, "New best has same score as old best, "+currentBestScore+" but is longer for "+s.toString()+" so skipping");
							continue;
						}
					}
					bestRules.put(s, currBestRule);
					oneBests.put(s, currentBestScore);
					oneBestDepths.put(s, currBestDepth);
					 if (debug) Debug.debug(debug, "Best for "+s.toString()+" is "+currBestRule.toString()+" with score "+currentBestScore+" and depth "+currBestDepth);
					currentCount++;
				}
			}
			Date stopTime = new Date();
			if (debug) {
				long lapse = stopTime.getTime() - startTime.getTime();
				Debug.debug(debug, "Time for iteration: "+lapse);
			}
			if (debug) Debug.debug(debug, "Current count to "+currentCount+ " on this iteration");
		} while (currentCount > 0);
	}

	// construct a heap of the top productions of each rule that has state as its lhs.
	// this is the skeleton of the k-best problem - we will lazily add on cases that are not 
	// the top production of a rule, because they could be better than the top production of 
	// another rule.
	private void getCandidates(int state) throws UnusualConditionException {
		boolean debug = false;
		if (debug) Debug.debug(debug, "Getting candidates for "+state+" which is "+i2s.get(state));
		Semiring semiring = rs.getSemiring();
		ArrayList<Rule> currRules = rs.getRulesOfType(i2s.get(state));
		if (currRules == null)
			return;
		int j = 0;
		PriorityQueue<KBestHeapKey> h = null;
		if (semiring.ONE() > semiring.ZERO())
			h = new PriorityQueue<KBestHeapKey>(11, new ReverseKBestHeapKeyComparator());
		else
			h = new PriorityQueue<KBestHeapKey>();
		
		HashSet<KBestHeapKey> hs = new HashSet<KBestHeapKey>();
		candidates[state] = h;
		candidateSets[state] = hs;
		for (Rule r : currRules) {
			if (debug) Debug.debug(debug, "Considering "+r.toString());
			Vector kids = rs.getLeafChildren(r);
			if (debug) Debug.debug(debug, "There are "+kids.size()+" children");
			// build the heap key: rule, choice array, and 
			int choices[] = new int[kids.size()];
			double score = r.getWeight();
			for (int i = 0; i < choices.length; i++) {
				choices[i] = 1;
				if (!oneBests.containsKey((Symbol)kids.get(i)))
					throw new UnusualConditionException("Unable to get top best for "+((Symbol)kids.get(i)).toString()+
					"; perhaps there is something wrong with the RTG?\n");
				score = semiring.times(score, oneBests.get((Symbol)kids.get(i)));
			}
			if (!bestRules.containsKey(i2s.get(state)))
				throw new UnusualConditionException("No best rule for "+i2s.get(state)+" when considering "+r);
			boolean bestRule = bestRules.get(i2s.get(state)).equals(r);
			KBestHeapKey newKey = new KBestHeapKey(r2i.get(r), choices, score, bestRule ? 0 : choices.length);
			if (debug) Debug.debug(debug, "Adding "+newKey.toString()+" to heap for "+state);
			h.add(newKey);
			hs.add(newKey);
		}
	}

	// return the kth best score, chiefly by looking it up. But if not available, build it
	private double getKthBestScore(int state, int k) throws Exception {
		boolean debug = false;
		if (derivs[state] == null)
			throw new Exception("Attempting to get kth best score without initializing derivs for "+i2s.get(state));
		Vector<KBestHeapKey> map = derivs[state];
		if (derivssize[state] < k)
			throw new Exception("Unable to get "+k+"th best score of "+i2s.get(state)+" - should have checked derive first!");
		// k-1 because the vector is 0-based but we are 1-based
		return map.get(k-1).val;
	}

	// construct derivations. return false if it didn't go
	private boolean deriveKthBest(int state, int k) throws Exception {
		boolean debug = false;
		//currentGet++;
		if(debug) Debug.debug(debug, "Attempting to derive "+k+"th best of "+i2s.get(state));
		if (globalK < k) {
			if(debug) Debug.debug(debug, "Increasing global k to "+k);
			globalK = k;
		}
		// if we've already done it, don't redo it
		if (derivcheck[state] >= k)
			return (derivs[state] != null && derivssize[state] >= k);

		// initialize
		if (candidates[state] == null) {
			getCandidates(state);
			if (candidates[state] == null) {
				derivcheck[state] = k;
				return false;
			}
			Vector<KBestHeapKey> v = new Vector<KBestHeapKey>();
			PriorityQueue<KBestHeapKey> h = candidates[state];
			KBestHeapKey hk = h.poll();
			//Debug.prettyDebug("for "+state+": "+hk.toString());
			v.add(hk);
			derivs[state] = v;
			if (debug) Debug.debug(debug, "Created derivs for "+state);
			derivssize[state]++;
		}
		Vector<KBestHeapKey> v = derivs[state];
		PriorityQueue<KBestHeapKey> h = candidates[state];
		HashSet<KBestHeapKey> hs = candidateSets[state];
		if(debug) Debug.debug(debug, "Going into lazy addition, there are "+derivssize[state]+" honest derivs");
//		if(debug) Debug.debug(debug, "Trying for "+k+", and empty status of h is "+h.isEmpty());
		boolean wasEmpty = h.isEmpty();
		boolean trulyEmpty = false;
		while (derivssize[state] < k && !trulyEmpty) {
			KBestHeapKey key = (KBestHeapKey)v.get(derivssize[state]-1);
			// emptyTest: we're really empty if we were before lazyNext AND after lazyNext
			// use last iter as a guide
			lazyNext(h, hs, key);
			boolean isEmpty = h.isEmpty();
			trulyEmpty = isEmpty && wasEmpty;
			if (!trulyEmpty) {
				KBestHeapKey hk = h.poll();
				//Debug.prettyDebug("refilling "+state+": "+hk.toString());
				v.add(hk);
				derivssize[state]++;
			}
			wasEmpty=isEmpty;
		}
		derivcheck[state] = k;
		return (derivs[state] != null) && (derivssize[state] >= k);
	}

	// lazily advance the frontier
	private void lazyNext(PriorityQueue<KBestHeapKey> h, HashSet<KBestHeapKey> hs, KBestHeapKey key) throws Exception{
		boolean debug = false;
		//		currentLazy++;
		if(debug) Debug.debug(debug, "Lazily getting next along "+key.toString());
		int [] choices = key.choices;
		int r = key.rule;
		Vector<Integer> kids = rulekids.get(r);
		for (int i = 0; i < choices.length; i++) {
			int [] newchoice = new int[choices.length];
			System.arraycopy(choices, 0, newchoice, 0, choices.length);
			newchoice[i] += 1;
			deriveKthBest(kids.get(i), newchoice[i]);
			double score = i2r.get(r).getWeight();
			boolean isOkay = true;
			for (int j = 0; j < newchoice.length; j++) {
				int jth = kids.get(j);
				if (!deriveKthBest(jth, newchoice[j])) {
					if(debug) Debug.debug(debug, newchoice[j]+"th of "+jth+" prevents this lazy choice");
					isOkay = false;
					break;
				}
				score = rs.getSemiring().times(score, getKthBestScore(jth, newchoice[j]));
			}
			if (isOkay && newchoice[i] <= derivssize[kids.get(i)]) {
				KBestHeapKey lazyKey = new KBestHeapKey(r, newchoice, score, kids.size());
				if (!hs.contains(lazyKey)) {
					if(debug) Debug.debug(debug, "Lazily adding key "+lazyKey.toString());
					h.add(lazyKey);
					hs.add(lazyKey);
				}
				else {
					if(debug) Debug.debug(debug, "Duplicate key "+lazyKey.toString()+" not added");
				}
			}
		}
		if(debug) Debug.debug(debug, "Done with lazyNext along "+key.toString());
	}

	// get the deriv of a particular state
	private KBestHeapKey getKthBestDeriv(int state, int k) throws Exception {
		boolean debug = false;
		if (!deriveKthBest(state, k)) {
			Debug.prettyDebug("Warning: returning fewer trees than requested");
			return null;
		}
		return derivs[state].get(k-1);
	}
//	private int largestDeriv = 0;
//	private int currentDeriv = 0;
//	private int largestLazy = 0;
//	private int currentLazy = 0;
//	private int largestGet = 0; 
//	private int currentGet = 0;
	public Item[] getKBestItems(int k) {
		boolean getItems = true;
		Item[] ret;
		if (getItems) {

			if (isTree)
				ret = new TreeItem[k];
			else
				ret = new StringItem[k];
		}
		else {
			if (isTree)
				ret = new TreeItem[1];
			else
				ret = new StringItem[1];
		}
			
	//	largestDeriv = 0;
		for (int i = 1; i <= k; i++) {
			Vector<Rule> v = new Vector<Rule>();
//			currentDeriv = 0;
//			currentLazy=0;
//			currentGet=0;
			Item item = buildKBestItem(i, v, true);
			if (getItems) {
				ret[i-1] = item;
				//			Debug.prettyDebug("For "+i+" deriv="+currentDeriv+" lazy="+currentLazy+" get="+currentGet);
				//			Debug.prettyDebug("Deriv for "+i+" is "+currentDeriv);
				//			if (currentDeriv > largestDeriv)
				//				largestDeriv = currentDeriv;
				//			if (currentLazy > largestLazy)
				//				largestLazy = currentLazy;
				//			if (currentGet > largestGet)
				//				largestGet = currentGet;
				if (ret[i-1] == null)
					break;
			}
//			Debug.prettyDebug(i+":"+ret[i-1]+" # "+ret[i-1].weight);
//			Debug.debug(true, ret[i-1]+": "+ret[i-1].weight);
//			for (Rule r : v)
//				Debug.debug(true, "\t"+r);
			
		}
//		Debug.prettyDebug("For "+k+" deriv="+largestDeriv+" lazy="+largestLazy+" get="+largestGet);
		return ret;
	}


	// for composition, get TRTs tied to a rule set
	public TransducerRightTree[] getKBestTransducerRightTrees(int k) {
		boolean debug = false;
		TransducerRightTree[] ret = new TransducerRightTree[k];
		for (int i = 1; i <= k; i++) {
			Vector v = new Vector();
			ret[i-1] = buildKBestTransducerRightTree(i, v, true);
			if (ret[i-1] == null)
				break;
			// 	    Debug.debug(true, ret[i-1].toString()+": "+ret[i-1].weight);
			// 	    Iterator it = v.iterator();
			// 	    while (it.hasNext()) {
			// 		Debug.debug(true, "\t"+((RTGRule)it.next()).toString());
			//          }
		}
		return ret;
	}


	// actually build the item, somewhat recursively
	public Item buildKBestItem(int k, Vector<Rule> rset, boolean fillHash) {
		boolean debug = false;
		// if doWork is true, we actually get the tree. otherwise we just get the derivation structure.
		boolean doWork = true;
		if (debug) Debug.debug(debug, "Debugging "+k+" best");
		Item seed = null;
		try {
			if (isTree)
				seed = new TreeItem(rs.startState, rs.getSemiring().ONE());
			else
				seed = new StringItem(rs.startState, rs.getSemiring().ONE());
			KBestHeapKey deriv = getKthBestDeriv(s2i.get(rs.startState), k);
			if (doWork) {
			if (deriv != null) {
				if (debug) Debug.debug(debug, k+"th best deriv is "+deriv.toString());
				buildKBestItemDriver(seed, deriv, rset, fillHash, 0);
			}
			else
				seed = null;
			}
			else {
				seed = new TreeItem(Symbol.getEpsilon());
			}
			
		}
		catch (UnusualConditionException e) {
			System.err.println("Unusual condition while building k-best item: "+e.getMessage());
			System.exit(-1);
		}
		catch (Exception e) {
			StackTraceElement elements[] = e.getStackTrace();
			int n = elements.length;
			for (int i = 0; i < n; i++) {       
				System.err.println(e.toString()+": "+elements[i].getFileName() + ":" 
						+ elements[i].getLineNumber() 
						+ ">> " 
						+ elements[i].getMethodName() + "()");
			}
			System.err.println(e.getMessage());
			System.exit(-1);
		}
		return seed;
	}

	// do the work - stitch what's specified in deriv onto t and recurse
	private void buildKBestItemDriver(Item t, KBestHeapKey deriv, Vector<Rule> rset, boolean fillHash, int level) throws Exception {
		boolean debug = false;
		//	currentDeriv++;
		if (debug) Debug.debug(debug, level, "Deriv is "+deriv.toString());
		Vector<Integer> choicevec = new Vector<Integer>();
		for (int i : deriv.choices)
			choicevec.add(i-1);
//		Debug.prettyDebug(i2r.get(deriv.rule)+": "+choicevec);
		// difference between tree building and string building:
		// 1) each has a different deepCopy constructor
		// 2) for string building we need to identify when the end of this replacement level is. We can do this
		// by identifying the getNext() in the child list and scanning through it.


		Item stopItem=null;
		if (debug) Debug.debug(debug, level, "Copying "+i2r.get(deriv.rule)+" into "+t.toString());
		if (t instanceof StringItem) {
			StringItem tstr = (StringItem)t;
			// if no children, stopPoint is just all of them
			if (tstr.getNumChildren() > 0)
				stopItem = tstr.getNext();
			//	    if (!deriv.rule.getRHS().isEmptyString())
			tstr.deepCopy((StringItem)i2r.get(deriv.rule).getRHS());
			t = tstr;

		}
		else if (t instanceof TreeItem) {
			TreeItem ttr = (TreeItem)t;
			ttr.deepCopy((TreeItem)i2r.get(deriv.rule).getRHS());
			t = ttr;
		}
		else
			throw new UnexpectedCaseException ("Building k-best items that are neither tree nor string");
		if (fillHash)
			rset.add(i2r.get(deriv.rule));
		t.weight = i2r.get(deriv.rule).getWeight();
		//	if (debug) Debug.debug(debug, level, "Starting tree weight at "+t.weight);
		// find children that are nonterms in t and recurse on them, using the k specified in the deriv list
		// number of nodes has to be updated manually
		Item[] leaves = t.getItemLeaves();
		int nextItem = 0;
		if (debug) Debug.debug(debug, level, leaves.length+" leaves to check");
		for (int i = 0; i < leaves.length; i++) {
			if (leaves[i] == stopItem) {
				if (debug) Debug.debug(debug, level, "Reached stop point at "+i+", "+leaves[i].toString());		
				break;
			}
			if (debug) Debug.debug(debug, level, "Inspecting child "+i+", "+leaves[i].toString());
			if (rs.states.contains(leaves[i].label)) {
				if (debug) Debug.debug(debug, level, 
						leaves[i].label+" is a state archived as "+
						s2i.get(leaves[i].label)+" with derivs set "+
						derivs[s2i.get(leaves[i].label)]+"; getting item "+
						nextItem+", "+
						deriv.choices[nextItem]+"th best deriv");
				buildKBestItemDriver(leaves[i], getKthBestDeriv(s2i.get(leaves[i].label), deriv.choices[nextItem++]), rset, fillHash, level+1);
				// manual resetting of number of nodes -- hope this doesn't affect runtime!!
				t.setNumNodes();
				// combining step - if it's a state-to-state, leaves[0] == t. so just add the rule weight again.
				// otherwise, combine leaves into the parent
				if (t.equals(leaves[i]))
					t.weight = rs.getSemiring().times(t.weight, i2r.get(deriv.rule).getWeight());
				else
					t.weight = rs.getSemiring().times(t.weight, leaves[i].weight);
				t.setNumNodes();
				//		Debug.debug(true, level, "Tree now to "+t.toString()+"; weight now to "+t.weight+"; size now to "+t.numNodes());
			}
		}
	}

	// actually build the transducer rule RHS. mirror of buildKBestTree
	// build tree, somewhat recursively
	public TransducerRightTree buildKBestTransducerRightTree(int k, Vector rset, boolean fillHash) {
		boolean debug = false;
		TreeItem seedTree = null;
		TransducerRightTree retSeed = null;
		try {
			seedTree = new TreeItem(rs.startState, rs.getSemiring().ONE());
			retSeed = new TransducerRightTree();
			KBestHeapKey deriv = getKthBestDeriv(s2i.get(rs.startState), k);
			if (deriv != null) {
				if (debug) Debug.debug(debug, k+"th best deriv is "+deriv.toString());
				buildKBestTransducerRightTreeDriver(seedTree, retSeed, deriv, rset, fillHash, debug, 0);
			}
			else {
				seedTree = null;
				retSeed = null;
			}
		}
		catch (UnusualConditionException e) {
			System.err.println("Unusual condition while building k-best transducer right tree: "+e.getMessage());
			System.exit(-1);
		}
		catch (Exception e) {
			StackTraceElement elements[] = e.getStackTrace();
			int n = elements.length;
			for (int i = 0; i < n; i++) {       
				System.err.println(e.toString()+": "+elements[i].getFileName() + ":" 
						+ elements[i].getLineNumber() 
						+ ">> " 
						+ elements[i].getMethodName() + "()");
			}
			System.err.println(e.getMessage());
			System.exit(-1);
		}
		return retSeed;
	}

	// for returning transducer rule RHS. mirror of buildKBestTreeDriver with reference to tied transducer rules instead
	// do the work - stitch what's specified in deriv onto t and recurse
	private void buildKBestTransducerRightTreeDriver(TreeItem t, TransducerRightTree returnt, KBestHeapKey deriv, 
			Vector rset, boolean fillHash, 
			boolean debug, int level) throws Exception, UnusualConditionException {
		if (debug) Debug.debug(debug, level, "Deriv is "+deriv.toString());
		RTGRule therule = (RTGRule)i2r.get(deriv.rule);
		if (!therule.hasTransducerRule() && !((TreeItem)therule.getRHS()).isTransducerState())
			throw new UnusualConditionException("While building tree for transducer composition: "+
					therule+" is not tied to a transducer rule and doesn't represent a transducer state");
		t.deepCopy((TreeItem)therule.getRHS());
		// if a transducer rule inside, copy its right side into the tree
		if ((therule).hasTransducerRule()) {
			returnt.deepCopy(((TreeTransducerRule)therule.getTransducerRules().get(0)).getRHS());
		}
		// if a state, copy the state through and the hidden variable
		else {
			returnt.setState((therule).getRHS().getLabel());
			returnt.setVariable(((TreeItem)(therule).getRHS()).getHiddenVariable());
		}
		if (fillHash)
			rset.add(therule);
		t.weight = therule.getWeight();
		//	if (debug) Debug.debug(debug, level, "Starting tree weight at "+t.weight);
		// find children that are nonterms in t and recurse on them, using the k specified in the deriv list
		// find the appropriate returnt in this way:
		// 1) t maps directly to the left side of the transducer rule
		// 2) that state maps to a tree on the right side
		// 3) when deep copy was done, a pointer to the copy was (temporarily) set in the rule

		// leaves are guaranteed to map to a child of the left of the transducer rule

		// WARNING: if this is extended to StringItems (as it should be), make sure to trap for the "true" leaves,
		// as done in regular k-best traversal
		Item[] leaves = t.getItemLeaves();

		int nextItem = 0;
		for (int i = 0; i < leaves.length; i++) {
			if (debug) Debug.debug(debug, level, "Inspecting child "+i+", "+leaves[i].toString());
			if (rs.states.contains(leaves[i].label)) {
				TransducerLeftTree trleaf = therule.getTransducerRules().get(0).getLHS().getChild(i);
				HashSet rthsh = therule.getTransducerRules().get(0).getTRVM().getRHS(trleaf);
				if (rthsh.size() != 1)
					throw new UnusualConditionException("mappings of "+trleaf.toString()+" inside "+therule.getTransducerRules()+
							" should be 1 but are "+rthsh.size());

				TransducerRightTree nextreturn = ((TransducerRightTree)rthsh.iterator().next()).getLastCloned();
				//		if (debug) Debug.debug(debug, level, "It's a state! getting item "+nextItem+", "+deriv.choices[nextItem]+"th best deriv");
				buildKBestTransducerRightTreeDriver((TreeItem)leaves[i], nextreturn, 
						getKthBestDeriv(s2i.get(leaves[i].label), 
								deriv.choices[nextItem++]), rset, fillHash, debug, level+1);
				// combining step - if it's a state-to-state, leaves[0] == t. so just add the rule weight again.
				// otherwise, combine leaves into the parent
				if (t.equals(leaves[i]))
					t.weight = rs.getSemiring().times(t.weight, therule.getWeight());
				else
					t.weight = rs.getSemiring().times(t.weight, leaves[i].weight);
				//		if (debug) Debug.debug(debug, level, "Tree now to "+t.toString()+"; weight now to "+t.weight);
			}
		}
	}


	public Item getRandomItem(int z) {
		boolean debug = false;
		Semiring semiring = rs.getSemiring();
		Vector nodes = new Vector();
		Item t;
		if (isTree)
			t = new TreeItem(rs.startState, semiring.ONE());
		else
			t = new StringItem(rs.startState, semiring.ONE());
		nodes.add(t);
		int counter = 0;
		int maxChoice = 0;
		int minChoice = 100;
		while (!nodes.isEmpty() && (z == 0 || counter < z)) {
			counter++;
			int choice = (nodes.size() > 1) ? rand.nextInt(nodes.size()) : 0;
			while (choice < 0 || choice > nodes.size()) {
				Debug.debug(true, "Choice error: "+choice+", nodes size is "+nodes.size());
				Debug.debug(true, "Current choice range: "+minChoice+", "+maxChoice);
				choice = (nodes.size() > 1) ? rand.nextInt(nodes.size()) : 0;
			}
			if (choice > maxChoice)
				maxChoice = choice;
			if (choice < minChoice)
				minChoice = choice;
			Item child = (Item)(nodes.get(choice));
			nodes.removeElementAt(choice);
			// if a string item, we need to know when the leaves are not the recently expanded leaves
			Item stopItem = null;
			if (!isTree) {
				if (child.getNumChildren() > 0)
					stopItem = ((StringItem)child).getNext();
			}
			// expand node, incorporating weight into tree weight at top
			if (debug) Debug.debug(debug, "Before: "+t.toString()+" # "+t.weight);
			expandNode(child, t);
			if (debug) Debug.debug(debug, "After: "+t.toString()+" # "+t.weight);
			if (debug) Debug.debug(debug, "Current weight of "+t.toString()+" is "+t.weight);
			// child is now transformed into a tree. traverse this tree for nonterminal children and add them
			Item leaves[] = child.getItemLeaves();
			for (int i = 0; i < leaves.length; i++) {
				if (leaves[i] == stopItem) {
					if (debug) Debug.debug(debug, "Stop item reached in random expansion at "+i);
					break;
				}
				if (rs.states.contains(leaves[i].label))
					nodes.add(leaves[i]);
			}
		}
		// best item for rest of expansions
		if (!nodes.isEmpty()) {
			t.truncated = true;
			if (debug) Debug.debug(debug, "Reached limit of random expansion: adding best items to "+nodes.size()+" unexpanded nonterminals.");
			if (debug) Debug.debug(debug, "Item thus far is "+t.toString()+" # "+t.weight);
			for (int i = 0; i < nodes.size(); i++) {
				Item child = (Item)(nodes.get(i));
				if (debug) Debug.debug(debug, "Building best item under "+child.toString());
				try {
					KBestHeapKey deriv = getKthBestDeriv(s2i.get(child.label), 1);
					buildKBestItemDriver(child, deriv, null, false, 0);
				}
				catch (Exception e) {
					Debug.debug(true, "in getRandomItem: "+e.toString());
				}
				if (debug) Debug.debug(debug, "That best item is "+child.toString()+" # "+child.weight);
				t.weight = semiring.times(t.weight, child.weight);
				if (debug) Debug.debug(debug, "Item is now "+t.toString()+" # "+t.weight);
			}
		}
		return t;
	}

	// change t based on the rules available

	// TODO: note the slowness inherent here - we build up a normalizer first for each choice set. A quicker overall way to 
	// do this would be to normalize the weights of the rule set first, but this would change the rule set.
	// Another way to do this would be to memoize the total weight for a particular symbol, but that also seems like a kludge.

	private void expandNode(Item t, Item top) {
		boolean debug = false;
		if (debug) Debug.debug(debug, "Expanding item "+t.toString());
		Semiring semiring = rs.getSemiring();
		try {
			double predictor = semiring.convertFromReal(rand.nextDouble());
			ArrayList<Rule> choices = rs.getRulesOfType(t.label);

			// first see the range the predictor can be from
			double maxval = semiring.ZERO();
			for (Rule choice : choices) {
				if (debug) Debug.debug(debug, "Considering rule "+choice.toString());
				maxval = semiring.plus(maxval, choice.getWeight());
			}
			// and normalize the predictor appropriately
			predictor = semiring.times(predictor, maxval);

			// find the proper rule as follows: for each member of the
			// set, we increase the tally of probability. If predictor is 
			// less than that number, we choose this rule.
			double tally = semiring.ZERO();
			//Debug.debug(true, "Matching to predictor "+predictor);
			for (Rule choice : choices) {
				tally = semiring.plus(tally, choice.getWeight());
				//		    Debug.debug(true, "\tTally at "+tally);
				// modify this tree with our choice
				// weight of trees is irrelevant - we just want to incorporate it into
				// the top node weight
				if (semiring.better(tally, predictor)) {
					if (debug) Debug.debug(debug, "Chose rule "+choice.toString());
					double prevWeight = top.weight;
					if (t instanceof StringItem) {
						StringItem tstr = (StringItem)t;
						if (!choice.getRHS().isEmptyString())
							tstr.deepCopy((StringItem)choice.getRHS());
						t = tstr;
					}
					else if (t instanceof TreeItem) {
						TreeItem ttr = (TreeItem)t;
						ttr.deepCopy((TreeItem)choice.getRHS());
						t = ttr;
					}
					else
						throw new UnexpectedCaseException ("Getting stochastic items that are neither tree nor string");
					if (debug) Debug.debug(debug, "After substitution: "+t.toString());
					top.weight = semiring.times(prevWeight, choice.getWeight());
					return;
				}
			}
			Debug.debug(true, "WARNING: ITEM DIDN'T EXPAND. DEBUG ME!");
			Debug.debug(true, "Predictor was "+predictor+" but tally only got up to "+tally);
			Debug.debug(true, "Item at "+t.toString());
			// 	    Debug.debug(true, "Choices for "+t.label.toString()+":");
			// 	    choiceit = choices.iterator();
			// 	    while (choiceit.hasNext()) {
			// 		RTGRule choice = (RTGRule)choiceit.next();
			// 		Debug.debug(true, "\t"+choice.toString());
			// 	    }
		}
		catch (ArrayIndexOutOfBoundsException e) {
			System.err.println("ArrayIndexOutOfBoundsException: "+e.getMessage());
			e.printStackTrace();
		}
		catch (UnexpectedCaseException e) {
			System.err.println("UnexpectedCaseException: "+e.getMessage());
			e.printStackTrace();
		}
	}

	// alpha and beta routines: beta is part of getting best trees. alpha needs to be calculated separately
	private double getBeta(Symbol s) {
		return oneBests.get(s);
	}
	private double getAlpha(Symbol s) {
		if (alphas == null)
			calculateAlphas();
		return alphas.get(s);
	}
	// model cost is beta of start state
	public double getModelCost() {
		return oneBests.get(rs.getStartState());
	}
	// get all the alphas
	// TODO: replace Heap with PriorityQueue
	private void calculateAlphas() {
		Semiring semiring = rs.getSemiring();
		alphas = new TObjectDoubleHashMap();
		// instead of setting alpha, we assume semiring.ZERO() if they aren't in the hash
		alphas.put(rs.startState, semiring.ONE());
		Heap Q = new Heap(semiring.ONE() > semiring.ZERO());
		OutsideHeapKey startkey = new OutsideHeapKey(rs.startState, semiring.ONE());
		Q.insert(startkey);
		//	qset.add(startkey);
		while (!Q.isEmpty()) {
			OutsideHeapKey currkey = (OutsideHeapKey)Q.remove();
			Symbol x = currkey.key;
			double alphaCost = alphas.get(x);
			for (Rule currRule : rs.getRulesOfType(x)) {
				double totalCost = semiring.times(alphaCost, currRule.getWeight());
				Vector leaves = rs.getLeafChildren(currRule);
				Iterator it = leaves.iterator();
				while (it.hasNext()) {
					Symbol leaf = (Symbol)it.next();
					totalCost = semiring.times(totalCost, getBeta(leaf));
				}
				// now set the alpha values, if appropriate, for each nonterm. alpha is total cost with inside cost removed.
				it = leaves.iterator();
				while (it.hasNext()) {
					Symbol leaf = (Symbol)it.next();
					double outsideCost = semiring.times(totalCost, semiring.inverse(getBeta(leaf)));
					// somewhat different from jon's algorithm - insert on top rather than decreasing key
					double currentAlpha = (alphas.containsKey(leaf) ? alphas.get(leaf) : semiring.ZERO());
					if (semiring.better(outsideCost, currentAlpha)) {
						OutsideHeapKey newKey = new OutsideHeapKey(leaf, outsideCost);
						Q.insert(newKey);
						alphas.put(leaf, outsideCost);
					}
				}
			}
		}
	}

	// prune - prunes to within some constant number of the gamma (alpha + beta) 
	public void pruneRules(double kappa) {
		boolean debug = false;
		if (rs.rules == null || rs.rules.size() == 0)
			return;
		Semiring semiring = rs.getSemiring();
		kappa = semiring.convertFromReal(kappa);
		double tolerance = semiring.times(getAlpha(rs.startState), semiring.times(getBeta(rs.startState), kappa));
		if (debug) Debug.debug(debug, "Setting tolerance to "+getAlpha(rs.startState)+" x "+getBeta(rs.startState)+" x "+kappa+" = "+tolerance);
		ArrayList<Rule> newRules = new ArrayList<Rule>();
		for (Rule r : rs.getRules()) {
			// gamma for a rule is alpha of head (times) beta of tails (times) rule weight
			double gamma = semiring.times(getAlpha(r.getLHS()), r.getWeight());
			Vector leaves = rs.getLeafChildren(r);
			Iterator it = leaves.iterator();
			while (it.hasNext()) {
				Symbol leaf = (Symbol)it.next();
				gamma = semiring.times(gamma, getBeta(leaf));
			}
			if (debug) Debug.debug(debug, "Gamma for "+r+" is "+gamma);
			if (semiring.better(gamma, tolerance))
				newRules.add(r);
			else
				if (debug) Debug.debug(debug, "DITCHING "+r);
		}
		if (debug) Debug.debug(debug, "Going from "+rs.rules.size()+" to "+newRules.size());
		rs.rules = newRules;
		// now do a clean while we're in this weird state:
		rs.initialize();
		rs.pruneUseless();
		
	}



	// test code
	public static void main(String argv[]) {
		try {
			boolean doTree = false;
			if (doTree) {
				RealSemiring s = new RealSemiring();
				RTGRuleSet ruleSet = new RTGRuleSet(argv[0], s);
				KBest kb = new KBest(ruleSet);
				for (int i = 1; i <= 3; i++) {
					TreeItem t = (TreeItem)kb.buildKBestItem(i, null, false);
					Debug.debug(true, t.toString()+": "+s.internalToPrint(t.weight));
				}
				for (int i = 1; i <= 3; i++) {
					TreeItem t = (TreeItem)kb.getRandomItem(20);
					Debug.debug(true, t.toString()+": "+s.internalToPrint(t.weight));
				}
			}
			else {
				RealSemiring s = new RealSemiring();
				CFGRuleSet ruleSet = new CFGRuleSet(argv[0], s);
				KBest kb = new KBest(ruleSet);
				for (int i = 1; i <= 3; i++) {
					StringItem t = (StringItem)kb.buildKBestItem(i, null, false);
					Debug.debug(true, t.toString()+": "+s.internalToPrint(t.weight));
				}
				for (int i = 1; i <= 3; i++) {
					StringItem t = (StringItem)kb.getRandomItem(20);
					Debug.debug(true, t.toString()+": "+s.internalToPrint(t.weight));
				}
			}
		}
		catch (Exception e) {
			StackTraceElement elements[] = e.getStackTrace();
			int n = elements.length;
			for (int i = 0; i < n; i++) {       
				System.err.println(e.toString()+": "+elements[i].getFileName() + ":" 
						+ elements[i].getLineNumber() 
						+ ">> " 
						+ elements[i].getMethodName() + "()");
			}
			System.err.println(e.getMessage());
		}
	}

}








