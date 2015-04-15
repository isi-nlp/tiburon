package edu.isi.tiburon;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.StreamTokenizer;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.Stack;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.isi.tiburon.EarleyState.ESPair;
import edu.isi.tiburon.EarleyState.ESStateList;


import gnu.trove.TIntObjectHashMap;

public class RTGRuleSet extends RuleSet {

	// until we replace it with an array as in cfgruleset
	
	// for assigning new state names in weighted determinizer
	private Hashtable determNames = null;
	private int nextDeterm = 0;


	// for timing in weighted determinize
	private long timeLimit = 0;
	private long startTime = 0;
	private long lastLapse = 0;




	
	// for debugging only - after a weighted determinization the determnames
	// array has references to the newstateset objects the name represents.
	// if the calling class has newstateset objects imported, these can be
	// printed out. Just return the whole hash map for later dealing
	public Hashtable getDetermNames() { return determNames;}

	// implicitly fill the set of states in
	// each lhs contributes to a state. anything on the rhs
	// that is not on the lhs is considered a terminal
	public void initialize() {
		boolean debug = false;
		if (debug) Debug.debug(debug, "initializing RTG");
		leafChildren = new Hashtable<Rule, Vector<Symbol>>();
		states = new HashSet();
		states.add(startState);
		// star is always a state in the rtg, but
		// may not be in any rules
		states.add(Symbol.getStar());
		rulesByLHS = new Hashtable();
		rulesByIndex = new Hashtable<Integer, Rule>();
		rulesByTie = new TIntObjectHashMap();
		Iterator i = rules.iterator();
		while (i.hasNext()) {
			RTGRule r = (RTGRule)i.next();
			states.add(r.getLHS());
			if (!rulesByLHS.containsKey(r.getLHS()))
				rulesByLHS.put(r.getLHS(), new ArrayList<Rule>());
			rulesByLHS.get(r.getLHS()).add(r);
			rulesByIndex.put(r.getIndex(), r);
			if (r.getTie() > 0) {
				if (!rulesByTie.containsKey(r.getTie()))
					rulesByTie.put(r.getTie(), new ArrayList<Rule>());
				((ArrayList<Rule>)rulesByTie.get(r.getTie())).add(r);
			}
		}
		// Note: must go recursive on finding terms because
		// this set of rules may not be in normal form
		terminals = new HashSet();
		i = rules.iterator();
		while (i.hasNext()) {
			RTGRule r = (RTGRule)i.next();
			terminals.addAll(findTerminals((TreeItem)r.getRHS()));
		}

		
		// sanity check
		// 	Iterator termit = terminals.iterator();
		// 	Debug.debug(true, "Terminals:");
		// 	while (termit.hasNext()) {
		// 	    Debug.debug(true, "\t"+(String)termit.next());
		//      }

	}

	// recursively search for terminal symbols
	public HashSet findTerminals(Item i) {
		TreeItem t = (TreeItem) i;
		HashSet s = new HashSet();
		// transducer state terminals don't count as real terminals
		if (t.isTransducerState())
			return s;
		if (t.numChildren == 0) {
			if (!states.contains(t.label))
				s.add(t.label);
		}
		else {
			for (int kid = 0; kid < t.numChildren; kid++)
				s.addAll(findTerminals(t.children[kid]));
		}
		return s;
	}

	public RTGRuleSet(Semiring s) {
		super(s);
	}

	// we should be adding rules to this
	public RTGRuleSet(Symbol ss, Semiring s) {
		super(s);
		startState = ss;
		rules = new ArrayList<Rule>();
		nextRuleIndex = 0;
		initialize();
	}

	// copy constructor. no memoization is copied
	public RTGRuleSet(RuleSet rs) {
		super(rs);
	}

	public RTGRuleSet(Symbol ss, RTGRule[] rs, Semiring s) {
		semiring = s;
		startState = ss;
		rules = new ArrayList<Rule>();
		nextRuleIndex = 0;
		for (int i = 0; i < rs.length; i++)
			rules.add(rs[i]);
		initialize();
	}
	public RTGRuleSet(String filename, Semiring s) throws FileNotFoundException, IOException, DataFormatException  {
		this(new BufferedReader(new InputStreamReader(new FileInputStream(filename), "utf-8")), s);
	}
	public RTGRuleSet(String filename, String encoding, Semiring s) throws FileNotFoundException, IOException, DataFormatException  {
		this(new BufferedReader(new InputStreamReader(new FileInputStream(filename), encoding)), s);
	}
	// read from file

	// empty spaces or comments regions
	private static Pattern commentPat = Pattern.compile("\\s*(%.*)?");
	
	// something that can be a start state -- no spaces, no parens
	// can be followed by whitespace and comment
	private static Pattern startStatePat = Pattern.compile("\\s*(\\S+)\\s*(%.*)?");
	
	// strip comments off
	private static Pattern commentStripPat = Pattern.compile("\\s*(.*?[^\\s%])(\\s*(?:%.*)?)?");

	public RTGRuleSet(BufferedReader br, Semiring s) throws  IOException, DataFormatException  {
		boolean debug = false;
		semiring = s;	
		nextRuleIndex = 0;

		String line = br.readLine();
		// 1) ignore all comments fields and blank lines in the header
		Matcher commentMatch = commentPat.matcher(line);
		while (commentMatch.matches()) {
			if (debug) Debug.debug(debug, "Ignoring comment/whitespace: "+line);
			try {
				line = br.readLine();
			}
			catch (IOException e) {
				throw new IOException("Couldn't read start state");
			}
			commentMatch = commentPat.matcher(line);
		}
		

		// 2) get start state
		Matcher startStateMatch = startStatePat.matcher(line);
		if (debug) Debug.debug(debug, "Trying to get a start state out of "+line);
		if (!startStateMatch.matches())
			throw new IOException("Could not find start state in "+line);
		
		rules = new ArrayList<Rule>();
		startState = SymbolFactory.getSymbol(startStateMatch.group(1));
		
		// 3) get rules, skipping white space and comments
		int rulecounter = 0;
		Date readTime = new Date();
		boolean didPrintWarning = false;
		while (br.ready()) {
			line = br.readLine();
			if (debug) Debug.debug(debug, "Trying to get a rule out of "+line);
			commentMatch = commentPat.matcher(line);
			if (commentMatch.matches()) {
				if (debug) Debug.debug(debug, "Ignoring comment/whitespace: "+line);
				continue;
			}
			Matcher commentStripMatch = commentStripPat.matcher(line);
			if (!commentStripMatch.matches())
				throw new DataFormatException("Couldn't strip comments off of "+line);
			String ruleText = commentStripMatch.group(1);

			if (debug) Debug.debug(debug, "Isolated "+ruleText+" for rule");
			RTGRule r = null;
			try {
				r = new RTGRule(this, ruleText, semiring);
			}
			catch (DataFormatException e) {
				throw new DataFormatException(ruleText+", "+e.getMessage(), e);
			}
			if (debug) Debug.debug(debug, "Made rule "+r.toString());
			if (debug) Debug.debug(debug, "Rule's tie id is "+r.getTie());
			rules.add(r);
			rulecounter++;
			if (rulecounter >= 10000 && rulecounter % 10000 == 0) {
				Date pause = new Date();
				long lapse = pause.getTime() - readTime.getTime();
				readTime = pause;
				if (!didPrintWarning){
					Debug.prettyDebug("File is large (>10,000 rules) so time to read in will be reported below");
					didPrintWarning = true;
				}
				Debug.prettyDebug("Read "+rulecounter+" rules: "+lapse+" ms");
			}
			
		}
		br.close();

		initialize();
		pruneUseless();
		if (didPrintWarning)
			Debug.prettyDebug("Done reading large file");
	}
	
	
	// so states are not reused!
	static int nextStateNum = 0;
	// build a finite, fairly derivative grammar from the single tree that it produces.
	public RTGRuleSet(TreeItem t, Semiring s) {
		boolean debug = false;
		semiring = s;
		if (debug )Debug.debug(debug, "Semiring is "+semiring);
		// prepend new states with "TG" = tree grammar.
		startState = SymbolFactory.getStateSymbol("TG-"+(nextStateNum));
		madeNormal = false;
		rules = new ArrayList<Rule>();
		nextRuleIndex = 0;

		if (debug) Debug.debug(debug, "Start symbol is "+startState+"; building rules from "+t);
		nextStateNum = fillRules(rules, t, nextStateNum);
		initialize();
	}


	// rule set from a DerivationRuleSet - the idea is that I want to do things that I already do with the RuleSet, so this
	// is a simple conversion that has canonical names for states, labels, things like that. It makes use of the fact that:
	// 1) Labels are either transducerrules with indices or null
	// 2) structure of derivation rules is flat
	// 3) states are just numbers
	// 
	// for this to be useful to people they have to associate the non-virtual labels (i.e. non-null) with transducer rules. These are,
	// by definition, in correspondence with the order in which they appear in their file
	public RTGRuleSet(DerivationRuleSet drs) {
		semiring = drs.getSemiring();
		startState = SymbolFactory.getSymbol("S"+drs.getStartState());
		rules = new ArrayList<Rule>();
		nextRuleIndex = 0;
		Iterator it = drs.getRules().iterator();
		int nextVirt = 0;
		while (it.hasNext()) {
			DerivationRule dr = (DerivationRule)it.next();
			// lhs is just the lhs of dr, symbol-ized
			Symbol lhs = SymbolFactory.getSymbol("S"+dr.getLHS());
			// rhs is a flat tree - parent is the rule, symbol-ized, and children, if they exist, are
			// the child states, symbol-ized
			int[] children = drs.getLeafChildren(dr);
			// virtual node or real node?
			Symbol rhssym = null;
			double weight = semiring.ONE();
			if (dr.isVirtual()) {
				rhssym = SymbolFactory.getSymbol("V"+(nextVirt++));
			}
			else {
				rhssym = SymbolFactory.getSymbol("R"+dr.getRuleIndex());
				weight = dr.getWeight();
			}
			TreeItem rhs = null;
			if (children.length == 0)
				rhs = new TreeItem(rhssym);
			else {
				TreeItem[] treechild = new TreeItem[children.length];
				for (int i = 0; i < treechild.length; i++)
					treechild[i] = new TreeItem(SymbolFactory.getSymbol("S"+children[i]));
				rhs = new TreeItem(treechild.length, rhssym, treechild, semiring);
			}
			RTGRule r = new RTGRule(this, lhs, rhs, weight, semiring);
			rules.add(r);
		}
		initialize();
	}
	// for conversion of CFGRuleSet
	// lhs of a rule becomes parent of rhs. states are re-named to distinguish from nonterminals
	// epsilon rules not convertable
	public RTGRuleSet(CFGRuleSet cfg) throws ImproperConversionException {
		// we need an alias for current states and should check that such an alias doesn't
		// already exist
		// what about checking the alias doesn't conflict with current symbols?
		boolean debug = false;
		HashMap map = new HashMap();
		HashSet aliases = new HashSet();
		Iterator stit = cfg.getStates().iterator();
		nextRuleIndex = 0;
		while (stit.hasNext()) {
			Symbol currState = (Symbol)stit.next();
			if (map.containsKey(currState))
				continue;
			Symbol currAlias = SymbolFactory.getSymbol("q_"+currState.toString());
			while (aliases.contains(currAlias))
				currAlias = SymbolFactory.getSymbol("q_"+currAlias.toString());
			if (debug) Debug.debug(debug, "Creating alias "+currAlias.toString()+" for "+currState.toString());
			map.put(currState, currAlias);
			aliases.add(currAlias);
		}
		// now we can create an RTGRuleSet with the converted start state and conversions
		// of each rule
		startState = (Symbol)map.get(cfg.getStartState());
		semiring = cfg.getSemiring();
		rules = new ArrayList<Rule>();
		Iterator rit = cfg.getRules().iterator();
		while (rit.hasNext()) {
			CFGRule currRule = (CFGRule)rit.next();
			if (debug) Debug.debug(debug, "Converting "+currRule.toString()+" to cfg");
			try {
				RTGRule newrule = new RTGRule(this, currRule.getLHS(), currRule, map);
				if (debug) Debug.debug(debug, "Converted to "+newrule.toString());
				rules.add(newrule);
			}
			catch (ImproperConversionException e) {
				throw new ImproperConversionException("Could not add converted rule after "+rules.size()+" to RTGRuleSet: "+e.getMessage());
			}
		}
		initialize();
	}


	// for projection of the domain of transducer
	// lhs of rule becomes rhs of rtg rule, with states mapped appropriately
	// throw improperconversion for cases we can't handle
	public RTGRuleSet(TransducerRuleSet trs) throws ImproperConversionException {
		boolean debug = false;
		//	if (debug) Debug.debug(debug, "Domain projection of "+trs);
		startState = trs.getStartState();
		semiring = trs.getSemiring();
		// keep them unique to begin
		HashSet<RTGRule> temprules = new HashSet<RTGRule>();
		// states to process
		Vector<HashSet<Symbol>> todolist = new Vector<HashSet<Symbol>>();
		// states processed
		HashSet<HashSet<Symbol>> doneset = new HashSet<HashSet<Symbol>>();
		// multi state - symbol mapping
		HashMap<HashSet<Symbol>, Symbol> multimap = new HashMap<HashSet<Symbol>, Symbol>();
		nextRuleIndex = 0;

		int counter = 0;
		Date readTime = new Date();
		int rulecounter = 0;
		boolean doStarRules = false;
		// first do all the original states and check if star rules are needed
		for (Symbol currstate : trs.getStates()) {
			// need to wrap it in a hash set for my implementation	    
			HashSet<Symbol> currset = new HashSet<Symbol>();
			currset.add(currstate);

			counter++;
			if (debug && counter % 1000 == 0) {
				Date pause = new Date();
				long lapse = pause.getTime() - readTime.getTime();
				readTime = pause;
				Debug.debug(debug, "Converting state "+counter+"; "+lapse);
			}

			// get relevant rules and combinable states
			// outer vector of nextStates organized to line up with relevant rules
			// inner vector of next states organized to line up with **variable order**
			Vector<Vector<HashSet<Symbol>>> nextStates = new Vector<Vector<HashSet<Symbol>>>();
			Vector<Vector<TransducerRule>> relevantRules = trs.getRelevantRules(currset, nextStates);
			//	    if (debug) Debug.debug(debug, "Retrieved "+relevantRules.size()+" relevant rules");
			//	    if (debug) Debug.debug(debug, "Retrieved "+relevantRules.size()+" relevant rules: "+relevantRules);
			// put all unused state sets with multiple symbols into the todo list.
			for (Vector<HashSet<Symbol>> rulevec : nextStates) {
				for (HashSet<Symbol> newStates : rulevec) {
					if (newStates.size() < 2) continue;
					if (!doneset.contains(newStates) && !todolist.contains(newStates)) {
						if (debug) Debug.debug(debug, "Adding state set "+newStates+" to todo list:");
						todolist.add(newStates);
						Symbol mapval = SymbolFactory.getStateSymbol(newStates);
						if (debug) Debug.debug(debug, "Mapping "+newStates+" to "+mapval);
						multimap.put(newStates, mapval);
					}
				}
			}
			//  combine rules as appropriate
			for (int i = 0; i < relevantRules.size(); i++) {
				Vector<TransducerRule> ruleSet = relevantRules.get(i);
				Vector<HashSet<Symbol>> varSet = nextStates.get(i);
				try {
					rulecounter++;
					if (debug && rulecounter % 10000 == 0) {
						Debug.debug(debug, "Adding rule "+rulecounter);
					}
					// combine rules, using the variable combinations and start state specified	
					RTGRule newrule = new RTGRule(this, currset, ruleSet, varSet, multimap);
					if (!doStarRules) {
						for (int childItem = 0; childItem < newrule.getRHS().getNumChildren(); childItem++) {
							Symbol childLabel = ((TreeItem)newrule.getRHS()).getChild(childItem).getLabel();
							if (debug) Debug.debug(debug, "Checking "+childLabel+" for star");
							if (childLabel.equals(Symbol.getStar())) {
								doStarRules = true;
								break;
							}
						}
					}
					if (debug) Debug.debug(debug, "Converted "+ruleSet+" and "+varSet+"  to "+newrule);
					temprules.add(newrule);
				}
				catch (ImproperConversionException e) {
					throw new ImproperConversionException("Could not add converted rule after "+temprules.size()+" to RTGRuleSet: "+e.getMessage());
				}
			}
		}
		// now do star rules if needed
		if (doStarRules) {
			if (debug) Debug.debug(debug, "Doing star rules");
			temprules.addAll(trs.generateStarRules(this));
			if (debug) Debug.debug(debug, "Current rules are "+temprules);

		}

		// now do any states that were put into the todolist
		while (todolist.size() > 0) {
			HashSet<Symbol> currset = todolist.remove(0);
			counter++;
			doneset.add(currset);
			if (debug) Debug.debug(debug, "converting rules for the states of "+currset);
			if (debug && counter % 1000 == 0) {
				Date pause = new Date();
				long lapse = pause.getTime() - readTime.getTime();
				readTime = pause;
				Debug.debug(debug, "Converting state "+counter+"; "+lapse);
			}

			// if the set has star and other things, add the rule:
			// set -> other things
			// set -> star
			// and add "other things" to the list if it isn't already there

			if (currset.contains(Symbol.getStar())) {
				if (currset.size() == 1) {
					if (debug) Debug.debug(debug, "Skipping creation of star rules");
					continue;
				}
				Symbol setval = multimap.get(currset);
				currset.remove(Symbol.getStar());
				Symbol otherval = null;
				if (currset.size() > 1) {
					otherval = SymbolFactory.getStateSymbol(currset);
					if (!multimap.containsKey(currset))
						multimap.put(currset, otherval);
				}
				else {
					Symbol[] ar = new Symbol[1];
					otherval = currset.toArray(ar)[0];
				}
				// build the rules 
				// don't build to star anymore
//				if (debug) Debug.debug(debug, "Adding state-to-state transitions from "+setval+" to star and "+otherval);
//				temprules.add(new RTGRule(this, setval, Symbol.getStar(), semiring.ONE(), semiring));
				temprules.add(new RTGRule(this, setval, otherval, semiring.ONE(), semiring));		
				if (currset.size() > 1 && !todolist.contains(currset) && !doneset.contains(currset)) {
					if (debug) Debug.debug(debug, "Adding state set "+currset+" to todo list:");
					todolist.add(currset);
				}
				continue;
			}

			// get relevant rules and combinable states
			// outer vector of nextStates organized to line up with relevant rules
			// inner vector of next states organized to line up with **variable order**
			Vector<Vector<HashSet<Symbol>>> nextStates = new Vector<Vector<HashSet<Symbol>>>();
			Vector<Vector<TransducerRule>> relevantRules = trs.getRelevantRules(currset, nextStates);
			//	    if (debug) Debug.debug(debug, "Retrieved "+relevantRules.size()+" relevant rules");
			//	    if (debug) Debug.debug(debug, "Retrieved "+relevantRules.size()+" relevant rules: "+relevantRules);
			// put all unused state sets into the todo list.
			for (Vector<HashSet<Symbol>> rulevec : nextStates) {
				for (HashSet<Symbol> newStates : rulevec) {
					if (newStates.size() < 2) continue;
					if (!doneset.contains(newStates) && !todolist.contains(newStates)) {
						if (debug) Debug.debug(debug, "Adding state set "+newStates+" to todo list:");
						todolist.add(newStates);
						if (newStates.size() > 1) {
							Symbol mapval = SymbolFactory.getStateSymbol(newStates);
							if (debug) Debug.debug(debug, "Mapping "+newStates+" to "+mapval);
							multimap.put(newStates, mapval);
						}
					}
				}
			}
			//  combine rules as appropriate
			for (int i = 0; i < relevantRules.size(); i++) {
				Vector<TransducerRule> ruleSet = relevantRules.get(i);
				Vector<HashSet<Symbol>> varSet = nextStates.get(i);
				try {
					// combine rules, using the variable combinations and start state specified	
					RTGRule newrule = new RTGRule(this, currset, ruleSet, varSet, multimap);
					if (debug) Debug.debug(debug, "Converted "+ruleSet+"  to "+newrule);
					temprules.add(newrule);
				}
				catch (ImproperConversionException e) {
					throw new ImproperConversionException("Could not add converted rule after "+temprules.size()+" to RTGRuleSet: "+e.getMessage());
				}
			}
		}
		rules = new ArrayList<Rule>(temprules);
		if (debug) Debug.debug(debug, "Done creating new rules");
		// set up the states and rules
		initialize();
		if (debug) Debug.debug(debug, "Done initializing");
		// make sure the new states are in the state set
		states.addAll(multimap.values());
		if (debug) Debug.debug(debug, "Done adding new states");
		//	if (debug) Debug.debug(debug, "States before reinitialization are "+states);
			if (debug) Debug.debug(debug, "rtg before pruning is "+toString());
		// trim the rtg 
		pruneUseless();
		if (debug) Debug.debug(debug, "Done pruning");
		// reset the tables
		initialize();
		if (debug) Debug.debug(debug, "Done initializing");
		//	normalizeWeights();
		//	if (debug) Debug.debug(debug, "Done normalizing weights");
		//	if (debug) Debug.debug(debug, "States after reinitialization are "+states);
		//	if (debug) Debug.debug(debug, "rtg after pruning and reinit is "+toString());
	}

