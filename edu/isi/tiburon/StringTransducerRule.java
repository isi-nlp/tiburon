package edu.isi.tiburon;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import gnu.trove.TIntHashSet;
import gnu.trove.TIntIterator;

// transducer rule - a state, lhs, rhs, variable set, and weight
// difference between StringTransducerRule and TreeTransducerRule is the former has a TransducerRightString

public class StringTransducerRule extends TransducerRule {

    //    private TransducerRightString rhs;
    void setHashCode() {
	hsh = getState() == null ? new Hash() : new Hash(getState().getHash());
	hsh = hsh.sag(getLHS() == null ? new Hash(): getLHS().getHash());
	hsh = hsh.sag(rhs == null ? new Hash(): rhs.getHash());
	hsh = hsh.sag(new Hash(weight));
    }   


    // equals if all non-variable info is the same and variable mapping is the same
    // but to start with, just left side/right side similarity test
    public boolean equals(Object o) {
	boolean debug = false;
	if (debug) Debug.debug(debug, "Checking equality of "+o.toString()+" to "+toString());
	if (!o.getClass().equals(this.getClass())) {
	    if (debug) Debug.debug(debug, "\tClass of "+o.toString()+" not equal to that of "+toString());
	    return false;
	}

	StringTransducerRule r = (StringTransducerRule)o;
	if (!getState().equals(r.getState())) {
	    if (debug) Debug.debug(debug, "\tState of "+r.toString()+" not equal to that of "+toString()+"; "+r.getState().toString()+" vs "+getState().toString());	    
	    return false;
	}
	if (!getLHS().equals(r.getLHS())) {
	    if (debug) Debug.debug(debug, "\tlhs of "+r.toString()+" not equal to that of "+toString()+"; "+r.getLHS().toString()+" vs "+getLHS().toString());	    
	    return false;
	}
	if (!rhs.equals(r.rhs)) {
	    if (debug) Debug.debug(debug, "\trhs of "+r.toString()+" not equal to that of "+toString()+"; "+r.rhs.toString()+" vs "+rhs.toString());	    
	    return false;
	}
 	if (weight != r.weight) {
 	    if (debug) Debug.debug(debug, "\tweight of "+r.toString()+" not equal to that of "+toString()+"; "+r.weight+" vs "+weight);	    
 	    return false;
 	}
	if (semiring != r.semiring) {
	    if (debug) Debug.debug(debug, "\tSemiring of "+r.toString()+" not equal to that of "+toString());
	    return false;
	}
	if (debug) Debug.debug(debug, "\tTRUE: "+o.toString()+" is equal to "+toString());
	return true;
    }

    // read off initial state
    private static Pattern statePat = Pattern.compile("([^\\.]+)\\.(.*)");
    // separate left from right
    private static Pattern sidesPat = Pattern.compile("(.*)\\s*->\\s*(.*?)\\s*(?:#\\s*(\\S+)\\s*)?(?:@\\s*(\\S+)\\s*)?");

