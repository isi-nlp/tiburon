package edu.isi.tiburon;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;

import gnu.trove.TIntObjectHashMap;

// Abstract set of rules
// indexed by lhs. 
// useful classes are RTGRuleSet, CFGRuleSet

public abstract class RuleSet {

	public Symbol startState;
	ArrayList<Rule> rules;
	Hashtable<Integer, Rule> rulesByIndex;
	TIntObjectHashMap rulesByTie;
	// used for serialization

	int nextRuleIndex;

	// implicitly filled, automatically defined
	public HashSet<Symbol> states;
	// ditto, but also indirectly accessible
	Hashtable<Symbol, ArrayList<Rule>> rulesByLHS;

	// ditto, and ditto
	HashSet terminals;

	Semiring semiring;

	// memoization for getting leaf children - leaves that are states
	Hashtable<Rule, Vector<Symbol>> leafChildren;


	// normal form done only once
	boolean madeNormal = false;
	
	// so we don't removeEps too much
	// gets unset after determinization
	boolean epsRemoved=false;

	public RuleSet() {
		startState = null;
		rules = null;
		rulesByIndex = null;
		rulesByTie = null;
		states = null;
		rulesByLHS = null;
		terminals = null;
		semiring = null;
		nextRuleIndex = 0;
	}

	// accessors

	public Symbol getStartState() {
		return startState;
	}

	public int getNextRuleIndex() {
		return nextRuleIndex++;
	}

	public ArrayList<Rule> getRules() {
		return rules;
	}
	public HashSet<Symbol> getStates() {
		return states;
	}

	public int getNumRules() { return rules.size();}
	public int getNumStates() { 
		boolean debug = false;
		if (debug) Debug.debug(debug, "does rulesByLHS contain star? "+rulesByLHS.containsKey(Symbol.getStar()));
		if (debug) Debug.debug(debug, "does states ="+states+" contain star? "+states.contains(Symbol.getStar()));
		
		if (rulesByLHS.containsKey(Symbol.getStar()) || !states.contains(Symbol.getStar()))
			return states.size();
		else
			return states.size()-1;
	}
	public int getNumTerminals() { return terminals.size();}
	public Semiring getSemiring() { return semiring; }

	// just return the term set
	public HashSet getTerminals(){ return terminals; }

	// useful in indexing rules by a common lhs
	public ArrayList<Rule> getRulesOfType(Symbol s) {
		return rulesByLHS.get(s);
	}

	// access by index
	public Rule getRuleByIndex(int index) {
		return rulesByIndex.get(index);
	}

	// access by tie
	public ArrayList<Rule> getTiedRules(int index) {
		return (ArrayList<Rule>)rulesByTie.get(index);
	}

	// universal constructors

	// copy constructor. no memoization is copied
	public RuleSet(RuleSet rs) {
		startState = rs.startState;
		rules = new ArrayList<Rule>(rs.rules);
		rulesByIndex = new Hashtable<Integer, Rule>(rs.rulesByIndex);
		rulesByTie = new TIntObjectHashMap(rs.rulesByTie);
		nextRuleIndex = rs.nextRuleIndex;
		states = new HashSet<Symbol>(rs.states);
		rulesByLHS = new Hashtable<Symbol, ArrayList<Rule>> (rs.rulesByLHS);
		terminals = new HashSet(rs.terminals);
		madeNormal = rs.madeNormal;
		semiring = rs.semiring;
	}

	// "clean" ruleset that can be added to manually (inspiration from forward application)
	public RuleSet(Semiring s) {
		semiring = s;
		rules = new ArrayList<Rule>();
		states = new HashSet<Symbol>();
		rulesByLHS = new     Hashtable<Symbol, ArrayList<Rule>> ();
		rulesByIndex = new Hashtable<Integer, Rule>();
		rulesByTie = new TIntObjectHashMap();
		terminals = new HashSet();
	}

	// other universal methods