	// for projection of the range of a tree transducer
	// cannot perform if it's copying
	// state of rule becomes lhs of rtg rule.
	// rhs of rule becomes rhs of rtg rule with states instead of variables
	public RTGRuleSet(TreeTransducerRuleSet trs) throws ImproperConversionException {
		boolean debug = false;
		if (debug) Debug.debug(debug, "Range projection");
		if (trs.isCopying())
			throw new ImproperConversionException("Can't get range RTG of a copying transducer");
		startState = trs.getStartState();
		semiring = trs.getSemiring();
		rules = new ArrayList<Rule>();
		nextRuleIndex = 0;
		Iterator it = trs.getRules().iterator();
		while (it.hasNext()) {
			TreeTransducerRule tr = (TreeTransducerRule)it.next();
			RTGRule newrule = new RTGRule(this, tr);
			if (debug) Debug.debug(debug, "Converted "+tr+" to "+newrule);
			rules.add(newrule);
		}
		initialize();
	}


	// forward application of a tree through a TreeTransducerRuleSet. Originally in its own class
	// the tree, then let application return tree-state pairs. continue while there's some left to do

	// "normal mode". Used in batch. Eps rules allowed here.
//	public RTGRuleSet(TreeItem t, TreeTransducerRuleSet trs) {
//		this(t, trs.getStartState(), trs, new HashSet<Symbol>(), new HashMap<Symbol, StateTreePair>(), new HashMap<StateTreePair, Symbol>(),  true);
//	}

	// allow any state to start the application. For composition. Eps rules not allowed
	public RTGRuleSet(TreeItem t, 
					  Symbol state, 
					  TreeTransducerRuleSet trs, 
					  Set<Symbol> varSymSet, 
					  HashMap<Symbol, StateTreePair> varSymSTP, 
					  HashMap<StateTreePair, Symbol> STPvarSym) {
		this(t, state, trs, varSymSet, varSymSTP, STPvarSym,  false);
	}


	// choose state to start application. determine whether to use eps rules
	// initialization done at the same time
	public RTGRuleSet(TreeItem t, 
					  Symbol state, 
					  TransducerRuleSet trs, 
					  Set<Symbol> varSymSet, 
					  HashMap<Symbol, StateTreePair> varSymSTP, 
					  HashMap<StateTreePair, Symbol> STPvarSym, 
					  boolean epsAllowed) {
		leafChildren = new Hashtable<Rule, Vector<Symbol>>();
		states = new HashSet();
		rulesByLHS = new Hashtable();
		rulesByIndex = new Hashtable<Integer, Rule>();
		rulesByTie = new TIntObjectHashMap();
		Debug.prettyDebug("Old style forward app");
		boolean debug = false;
		semiring = trs.getSemiring();
		nextRuleIndex = 0;
		if (debug) Debug.debug(debug, "Applying "+t+" through transducer starting with "+state);
		StateTreePair startStatePair = SymbolFactory.getStateTreePair(state, t, 1, t.getLeaves().length);
		startState = startStatePair.getSymbol();
		states.add(startState);
		rules = new ArrayList<Rule>();

		HashSet<Symbol> usedStates = new HashSet<Symbol>();
		Vector pendingStates = new Vector();
		usedStates.add(startStatePair);
		pendingStates.add(startStatePair);
		while (!pendingStates.isEmpty()) {
			StateTreePair stp = (StateTreePair)pendingStates.remove(0);
			ArrayList<StateTreePair> possibles = new ArrayList<StateTreePair>();
			if (debug) Debug.debug(debug, "Adding all of "+stp);
			for (Rule r : trs.getForwardGrammarRules(this, stp, possibles, varSymSet, varSymSTP, STPvarSym, epsAllowed)) {
				if (debug) Debug.debug(debug, "Adding "+r);
				// bless with new index
				r.setIndex(getNextRuleIndex());
				rules.add(r);
				if (!rulesByLHS.containsKey(r.getLHS()))
					rulesByLHS.put(r.getLHS(), new ArrayList<Rule>());
				rulesByLHS.get(r.getLHS()).add(r);
				rulesByIndex.put(r.getIndex(), r);
				if (r.getTie() > 0) {
					if (!rulesByTie.containsKey(r.getTie()))
						rulesByTie.put(r.getTie(), new ArrayList<Rule>());
					((ArrayList<Rule>)rulesByTie.get(r.getTie())).add(r);
				}
			}
			for (StateTreePair poss : possibles) {
				if (!usedStates.contains(poss)) {
					usedStates.add(poss);
					states.add(poss);
					//Debug.debug(true, "Going to check "+poss.toString());
					pendingStates.add(poss);
				}
			}
		}

		//	    Debug.debug(true, "Done adding");
		// 	    Debug.debug(true, stateSet.size()+" states in rtg");
		// 	    Debug.debug(true, ruleSet.size()+" rules in rtg");


		if (debug) Debug.debug(debug, "ForwardApplication: before pruning: "+toString());
		pruneUseless();
		if (debug) Debug.debug(debug, "ForwardApplication: after pruning: "+toString());
		initialize();
		if (debug) Debug.debug(debug, "ForwardApplication: after initializing: "+toString());
	}


	// forward application without external maps
	
	// start state only
	public RTGRuleSet(TreeItem t, TreeTransducerRuleSet trs) {
		this(t, trs.getStartState(), trs, true);
	}
	// TODO: allow general one...
	
	// allow any state to start the application. For composition. Eps rules not allowed
	public RTGRuleSet(TreeItem t, 
					  Symbol state, 
					  TreeTransducerRuleSet trs) {
		this(t, state, trs, false);
	}
	
	// choose state to start application. determine whether to use eps rules
	// initialization done at the same time
	public RTGRuleSet(TreeItem t, 
					  Symbol state, 
					  TransducerRuleSet trs, 
					  boolean epsAllowed) {
//		Debug.prettyDebug("New style forward app");
		leafChildren = new Hashtable<Rule, Vector<Symbol>>();
		states = new HashSet<Symbol>();
		rulesByLHS = new Hashtable<Symbol, ArrayList<Rule>>();
		rulesByIndex = new Hashtable<Integer, Rule>();
		rulesByTie = new TIntObjectHashMap();
		boolean debug = false;
		semiring = trs.getSemiring();
		nextRuleIndex = 0;
		if (debug) Debug.debug(debug, "Applying "+t+" through transducer starting with "+state);
		StateTreePair startStatePair = SymbolFactory.getStateTreePair(state, t, 1, t.getLeaves().length);
		startState = startStatePair.getSymbol();
		states.add(startState);
		rules = new ArrayList<Rule>();

		HashSet<Symbol> usedStates = new HashSet<Symbol>();
		Vector<StateTreePair> pendingStates = new Vector<StateTreePair>();
		usedStates.add(startStatePair);
		pendingStates.add(startStatePair);
		while (!pendingStates.isEmpty()) {
			StateTreePair stp = pendingStates.remove(0);
			ArrayList<StateTreePair> possibles = new ArrayList<StateTreePair>();
			if (debug) Debug.debug(debug, "Adding all of "+stp);
			for (Rule r : trs.getForwardGrammarRules(this, stp, possibles, epsAllowed)) {
				if (debug) Debug.debug(debug, "Adding "+r);
				// bless with new index
				r.setIndex(getNextRuleIndex());
				rules.add(r);
				if (!rulesByLHS.containsKey(r.getLHS()))
					rulesByLHS.put(r.getLHS(), new ArrayList<Rule>());
				rulesByLHS.get(r.getLHS()).add(r);
				rulesByIndex.put(r.getIndex(), r);
				if (r.getTie() > 0) {
					if (!rulesByTie.containsKey(r.getTie()))
						rulesByTie.put(r.getTie(), new ArrayList<Rule>());
					((ArrayList<Rule>)rulesByTie.get(r.getTie())).add(r);
				}
			}
			for (StateTreePair poss : possibles) {
				if (!usedStates.contains(poss)) {
					usedStates.add(poss);
					states.add(poss);
					//Debug.debug(true, "Going to check "+poss.toString());
					pendingStates.add(poss);
				}
			}
		}

		//	    Debug.debug(true, "Done adding");
		// 	    Debug.debug(true, stateSet.size()+" states in rtg");
		// 	    Debug.debug(true, ruleSet.size()+" rules in rtg");


		if (debug) Debug.debug(debug, "ForwardApplication: before pruning: "+rules.size());
		pruneUseless();
		if (debug) Debug.debug(debug, "ForwardApplication: after pruning: "+rules.size());
		initialize();
		if (debug) Debug.debug(debug, "ForwardApplication: after initializing: "+rules.size());
	}

	

	// backward application of a string through a StringTransducerRuleSet. This is essentially a parser
	// this can be extended for a non-loopy CFG. I don't think it can be done for a general CFG...but I could be wrong.
	
	// "normal mode". Used in batch. Eps rules allowed here.
	public RTGRuleSet(TreeTransducerRuleSet trs, TreeItem t) {
		this(trs, trs.getStartState(), t, new HashSet<Symbol>(), new HashMap<Symbol, StateTreePair>(), new HashMap<StateTreePair, Symbol>(),  true);
	}

	// allow any state to start the backward application. For composition. Eps rules not allowed
	public RTGRuleSet(TreeTransducerRuleSet trs,
					  Symbol state, 
					  TreeItem t, 
					  Set<Symbol> varSymSet, 
					  HashMap<Symbol, StateTreePair> varSymSTP, 
					  HashMap<StateTreePair, Symbol> STPvarSym) {
		this(trs, state, t, varSymSet, varSymSTP, STPvarSym,  false);
	}


	// choose state to start backward application. determine whether to use eps rules
	// initialization done at the same time
	public RTGRuleSet(TreeTransducerRuleSet trs, 
					  Symbol state, 
					  TreeItem t, 
					  Set<Symbol> varSymSet, 
					  HashMap<Symbol, StateTreePair> varSymSTP, 
					  HashMap<StateTreePair, Symbol> STPvarSym, 
					  boolean epsAllowed) {
		leafChildren = new Hashtable<Rule, Vector<Symbol>>();
		states = new HashSet();
		rulesByLHS = new Hashtable();
		rulesByIndex = new Hashtable<Integer, Rule>();
		rulesByTie = new TIntObjectHashMap();
		boolean debug = false;
		semiring = trs.getSemiring();
		nextRuleIndex = 0;
		if (debug) Debug.debug(debug, "Backward Applying "+t+" through transducer starting with "+state);
		StateTreePair startStatePair = SymbolFactory.getStateTreePair(state, t, 1, t.getLeaves().length);
		startState = startStatePair.getSymbol();
		states.add(startState);
		rules = new ArrayList<Rule>();

		HashSet<Symbol> usedStates = new HashSet<Symbol>();
		Vector pendingStates = new Vector();
		usedStates.add(startStatePair);
		pendingStates.add(startStatePair);
		while (!pendingStates.isEmpty()) {
			StateTreePair stp = (StateTreePair)pendingStates.remove(0);
			ArrayList<StateTreePair> possibles = new ArrayList<StateTreePair>();
			if (debug) Debug.debug(debug, "Adding all of "+stp.toInternalString());
			for (RTGRule r : trs.getBackwardGrammarRules(this, stp, possibles, varSymSet, varSymSTP, STPvarSym, epsAllowed)) {
				if (debug) Debug.debug(debug, "Adding "+r);
				// bless with new index
				r.setIndex(getNextRuleIndex());
				rules.add(r);
				if (!rulesByLHS.containsKey(r.getLHS()))
					rulesByLHS.put(r.getLHS(), new ArrayList<Rule>());
				rulesByLHS.get(r.getLHS()).add(r);
				rulesByIndex.put(r.getIndex(), r);
				if (r.getTie() > 0) {
					if (!rulesByTie.containsKey(r.getTie()))
						rulesByTie.put(r.getTie(), new ArrayList<Rule>());
					((ArrayList<Rule>)rulesByTie.get(r.getTie())).add(r);
				}
			}
			for (StateTreePair poss : possibles) {
				if (!usedStates.contains(poss)) {
					usedStates.add(poss);
					states.add(poss);
					//Debug.debug(true, "Going to check "+poss.toString());
					pendingStates.add(poss);
				}
			}
		}

		//	    Debug.debug(true, "Done adding");
		// 	    Debug.debug(true, stateSet.size()+" states in rtg");
		// 	    Debug.debug(true, ruleSet.size()+" rules in rtg");


		if (debug) Debug.debug(debug, "BackwardApplication: before pruning: "+toString());
		pruneUseless();
		if (debug) Debug.debug(debug, "BackwardApplication: after pruning: "+toString());
		initialize();
		if (debug) Debug.debug(debug, "BackwardApplication: after initializing: "+toString());
	}


	
	// backward application without external maps
	
	// TODO: allow general one...
	
	// allow any state to start the backward application. For composition. Eps rules not allowed
	public RTGRuleSet(TreeTransducerRuleSet trs,
					  Symbol state, 
					  TreeItem t) {
		this(trs, state, t, false);
	}


	// choose state to start backward application. determine whether to use eps rules
	// initialization done at the same time
	public RTGRuleSet(TreeTransducerRuleSet trs, 
					  Symbol state, 
					  TreeItem t, 
					  boolean epsAllowed) {
		leafChildren = new Hashtable<Rule, Vector<Symbol>>();
		states = new HashSet();
		rulesByLHS = new Hashtable();
		rulesByIndex = new Hashtable<Integer, Rule>();
		rulesByTie = new TIntObjectHashMap();
		boolean debug = false;
		semiring = trs.getSemiring();
		nextRuleIndex = 0;
		if (debug) Debug.debug(debug, "Backward Applying "+t+" through transducer starting with "+state);
		StateTreePair startStatePair = SymbolFactory.getStateTreePair(state, t, 1, t.getLeaves().length);
		startState = startStatePair.getSymbol();
		states.add(startState);
		rules = new ArrayList<Rule>();

		HashSet<Symbol> usedStates = new HashSet<Symbol>();
		Vector pendingStates = new Vector();
		usedStates.add(startStatePair);
		pendingStates.add(startStatePair);
		while (!pendingStates.isEmpty()) {
			StateTreePair stp = (StateTreePair)pendingStates.remove(0);
			ArrayList<StateTreePair> possibles = new ArrayList<StateTreePair>();
			if (debug) Debug.debug(debug, "Adding all of "+stp.toInternalString());
			for (RTGRule r : trs.getBackwardGrammarRules(this, stp, possibles, epsAllowed)) {
				if (debug) Debug.debug(debug, "Adding "+r);
				// bless with new index
				r.setIndex(getNextRuleIndex());
				rules.add(r);
				if (!rulesByLHS.containsKey(r.getLHS()))
					rulesByLHS.put(r.getLHS(), new ArrayList<Rule>());
				rulesByLHS.get(r.getLHS()).add(r);
				rulesByIndex.put(r.getIndex(), r);
				if (r.getTie() > 0) {
					if (!rulesByTie.containsKey(r.getTie()))
						rulesByTie.put(r.getTie(), new ArrayList<Rule>());
					((ArrayList<Rule>)rulesByTie.get(r.getTie())).add(r);
				}
			}
			for (StateTreePair poss : possibles) {
				if (!usedStates.contains(poss)) {
					usedStates.add(poss);
					states.add(poss);
					//Debug.debug(true, "Going to check "+poss.toString());
					pendingStates.add(poss);
				}
			}
		}

		//	    Debug.debug(true, "Done adding");
		// 	    Debug.debug(true, stateSet.size()+" states in rtg");
		// 	    Debug.debug(true, ruleSet.size()+" rules in rtg");


		if (debug) Debug.debug(debug, "BackwardApplication: before pruning: "+toString());
		pruneUseless();
		if (debug) Debug.debug(debug, "BackwardApplication: after pruning: "+toString());
		initialize();
		if (debug) Debug.debug(debug, "BackwardApplication: after initializing: "+toString());
	}

	
	
	// 
	// the real driver behind the tree constructor: given a start number, recursively build rules
	// for children. add to hash set. return the updated number
	private int fillRules(ArrayList<Rule> set, TreeItem t, int n) {
		boolean debug = false;
		if (debug) Debug.debug(debug, "filling rules from "+t+" with "+t.numChildren+" children");
		Symbol lhs = SymbolFactory.getStateSymbol("TG-"+(n++));
		TreeItem rhs = null;
		if (t.isTransducerState())
			rhs = new TreeItem(t.label, t.getHiddenVariable());
		else
			rhs = new TreeItem(t.label);
		for (int i = 0; i < t.numChildren; i++) {
			rhs.addChild(new TreeItem(SymbolFactory.getStateSymbol("TG-"+(n))));
			n = fillRules(set, (TreeItem)t.children[i], n);
		}
		if (debug) Debug.debug(debug, "Forming rule out of "+lhs+" and "+rhs);
		RTGRule r = new RTGRule(this, lhs, rhs, semiring.ONE(), semiring);
		if (debug) Debug.debug(debug, "Formed "+r+" out of "+lhs+" and "+rhs);
		set.add(r);
		return n;
	}

	// normal form: each rule should have a tree of at most height one on its rhs.
	// if tree is height one, all children are nonterminals
	// if not, extract the inside and create new states



	//     // makeEfficientNormal currently not used
	//     // a crude improvement: track rules indexed by their rhs as long as their lhs has
	//     // only one rhs (thus we also have to track them by their lhs). When creating a new rule,
	//     // if the rule already exists as a singleton, use the already existing rule rather than
	//     // creating a new rule. This should save on rules.

	//     public void makeEfficientNormal() {
	// 	if (madeNormal) {
	// 	    Debug.debug(true, "Already normal");
	// 	    return;
	// 	}