    // passed in here from StringTransducerRuleSet
    public StringTransducerRule(StringTransducerRuleSet trs, HashSet<Symbol> states, String text, Semiring s) throws DataFormatException {
	boolean debug = false;
	parent = trs;
	grammarRules = new HashMap();
	grammarStates = new HashMap();
	semiring = s;
	// weight can be modified if it is specified
	weight = semiring.ONE();
	if (debug) Debug.debug(debug, "Initialized new maps");
	if (debug) Debug.debug(debug, "building rule from "+text);
	StringReader sr = null;

	Matcher stateMatch = statePat.matcher(text);
	if (!stateMatch.matches())
	    throw new DataFormatException("Expected a state next to \".\" in "+text);
	setState(SymbolFactory.getSymbol(stateMatch.group(1)));
	states.add(getState());
	if (debug) Debug.debug(debug, "Read in state "+getState().toString());
	Matcher sidesMatch = sidesPat.matcher(stateMatch.group(2));
	if (!sidesMatch.matches())
	    throw new DataFormatException("Expected -> separating items in "+stateMatch.group(2));
	if (debug) Debug.debug(debug, "Read "+sidesMatch.groupCount()+" groups");
	StringBuffer lhsbuf = new StringBuffer(sidesMatch.group(1));
	StringBuffer rhsbuf = new StringBuffer(sidesMatch.group(2));

	if (sidesMatch.group(3) != null) {
	    try {
		weight = semiring.printToInternal((new Double(sidesMatch.group(3))).doubleValue());
		if (debug) Debug.debug(debug, "Read weight as "+weight);
	    }
	    catch (NumberFormatException e) {
		throw new DataFormatException("Bad number format from "+sidesMatch.group(3)+": "+e.getMessage(), e);
	    }
	}

	if (sidesMatch.group(4) != null) {
	    try {
		tieid = (new Integer(sidesMatch.group(4))).intValue();
		if (debug) Debug.debug(debug, "Read tieid as "+tieid);
	    }
	    catch (NumberFormatException e) {
		throw new DataFormatException("Tie id not integer value in "+sidesMatch.group(4)+": "+e.getMessage(), e);
	    }
	}

	trvm = new TransducerRuleVariableMap();
	if (debug) Debug.debug(debug, "Initialized trvm");
	if (debug) Debug.debug(debug, "Making tree from "+lhsbuf.toString());
	try {
	    setLHS(new TransducerLeftTree(lhsbuf, trvm));
	}
	catch (DataFormatException e) {
	    throw new DataFormatException(e.getMessage()+" : lhs of "+text);
	}
	
	if (debug) Debug.debug(debug, "created lhs tree: "+getLHS().toString());
	if (lhsbuf.length() > 0) {
	    for (int i = 0; i < lhsbuf.length(); i++)
		if (lhsbuf.charAt(i) != 9 && lhsbuf.charAt(i) != 32) 
		    throw new DataFormatException("lhs has remnant ["+lhsbuf.toString()+"]");
	}
	if (getLHS().hasVariable())
	    trvm.addLHS(getLHS());
	if (debug) Debug.debug(debug, "Making string from "+rhsbuf.toString());
	try {
	    rhs = new TransducerRightString(rhsbuf, states, trvm);
	}
	catch (DataFormatException e) {
	    throw new DataFormatException(e.getMessage()+" : rhs of "+text);
	}
	
	if (debug) Debug.debug(debug, "created rhs string: "+rhs.toString());
	// 	    if (rhs.hasVariable())
	// 		trvm.addRHS(rhs);
	if (!trvm.isValid())
	    throw new DataFormatException("StringTransducerRule: variable error: "+trvm.getInvalidMessage()); 
	ruleIndex = parent.getNextRuleIndex();
	if (debug) Debug.debug(debug, "done with str creation");	    
    }
    
    // set-at-once constructor
    public StringTransducerRule(Symbol st, TransducerLeftTree left, TransducerRightString right, 
			      TransducerRuleVariableMap vm, double w, Semiring s) {
    	super(null, left, st, w, s, vm);
	rhs = right;	
    }

    // if you happen to have the parent
    public StringTransducerRule(Symbol st, TransducerLeftTree left, TransducerRightString right, 
			      TransducerRuleVariableMap vm, double w, Semiring s, StringTransducerRuleSet trs ) {
	this(st, left, right, vm, w, s);
	parent = trs;
	ruleIndex = parent.getNextRuleIndex();
    }

    
	// epsilon input for composition
    // q. + r.x0: -> RHS => q_r.x0: -> RHS (with q adjoined to each state)
    
	// build an epsilon-input rule that goes to the specified RHS (though the TRVM must be re-mapped). Used
	// for composing to input-epsilon. a state from join in both input and output state. b state from join in input,
	// bstate(s) from rhs used in output
	public StringTransducerRule(StringTransducerRuleSet trs, Symbol newst, Symbol oldast, 
			TransducerRightString oldrhs, double wgt) {
		// lhs is epsilon input rule
		super(trs, new TransducerLeftTree(0), newst, wgt, trs.getSemiring(), new TransducerRuleVariableMap());
		boolean debug = false;
		if (debug) Debug.debug(debug, "Building epsilon-input rule from "+newst+" and "+oldrhs);
		trvm.addLHS(getLHS());
		rhs = new TransducerRightString(trs, oldrhs, trvm, oldast, getLHS().getVariable());
		// check for top-level variable (lower-level variables checked in creation)
		if (rhs.hasVariable())
			trvm.addRHS(rhs, true);
	}

