package edu.isi.tiburon;

// implementation of lazy k-best as described in huang/chiang 05
// just for derivation rule sets -- should be integrated!!


import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.Vector;

import mjr.heap.Heap;
import mjr.heap.Heapable;
import gnu.trove.TIntDoubleHashMap;
import gnu.trove.TIntIterator;
import gnu.trove.TIntObjectHashMap;
public class CascadeDRSKBest {

	// for getting k-best list
	// val dictates primary value. if vals are equal, look at kidcount.
	// fewer state children is "better".
	class KBestHeapKey implements Heapable {
		public double val;
		public double kidcount;
		public CascadeDerivationRule rule;
		public int[] choices;
		private String sval = null;
		private int pos;
		private Hash hsh;
		public KBestHeapKey(CascadeDerivationRule r, int[] c, double v, double kc) {
			val = v;
			kidcount = kc;
			rule = r;
			choices = c;
			pos = -1;
			hsh = new Hash();
			hsh = hsh.sag(r.getHash());
			for (int i = 0; i < choices.length; i++)
				hsh = hsh.sag(new Hash(choices[i]));
			hsh = hsh.sag(new Hash(v));
		}
		public String toString() {
			if (sval == null) {
				StringBuffer ret = new StringBuffer(val+":"+rule.toString()+":[");
				for (int i = 0; i < choices.length; i++)
					ret.append(choices[i]+" ");
				ret.append("]:");
				ret.append(kidcount);
				ret.append(":");
				sval = ret.toString();
			}
			return sval;
		}
		public boolean equalTo(Object o) {
			return (val == ((KBestHeapKey)o).val && 
					kidcount == ((KBestHeapKey)o).kidcount);
		}
		// kidcount always less because these functions are used differently depending which semiring
		// we're in