	// 	// i think it's more costly to try and delete rules violating the laws, so instead we just put them all in
	// 	// and see what happens
	// 	// index of rules by rhs
	// 	Hashtable rulesByRHS = new Hashtable();
	// 	// index of rules by lhs
	// 	Hashtable rulesByLHS = new Hashtable();
	// // 	Iterator rit = rules.iterator();
	// // 	while (rit.hasNext()) {
	// // 	    Rule r = (Rule)rit.next();
	// // 	    if (!rulesByLHS.containsKey(r.getLHS()))
	// // 		rulesByLHS.put(r.getLHS(), new HashSet());
	// // 	    ((HashSet)rulesByLHS.get(r.getLHS())).add(r);
	// // 	    if (!rulesByRHS.containsKey(r.getRHS()))
	// // 		rulesByRHS.put(r.getRHS(), new HashSet());
	// // 	    ((HashSet)rulesByRHS.get(r.getRHS())).add(r);
	// // 	}

	// 	// First, consider the extant rules. Extract from them as necessary. Add the extracted
	// 	// rules to a set
	// 	HashSet okayRules = new HashSet();
	// 	HashSet newStates = new HashSet();
	// 	HashSet newRules = new HashSet();
	// 	int autocount = 0;
	// 	Iterator rit = rules.iterator();
	// 	while (rit.hasNext()) {
	// 	    Rule r = (Rule)rit.next();
	// 	    boolean ruleWasNormal = false;
	// 	    if (r.getRHS().numChildren == 0) {
	// 		//		Debug.debug(true, r.toString()+" is okay because no kids");
	// 		okayRules.add(r);
	// 		ruleWasNormal = true;
	// 	    }
	// 	    else {
	// 		boolean passed = true;
	// 		for (int i = 0; i < r.getRHS().numChildren; i++) {
	// 		    if (r.getRHS().children[i].numChildren > 0) {
	// 			passed = false;
	// 			break;
	// 		    }
	// 		    if (!states.contains(r.getRHS().children[i].label)) {
	// 			passed = false;
	// 			break;
	// 		    }
	// 		}
	// 		if (passed) {
	// 		    //		    Debug.debug(true, r.toString()+" is okay because all kids are terms and states");
	// 		    okayRules.add(r);
	// 		    ruleWasNormal = true;
	// 		}
	// 	    }
	// 	    // add it to the rulesbylhs/rhs if it's okay
	// 	    if (ruleWasNormal) {
	// 		if (!rulesByLHS.containsKey(r.getLHS()))
	//  		rulesByLHS.put(r.getLHS(), new HashSet());
	// 		((HashSet)rulesByLHS.get(r.getLHS())).add(r);
	// 		if (!rulesByRHS.containsKey(r.getRHS()))
	// 		    rulesByRHS.put(r.getRHS(), new HashSet());
	// 		((HashSet)rulesByRHS.get(r.getRHS())).add(r);
	// 	    }
	// 	    else {

	// 		Tree t = new Tree(r.getRHS().label);
	// 		// create a normalized form of this rule by replicating every legal node
	// 		// and making a new state for each illegal node. defer processing of the
	// 		// illegal nodes for now...
	// 		for (int j = 0; j < r.getRHS().numChildren; j++) {
	// 		    if (r.getRHS().children[j].numChildren == 0 && states.contains(r.getRHS().children[j].label)) {
	// 			t.addChild(r.getRHS().children[j]);
	// 			if (states.contains(r.getRHS().children[j].label))
	// 			    newStates.add(r.getRHS().children[j].label);
	// 		    }
	// 		    // make a new state
	// 		    else {
	// 			Symbol newlabel = null;
	// 			// before creating a rule and a state, try to find the equivalent rule
	// 			if (rulesByRHS.containsKey(r.getRHS().children[j])) {
	// 			    HashSet possibleRules = (HashSet)rulesByRHS.get(r.getRHS().children[j]);
	// 			    // look for a singleton
	// 			    Iterator prit = possibleRules.iterator();
	// 			    while (prit.hasNext()) {
	// 				Rule pr = (Rule)prit.next();
	// 				if (((HashSet)rulesByLHS.get(pr.getLHS())).size() > 1) {
	// 				    //				    Debug.debug(true, "Rule "+pr.toString()+" is viable, but lhs has too many options");
	// 				    continue;
	// 				}
	// 				//				Debug.debug(true, "Using "+pr.getLHS().toString()+" to get to "+r.getRHS());
	// 				newlabel = pr.getLHS();
	// 				break;
	// 			    }
	// 			}
	// 			if (newlabel == null) {
	// 			    newlabel = SymbolFactory.getSymbol("AUTO-"+(autocount++));
	// 			    newStates.add(newlabel);
	// 			    Rule newrule = new Rule(this, newlabel, r.getRHS().children[j], semiring.ONE(), semiring);
	// 			    //			Debug.debug(true, "New possibly abnormal rule: "+newrule.toString());
	// 			    newRules.add(newrule);
	// 			}
	// 			t.addChild(new Tree(newlabel));

	// 		    }
	// 		}
	// 		// the newly normal form rule is okay
	// 		Rule newrule = new Rule(this, r.getLHS(), t, r.getWeight(), semiring);
	// 		//		Debug.debug(true, "Newly created normal rule: "+newrule.toString());
	// 		okayRules.add(newrule);
	// 		if (!rulesByLHS.containsKey(newrule.getLHS()))
	// 		    rulesByLHS.put(newrule.getLHS(), new HashSet());
	// 		((HashSet)rulesByLHS.get(newrule.getLHS())).add(newrule);
	// 		if (!rulesByRHS.containsKey(newrule.getRHS()))
	// 		    rulesByRHS.put(newrule.getRHS(), new HashSet());
	// 		((HashSet)rulesByRHS.get(newrule.getRHS())).add(newrule);
	// 	    }
	// 	}
	// 	// then consider the extracted rules until there are none left. Make them normal and add them to an additional set
	// 	// add newly extracted rules to the pending set and continue in this fashion until no new rules are
	// 	// created
	// 	while (!newRules.isEmpty()) {
	// 	    HashSet currRules = new HashSet(newRules);
	// 	    Iterator i = currRules.iterator();
	// 	    while (i.hasNext()) {
	// 		Rule r = (Rule)i.next();
	// 		newRules.remove(r);

	// 		boolean ruleWasNormal = false;
	// 		if (r.getRHS().numChildren == 0) {
	// 		    //		    Debug.debug(true, r.toString()+" is okay because no kids (2nd pass)");
	// 		    okayRules.add(r);
	// 		    ruleWasNormal = true;
	// 		}
	// 		else {
	// 		    boolean passed = true;
	// 		    for (int j = 0; j < r.getRHS().numChildren; j++) {
	// 			if (r.getRHS().children[j].numChildren > 0) {
	// 			    passed = false;
	// 			    break;
	// 			}
	// 			if (!states.contains(r.getRHS().children[j].label) &&
	// 			    !newStates.contains(r.getRHS().children[j].label) ) {
	// 			    passed = false;
	// 			    break;
	// 			}
	// 		    }
	// 		    if (passed) {
	// 			//			Debug.debug(true, r.toString()+" is okay because all kids are terms and states (or new states)");
	// 			okayRules.add(r);
	// 			ruleWasNormal = true;
	// 		    }
	// 		}
	// 		// add it to the rulesbylhs/rhs if it's okay
	// 		if (ruleWasNormal) {
	// 		    if (!rulesByLHS.containsKey(r.getLHS()))
	// 			rulesByLHS.put(r.getLHS(), new HashSet());
	// 		    ((HashSet)rulesByLHS.get(r.getLHS())).add(r);
	// 		    if (!rulesByRHS.containsKey(r.getRHS()))
	// 			rulesByRHS.put(r.getRHS(), new HashSet());
	// 		    ((HashSet)rulesByRHS.get(r.getRHS())).add(r);
	// 		}
	// 		else {
	// 		    Tree t = new Tree(r.getRHS().label);
	// 		    for (int j = 0; j < r.getRHS().numChildren; j++) {
	// 			if (r.getRHS().children[j].numChildren == 0 && 
	// 			    (states.contains(r.getRHS().children[j].label) ||
	// 			     newStates.contains(r.getRHS().children[j].label)))
	// 			    t.addChild(r.getRHS().children[j]);
	// 			// make a new state
	// 			else {
	// 			    Symbol newlabel = null;
	// 			    // before creating a rule and a state, try to find the equivalent rule
	// 			    if (rulesByRHS.containsKey(r.getRHS().children[j])) {
	// 				HashSet possibleRules = (HashSet)rulesByRHS.get(r.getRHS().children[j]);
	// 				// look for a singleton
	// 				Iterator prit = possibleRules.iterator();
	// 				while (prit.hasNext()) {
	// 				    Rule pr = (Rule)prit.next();
	// 				    if (((HashSet)rulesByLHS.get(pr.getLHS())).size() > 1) {
	// 					//					Debug.debug(true, "Rule "+pr.toString()+" is viable, but lhs has too many options");
	// 					continue;
	// 				    }
	// 				    //				    Debug.debug(true, "Using "+pr.getLHS().toString()+" to get to "+r.getRHS());
	// 				    newlabel = pr.getLHS();
	// 				    break;
	// 				}
	// 			    }
	// 			    if (newlabel == null) {
	// 				newlabel = SymbolFactory.getSymbol("AUTO-"+(autocount++));
	// 				newStates.add(newlabel);
	// 				Rule newrule = new Rule(this, newlabel, r.getRHS().children[j], semiring.ONE(), semiring);
	// 				//			    Debug.debug(true, "New possibly abnormal rule: "+newrule.toString());
	// 				newRules.add(newrule);
	// 			    }
	// 			    t.addChild(new Tree(newlabel));
	// 			}
	// 		    }
	// 		    Rule newrule = new Rule(this, r.getLHS(), t, r.getWeight(), semiring);
	// 		    //		    Debug.debug(true, "Newly created normal rule: "+newrule.toString());
	// 		    okayRules.add(newrule);
	// 		    if (!rulesByLHS.containsKey(newrule.getLHS()))
	// 			rulesByLHS.put(newrule.getLHS(), new HashSet());
	// 		    ((HashSet)rulesByLHS.get(newrule.getLHS())).add(newrule);
	// 		    if (!rulesByRHS.containsKey(newrule.getRHS()))
	// 			rulesByRHS.put(newrule.getRHS(), new HashSet());
	// 		    ((HashSet)rulesByRHS.get(newrule.getRHS())).add(newrule);
	// 		}
	// 	    }
	// 	}
	// 	rules = okayRules;
	// 	initialize();
	// 	madeNormal = true;
	//     }


	// old version of makeNormal for comparsion

	public void makeNormal() {
		if (madeNormal) {
			//	    Debug.debug(true, "Already normal");
			return;
		}

		// First, consider the extant rules. Extract from them as necessary. Add the extracted
		// rules to a set
		ArrayList<Rule> okayRules = new ArrayList<Rule>();
		HashSet newStates = new HashSet();
		ArrayList<Rule> newRules = new ArrayList<Rule>();
//		int autocount = 0;
		boolean debug = false;
		if (debug) Debug.debug(debug, "Processing initial rule set ("+rules.size()+" rules)");
		while (!rules.isEmpty()) {
			RTGRule r = (RTGRule)rules.remove(0);
			boolean ruleWasNormal = false;
			if (r.getRHS().numChildren == 0) {
				//		Debug.debug(true, r.toString()+" is okay because no kids");
				okayRules.add(r);
				ruleWasNormal = true;
			}
			else {
				boolean passed = true;
				for (int i = 0; i < r.getRHS().numChildren; i++) {
					if (r.getRHS().children[i].numChildren > 0) {
						passed = false;
						break;
					}
					if (!states.contains(r.getRHS().children[i].label)) {
						passed = false;
						break;
					}
				}
				if (passed) {
					//		    Debug.debug(true, r.toString()+" is okay because all kids are terms and states");
					okayRules.add(r);
					ruleWasNormal = true;
				}
			}
			if (!ruleWasNormal) {
				TreeItem t = new TreeItem(r.getRHS().label);
				// create a normalized form of this rule by replicating every legal node
				// and making a new state for each illegal node. defer processing of the
				// illegal nodes for now...
				for (int j = 0; j < r.getRHS().numChildren; j++) {
					if (r.getRHS().children[j].numChildren == 0 && states.contains(r.getRHS().children[j].label)) {
						t.addChild((TreeItem)r.getRHS().children[j]);
						if (states.contains(r.getRHS().children[j].label))
							newStates.add(r.getRHS().children[j].label);
					}
					// make a new state
					else {
						Symbol newlabel = SymbolFactory.getStateSymbol();
						t.addChild(new TreeItem(newlabel));
						newStates.add(newlabel);
						RTGRule newrule = new RTGRule(this, newlabel, (TreeItem)r.getRHS().children[j], semiring.ONE(), semiring);
						//			Debug.debug(true, "New possibly abnormal rule: "+newrule.toString());
						newRules.add(newrule);
					}
				}
				// the newly normal form rule is okay
				RTGRule newrule = new RTGRule(this, r.getLHS(), t, r.getWeight(), semiring);
				//		Debug.debug(true, "Newly created normal rule: "+newrule.toString());
				okayRules.add(newrule);

			}
		}
		if (debug) Debug.debug(debug, "Approved "+okayRules.size()+" so far");

		// then consider the extracted rules until there are none left. Make them normal and add them to an additional set
		// add newly extracted rules to the pending set and continue in this fashion until no new rules are
		// created
		while (!newRules.isEmpty()) {
			if (debug) Debug.debug(debug, "Processing remainder rule set ("+newRules.size()+" rules)");

			ArrayList<Rule> currRules = new ArrayList<Rule>(newRules);
			newRules.clear();
			while (!currRules.isEmpty()) {
				RTGRule r = (RTGRule)currRules.remove(0);
				

				boolean ruleWasNormal = false;
				if (r.getRHS().numChildren == 0) {
					//		    Debug.debug(true, r.toString()+" is okay because no kids (2nd pass)");
					okayRules.add(r);
					ruleWasNormal = true;
				}
				else {
					boolean passed = true;
					for (int j = 0; j < r.getRHS().numChildren; j++) {
						if (r.getRHS().children[j].numChildren > 0) {
							passed = false;
							break;
						}
						if (!states.contains(r.getRHS().children[j].label) &&
								!newStates.contains(r.getRHS().children[j].label) ) {
							passed = false;
							break;
						}
					}
					if (passed) {
						//			Debug.debug(true, r.toString()+" is okay because all kids are terms and states (or new states)");
						okayRules.add(r);
						ruleWasNormal = true;
					}
				}
				if (!ruleWasNormal) {
					TreeItem t = new TreeItem(r.getRHS().label);
					for (int j = 0; j < r.getRHS().numChildren; j++) {
						if (r.getRHS().children[j].numChildren == 0 && 
								(states.contains(r.getRHS().children[j].label) ||
										newStates.contains(r.getRHS().children[j].label)))
							t.addChild((TreeItem)r.getRHS().children[j]);
						// make a new state
						else {
							Symbol newlabel = SymbolFactory.getStateSymbol();
							t.addChild(new TreeItem(newlabel));
							newStates.add(newlabel);
							RTGRule newrule = new RTGRule(this, newlabel, (TreeItem)r.getRHS().children[j], semiring.ONE(), semiring);
							//			    Debug.debug(true, "New possibly abnormal rule: "+newrule.toString());
							newRules.add(newrule);
						}
					}
					RTGRule newrule = new RTGRule(this, r.getLHS(), t, r.getWeight(), semiring);
					//		    Debug.debug(true, "Newly created normal rule: "+newrule.toString());
					okayRules.add(newrule);
				}
			}
			if (debug) Debug.debug(debug, "Approved "+okayRules.size()+" so far");
		}
		rules = okayRules;
		initialize();
		madeNormal = true;
	}





	// a test of grammar validity: this function returns true if all rules 
	//can eventually be reached by the start symbol
	// if this is not true, it's a good sign that something is wrong with this grammar
	public boolean isAllReachable() {
		HashSet reached = new HashSet();
		HashSet unreached = new HashSet();
		reached.add(startState);
		// phase 1: add each lhs for each rule to unreached unless it is in reached. 
		// This will make unreached as large as it can be
		Iterator rit = rules.iterator();
		while (rit.hasNext()) {
			RTGRule r = (RTGRule)rit.next();	
			if (!reached.contains(r.getLHS()))
				unreached.add(r.getLHS());
		}
		int unreachedSize = unreached.size();
		// phase 2: for each rule, if lhs is in reached, remove all children from rhs and add to lhs
		// continue this while unreachedSize > 0 and while we make steady progress
		while (unreachedSize > 0) {
			rit = rules.iterator();
			while (rit.hasNext()) {
				RTGRule r = (RTGRule)rit.next();	
				if (reached.contains(r.getLHS())) {
					Item [] children = ((TreeItem)r.getRHS()).getItemLeaves();
					//		    Debug.debug(true, "Before: Unreached: "+unreached.size()+", reached: "+reached.size());
					for (int j = 0; j < children.length; j++) {
						//			Debug.debug(true, "Processing "+children[j].label);
						unreached.remove(children[j].label);
						reached.add(children[j].label);
						if (unreached.size() == 0)
							break;
					}
					//		    Debug.debug(true, "After: Unreached: "+unreached.size()+", reached: "+reached.size());
				}
				if (unreached.size() == 0)
					break;
			}
			if (unreached.size() >= unreachedSize)
				break;
			unreachedSize = unreached.size();
		}
		if (unreachedSize > 0) {
			Debug.debug(true, "Warning: some grammar members are unreachable: ");
			Iterator it = unreached.iterator();
			while (it.hasNext()) {
				Symbol ur = (Symbol)it.next();
				Debug.debug(true, "\t"+ur.toString());
			}
		}
		return (unreachedSize == 0);
	}

	// tarjan's algorithm for strongly connected components. If it seems good, remove isFinite and makeFinite below
	// breakscc = true if strongly connected components should be eliminated (by removing links between them).
	// algorithm from http://en.wikipedia.org/wiki/Tarjan%27s_strongly_connected_components_algorithm
	class TarjanVert {
		int index;
		int lowlink;
		Symbol state;
		// technically a vertex is connected to itself. we need explicit connection
		boolean selfloop;
		public TarjanVert(Symbol s) { index=lowlink=-1; state=s; selfloop=false;} 

		// equality stuff -- only symbol counts
		private Hash hsh=null;
		public int hashCode() { 
			if (hsh == null) {
				setHashCode();
			}
			return hsh.bag(Integer.MAX_VALUE);
		}

		private void setHashCode() {
			hsh = new Hash(state.getHash());
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
			TarjanVert r = (TarjanVert)o;
			if (state != r.state)
				return false;
			return true;
		}
		public String toString() { return state+":"+index+":"+lowlink;}
	}
	// global var needed for isFiniteTarjan
	private int tarjanIndex = 0;
	// global stack needed for isFiniteTarjan
	private Stack<TarjanVert> tarjanStack;
	// track which states have been created
	private Hashtable<Symbol, TarjanVert> tarjanSeen = new Hashtable<Symbol, TarjanVert>();
	// includeproducers=true means finiteness includes cycles that always produce some symbol
	boolean isFinite(boolean includeproducers) {
		boolean debug = false;
		if (rules.size() < 1 || states.size() < 1)
			return true;
		tarjanStack= new Stack<TarjanVert>();
		tarjanSeen = new Hashtable<Symbol, TarjanVert>();
		tarjanIndex = 0;
		TarjanVert init = new TarjanVert(startState);
		tarjanSeen.put(startState, init);
		return tarjan(includeproducers, init);
	}
	