	// epsilon output for composition
	// q.LHS -> r.x0: + s => q_s.LHS -> r_s.x0
	// use given lhs, given input and output states, trivial rhs
	public StringTransducerRule(StringTransducerRuleSet trs, TransducerLeftTree newlhs, Symbol var,
			Symbol srcstate, Symbol dststate, double wgt) {
		super(trs, newlhs, srcstate, wgt, trs.getSemiring(), new TransducerRuleVariableMap(newlhs));	
		rhs = new TransducerRightString(var, dststate);
		trvm.addRHS(rhs, true);
	}
	
	// constructor used in composition
	// lhs comes from lhs of rulea
	// rhs starts as tree
	// but has leaves changed to match the variables referenced by lhs, and noted in trvm
	public StringTransducerRule(StringTransducerRuleSet trs, TransducerLeftTree newlhs, 
			Symbol currstate, StringItem rhsstring, 
			double currweight,
			HashMap<Symbol, TransducerRightSide> amap, 
			HashMap<Symbol, StateTreePair> bmap)
	throws ImproperConversionException {
		super(trs, newlhs, currstate, currweight, trs.getSemiring(), new TransducerRuleVariableMap(newlhs));	

		boolean debug = false;
		
		if (debug) Debug.debug(debug, "State is "+getState());
		
		if (debug) Debug.debug(debug, "LHS is "+getLHS());
		
		if (debug) Debug.debug(debug, "weight is "+weight);
		
		rhs = new TransducerRightString(rhsstring, trvm, amap, bmap);
		if (debug) Debug.debug(debug, "rhs is "+rhs);
		
		if (debug) Debug.debug(debug, "String value is "+toString());
		if (debug) Debug.debug(debug, "Done with construction");
	}


	// constructor used in multi-transducer leftside composition
	// lhs is a transducer lhs
	// rhs is a tree with special vector terminals
	public StringTransducerRule(StringTransducerRuleSet trs, TransducerLeftTree newlhs, 
			Symbol currstate, StringItem rhsstring, double currweight)
	throws ImproperConversionException {
		super(trs, newlhs, currstate, currweight, trs.getSemiring(), new TransducerRuleVariableMap(newlhs));	
		boolean debug = false;
		
		if (debug) Debug.debug(debug, "State is "+getState());
	
		if (debug) Debug.debug(debug, "LHS is "+getLHS());
	
		if (debug) Debug.debug(debug, "weight is "+weight);
	
		rhs = new TransducerRightString(rhsstring, trvm);
		// check for top-level variable (lower-level variables checked in creation)
		if (rhs.hasVariable())
			trvm.addRHS(rhs, true);
		if (debug) Debug.debug(debug, "rhs is "+rhs);
		
		if (debug) Debug.debug(debug, "String value is "+toString());
		if (debug) Debug.debug(debug, "Done with construction");
	}
	
    
	// constructor used in multi-transducer rightside composition
	// lhs is a tree with special vector terminals
	// rhs is a TransducerRightString
	public StringTransducerRule(StringTransducerRuleSet trs, TreeItem lhstree, 
			Symbol currstate, TransducerRightString newrhs, double currweight)
	throws ImproperConversionException {
		boolean debug = false;
		setState(currstate);
		semiring = trs.getSemiring();
		if (debug) Debug.debug(debug, "State is "+getState());
		trvm = new TransducerRuleVariableMap();
		setLHS(new TransducerLeftTree(lhstree, trvm));
		// check for top-level variable (lower-level variables checked in creation)
		if (getLHS().hasVariable())
			trvm.addLHS(getLHS());
		if (debug) Debug.debug(debug, "LHS is "+getLHS());
		rhs = newrhs;
		trvm.traverse(newrhs);

		if (debug) Debug.debug(debug, "rhs is "+rhs);
		weight = currweight;
		if (debug) Debug.debug(debug, "weight is "+weight);

		parent = trs;	
		if (debug) Debug.debug(debug, "String value is "+toString());
		if (debug) Debug.debug(debug, "Done with construction");
	}
	
	
    // build domain-preserving rule from extended tlt. only look at first level. copy any variables seen.
    // turn any non-variable children into new variables with specific states
    // save those states in the vector and add them to the hash set
    private StringTransducerRule(TransducerRuleSet trs, Symbol currstate, 
			       TransducerLeftTree tlt, TransducerRuleVariableMap oldtrvm, 
			       double wgt, Vector<Symbol> stvec) {
	boolean debug = false;
	if (debug) Debug.debug(debug, "Building rule from "+tlt+" with "+currstate);
	setState(currstate);
	trvm = new TransducerRuleVariableMap();
	parent = trs;
	semiring = trs.getSemiring();
	weight = wgt;
	// new lhs will be the label of tlt with a variable for each child.
	setLHS(new TransducerLeftTree(tlt.getLabel(), tlt.getNumChildren(), trvm));
	if (debug) Debug.debug(debug, "Built TLT "+getLHS());
	// If a child was part of the tree, we need to introduce a new state
	// and then add that to the stvec
	// if not, use the variables from oldtrvm
	stvec.setSize(tlt.getNumChildren());

	TransducerRightString currrhs = null;
	for (int i = 0; i < tlt.getNumChildren(); i++) {
		if (tlt.getChild(i).hasVariable()) {
			for (TransducerRightSide oldright : oldtrvm.getRHS(tlt.getChild(i))) {
				currrhs = new TransducerRightString(currrhs, null, getLHS().getChild(i).getVariable(), oldright.getState());
				trvm.addRHS(currrhs, true);
			}
		}
		else {
			Symbol newstate = SymbolFactory.getStateSymbol();
			currrhs = new TransducerRightString(currrhs, null, getLHS().getChild(i).getVariable(), newstate);
			trvm.addRHS(currrhs, true);
			stvec.add(i, newstate);
		}
	}
	if (rhs == null)
	    rhs = new TransducerRightString(SymbolFactory.getSymbol("X"));
	else
	    rhs = currrhs;
    }

