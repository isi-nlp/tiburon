package edu.isi.tiburon;

import java.io.BufferedReader;
import java.io.Externalizable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.StreamTokenizer;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import gnu.trove.TIntObjectHashMap;

public class CFGRuleSet extends RuleSet implements Serializable {

	private Hashtable<Symbol, Short> s2i;
	private Hashtable<Short, Symbol> i2s;
	public short s2i(Symbol s) {
		return s2i.get(s);
	}
	
	public Symbol i2s(short i) {
		return i2s.get(i);
	}

	// array of rules is sometimes convenient, but only when called for
	private CFGRule[] rulearr = null;
	public CFGRule[] getRuleArr() {
		boolean debug = false;
		if (rulearr != null)
			return rulearr;
		// could be mostly empty, but we've already created at least this many rules so it
		// shouldn't be that problematic
		rulearr = new CFGRule[nextRuleIndex];
		if (debug) Debug.debug(debug, " building rule array with up to "+nextRuleIndex+" rules");
		for (Map.Entry<Integer, Rule> map : rulesByIndex.entrySet()) {
			if (debug) Debug.debug(debug, "Filling slot "+map.getKey());
			rulearr[map.getKey()] = (CFGRule)map.getValue();
		}
		return rulearr;
	}
	
	
	// find out what's a state, what's a term. maybe do some other stuff?
	public void initialize() {
		boolean debug = false;
		leafChildren = new Hashtable();
		states = new HashSet();
		states.add(startState);
		rulesByLHS = new Hashtable<Symbol, ArrayList<Rule>>();
		rulesByIndex = new Hashtable<Integer, Rule>();
		rulesByTie = new TIntObjectHashMap();
		Iterator i = rules.iterator();
		// first add states (ie nonterminals)
		if (debug) Debug.debug(debug, "Processing "+rules.size()+" rules");
		while (i.hasNext()) {
			CFGRule r = (CFGRule)i.next();
		//	if (debug) Debug.debug(debug, "Adding "+r+" with index "+r.getIndex());
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

		// to find nonterminals, check each rule for symbols that are not in the state set
		terminals = new HashSet();
		i = rules.iterator();
		while (i.hasNext()) {
			CFGRule r = (CFGRule)i.next();
			StringItem rhs = (StringItem)r.getRHS();
			while (rhs != null) {
				Symbol rsym = rhs.getLabel();
				if (!states.contains(rsym))
					terminals.add(rsym);
				rhs = rhs.getNext();
			}
		}
		if (debug) {
			Debug.debug(debug, "Nonterminals:");
			Iterator stit = states.iterator();
			while (stit.hasNext()) {
				Symbol state = (Symbol)stit.next();
				Debug.debug(debug, "\t"+state.toString());
			}
			Debug.debug(debug, "Terminals:");
			Iterator tit = terminals.iterator();
			while (tit.hasNext()) {
				Symbol term = (Symbol)tit.next();
				Debug.debug(debug, "\t"+term.toString());
			}
		}
		
		s2i = new Hashtable<Symbol, Short>();
		i2s = new Hashtable<Short, Symbol>();
		// integer mapping to and from xrs states used by backwards application
		short nextState=0;
		for (Symbol s : states) {
			if (debug) Debug.debug(debug, "Mapping "+nextState+" to "+s);
			s2i.put(s, nextState);
			i2s.put(nextState++, s);
		}
	}


	// search for terminal symbols
	public HashSet findTerminals(Item i) {
		StringItem s = (StringItem)i;
		StringItem str = s;
		HashSet set = new HashSet();
		while (str != null) {
			if (!states.contains(str.label))
				set.add(str.label);
			str = str.getNext();
		}
		return set;
	}


	// just for testing and filedifferentiation
	public CFGRuleSet() {
		//	Debug.debug(true, "WARNING: CFGRuleSet empty constructor; for prototyping only");
	}

	// to be filled later
	public CFGRuleSet(Semiring s) {
		super(s);
	}

	// copy constructor. no memoization is copied
	public CFGRuleSet(RuleSet rs) {
		super(rs);
	}

	public CFGRuleSet(String filename, Semiring s) throws FileNotFoundException, IOException, DataFormatException  {
		this(new BufferedReader(new InputStreamReader(new FileInputStream(filename), "utf-8")), s);
	}
	public CFGRuleSet(String filename, String encoding, Semiring s) throws FileNotFoundException, IOException, DataFormatException  {
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
	
	public CFGRuleSet(BufferedReader br, Semiring s) throws  IOException, DataFormatException  {
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
			CFGRule r = null;
			try {
				r = new CFGRule(this, ruleText, semiring);
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
//		StreamTokenizer st = new StreamTokenizer(br);
		//
//				st.resetSyntax();
//				st.eolIsSignificant(true);
//				st.wordChars(33, '\u00ff');
//				st.quoteChar('"');
//				st.commentChar('%');
//		boolean isStartState = false;
//		rules = new ArrayList<Rule>();
//
//		while (!isStartState && st.nextToken() != StreamTokenizer.TT_EOF) {
//			switch(st.ttype) {
//			case StreamTokenizer.TT_NUMBER:
//				//			throw new DataFormatException("start state is numerical");
//				startState = SymbolFactory.getSymbol(""+st.nval);
//				isStartState = true;
//				int next = st.nextToken();
//				// get rid of any excess tabs
//				while (next == 32 || next == 9)
//					next = st.nextToken();
//				if (next != StreamTokenizer.TT_EOL && next != 13)
//					throw new DataFormatException("Expected single start state but read "+next);
//				break;
//			case StreamTokenizer.TT_WORD:
//				startState = SymbolFactory.getSymbol(st.sval);
//				//		    Debug.debug(true, "Setting start state to "+startState.toString());
//				if (debug) Debug.debug(debug, "Read start state ["+startState.toString()+"]");
//				isStartState = true;
//				next = st.nextToken();
//				// get rid of any excess tabs
//				while (next == 32 || next == 9)
//					next = st.nextToken();
//				if (next != StreamTokenizer.TT_EOL && next != 13)
//					throw new DataFormatException("Expected single start state but read "+next);
//				break;
//			case '"':
//				startState = SymbolFactory.getSymbol('"'+st.sval+'"');
//				//		    Debug.debug(true, "Setting start state to "+startState.toString());
//				isStartState = true;
//				next = st.nextToken();
//				// get rid of any excess tabs
//				while (next == 32 || next == 9)
//					next = st.nextToken();
//				if (next != StreamTokenizer.TT_EOL && next != 13)
//					throw new DataFormatException("Expected single start state but read "+next);
//				break;
//			case StreamTokenizer.TT_EOL:
//				// must be in a comment;
//				break;
//			default:
//				throw new DataFormatException("Bad type for start state");
//			}
//		}
//		// set up for rules now
//		st.resetSyntax();
//		st.wordChars(0, 9);
//		st.wordChars(14, '\u00ff');
//		st.eolIsSignificant(true);
//		st.quoteChar('"');
//		st.commentChar('%');
//		//	    st.whitespaceChars(10, 13);
//		StringBuffer ruleText = new StringBuffer();
//		while (st.nextToken() != StreamTokenizer.TT_EOF) {
//			switch(st.ttype) {
//			case StreamTokenizer.TT_WORD:
//				ruleText.append(st.sval);
//				break;
//			case '"':
//				// """ case or possibly ""
//				if (st.sval.length() == 0) {
//					st.ordinaryChar('"');
//					if (st.nextToken() == '"') {
//						if (debug) Debug.debug(debug, "Found triple quote");
//						ruleText.append("\"\"\"");
//					}
//					else {
//						ruleText.append("\"\"");
//						st.pushBack();
//					}
//					st.quoteChar('"');
//				}
//				else
//					ruleText.append('"'+st.sval+'"');
//				break;
//			case 13:
//			case StreamTokenizer.TT_EOL:
//				if (ruleText.length() > 0) {
//					if (debug) Debug.debug(debug, "About to make a rule out of "+ruleText.toString());
//					CFGRule r = null;
//					try {
//						r = new CFGRule(this, ruleText.toString(), semiring);
//					}
//					catch (DataFormatException e) {
//						throw new DataFormatException(ruleText.toString()+", "+e.getMessage(), e);
//					}
//					if (debug) Debug.debug(debug, "Made rule "+r.toString());
//					rules.add(r);
//				}
//				ruleText = new StringBuffer();
//				break;
//			default:
//				throw new DataFormatException((char)st.ttype+", "+"Expected a rule");
//			}
//		}
//		if (ruleText.length() > 0) {
//			if (debug) Debug.debug(debug, "About to make LAST rule out of "+ruleText.toString());
//			CFGRule r = null;
//			try {
//				r = new CFGRule(this, ruleText.toString(), semiring);
//			}
//			catch (DataFormatException e) {
//				throw new DataFormatException(ruleText.toString()+", "+e.getMessage(), e);
//			}
//			if (debug) Debug.debug(debug, "Made rule "+r.toString());
//			rules.add(r);
//		}
//		br.close();
//
//		initialize();
//		pruneUseless();
//	}

	// for conversion of RTGRuleSet
	// leaves of rhs used as rhs. lhs untouched
	public CFGRuleSet(RTGRuleSet rtg) throws ImproperConversionException {
		boolean debug = false;
		startState = rtg.getStartState();
		semiring = rtg.getSemiring();
		rules = new ArrayList<Rule>();
		nextRuleIndex = 0;
		Iterator rit = rtg.getRules().iterator();
		while (rit.hasNext()) {
			RTGRule currRule = (RTGRule)rit.next();
			if (debug) Debug.debug(debug, "Converting "+currRule.toString()+" to rtg");
			try {
				CFGRule newrule = new CFGRule(this, currRule);
				if (debug) Debug.debug(debug, "Converted to "+newrule.toString());
				rules.add(newrule);
			}
			catch (ImproperConversionException e) {
				throw new ImproperConversionException("Could not add converted rule after "+rules.size()+" to CFGRuleSet: "+e.getMessage());
			}
		}
		initialize();
	}

	// for projection of the range of a string transducer
	// cannot perform if it's copying
	// state of rule becomes lhs of rtg rule.
	// rhs of rule becomes rhs of rtg rule with states instead of variables
	public CFGRuleSet(StringTransducerRuleSet trs) throws ImproperConversionException {
		boolean debug = false;
		if (trs.isCopying())
			throw new ImproperConversionException("Can't get range CFG of a copying transducer");
		startState = trs.getStartState();
		semiring = trs.getSemiring();
		rules = new ArrayList<Rule>();
		nextRuleIndex = 0;
		Iterator it = trs.getRules().iterator();
		while (it.hasNext()) {
			StringTransducerRule tr = (StringTransducerRule)it.next();
			CFGRule newrule = new CFGRule(this, tr);
			if (debug) Debug.debug(debug, "Converted "+tr+" to "+newrule);
			rules.add(newrule);
		}
		initialize();
	}


	// forward application of a tree through a StringTransducerRuleSet. Originally in its own class
	// the tree, then let application return tree-state pairs. continue while there's some left to do

	// "normal mode"
	public CFGRuleSet(TreeItem t, StringTransducerRuleSet trs) {
		this(t, trs.getStartState(), trs, new HashSet<Symbol>(), new HashMap<Symbol, StateTreePair>(), new HashMap<StateTreePair, Symbol>(), true);
	}

	// allow any state to start the application. For composition. Eps rules not allowed
	public CFGRuleSet(TreeItem t, Symbol state, StringTransducerRuleSet trs, 
			Set<Symbol> varSymSet, 
			  HashMap<Symbol, StateTreePair> varSymSTP, 
			  HashMap<StateTreePair, Symbol> STPvarSym) {
		this(t, state, trs, varSymSet, varSymSTP, STPvarSym, false);
	}

	// choose state to start the application. determine whether to use eps rules
	// initialization done at the same time
	public CFGRuleSet(TreeItem t, 
				      Symbol state, 
				      StringTransducerRuleSet trs, 
				      Set<Symbol> varSymSet, 
					  HashMap<Symbol, StateTreePair> varSymSTP, 
					  HashMap<StateTreePair, Symbol> STPvarSym,
					  boolean epsAllowed) {
		boolean debug = false;
		leafChildren = new Hashtable();
		states = new HashSet();
		rulesByLHS = new Hashtable<Symbol, ArrayList<Rule>>();
		rulesByIndex = new Hashtable<Integer, Rule>();
		rulesByTie = new TIntObjectHashMap();
		nextRuleIndex = 0;

		semiring = trs.getSemiring();

		if (debug) Debug.debug(debug, "Applying "+t+" through transducer starting with "+state);
		StateTreePair startStatePair = SymbolFactory.getStateTreePair(state, t, 1, t.getLeaves().length);
		startState = startStatePair.getSymbol();
		states.add(startState);
		rules = new ArrayList<Rule>();

		HashSet usedStates = new HashSet();
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
	
	// TODO: allow general one...
	
	// allow any state to start the application. For composition. Eps rules not allowed
	public CFGRuleSet(TreeItem t, 
					  Symbol state, 
					  StringTransducerRuleSet trs) {
		this(t, state, trs, false);
	}
	
	// choose state to start application. determine whether to use eps rules
	// initialization done at the same time
	public CFGRuleSet(TreeItem t, 
					  Symbol state, 
					  StringTransducerRuleSet trs, 
					  boolean epsAllowed) {
		leafChildren = new Hashtable<Rule, Vector<Symbol>>();
		states = new HashSet();
		rulesByLHS = new Hashtable();
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


		if (debug) Debug.debug(debug, "ForwardApplication: before pruning: "+toString());
		pruneUseless();
		if (debug) Debug.debug(debug, "ForwardApplication: after pruning: "+toString());
		initialize();
		if (debug) Debug.debug(debug, "ForwardApplication: after initializing: "+toString());
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
		// track whether or not epsilon can be reached 
		boolean canBeEpsilon=false;
		// track whether or not we've tested for epsilon
		boolean wasEpsilonTested=false;
		public TarjanVert(Symbol s) { index=lowlink=-1; state=s; selfloop=canBeEpsilon=wasEpsilonTested=false;} 

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
	public boolean isFinite(boolean includeproducers) {
		boolean debug = false;
		tarjanStack= new Stack<TarjanVert>();
		tarjanSeen = new Hashtable<Symbol, TarjanVert>();
		tarjanIndex = 0;
		TarjanVert init = new TarjanVert(startState);
		tarjanSeen.put(startState, init);
		// might have to check all states
		if (includeproducers) {
			if (debug) Debug.debug(debug, "Checking "+init+" for monadic chain");
			if (!tarjanEps(init))
				return false;
			for (Symbol st : states) {
				if (!tarjanSeen.containsKey(st)) {
					TarjanVert vert = new TarjanVert(st);
					if (debug) Debug.debug(debug, "Checking "+vert+" for monadic chain");
					tarjanSeen.put(st, vert);
					if (!tarjanEps(vert))
						return false;
				}
			}
			return true;
		}
		else {
			return tarjan(init);
		}
	}
	
	// internal recursive tarjan scc finding operator
	private boolean tarjan(TarjanVert v) {
		boolean debug = false;
		v.index = tarjanIndex;
		v.lowlink = tarjanIndex;
		if (debug) Debug.debug(debug, "Operating on "+v.state+"; setting index and lowlink to "+v.index);
		tarjanIndex++;
		
//		// TODO: add eps detection to this!
//		// if producers are okay, then recurse down on producer rules before inserting self
//		if (includeproducers) {
//			for (Rule abstractr : getRulesOfType(v.state)) {
//				CFGRule r = (CFGRule)abstractr;
//				if (r.getRHS().getSize() > 1) {
////					if (debug) Debug.debug(debug, "Recurse-free considering "+r);
//					Symbol[] leaves = r.getRHS().getLeaves();
//					// no self-loops allowed but otherwise same rules as below
//					// no update based on already-inserted states
//					for (int i = 0; i < leaves.length; i++) {
//						if (!states.contains(leaves[i]))
//							continue;
//						if (v.state == leaves[i])
//							continue;
//						if (tarjanSeen.containsKey(leaves[i]))
//							continue;
//						if (debug) Debug.debug(debug, "Recursing parent-free on "+leaves[i]);
//						TarjanVert leaf = new TarjanVert(leaves[i]);
//						tarjanSeen.put(leaves[i], leaf);
//						// if lower level is false return false right away
//						if (!tarjan(includeproducers, leaf)) { 
//							if (debug) Debug.debug(debug, "Found loop in "+leaf+" so aborting search");
//							return false;
//						}
//					}
//				}
//			}
//		}
		if (debug) Debug.debug(debug, "Enqueuing "+v);
		tarjanStack.push(v);
		// normal call or, if producers are okay, state-to-state rules only
		if (getRulesOfType(v.state) != null) {
			for (Rule abstractr : getRulesOfType(v.state)) {
				CFGRule r = (CFGRule)abstractr;
				// TODO: add eps detection to this
//				if (includeproducers && r.getRHS().getSize() > 1) {
//					if (debug) Debug.debug(debug, "Skipping "+r+" for size");
//					continue;
//				}
	//			if (debug) Debug.debug(debug, "Considering "+r);
				Symbol[] leaves = r.getRHS().getLeaves();
				for (int i = 0; i < leaves.length; i++) {
					// direct self loop. Mark for later, but not if we're in includeproducers
//					if (!includeproducers && v.state == leaves[i]) {
						if (v.state == leaves[i]) {

						if (debug) Debug.debug(debug, "Found direct self loop for "+v.state+" due to "+r);
						v.selfloop = true;
					}
					if (states.contains(leaves[i])) {
						if (!tarjanSeen.containsKey(leaves[i]) || tarjanSeen.get(leaves[i]).index == -1) {
							if (debug) Debug.debug(debug, "Recursing on "+leaves[i]);
							TarjanVert leaf = new TarjanVert(leaves[i]);
							tarjanSeen.put(leaves[i], leaf);
							// if lower-level is false, return false right away
							if (!tarjan(leaf)) { 
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
	
	// separate tarjan for monadic chains only
	// includes monadic check
	// internal recursive tarjan scc finding operator
	private boolean tarjanEps(TarjanVert v) {
		boolean debug = false;
		v.index = tarjanIndex;
		v.lowlink = tarjanIndex;
		if (debug) Debug.debug(debug, "Operating on "+v.state+"; setting index and lowlink to "+v.index);
		tarjanIndex++;
		
		if (debug) Debug.debug(debug, "Enqueuing "+v);
		tarjanStack.push(v);
		// state-to-state rules only
		// if all other sibs can be epsilon, non-state-to-state okay
		if (getRulesOfType(v.state) != null) {
			for (Rule abstractr : getRulesOfType(v.state)) {
				CFGRule r = (CFGRule)abstractr;
				// TODO: here is where we progressively choose which gets to be the iter state
				HashSet<Symbol> itersyms = new HashSet<Symbol>();
				if (r.getRHS().getSize() > 1) {
					Symbol[] leafset = r.getRHS().getLeaves();
					HashSet<Symbol> epsSet = getEpsStates();
					for (int i = 0; i < leafset.length; i++) {
						Symbol cand = leafset[i];
						boolean isOkay = true;
						for (int j = 0; j < leafset.length; j++) {
							if (!epsSet.contains(leafset[j])) {
								isOkay = false;
								break;
							}
						}
						if (isOkay) {
							if (debug) Debug.debug(debug, "Monadic chain okay for "+cand+" in "+r);
							itersyms.add(cand);
						}
					}
					if (debug) Debug.debug(debug, "Skipping "+r+" for size");
					continue;
				}
				else {
					itersyms.add(r.getRHS().getLeaves()[0]);
				}
				for (Symbol iterstate : itersyms) {
					// direct self loop. Mark for later
					if (v.state == iterstate) {
						if (debug) Debug.debug(debug, "Found direct self loop for "+v.state+" due to "+r);
						v.selfloop = true;
					}
					if (states.contains(iterstate)) {
						if (!tarjanSeen.containsKey(iterstate) || tarjanSeen.get(iterstate).index == -1) {
							if (debug) Debug.debug(debug, "Recursing on "+iterstate);
							TarjanVert leaf = new TarjanVert(iterstate);
							tarjanSeen.put(iterstate, leaf);
							// if lower-level is false, return false right away
							if (!tarjanEps(leaf)) { 
								if (debug) Debug.debug(debug, "Found loop in "+leaf+" so aborting search");
								return false;
							}
							if (leaf.lowlink < v.lowlink) {
								if (debug) Debug.debug(debug, "Lowering lowlink of "+v.state+" to "+leaf.lowlink+" because of "+leaf.state);
								v.lowlink = leaf.lowlink;
							}
						}
						else {
							TarjanVert leaf = tarjanSeen.get(iterstate);
							if (tarjanStack.contains(leaf) && leaf.lowlink < v.lowlink) {
								if (debug) Debug.debug(debug, "Lowering lowlink of "+v.state+" to "+leaf.lowlink+" because of enqueued "+leaf.state);
								v.lowlink = leaf.lowlink;
							}
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
	
	
	// check for epsilon production ability of a state
	// progressively build the set of epsilon states
	//m^2 algorithm!
	private HashSet<Symbol> epsStates = null;
	HashSet<Symbol> getEpsStates() {
		boolean debug = true;
		if (epsStates != null)
			return epsStates;
		HashSet<Symbol> ret = new HashSet<Symbol>();
		do {
			epsStates = ret;
			ret = new HashSet<Symbol>();
			for (Symbol s : states) {
				if (epsStates.contains(s))
					ret.add(s);
				else {
					for (Rule r : getRulesOfType(s)) {
						if (r.rhs.isEmptyString()) {
							if (debug) Debug.debug(debug, "Found direct epsilon rule: "+r);
							ret.add(s);
							break;
						}
						Symbol[] rhssyms = r.getRHS().getLeaves();
						boolean isEmpty = true;
						for (int i = 0; i < rhssyms.length; i++) {
							if (!epsStates.contains(rhssyms[i]) && !ret.contains(rhssyms[i])) {
								isEmpty = false;
								break;
							}
						}
						if (isEmpty) {
							if (debug) Debug.debug(debug, "All rhs in "+r+" go to eps");
							ret.add(s);
							break;
						}
					}
				}				
			}
		} while (ret.size() > epsStates.size());
		return epsStates;
	}

	
	
	// adapted from the version for rtgs
	// isFinite returns true if there are not an infinite number of derivations that can be produced
	// from this set
	boolean isFinite() {
		// TODO: memoize this, releasing memoization when change to set occurs
		// maintain master set (states we will process), then for each state popped off,
		// get the set of states it reaches until this is unchanging. If one of the members of the set
		// is the state from the master, we're in a loop. Union the reached states 
		// minus the processed states with the master set
		// and continue. When the master set is empty, we're done.

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
			//Debug.debug(true, "Getting rules for "+currState.toString()+": "+stateCounter);

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
					StringItem rhs = (StringItem)((CFGRule)it.next()).getRHS();
					while (rhs != null) {
						Symbol leaf = rhs.getLabel();
						if (states.contains(leaf)) {
							if (currState.equals(leaf)) {
								//Debug.debug(true, "Can reach "+currState.toString()+" from itself");
								return false;
							}
							miniSet.add(leaf);
						}
						rhs = rhs.getNext();
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


	// adapted from the version for rtgs
	// algorithm: keep count of the number of possibilities for each state.
	// for all states with only terminal productions (some must exist in a finite grammar) count 1 per rule. add these states
	// to the "okay" set.
	// while there is no entry for the start state (alternatively, while there are states that aren't okay), for all okay states,
	// for each derivation, multiply the entries for each state produced (1 if only terminals). Score for the state is the sum over
	// all derivations for that state.

	// we get this in biginteger 
	public String getNumberOfDerivations() {
		boolean debug = false;
		// make sure there's something to count
		if (rules.size() == 0)
			return "0";
		Hashtable<Symbol, BigInteger> scores = new Hashtable<Symbol, BigInteger>();
		HashSet<Symbol> okStates = new HashSet<Symbol>();
		HashSet<Symbol> leftStates = new HashSet<Symbol>(states);

		// copy cause we change the set
		while (!scores.containsKey(startState)) {
			HashSet<Symbol> leftStatesCopy = new HashSet<Symbol>(leftStates);
			for (Symbol currState : leftStatesCopy) {
//				if (debug) Debug.debug(debug, "Size of left states = "+leftStates.size());
//				if (debug) Debug.debug(debug, "Size of left states copy = "+leftStatesCopy.size());
//				if (debug) Debug.debug(debug, "Considering state "+currState);
				ArrayList<Rule> currRules = getRulesOfType(currState);
				if (currRules == null) {
					Debug.debug(true, "ERROR: "+currState.toString()+" has no rules!");
					System.exit(0);
				}
				Iterator crit = currRules.iterator();
				BigInteger derivCount = new BigInteger("0");
				boolean hasBadState = false;
				for (Rule cr : currRules) {
					BigInteger ruleDeriv = new BigInteger("1");
					StringItem rhs = (StringItem)((CFGRule)cr).getRHS();
					while (rhs != null) {
						Symbol leaf = rhs.getLabel();
						//			System.err.print(leaf+"...");
						// acceptable states multiply the derivations. unacceptables
						// get the can. terminals don't affect the number
						if (states.contains(leaf)) {
							if (okStates.contains(leaf)) {
								BigInteger contrib = scores.get(leaf);
								ruleDeriv = ruleDeriv.multiply(contrib);
								//				System.err.print("("+contrib+") ");
							}
							else {
								//				Debug.debug(true, "BAD!");
								hasBadState = true;
								break;
							}
						}
						rhs = rhs.getNext();
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
				if (debug) Debug.debug(debug, "Derivations for "+currState+" at "+derivCount);
				scores.put(currState, derivCount);
				okStates.add(currState);
				leftStates.remove(currState);
			}
		}
		if (debug) Debug.debug(debug, "Derivations for rule set: "+scores.get(startState));
		return scores.get(startState).toString();
	}


	// put into CNF
	public void makeNormal() {
		boolean debug = true;		
		Debug.debug(debug, "WARNING: makeNormal not yet implemented for CFG");
//		if (madeNormal) {
//			//	    Debug.debug(true, "Already normal");
//			return;
//		}
//
//		// collect nonterms that have eps rhs
//		HashMap<Symbol, Double> epsStates = new HashMap<Symbol, Double>();
//		Iterator rit = rules.iterator();
//		while (rit.hasNext()) {
//			CFGRule r = (CFGRule)rit.next();
//			if (r.rhs.isEmptyString()) {
//				if (debug) Debug.debug(debug, "Adding "+r.lhs+" to eps-removal set");
//				epsStates.put(r.lhs, new Double(r.getWeight()));
//			}
//		}
//		// expand epsilon removal in each appropriate rule. Do it per-state
//		for (Symbol currState : states)  {
//			Iterator csrit = getRulesOfType(currState).iterator();
//			
//			while (csrit.hasNext()) {
//				CFGRule r = (CFGRule)csrit.next();
//				if (debug) Debug.debug(debug, "Getting new epsilon-removed strings from "+r);
//				HashMap<StringItem, Double> newrhs = r.epsRemove(epsStates);
//				for (StringItem nr : newrhs.keySet()) {
//					// weight is original rule weight times the number of epsilon instances
//					double weight = semiring.times(r.getWeight(), newrhs.get(nr).doubleValue());
//					CFGRule newEpsRule = new CFGRule(this, currState, nr, weight, semiring);
//					if (debug) Debug.debug(debug, "Created new rule "+newEpsRule);
//				}
//			}			
//		}

	}


	// TODO: check for reachability; similar code to that for RTG
	public boolean isAllReachable() {
		boolean debug = true;
		Debug.debug(debug, "WARNING: isAllReachable not yet implemented for CFG");
		return true;
	}


	// TODO: implement
	public void makeFinite() {
		boolean debug = true;
		Debug.debug(debug, "WARNING: makeFinit not yet implemented for CFG");
	}
	
	
	// is a rule an epsilon rule? Rules themselves can't know this because they don't know about state
	// an epsilon rule is a rule with rhs singleton that is a state
	public boolean isEpsilonRule(CFGRule r) {
		StringItem rhs = (StringItem)r.getRHS();
		if (rhs.getNumChildren() > 0)
			return false;
		return states.contains(rhs.getLabel());
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
				CFGRule r = (CFGRule)genr;
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
				CFGRule r = (CFGRule)genr;
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
				CFGRule nr = new CFGRule(this, lhs, (StringItem)rhs, ruleMatrix.get(lhs).get(rhs), semiring);
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

//	// nothing to do in cfg land. the method exists as a convenience.
//	public void removeEpsilons() {
//		return;
//	}

	// memoized function originally from Knuth (bad location for it)
	// returns and memoizes the leaves of a rule that are states
	// in general some bad coding...
	public Vector<Symbol> getLeafChildren(Rule r) {
		CFGRule realr = (CFGRule)r;
		if (!leafChildren.containsKey(realr)) {
			Vector<Symbol> v = new Vector<Symbol>();
			StringItem rhs = (StringItem)realr.getRHS();
			while (rhs != null) {
				Symbol sym = rhs.getLabel();
				if (states.contains(sym))
					v.add(sym);
				rhs = rhs.getNext();
			}
			leafChildren.put(realr, v);
		}
		return leafChildren.get(realr);
	}


	// prune useless - two-pass algorithm, based on knuth. First go bottom up and mark all states
	// that can be used to reach terminal symbols. Then go top-down and mark all states that can
	// be reached from the start symbol. Only keep states that satisfy both criteria.

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
				// do not consider star state
				if (currState == Symbol.getStar())
					continue;
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
					CFGRule currRule = (CFGRule)rit.next();

					// DISABLED due to time length
					// sometimes this is run when ruleset is in an uncertain state. trust rules list above rulesoftype
					// list
					//		    if (!rules.contains(currRule))
					//			continue;

					// rule doesn't count if it has zero weight
					if (semiring.betteroreq(semiring.ZERO(), currRule.getWeight()))
						continue;
					StringItem rhs = (StringItem)currRule.getRHS();
					boolean isOkay = true;
					// check that all leaves are either terms or already seen
					while (rhs != null) {
						Symbol leaf = rhs.getLabel();
						if (states.contains(leaf) && leaf != Symbol.getStar() && !bottomReachable.contains(leaf)) {
							isOkay = false;
							break;
						}
						rhs = rhs.getNext();
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
			// do not consider star state
			if (currState == Symbol.getStar())
				continue;
			if (debug) Debug.debug(debug, "TD: "+currState.toString());
			checkedStates.add(currState);
			ArrayList<Rule> currRules = getRulesOfType(currState);
			if (currRules == null)
				continue;
			Iterator rit = currRules.iterator();
			// look for at least one valid rule
			while (rit.hasNext()) {
				CFGRule currRule = (CFGRule)rit.next();

				// DISABLED -- too slow
				// sometimes this is run when ruleset is in an uncertain state. trust rules list above rulesoftype
				// list
				//		if (!rules.contains(currRule))
				//		    continue;

				// rule doesn't count if it has zero weight
				if (semiring.betteroreq(semiring.ZERO(), currRule.getWeight()))
					continue;
				StringItem rhs = (StringItem)currRule.getRHS();
				boolean isOkay = true;
				// check that all leaves are either terms or already seen
				while (rhs != null) {
					Symbol leaf = rhs.getLabel();
					if (states.contains(leaf) && leaf != Symbol.getStar() && !bottomReachable.contains(leaf)) {
						isOkay = false;
						break;
					}
					rhs = rhs.getNext();
				}
				// valid rules inspire other states to check
				if (isOkay) {
					checkedRules.add(currRule);
					rhs = (StringItem)currRule.getRHS();
					while (rhs != null) {
						Symbol leaf = rhs.getLabel();
						if (states.contains(leaf) && 
								leaf != Symbol.getStar() &&
								!checkedStates.contains(leaf) &&
								!readyStates.contains(leaf)) {
							readyStates.push(leaf);
						}
						rhs = rhs.getNext();
					}
				}
			}
		}
		rules = checkedRules;
		initialize();
	}


	// for cfg training.
	// given a state and trainingstring, find appropriate rules
	// TODO: memoize!!
	public HashSet getTrainingRules(Symbol s, TrainingString item) throws UnexpectedCaseException{
		boolean debug = false;
		HashSet retSet = new HashSet();
		ArrayList<Rule> currRules = getRulesOfType(s);
		if (currRules == null)
			throw new UnexpectedCaseException("No rules for "+s.toString());
		Iterator rit = currRules.iterator();
		while (rit.hasNext()) {
			CFGRule r = (CFGRule)rit.next();
			if (r.isItemMatch(s, item, this))
				retSet.add(r);
		}
		return retSet;
	}

	


	// just parsing. used by training and backwards app.
	// given the cfg (i.e. "this") and string, return the finished chart
	// only add beam best hyperedges for each state.
	
	public HashSet<EarleyState>[][] parse(StringItem string, int beam, int timeLevel) {
		boolean debug = false;

		// initialize the earley state factory
//		EarleyStateFactory.init(this, string.getSize());
		Date preParseTime = new Date();
//		Debug.prettyDebug("About to parse "+string+" in cfg with "+states.size()+" states and "+rules.size()+" rules");
		Stack<EarleyState> agenda = new Stack<EarleyState>();
		// track all states that have been added to an agenda. map from the state with no next pointers
		// to the state with all next pointers (which is the state actually added into the agenda) and update
		// the latter. 
		
//		HashMap<EarleyState, EarleyState> addedStates = new HashMap<EarleyState,EarleyState>();
		
		// categorize chart nodes by finished and unfinished variants
		// index by state id and string pos
		HashSet<EarleyState>[][] finishedChart = new HashSet[states.size()][string.getSize()+1];
		
		HashSet<EarleyState>[][] unfinishedChart = new HashSet[states.size()][string.getSize()+1];
		// track whether prediction items are handled
		// index by symbol id and string position
		boolean[][] preds = new boolean[states.size()][string.getSize()+1];
//		HashMap<Integer, HashSet<Integer>> preds = new HashMap<Integer, HashSet<Integer>>();
		
		
		// track how many times things are done
		int initCount=0;
		int predCount=0;
		int popCount=0;
		int concatCount=0;
		int shiftCount=0;
		
		// we'll need these rules for parsing
		CFGRule[] arr = getRuleArr();
		// initialize agenda
		for (Rule rule : getRulesOfType(getStartState())) {
			EarleyState es = EarleyStateFactory.getState(this, (CFGRule) rule, 0, 0, 0);
			if (debug) Debug.debug(debug, "Initializing agenda with "+es);
			initCount++;
			es.isAdded = true;
			agenda.push(es);
	//		addedStates.put(es, es);
		}
//		HashSet<Integer> startint = new HashSet<Integer>();
	//	startint.add(0);
		preds[s2i.get(getStartState())][0] = true;
	//	preds.put(s2i.get(getStartState()), startint);
		while (!agenda.isEmpty()) {
			
//			if (agenda.size() % 1000 == 0) {
//				Debug.prettyDebug(agenda.size()+" in agenda");
//			}
			EarleyState item = agenda.pop();
			popCount++;
			int theSym = -1;
			int theInt = -1;
			HashSet<EarleyState>[][] theChart = null;
		

			if (debug) Debug.debug(debug, "Removing "+item+" from agenda");

			// can we predict anything from this item?
			int predSym = item.predict();
			int prednum = item.stringEndPos;
			if (predSym >= 0 && !preds[predSym][prednum]) {
//			if (predSym >= 0 && (!preds.containsKey(predSym) || !preds.get(predSym).contains(prednum))) {

				// add predictions for this item at this integer position
//				if (!preds.containsKey(predSym))
//					preds.put(predSym, new HashSet<Integer>());
//				preds.get(predSym).add(prednum);
				preds[predSym][prednum] = true;

				if (debug) Debug.debug(debug, "About to get rules starting with "+predSym+" which is "+i2s((short)predSym));
				for (Rule rule : getRulesOfType(i2s((short)predSym))) {

					EarleyState es = EarleyStateFactory.getState(this, (CFGRule) rule, 0, prednum, prednum);
					if (debug) Debug.debug(debug, "\tAdding prediction "+es);
					es.isAdded = true;
					predCount++;
					agenda.push(es);
		//			addedStates.put(es, es);
				}
			}
			// add to finished/unfinished chart if needed 
			if (item.isFinished) {
				theChart = finishedChart;
				theSym = item.src;
				theInt = item.stringStartPos;
				if (debug) Debug.debug(debug, "\tAdding "+item+" to finished chart");
			}
			else if (predSym >= 0){
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
					theChart[theSym][theInt] = new HashSet<EarleyState>();
				theChart[theSym][theInt].add(item);
			}
			
			// can we shift anything from this item?
			// allow shift at end as long as the item reads null
			if(item.stringEndPos <= string.getSize()) {
				Symbol shiftsym = null;
				if (item.stringEndPos < string.getSize())
					shiftsym = string.getSym(item.stringEndPos);
				else
					shiftsym = Symbol.getEpsilon();
				EarleyState shiftstate = item.shift(shiftsym, arr, this);
				if (shiftstate != null) {
					boolean didAdd = false;
					// if the state is already in there, make sure we have the version with
					// all the edges
//					if (addedStates.containsKey(shiftstate))
//						shiftstate = addedStates.get(shiftstate);
					// add the new edges (either to the fresh state or to the recalled one
					shiftstate.update(item);
					// now add the loaded version to the agenda and to the addedStates list
//					if (!addedStates.containsKey(shiftstate)) {
					if (!shiftstate.isAdded) {
						shiftstate.isAdded = true;
						agenda.push(shiftstate);
						shiftCount++;
//						addedStates.put(shiftstate, shiftstate);
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
				if (predSym >= 0) {
					int searchInt = item.stringEndPos;
					if (finishedChart[predSym][searchInt] != null) {
						//							for (EarleyState match : finishedChart.get(predSym).get(searchInt)) {
						for (EarleyState match : finishedChart[predSym][searchInt]) {
							if (debug) Debug.debug(debug, "\tCombining "+item+" and "+match);
							EarleyState combine = EarleyStateFactory.getState(this, arr[item.rule], item.rulepos+1, item.stringStartPos, match.stringEndPos);
							boolean didAdd = false;
							concatCount++;
							// if the state is already in there, make sure we have the version with
							// all the edges
//							if (addedStates.containsKey(combine))
//								combine = addedStates.get(combine);
							// add the new edges (either to the fresh state or to the recalled one
							combine.update(item, match, beam);

							// now add the loaded version to the agenda and to the addedStates list
							if (!combine.isAdded) {
//							if (!addedStates.containsKey(combine)) {
								combine.isAdded = true;
								agenda.push(combine);
//								addedStates.put(combine, combine);
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
//					if (unfinishedChart.containsKey(item.rule.getState()) && unfinishedChart.get(item.rule.getState()).containsKey(searchInt)) {
					if (unfinishedChart[item.src][searchInt] != null) {
						for (EarleyState match : unfinishedChart[item.src][searchInt]) {
							if (debug) Debug.debug(debug, "\tCombining "+match+" and "+item);
							EarleyState combine = EarleyStateFactory.getState(this, arr[match.rule], match.rulepos+1, match.stringStartPos, item.stringEndPos);
							boolean didAdd = false;
							concatCount++;
							// if the state is already in there, make sure we have the version with
							// all the edges
//							if (addedStates.containsKey(combine))
//								combine = addedStates.get(combine);
							// add the new edges (either to the fresh state or to the recalled one
							combine.update(match, item, beam);

							// now add the loaded version to the agenda and to the addedStates list
							if (!combine.isAdded) {
//								if (!addedStates.containsKey(combine)) {
								combine.isAdded = true;
								agenda.push(combine);
//								addedStates.put(combine, combine);
								didAdd = true;
							}
//							if (!addedStates.containsKey(combine)) {
//								agenda.push(combine);
//								addedStates.put(combine, combine);
//								didAdd = true;
//							}
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
//		Debug.prettyDebug("done parsing");
		Date postParseTime = new Date();
		Debug.dbtime(timeLevel, 3, preParseTime, postParseTime, "parse string with cfg");
		//Debug.debug(true, initCount+" init "+predCount+" pred "+shiftCount+" shift "+popCount+" pop "+concatCount+" concat");
		EarleyStateFactory.clear();
		return finishedChart;
	}
	
	
	
	// just parsing. used by training and backwards app.
	// given the cfg (i.e. "this") and string, return the finished chart
	// only add beam best hyperedges for each state.
	
	public HashSet<SlimEarleyState>[][] slimparse(StringItem string, int beam, int timeLevel) {
		boolean debug = false;

		// initialize the earley state factory
//		EarleyStateFactory.init(this, string.getSize());
		Date preParseTime = new Date();
//		Debug.prettyDebug("About to parse "+string+" in cfg with "+states.size()+" states and "+rules.size()+" rules");
		Stack<SlimEarleyState> agenda = new Stack<SlimEarleyState>();
		// track all states that have been added to an agenda. map from the state with no next pointers
		// to the state with all next pointers (which is the state actually added into the agenda) and update
		// the latter. 
		
//		HashMap<EarleyState, EarleyState> addedStates = new HashMap<EarleyState,EarleyState>();
		
		// categorize chart nodes by finished and unfinished variants
		// index by state id and string pos
		HashSet<SlimEarleyState>[][] finishedChart = new HashSet[states.size()][string.getSize()+1];
		
		HashSet<SlimEarleyState>[][] unfinishedChart = new HashSet[states.size()][string.getSize()+1];
		// track whether prediction items are handled
		// index by symbol id and string position
		boolean[][] preds = new boolean[states.size()][string.getSize()+1];
//		HashMap<Integer, HashSet<Integer>> preds = new HashMap<Integer, HashSet<Integer>>();
		
		
		// track how many times things are done
		int initCount=0;
		int predCount=0;
		int popCount=0;
		int concatCount=0;
		int shiftCount=0;
		
		// we'll need these rules for parsing
		CFGRule[] arr = getRuleArr();
		// initialize agenda
		for (Rule rule : getRulesOfType(getStartState())) {
			SlimEarleyState es = SlimEarleyState.getState(this, (CFGRule) rule, (short)0, (short)0, (short)0);
			if (debug) Debug.debug(debug, "Initializing agenda with "+es);
			initCount++;
			es.isAdded = true;
			agenda.push(es);
	//		addedStates.put(es, es);
		}
//		HashSet<Integer> startint = new HashSet<Integer>();
	//	startint.add(0);
		preds[s2i.get(getStartState())][0] = true;
	//	preds.put(s2i.get(getStartState()), startint);
		while (!agenda.isEmpty()) {
			
//			if (agenda.size() % 1000 == 0) {
//				Debug.prettyDebug(agenda.size()+" in agenda");
//			}
			SlimEarleyState item = agenda.pop();
			popCount++;
			int theSym = -1;
			int theInt = -1;
			HashSet<SlimEarleyState>[][] theChart = null;
		

			if (debug) Debug.debug(debug, "Removing "+item+" from agenda");

			// can we predict anything from this item?
			int intpredSym = item.predict();
			short prednum = item.stringEndPos;
			short predSym= (short)intpredSym;
			if (intpredSym >= 0 && !preds[intpredSym][prednum]) {
				
//			if (predSym >= 0 && (!preds.containsKey(predSym) || !preds.get(predSym).contains(prednum))) {

				// add predictions for this item at this integer position
//				if (!preds.containsKey(predSym))
//					preds.put(predSym, new HashSet<Integer>());
//				preds.get(predSym).add(prednum);
				preds[predSym][prednum] = true;

				for (Rule rule : getRulesOfType(i2s(predSym))) {

					SlimEarleyState es = SlimEarleyState.getState(this, (CFGRule) rule, (short)0, prednum, prednum);
					if (debug) Debug.debug(debug, "\tAdding prediction "+es);
					es.isAdded = true;
					predCount++;
					agenda.push(es);
		//			addedStates.put(es, es);
				}
			}
			// add to finished/unfinished chart if needed 
			if (item.isFinished) {
				theChart = finishedChart;
				theSym = item.src;
				theInt = item.stringStartPos;
				if (debug) Debug.debug(debug, "\tAdding "+item+" to finished chart");
			}
			else if (predSym >= 0){
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
					theChart[theSym][theInt] = new HashSet<SlimEarleyState>();
				theChart[theSym][theInt].add(item);
			}
			
			// can we shift anything from this item?
			// allow shift at end as long as the item reads null
			if(item.stringEndPos <= string.getSize()) {
				Symbol shiftsym = null;
				if (item.stringEndPos < string.getSize())
					shiftsym = string.getSym(item.stringEndPos);
				else
					shiftsym = Symbol.getEpsilon();
				SlimEarleyState shiftstate = item.shift(shiftsym, arr, this);
				if (shiftstate != null) {
					boolean didAdd = false;
					// if the state is already in there, make sure we have the version with
					// all the edges
//					if (addedStates.containsKey(shiftstate))
//						shiftstate = addedStates.get(shiftstate);
					// add the new edges (either to the fresh state or to the recalled one
					shiftstate.update(item);
					// now add the loaded version to the agenda and to the addedStates list
//					if (!addedStates.containsKey(shiftstate)) {
					if (!shiftstate.isAdded) {
						shiftstate.isAdded = true;
						agenda.push(shiftstate);
						shiftCount++;
//						addedStates.put(shiftstate, shiftstate);
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
				if (predSym >= 0) {
					int searchInt = item.stringEndPos;
					if (finishedChart[predSym][searchInt] != null) {
						//							for (EarleyState match : finishedChart.get(predSym).get(searchInt)) {
						for (SlimEarleyState match : finishedChart[predSym][searchInt]) {
							if (debug) Debug.debug(debug, "\tCombining "+item+" and "+match);
							SlimEarleyState combine = SlimEarleyState.getState(this, arr[item.rule], (short)(item.rulepos+1), item.stringStartPos, match.stringEndPos);
							boolean didAdd = false;
							concatCount++;
							// if the state is already in there, make sure we have the version with
							// all the edges
//							if (addedStates.containsKey(combine))
//								combine = addedStates.get(combine);
							// add the new edges (either to the fresh state or to the recalled one
							combine.update(item, match, beam);

							// now add the loaded version to the agenda and to the addedStates list
							if (!combine.isAdded) {
//							if (!addedStates.containsKey(combine)) {
								combine.isAdded = true;
								agenda.push(combine);
//								addedStates.put(combine, combine);
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
//					if (unfinishedChart.containsKey(item.rule.getState()) && unfinishedChart.get(item.rule.getState()).containsKey(searchInt)) {
					if (unfinishedChart[item.src][searchInt] != null) {
						for (SlimEarleyState match : unfinishedChart[item.src][searchInt]) {
							if (debug) Debug.debug(debug, "\tCombining "+match+" and "+item);
							SlimEarleyState combine = SlimEarleyState.getState(this, arr[match.rule], (short)(match.rulepos+1), match.stringStartPos, item.stringEndPos);
							boolean didAdd = false;
							concatCount++;
							// if the state is already in there, make sure we have the version with
							// all the edges
//							if (addedStates.containsKey(combine))
//								combine = addedStates.get(combine);
							// add the new edges (either to the fresh state or to the recalled one
							combine.update(match, item, beam);

							// now add the loaded version to the agenda and to the addedStates list
							if (!combine.isAdded) {
//								if (!addedStates.containsKey(combine)) {
								combine.isAdded = true;
								agenda.push(combine);
//								addedStates.put(combine, combine);
								didAdd = true;
							}
//							if (!addedStates.containsKey(combine)) {
//								agenda.push(combine);
//								addedStates.put(combine, combine);
//								didAdd = true;
//							}
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
//		Debug.prettyDebug("done parsing");
		Date postParseTime = new Date();
		Debug.dbtime(timeLevel, 3, preParseTime, postParseTime, "parse string with cfg");
		//Debug.debug(true, initCount+" init "+predCount+" pred "+shiftCount+" shift "+popCount+" pop "+concatCount+" concat");
		SlimEarleyState.clear();
		return finishedChart;
	}
	
	
	
	// do earley parsing on the provided CFG and StringItem, returning a CFG that covers this string
	public CFGRuleSet(CFGRuleSet cfg, StringItem string, int beam, int timeLevel) throws UnusualConditionException {
		boolean debug = false;
		semiring = cfg.semiring;
		nextRuleIndex = 0;


//		Debug.prettyDebug("About to parse");
		HashSet<SlimEarleyState>[][] finishedChart = cfg.slimparse(string, beam, timeLevel);
//		Debug.prettyDebug("Done parsing");
		// assume s2i and i2s were properly set
		Date preRecoverTime = new Date();
		// traverse the chart from finished start symbols. When encountering a new rule, look down leftmost chain 
		Symbol[][][] newSyms = new Symbol[cfg.states.size()][string.getSize()+1][string.getSize()+1];
		ArrayList<SlimEarleyState> todoList = new ArrayList<SlimEarleyState>();

		if (finishedChart[cfg.s2i(cfg.startState)][0] != null) {
			for (SlimEarleyState item : finishedChart[cfg.s2i(cfg.startState)][0]) {
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
	
		
		HashSet<CFGRule> ruleSet = new HashSet<CFGRule>();
		while (todoList.size() > 0) {
			SlimEarleyState item = todoList.remove(0);
			if (debug) Debug.debug(debug, "Adding CFG Rule for "+item);
			Vector<CFGRule> newrule = item.buildCFG(cfg, newSyms, todoList, this, cfg.getRuleArr());
			if (debug) Debug.debug(debug, "Created new CFG Rule "+newrule);
			ruleSet.addAll(newrule);
		}
		Date postRecoverTime = new Date();
		Debug.dbtime(timeLevel, 3, preRecoverTime, postRecoverTime, "recover cfg rules from Earley chart");
		// now that all buildCFG has been done, get start state
		if (startState == null)
			startState = cfg.startState;
		rules = new ArrayList<Rule>(ruleSet);
		if (debug) Debug.debug(debug, "after construction: "+rules.size()+" rules");
		initialize();
		if (debug) Debug.debug(debug, "after init: "+rules.size()+" rules");
		if (rules != null && startState != null)
			pruneUseless();	
		if (debug) Debug.debug(debug, "after prune: "+rules.size()+" rules");


	}
	
	
	
	public static void main(String argv[]) {
		try {
			RealSemiring s = new RealSemiring();

			// test isFinite
			CFGRuleSet cfg = new CFGRuleSet(new BufferedReader(new InputStreamReader(new FileInputStream(argv[0]), "utf-8")), s);
			//Debug.debug(true, cfg.toString());
			Debug.prettyDebug("Finiteness of grammar is "+cfg.isFinite(false));
			Debug.prettyDebug("Monadic-chain-freeness of grammar is "+cfg.isFinite(true));			
			
			// test backward app
	/*		CFGRuleSet cfg = new CFGRuleSet(new BufferedReader(new InputStreamReader(new FileInputStream(argv[0]), "utf-8")), s);
			Debug.debug(true, cfg.toString());
			File f = new File(argv[1]);
			Vector v = StringItem.readStringSet(f, "utf-8");
			ObjectInputStream ois = new ObjectInputStream(new FileInputStream((File)v.get(0)));
			StringItem str = (StringItem)ois.readObject();
			Debug.debug(true, str.toString());
			CFGRuleSet parse = new CFGRuleSet(cfg, str);
			Debug.prettyDebug(parse.toString());*/
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