	// internal recursive tarjan scc finding operator
	private boolean tarjan(boolean includeproducers, TarjanVert v) {
		boolean debug = false;
		v.index = tarjanIndex;
		v.lowlink = tarjanIndex;
		if (debug) Debug.debug(debug, "Operating on "+v.state+"; setting index and lowlink to "+v.index);
		tarjanIndex++;
		// if producers are okay, then recurse down on producer rules before inserting self
		if (includeproducers) {
			for (Rule abstractr : getRulesOfType(v.state)) {
				RTGRule r = (RTGRule)abstractr;
				if (r.getRHS().getNumChildren() > 0) {
					if (debug) Debug.debug(debug, "Recurse-free considering "+r);
					Symbol[] leaves = r.getRHSLeaves();
					// no self-loops allowed but otherwise same rules as below
					// no update based on already-inserted states
					for (int i = 0; i < leaves.length; i++) {
						if (!states.contains(leaves[i]))
							continue;
						if (v.state == leaves[i])
							continue;
						if (tarjanSeen.containsKey(leaves[i]))
							continue;
						if (debug) Debug.debug(debug, "Recursing parent-free on "+leaves[i]);
						TarjanVert leaf = new TarjanVert(leaves[i]);
						tarjanSeen.put(leaves[i], leaf);
						// if lower level is false return false right away
						if (!tarjan(includeproducers, leaf)) { 
							if (debug) Debug.debug(debug, "Found loop in "+leaf+" so aborting search");
							return false;
						}
					}
				}
			}
		}
		if (debug) Debug.debug(debug, "Enqueuing "+v);
		tarjanStack.push(v);
		// normal call or, if producers are okay, state-to-state rules only
		for (Rule abstractr : getRulesOfType(v.state)) {
			RTGRule r = (RTGRule)abstractr;
			if (includeproducers && r.getRHS().numChildren > 0)
				continue;
			if (debug) Debug.debug(debug, "Considering "+r);
			Symbol[] leaves = r.getRHSLeaves();
			for (int i = 0; i < leaves.length; i++) {
				// direct self loop. Mark for later
				if (v.state == leaves[i]) {
					if (debug) Debug.debug(debug, "Found direct self loop for "+v.state);
					v.selfloop = true;
				}
				if (states.contains(leaves[i])) {
					if (!tarjanSeen.containsKey(leaves[i])) {
						if (debug) Debug.debug(debug, "Recursing on "+leaves[i]);
						TarjanVert leaf = new TarjanVert(leaves[i]);
						tarjanSeen.put(leaves[i], leaf);
						// if lower-level is false, return false right away
						if (!tarjan(includeproducers, leaf)) { 
							if (debug) Debug.debug(debug, "Found loop in "+leaf+" so aborting search");
							return false;
						}
						if (leaf.lowlink < v.lowlink) {
							if (debug) Debug.debug(debug, "Lowering lowlink of "+v.state+" to "+leaf.lowlink+" because of "+leaf.state);
							v.lowlink = leaf.lowlink;
						}
					}
					else {
						TarjanVert leaf = tarjanSeen.get(leaves[i]);
						if (tarjanStack.contains(leaf) && leaf.lowlink < v.lowlink) {
							if (debug) Debug.debug(debug, "Lowering lowlink of "+v.state+" to "+leaf.lowlink+" because of enqueued "+leaf.state);
							v.lowlink = leaf.lowlink;
						}
					}
				}
			}
		}
		if (v.lowlink == v.index) {
			if (debug) Debug.debug(debug, "We have completed exploration of "+v.state);
			Vector<TarjanVert> sccmembers = new Vector<TarjanVert>();
			while (tarjanStack.peek() != v) {
				// one member is all we need
				TarjanVert sccmember = tarjanStack.pop();
				if (debug) Debug.debug(debug, v+" is connected to "+sccmember);
				sccmembers.add(sccmember);
			}
			// discard self
			sccmembers.add(tarjanStack.pop());
			// singleton => if it's got a self loop it's infinite, otherwise it's finite
			if (sccmembers.size() < 2) {
				if (debug) Debug.debug(debug, "Self loop of "+sccmembers.get(0)+" is "+sccmembers.get(0).selfloop);
				return !sccmembers.get(0).selfloop;
			}
			return false;
		}
		// we probably shouldn't be here...
		if (debug) Debug.debug(debug, "Couldn't decide about "+v.state+" so returning true");
		return true;
	}

	// deprecated version replaced by tarjan algorithm above
	
	// isFinite returns true if there are not an infinite number of trees that can be produced
	// from this set
/*
		// TODO: memoize this, releasing memoization when change to set occurs
		// maintain master set (states we will process), then for each state popped off,
		// get the set of states it reaches until this is unchanging. If one of the members of the set
		// is the state from the master, we're in a loop. Union the reached states 
		// minus the processed states with the master set
		// and continue. When the master set is empty, we're done.
		boolean debug = false;
		HashSet masterStates = new HashSet();
		HashSet processedStates = new HashSet();
		masterStates.add(startState);
		int stateCounter = 0;
		while(!masterStates.isEmpty()) {
			// the state we will look for a loop to
			Symbol currState = (Symbol)masterStates.iterator().next();
			stateCounter++;
			processedStates.add(currState);
			masterStates.remove(currState);
			HashSet reachedStates = new HashSet();
			// the things that could possibly increase the size of reached states
			// initialized with the rules produced from the currstate
			if (debug) Debug.debug(debug, "Getting rules for "+currState+": "+stateCounter);

			// need to make a copy cause we're going to modify it
			ArrayList<Rule> rulesOfType = getRulesOfType(currState);
			if (rulesOfType == null)
				continue;
			ArrayList<Rule> currRules = new ArrayList<Rule>(rulesOfType);
			int size = reachedStates.size();
			// in the loop: 
			//    1) add all the states from currRules to a mini set. die if the set has the currstate
			//    2) empty out currRules and refill it with the expansions from the miniset
			//    3) union reachedstates with the miniset. check size and break if static
			// stop when the reached states size stops changing
			while (true) {
				HashSet miniSet = new HashSet();
				//    1) add all the states from currRules to a mini set. die if the set has the currstate
				Iterator it = currRules.iterator();
				while (it.hasNext()) {
					Symbol[] leaves = ((RTGRule)it.next()).getRHSLeaves();
					for (int i = 0; i < leaves.length; i++) {
						if (states.contains(leaves[i])) {
							if (currState.equals(leaves[i])) {
								if (debug) Debug.debug(debug, "Can reach "+currState.toString()+" from itself");
								return false;
							}
							miniSet.add(leaves[i]);
						}
					}
				}
				//    2) empty out currRules and refill it with the expansions from the miniset
				currRules.clear();
				it = miniSet.iterator();
				while (it.hasNext())
					currRules.addAll(getRulesOfType((Symbol)it.next()));
				//    3) union reachedstates with the miniset. check size and break if static
				reachedStates.addAll(miniSet);
				if (reachedStates.size() == size)
					break;
				size = reachedStates.size();
			}
			// we've established that currState does not generate rules to reach itself.
			// if there are new states here, add them to the masterstates list
			reachedStates.removeAll(processedStates);
			masterStates.addAll(reachedStates);
		}
		return true;
	}
*/

	// deprecated version not really useful
	
	/*// makeFinite is almost an exact copy of isFinite, above, but it removes rules that cause loops
	public void makeFinite() {
		makeFinite(false);
	}
	public void makeFinite(boolean debug) {
		// maintain master set (states we will process), then for each state popped off,
		// get the set of states it reaches until this is unchanging. If one of the members of the set
		// is the state from the master, we're in a loop, so remove that rule (and reinitialize). 
		// Union the reached states 
		// minus the processed states with the master set
		// and continue. When the master set is empty, we're done.
		if (debug) Debug.debug(debug, "There are "+states.size()+" states");
		HashSet masterStates = new HashSet();
		HashSet processedStates = new HashSet();
		masterStates.add(startState);
		int stateCounter = 0;
		while(!masterStates.isEmpty()) {
			// the state we will look for a loop to
			Symbol currState = (Symbol)masterStates.iterator().next();
			stateCounter++;
			processedStates.add(currState);
			masterStates.remove(currState);
			HashSet reachedStates = new HashSet();
			// the things that could possibly increase the size of reached states
			// initialized with the rules produced from the currstate
			if (debug) Debug.debug(debug, "Getting rules for "+currState.toString()+": "+stateCounter);

			// map states to sets of rules used to get to them. If removing a chosen rule
			// would remove all ways to leave the state, remove these instead
			Hashtable alternativeDeleteRules = new Hashtable();
			// track rules deleted in this iteration to handle case where all paths are removed
			HashSet deletedRules = new HashSet();

			ArrayList<Rule> crnocopy = getRulesOfType(currState);
			if (crnocopy == null) {
				if (debug) Debug.debug(debug, "No rules for "+currState.toString());
				continue;
			}
			ArrayList<Rule> currRules = new ArrayList<Rule>(getRulesOfType(currState));
			int size = reachedStates.size();
			// in the loop: 
			//    1) add all the states from currRules to a mini set. die if the set has the currstate
			//    2) empty out currRules and refill it with the expansions from the miniset
			//    3) union reachedstates with the miniset. check size and break if static
			// stop when the reached states size stops changing
			while (true) {
				HashSet miniSet = new HashSet();
				//    1) add all the states from currRules to a mini set. Remove a rule if it causes a loop.
				Iterator it = currRules.iterator();
				while (it.hasNext()) {
					RTGRule r = (RTGRule)it.next();
					//		    if (debug) Debug.debug(debug, "\t Following "+r.toString());
					Symbol[] leaves = r.getRHS().getLeaves();
					boolean noremoval=true;
					for (int i = 0; i < leaves.length; i++) {
						if (states.contains(leaves[i])) {
							if (currState.equals(leaves[i])) {
								if (debug) Debug.debug(debug, "Can reach "+currState.toString()+" from itself");
								// initially the delete rule set contains the troubling rule. But if we go a step back
								// and need to remove multiple rules, this problem could grow.
								HashSet deleteRuleSet = new HashSet();
								deleteRuleSet.add(r);
								while (deleteRuleSet.size() > 0) {
									HashSet drscopy = new HashSet(deleteRuleSet);
									Iterator drsit = drscopy.iterator();
									while (drsit.hasNext()) {
										RTGRule dr = (RTGRule)drsit.next();
										deleteRuleSet.remove(dr);
										if (deletedRules.contains(dr)) {
											if (debug) Debug.debug(debug, "ERROR: rule "+dr.toString()+" already deleted!");
											continue;
										}
										// if we can remove it for real, no need to do more.
										ArrayList<Rule> lhstyperules = new ArrayList<Rule>(getRulesOfType(dr.getLHS()));
										// intersect with already deleted rules
										lhstyperules.removeAll(deletedRules);
										if (lhstyperules.size() > 1) {
											if (debug) Debug.debug(debug, "Removing "+dr.toString());
											rules.remove(dr);
											deletedRules.add(dr);
										}
										else {
											// find a rule to delete by going back through the alternative delete rules
											if (debug) Debug.debug(debug, "Can't remove "+dr.toString()+" because of singleton lhs. Using alternatives:");
											HashSet altSet = (HashSet)alternativeDeleteRules.get(dr.getLHS());
											if (altSet == null) {
												if (debug) Debug.debug(debug, "WARNING: no alternative delete rules, so deleting (unwisely) "+dr.toString());
												rules.remove(dr);
												deletedRules.add(dr);
											}
											else {
												deleteRuleSet.addAll(altSet);
												Iterator asit = altSet.iterator();
												while (asit.hasNext()) {
													RTGRule altRule = (RTGRule)asit.next();
													if (debug) Debug.debug(debug, "\t adding "+altRule.toString());
												}
											}
										}
									}
								}
								noremoval = false;
								break;
							}
						}
					}
					// okay to add states from this rule
					if (noremoval) {
						for (int i = 0; i < leaves.length; i++) {
							if (states.contains(leaves[i])) {
								// save backwards pointers, otherwise known as alternative delete rules
								// this will blow up a bit...
								HashSet altRuleSet = null;
								if (!alternativeDeleteRules.containsKey(leaves[i]))
									altRuleSet = new HashSet();
								else
									altRuleSet = (HashSet)alternativeDeleteRules.get(leaves[i]);
								altRuleSet.add(r);
								alternativeDeleteRules.put(leaves[i], altRuleSet);

								// add as a newly accessed state
								miniSet.add(leaves[i]);
							}
						}
					}
				}
				//    2) empty out currRules and refill it with the expansions from the miniset
				currRules.clear();
				it = miniSet.iterator();
				while (it.hasNext())
					currRules.addAll(getRulesOfType((Symbol)it.next()));
				//    3) union reachedstates with the miniset. check size and break if static
				reachedStates.addAll(miniSet);
				if (reachedStates.size() == size)
					break;
				size = reachedStates.size();
			}
			// we've established that currState does not generate rules to reach itself.
			// if there are new states here, add them to the masterstates list
			reachedStates.removeAll(processedStates);
			masterStates.addAll(reachedStates);
			if (debug) Debug.debug(debug, "About to initialize");
			initialize();
			pruneUseless();
		}

	}
*/
	// in the future, normal form and condensation where appropriate should make this equal to the number of
	// trees...but maybe not!

	// note that the way this is done and the way grammars are stored mean that the calculation is not very efficient
	// TODO: this can be prevented by init-time fixes...


	// algorithm: keep count of the number of possibilities for each state.
	// for all states with only terminal productions (some must exist in a finite grammar) count 1 per rule. add these states
	// to the "okay" set.
	// while there is no entry for the start state (alternatively, while there are states that aren't okay), for all okay states,
	// for each derivation, multiply the entries for each state produced (1 if only terminals). Score for the state is the sum over
	// all derivations for that state.

	// we get this in biginteger 
	public String getNumberOfDerivations() {

		//	Debug.debug(true, "WARNING! FINITE CHECK IN NUMBER OF DERIVATIONS IS DISABLED!");
		// 	if (!isFinite()) {
		// 	    Debug.debug(true, "There are an infinite number of trees");
		// 	    return -1;
		// 	}
		// ensure normal form 
		if (!madeNormal)
			makeNormal();
		// make sure there's something to count
		if (rules.size() == 0)
			return "0";
		Hashtable scores = new Hashtable();
		HashSet okStates = new HashSet();
		HashSet leftStates = new HashSet(states);

		// copy cause we change the set
		while (!scores.containsKey(startState)) {
			HashSet leftStatesCopy = new HashSet(leftStates);
			Iterator it = leftStatesCopy.iterator();
			while (it.hasNext()) {
				//		Debug.debug(true, "Size of left states = "+leftStates.size());
				//		Debug.debug(true, "Size of left states copy = "+leftStatesCopy.size());
				Symbol currState = (Symbol)it.next();
				//		Debug.debug(true, "Considering state "+currState);
				ArrayList<Rule> currRules = getRulesOfType(currState);
				if (currRules == null)
					continue;
				Iterator crit = currRules.iterator();
				BigInteger derivCount = new BigInteger("0");
				boolean hasBadState = false;
				while (crit.hasNext()) {
					BigInteger ruleDeriv = new BigInteger("1");
					Symbol[] leaves = ((RTGRule)crit.next()).getRHS().getLeaves();
					//		    System.err.print("\t");
					for (int i = 0; i < leaves.length; i++) {
						//			System.err.print(leaves[i]+"...");
						// acceptable states multiply the derivations. unacceptables
						// get the can. terminals don't affect the number
						if (states.contains(leaves[i])) {
							if (okStates.contains(leaves[i])) {
								BigInteger contrib = (BigInteger)scores.get(leaves[i]);
								ruleDeriv = ruleDeriv.multiply(contrib);
								//				System.err.print("("+contrib+") ");
							}
							else {
								//				Debug.debug(true, "BAD!");
								hasBadState = true;
								break;
							}
						}
						//			else
						//			    System.err.print("(1t) ");
					}
					// one bad apple spoils the bunch
					if (hasBadState)
						break;
					//		    Debug.debug(true, "="+ruleDeriv);
					derivCount = derivCount.add(ruleDeriv);
				}
				if (hasBadState)
					continue;
				//		Debug.debug(true, "\tTOTAL: "+derivCount.toString());
				// now we know this state, so it's no longer bad. we can remove it from 
				// consideration and make it okay
				scores.put(currState, derivCount);
				okStates.add(currState);
				leftStates.remove(currState);
			}
		}
		return ((BigInteger)scores.get(startState)).toString();
	}


	// improved epsilon removal that uses computeClosure
	// 0) map states to integers and back
	// 1) build array of state x state. seed with epsilon transition weights
	// 2) compute closure
	// 3) adjoin all rules with alternate states
	public void removeEpsilons() throws UnusualConditionException {
		boolean debug = false;
		if (epsRemoved)
			return;
		// map states to integers
		int numStates = states.size();
		Symbol[] i2s =new Symbol[numStates];
		HashMap<Symbol, Integer> s2i = new HashMap<Symbol, Integer>();
		int nextid = 0;
		for (Symbol s : states) {
			i2s[nextid] = s;
			s2i.put(s, nextid++);
		}
		// build seeded array
		// also set up seeded non-eps map
		// also note which states have non-eps rules
		double[][] arr = new double[numStates][];
		boolean[] liveStates = new boolean[numStates];
		HashMap<Symbol, HashMap<Item, Double>> ruleMatrix = new HashMap<Symbol, HashMap<Item, Double>>();
		boolean sawEps = false;
		for (int i = 0; i < numStates; i++) {
			Symbol s = i2s[i];
			liveStates[i] = false;
			arr[i] = new double[numStates];
			for (int j = 0; j < numStates; j++)
				arr[i][j] = semiring.ZERO();
			ruleMatrix.put(s, new HashMap<Item, Double>());
			if (getRulesOfType(s) == null) {
				if (debug) Debug.debug(debug, "Skipping "+s+" cause it has no rules");
				continue;
			}
			for (Rule genr : getRulesOfType(s)) {
				RTGRule r = (RTGRule)genr;
				if (isEpsilonRule(r)) {
					if (debug) Debug.debug(debug, "Saw epsilon rule "+r);
					arr[i][s2i.get(r.getRHS().getLabel())] = r.getWeight();
					sawEps = true;
				}
				else {
					liveStates[i] = true;
				}
			}
		}
		// no eps rules, nothing to do
		if (!sawEps) {
			if (debug) Debug.debug(debug, "No epsilon rules");
			epsRemoved = true;
			return;
		}
		arr = computeClosure(arr);
		// adjoin all rules with alternate states
		for (int dst= 0; dst < numStates; dst++) {
			// don't bother if there aren't any entries in matrix
			if (!liveStates[dst])
				continue;
			// TODO: should src and rule loops be interchanged?
			for (Rule genr : getRulesOfType(i2s[dst])) {
				RTGRule r = (RTGRule)genr;
				if (!isEpsilonRule(r)) {
					for (int src = 0; src < numStates; src++) {
						// don't bother if src-dst has no weight
						if (semiring.betteroreq(semiring.ZERO(), arr[src][dst]))
							continue;
						Symbol srcstate = i2s[src];
						double total = semiring.ZERO(); 
						if (ruleMatrix.get(srcstate).containsKey(r.getRHS()))
							total = ruleMatrix.get(srcstate).get(r.getRHS());
						double addin = semiring.times(r.getWeight(), arr[src][dst]);
						if (debug) Debug.debug(debug, "Combining "+dst+" -> "+ r.getRHS()+", "+r.getWeight()+
								" with "+src+" -> "+dst+ ", "+arr[src][dst]+" and extant cost "+total);
						total = semiring.plus(total, addin);
						if (total == Double.POSITIVE_INFINITY || total == Double.NEGATIVE_INFINITY)
							throw new UnusualConditionException("Can't remove epsilons: cycles cause divergence");
						ruleMatrix.get(srcstate).put(r.getRHS(), total);

					}
				}
			}
		}
		// create new rule set
		ArrayList<Rule> newRules = new ArrayList<Rule>();
		for (Symbol lhs : ruleMatrix.keySet()) {
			for (Item rhs : ruleMatrix.get(lhs).keySet()) {
				RTGRule nr = new RTGRule(this, lhs, (TreeItem)rhs, ruleMatrix.get(lhs).get(rhs), semiring);
				if (debug) Debug.debug(debug, "Created "+nr);
				newRules.add(nr);
			}
		}
		// badda bing
		rules = newRules;
		initialize();
		pruneUseless();
		epsRemoved = true;
	}
	