    // conversion from RTG rule
    // state is the same. rule rhs is copied by lhs, yielded for rhs. In lhs, just variables.
    // In rhs, state-variable match.
    public StringTransducerRule(StringTransducerRuleSet trs, RTGRule rule, HashSet<Symbol> availableSyms, HashSet rtgstates) {
	super(trs, rule, availableSyms, rtgstates);
	// parent takes care of the lhs. specialization here for the rhs
	rhs = new TransducerRightString((TreeItem)rule.getRHS(), trvm, rtgstates);
    }


    public String toString() {
	if (sval == null) 
	    sval = getState().toString()+"."+getLHS().toString()+" -> "+rhs.toString()+" # "+Rounding.round(semiring.internalToPrint(weight), 6);
	return sval;
    }

    public String toDecoderString(Symbol top) {
	return top.toString()+"("+getLHS().toString()+") -> "+((TransducerRightString)rhs).toStateFreeString()+" # "+Rounding.round(semiring.internalToPrint(weight), 6);
    }

    // recursive function that builds the new cfg stromg for application
    Rule buildItem (StateTreePair st, HashMap<TransducerLeftTree, TreeItem> h, RuleSet rs, HashSet<StateTreePair> states, double weight, Semiring semiring) {
    	boolean debug = false;
    	TreeItem refTree = st.getTree();
    	StringItem s = buildString(refTree, h, states, (TransducerRightString)rhs);
    	if (debug) Debug.debug(debug, "Built StringItem "+s.toString());
    	CFGRule rule = new CFGRule((CFGRuleSet)rs, st.getSymbol(), s, weight, semiring);
    	rule.tieToTransducerRule(this);
    	return rule;
    }