	// manual adding - in this way of doing things, rules do not dictate states and terminals. specifically, a state can
	// exist on a rhs that does not appear on an lhs! However, lhs of rules are still assumed to be states
	// because of this, there is a possibility of error - trap this error!
	public void addState(Symbol s) throws DataFormatException {
		if (terminals.contains(s))
			throw new DataFormatException("tried to add "+s.toString()+" as a state, but it is already a terminal");
		states.add(s);
		if (!rulesByLHS.containsKey(s))
			rulesByLHS.put(s, new ArrayList<Rule>());
	}

	// add new rule. Let lower classes handle the terminal finding part
	public void addRule(Rule r) throws DataFormatException {
		if (!states.contains(r.getLHS()))
			throw new DataFormatException("tried to add "+r.toString()+" as a rule, but lhs is not a recognized state!");
		rules.add(r);
		rulesByLHS.get(r.getLHS()).add(r);
		rulesByIndex.put(r.getIndex(), r);
		terminals.addAll(findTerminals(r.getRHS()));
	}

	abstract public HashSet findTerminals(Item i);

	// check that start state is a state
	public void addStartState(Symbol s) throws DataFormatException {
		if (!states.contains(s))
			throw new DataFormatException("tried to add "+s.toString()+" as a start state, but it is not a state");
		startState = s;
	}

	// mostly useful for training, this method chooses a random probability between 0.2 and 0.8 for each rule
	public void randomizeRuleWeights() {
		for (Rule r : rules) {
			double val = 0.2+ (Math.random()*0.6);
			r.setWeight(semiring.convertFromReal(val));
		}
	}

	// the -n flag in carmel: normalize weights
	public void normalizeWeights() {
		boolean debug=false;
		for (Symbol left : rulesByLHS.keySet()) {
			if (debug) Debug.debug(debug, "Checking rules with "+left);
			double counter = semiring.ZERO();
			for (Rule r : getRulesOfType(left)) {
				counter = semiring.plus(counter, r.getWeight());
			}
			if (debug) Debug.debug(debug, "Counter for "+left+" is "+counter);
			for (Rule r : getRulesOfType(left)) {
				if (debug) Debug.debug(debug, "Setting "+r+" to "+r.getWeight()+"/"+counter);
				r.setWeight(semiring.times(r.getWeight(), semiring.inverse(counter)));
			}
		}
	}

	// for convenience output is grouped by lhs.
	// init rules are first, then all the rest
	public String toString() {
		boolean debug = false;
		StringBuffer sb = new StringBuffer(startState.toString());
		sb.append("\n");

		ArrayList<Rule> initSet = getRulesOfType(startState);
		if (initSet != null) {
			for (Rule r : initSet) {
				// TODO: option to print 0-scoring rules
				if (semiring.betteroreq(semiring.ZERO(), r.getWeight()))
					continue;
				sb.append(r.toString());
				if (debug) Debug.debug(debug, "Added Rule "+r.toString()+" to printing buffer");
				// check if we have to print the tie
				if (r.getTie() > 0) {
					if (debug) Debug.debug(debug, "Rule has tie "+r.getTie());
					boolean seenMatch = false;
					for (Rule tierule : getTiedRules(r.getTie())) {
						if (debug) Debug.debug(debug, "Found matching rule "+tierule.getTie());
						if (tierule != r && semiring.better(tierule.getWeight(), semiring.ZERO())) {
							if (debug) Debug.debug(debug, "rule is valid");
							seenMatch = true;
							break;
						}
					}
					if (seenMatch)
						sb.append(" @ "+r.getTie());
				}
				sb.append("\n");
			}
		}
		for (Symbol left : rulesByLHS.keySet()) {
			if (left.equals(startState))
				continue;
			for (Rule r : getRulesOfType(left)) {
				// TODO: option to print 0-scoring rules
				if (semiring.betteroreq(semiring.ZERO(), r.getWeight()))
					continue;
				sb.append(r.toString());
				if (debug) Debug.debug(debug, "Added Rule "+r.toString()+" to printing buffer");
				// check if we have to print the tie
				if (r.getTie() > 0) {
					if (debug) Debug.debug(debug, "Rule has tie "+r.getTie());
					boolean seenMatch = false;
					for (Rule tierule : getTiedRules(r.getTie())) {
						if (debug) Debug.debug(debug, "Found matching rule "+tierule.getTie());
						if (tierule != r && semiring.better(tierule.getWeight(), semiring.ZERO())) {
							if (debug) Debug.debug(debug, "rule is valid");
							seenMatch = true;
							break;
						}
					}
					if (seenMatch)
						sb.append(" @ "+r.getTie());
				}
				sb.append("\n");
			}
		}   
		// 	for (int i = 0; i < rules.length; i++) {
		// 	    sb.append(rules[i].toString());
		// 	    sb.append("\n");
		// 	}
		return sb.toString();
	}
	