	// remove epsilon transitions - that is, state-to-state transitions, so called because they produce no symbol
	// epsilons are compact ways of writing rtgs but they lead to undeterminizable conditions.
	// i don't think it matters what order this is done in - collect all epsilons, expand them all by multiplying weight through. 
	// If in the process 
	// more epsilons rules are created add them to the queue too. When done, initialize the new rule set
	public void oldRemoveEpsilons() {
		boolean debug = false;
		if (epsRemoved)
			return;
		ArrayList<Rule> newRules = new ArrayList<Rule>();
		HashSet<Rule> seenRules = new HashSet<Rule>();
		Stack epsilonRules = new Stack();
		// first gather all the epsilon rules
		Iterator rit = rules.iterator();
		while (rit.hasNext()) {
			RTGRule r = (RTGRule)rit.next();
			if (isEpsilonRule(r)) {
				if (debug) Debug.debug(debug, "Adding epsilon rule "+r.toString());
				seenRules.add(r);
				epsilonRules.push(r);
			}
			else
				newRules.add(r);
		}
		// now process each epsilon rule in turn
		while (epsilonRules.size() > 0) {
			RTGRule r = (RTGRule)epsilonRules.pop();
			if (debug) Debug.debug(debug, "Expanding epsilon rule "+r.toString());
			// expand the right hand side
			ArrayList<Rule> replaceRules = getRulesOfType(r.getRHS().getLabel());
			if (replaceRules == null)
				continue;
			Iterator rrit = replaceRules.iterator();
			while (rrit.hasNext()) {
				RTGRule rr = (RTGRule)rrit.next();
				// new rule is lhs of the old rule, rhs of the new rule, product of 
				// the weights
				RTGRule nr = new RTGRule(this, r.getLHS(), (TreeItem)rr.getRHS(), semiring.times(r.getWeight(), rr.getWeight()), semiring);
				if (debug) Debug.debug(debug, "\t Created "+nr.toString());
				if (isEpsilonRule(nr)) {
					if (!seenRules.contains(nr)) {
						epsilonRules.push(nr);
						seenRules.add(nr);
					}
				}
				else
					newRules.add(nr);
			}
		}
		// badda bing
		rules = newRules;
		initialize();
		pruneUseless();
		epsRemoved = true;
	}

	// is a rule an epsilon rule? Rules themselves can't know this because they don't know about state
	// an epsilon rule is a rule with rhs singleton that is a state
	public boolean isEpsilonRule(RTGRule r) {
		TreeItem rhs = (TreeItem)r.getRHS();
		if (rhs.getNumChildren() > 0)
			return false;
		return states.contains(rhs.getLabel());
	}


	// reinventing the wheel? sort of. This weighted determinize properly propagates the weights
	// to the new grammar. It does it using the Mohri approach, which is fundamentally equivalent to the
	// unweighted approach above, but uses tree automata. This is a frontier-root deal, which means some
	// scuttling will necessarily be involved. I think it's faster to code this up again, stealing
	// code and algorithms where useful from above.

	// a pair maps a state (as we know them) to a residual
	// although the weight side of a pair is stored as a double, they are compared
	// to other pairs as a float!
	// NOTE: currently floatification off...

	class Pair {
		public Symbol state;
		public double weight;
		private Semiring semiring;
		private int hsh;
		public Pair(Symbol s, Semiring sr) {
			state = s;
			semiring = sr;
			weight = -1;
			setHashCode();
		}
		public Pair(Symbol s, double w, Semiring sr) {
			state = s;
			semiring = sr;
			weight = w;
			setHashCode();
		}
		public Pair(Symbol s, Double w, Semiring sr) {
			state = s;
			semiring = sr;
			weight = w.doubleValue();
			setHashCode();
		}
		public void update(double w) {
			if (weight < 0)
				weight = w;
			else
				weight = semiring.plus(w, weight);
			setHashCode();
		}

		private void setHashCode() {
			double mult = Math.pow(10.0d, 4);
			hsh = state.hashCode()+new Double(Math.round(weight*mult)/mult).hashCode();
		}
		// steve's idea. hashcode is just the state. equals comparison tests for a tolerance
		public int hashCode() {

			// NOTE: the good one
			return hsh;


			// a little too inefficient
			//	    return state.hashCode();


			//return state.hashCode()+(int)(weight);
			// NOTE: the bad one
			// 	    return state.hashCode()+new Double(weight).hashCode();
		}

		public boolean equals(Object o) {
			if (!o.getClass().equals(this.getClass()))
				return false;
			Pair p = (Pair)o;
			if (!p.state.equals(this.state))
				return false;

			// NOTE: the bad one
			  	    if (p.weight != this.weight)
			  		return false;

			// NOTE: the good one
			//if (Math.abs(p.weight-this.weight) > 0.000001)
			//	return false;

			return true;


			//return (p.state.equals(this.state) && p.weight == this.weight);
		}
		public String toString() {
			return "("+this.state.toString()+", "+this.weight+")";
		}
	}

	// just make it easier to compare
	class NewStateSet extends HashMap<Symbol, Pair> {
		public boolean containsStart = false;
		public int hashCode() {
			int retval = 0;
			Iterator it = this.values().iterator();
			while (it.hasNext())
				retval += it.next().hashCode();
			return retval;
		}

		public boolean equals(Object o) {
			if (!o.getClass().equals(this.getClass()))
				return false;
			NewStateSet n = (NewStateSet)o;
			if (this.size() != n.size())
				return false;
			for (Map.Entry<Symbol, Pair> e : entrySet()) {
				Symbol key = e.getKey();
				Pair p1 = e.getValue();
				Pair p2 = n.get(key);
				if (p2 == null)
					return false;
				if (!p1.equals(p2))
					return false;
			}
			return true;
		}

		public String toString() {
			Iterator scit = this.values().iterator();
			StringBuffer retval = new StringBuffer();
			retval.append("{");
			while (scit.hasNext()) {
				Pair p = (Pair)scit.next();
				retval.append(p.toString()+" ");
			}
			retval.append("}");
			return retval.toString();
		}
	}
	// a vector of newstatesets that we want to compare
	class StateVector extends Vector {
		public StateVector(){
			super();
		}
		public StateVector(Collection c) {
			super(c);
		}
		public int hashCode() {
			int retval = 0;
			Iterator it = this.iterator();
			while (it.hasNext())
				retval += it.next().hashCode();
			return retval;
		}
		public boolean equals(Object o) {
			if (!o.getClass().equals(this.getClass()))
				return false;
			StateVector v = (StateVector)o;
			if (this.size() != v.size())
				return false;
			for (int i = 0; i < this.size(); i++)
				if (!((NewStateSet)this.elementAt(i)).equals((NewStateSet)v.elementAt(i)))
					return false;
			return true;
		}
		public String toString() {
			StringBuffer ret = new StringBuffer("[");
			for (int i = 0; i < this.size(); i++)
				ret.append(((NewStateSet)this.elementAt(i)).toString() + ", ");
			ret.append("]");
			return ret.toString();
		}
	}


	// different way of doing weighted determinize. In the inner loop, go by labels and available states, rather
	// than enumerating each vector. Build the valid vectors piece by piece this way

	// return true if it was successful

	public boolean weightedDeterminize(boolean doBorchardt) {
		return weightedDeterminize(0, doBorchardt);
	}

	// for timeout and checking
	private boolean timeout(String msg) {
		if (timeLimit > 0) {
			long lapse = System.currentTimeMillis()-startTime;
			if (lapse > timeLimit) {
				Debug.debug(true, msg);
				return true;
			}
			if (lapse / (60*1000) > lastLapse) {
				System.err.print(".");
				lastLapse = lapse / (60*1000);
			}
		}
		return false;
	}

	
	
	// reimplementation of weighted Determinize, using (priority) queue and hopefully more readable
	private boolean pqwd(long tl, boolean doBor) {
		boolean debug = false;
		if (doBor) {
			Debug.prettyDebug("Doing borchardt-style determinization");
		}
		
		// keep a 
		return true;
	}
	
	
	