    // set the symbol of each element in the rhs, loading them into a vector and possibly continue to next symbol
    // then construct the string
    private StringItem buildString(TreeItem refTree, HashMap<TransducerLeftTree, TreeItem> h, HashSet<StateTreePair> states, TransducerRightString currrhs) {
    	boolean debug = false;
    	if (currrhs.isEpsilon()) {
    		if (debug) Debug.debug(debug, "building epsilon string from epsilon rule string");
    		StringItem ret = new StringItem();
    		return ret;
    	}
    	Vector v = new Vector();
    	TransducerRightString ptr = currrhs;
    	while (ptr != null) {
    		// map variable -- insert the state-tree pair
    		if (ptr.hasVariable()) {
    			if (debug) Debug.debug(debug, ptr.getLabel().toString()+" has a variable");
    			TransducerLeftTree tlt = getTRVM().getLHS(ptr);
    			TreeItem mapTree = (TreeItem)h.get(tlt);
    			if (debug) Debug.debug(debug, "mapped tree is "+mapTree.toString());
    			StateTreePair st = SymbolFactory.getStateTreePair(ptr.getState(), mapTree, 1, mapTree.getLeaves().length);
    			if (debug) Debug.debug(debug, "Adding "+st.getSymbol().toString());
    			v.add(st.getSymbol());		
    			states.add(st);
    		}
    		// epsilon -- return an epsilon stringItem
    		else if (ptr.isEpsilon()) {
    			if (debug) Debug.debug(debug, "string built is epsilon");
    			StringItem ret = new StringItem();
    			return ret;
    		}
    		// literal -- copy it
    		else {
    			if (debug) Debug.debug(debug, ptr.getLabel().toString()+" is a literal");
    			v.add(ptr.getLabel());
    		}
    		ptr = ptr.next();
    	}
    	StringItem ret = null;
    	try {
    		ret = new StringItem(v);
    	}
    	catch (DataFormatException e) {
    		System.err.println("Error building CFG string from "+refTree.toString()+" and "+currrhs.toString()+"; "+e.getMessage());
    		System.exit(1);
    	}
    	return ret;
    }

    // for training
    public boolean isTreeMatch(Symbol st, TreeItem i, TrainingString o) {
    	return isTreeMatch(st, i, o, false);
    }
    public boolean isTreeMatch(Symbol st, TreeItem i, TrainingString o, boolean debug) {
    	if (debug) Debug.debug(debug, "Comparing tree "+i+" against rule "+toString());
    	if (!st.equals(getState())) {
    		if (debug) Debug.debug(debug, st+" is not the same as the state of this rule, which is "+getState());
    		return false;
    	}
    	return (getLHS().isTreeMatch(i) && ((TransducerRightString)rhs).isStringMatch(o, debug));
    }



    // for training: getAllAlignments returns a vector of Vectors of nonterminal clusters, which are consecutive nonterminals and the string
    // they must fill. It is analogous to getPaths in TreeTransducerRule
    
    // we're assuming the rule is appropriate!
    public Vector getAllAlignments(VariableCluster qio) {
	return getAllAlignments(qio, false);
    }
    
    public Vector getAllAlignments(VariableCluster qio, boolean debug) {
	Symbol q = qio.state();
	TreeItem i = qio.in();
	TrainingString o = qio.out();
	int[] align = qio.align();
	try {
	    getLHS().mapTree(i, align[0], align[1]);
	}
	catch (Exception e) {
	    System.err.println("Getting paths from "+i.toString()+" to "+o.toString()+" in "+toString()+": "+e.getMessage());
	}
	TIntHashSet inElements = new TIntHashSet();
	Vector nvc = getLHS().getNonVariableChildren();
	Iterator nvcit = nvc.iterator();
	while (nvcit.hasNext()) {
	    TransducerLeftTree tlt = (TransducerLeftTree)nvcit.next();
	    inElements.add(tlt.getMapTreeEnd());
	}

	Vector v = new Vector();
	recursiveGetAlignments(v, new Vector(), o, (TransducerRightString)rhs, debug);
	Iterator vit = v.iterator();
	Vector stringV = new Vector();
	while (vit.hasNext()) {
	    TIntHashSet outElements = new TIntHashSet();
	    // put all the possible mapped elements (later subtract them based on what's in the variable
	    // clusters).
	    // special hack for empty string
	    if (o.getEndIndex() == 0 && o.getStartIndex() == 0)
		outElements.add(o.getEndIndex());
	    else
		for (int idx = o.getStartIndex()+1; idx <= o.getEndIndex(); idx++)
		    outElements.add(idx);
	    Vector a = (Vector)vit.next();
	    Iterator ait = a.iterator();
	    while (ait.hasNext()) {
		VariableCluster clust = (VariableCluster)ait.next();
		TrainingString piece = clust.getString();
		for (int idx = piece.getStartIndex()+1; idx <= piece.getEndIndex(); idx++)
		    outElements.remove(idx);
	    }
	    StringBuffer alignStr = new StringBuffer();
	    TIntIterator ieit = inElements.iterator();
	    while (ieit.hasNext()) {
		int inNum = ieit.next();
		TIntIterator oeit = outElements.iterator();
		while (oeit.hasNext()) {
		    int outNum = oeit.next();
		    alignStr.append(inNum+":"+outNum+" ");
		}
	    }
	    stringV.add(alignStr);
	}
	Vector pairV = new Vector();
	pairV.add(v);
	pairV.add(stringV);
	return pairV;
    }
    