	// better than writing the whole thing to a single memory object
	public void print(OutputStreamWriter w) throws IOException {
		boolean debug = false;
		w.write(startState+"\n");
		ArrayList<Rule> initSet = getRulesOfType(startState);
		if (initSet != null) {
			for (Rule r : initSet) {
				// TODO: option to print 0-scoring rules
				if (semiring.betteroreq(semiring.ZERO(), r.getWeight()))
					continue;
				w.write(r.toString());
				if (debug) Debug.debug(debug, "Added Rule "+r.toString()+" to printing buffer");
				// check if we have to print the tie
				if (r.getTie() > 0) {
					if (debug) Debug.debug(debug, "Rule has tie "+r.getTie());
					boolean seenMatch = false;
					for (Rule tierule : getTiedRules(r.getTie())) {
						if (debug) Debug.debug(debug, "Found matching rule "+tierule.getTie());
						if (tierule != r && semiring.better(tierule.getWeight(), semiring.ZERO())) {
							if (debug) Debug.debug(debug, "rule is valid");
							seenMatch = true;
							break;
						}
					}
					if (seenMatch)
						w.write(" @ "+r.getTie());
				}
				w.write("\n");
			}
		}
		for (Symbol left : rulesByLHS.keySet()) {
			if (left.equals(startState))
				continue;
			for (Rule r : getRulesOfType(left)) {
				// TODO: option to print 0-scoring rules
				if (semiring.betteroreq(semiring.ZERO(), r.getWeight()))
					continue;
				w.write(r.toString());
				if (debug) Debug.debug(debug, "Added Rule "+r.toString()+" to printing buffer");
				// check if we have to print the tie
				if (r.getTie() > 0) {
					if (debug) Debug.debug(debug, "Rule has tie "+r.getTie());
					boolean seenMatch = false;
					for (Rule tierule : getTiedRules(r.getTie())) {
						if (debug) Debug.debug(debug, "Found matching rule "+tierule.getTie());
						if (tierule != r && semiring.better(tierule.getWeight(), semiring.ZERO())) {
							if (debug) Debug.debug(debug, "rule is valid");
							seenMatch = true;
							break;
						}
					}
					if (seenMatch)
						w.write(" @ "+r.getTie());
				}
				w.write("\n");
			}
		}   
		// 	for (int i = 0; i < rules.length; i++) {
		// 	    sb.append(rules[i].toString());
		// 	    sb.append("\n");
		// 	}
	}


	// universal methods


	// implicitly fill the set of states in
	// each lhs contributes to a state. anything on the rhs
	// that is not on the lhs is considered a terminal
	abstract public void initialize();

	// we get this in biginteger 
	abstract public String getNumberOfDerivations();


	// normal form exists for both versions
	abstract public void makeNormal();


	// a test of grammar validity: this function returns true if all rules 
	//can eventually be reached by the start symbol
	// if this is not true, it's a good sign that something is wrong with this grammar
	abstract public boolean isAllReachable();


	// isFinite returns true if there are not an infinite number of items that can be produced
	// from this set
	// includeProducers = true means rules that generate some symbol (as opposed to state transitions
	// or epsilon) count as finite
	
	abstract boolean isFinite(boolean includeProducers);