	public boolean weightedDeterminize(long tl, boolean doBorchardt) {
		if (doBorchardt) {
			Debug.prettyDebug("Doing borchardt-style determinization");
		}
		//		Debug.debug(true, "BEGINNING EFFICIENTRULESET WEIGHTED DETERMINIZE");
		// each member of newstates is a map of state -> (state, residual) pairs
		timeLimit = tl;
		lastLapse = 0;
		startTime = System.currentTimeMillis();

		HashSet newstates = new HashSet();
		// new rules will become the actual set of rules
		// NOTE: now it's a vector!
		//		HashSet newrules = new HashSet();
		Vector newrules = new Vector();
		// we simulate the initial state: {(i, 0)}. by finding all rules with terminal expansions
		// and creating our new states
		Iterator it = rules.iterator();
		// invert the mapping: trees (terminal trees) to sets of rules. 
		HashMap<TreeItem, HashSet<RTGRule>> term2Rule = new HashMap<TreeItem, HashSet<RTGRule>>();
		// alternate inversion: map tree LABEL to sets of rules
		Hashtable label2Rule = new Hashtable();
		// rankMap maps states to the size of the largest rule they're in the tail of
		Hashtable rankMap = new Hashtable();
		// state2Label maps states to the sets of labels they could be involved in
		// and for each label, the set of rules they could be involved in
		Hashtable state2Label = new Hashtable();
		// freshset is a set of all labels that may be considered, each mapping to
		// all rules
		// that may be considered
		Hashtable freshSet = new Hashtable();
		// determnames maps to new names
		determNames = new Hashtable();
		nextDeterm = 0;
		// might be more than one new start state
		HashSet newStartStates = new HashSet();

		Symbol nullsym = SymbolFactory.getSymbol("***NULL***");
		while (it.hasNext()) {
			RTGRule r = (RTGRule)it.next();
			if (!states.contains(r.getRHS().label)) {
				if (!freshSet.containsKey(r.getRHS().label))
					freshSet.put(r.getRHS().label, new HashSet());
				((HashSet)freshSet.get(r.getRHS().label)).add(r);
			}
			else {
				if (!freshSet.containsKey(nullsym))
					freshSet.put(nullsym, new HashSet());
				((HashSet)freshSet.get(nullsym)).add(r);
			}
			if (!term2Rule.containsKey(r.getRHS()))
				term2Rule.put((TreeItem)r.getRHS(), new HashSet<RTGRule>());
			term2Rule.get(r.getRHS()).add(r);
			// have to cover the "label is a state" case, too
			if (!states.contains(r.getRHS().label)) {
				if (!label2Rule.containsKey(r.getRHS().label))
					label2Rule.put(r.getRHS().label, new HashSet());
				((HashSet)label2Rule.get(r.getRHS().label)).add(r);
			}
			else {
				if (!label2Rule.containsKey(nullsym))
					label2Rule.put(nullsym, new HashSet());
				((HashSet)label2Rule.get(nullsym)).add(r);
			}
			if (r.getRHS().numChildren > 0) {
				for (int i = 0; i < r.getRHS().numChildren; i++)
					if (states.contains(r.getRHS().children[i].label)) {
						if (!rankMap.containsKey(r.getRHS().children[i].label))
							rankMap.put(r.getRHS().children[i].label, new Integer(r.getRHS().numChildren));
						else
							rankMap.put(r.getRHS().children[i].label, 
									new Integer(Math.max(r.getRHS().numChildren, 
											((Integer)rankMap.get(r.getRHS().children[i].label)).intValue())));
						if (!state2Label.containsKey(r.getRHS().children[i].label))
							state2Label.put(r.getRHS().children[i].label, new Hashtable());
						Hashtable s2l2r = (Hashtable)state2Label.get(r.getRHS().children[i].label);
						if (!s2l2r.containsKey(r.getRHS().label))
							s2l2r.put(r.getRHS().label, new HashSet());
						((HashSet)s2l2r.get(r.getRHS().label)).add(r);
						state2Label.put(r.getRHS().children[i].label, s2l2r);
					}
			}
			// state-to-state case: treat the label as if it is the first child, and treat the
			// nullsym as the label
			else if (states.contains(r.getRHS().label)) {
				if (!state2Label.containsKey(r.getRHS().label))
					state2Label.put(r.getRHS().label, new Hashtable());
				Hashtable s2l2r = (Hashtable)state2Label.get(r.getRHS().label);
				if (!s2l2r.containsKey(nullsym))
					s2l2r.put(nullsym, new HashSet());
				((HashSet)s2l2r.get(nullsym)).add(r);
				state2Label.put(r.getRHS().label, s2l2r);
			}
		}
		if (timeout("weightedDeterminize: bailing out after initialization"))
			return false;

		// sanity check: term2Rule, label2Rule, rankMap
		// 	Debug.debug(true, "Term2Rule:");
		// 	Iterator t2rit = term2Rule.keySet().iterator();
		// 	while (t2rit.hasNext()) {
		// 	    Tree t = (Tree)t2rit.next();
		// 	    Debug.debug(true, "\t"+t.toString()+":");
		// 	    HashSet hs = (HashSet)term2Rule.get(t);
		// 	    Iterator hsit = hs.iterator();
		// 	    while (hsit.hasNext()) {
		// 		Rule r = (Rule)hsit.next();
		// 		Debug.debug(true, "\t\t"+r.toString());
		// 	    }
		// 	}

		// 	Debug.debug(true, "Label2Rule:");
		// 	Iterator l2rit = label2Rule.keySet().iterator();
		// 	while (l2rit.hasNext()) {
		// 	    Symbol l = (Symbol)l2rit.next();
		// 	    Debug.debug(true, "\t"+l.toString()+":");
		// 	    HashSet hs = (HashSet)label2Rule.get(l);
		// 	    Iterator hsit = hs.iterator();
		// 	    while (hsit.hasNext()) {
		// 		Rule r = (Rule)hsit.next();
		// 		Debug.debug(true, "\t\t"+r.toString());
		// 	    }
		// 	}
		// 	Debug.debug(true, "RankMap:");
		// 	Iterator rmit = rankMap.keySet().iterator();
		// 	while (rmit.hasNext()) {
		// 	    Symbol s = (Symbol)rmit.next();
		// 	    Debug.debug(true, "\t"+s.toString()+": "+s.hashCode()+": "+((Integer)rankMap.get(s)).intValue());
		// 	}

		// for each possible symbol, calculate the weight of this symbol
		// we calculate weights in the specified semiring
		// (gets more complicated when there might be residuals. there are none here)
		boolean debug = false;
		boolean debugcount = true;
		int countiter = 100000;
		for (TreeItem tree : term2Rule.keySet()) {
		
			if (timeout("weightedDeterminize: bailing out in rule creation for terminals"))
				return false;
			
			// only interested in terminal trees
			// update: preterminal trees count too
			if (tree.numChildren == 0 && states.contains(tree.label))
				continue;
			boolean isGood = true;
			for (int i = 0; i < tree.numChildren; i++) {
				if (states.contains(tree.children[i].label)) {
					isGood = false;
					continue;
				}
			}
			if (!isGood)
				continue;

			HashSet<RTGRule> treeRules = term2Rule.get(tree);
			Iterator treeRulesit = treeRules.iterator();
			// TODO: a better initial weight?
			// remember: rules can't have negative weight
			double weight = -1;
			boolean set = false;
			//	    debug = false;
			if (debug) Debug.debug(debug, "Setting weights for tree "+tree.toString());
			while (treeRulesit.hasNext()) {
				RTGRule r = (RTGRule)treeRulesit.next();
				if (debug) Debug.debug(debug, "\t"+r.toString()+" sends weight from "+weight);
				if (!set) {
					weight = r.getWeight();
					set = true;
				}
				else weight = semiring.plus(weight, r.getWeight());
				if (debug) Debug.debug(debug, " to "+weight);

			}
			if (doBorchardt)
				weight = semiring.ONE();
			treeRulesit = treeRules.iterator();
			// we now build the new state - actually a hash map from state to pair
			NewStateSet newState = new NewStateSet();
			while (treeRulesit.hasNext()) {
				RTGRule r = (RTGRule)treeRulesit.next();
				// no need to worry about previous residuals,
				// in the terminal case
				// just calculate what this residual should be
				double res = semiring.times(r.getWeight(), semiring.inverse(weight));
				Pair p = null;
				if (newState.containsKey(r.getLHS()))
					p = (Pair)newState.get(r.getLHS());
				else
					p = new Pair(r.getLHS(), semiring);
				p.update(res);
				newState.put(r.getLHS(), p);
				if (startState.equals(r.getLHS()))
					newState.containsStart = true;
			}
			if (debug) Debug.debug(debug, "Just added first-order state "+newState.toString());
			// now we've got the new state. add it to the set of new states
			// and the set of consideration states.
			// and create a rule: this newstate goes to this terminal with its weight!

			// sanity check:
			// 	    Iterator scit = newState.values().iterator();
			// 	    System.err.print("Adding state {");
			// 	    while (scit.hasNext()) {
			// 		Pair p = (Pair)scit.next();
			// 		System.err.print("("+p.state+", "+p.weight+") ");
			// 	    }
			// 	    Debug.debug(true, "}");


			newstates.add(newState);
			if (debugcount && newstates.size() % countiter == 0) {
				Debug.prettyDebug("Added "+newstates.size()+" level 1 states");
			}
			if (newState.containsStart)
				newStartStates.add(newState);
			Symbol name = getStateName(newState);

			RTGRule r = new RTGRule(this, name, tree, weight, semiring);
			newrules.add(r);
			if (debugcount && newrules.size() % countiter == 0) {
				Debug.prettyDebug("Added "+newrules.size()+" level 1 rules");
			}
			//	    Debug.debug(true, "Adding rule "+r.toString());
		}
//		Debug.prettyDebug("Done with initialization");
		// now the "normal" part of the algorithm: find the rank of the newstates 
		// (i.e. the largest vector we'll have to create).
		// make all possible orderings (do this recursively...see the other determinize for motivation). 
		// add these orderings to 
		// a hash set. and continue.

		// remember, now: a state vector is a list of new states. a new state is a map 
		// of old states to old states paired with residuals.
		// make sure to keep that in mind

		// to avoid adding edges we've already added, forbid vectors without at least one 
		// new member. to begin with they're all new
		HashSet recentNewStates = new HashSet(newstates);
		// 	Iterator rnsit = recentNewStates.iterator();
		// 	Debug.debug(true, "Recently added new states: ");
		// 	while (rnsit.hasNext()) {
		// 	    Debug.debug(true, ((NewStateSet)rnsit.next()).toString());
		//      }

		int newstatessize = newstates.size();
		do {
			if (timeout("weightedDeterminize: bailing out at top of iteration"))
				return false;


			// early bailout to keep from running forever
			// super early bailout just to test each of them
			//	    if (newstates.size()-newstatessize > 2000) {
			// 	    if (newstates.size() > 20000 && newstates.size()-newstatessize > 5000) {
			// 		Debug.debug(true, "WARNING: Early bailout from weighted determinize when considering "+newstates.size()+" states.");
			// 		return false;
			// 	    }

			// currentRecentNewStates were the states created in the last iteration (first time through they're
			// all the new states). 
			HashSet currentRecentNewStates = new HashSet(recentNewStates);
			// recentNewStates are the states we will create in this iteration. We won't look at them until we're
			// ready to try freshness at the end and we'll then integrate them into newstates
			recentNewStates = new HashSet();
			// newstates are the states we have to work with. they were NOT created at any point during this iteratioin
			newstatessize = newstates.size();
			//	    Debug.debug(true, "Newstates has size of "+newstates.size()+"; recent new states has size of "+currentRecentNewStates.size());
			// 	    // sanity check
			Iterator nsscit = newstates.iterator();
			// 	    while (nsscit.hasNext()) {
			// 		NewStateSet nshm = (NewStateSet)nsscit.next();
			// 		Debug.debug(true, "\t"+nshm.toString());
			// 	    }




			// TODO: 
			// setting the rho values (final weights)
			// should be done before vectors are formed. But right now 
			// I haven't put final weights into the initial read, so
			// maybe never mind...


			// for greater efficiency, do this by label, and build the needed
			// vectors
			//	    Debug.debug(true, "Fresh set has "+freshSet.keySet().size()+" members");
			Iterator labelit = label2Rule.keySet().iterator();

			while (labelit.hasNext()) {
				Symbol label = (Symbol)labelit.next();

				if (debug) Debug.debug(debug, "About to consider the label "+label.toString());
				if (!freshSet.keySet().contains(label)) {
					if (debug) Debug.debug(debug, label.toString()+" is unfresh. Not processing");
					continue;
				}
				//		if (label.equals(nullsym))
				//		    debug = true;

				//		HashSet labelRules = (HashSet)label2Rule.get(label);

				// only look at the fresh rules

				// restored
				// TODO: restore this efficiency
				//		HashSet labelRules = (HashSet)label2Rule.get(label);
				HashSet labelRules = (HashSet)freshSet.get(label);

				// until further notice, no rules with this label are fresh
				freshSet.remove(label);

				// Hashmap for tracking scores for each unique vector that has this label
				Hashtable vectorMap = new Hashtable();
				// this one just for comparison
				HashSet fullLabelRules = (HashSet)label2Rule.get(label);
				if (debug && labelRules.size() < fullLabelRules.size()) {
					Debug.debug(true, label.toString()+" has "+labelRules.size()+" rules fresh of the "+fullLabelRules.size()+" total ");
				}
				Iterator lrit = labelRules.iterator();
				// ruleCandidates maps each rule to the hashmap with candidates
				// this is because we will use this info again to determine destination
				// state and residual weights.
				Hashtable ruleCandidates = new Hashtable();
				//		debug = true;
				while (lrit.hasNext()) {
					// go over each rule and determine the 
					// set of states that can serve in each position. This will
					// determine the number of vectors that can have this particular rule
					RTGRule labelRule = (RTGRule)lrit.next();
					//		    if (debug) Debug.debug(debug, "Determining states for "+labelRule.toString());
					// we only want rules that have some state in them
					if (labelRule.getRHS().numChildren == 0) {
						if (!states.contains(labelRule.getRHS().label))
							continue;
					}
					else {
						boolean isGood = false;
						for (int i = 0; i < labelRule.getRHS().numChildren; i++) {
							if (states.contains(labelRule.getRHS().children[i].label)) {
								isGood = true;
								break;
							}
						}
						if (!isGood)
							continue;
					}


					// build the candidates for each position. Map top position
					Hashtable candidates = new Hashtable();
					int possibilities = 1;
					// treat 0 children (state-state) like 1 child
					if (labelRule.getRHS().numChildren == 0) {
						// options has the valid states for position i
						HashSet options = new HashSet();
						nsscit = newstates.iterator();
						while (nsscit.hasNext()) {
							NewStateSet nshm = (NewStateSet)nsscit.next();
							if (nshm.keySet().contains(labelRule.getRHS().label))
								options.add(nshm);
						}
						possibilities *= options.size();
						candidates.put(new Integer(0), options);
					}
					else {
						for (int i = 0; i < labelRule.getRHS().numChildren; i++) {
							// options has the valid states for position i
							HashSet options = new HashSet();
							nsscit = newstates.iterator();
							while (nsscit.hasNext()) {
								NewStateSet nshm = (NewStateSet)nsscit.next();
								if (nshm.keySet().contains(labelRule.getRHS().children[i].label))
									options.add(nshm);
							}
							possibilities *= options.size();
							candidates.put(new Integer(i), options);
						}
					}
					//		    if (newstatessize > 2)
					//			debug = true;
//							    debug = true;
					if(debug && possibilities > 0) Debug.debug(true, possibilities+" different vectors for rule "+labelRule.toString());
	//				debug = false;
					HashSet stateVectors = new HashSet();

					// track rejectedvectors
					int rejectVectorCount = 0;
					// again, do it differently for 0 children

					// sanity check!
					// rnsit = currentRecentNewStates.iterator();
					// 		    Debug.debug(true, "Recently added new states: ");
					// 		    while (rnsit.hasNext()) {
					// 			Debug.debug(true, ((NewStateSet)rnsit.next()).toString());
					//                  }

					if (timeout("weightedDeterminize: bailing out after gathering "+possibilities+" possibilities"))
						return false;

					if (labelRule.getRHS().numChildren == 0)
						rejectVectorCount = fillWithRelevantVectors(stateVectors, candidates, currentRecentNewStates, 1);
					// far fewer vectors than before...but perhaps this has to be done many times now?
					else
						rejectVectorCount = fillWithRelevantVectors(stateVectors, candidates, currentRecentNewStates, labelRule.getRHS().numChildren);

					if (timeout("weightedDeterminize: bailing out after "+possibilities+" possibilities led to "+stateVectors.size()+" vectors"))
						return false;


					if (debug && stateVectors.size() > 0) Debug.debug(true, "Done filling with vectors");
					if (debug && rejectVectorCount > 0)	Debug.debug(true, "Rejected "+rejectVectorCount+" old vectors and have "+stateVectors.size()+" with rule "+labelRule.toString());
					//		    if (newstatessize > 2)
					//			debug = false;
					// archive the rule->vectors mapping
					// note: if this is too much data to store, we can always just store the
					// candidates hash, then expand later
					ruleCandidates.put(labelRule, stateVectors);
					Iterator svit = stateVectors.iterator();
					//		    if (!trueLabelRules.contains(labelRule) && stateVectors.size() > 0) {
					//			Debug.debug(true, "Non-fresh rule: "+labelRule.toString());
					//  }

					if (debug && stateVectors.size() > 0) Debug.debug(true, "About to process "+stateVectors.size()+" vectors");
					while (svit.hasNext()) {
						if (timeout("weightedDeterminize: bailing out during calculation of "+stateVectors.size()+" vectors"))
							return false;

						// Now we want to calculate the score: weight of rule + residual of all elements used
						StateVector v = (StateVector)svit.next();
						// 			if (!trueLabelRules.contains(labelRule)) {
						// 			    Debug.debug(true, "\t Processing vector "+v.toString());
						// 			    for (int i = 0; i < v.size(); i++) {
						// 				if (currentRecentNewStates.contains(v.elementAt(i))) {
						// 				    Debug.debug(true, "\t\t Item "+i+" is current");
						//                              }
						// 			    }
						// 			}
						if (debug) Debug.debug(debug, "\tFor "+v.toString());
						double tempScore = semiring.ONE();
						if (debug) Debug.debug(debug, "\t\tStarting score at "+tempScore);
						// state-state case
						if (labelRule.getRHS().numChildren == 0) {
							tempScore = semiring.times(tempScore, ((Pair)((NewStateSet)v.elementAt(0)).get(labelRule.getRHS().label)).weight);
							if (debug) Debug.debug(debug, "\t\tChild changed tempScore to "+tempScore);
						}
						// normal case
						else {
							for (int i = 0; i < labelRule.getRHS().numChildren; i++) {
								// state is in proper vector position
								tempScore = semiring.times(tempScore, ((Pair)((NewStateSet)v.elementAt(i)).get(labelRule.getRHS().children[i].label)).weight);
								if (debug) Debug.debug(debug, "\t\tChild changed tempScore to "+tempScore);
							}
						}
						tempScore = semiring.times(tempScore, labelRule.getWeight());
						if (debug) Debug.debug(debug, "\t\tRule weight of "+labelRule.getWeight()+" changed tempScore to "+tempScore);
						// now we want to compare this to other values of this label from this vector
						if (vectorMap.containsKey(v)) {
							double score = ((Double)vectorMap.get(v)).doubleValue();
							if (debug) Debug.debug(debug, "\t\tPrevious score was "+score);
							score = semiring.plus(score, tempScore);
							if (debug) Debug.debug(debug, "\tNew score is "+score);
							vectorMap.put(v, new Double(score));
						}
						else {
							vectorMap.put(v, new Double(tempScore));
							if (debug) Debug.debug(debug, "\tNo previous score, score is "+tempScore);
						}
					}
					if (debug && stateVectors.size() > 0) Debug.debug(true, "...done");
					//		    debug = false;
				}
				// so now vectorMap has the appropriate weight for the hyperedge from each vector with label
				// to somewhere. We need to construct that somewhere, by creating new states and adding the
				// residuals in. Go through the ruleCandidates to do this

				// calculating the residual: residual is based on the original state reached. it's 
				// calculated for each (initial state vector, label, destination state) tuple. going rule
				// by rule, we get different bits for one vector from different rules, potentially, so this has
				// to be stored in a hash like before

				// this map goes from vector to a map going from final state to score
				Hashtable residualMap = new Hashtable();
				// notice that lrit is now just the rule candidates, so there's a mapping to a set of vectors
				//		debug = true;
				lrit = ruleCandidates.keySet().iterator();
				//		debug = true;
				if (debug) Debug.debug(debug, "About to process "+ruleCandidates.keySet().size()+" rule candidates for residuals");
				//		debug = false;
				long timea = System.currentTimeMillis();
				if (timeout("weightedDeterminize: bailing out before calculating residuals for "+ruleCandidates.keySet().size()+" rules"))
					return false;

				while (lrit.hasNext()) {
					RTGRule labelRule = (RTGRule)lrit.next();
					if (debug) Debug.debug(debug, "Calculating residuals for "+labelRule.toString());
					// rule candidates actually has the vectors, not the compressed choices
					HashSet stateVectors = (HashSet)ruleCandidates.get(labelRule);
					Iterator svit = stateVectors.iterator();
					while (svit.hasNext()) {

						// Now we want to calculate the residual: 
						// weight of rule * residual of all elements used * inverse of score on the arc
						StateVector v = (StateVector)svit.next();

						if (timeout("weightedDeterminize: bailing out before calculating residuals for vector "+v.toString()+
								" under rule "+labelRule.toString()+", one of "+stateVectors.size()+" vectors and one of "+
								ruleCandidates.keySet().size()+" rules"))
							return false;

						if (debug) Debug.debug(debug, "\tFor "+v.toString());
						double tempScore = semiring.ONE();
						if (debug) Debug.debug(debug, "\t\tStarting residual at "+tempScore);
						// state-state case
						if (labelRule.getRHS().numChildren == 0) {
							tempScore = semiring.times(tempScore, ((Pair)((NewStateSet)v.elementAt(0)).get(labelRule.getRHS().label)).weight);
							if (debug) Debug.debug(debug, "\t\tChild changed tempScore to "+tempScore);
						}
						// normal case
						else {
							for (int i = 0; i < labelRule.getRHS().numChildren; i++){
								tempScore = semiring.times(tempScore, 
										((Pair)(((NewStateSet)v.elementAt(i)).get(labelRule.getRHS().children[i].label))).weight);
								if (debug) Debug.debug(debug, "\t\tChild changed tempScore to "+tempScore);
							}
						}
						tempScore = semiring.times(tempScore, labelRule.getWeight());
						if (debug) Debug.debug(debug, "\t\tRule weight of "+labelRule.getWeight()+" changed tempScore to "+tempScore);
						if (!doBorchardt) {
							tempScore = semiring.times(tempScore, 
									semiring.inverse(((Double)vectorMap.get(v)).doubleValue()));
							if (debug) Debug.debug(debug, "\t\tInverse of new residual "+(((Double)vectorMap.get(v)).doubleValue())+" changed tempScore to "+tempScore);
						}
						// store this vector, lhs(i.e. dst state) -> score, possibly updating
						if (residualMap.containsKey(v)) {
							Hashtable resVecMap = (Hashtable)residualMap.get(v);
							if (resVecMap.containsKey(labelRule.getLHS())) {
								double score = ((Double)resVecMap.get(labelRule.getLHS())).doubleValue();
								if (debug) Debug.debug(debug, "\t\tPrevious residual (for "+labelRule.getLHS().toString()+" was "+score);
								score = semiring.plus(score, tempScore);
								if (debug) Debug.debug(debug, "\tNew residual is "+score);
								resVecMap.put(labelRule.getLHS(), new Double(score));
							}
							else {
								resVecMap.put(labelRule.getLHS(), new Double(tempScore));
								if (debug) Debug.debug(debug, "\tNo previous residual for "+labelRule.getLHS().toString()+", residual is "+tempScore);
							}
							residualMap.put(v, resVecMap);
						}
						else {
							Hashtable resVecMap = new Hashtable();
							resVecMap.put(labelRule.getLHS(), new Double(tempScore));
							residualMap.put(v, resVecMap);
						}
					}
					if (debug) Debug.debug(debug, "Residual map has "+residualMap.size()+" members");
				}
				//		debug = false;
				long timeb = System.currentTimeMillis();
				//		debug = true;
				if (debug) Debug.debug(debug, "...done: "+(timeb-timea)+" ms");
				//		debug = false;
				// now for each vector we can form a new state, consisting of the label, residual pairs
				// and add that new state 
				Iterator svit = residualMap.keySet().iterator();
				if (debug) Debug.debug(debug, "About to form new states from "+residualMap.keySet().size()+" vectors");
				//		debug = false;
				timea = System.currentTimeMillis();
				int counter = 0;
				while(svit.hasNext()) {
					StateVector v = (StateVector)svit.next();
					NewStateSet newState = new NewStateSet();
					// m is string -> Double mappings that go into the newstate
					Hashtable m = (Hashtable)residualMap.get(v);
					Iterator dstit = m.entrySet().iterator();
					while (dstit.hasNext()) {
						Map.Entry me = (Map.Entry)dstit.next();
						// make the pair
						Pair p = new Pair((Symbol)me.getKey(), (Double)me.getValue(), semiring);
						newState.put((Symbol)me.getKey(), p);
						if (startState.equals(me.getKey()))
							newState.containsStart = true;
					}
					// new state gets added to the pending bin
					// we won't see it until next iteratioin, but any rule that could use it will be made 
					// fresh before this iteration, so no need to worry!
					recentNewStates.add(newState);

					// a rule can be made. It might be redundant, but that's okay!

					// left side of rule: the new state we just created.
					TreeItem t = null;
					// nullsym case:
					// right side of rule: the child of the tree as a label
					if (label.equals(nullsym))
						t = new TreeItem(getStateName((NewStateSet)v.elementAt(0)));

					// normal case:
					// right side of rule: the old label of the tree, plus children
					// that are the states we followed to get here.
					else {
						t = new TreeItem(label);
						// each member of v is a child in the tree, so form a state name out of them:
						for (int i = 0; i < v.size(); i++)
							t.addChild(new TreeItem(getStateName((NewStateSet)v.elementAt(i))));
					}

					RTGRule r = new RTGRule(this, getStateName(newState), t, doBorchardt ? semiring.ONE() : ((Double)vectorMap.get(v)).doubleValue(), semiring);
					counter++;
					newrules.add(r);
					if (debugcount && newrules.size() % countiter == 0) {
						Debug.prettyDebug("Added "+newrules.size()+" rules");
					}
					if (debug) Debug.debug(debug, "Adding rule "+r.toString());

					if (timeout("weightedDeterminize: bailing out while adding rule "+r.toString()+"; one of "+
							residualMap.keySet().size()))
						return false;

				}
				//		debug = true;
				if (debug) Debug.debug(debug, "...done");
				timeb = System.currentTimeMillis();
				if (debug) Debug.debug(debug, (timeb-timea)+" ms for "+counter+" adds");
				//				if ((timeb-timea) > 2000)
				//				    System.exit(0);
			}
			// all labels and rules have been considered. Now go through the recentNewStates
			// and determine freshness and integrate with newStates!
//				    debug = true;
			if (debug) Debug.debug(debug, "End of iteration. About to consider "+recentNewStates.size()+" states for freshening");
	//			    debug = false;

			// dump any states in recentNewStates that already exist
			recentNewStates.removeAll(newstates);

			Iterator nsit = recentNewStates.iterator();
			while (nsit.hasNext()) { 
				NewStateSet newState = (NewStateSet)nsit.next();
				newstates.add(newState);
				if (debugcount && newstates.size() % countiter == 0) {
					Debug.prettyDebug("Added "+newstates.size()+" states");
				}
				if (newState.containsStart)
					newStartStates.add(newState);
				Iterator keyit = newState.keySet().iterator();
				while (keyit.hasNext()) {
					Symbol s = (Symbol)keyit.next();
					// fmap is all the labels this state is involved in
					// for each label it's all the rules
					Hashtable fMap = (Hashtable)state2Label.get(s);
					if (fMap == null) {
						if (debug) Debug.debug(debug, "No labels from freshened state "+s.toString());
						continue;
					}

					if (timeout("weightedDeterminize: bailing out while freshening rules for "+s.toString()))
						return false;

					Iterator fsit = fMap.keySet().iterator();
					while (fsit.hasNext()) {
						Symbol mapLabel = (Symbol)fsit.next();
						// union with existing entries in freshset for this label
						// or, if there are none, set what we've got
						if (!freshSet.containsKey(mapLabel))
							freshSet.put(mapLabel, new HashSet());
						((HashSet)freshSet.get(mapLabel)).addAll((HashSet)fMap.get(mapLabel));
					}
				}
			}
			//	    Debug.debug(true, newstatessize+" vs "+newstates.size());
		} while (newstatessize != newstates.size());
		if (newStartStates.size() == 1 && !doBorchardt) {
			Symbol ns = getStateName(((NewStateSet)newStartStates.iterator().next()));
			startState = ns;
		}
		else {
			// add state-to-state rules to cover the case of multiple start states
			// weight is the start state component of each state
//			Debug.debug(true, "WARNING: number of new start states is "+newStartStates.size());
			Iterator nssit = newStartStates.iterator();
			while (nssit.hasNext()) {
				NewStateSet nss = (NewStateSet)nssit.next();
				RTGRule r = new RTGRule(this, startState, new TreeItem(getStateName(nss)), ((Pair)nss.get(startState)).weight, semiring);
				newrules.add(r);
				if (debug) Debug.debug(debug, "Adding rule "+r);
//				Debug.debug(true, nss.toString()+" : "+getStateName(nss).toString());
			}
		}

		//	rules = newrules;
		//	Debug.debug(true, "About to form a hash set from a vector of "+newrules.size()+" rules");
		rules = new ArrayList<Rule>(newrules);
		//	Debug.debug(true, "Done. rules are now "+rules.size());

		initialize();
		pruneUseless();
		epsRemoved = false;
		return true;
	}


	// fillWithRelevantVectors: given a hashset and a map of position to newstateset objects,
	// and, for convenience, the number of total positions
	// create all possible vectors and put them in the hashset
	// also given a set of criticals. At least one critical must be present
	// return the number of vectors avoided by the critical test
	// TODO: although the stuff here changes quite a bit, can this be memoized?