    // traverse through the rule until the end is reached, adding elements along the way
    private void recursiveGetAlignments(Vector alignSet, Vector currAlign, TrainingString currString, 
    		TransducerRightString currRule, boolean debug) {
    	if(debug) {
    		System.err.print("RecursiveGetAlignments: from ");
    		System.err.print(currString == null ? "null" : currString.toString());
    		System.err.print(" to ");
    		System.err.print(currRule == null ? "null" : currRule.toString());
    		System.err.println();
    	}
    	// if no more rule and no more string, add the current alignment
    	if (currRule == null && (currString == null || currString.isEpsilon())) {
    		if (debug) Debug.debug(debug, "String and rule both null. adding current alignment");
    		alignSet.add(currAlign);
    		return;
    	}
    	// if more string and no more rule this alignment is bogus
    	if (currRule == null && currString != null) {
    		if (debug) Debug.debug(debug, "rule null, string is "+currString.toString()+". abandoning current alignment");
    		return;
    	}

    	// chop off till next variable, but make sure there's a match
    	if (!currRule.hasVariable()) {
    		// if rule is epsilon or string is epsilon, only epsilon will do
    		if (currRule.isEpsilon()) {
    			if (currString == null || currString.isEpsilon()) {
    				if (debug) Debug.debug(debug, "Epsilon rule matches epsilon or null string. add alignment and continue");
    				alignSet.add(currAlign);
    				return;
    			}
    			if (debug) Debug.debug(debug, "Epsilon rule can only match epsilon string. abandoning current alignment");
    			return;
    		}
    		if (currString == null || currString.isEpsilon()) {
    			if (debug) Debug.debug(debug, "Epsilon or null string can only match epsilon rule (or no rule). abandoning current alignment");
    			return;
    		}
    		if (currString == null || !currRule.getLabel().equals(currString.getLabel())) {
    			if (debug) Debug.debug(debug, "rule terminal "+currRule.getLabel().toString()+" mismatch with string terminal; abandoning current alignment");
    			return;
    		}
    		if (debug) Debug.debug(debug, "chopping terminal "+currRule.getLabel().toString()+" and continuing");
    		recursiveGetAlignments(alignSet, currAlign, currString.next(), currRule.next(), debug);
    		return;
    	}
    	if (debug) Debug.debug(debug, "We are matching state "+currRule.getState().toString()+", variable "+currRule.getVariable().toString());
    	// at variable, identify next terminal, match in all the ways possible, from smallest to largest
    	Symbol nextTerm = currRule.nextTerminal() == null ? null : currRule.nextTerminal().getLabel();
    	// if no next Terminal, it's just the rest of the string. only one option
    	if (nextTerm == null) {
    		if (debug) Debug.debug(debug, "No next terminal");
    		VariableCluster newCluster = null;
    		if (currString == null)
    			newCluster = new VariableCluster(TrainingString.getEpsilon());
    		else
    			newCluster = new VariableCluster(currString);

    		while (currRule != null && currRule.hasVariable()) {
    			TransducerLeftTree lvar = getTRVM().getLHS(currRule);
    			if (lvar == null) {
    				Debug.debug(true, "Unable to get left tree of "+currRule.toString()+" with hash code "+currRule.hashCode()+" in "+toString()+" hash "+hashCode());
    				System.exit(1);
    			}
    			StateTreePair sts = SymbolFactory.getStateTreePair(currRule.getState(), lvar.getMapTree(), lvar.getMapTreeStart(), lvar.getMapTreeEnd());
    			if (debug) Debug.debug(debug, "adding state tree pair "+sts.toString()+" to cluster");
    			newCluster.addVariable(sts);
    			currRule = currRule.next();
    		}
    		if (debug && currString != null) Debug.debug(true, "adding match of nonterminal cluster to  "+currString.toString()+"; done with alignment");
    		currAlign.add(newCluster);
    		alignSet.add(currAlign);
    		return;
    	}
    	// normal case: for each time nextTerm is found in the string, match the string to that point to the cluster of nonterms
    	// and recurse on the rest
    	else {
    		if (debug) Debug.debug(debug, "next terminal symbol in rule is "+nextTerm.toString());
    		VariableCluster newClusterStub = new VariableCluster();
    		while (currRule != null && currRule.hasVariable()) {
    			TransducerLeftTree lvar = getTRVM().getLHS(currRule);
    			if (lvar == null) {
    				Debug.debug(true, "Unable to get left tree of "+currRule.toString()+" with hash code "+currRule.hashCode()+" in "+toString()+" hash "+hashCode());
    				System.exit(1);
    			}
    			StateTreePair sts = SymbolFactory.getStateTreePair(currRule.getState(), lvar.getMapTree(), lvar.getMapTreeStart(), lvar.getMapTreeEnd());
    			if (debug) Debug.debug(debug, "adding state tree pair "+sts.toString()+" to cluster");
    			newClusterStub.addVariable(sts);
    			currRule = currRule.next();
    		}
    		// first try the epsilon case
    		// copy the alignment stub and cluster stub
    		Vector epsAlign = new Vector(currAlign);
    		VariableCluster epsCluster = new VariableCluster(newClusterStub);
    		// set the cluster with the current substring and add it to the alignment stub
    		epsCluster.setString(TrainingString.getEpsilon());
    		if (debug) Debug.debug(debug, "aligning cluster to epsilon and continuing");
    		epsAlign.add(epsCluster);
    		// recurse on the rest of the rule, without shrinking the string
    		recursiveGetAlignments(alignSet, epsAlign, currString, currRule, debug);

    		// now do the non-epsilon cases if there's anything left to align to
    		if (currString != null) {
    			TrainingString nextString = currString.next(nextTerm);
    			while (nextString != null) {
    				// copy the alignment stub and cluster stub
    				Vector nextAlign = new Vector(currAlign);
    				VariableCluster newCluster = new VariableCluster(newClusterStub);
    				// set the cluster with the current substring and add it to the alignment stub
    				newCluster.setString(currString.getSubString(currString.getStartIndex(), nextString.getStartIndex()));
    				if (debug) Debug.debug(debug, "aligning cluster to "+newCluster.getString().toString()+" and continuing");
    				nextAlign.add(newCluster);
    				// recurse on the rest of the rule
    				recursiveGetAlignments(alignSet, nextAlign, nextString, currRule, debug);
    				// set next string for next alignment
    				nextString = nextString.next(nextTerm);
    			}
    		}
    	}
    }