		public boolean greaterThan(Object o) {
			return (val > ((KBestHeapKey)o).val ||
					(val == ((KBestHeapKey)o).val && 
							kidcount > ((KBestHeapKey)o).kidcount));
		}
		public boolean lessThan(Object o) {
			return (val < ((KBestHeapKey)o).val ||
					(val == ((KBestHeapKey)o).val && 
							kidcount > ((KBestHeapKey)o).kidcount));
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
			if (!rule.equals(k.rule))
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

	// heap key for outside algorithm
	class OutsideHeapKey implements Heapable {
		public double val;
		public int key;
		private int pos;
		private String sval=null;
		public OutsideHeapKey(int k, double v) {
			key = k;
			val = v;
			pos = -1;
		}
		public String toString() {
			if (sval == null)
				sval = val+":"+key;
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
	private CascadeDerivationRuleSet rs;
	// heaps, indexed by lhs, of weighted candidate derivations stored in heaps
	private TIntObjectHashMap candidates;
	// hashtable of hashsets, indexed by lhs, to prevent duplicate entry in lazy traversal
	private TIntObjectHashMap candidateSets;
	// vectors, indexed by lhs, of the kth best derivation
	private TIntObjectHashMap derivs;
	// for stochastic generation
	private Random rand;
	// state int -> double: 1-bests only. used for beta, too
	private TIntDoubleHashMap oneBests;
	// state int -> double: alpha costs. initialized on demand
	private TIntDoubleHashMap alphas = null;
	// state int -> rule: 1-best rules. used to avoid infinite derivations
	// on unweighted grammars
	private TIntObjectHashMap bestRules;

	// global highest k used
	private int globalK;

	public CascadeDRSKBest(CascadeDerivationRuleSet ruleSet) throws UnusualConditionException {
		rs = ruleSet;
		candidates = new TIntObjectHashMap();
		candidateSets = new TIntObjectHashMap();
		derivs = new TIntObjectHashMap();
		rand = new Random();
		oneBests = new TIntDoubleHashMap();
		bestRules = new TIntObjectHashMap();
		globalK = 1;
		buildOneBest();
	}

	// bottom up pass initializes the top member of derivs
	// build until you don't build any more
	private void buildOneBest() throws UnusualConditionException {
		boolean debug = false;
		Semiring semiring = rs.getSemiring();
		int currentCount = 0;
		TIntIterator stit = null;
		if (debug) Debug.debug(debug, "About to get one best for "+rs);
		do {
			currentCount = 0;
			for (int s = rs.getMinState(); s < rs.getMaxState(); s++) {
				if (debug) Debug.debug(debug, "Considering state "+s);
				boolean added = false;
				double currentBestScore = semiring.ZERO();
				Vector<CascadeDerivationRule> currRules = rs.getRulesByLHS(s);
				if (currRules == null)
					continue;
				CascadeDerivationRule currBestRule = null;
				for (CascadeDerivationRule r : currRules) {
					if (debug) Debug.debug(debug, "\tConsidering rule "+r.toString());
					double currentScore = r.getWeight();
					// check for validity (no children we haven't processed yet)
					boolean isOkay = true;
					int[] v = rs.getLeafChildren(r);
					for (int i = 0; i < v.length; i++) {
						int child = v[i];
						if (!oneBests.containsKey(child)) {
							if (debug) Debug.debug(debug, "\t\t"+child+" makes rule invalid");
							isOkay = false;
							break;
						}
						currentScore = semiring.times(currentScore, oneBests.get(child));
					}
					if (!isOkay)
						continue;
					if (debug) Debug.debug(debug, "\tConsidering valid rule "+r+" and score "+currentScore);
					if (semiring.better(currentScore, currentBestScore)) {
						if (debug) Debug.debug(debug, "\t\tnew best rule with score of "+currentScore);
						currentBestScore = currentScore;
						added = true;
						currBestRule = r;
					}
					// 		    else {
					// 			if (currentBestScore == semiring.ZERO())
					// 			    if (debug) Debug.debug(debug, "\t\tcurrent score of "+currentScore+" not enough to beat ZERO ("+currentBestScore+")");
					// 			added = true;
					// 			currBestRule = r;
					// 			currentBestScore = currentScore;
					// 		    }
				}
				// after all rules have been checked, if something has won, we've built a state
				// as long as its better than something that's already there
				if (added) {
					if (oneBests.containsKey(s)) {
						if (!semiring.better(currentBestScore, oneBests.get(s))) {
							//			    if (debug) Debug.debug(debug, "New best doesn't beat old best for "+s.toString());
							continue;
						}
					}
					bestRules.put(s, currBestRule);
					oneBests.put(s, currentBestScore);
					if (debug) Debug.debug(debug, "Best for "+s+" is "+currBestRule.toString()+" with score "+currentBestScore);
					currentCount++;
				}
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
		if (debug) Debug.debug(debug, "Getting candidates for "+state);
		Semiring semiring = rs.getSemiring();
		Vector<CascadeDerivationRule> currRules = rs.getRulesByLHS(state);
		if (currRules == null)
			return;
		
		int j = 0;
		Heap h = new Heap(semiring.ONE() > semiring.ZERO());
		HashSet hs = new HashSet();
		candidates.put(state, h);
		candidateSets.put(state, hs);
		for (CascadeDerivationRule r : currRules) {
			
			if (debug) Debug.debug(debug, "Considering "+r.toString());
			int[] kids = rs.getLeafChildren(r);
			// build the heap key: rule, choice array, and 
			int choices[] = new int[kids.length];
			double score = r.getWeight();
			for (int i = 0; i < choices.length; i++) {
				choices[i] = 1;
				if (!oneBests.containsKey(kids[i]))
					throw new UnusualConditionException("Unable to get top best for "+kids[i]+
					"; perhaps there is something wrong with the RTG?");
				score = semiring.times(score, oneBests.get(kids[i]));
			}
			boolean bestRule = bestRules.get(state).equals(r);
			KBestHeapKey newKey = new KBestHeapKey(r, choices, score, bestRule ? 0 : choices.length);
			if (debug) Debug.debug(debug, "Adding "+newKey.toString()+" to heap for "+state);
			h.insert(newKey);
			hs.add(newKey);
		}
	}

	// return the kth best score, chiefly by looking it up. But if not available, build it
	private double getKthBestScore(int state, int k) throws UnusualConditionException {
		return getKthBestScore(state, k, false);
	}
	private double getKthBestScore(int state, int k, boolean debug) throws UnusualConditionException {
		if (!derivs.containsKey(state))
			throw new UnusualConditionException("Attempting to get kth best score without initializing derivs for "+state);
		Vector map = (Vector)derivs.get(state);
		if (map.size() < k)
			throw new UnusualConditionException("Unable to get "+k+"th best score of "+state+" - should have checked derive first!");
		// k-1 because the vector is 0-based but we are 1-based
		return ((KBestHeapKey)map.get(k-1)).val;
	}

	// construct derivations. return false if it didn't go
	private boolean deriveKthBest(int state, int k) throws UnusualConditionException {
		return deriveKthBest(state, k, false);
	}
	private boolean deriveKthBest(int state, int k, boolean debug) throws UnusualConditionException {
		if (debug) Debug.debug(debug, "Attempting to derive "+k+"th best of "+state);
		if (globalK < k) {
			if (debug) Debug.debug(debug, "Increasing global k to "+k);
			globalK = k;
		}
		// if we've already done it, don't redo it
		if (derivs.containsKey(state) && ((Vector)derivs.get(state)).size() >= k)
			return true;

		// initialize
		if (!candidates.containsKey(state)) {
			getCandidates(state);
			if (!candidates.containsKey(state))
				return false;
			Vector v = new Vector();
			Heap h = (Heap)candidates.get(state);
			v.add(h.remove());
			derivs.put(state, v);
		}
		Vector v = (Vector)derivs.get(state);
		Heap h = (Heap)candidates.get(state);
		HashSet hs = (HashSet)candidateSets.get(state);
		if (debug) Debug.debug(debug, "Going into lazy addition, there are "+v.size()+" honest derivs");
		if (debug) Debug.debug(debug, "Trying for "+k+", and empty status of h is "+h.isEmpty());
		boolean trulyEmpty = false;
		while (v.size() < k && !trulyEmpty) {
			KBestHeapKey key = (KBestHeapKey)v.get(v.size()-1);
			// emptyTest: empty before lazyNext AND after lazyNext
			boolean emptyTest = h.isEmpty();
			if (debug) Debug.debug(debug, "EmptyTest is "+emptyTest);
			lazyNext(h, hs, key, debug);
			trulyEmpty = emptyTest && h.isEmpty();
			if (debug) Debug.debug(debug, "TrulyEmpty is "+trulyEmpty);
			if (!trulyEmpty)
				v.add(h.remove());

		}
		return derivs.containsKey(state) && ((Vector)derivs.get(state)).size() >= k;
	}

	// lazily advance the frontier
	private void lazyNext(Heap h, HashSet hs, KBestHeapKey key) throws UnusualConditionException {
		lazyNext(h, hs, key, false);
	}
	private void lazyNext(Heap h, HashSet hs, KBestHeapKey key, boolean debug) throws UnusualConditionException {
		if (debug) Debug.debug(debug, "Lazily getting next along "+key.toString());
		int [] choices = key.choices;
		CascadeDerivationRule r = key.rule;
		int[] kids = rs.getLeafChildren(r);
		for (int i = 0; i < choices.length; i++) {
			int [] newchoice = new int[choices.length];
			System.arraycopy(choices, 0, newchoice, 0, choices.length);
			newchoice[i] += 1;
			deriveKthBest(kids[i], newchoice[i], debug);
			double score = r.getWeight();
			boolean isOkay = true;
			for (int j = 0; j < newchoice.length; j++) {
				if (!deriveKthBest(kids[j], newchoice[j], debug)) {
					if (debug) Debug.debug(debug, newchoice[j]+"th of "+kids[j]+" prevents this lazy choice");
					isOkay = false;
					break;
				}
				score = rs.getSemiring().times(score, getKthBestScore(kids[j], newchoice[j], debug));
			}
			if (isOkay && newchoice[i] <= ((Vector)derivs.get(kids[i])).size()) {
				KBestHeapKey lazyKey = new KBestHeapKey(r, newchoice, score, kids.length);
				if (!hs.contains(lazyKey)) {
					if (debug) Debug.debug(debug, "Lazily adding key "+lazyKey.toString());
					h.insert(lazyKey);
					hs.add(lazyKey);
				}
				else {
					if (debug) Debug.debug(debug, "Duplicate key "+lazyKey.toString()+" not added");
				}
			}
		}
	}

	// get the deriv of a particular state
	private KBestHeapKey getKthBestDeriv(int state, int k) throws UnusualConditionException {
		return getKthBestDeriv(state, k, false);
	}
	private KBestHeapKey getKthBestDeriv(int state, int k, boolean debug) throws UnusualConditionException {
		if (!deriveKthBest(state, k, debug)) {
			Debug.debug(true, "Warning: returning fewer trees than requested");
			return null;
		}
		return (KBestHeapKey)((Vector)derivs.get(state)).get(k-1);
	}
	public TreeItem[] getKBestTrees(int k) throws UnusualConditionException {
		TreeItem[] ret = new TreeItem[k];
		for (int i = 1; i <= k; i++) {
			Vector v = new Vector();
			ret[i-1] = buildKBestTree(i, v, true);
			if (ret[i-1] == null)
				break;
			// 	    Debug.debug(true, ret[i-1].toString()+": "+ret[i-1].weight);
			// 	    Iterator it = v.iterator();
			// 	    while (it.hasNext()) {
			// 		Debug.debug(true, "\t"+((Rule)it.next()).toString());
			//          }
		}
		return ret;
	}


	// actually build the tree, somewhat recursively
	public TreeItem buildKBestTree(int k, Vector rset, boolean fillHash) throws UnusualConditionException {
		boolean debug = false;
		TreeItem seedTree = null;
		try {
			seedTree = new TreeItem(SymbolFactory.getSymbol(""+rs.getStartState()), rs.getSemiring().ONE());
			KBestHeapKey deriv = getKthBestDeriv(rs.getStartState(), k, debug);
			if (deriv != null) {
				if (debug) Debug.debug(debug, k+"th best deriv is "+deriv.toString());
				buildKBestTreeDriver(seedTree, deriv, rset, fillHash, debug, 0);
			}
			else
				seedTree = null;
		}
		catch (UnusualConditionException e) {
			throw new UnusualConditionException("Unusual condition while building k-best tree: "+e.getMessage());
		}
		return seedTree;
	}

	// do the work - stitch what's specified in deriv onto t and recurse
	private void buildKBestTreeDriver(TreeItem t, KBestHeapKey deriv, 
			Vector rset, boolean fillHash, 
			boolean debug,int level) throws UnusualConditionException {
		//	if (debug) Debug.debug(debug, level, "Deriv is "+deriv.toString());
		if (debug) Debug.debug(debug, level, (deriv.rule.isVirtual() ? "" : deriv.rule.getRuleIndex()+" = ")+deriv.rule.toString());
		t.deepCopyCascadeDerivRule(deriv.rule);
		if (fillHash)
			rset.add(deriv.rule);
		t.weight = deriv.rule.getWeight();
		if (debug) Debug.debug(debug, level, "Starting tree "+t+" weight at "+t.weight);
		// find children that are nonterms in t and recurse on them, using the k specified in the deriv list
		TreeItem[] leaves = (TreeItem [])t.getItemLeaves();
		int nextItem = 0;
		for (int i = 0; i < leaves.length; i++) {
			if (debug) Debug.debug(debug, level, "Inspecting child "+i+", "+leaves[i].toString());
			// if it's not a number, we're at a terminal, so continue on
			try {
				int labelint = Integer.parseInt(leaves[i].label.toString());
				//if (rs.getStates().contains(labelint)) {
				if (labelint >= rs.getMinState() && labelint < rs.getMaxState()) {
					if (debug) Debug.debug(debug, level, "It's a state! getting item "+nextItem+", "+deriv.choices[nextItem]+"th best deriv");
					buildKBestTreeDriver(leaves[i], getKthBestDeriv(labelint, deriv.choices[nextItem++]), rset, fillHash, debug, level+1);
					// combining step - if it's a state-to-state, leaves[0] == t. so just add the rule weight again.
					// otherwise, combine leaves into the parent
					if (t.equals(leaves[i]))
						t.weight = rs.getSemiring().times(t.weight, deriv.rule.getWeight());
					else
						t.weight = rs.getSemiring().times(t.weight, leaves[i].weight);
					if (debug) Debug.debug(debug, level, "Tree now to "+t.toString()+"; weight now to "+t.weight);
				}
			}
			catch (NumberFormatException e) {
				if (debug) Debug.debug(debug, level, leaves[i].label.toString()+" is a label");
				continue;
			}
		}
	}

	// like trees, but alignment producing instead
	public String [] getKBestAlignments(int k) throws UnusualConditionException {
		boolean debug = false;
		String [] ret = new String[k];
		for (int i = 1; i <= k; i++) {
			ret[i-1] = buildKBestAlignment(i);
			if (ret[i-1] == null)
				break;
		}
		return ret;
	}

	public String buildKBestAlignment(int k) throws UnusualConditionException {
		boolean debug = false;
		StringBuffer ret = new StringBuffer();
		try {	
			// NOTE: kbest deriv debug turned off!
			KBestHeapKey deriv = getKthBestDeriv(rs.getStartState(), k, false);
			if (deriv != null) {
				if (debug) Debug.debug(debug, k+"th best deriv is "+deriv.toString());

				// NOTE: kbest alignment driver debug turned off!
				double score = buildKBestAlignmentDriver(ret, deriv, false, 0);
				if (debug) Debug.debug(debug, k+"th best alignment is "+ret.toString()+" with score "+score);
			}
		}
		catch (UnusualConditionException e) {
			throw new UnusualConditionException("Unusual condition while building k-best alignment: "+e.getMessage());
		}
		return ret.toString();
	}

	// do the work - stitch what's specified in deriv onto ret and recurse
	private double buildKBestAlignmentDriver(StringBuffer ret, KBestHeapKey deriv, 
			boolean debug, int level) throws UnusualConditionException {
		if (debug) Debug.debug(debug, level, "Deriv is "+deriv.toString());
		if (debug) Debug.debug(debug, level, (deriv.rule.isVirtual() ? "" : deriv.rule.getRuleIndex()+" = ")+deriv.rule.toString());
		double weight = deriv.rule.getWeight();
		StringBuffer as = deriv.rule.getAlignString();
		if (as != null) {
			ret.append(as);
			if (debug) Debug.debug(debug, level, "Adding "+as.toString()+" to alignment string");
		}
		// recurse on children
		int[] leaves = deriv.rule.getRHS();
		if (debug) Debug.debug(debug, level, deriv.rule.getNumChildren()+" leaves");
		for (int i = 0; i < deriv.rule.getNumChildren(); i++) {
			if (debug) Debug.debug(debug, level, "Inspecting child "+i+", "+leaves[i]);
			weight = rs.getSemiring().times(weight, buildKBestAlignmentDriver(ret, getKthBestDeriv(leaves[i], deriv.choices[i], debug), debug, level+1));
		}
		return weight;
	}


	//     // stochastic tree generation
	//     public Tree getRandomTree(int z) {
	// 	return getRandomTree(z, false);
	//     }
	//     public Tree getRandomTree(int z, boolean debug) {
	// 	Semiring semiring = rs.getSemiring();
	// 	Vector nodes = new Vector();
	// 	Tree t = new Tree(rs.startState, semiring.ONE());
	// 	nodes.add(t);
	// 	int counter = 0;
	// 	int maxChoice = 0;
	// 	int minChoice = 100;
	// 	while (!nodes.isEmpty() && counter < z) {
	// 	    counter++;
	// 	    int choice = (nodes.size() > 1) ? rand.nextInt(nodes.size()) : 0;
	// 	    while (choice < 0 || choice > nodes.size()) {
	// 		Debug.debug(true, "Choice error: "+choice+", nodes size is "+nodes.size());
	// 		Debug.debug(true, "Current choice range: "+minChoice+", "+maxChoice);
	// 		choice = (nodes.size() > 1) ? rand.nextInt(nodes.size()) : 0;
	// 	    }
	// 	    if (choice > maxChoice)
	// 		maxChoice = choice;
	// 	    if (choice < minChoice)
	// 		minChoice = choice;
	// 	    Tree child = (Tree)(nodes.get(choice));
	// 	    nodes.removeElementAt(choice);
	// 	    // expand node, incorporating weight into tree weight at top
	// 	    if (debug) Debug.debug(debug, "Before: "+t.toString()+" # "+t.weight);
	// 	    expandNode(child, t);
	// 	    if (debug) Debug.debug(debug, "After: "+t.toString()+" # "+t.weight);
	// 	    if (debug) Debug.debug(debug, "Current weight of "+t.toString()+" is "+t.weight);
	// 	    // child is now transformed into a tree. traverse this tree for nonterminal children and add them
	// 	    Tree leaves[] = child.getTreeLeaves();
	// 	    for (int i = 0; i < leaves.length; i++) {
	// 		if (rs.states.contains(leaves[i].label))
	// 		    nodes.add(leaves[i]);
	// 	    }
	// 	}
	// 	// best tree for rest of expansions
	// 	if (!nodes.isEmpty()) {
	// 	    t.truncated = true;
	// 	    if (debug) Debug.debug(debug, "Reached limit of random expansion: adding best trees to "+nodes.size()+" unexpanded nonterminals.");
	// 	    if (debug) Debug.debug(debug, "Tree thus far is "+t.toString()+" # "+t.weight);
	// 	    for (int i = 0; i < nodes.size(); i++) {
	// 		Tree child = (Tree)(nodes.get(i));
	// 		if (debug) Debug.debug(debug, "Building best tree under "+child.toString());
	// 		try {
	// 		    KBestHeapKey deriv = getKthBestDeriv(child.label, 1);
	// 		    buildKBestTreeDriver(child, deriv, null, false, false, 0);
	// 		}
	// 		catch (Exception e) {
	// 		    Debug.debug(true, "in getRandomTree: "+e.toString());
	// 		}
	// 		if (debug) Debug.debug(debug, "That best tree is "+child.toString()+" # "+child.weight);
	// 		t.weight = semiring.times(t.weight, child.weight);
	// 		if (debug) Debug.debug(debug, "Tree is now "+t.toString()+" # "+t.weight);
	// 	    }
	// 	}
	// 	return t;
	//     }

	//     // change t based on the rules available
	//     // this was done with non-log probs in mind and a weird assumption
	//     // that the output tree would want log. We need to convert for choice purposes
	//     // into non-log probs, but return value is proper tree weight
	//     // the rule weight is only incorporated into the top node, since that's what we read from

	//     // TODO: note the slowness inherent here - we build up a normalizer first for each choice set. A quicker overall way to 
	//     // do this would be to normalize the weights of the rule set first, but this would change the rule set.
	//     // Another way to do this would be to memoize the total weight for a particular symbol, but that also seems like a kludge.

	//     private void expandNode(Tree t, Tree top) {
	// 	Semiring semiring = rs.getSemiring();
	// 	try {
	// 	    double predictor = rand.nextDouble();
	// 	    HashSet choices = rs.getRulesOfType(t.label);

	// 	    // first see the range the predictor can be from
	// 	    Iterator choiceit = choices.iterator();
	// 	    double maxval = 0;
	// 	    while (choiceit.hasNext()) {
	// 		Rule choice = (Rule)choiceit.next();
	// 		double incamt = 0;
	// 		incamt = Math.exp(-choice.getProb());
	// 		maxval+=incamt;
	// 	    }
	// 	    // and normalize the predictor appropriately
	// 	    predictor *= maxval;

	// 	    // find the proper rule as follows: for each member of the
	// 	    // set, we increase the tally of probability. If predictor is 
	// 	    // less than that number, we choose this rule.
	// 	    double tally = 0;
	// 	    choiceit = choices.iterator();
	// 	    //Debug.debug(true, "Matching to predictor "+predictor);
	// 	    while (choiceit.hasNext()) {
	// 		Rule choice = (Rule)choiceit.next();
	// 		double incamt = 0;
	// 		incamt = Math.exp(-choice.getProb());
	// 		tally += incamt;
	// 		//		    Debug.debug(true, "\tTally at "+tally);
	// 		// modify this tree with our choice
	// 		// weight of trees is irrelevant - we just want to incorporate it into
	// 		// the top node weight
	// 		if (predictor < tally) {
	// 		    double prevWeight = top.weight;
	// 		    t.deepCopy(choice.getRHS());
	// 		    top.weight = semiring.times(prevWeight, choice.getProb());
	// 		    return;
	// 		}
	// 	    }
	// 	    Debug.debug(true, "WARNING: TREE DIDN'T EXPAND. DEBUG ME!");
	// 	    Debug.debug(true, "Predictor was "+predictor+" but tally only got up to "+tally);
	// 	    Debug.debug(true, "Tree at "+t.toString());
	// // 	    Debug.debug(true, "Choices for "+t.label.toString()+":");
	// // 	    choiceit = choices.iterator();
	// // 	    while (choiceit.hasNext()) {
	// // 		Rule choice = (Rule)choiceit.next();
	// // 		Debug.debug(true, "\t"+choice.toString());
	// // 	    }
	// 	}
	// 	catch (ArrayIndexOutOfBoundsException e) {
	// 	    e.printStackTrace();
	// 	}
	//     }

	// alpha and beta routines: beta is part of getting best trees. alpha needs to be calculated separately
	private double getBeta(int s) {
		return oneBests.get(s);
	}
	private double getAlpha(int s) {
		if (alphas == null)
			calculateAlphas();
		return alphas.get(s);
	}
	// get all the alphas
	private void calculateAlphas() {
		Semiring semiring = rs.getSemiring();
		alphas = new TIntDoubleHashMap();
		// instead of setting alpha, we assume semiring.ZERO() if they aren't in the hash
		alphas.put(rs.getStartState(), semiring.ONE());
		Heap Q = new Heap(semiring.ONE() > semiring.ZERO());
		OutsideHeapKey startkey = new OutsideHeapKey(rs.getStartState(), semiring.ONE());
		Q.insert(startkey);
		//	qset.add(startkey);
		while (!Q.isEmpty()) {
			OutsideHeapKey currkey = (OutsideHeapKey)Q.remove();
			int x = currkey.key;
			Vector<CascadeDerivationRule> currRules = rs.getRulesByLHS(x);
			
			double alphaCost = alphas.get(x);
			for (CascadeDerivationRule currRule : currRules) {
				double totalCost = semiring.times(alphaCost, currRule.getWeight());
				int [] leaves = rs.getLeafChildren(currRule);
				for (int i = 0; i < leaves.length; i++) {
					int leaf = leaves[i];
					totalCost = semiring.times(totalCost, getBeta(leaf));
				}
				// now set the alpha values, if appropriate, for each nonterm. alpha is total cost with inside cost removed.
				for (int i = 0; i < leaves.length; i++) {
					int leaf = leaves[i];
					double outsideCost;
					// avoid the close-to-zero ones.
					if (totalCost == getBeta(leaf))
						outsideCost = semiring.ONE();
					else
						outsideCost = semiring.times(totalCost, semiring.inverse(getBeta(leaf)));
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

	//     // prune - prunes to within some constant number of the gamma (alpha + beta) 
	//     public void pruneRules(double kappa) {
	// 	Semiring semiring = rs.getSemiring();
	// 	double tolerance = semiring.times(getAlpha(rs.startState), semiring.times(getBeta(rs.startState), kappa));
	// 	HashSet newRules = new HashSet();
	// 	Iterator rit = rs.rules.iterator();
	// 	while (rit.hasNext()) {
	// 	    Rule r = (Rule)rit.next();
	// 	    // gamma for a rule is alpha of head (times) beta of tails (times) rule weight
	// 	    double gamma = semiring.times(getAlpha(r.getLHS()), r.getProb());
	// 	    Vector leaves = rs.getLeafChildren(r);
	// 	    Iterator it = leaves.iterator();
	// 	    while (it.hasNext()) {
	// 		Symbol leaf = (Symbol)it.next();
	// 		gamma = semiring.times(gamma, getBeta(leaf));
	// 	    }
	// 	    if (semiring.better(gamma, tolerance))
	// 		newRules.add(r);
	// 	}
	// 	rs.rules = newRules;
	// 	// now do a clean while we're in this weird state:
	// 	rs.pruneUseless();
	//     }



	//     // test code
	//     public static void main(String argv[]) {
	// 	try {
	// 	    TropicalAccumulativeSemiring s = new TropicalAccumulativeSemiring();
	// 	    RuleSet ruleSet = new RuleSet(argv[0], s);
	// 	    CascadeDRSKBest kb = new DRSKBest(ruleSet);
	// 	    for (int i = 1; i <= 10; i++) {
	// 		Tree t = kb.buildKBestTree(i, null, false);
	// 		Debug.debug(true, t.toString()+": "+t.weight);
	// 	    }
	// 	}
	// 	catch (Exception e) {
	// 	    StackTraceElement elements[] = e.getStackTrace();
	// 	    int n = elements.length;
	// 	    for (int i = 0; i < n; i++) {       
	// 		System.err.println(e.toString()+": "+elements[i].getFileName() + ":" 
	// 				   + elements[i].getLineNumber() 
	// 				   + ">> " 
	// 				   + elements[i].getMethodName() + "()");
	// 	    }
	// 	    System.err.println(e.getMessage());
	// 	}
	//     }

}