	public int fillWithRelevantVectors(HashSet set, Hashtable map, HashSet criticals, int size) {
		return recursiveFillWithRelevantVectors(set, map, criticals, new StateVector(), 0, size);
	}

	// the thing that does the function above
	private int recursiveFillWithRelevantVectors(HashSet fillSet, Hashtable map, HashSet criticals,
			StateVector v, int currSize, int maxSize) {
		if (currSize == maxSize) {
			//	    Debug.debug(true, "Fill with relevant vectors: adding "+v.toString());
			for (int i = 0; i < maxSize; i++) {
				if (criticals.contains(v.elementAt(i))) {
					fillSet.add(v);
					return 0;
				}
			}


			// criticals not found!
			// 	    System.err.print("Skipping {");
			// 	    for (int i = 0; i < maxSize; i++)
			// 		System.err.print(((NewStateSet)v.elementAt(i)).toString()+" ");
			// 	    Debug.debug(true, "}");
			//fillSet.add(v);

			return 1;

		}
		else {
			HashSet getSet = (HashSet)map.get(new Integer(currSize));
			Iterator it = getSet.iterator();
			int critCount = 0;
			while (it.hasNext()) {
				StateVector newv = new StateVector(v);
				newv.add(it.next());
				critCount += recursiveFillWithRelevantVectors(fillSet, map, criticals, newv, currSize+1, maxSize);
			}
			return critCount;
		}
	}





	// a dangerous way to construct the name of the state:
	// update: now a less dangerous and more readable way
	private Symbol getStateName(NewStateSet state) {
		if (determNames.containsKey(state))
			return (Symbol)determNames.get(state);
		//	Symbol name = SymbolFactory.getSymbol(state.toString());
		Symbol name = SymbolFactory.getStateSymbol("ns"+(nextDeterm++));
		determNames.put(state, name);
		return name;
	}


	// given a set of "new" states, determine the longest number of arguments in any rule these
	// states are involved in
	private int findRank(HashSet set, Hashtable map) {
		Iterator sit = set.iterator();
		int retval = 0;
		while (sit.hasNext()) {
			NewStateSet state = (NewStateSet)sit.next();
			Iterator stit = state.keySet().iterator();
			while (stit.hasNext()) {
				Symbol stateSym = (Symbol)stit.next();
				if (map.containsKey(stateSym))
					retval = Math.max(retval, ((Integer)map.get(stateSym)).intValue());
			}
		}
		return retval;
	}

	// add vectors up to a certain length that contain elements of a set into a set
	private void fillWithVectors(HashSet fillSet, HashSet getSet, int maxSize) {
		for (int i = 1; i <= maxSize; i++) {
			//	    Debug.debug(true, "About to fill with vectors of size "+i);
			recursiveFillWithVectors(fillSet, getSet, new Vector(), 0, i);
			//	    Debug.debug(true, "Done filling with vectors of size "+i+": "+fillSet.size()+" total vectors so far");
		}
	}

	// add a particular member of a vector, then pass to the next level. insert if done
	private void recursiveFillWithVectors(HashSet fillSet, HashSet getSet, Vector v, int currSize, int maxSize) {
		if (currSize == maxSize) {
			//	    System.err.print("Fill with vectors: adding [");
			//	    for (int i = 0; i < v.size(); i++)
			//		System.err.print(getNewState((NewStateSet)v.elementAt(i)) + ", ");
			//	    Debug.debug(true, "]");
			fillSet.add(v);
		}
		else {
			Iterator it = getSet.iterator();
			while (it.hasNext()) {
				Vector newv = new Vector(v);
				newv.add(it.next());
				recursiveFillWithVectors(fillSet, getSet, newv, currSize+1, maxSize);
			}
		}
	}


	// prune useless - two-pass algorithm, based on knuth. First go bottom up and mark all states
	// that can be used to reach terminal symbols. Then go top-down and mark all states that can
	// be reached from the start symbol. Only keep states that satisfy both criteria.

	// This is a "strong" prune useless, in that a rule with weight of semiring.ZERO is considered
	// not to exist.

	public void pruneUseless() {
		boolean debug = false;
		HashSet bottomReachable = new HashSet();
		// phase 1: bottom up
		// for each state not already reachable, try and add it

		int brSize = 0;
		do {
			brSize = bottomReachable.size();
			Iterator stit = states.iterator();
			while (stit.hasNext()) {
				Symbol currState = (Symbol)stit.next();
				// OLD: do not consider star state
				//if (currState == Symbol.getStar())
				//	continue;
				if (debug) Debug.debug(debug, "BU: Considering state "+currState.toString());
				if (bottomReachable.contains(currState))
					continue;
				ArrayList<Rule> currRules = getRulesOfType(currState);
				if (currRules == null)
					continue;
				if (debug) Debug.debug(debug, "\t"+currRules.size()+" rules");
				Iterator rit = currRules.iterator();
				// look for at least one valid rule
				while (rit.hasNext()) {
					RTGRule currRule = (RTGRule)rit.next();
					if (debug) Debug.debug(debug, "\t\t"+currRule.toString());

					// CURRENTLY DISABLED -- too slow. Hope it doesn't hurt?!
					// sometimes this is run when ruleset is in an uncertain state. trust rules list above rulesoftype
					// 		    // list

					// 		    if (!rules.contains(currRule)) {
					// 			if (debug) Debug.debug(debug, "\t\tNot in true rule set!");
					// 			continue;
					// 		    }

					// rule doesn't count if it has zero weight
					if (semiring.betteroreq(semiring.ZERO(), currRule.getWeight())) {
						if (debug) Debug.debug(debug, "\t\tZero weight!");
						continue;
					}
					Symbol[] leaves = currRule.getRHSLeaves();
					boolean isOkay = true;
					// check that all leaves are either terms or already seen
					for (int i = 0; i < leaves.length; i++) {
						if (states.contains(leaves[i]) && 
								//leaves[i] != Symbol.getStar() && 
								!bottomReachable.contains(leaves[i])) {
							isOkay = false;
							break;
						}
					}
					if (isOkay) {
						if (debug) Debug.debug(debug, "BU: "+currRule.getLHS().toString());
						if (debug) Debug.debug(debug, "\t thanks to "+currRule.toString());
						bottomReachable.add(currRule.getLHS());
						break;
					}
				}
			}
			if (debug) Debug.debug(debug, "Gone from "+brSize+" to "+bottomReachable.size());
		} while (brSize < bottomReachable.size());

		// phase 2: top down
		// starting with the start state (if it's bottom-reachable), 
		// find each state that can be reached in a downward direction
		// more specifically, find each rule that applies
		ArrayList<Rule> checkedRules = new ArrayList<Rule>();
		HashSet checkedStates = new HashSet();
		Stack readyStates = new Stack();
		if (bottomReachable.contains(startState))
			readyStates.push(startState);
		while (readyStates.size() > 0) {
			Symbol currState = (Symbol)readyStates.pop();
			// OLD: do not consider star state
			// if (currState == Symbol.getStar())
			// 	continue;
			if (debug) Debug.debug(debug, "TD: "+currState.toString());
			checkedStates.add(currState);
			ArrayList<Rule> currRules = getRulesOfType(currState);
			if (currRules == null)
				continue;
			Iterator rit = currRules.iterator();
			// look for at least one valid rule
			while (rit.hasNext()) {
				RTGRule currRule = (RTGRule)rit.next();

				// CURRENTLY DISABLED -- too slow. Hope it doesn't hurt?!
				// sometimes this is run when ruleset is in an uncertain state. trust rules list above rulesoftype
				// list
				// 		if (!rules.contains(currRule))
				// 		    continue;
				// rule doesn't count if it has zero weight
				if (semiring.betteroreq(semiring.ZERO(), currRule.getWeight()))
					continue;		
				Symbol[] leaves = currRule.getRHSLeaves();
				boolean isOkay = true;
				// check that all leaves are either terms, star,  or already seen
				for (int i = 0; i < leaves.length; i++) {
					if (states.contains(leaves[i]) && 
						//	leaves[i] != Symbol.getStar() && 
							!bottomReachable.contains(leaves[i])) {
						isOkay = false;
						break;
					}
				}
				// valid rules inspire other states to check
				if (isOkay) {
					if (debug) Debug.debug(debug, "\t Adding "+currRule.toString());
					checkedRules.add(currRule);
					for (int i = 0; i < leaves.length; i++) {
						if (states.contains(leaves[i]) && 
							//	leaves[i] != Symbol.getStar() &&
								!checkedStates.contains(leaves[i]) &&
								!readyStates.contains(leaves[i])) {
							readyStates.push(leaves[i]);
						}
					}
				}
			}
		}
		if (debug) {
			Debug.debug(debug, "Rules after pruning:");
			Iterator crit = checkedRules.iterator();
			while (crit.hasNext()) {
				RTGRule crr = (RTGRule)crit.next();
				Debug.debug(debug, crr.toString());
			}
		}
		rules = checkedRules;
		initialize();
	}

	// memoized function originally from Knuth (bad location for it)
	// returns and memoizes the leaves of a rule that are states
	// in general some bad coding...
	public Vector<Symbol> getLeafChildren(Rule r) {
		if (!leafChildren.containsKey(r)) {
			Vector<Symbol> v = new Vector<Symbol>();
			Symbol [] syms = ((RTGRule)r).getRHSLeaves();
			for (int i = 0; i < syms.length; i++) {
				if (states.contains(syms[i]))
					v.add(syms[i]);
			}
			leafChildren.put(r, v);
		}
		return leafChildren.get(r);
	}


	// for rtg training.
	// TODO: memoize!!
	public HashSet getTrainingRules(Symbol s, Item item) throws UnexpectedCaseException{
		HashSet retSet = new HashSet();
		ArrayList<Rule> currRules = getRulesOfType(s);
		if (currRules == null)
			throw new UnexpectedCaseException("No rules for "+s.toString());
		Iterator rit = currRules.iterator();
		while (rit.hasNext()) {
			RTGRule r = (RTGRule)rit.next();
			//	    Debug.debug(true, "Checking "+r.toString()+" for "+s.toString()+", "+item.toString()+", "+otree.toString());
			if (r.isItemMatch(s, item, this)) {
				//		Debug.debug(true, "Success!");
				retSet.add(r);
			}
		}
		return retSet;
	}

	// cf CFGRuleSet
	// states for Earley parsing
		
