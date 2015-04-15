package edu.isi.tiburon;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TransducerRightString extends TransducerRightSide {

	private TransducerRightString next;
	private TransducerRightString nextTerm;
	// TODO: be able to set this!!
	private boolean isEpsilon=false;
	public boolean isEpsilon() { return isEpsilon; }
	void setHashCode() {
		hsh = new Hash();
		if (variable != null) {
			hsh = hsh.sag(variable.getHash());
			hsh = hsh.sag(state.getHash());
		}
		// label not relevant if a variable exists
		else if (label != null) {
			hsh = hsh.sag(label.getHash());
		}
		// nextTerm is just a convenience pointer; not new data. so only include next in the hash
		hsh = hsh.sag(next == null ? new Hash() : next.getHash());
	}

	// equal if label and/or variable are same and tail is the same

	public boolean equals(Object o) {
		if (!o.getClass().equals(this.getClass()))
			return false;
		boolean debug = false;
		TransducerRightString t = (TransducerRightString)o;
		if (debug) Debug.debug(debug, "Comparing "+toString()+" to "+t.toString());
		if (isEpsilon ^ t.isEpsilon)
			return false;
		if (isEpsilon && t.isEpsilon)
			return true;
		if (variable == null ^ t.variable == null)
			return false;
		if (debug) Debug.debug(debug, "passed variable null check");
		if (variable != null && (!variable.equals(t.variable) || !state.equals(t.state))){
			if (debug) Debug.debug(debug, "returning false");
			return false;
		}
		if (debug) Debug.debug(debug, "passed variable and state check");
		// label only relevant if variable doesn't exist
		if (variable == null) {
			if (label == null ^ t.label == null)
				return false;
			if (debug) Debug.debug(debug, "passed label null check");
			if (label != null && !label.equals(t.label))
				return false;
			if (debug) Debug.debug(debug, "passed label check");
		}
		if (next == null ^ t.next == null)
			return false;
		if (debug) Debug.debug(debug, "passed next null check");
		if (next != null && !next.equals(t.next))
			return false;
		if (debug) Debug.debug(debug, "passed next check");
		if (debug) Debug.debug(debug, "Returning true");
		return true;
	}

	// separate into individual bits -- bits can be:
	// 1) a raw symbol with no (), ., *, % and no spaces
	// 2) a pair of double quotes with anything except another double
	//    quote (or %) in between
	// 3) a triple double quote
	// 4) any two of the above joined by .
	// 5) special symbol *e*
	private static Pattern stringSepPat = Pattern.compile("\\G((?:(?:^\\s*\\*e\\*\\s*$|[^\\s\\(\\)\".@]+|\"\"\"|\"[^\"]+\")(?:\\.(?:[^\\s\\(\\)\".@]+|\"\"\"|\"[^\"]+\"))?))\\s*");

//	private static Pattern stringSepPat = Pattern.compile("(\\S+)\\s*");

	public TransducerRightString(StringBuffer text, HashSet<Symbol> states, TransducerRuleVariableMap trvm) throws DataFormatException {
		boolean debug = false;

		// build a vector, then build recursively from the back, just like the constructor for trainingstring

		Vector<String> v = new Vector<String>();
		Matcher match = stringSepPat.matcher(text);
		boolean hitEnd = false;
		while (match.find()) {
			if (debug) Debug.debug(debug, "Matched "+match.group(1)+" in "+text);
			hitEnd = match.hitEnd();
			v.add(match.group(1));
		}

		if (v.size() == 0)
			throw new DataFormatException("Didn't match anything into TransducerRightString: "+text);
		if (!hitEnd)
			throw new DataFormatException("Didn't match entire string into TransducerRightString: "+text);
		// now build them up

		TransducerRightString lasttrs = null;
		for (int i =  v.size()-1; i > 0; i--) {
			try {
				TransducerRightString currtrs = new TransducerRightString(lasttrs, states, v.get(i));
				if (trvm != null && currtrs.hasVariable()) {
					if (debug) Debug.debug(debug, "Adding variable info to trvm");
					trvm.addRHS(currtrs, false);
					// this isn't done for trees, so it shouldn't be done here...
					// implicit label set
					// 		    if (trvm.isValid())
					// 			currtrs.setLabel(trvm.getLHSLabel(currtrs));
				}
				lasttrs = currtrs;
			}
			catch (DataFormatException e) {
				throw new DataFormatException(e.getMessage()+" in "+text);
			}
		}
		// last one -- make sure trvm is this object rather than exploded form
		try {
			TransducerRightString currtrs = new TransducerRightString(lasttrs, states, v.get(0));
			// explode the current one to this
			label = currtrs.label;
			variable = currtrs.variable;
			state = currtrs.state;
			next = currtrs.next;
			nextTerm = currtrs.nextTerm;
			isEpsilon = currtrs.isEpsilon;
			// and add it to trvm
			if (trvm != null && hasVariable()) {
				if (debug) Debug.debug(debug, "Adding variable info to trvm");
				trvm.addRHS(this, false);
			}
		}
		catch (DataFormatException e) {
			throw new DataFormatException(e.getMessage()+" in "+text);
		}

		
		// finally, explode the current one to this
//		label = lasttrs.label;
//		variable = lasttrs.variable;
//		state = lasttrs.state;
//		next = lasttrs.next;
//		nextTerm = lasttrs.nextTerm;
//		isEpsilon = lasttrs.isEpsilon;
//		lasttrs = this;
		//	    hshcode = lasttrs.hshcode;
		text.delete(0, text.length());
	}


	
	// an item being processed here is either:

	// 1) a raw symbol with no (), ., %  and no spaces or a quoted symbol
	// terminal (non-variable) pattern
	private static Pattern termPat = Pattern.compile("([^\\s\\(\\)\"\\.@]+|\"\"\"|\"[^\"]+\")");

	// 2) state, variable pair, separated by a .
	private static Pattern varPat = Pattern.compile("([^\\s\\(\\)\"\\.@]+|\"\"\"|\"[^\"]+\")\\.([^\\s\\(\\)\"\\.]+|\"\"\"|\"[^\"]+\")");

	// 3) epsilon symbol *e* (no pattern needed, literal match okay)
	private static String epsPat = "*e*";

	// the recursive constructor
	private TransducerRightString(TransducerRightString nxt, HashSet<Symbol> states, String text) throws DataFormatException {
		boolean debug = false;
		// BEGIN FASTER REGEX CODE
		Matcher varMatch = varPat.matcher(text);
		Matcher termMatch = termPat.matcher(text);
		if (text.equals(epsPat)) {
			if (debug) Debug.debug(debug, "Read epsilon");
			isEpsilon = true;
			label = Symbol.getEpsilon();
			variable = state = null;
			if (nxt != null)
				throw new DataFormatException("Saw epsilon with other elements!");
		}
		else if (varMatch.matches()) {
			if (debug) Debug.debug(debug, "Read state "+varMatch.group(1)+" and variable "+varMatch.group(2));
			state = SymbolFactory.getSymbol(varMatch.group(1));
			states.add(state);
			variable = SymbolFactory.getSymbol(varMatch.group(2));
			label = null;
		}
		else if (termMatch.matches()) {
			if (debug) Debug.debug(debug, "Read symbol "+termMatch.group(1));
			label = SymbolFactory.getSymbol(termMatch.group(1));
			variable = state = null;
		}
		else {
			if (debug) Debug.debug(debug, "Couldn't read "+text);
			throw new DataFormatException(text+" not a valid transducer right string element");
		}
		next = nxt;
		if (next == null || !next.hasVariable())
			nextTerm = next;
		else
			nextTerm = next.nextTerm;
		if (next != null && next.isEpsilon())
			throw new DataFormatException("Saw epsilon with other elements!");	
	}

	// simple symbol constructor
	public TransducerRightString(Symbol sym) {
		label = sym;
		variable = state = null;
		next = nextTerm = null;
	}

	// simple single variable constructor (used for output epsilon construction only)
	public TransducerRightString(Symbol var, Symbol st) {
		label = null;
		variable = var;
		state = st;
		next = nextTerm = null;
	}
	

	// simple item constructor used by conversion below
	public TransducerRightString(TransducerRightString nxt, Symbol lbl, Symbol vbl, Symbol st) {
		boolean debug = false;
		label = lbl;
		variable = vbl;
		state = st;
		next = nxt;
		if (next == null || !next.hasVariable())
			nextTerm = next;
		else
			nextTerm = next.nextTerm;
	}

	// recursive clone constructor
	public TransducerRightString(TransducerRightString s) {
		label = s.label;
		variable = s.variable;
		state = s.state;
		if (s.next == null) {
			next = null;
			nextTerm = null;
		}
		else {
			next = new TransducerRightString(s.next);
			if (!next.hasVariable())
				nextTerm = next;
			else
				nextTerm = next.nextTerm;
		}
	}
	
	// conversion for RTG rule-based trees
	// create string from yield of t, replacing states with state/variables
	public TransducerRightString(TreeItem t, TransducerRuleVariableMap trvm, HashSet states) {
		Symbol [] leaves = t.getLeaves();

		TransducerRightString lasttrs = null;
		// must add the items in backwards but number the variables to match the 
		// tlt, which must be added in forwards. thus we have to lower the nextVar
		// and hence the double traversal
		int nextVar = -1;
		for (int i = 0; i < leaves.length; i++) {
			if (states.contains(leaves[i]))
				nextVar++;
		}
		for (int i =  leaves.length-1; i > 0; i--) {
			TransducerRightString currtrs;
			if (states.contains(leaves[i])) {
				currtrs = new TransducerRightString(lasttrs, null, SymbolFactory.getSymbol("x"+(nextVar--)), leaves[i]);
				trvm.addRHS(currtrs, false);
			}
			else
				currtrs = new TransducerRightString(lasttrs, leaves[i], null, null);
			lasttrs = currtrs;
		}
		// last iter explodes first
		TransducerRightString currtrs;
		if (states.contains(leaves[0])) {
			currtrs = new TransducerRightString(lasttrs, null, SymbolFactory.getSymbol("x"+(nextVar--)), leaves[0]);
			// explode the current one to this
			label = currtrs.label;
			variable = currtrs.variable;
			state = currtrs.state;
			next = currtrs.next;
			nextTerm = currtrs.nextTerm;
			trvm.addRHS(this, false);
		}
		else {
			currtrs = new TransducerRightString(lasttrs, leaves[0], null, null);
			label = currtrs.label;
			variable = currtrs.variable;
			state = currtrs.state;
			next = currtrs.next;
			nextTerm = currtrs.nextTerm;
		}
//		lasttrs = currtrs;
		
//		// finally, explode the current one to this
//		label = lasttrs.label;
//		variable = lasttrs.variable;
//		state = lasttrs.state;
//		next = lasttrs.next;
//		nextTerm = lasttrs.nextTerm;
	}

	
	// for epsilon-input rules in composition
	// build TRT from old TRT, join of its states and the passed in state, and replacement of its variables 
	// with this one. Done from back to front!
	public TransducerRightString(StringTransducerRuleSet trs, TransducerRightString oldrhs, 
			TransducerRuleVariableMap trvm, Symbol aState, 
			Symbol var) {
		boolean debug = false;
		
		TransducerRightString lasttrs = null;
		int nextVar = 0;
		Vector<TransducerRightString> leaves = oldrhs.getItemLeaves();
		for (int i = leaves.size()-1; i > 0; i--) {
			TransducerRightString old = leaves.get(i);
			TransducerRightString currtrs;
			if (old.hasState()) {
				Vector<Symbol> stvec = new Vector<Symbol>();
				stvec.add(aState);
				stvec.add(old.getState());
				currtrs = new TransducerRightString(lasttrs, old.getLabel(), old.getVariable(), SymbolFactory.getVecSymbol(stvec)); 
				trvm.addRHS(currtrs, false);
			}
			else {
				currtrs = new TransducerRightString(lasttrs, old.getLabel(), null, null);
			}
			lasttrs = currtrs;
		}
		// last iter explodes first
		TransducerRightString old = leaves.get(0);
		TransducerRightString currtrs;
		if (old.hasState()) {
			Vector<Symbol> stvec = new Vector<Symbol>();
			stvec.add(aState);
			stvec.add(old.getState());
			currtrs = new TransducerRightString(lasttrs, old.getLabel(), old.getVariable(), SymbolFactory.getVecSymbol(stvec)); 
			// explode the current one to this
			label = currtrs.label;
			variable = currtrs.variable;
			state = currtrs.state;
			next = currtrs.next;
			nextTerm = currtrs.nextTerm;
			trvm.addRHS(this, false);
		}
		else {
			currtrs = new TransducerRightString(lasttrs, old.getLabel(), null, null);
			// explode the current one to this
			label = currtrs.label;
			variable = currtrs.variable;
			state = currtrs.state;
			next = currtrs.next;
			nextTerm = currtrs.nextTerm;
		}
		
	}

	// for composition
	// create a TRS out of the string. Replace terminal symbols with appropriate variable syntax
	// and join the state referenced in the A transducer with the one found in the B map.
	public TransducerRightString(StringItem string, TransducerRuleVariableMap trvm,
			HashMap<Symbol, TransducerRightSide> amap, 
			HashMap<Symbol, StateTreePair> bmap) throws ImproperConversionException {
		boolean debug = false;
		if (debug) Debug.debug(debug, "Making TRS out of "+string);
		// special case for empty string
		if (string.isEmptyString()) {
			if (debug) Debug.debug(debug, string+" is empty, so making empty TRS");
			label = null;
			variable = null;
			state = null;
			next = null;
			isEpsilon = true;
			nextTerm = null;
			return;
		}	
		Symbol[] leaves = string.getLeaves();
		// build TRS from inside out
		TransducerRightString lasttrs = null;
		int nextVar = 0;
		for (int i =  leaves.length-1; i > 0; i--) {
			TransducerRightString currtrs;
			// if found in bmap, get the rule, break it into state and label,
			//                get the TRS from the label and the variable with the oldtrvm
			if (bmap.containsKey(leaves[i])) {
				StateTreePair stp = bmap.get(leaves[i]);
				TransducerRightTree oldVar = (TransducerRightTree)amap.get(stp.getTree().getLabel());
				if (oldVar == null)
					throw new ImproperConversionException("No mapping for "+stp.getTree().getLabel());
				Vector<Symbol> vecstate = new Vector<Symbol>();
				vecstate.add(oldVar.getState());
				vecstate.add(stp.getState());
				VecSymbol currst = SymbolFactory.getVecSymbol(vecstate);
				variable = oldVar.getVariable();
				currtrs = new TransducerRightString(lasttrs, null, variable, currst);
				if (debug) Debug.debug(debug, " build variable-rich "+currtrs);
				trvm.addRHS(currtrs, false);
			}
			else {
				currtrs = new TransducerRightString(lasttrs, leaves[i], null, null);
				if (debug) Debug.debug(debug, " build variable-free "+currtrs);
			}
			lasttrs = currtrs;
		}
		// last iter explodes first
		TransducerRightString currtrs;
		// if found in bmap, get the rule, break it into state and label,
		//                get the TRS from the label and the variable with the oldtrvm
		if (bmap.containsKey(leaves[0])) {
			StateTreePair stp = bmap.get(leaves[0]);
			TransducerRightTree oldVar = (TransducerRightTree)amap.get(stp.getTree().getLabel());
			if (oldVar == null)
				throw new ImproperConversionException("No mapping for "+stp.getTree().getLabel());
			Vector<Symbol> vecstate = new Vector<Symbol>();
			vecstate.add(oldVar.getState());
			vecstate.add(stp.getState());
			VecSymbol currst = SymbolFactory.getVecSymbol(vecstate);
			variable = oldVar.getVariable();
			currtrs = new TransducerRightString(lasttrs, null, variable, currst);
			// explode the current one to this
			label = currtrs.label;
			variable = currtrs.variable;
			state = currtrs.state;
			next = currtrs.next;
			nextTerm = currtrs.nextTerm;
			if (debug) Debug.debug(debug, " build variable-rich "+currtrs);
			trvm.addRHS(this, false);
		}
		else {
			currtrs = new TransducerRightString(lasttrs, leaves[0], null, null);
			// explode the current one to this
			label = currtrs.label;
			variable = currtrs.variable;
			state = currtrs.state;
			next = currtrs.next;
			nextTerm = currtrs.nextTerm;
			if (debug) Debug.debug(debug, " build variable-free "+currtrs);
		}
//		// finally, explode the current one to this
//		label = lasttrs.label;
//		variable = lasttrs.variable;
//		state = lasttrs.state;
//		next = lasttrs.next;
//		nextTerm = lasttrs.nextTerm;
	}

	// 
	// for multi-transducer leftside composition
	// create a TRT out of the tree. Replace special terminal symbols with appropriate variable syntax
	// and join the state referenced in the A transducer with the one found in the B map.
	public TransducerRightString(StringItem string, TransducerRuleVariableMap trvm) throws ImproperConversionException {

		boolean debug = false;
		if (debug) Debug.debug(debug, "Making TRS out of "+string);
		// special case for empty string
		if (string.isEmptyString()) {
			if (debug) Debug.debug(debug, string+" is empty, so making empty TRS");
			label = Symbol.getEpsilon();
			variable = null;
			state = null;
			next = null;
			isEpsilon = true;
			nextTerm = null;
			return;
		}	
		Symbol[] leaves = string.getLeaves();
		// build TRS from inside out
		TransducerRightString lasttrs = null;
		int nextVar = 0;
		for (int i =  leaves.length-1; i > 0; i--) {
			TransducerRightString currtrs;
			// if found in bmap, get the rule, break it into state and label,
			//                get the TRS from the label and the variable with the oldtrvm
			if (leaves[i] instanceof VecSymbol) {
				Vector<Symbol> vecstate = new Vector<Symbol>(((VecSymbol)leaves[i]).getVec());
				variable = vecstate.remove(0);
				VecSymbol currst = SymbolFactory.getVecSymbol(vecstate);
				trvm.addRHS(this, false);
				currtrs = new TransducerRightString(lasttrs, null, variable, currst);
				if (debug) Debug.debug(debug, " build variable-rich "+currtrs);
				trvm.addRHS(currtrs, false);
			}
			else {
				currtrs = new TransducerRightString(lasttrs, leaves[i], null, null);
				if (debug) Debug.debug(debug, " build variable-free "+currtrs);
			}
			lasttrs = currtrs;
		}
		// last iter explodes first
		TransducerRightString currtrs;
		// if found in bmap, get the rule, break it into state and label,
		//                get the TRS from the label and the variable with the oldtrvm
		if (leaves[0] instanceof VecSymbol) {
			Vector<Symbol> vecstate = new Vector<Symbol>(((VecSymbol)leaves[0]).getVec());
			variable = vecstate.remove(0);
			VecSymbol currst = SymbolFactory.getVecSymbol(vecstate);
			trvm.addRHS(this, false);
			currtrs = new TransducerRightString(lasttrs, null, variable, currst);
			if (debug) Debug.debug(debug, " build variable-rich "+currtrs);
			// explode the current one to this
			label = currtrs.label;
			variable = currtrs.variable;
			state = currtrs.state;
			next = currtrs.next;
			nextTerm = currtrs.nextTerm;
			trvm.addRHS(this, false);
		}
		else {
			currtrs = new TransducerRightString(lasttrs, leaves[0], null, null);
			if (debug) Debug.debug(debug, " build variable-free "+currtrs);
			// explode the current one to this
			label = currtrs.label;
			variable = currtrs.variable;
			state = currtrs.state;
			next = currtrs.next;
			nextTerm = currtrs.nextTerm;
		}
//		// finally, explode the current one to this
//		label = lasttrs.label;
//		variable = lasttrs.variable;
//		state = lasttrs.state;
//		next = lasttrs.next;
//		nextTerm = lasttrs.nextTerm;
	}


	// for input epsilon rightside chain composition
	// build TRT from old TRT, with no trvm update, and adding the specified state
	// to all variables in the rhs
	
	// no trvm update, so no early explosion
	public TransducerRightString(TransducerRightString oldrhs, Symbol aState) {
		boolean debug = false;
		if (debug) Debug.debug(debug, "Making TRS out of "+oldrhs);
		TransducerRightString lasttrs = null;
		Vector<TransducerRightString> leaves = oldrhs.getItemLeaves();
		for (int i = leaves.size()-1; i >= 0; i--) {
			TransducerRightString old = leaves.get(i);
			TransducerRightString currtrs;
			if (old.hasVariable()) {
				VecSymbol newstate = null;
				if (old.getState() instanceof VecSymbol)
					newstate = SymbolFactory.getVecSymbol(aState, ((VecSymbol)old.getState()));
				else {
					Vector<Symbol> vec = new Vector<Symbol>();
					vec.add(aState);
					vec.add(old.getState());
					newstate = SymbolFactory.getVecSymbol(vec);
				}
				currtrs = new TransducerRightString(lasttrs, old.getLabel(), old.getVariable(), newstate);
			}
			else {
				currtrs = new TransducerRightString(lasttrs, old.getLabel(), null, null);
			}
			lasttrs = currtrs;
		}
		// finally, explode the current one to this
		label = lasttrs.label;
		variable = lasttrs.variable;
		state = lasttrs.state;
		next = lasttrs.next;
		nextTerm = lasttrs.nextTerm;
	}
	
	
	
	// for rightside chain composition
	// given tree with VecSymbol leaves and existing TRS, make new TRS that passes in the states
	// at the leaves of the tree to the current state symbols
	
	// also, strip those symbols from the tree
	// no trvm update, so no early explosion
	TransducerRightString(TreeItem t, TransducerRightString oldrhs) {
		boolean debug = false;
		// first, map from variables to the new states that will be added
		if (debug) Debug.debug(debug, "Before building varstatemap: "+t);
		HashMap<Symbol, Symbol> map = buildVarStateMap(t);
		if (debug) Debug.debug(debug, "After building varstatemap: "+t);

		// now build the new trs from the old
		if (debug) Debug.debug(debug, "Making TRS out of "+oldrhs);
		TransducerRightString lasttrs = null;
		Vector<TransducerRightString> leaves = oldrhs.getItemLeaves();
		for (int i = leaves.size()-1; i >= 0; i--) {
			TransducerRightString old = leaves.get(i);
			TransducerRightString currtrs=null;
			if (old.hasVariable()) {
				VecSymbol newstate = null;
				if (old.getState() instanceof VecSymbol)
					newstate = SymbolFactory.getVecSymbol(map.get(old.getVariable()), (VecSymbol)old.getState());
				else {
					Vector<Symbol> vec = new Vector<Symbol>();
					vec.add(map.get(old.getVariable()));
					vec.add(old.getState());
					newstate = SymbolFactory.getVecSymbol(vec);
				}
				currtrs = new TransducerRightString(lasttrs, old.getLabel(), old.getVariable(), newstate);
			}
			else {
				currtrs = new TransducerRightString(lasttrs, old.getLabel(), null, null);
			}
			lasttrs = currtrs;
		}
		// finally, explode the current one to this
		label = lasttrs.label;
		variable = lasttrs.variable;
		state = lasttrs.state;
		next = lasttrs.next;
		nextTerm = lasttrs.nextTerm;
		if (debug) Debug.debug(debug, "Replaced "+oldrhs+" with "+toString());
		return;
	}
	
	// grab variables and states out of leaves and map them
	private HashMap<Symbol, Symbol> buildVarStateMap(TreeItem t) {
		HashMap<Symbol, Symbol> map = new HashMap<Symbol, Symbol>();
		return buildVarStateMap(map, t);	
	}
	// grab variables and states out of leaves and map them
	private HashMap<Symbol, Symbol> buildVarStateMap(HashMap<Symbol, Symbol> map, TreeItem t) {
		if (t.numChildren > 0) {
			for (int i = 0; i < t.numChildren; i++) {
				map = buildVarStateMap(map, t.getChild(i));
			}
		}
		else {
			if (t.label instanceof VecSymbol) {
				Vector<Symbol> vec = new Vector<Symbol>(((VecSymbol)t.label).getVec());
				map.put(vec.firstElement(), vec.lastElement());
				// remove state from map
				vec.remove(vec.size()-1);
				t.setLabel(SymbolFactory.getVecSymbol(vec));
			}
		}
		return map;
	}
	
	public String toString() {
		return toString(1);
	}

	public String toStateFreeString() {
		return toString(0);
	}
	// can display varying levels of explicity   
	public String toString(int exlv) {
		if (isEpsilon)
			return "*e*";
		StringBuffer l = new StringBuffer();
		if (state != null && variable != null) {
			if (exlv >= 1)
				l.append(state.toString()+"."+variable.toString());
			else
				l.append(variable.toString());
			if (exlv >= 2 && label != null)
				l.append("["+label.toString()+"]");
		}
		else if (label != null)
			l.append(label.toString());
		if (next != null)
			l.append(" "+next.toString(exlv));
		return l.toString();
	}

	public TransducerRightString next() { return next; }
	public TransducerRightString nextTerminal() { return nextTerm; }

	// could this right side represent the training string?
	// algorithm: if is literal, must match head label of string and then check remainder recursively
	// if head is variable (without label), next non-variable must either match head or next instance or next...
	public boolean isStringMatch(TrainingString ts) {
		return isStringMatch(ts, false);
	}

	public boolean isStringMatch(TrainingString ts, boolean debug) {
		if (debug) {
			if (ts != null) Debug.debug(true, "Checking "+toString()+" against "+ts.toString());
			else Debug.debug(true, "Checking "+toString()+" against a null training string");
		}
		// epsilon: training string must be epsilon
		if (isEpsilon) {
			if (debug) Debug.debug(debug, "Rule is epsilon");
			if (ts.isEpsilon()) {
				if (debug) Debug.debug(debug, "string is epsilon, so match");
				return true;
			}
			if (debug) Debug.debug(debug, "string is not epsilon, so no match");
			return false;
		}
		// 	// if training string is epsilon, rule can be epsilon or can have variables only
		// 	if (ts == null || ts.isEpsilon()) {
		// 	    if (debug) Debug.debug(debug, "string is epsilon or null, but rule is not, so no match");
		// 	    return false;
		// 	}
		// otherwise don't bother
		// literal: must match and remainder must match
		if (hasLabel() && !hasVariable()) {
			if (debug) Debug.debug(debug, "Literal match to "+label.toString());
			if (ts == null || ts.isEpsilon() || !getLabel().equals(ts.getLabel())) {
				if (debug) Debug.debug(debug, "null or mismatch");
				return false;
			}
			TrainingString nextString = ts.next();
			// if both nexts are null, match is good
			// if our next is null but string's isn't, match is bad
			// if our next isn't null, recurse
			if (next == null) {
				if (debug) Debug.debug(debug, "End of rule. Match good if string is empty");
				return (nextString == null);
			}
			else {
				if (debug) Debug.debug(debug, "Recursive check on "+next.toString());
				return next.isStringMatch(nextString, debug);
			}
		}
		// variable: next literal/terminal, if it exists, must match this entire string (i.e. this variable matches empty)
		// or must match one of the next instances of that literal (i.e. this variable matches everything up to that)
		// if no more literals, okay (because this variable could match the remainder, no matter what
		else if (hasVariable()) {
			if (debug) Debug.debug(debug, "Variable match to "+variable.toString());
			if (nextTerm == null) {
				if (debug) Debug.debug(debug, "No next terminal. match okay");	
				return true;
			}
			// if no more string, this is bad because the next term must match something
			if (ts == null || ts.isEpsilon()) {
				if (debug) Debug.debug(debug, "No more string. match bad");
				return false;
			}
			// first try to match with this variable (and any adjacent variables) aligned to empty string
			if (nextTerm.isStringMatch(ts, debug)) {
				if (debug) Debug.debug(debug, "Matched with "+variable.toString()+" aligned to empty");
				return true;
			}
			// now try to match with this variable (and any adjacent variables) aligned to progressively larger blocks
			TrainingString nextString = ts.next(nextTerm.label);
			while (nextString != null) {
				if (nextTerm.isStringMatch(nextString, debug)) {
					if (debug) Debug.debug(debug, "Matched variable and subsequent "+nextTerm.toString()+" aligned to "+nextString.toString());
					return true;
				}
				if (debug) Debug.debug(debug, "Couldn't match subsequent "+nextTerm.toString()+" aligned to "+nextString.toString());
				nextString = nextString.next(nextTerm.label);
			}
			return false;
		}
		Debug.debug(true, "TransducerRightString: shouldn't be here");
		return false;
	}
	// analogue of TransducerRightTree's getLeaves. there are no leaves in a transducer right string, so this is really just
	// assembling a list of symbols of labels or states, depending on which you have
	// using an array is an unfortunate choice. i'll start as a vector and convert at the end
	// put string into a vector of items. useful for random access. memoized.
	private Symbol[] leaves = null;
	public Symbol[] getLeaves() {
		boolean debug = false;
		if (leaves != null)
			return leaves;
		Vector v = new Vector();
		TransducerRightString pointer = this;
		while (pointer != null) {
			// state is most important
			if (pointer.hasState()) {
				v.add(pointer.getState());
				if (debug) Debug.debug(debug, "TRS getLeaves(): adding state "+pointer.getState().toString()+" to vector");
			}
			// if no state, it should be a label
			else if (pointer.hasLabel()) {
				v.add(pointer.getLabel());
				if (debug) Debug.debug(debug, "TRS getLeaves(): adding label "+pointer.getLabel().toString()+" to vector");
			}
			// if it's an empty string, it's okay as long as this is the first child
			else if (v.size() == 0) {
				if (debug) Debug.debug(debug, "TRS getLeaves(): quitting because we are epsilon");
				break;
			}
			else {
				Debug.debug(true, "Problem while running getLeaves for TransducerRightString: leaf symbol has neither label nor state but has siblings: "+toString());
				System.exit(1);
			}
			pointer = pointer.next();
		}
		leaves = new Symbol[v.size()];
		if (debug) Debug.debug(debug, "TRS getLeaves(): copying vector into array");
		if (v.size() > 0)
			v.copyInto(leaves);
		return leaves;
	}
	// get the ith member of the string. could cause OutOfBounds Exception
	public Symbol getLabelSym(int i) {
		if (itemLeaves == null)
			getItemLeaves();
		return itemLeaves.get(i).getLabel();
	}
	
	// put string into a vector of items. useful for random access. memoized.
	private Vector<TransducerRightString> itemLeaves = null;
	public Vector<TransducerRightString> getItemLeaves() {
		boolean debug = false;
		if (itemLeaves == null) {
			itemLeaves = new Vector<TransducerRightString>();
			TransducerRightString walker = this;
			while (walker != null) {
				itemLeaves.add(walker);
				walker = walker.next();
			}
		}
		if (debug) Debug.debug(debug, "Vector representation of "+this+" is "+itemLeaves);
		return itemLeaves;
	}
	public int getSize() {
		return getItemLeaves().size();
	}

	// return a string by turning variable/state children into vectors
	// for left-oriented general epsilon input adds: insert the Vector of state symbols in front of the extant state
	public StringItem getEpsInputVecVarImageString(Vector<Symbol> states) {
		// recursively create; next item caused by recursion
		Symbol car = null;
		if (hasVariable()) {
			Vector<Symbol> vec = new Vector<Symbol>();
			vec.add(getVariable());
			for (Symbol s : states)
				vec.add(s);
			vec.add(getState());
			car = SymbolFactory.getVecSymbol(vec);
		}
		else {
			car = getLabel();
		}
		if (next == null)
			return new StringItem(car);
		else
			return new StringItem(car, next.getEpsInputVecVarImageString(states));
	}
	

}