	// makeFinite is almost an exact copy of isFinite, above, but it removes rules that cause loops
//	abstract public void makeFinite();

	// a no-op in cfg land. replace q -> r transitions with
	// non-epsilon equivalent in rtg land
	abstract public void removeEpsilons() throws UnusualConditionException;


	// straightforward interpretation of COMPUTE-CLOSURE
	// input = initial epsilon weights
	// output = weights used in epsilon removal
	// TODO: sparse matrix improvement?
	public double[][] computeClosure(double[][] arr) throws UnusualConditionException {
		boolean debug = false;
		if (debug) {
			Debug.debug(debug, "Input:");
			for (int i = 0; i < arr.length; i++) {
				System.err.print("\t"+i+": ");
				for (int j = 0; j < arr[i].length; j++) {
					System.err.print(arr[i][j]+" ");
				}
				System.err.print("\n");
			}
		}
		for (int mid = 0; mid < arr.length; mid++) {
			double midstar = semiring.star(arr[mid][mid]);
			for (int beg = 0; beg < arr.length; beg++) {
				if (beg == mid)
					continue;
				// TODO: abort if beg->mid DNE
				double begmid = semiring.times(arr[beg][mid], midstar);
				for (int end = 0; end < arr[beg].length; end++) {
					if (end == mid)
						continue;
					double begmidend = semiring.times(begmid, arr[mid][end]);
					arr[beg][end] = semiring.plus(arr[beg][end], begmidend);
				}
			}
			for (int beg = 0; beg < arr.length; beg++) {
				if (beg == mid)
					continue;
				arr[mid][beg] = semiring.times(midstar, arr[mid][beg]);
				arr[beg][mid] = semiring.times(arr[beg][mid], midstar);
			}
			arr[mid][mid] = midstar;
		}
		if (debug) {
			Debug.debug(debug, "Output:");
			for (int i = 0; i < arr.length; i++) {
				System.err.print("\t"+i+": ");
				for (int j = 0; j < arr[i].length; j++) {
					System.err.print(arr[i][j]+" ");
				}
				System.err.print("\n");
			}
		}
		return arr;
	}
	
	
	// sometimes a rule set gets created with states that don't look so good when printed, but carry lots of 
	// semantic information. For example, determinization, application, derivation rule set creation, etc. If
	// we don't care about this information at all, the following method ensures the rule set is print safe, that is,
	// it can be read in again without any problems.

	// note that similar code exists elsewhere, most notably in determinization and drs creation. That is not quite as 
	// semantics-free as this - this is supposed to serve as a catch-all
	public void makePrintSafe() {
		boolean debug = false;
		// note: this shouldn't have to run!
		return;
//		if (debug) Debug.debug(debug, "making print safe");
//		Hashtable<Symbol, Symbol> alias = new Hashtable<Symbol, Symbol>();
//		int nextid = 0;
//		// convert the states
//		Iterator<Symbol> it = states.iterator();
//		while (it.hasNext()) {
//			Symbol s = it.next();
//			if (s == Symbol.getStar())
//				alias.put(s, s);
//			else
//				alias.put(s, SymbolFactory.getSymbol("q"+(nextid++)));    
//			if (debug) Debug.debug(debug, "Mapped state "+s.toString()+" as "+alias.get(s).toString());
//		}
//		// now change the rules
//		for (Rule r : rules) {
//			if (debug) Debug.debug(debug, "Transformed "+r.toString());
//			r.makePrintSafe(this, alias);
//			if (debug) Debug.debug(debug, " into "+r.toString());
//		}
//		// change the start state, re-initialize, and we're done
//		startState = alias.get(startState);
//		initialize();
	}


	// return and memoize the state leaves of a rule
	abstract public Vector<Symbol> getLeafChildren(Rule r);

	// prune useless - two-pass algorithm, based on knuth. First go bottom up and mark all states
	// that can be used to reach terminal symbols. Then go top-down and mark all states that can
	// be reached from the start symbol. Only keep states that satisfy both criteria.

	abstract public void pruneUseless();

}