	static int nextauto = 0;
	
	
/*	private Hashtable<Symbol, Integer> s2i;
	private Hashtable<Integer, Symbol> i2s;
	class RTGEarleyState {
		StringTransducerRule rule;
		int rulepos;
		int stringStartPos;
		int stringEndPos;
		// integer alias for src and dst states
		// keyed on s2i, i2s
		int src;
		int[] dst;
		// isFinished means have we moved the pointer through the rule
		boolean isFinished;
		// avoid checking lists with these flags
		// isDone means have we expanded this state
		boolean isDone;
		boolean isTodo;
		Vector<RTGEarleyState[]> next;
		// equality stuff
		private Hash hsh=null;
		public int hashCode() { 
			if (hsh == null) {
				setHashCode();
			}
			return hsh.bag(Integer.MAX_VALUE);
		}

		private void setHashCode() {
			hsh = new Hash(rule.getHash());
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
			RTGEarleyState r = (RTGEarleyState)o;
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
		RTGEarleyState(StringTransducerRule r, int rp, int ssp, int sep) {
			rule = r;
			rulepos = rp;
			stringStartPos = ssp;
			stringEndPos = sep;
			// create src and dst items for later quick access
			src = s2i.get(r.state);
			dst = new int[rule.getRHS().getItemLeaves().size()];
			int counter = 0;
			for (TransducerRightString i : rule.getRHS().getItemLeaves()) {
				if (i.hasState())
					dst[counter] = s2i.get(i.getState());
				else
					dst[counter] = -1;
				counter++;
			}
			if (rulepos >= (rule.getRHS().getSize()))
				isFinished = true;
			isDone=isTodo=false;
			next = null;
			// point top-down	
		}
		// which, if any, nonterms should come next?
		// consider the label match if need be, too
		int predict(Symbol sym) {
			if (isFinished)
				return -1;
			// are we pointing at a member with a state?
			if (dst[rulepos] >= 0) {
				// check that labels match too, if this is specified
				if (rule.getRHS().getItemLeaves().get(rulepos).hasLabel()) {
					if (rule.getRHS().getItemLeaves().get(rulepos).getLabel() == sym)
						return dst[rulepos];
					else
						return -1;
				}
				else 
					return dst[rulepos];
			}
			else
				return -1;
		}

		// attempt to shift based on provided symbol
		// edge gets added in calling method
		RTGEarleyState shift(Symbol sym) {
			boolean debug = false;
			if (isFinished)
				return null;
			// handle epsilon rules too
			if (rule.rhs.isEpsilon()) {
				if (debug) Debug.debug(debug, "Found epsilon rule "+rule+"; acting accordingly");
				return new RTGEarleyState(rule, rulepos+1, stringStartPos, stringEndPos);
			}
			if (sym == Symbol.getEpsilon())
				return null;
			Symbol cmp = rule.getRHS().getItemLeaves().get(rulepos).getLabel();
			if (cmp == sym)
				return new RTGEarleyState(rule, rulepos+1, stringStartPos, stringEndPos+1);
			return null;
		}
		// add an edge to the state
		public void update(RTGEarleyState edge) {
			if (next == null)
				next = new Vector<RTGEarleyState[]>();
			
			RTGEarleyState[] v = new RTGEarleyState[2];
			v[0] = edge;
			next.add(v);
		}
		public void update(RTGEarleyState edge1, RTGEarleyState edge2) {
			if (next == null)
				next = new Vector<RTGEarleyState[]>();
			RTGEarleyState[] v = new RTGEarleyState[2];
				v[0] = edge1;
				v[1] = edge2;
			next.add(v);
		}
		public String toString() {
			StringBuffer str = new StringBuffer(rule+":"+rulepos+":"+stringStartPos+":"+stringEndPos);
			if (next != null) {
			//	if (next.size() > 3)
					str.append("["+next.size()+" children "+"]");
//				else {
//					for (RTGEarleyState[] v : next) {
//						str.append("\n[");
//						for (RTGEarleyState st : v)
//							if (st != null)
//								str.append("("+st.rule+")");
//						str.append("]");
//					}
//				}
			}
			else {
				str.append("[null]");
			}
			return str.toString();
		}
		// traverse through the chart from a single state, creating new states etc. as need be
		// look at lhs of rule when it is called for and create a rtg rule from it
//		public Vector<RTGRule> buildRTG(HashMap<Symbol, HashMap<Integer, HashMap<Integer,Symbol>>> newSyms, 
		public Vector<RTGRule> buildRTG(
				Symbol[][][] newSyms, 
				ArrayList<RTGEarleyState> todoList,
				RTGRuleSet parent) throws ImproperConversionException {
			boolean debug = true;
			// there used to be a done list, but this flag is good enough
			this.isDone = true;
			// get the new state symbol, either by lookup or recreation
			Symbol newstate;
			if (newSyms[src][stringStartPos][stringEndPos] != null)	{
				newstate = newSyms[src][stringStartPos][stringEndPos];
				if (debug) Debug.debug(debug, "Found "+rule+"->"+stringStartPos+"->"+stringEndPos+"->"+newstate);
			}
			else {
				throw new ImproperConversionException("Couldn't find state for "+rule+":"+stringStartPos+":"+stringEndPos+
						"; these should be created in getPossibleSymbols or top level constructor");	
			}
			if (debug) Debug.debug(debug, "Gathering RTG rules to explain "+rule+" from "+stringStartPos+" to "+stringEndPos);
			Vector<RTGRule> ret = new Vector<RTGRule>();


			Vector<Vector<Symbol>> stateseqs = new Vector<Vector<Symbol>>();
			stateseqs.add(new Vector<Symbol>());
			//debug = true;
			if (debug) Debug.debug(debug, "Gathering possible symbols for "+this);
			stateseqs = getPossibleSymbols(this, stateseqs, newSyms, todoList, 0);
		//	for (Vector<Symbol> ssq : stateseqs)
			//	if (debug) Debug.debug(debug, ssq+"");
			//debug = false;
			// add new rtg rule with substituted states

			// so that we can appropriately map between rhs and lhs, get all rhs variables in the order they occur
			ArrayList<TransducerRightSide> rhsvars = rule.trvm.getRHSInOrder();
			
			for (Vector<Symbol> newrhs : stateseqs) {
				RTGRule newRule=null;
				Hashtable<TransducerRightSide, Symbol> varMap = new Hashtable<TransducerRightSide, Symbol>();
				for (int i = 0; i < rhsvars.size(); i++) {
					if (debug) Debug.debug(debug, "Mapping "+rhsvars.get(i)+" to "+newrhs.get(i)+ " in "+rule);
					varMap.put(rhsvars.get(i), newrhs.get(i));
				}
				try {
					if (debug) Debug.debug(debug, "Building from "+rule+" and "+varMap+" and "+newstate);
					newRule = new RTGRule(parent, newstate, rule, varMap);
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
		
		// recursive revision of getPossibleSymbols that doesn't do index matching and hopefully
		// traverses the chart smartly
		
		// for each item in currstate.next
		// 	   for each vector in existing
		//     		if item[1] exists, add it to vector and to Todo, if relevant
		//     add getPossibleSymbols(item[0], adjoined vectors) to return vector
		//     vector of states only are returned. caller should insert literals where appropriate.
		private Vector<Vector<Symbol>> getPossibleSymbols(
				RTGEarleyState currState, 
				Vector<Vector<Symbol>> existing, 
				Symbol[][][] newSyms, 
				ArrayList<RTGEarleyState> todoList,
				int level) {
			boolean debug = false;
			if (debug) Debug.debug(debug, level, "Exploring "+currState);
			if (currState.next == null)
				return existing;
			Vector<Vector<Symbol>> ret = new Vector<Vector<Symbol>>();
			//			int oldrhssym = currState.src;
			for (RTGEarleyState[] choice : currState.next) {
				// tempret gets passed downward recursively
				Vector<Vector<Symbol>> tempret; 
				if (choice[1] == null) {
					tempret = existing;
				}
				else {
					tempret = new Vector<Vector<Symbol>>();
					for (Vector<Symbol> oldmember : existing) {
						Vector<Symbol> newmember = new Vector<Symbol>(oldmember);
						RTGEarleyState child = choice[1];
						int rhssym = child.src;
						// access symbol related to state, start, end
						if (newSyms[rhssym][child.stringStartPos][child.stringEndPos] == null) 
							newSyms[rhssym][child.stringStartPos][child.stringEndPos] = SymbolFactory.getStateSymbol(rhssym+":"+child.stringStartPos+":"+child.stringEndPos);						
						newmember.add(0, newSyms[rhssym][child.stringStartPos][child.stringEndPos]);
						if (!child.isDone && !child.isTodo) {
							if (debug) Debug.debug(debug, level, "Adding "+child+" to todo list");
							child.isTodo = true;
							todoList.add(child);
						}
						tempret.add(newmember);
					}
				}
				// recursive call!
				if (choice[0] != null)
					tempret = getPossibleSymbols(choice[0], tempret, newSyms, todoList, level+1);
				ret.addAll(tempret);
			}
			return ret;
		}

	}
	*/
	
	
/*	// do earley parsing on the provided TST and StringItem, returning a RTG that produces this string
	public RTGRuleSet(StringTransducerRuleSet trs, StringItem string) throws ImproperConversionException {
		boolean debug = false;
		semiring = trs.semiring;
		nextRuleIndex = 0;
		s2i = new Hashtable<Symbol, Integer>();
		i2s = new Hashtable<Integer, Symbol>();
		// integer mapping to and from xrs states used by backwards application
		int nextState=0;
		for (Symbol s : trs.states) {
			s2i.put(s, nextState);
			i2s.put(nextState++, s);
		}
		ArrayList<RTGEarleyState> agenda = new ArrayList<RTGEarleyState>();
		int agendaSize=0;
		// track all states that have been added to an agenda. map from the state with no next pointers
		// to the state with all next pointers (which is the state actually added into the agenda) and update
		// the latter. 
		
		HashMap<RTGEarleyState, RTGEarleyState> addedStates = new HashMap<RTGEarleyState,RTGEarleyState>();
		int addedStatesSize=0;
		// categorize chart nodes by finished and unfinished variants

		// index by xrs state id and string pos
		HashSet<RTGEarleyState>[][] finishedChart = new HashSet[trs.states.size()][string.getSize()+1];
		
		HashSet<RTGEarleyState>[][] unfinishedChart = new HashSet[trs.states.size()][string.getSize()+1];
		int chartSize=0;

		// track prediction items handled
		HashMap<Integer, HashSet<Integer>> preds = new HashMap<Integer, HashSet<Integer>>();
		try {
			// initialize agenda
			for (TransducerRule rule : trs.getRulesOfType(trs.getStartState())) {
				RTGEarleyState es = new RTGEarleyState((StringTransducerRule) rule, 0, 0, 0);
				if (debug) Debug.debug(debug, "Initializing agenda with "+es);
				agenda.add(es);
				agendaSize++;
				addedStates.put(es, es);
				addedStatesSize++;
			}
			HashSet<Integer> startint = new HashSet<Integer>();
			startint.add(0);
			preds.put(s2i.get(trs.getStartState()), startint);
			while (agenda.size() > 0) {
//				if (agendaSize % 100 == 0) { Debug.prettyDebug("Agenda has "+agendaSize+" items; "+addedStatesSize+" states"); }
				RTGEarleyState item = agenda.remove(0);
				agendaSize--;
				//Symbol theSym =null;
				int theSym =-1;
				int theInt=-1;
				HashSet<RTGEarleyState>[][] theChart = null;

				if (debug) Debug.debug(debug, "Removing "+item+" from agenda");

				// can we predict anything from this item?
				Symbol predLabel = null;
				int prednum = item.stringEndPos;
				if (prednum < string.getSize())
					predLabel = string.getSym(prednum);
				else
					predLabel = Symbol.getEpsilon();
				int predSym = item.predict(predLabel);
				if (predSym >= 0 && (!preds.containsKey(predSym) || !preds.get(predSym).contains(prednum))) {
					// add predictions for this item at this integer position
					if (!preds.containsKey(predSym))
						preds.put(predSym, new HashSet<Integer>());
					preds.get(predSym).add(prednum);
					for (TransducerRule rule : trs.getRulesOfType(i2s.get(predSym))) {
						RTGEarleyState es = new RTGEarleyState((StringTransducerRule) rule, 0, prednum, prednum);
						if (debug) Debug.debug(debug, "\tAdding prediction "+es);
						agenda.add(es);
						agendaSize++;
						addedStates.put(es, es);
						addedStatesSize++;

					}
				}
				// add to finished/unfinished chart if needed 
				if (item.isFinished) {
					theChart = finishedChart;
//					theSym = item.rule.getState();
					theSym = item.src;

					theInt = item.stringStartPos;
					if (debug) Debug.debug(debug, "\tAdding "+item+" to finished chart");
				}
				else if (predSym >=0 ){
					theChart = unfinishedChart;
					theSym = predSym;
					theInt = item.stringEndPos;
					if (debug) Debug.debug(debug, "\tAdding "+item+" to unfinished chart");
				}
				else {
					if (debug) Debug.debug(debug, "\tDiscarding "+item);
				}
				// if we can add this to one of the concat charts it can be used as a concat item
				boolean isConcatItem=false;
				if (theChart != null)  {
					isConcatItem=true;
					if (theChart[theSym][theInt] == null)
						theChart[theSym][theInt] = new HashSet<RTGEarleyState>();
					theChart[theSym][theInt].add(item);
					chartSize++;
				}
				// can we shift anything from this item?
				// allow shift at end as long as the item reads null
				if(item.stringEndPos <= string.getSize()) {
					Symbol shiftsym = null;
					if (item.stringEndPos < string.getSize())
						shiftsym = string.getSym(item.stringEndPos);
					else
						shiftsym = Symbol.getEpsilon();
					RTGEarleyState shiftstate = item.shift(shiftsym);
					if (shiftstate != null) {
						boolean didAdd = false;
						// if the state is already in there, make sure we have the version with
						// all the edges
						if (addedStates.containsKey(shiftstate))
							shiftstate = addedStates.get(shiftstate);
						// add the new edges (either to the fresh state or to the recalled one
						shiftstate.update(item);
						// now add the loaded version to the agenda and to the addedStates list
						if (!addedStates.containsKey(shiftstate)) {
							agenda.add(shiftstate);
							agendaSize++;
							addedStates.put(shiftstate, shiftstate);
							addedStatesSize++;

							didAdd = true;
						}
						// if this is updating a pointer, the agenda member and hashset member should be
						// changed appropriately!

						if (didAdd) {
							if(debug) Debug.debug(debug, "\tAdding shift "+shiftstate);
						}
						else {
							if(debug) Debug.debug(debug, "\tUpdating shift "+shiftstate);
						}
					}
				}
				// can we concat anything from this item? Only if it's a valid chart item
				if (isConcatItem) {
					//					if (predSym != null) {
					if (predSym >=0) {

						int searchInt = item.stringEndPos;
						if (finishedChart[predSym][searchInt] != null) {
							for (RTGEarleyState match : finishedChart[predSym][searchInt]) {
								if (debug) Debug.debug(debug, "\tCombining "+item+" and "+match);
								RTGEarleyState combine = new RTGEarleyState(item.rule, item.rulepos+1, item.stringStartPos, match.stringEndPos);
								boolean didAdd = false;
								// if the state is already in there, make sure we have the version with
								// all the edges
								if (addedStates.containsKey(combine))
									combine = addedStates.get(combine);
								// add the new edges (either to the fresh state or to the recalled one
								combine.update(item, match);

								// now add the loaded version to the agenda and to the addedStates list
								if (!addedStates.containsKey(combine)) {
									agenda.add(combine);
									agendaSize++;
									addedStates.put(combine, combine);
									addedStatesSize++;

									didAdd = true;
								}
								if (didAdd) {
									if(debug) Debug.debug(debug, "\tAdding left combine "+combine);
								}
								else {
									if(debug) Debug.debug(debug, "\tUpdating left combine "+combine);
								}
							}
						}
					}
					else {
						int searchInt = item.stringStartPos;
						if (unfinishedChart[item.src][searchInt] != null) {
							for (RTGEarleyState match : unfinishedChart[item.src][searchInt]) {
								if (debug) Debug.debug(debug, "\tCombining "+match+" and "+item);
								RTGEarleyState combine = new RTGEarleyState(match.rule, match.rulepos+1, match.stringStartPos, item.stringEndPos);
								boolean didAdd = false;
								// if the state is already in there, make sure we have the version with
								// all the edges
								if (addedStates.containsKey(combine))
									combine = addedStates.get(combine);
								// add the new edges (either to the fresh state or to the recalled one
								combine.update(match, item);

								// now add the loaded version to the agenda and to the addedStates list
								if (!addedStates.containsKey(combine)) {
									agenda.add(combine);
									agendaSize++;
									addedStates.put(combine, combine);
									addedStatesSize++;

									didAdd = true;
								}
								if (didAdd) {
									if(debug) Debug.debug(debug, "\tAdding right combine "+combine);
								}
								else {
									if(debug) Debug.debug(debug, "\tUpdating right combine "+combine);
								}
							}
						}
					}
				}				
			}
		} catch (OutOfMemoryError e) {
			System.err.print("Out of memory while parsing: ");
			System.err.print(addedStatesSize);
			System.err.print(" added states; ");
			System.err.print(chartSize);
			System.err.print(" chart members; ");
			System.err.print(agendaSize);
			System.err.print(" left in agenda\n");
			e.printStackTrace();
			throw new OutOfMemoryError(e.getMessage());
		}
		Debug.prettyDebug("Done parsing");
		Symbol[][][] newSyms = new Symbol[trs.states.size()][string.getSize()+1][string.getSize()+1];
		
		ArrayList<RTGEarleyState> todoList = new ArrayList<RTGEarleyState>();

		if (finishedChart[s2i.get(trs.startState)][0] != null) {
			//			if (finishedChart.containsKey(trs.startState)) {

			//			for (RTGEarleyState item : finishedChart.get(trs.startState).get(0)) {
			for (RTGEarleyState item : finishedChart[s2i.get(trs.startState)][0]) {
				if (item.stringEndPos == string.getSize()) {
					if (debug) Debug.debug(debug, "Traversing path for "+item);
					// top-level items get special start state; rest are created internally and are keyed
					// to rule, start, stop
					if (startState == null)
						startState = SymbolFactory.getStateSymbol(item.src+":0:"+item.stringEndPos);
					if (newSyms[item.src][item.stringStartPos][item.stringEndPos] == null)
						newSyms[item.src][item.stringStartPos][item.stringEndPos] = startState;
//					
//					newSyms[rule.ruleIndex][stringStartPos][stringEndPos] = newstate;
					item.isTodo = true;
					todoList.add(item);
				}
			}
		}
		
		HashSet<RTGRule> ruleSet = new HashSet<RTGRule>();
		while (todoList.size() > 0) {
			RTGEarleyState item = todoList.remove(0);
			item.isTodo = false;
			if (debug) Debug.debug(debug, "Adding RTG Rule for "+item);
			Vector<RTGRule> newrule = item.buildRTG(newSyms, todoList, this);
			if (debug) Debug.debug(debug, "Created new RTG Rule "+newrule);
			ruleSet.addAll(newrule);
		}
		// now that all buildRTG has been done, get start state

		if (startState == null)
			startState = trs.startState;
		rules = new ArrayList<Rule>(ruleSet);
		initialize();
		if (rules != null && startState != null)
			pruneUseless();						
	}
*/
	
	// earley parsing using the integrated earleyState 
	// do earley parsing on the provided CFG and StringItem, returning a CFG that covers this string
	public RTGRuleSet(StringTransducerRuleSet trs, StringItem string, int beam, int timeLevel) throws ImproperConversionException {
		boolean debug = false;
		boolean printChart = false;
		semiring = trs.semiring;
		nextRuleIndex = 0;

		EarleyState.resetParseMemo();
		HashSet<EarleyState>[][] finishedChart = trs.parse(string, beam, timeLevel);
//		Debug.prettyDebug("Created "+EarleyStateFactory.getCount()+" states and "+EarleyState.ESPair.getEdgeCount()+" edges");
//		Debug.prettyDebug("done parsing");
		// assume s2i and i2s were properly set
		Date preRecoverTime = new Date();
		// traverse the chart from finished start symbols. When encountering a new rule, look down leftmost chain 
		Symbol[][][] newSyms = new Symbol[trs.states.size()+1][string.getSize()+1][string.getSize()+1];
		ArrayList<EarleyState> todoList = new ArrayList<EarleyState>();

		if (finishedChart[trs.s2i(trs.startState)][0] != null) {
			for (EarleyState item : finishedChart[trs.s2i(trs.startState)][0]) {
				if (item.stringEndPos == string.getSize()) {
					if (debug) Debug.debug(debug, "Traversing path for "+item);
					// top-level items get special start state; rest are created internally and are keyed
					// to rule, start, stop
					if (startState == null)
						startState = SymbolFactory.getStateSymbol(item.src+":0:"+item.stringEndPos);
					if (newSyms[item.src][item.stringStartPos][item.stringEndPos] == null)
						newSyms[item.src][item.stringStartPos][item.stringEndPos] = startState;
//					
//					newSyms[rule.ruleIndex][stringStartPos][stringEndPos] = newstate;
					item.isTodo = true;
					todoList.add(item);
				}
			}
		}
	
		
		HashSet<RTGRule> ruleSet = new HashSet<RTGRule>();
		while (todoList.size() > 0) {
			EarleyState item = todoList.remove(0);
			if (printChart) {
				for (ESPair p : item.next)
					System.out.println(item+" -> "+p.left+" "+p.right);
			}
			if (debug) Debug.debug(debug, "Adding RTG Rule for "+item);
			Vector<RTGRule> newrule = item.buildRTG(newSyms, todoList, this, trs.getRuleArr());
			if (debug) Debug.debug(debug, "Created new RTG Rule "+newrule);
			ruleSet.addAll(newrule);
		}
		Date postRecoverTime = new Date();
		Debug.dbtime(timeLevel, 3, preRecoverTime, postRecoverTime, "recover cfg rules from Earley chart");
		// now that all buildCFG has been done, get start state
		if (startState == null)
			startState = trs.startState;
		rules = new ArrayList<Rule>(ruleSet);
		if (debug) Debug.debug(debug, "after construction: "+rules.size()+" rules");
		initialize();
		if (debug) Debug.debug(debug, "after init: "+rules.size()+" rules");
		if (rules != null && startState != null)
			pruneUseless();	
		if (debug) Debug.debug(debug, "after prune: "+rules.size()+" rules");


	}
	
	
	// parser-directed composition that does
	// earley parsing using the integrated earleyState 
	// do earley parsing on the provided TST and StringItem, also keying into the TTT(s), returning an RTG that covers this string
	// and all associated transformations
	// TODO: expand to allow arbitrary chain of ttt
	public RTGRuleSet(Vector<TreeTransducerRuleSet> ttts, StringTransducerRuleSet tst, StringItem string, int beam, int timeLevel) throws ImproperConversionException, UnusualConditionException {
		boolean debug = false;
		semiring = tst.semiring;
		nextRuleIndex = 0;

		EarleyState.resetParseMemo();
		Date preParseTime = new Date();
		HashSet<EarleyState>[][] finishedChart = tst.parseWithTrans(string, ttts, beam, timeLevel);
		Date postParseTime = new Date();
		Debug.dbtime(timeLevel, 1, preParseTime, postParseTime, "integrated parsing with chain of ttt");

		//		Debug.prettyDebug("done parsing");
		// assume s2i and i2s were properly set
		Date preRecoverTime = new Date();
		// traverse the chart from finished start symbols. When encountering a new rule, look down leftmost chain 

		ArrayList<ESStateList> todoList = new ArrayList<ESStateList>();

		if (finishedChart[tst.s2i(tst.startState)][0] != null) {
			Vector<Symbol> chainStartSym = new Vector<Symbol>();
			for (TreeTransducerRuleSet ts : ttts)
				chainStartSym.add(ts.startState);
			for (EarleyState item : finishedChart[tst.s2i(tst.startState)][0]) {
				if (item.stringEndPos == string.getSize() && item.nextBySeq != null && item.nextBySeq.containsKey(chainStartSym)) {
					PriorityQueue<ESPair> startStateQueue = item.nextBySeq.get(chainStartSym);
					//					boolean isStartable = true;
					//					for (int i = 0; i < item.matches.size(); i++) {
					//						TreeRuleTuple tup = item.matches.get(i);
					//						if (tup.tree != tup.rule.getRHS() ||
					//							tup.rule.getState() != ttts.get(i).getStartState()) {
					//							isStartable = false;
					//							break;
					//						}
					//					}
					//					if (isStartable) {
					if (debug) Debug.debug(debug, "Traversing path for "+item);
					// top-level items get special start state; rest are created internally and are keyed
					// to rule, start, stop
					if (startState == null) {
						// access symbol related to all external states, state, start, end
						// first child should be sufficient
						for (ESPair edge : startStateQueue) {
							startState = SymbolFactory.getSymbol(item.src+":"+edge.getStateSym().toString());
							break;
						}
					}
					ESStateList stateItem = startStateQueue.peek().headStateList;
					stateItem.isTodo = true;
					todoList.add(stateItem);
				}
			}
		}

	
		
		HashSet<RTGRule> ruleSet = new HashSet<RTGRule>();
		while (todoList.size() > 0) {
			ESStateList stateItem = todoList.remove(0);
			EarleyState item = stateItem.es;
			if (debug) Debug.debug(debug, "Adding RTG Rule for "+stateItem);
			Vector<RTGRule> newrule = item.buildTransRTG(stateItem, todoList, this, tst.getRuleArr());
			if (debug) Debug.debug(debug, "Created new RTG Rule "+newrule);
			ruleSet.addAll(newrule);
		}
		Date postRecoverTime = new Date();
		Debug.dbtime(timeLevel, 3, preRecoverTime, postRecoverTime, "recover cfg rules from Earley chart");
		// now that all buildCFG has been done, get start state
		if (startState == null)
			startState = tst.startState;
		rules = new ArrayList<Rule>(ruleSet);
		if (debug) Debug.debug(debug, "after construction: "+rules.size()+" rules");
		initialize();
		if (debug) Debug.debug(debug, "after init: "+rules.size()+" rules");
		if (rules != null && startState != null)
			pruneUseless();	
		if (debug) Debug.debug(debug, "after prune: "+rules.size()+" rules");


	}
	
	// IPParser constructor -- each item is very simple
	// construct the tree from the table, then build the rule from the tree
	public RTGRuleSet(HashMap<IPFinState, HashMap<Vector<IPFinState>, HashMap<Integer, Double>>> builtRules,
			HashMap<Integer, Symbol> i2s, IPFinState start, Semiring semi) {

		startState = SymbolFactory.getStateSymbol(start.toString());
		rules = new ArrayList<Rule>();
		semiring = semi;
		for (IPFinState lhs : builtRules.keySet()) {
			Symbol lhsState = SymbolFactory.getStateSymbol(lhs.toString());
			if (builtRules.get(lhs) != null) {
				for (Vector<IPFinState> rhs : builtRules.get(lhs).keySet()) {
					Vector<TreeItem> rhsTrees = new Vector<TreeItem>();
					for (IPFinState child: rhs) {
						rhsTrees.add(new TreeItem(SymbolFactory.getStateSymbol(child.toString())));
					}
					TreeItem[] arr = new TreeItem[rhsTrees.size()];
					rhsTrees.toArray(arr);
					for (int label : builtRules.get(lhs).get(rhs).keySet()) {
						rules.add(new RTGRule(this, lhsState, new TreeItem(rhsTrees.size(), i2s.get(label), arr), builtRules.get(lhs).get(rhs).get(label), semiring));
					}

				}
			}
		}
		initialize();
		pruneUseless();
		
	
	}
	
	
	// test code
	public static void main(String argv[]) {
		try {
			RealSemiring s = new RealSemiring();

			// testing tarjan
			RTGRuleSet rtg = new RTGRuleSet(new BufferedReader(new InputStreamReader(new FileInputStream(argv[0]), "utf-8")), s);
//			Debug.debug(true, rtg.toString());
			Debug.prettyDebug("Finiteness of grammar is "+rtg.isFinite(true));
			
			// testing backward application
	/*		StringTransducerRuleSet tst = new StringTransducerRuleSet(new BufferedReader(new InputStreamReader(new FileInputStream(argv[0]), "utf-8")), s);
			Debug.debug(true, tst.toString());
			File f = new File(argv[1]);
			Vector v = StringItem.readStringSet(f, "utf-8");
			ObjectInputStream ois = new ObjectInputStream(new FileInputStream((File)v.get(0)));
			StringItem str = (StringItem)ois.readObject();
			Debug.debug(true, str.toString());
			RTGRuleSet rtg = new RTGRuleSet(tst, str);
			Debug.prettyDebug(rtg.toString());
*/
		
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