    // accessor custom to this subclass
    public TransducerRightString getRHS() { return (TransducerRightString)rhs; }		

    // for debugging. how many items?
    public int getSize() { return getLHS().getSize() + ((TransducerRightString)rhs).getSize(); }


    // given an extended rule, make a set of rules from it by traversing down the rule one level at a time,
    // copying variables where found, and extracting others.
    public void makeNonExtended(ArrayList<TransducerRule> newrules) {
	makeNonExtended(getLHS(), getState(), parent, newrules, true);
    }

    // recursive function that walks down lhs and adds new rules
    // for domain-preserving modification
    private void makeNonExtended(TransducerLeftTree tlt, Symbol newst, 
				 TransducerRuleSet trs, ArrayList<TransducerRule> newrules, 
				 boolean isFirst) {
	boolean debug = false;
	Vector<Symbol> nextStates = new Vector<Symbol>();
	if (debug) Debug.debug(debug, "Making nonextended rule out of "+tlt);
	double wgt = isFirst ? weight : semiring.ONE();
	newrules.add(new StringTransducerRule(trs, newst, tlt, getTRVM(), wgt, nextStates));
	for (int i = 0; i < tlt.getNumChildren(); i++) {
	    if (!tlt.getChild(i).hasVariable())
		makeNonExtended(tlt.getChild(i), nextStates.get(i), trs, newrules, false);
	}
    }
    
    
	// get the rhs of this rule as a string with vecSymbols in leafs
    // used for continuity with vector composition
	public StringItem getEpsInputRightComposableString(Vector<Symbol> stateVec) {
		boolean debug = false;
		return getRHS().getEpsInputVecVarImageString(stateVec);
	}

}
	
