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

// tree transducer rule - a state, lhs, rhs, variable set, and weight

public class TreeTransducerRule extends TransducerRule implements Derivable {

	//    private TransducerRightTree rhs;
	// filled on demand with mappings from a tree to the rtg rule produced
	// and the members of that rule that are states
	private HashMap<TreeItem, RTGRule> backGrammarRules;
	private HashMap<TreeItem, HashSet<StateTreePair>> backGrammarStates;
	
	void setHashCode() {
		hsh = new Hash(getLHS().getHash());
		hsh = hsh.sag(rhs.getHash());
		hsh = hsh.sag(new Hash(weight));
	}

	// equals if all non-variable info is the same and variable mapping is the same
	// but to start with, just left side/right side similarity test
	public boolean equals(Object o) {
		if (!o.getClass().equals(this.getClass()))
			return false;

		TreeTransducerRule r = (TreeTransducerRule)o;
		if (!getState().equals(r.getState()))
			return false;
		if (!getLHS().equals(r.getLHS()))
			return false;
		if (!rhs.equals(r.rhs))
			return false;
		if (weight != r.weight)
			return false;
		if (semiring != r.semiring)
			return false;
		return true;
	}



	// read off initial state
	private static Pattern statePat = Pattern.compile("([^\\.]+)\\.(.*)");
	// separate left from right
	private static Pattern sidesPat = Pattern.compile("(.*)\\s*->\\s*(.*?)\\s*(?:#\\s*(\\S+)\\s*)?(?:@\\s*(\\S+)\\s*)?");
	// passed in here from TreeTransducerRuleSet
	public TreeTransducerRule(TreeTransducerRuleSet trs, HashSet<Symbol> states, String text, Semiring s) throws DataFormatException {
		boolean debug = false;
		parent = trs;
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
		setLHS(new TransducerLeftTree(lhsbuf, trvm));
		if (lhsbuf.length() > 0) {
			for (int i = 0; i < lhsbuf.length(); i++)
				if (lhsbuf.charAt(i) != 9 && lhsbuf.charAt(i) != 32) 
					throw new DataFormatException("lhs has remnant ["+lhsbuf.toString()+"]");
		}
		if (debug) Debug.debug(debug, "Remainder is "+lhsbuf.toString());
		if (debug) Debug.debug(debug, "created lhs tree: "+getLHS().toString());
		if (getLHS().hasVariable())
			trvm.addLHS(getLHS());
		if (debug) Debug.debug(debug, "Making tree from "+rhsbuf.toString());
		rhs = new TransducerRightTree(rhsbuf, states, trvm);
		if (rhsbuf.length() > 0) {
			for (int i = 0; i < rhsbuf.length(); i++)
				if (rhsbuf.charAt(i) != 9 && rhsbuf.charAt(i) != 32) 
					throw new DataFormatException("rhs has remnant ["+rhsbuf.toString()+"]");
		}

		if (debug) Debug.debug(debug, "Remainder is "+rhsbuf.toString());
		if (debug) Debug.debug(debug, "created rhs tree: "+rhs.toString());
		if (rhs.hasVariable())
			trvm.addRHS(rhs, true);
		if (!trvm.isValid())
			throw new DataFormatException("variable error: "+trvm.getInvalidMessage()); 
		ruleIndex = parent.getNextRuleIndex();
	}

	// set-at-once constructor used in composition
	public TreeTransducerRule(Symbol st, TransducerLeftTree left, TransducerRightTree right, 
			TransducerRuleVariableMap vm, double w, Semiring s) {
		super(null, left, st, w, s, vm);
		rhs = right;
	}

	// if you happen to have the parent
	public TreeTransducerRule(Symbol st, TransducerLeftTree left, TransducerRightTree right, 
			TransducerRuleVariableMap vm, double w, Semiring s, TreeTransducerRuleSet trs ) {
		this(st, left, right, vm, w, s);
		parent = trs;
		ruleIndex = parent.getNextRuleIndex();
	}

	// conversion from RTG rule
	// state is the same. rule rhs is used by both lhs and rhs. In lhs, just variables.
	// In rhs, state-variable match.
	// experiment: try keeping track of all lhs symbols used
	public TreeTransducerRule(TreeTransducerRuleSet trs, RTGRule rule, HashSet<Symbol> availableSyms, HashSet rtgstates) {
		super(trs, rule, availableSyms, rtgstates);
		// parent takes care of the lhs. specialization here for the rhs
		rhs = new TransducerRightTree((TreeItem)rule.getRHS(), trvm, rtgstates);
		// check for top-level variable (lower-level variables checked in creation)
		if (rhs.hasVariable())
			trvm.addRHS(rhs, true);
	}

	

	
	// new constructor matches "lazy" composition
	// constructor used in leftside composition
	// lhs comes from lhs of rulea
	// rhs starts as tree
	// but has leaves changed to match the variables referenced by lhs, and noted in trvm
	public TreeTransducerRule(TreeTransducerRuleSet trs, TransducerLeftTree newlhs, 
			Symbol currstate, TreeItem rhstree, double currweight,
			HashMap<Symbol, TransducerRightSide> amap, 
			HashMap<Symbol, StateTreePair> bmap)
	throws ImproperConversionException {
		super(trs, newlhs, currstate, currweight, trs.getSemiring(), new TransducerRuleVariableMap(newlhs));
		boolean debug = false;
		
		if (debug) Debug.debug(debug, "State is "+getState());
		
		if (debug) Debug.debug(debug, "LHS is "+getLHS());
		
		if (debug) Debug.debug(debug, "weight is "+weight);
		
		rhs = new TransducerRightTree(rhstree, trvm, amap, bmap);
		// check for top-level variable (lower-level variables checked in creation)
		if (rhs.hasVariable())
			trvm.addRHS(rhs, true);
		if (debug) Debug.debug(debug, "rhs is "+rhs);
		
		if (debug) Debug.debug(debug, "String value is "+toString());
		if (debug) Debug.debug(debug, "Done with construction");
	}
	
	// constructor used in multi-transducer leftside composition
	// lhs is a transducer lhs
	// rhs is a tree with special vector terminals
	public TreeTransducerRule(TreeTransducerRuleSet trs, TransducerLeftTree newlhs, 
			Symbol currstate, TreeItem rhstree, double currweight)
	throws ImproperConversionException {
		super(trs, newlhs, currstate, currweight, trs.getSemiring(), new TransducerRuleVariableMap(newlhs));

		boolean debug = false;
		
		if (debug) Debug.debug(debug, "State is "+getState());
		
		if (debug) Debug.debug(debug, "LHS is "+getLHS());
		
		if (debug) Debug.debug(debug, "weight is "+weight);
		
		rhs = new TransducerRightTree(rhstree, trvm);
		// check for top-level variable (lower-level variables checked in creation)
		if (rhs.hasVariable())
			trvm.addRHS(rhs, true);
		if (debug) Debug.debug(debug, "rhs is "+rhs);
		
		if (debug) Debug.debug(debug, "String value is "+toString());
		if (debug) Debug.debug(debug, "Done with construction");
	}
	
	
	
	// constructor used in rightside composition
	// lhs starts as tree
	// but has leaves changed to match the variables referenced by rhs, and noted in trvm
	// rhs comes from rhs of ruleb
	public TreeTransducerRule(TreeTransducerRuleSet trs, TreeItem lhstree, 
			Symbol currstate, TransducerRightTree newrhs, double currweight,
			HashMap<Symbol, TransducerRightSide> amap, 
			HashMap<Symbol, StateTreePair> bmap)
	throws ImproperConversionException {
		boolean debug = false;
		setState(currstate);
		semiring = trs.getSemiring();
		if (debug) Debug.debug(debug, "State is "+getState());
		trvm = new TransducerRuleVariableMap();
		// to later match up states, map old TRT with variable to original state
		HashMap<TransducerRightSide, Symbol> aSideStateMap = new HashMap<TransducerRightSide, Symbol>();
		setLHS(new TransducerLeftTree(lhstree, trvm, amap, bmap, aSideStateMap));
		// check for top-level variable (lower-level variables checked in creation)
		if (getLHS().hasVariable())
			trvm.addLHS(getLHS());
		rhs = new TransducerRightTree(newrhs, trvm, aSideStateMap);
		if (debug) Debug.debug(debug, "RHS is "+rhs);
		weight = currweight;
		if (debug) Debug.debug(debug, "weight is "+weight);

		if (debug) Debug.debug(debug, "rhs is "+rhs);
		parent = trs;	
		if (debug) Debug.debug(debug, "String value is "+toString());
		if (debug) Debug.debug(debug, "Done with construction");
	}
	
	// constructor used in multi-transducer rightside composition
	// lhs is a tree with special vector terminals
	// rhs is a TransducerRightTree
	public TreeTransducerRule(TreeTransducerRuleSet trs, TreeItem lhstree, 
			Symbol currstate, TransducerRightTree newrhs, double currweight)
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
	
	
	
	// epsilon output constructor used in composition. use given lhs, given input and output states, trivial rhs
	public TreeTransducerRule(TreeTransducerRuleSet trs, TransducerLeftTree newlhs, Symbol var,
			Symbol srcstate, Symbol dststate, double wgt) {
		super(trs, newlhs, srcstate, wgt, trs.getSemiring(), new TransducerRuleVariableMap(newlhs));
		rhs = new TransducerRightTree(var, dststate);
		trvm.addRHS(rhs, true);
	}


	// build domain-preserving rule from extended tlt. only look at first level. copy any variables seen.
	// turn any non-variable children into new variables with specific states
	// save those states in the vector and add them to the hash set
	private TreeTransducerRule(TransducerRuleSet trs, Symbol currstate, 
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
		// rhs has symbol X. If a child was part of the tree, we need to introduce a new state
		// and then add that to the stvec
		// if not, copy instances of the variables from oldtrvm
		Vector<TransducerRightTree> kids = new Vector<TransducerRightTree>();
		stvec.setSize(tlt.getNumChildren());
		for (int i = 0; i < tlt.getNumChildren(); i++) {
			if (tlt.getChild(i).hasVariable()) {
				for (TransducerRightSide oldright : oldtrvm.getRHS(tlt.getChild(i))) {
					kids.add(new TransducerRightTree(getLHS().getChild(i), oldright.getState()));
				}
			}
			else {
				Symbol newstate = SymbolFactory.getSymbol("TTR-"+trs.getNextNewState());
				kids.add(new TransducerRightTree(getLHS().getChild(i), newstate));
				stvec.add(i, newstate);
			}
		}
		rhs = new TransducerRightTree(SymbolFactory.getSymbol("X"), kids, trvm);
	}


	// new, "lazy" version
	// build an epsilon-input rule that goes to the specified RHS (though the TRVM must be re-mapped). Used
	// for composing to input-epsilon. a state from join in both input and output state. b state from join in input,
	// bstate(s) from rhs used in output
	public TreeTransducerRule(TreeTransducerRuleSet trs, Symbol newst, Symbol oldast, 
			TransducerRightTree oldrhs, double wgt) {
		
		boolean debug = false;
		if (debug) Debug.debug(debug, "Building epsilon-input rule from "+newst+" and "+oldrhs);
		parent = trs;
		setState(newst);
		semiring = trs.getSemiring();
		weight = wgt;
		trvm = new TransducerRuleVariableMap();
		// lhs is epsilon input rule
		setLHS(new TransducerLeftTree(0));
		trvm.addLHS(getLHS());
		rhs = new TransducerRightTree(trs, oldrhs, trvm, oldast, getLHS().getVariable());
		// check for top-level variable (lower-level variables checked in creation)
		if (rhs.hasVariable())
			trvm.addRHS(rhs, true);
	}

	
	// create an RTG rule from a state, tree pair, and memoize it. Memoize states too, that are returned by getGrammarStates
	// only the actual rule state would apply, but this is a double check
	public RTGRule getBackwardGrammarRule(RTGRuleSet rs, StateTreePair st) {
		boolean debug = false;
		if (backGrammarRules == null) {
			backGrammarRules = new HashMap<TreeItem, RTGRule>();
			backGrammarStates = new HashMap<TreeItem, HashSet<StateTreePair>>();
		}
		//	Debug.debug(true, "Attempting to match "+st.toString()+" to "+toString());
		if (backGrammarRules.containsKey(st.getTree())) {
			//	    Debug.debug(true, "Memoized!");
			return backGrammarRules.get(st.getTree());
		}
		// store if there's no tree match
		if (!isRHSTreeMatch(st)) {
			backGrammarRules.put(st.getTree(), null);
			backGrammarStates.put(st.getTree(), new HashSet());
			return null;
		}
		if (debug) Debug.debug(debug, "matching "+st.toString()+" to "+toString());
		// how to build an rtg rule:
		// lhs is the state, tree pair as a state
		// rhs is the lhs of the transducer rule with variables substituted

		HashMap<TransducerRightTree, TreeItem> h = new HashMap<TransducerRightTree, TreeItem>();
		((TransducerRightTree)rhs).getInverseTreeMatchPairs(st.getTree(), h);

		// other states added by buildTree

		backGrammarStates.put(st.getTree(), new HashSet<StateTreePair>());
		backGrammarStates.get(st.getTree()).add(st);
		// build the rhs and create a rule out of it. Different for Tree and String
		Item newItem;
		RTGRule r = buildBackItem(st, h, rs, weight, semiring);
		backGrammarRules.put(st.getTree(), r);
		if (debug) Debug.debug(debug, "Built "+r.toString());
		return r;	
	}
	
	// states in the rule. rule must be calculated first
	public HashSet<StateTreePair> getBackwardGrammarStates(StateTreePair st) throws DataFormatException {
		boolean debug = false;
		// don't bother if there's no state match
		if (!st.getState().equals(getState())) {
			if (debug) Debug.debug(debug, "No state match, so returning null");
			return null;
		}
		if (!backGrammarStates.containsKey(st.getTree())) {
			if (debug) Debug.debug(debug, "Throwing exception because we're too early");
			throw new DataFormatException("TreeTransducerRule: looked for states in rule created by "+st.toString()+" before calculating rule");
		}
		return backGrammarStates.get(st.getTree());
	}
	
	public String toString() {
		if (sval == null)
			sval = getState().toString()+"."+getLHS().toString()+" -> "+rhs.toString()+" # "+Rounding.round(semiring.internalToPrint(weight), 6);
		return sval;
	}


	public boolean isRHSTreeMatch(StateTreePair st) {
		return isRHSTreeMatch(st.getState(), st.getTree());
	}
	public boolean isRHSTreeMatch(Symbol st, TreeItem t) {
		if (!st.equals(getState()))
			return false;
		return t.isTransducerRightTreeMatch(((TransducerRightTree)rhs));
	}

	// recursive function that builds the new forward-applied tree
	Rule buildItem(StateTreePair st, HashMap<TransducerLeftTree, TreeItem> h, RuleSet rs, HashSet<StateTreePair> states, double weight, Semiring semiring) {
		boolean debug = false;
		TreeItem refTree = st.getTree();
		TreeItem t = new TreeItem();
		buildTree(refTree, h, (TransducerRightTree)rhs, states, t);
		if (debug) Debug.debug(debug, "Built TreeItem "+t.toString());
		RTGRule newrule = new RTGRule((RTGRuleSet)rs, st.getSymbol(), t, weight, semiring);
		// for training on a grammar that implicitly represents matching to a tree
		newrule.tieToTransducerRule(this);
		return newrule;
	}

	// recursive function that builds the new forward-applied tree
	private void buildTree(TreeItem refTree, HashMap<TransducerLeftTree, TreeItem> h, TransducerRightTree currrhs, HashSet<StateTreePair> states, TreeItem t) {
		boolean debug = false;
		
		// set the symbol of t and possibly call on children
		// if currrhs has no variable copy the symbol
		// otherwise copy from the mapping
		if (currrhs.hasVariable()) {
			if (debug) Debug.debug(debug, currrhs+" has a variable");
			TransducerLeftTree tlt = getTRVM().getLHS(currrhs);
			TreeItem mapTree = h.get(tlt);
			StateTreePair st = SymbolFactory.getStateTreePair(currrhs.getState(),  mapTree, 1, mapTree.getLeaves().length);
			t.setLabel(st.getSymbol());
			if (debug) Debug.debug(debug, "buildTree: set label to (variable) "+st.getSymbol());
			if (states != null)
				states.add(st);
			// can't be any children, so return
			return;
		}
		t.setLabel(currrhs.getLabel());
		if (debug) Debug.debug(debug, "buildTree: set label to "+currrhs.getLabel());
		for (int i = 0; i < currrhs.getNumChildren(); i++) {
			TreeItem child = new TreeItem();
			buildTree(refTree, h, currrhs.getChild(i), states, child);
			t.addChild(child);
		}
	}

	// recursive function that builds the new backward-applied tree
	private RTGRule buildBackItem(StateTreePair st, HashMap<TransducerRightTree, TreeItem> h, RTGRuleSet rs, double weight, Semiring semiring) {
		boolean debug = false;
		TreeItem refTree = st.getTree();
		TreeItem t = new TreeItem();
		buildBackTree(refTree, h, getLHS(), t);
		if (debug) Debug.debug(debug, "Built "+t+" from "+refTree+" and "+h);
		return new RTGRule(rs, st.getSymbol(), t, weight, semiring);
	}
	
	// recursive function that builds the new backward-applied tree
	private void buildBackTree(TreeItem refTree, HashMap<TransducerRightTree, TreeItem> h, TransducerLeftTree currlhs, TreeItem t) {
		boolean debug = false;
		// set the symbol of t and possibly call on children
		// if currlhs has no variable copy the symbol
		// otherwise copy from the mapping
		if (currlhs.hasVariable()) {
			if (debug) Debug.debug(debug, currlhs+" has a variable");
			// TODO: make sure this works with copying!
			// TODO: probably need to handle subsequent states (in construction of st) better 
			// since we assume it does, just get any element from the getRHS
			TransducerRightSide[] arr = new TransducerRightSide[1];
			arr = getTRVM().getRHS(currlhs).toArray(arr);
			
			// deleting rule: make a star tree (with label if there is one)
			if (arr[0] == null || !h.containsKey(arr[0])) {
				if (currlhs.hasLabel()) {
					t.setLabel(currlhs.getLabel());
					t.addChild(new TreeItem(Symbol.getStar()));
				}
				else
					t.setLabel(Symbol.getStar());
				if (debug) Debug.debug(debug, "buildTree: deleting rules, so set descendant to "+t);

			}
			// non-deleting rule: get the state and add it to the set
			else {
				TreeItem mapTree = h.get(arr[0]);
				StateTreePair st = SymbolFactory.getStateTreePair(arr[0].getState(), mapTree, 1, mapTree.getLeaves().length);
				t.setLabel(st.getSymbol());
				if (debug) Debug.debug(debug, "buildTree: set label to (variable) "+st.getSymbol());
				((HashSet)backGrammarStates.get(refTree)).add(st);
			}
			// can't be any children, so return
			return;
		}
		t.setLabel(currlhs.getLabel());
		if (debug) Debug.debug(debug, "buildTree: set label to "+currlhs.getLabel());
		for (int i = 0; i < currlhs.getNumChildren(); i++) {
			TreeItem child = new TreeItem();
			buildBackTree(refTree, h, currlhs.getChild(i), child);
			t.addChild(child);
		}
	}
	
	
	// for training
	public boolean isTreeMatch(Symbol st, TreeItem i, TreeItem o) {
		if (!st.equals(getState())) {
			//	    Debug.debug(true, "STATE Fail: "+st.toString()+" not equal to "+state.toString());
			return false;
		}
		return (getLHS().isTreeMatch(i) && ((TransducerRightTree)rhs).isTreeMatch(o));
	}


	// for training: getPaths returns an ordered set of the symbols of state, intree, outtree triples, encapsulated into a StateTreeTree
	// the name getPaths is archaic to jon's paper

	// we're assuming the rule is appropriate!
	public Vector getPaths(StateTreeTree qio) {
		Symbol q = qio.state();
		TreeItem i = qio.in();
		TreeItem o = qio.out();
		int[] align = qio.align();
		// 	Debug.debug(true, "In rule "+toString());
		// 	Debug.debug(true, "Getting paths from "+i.toString()+" to "+o.toString()+"("+align[0]+", "+align[1]+") ("+align[2]+", "+align[3]+")");
		Vector retVec = new Vector();
		try {
			getLHS().mapTree(i, align[0], align[1]);
			((TransducerRightTree)rhs).mapTree(o, align[2], align[3]);
		}
		catch (Exception e) {
			System.err.println("Getting paths from "+i.toString()+" to "+o.toString()+" in "+toString()+": "+e.getMessage());
		}
		// make all mappings between literals in this rule
		StringBuffer alignStr = new StringBuffer();
		Vector invc = getLHS().getNonVariableChildren();
		// 	Debug.debug(true, invc.size()+" input literals");
		if (invc.size() > 0) {
			Vector onvc = ((TransducerRightTree)rhs).getNonVariableChildren();
			// 	    Debug.debug(true, onvc.size()+" output literals");
			if (onvc.size() > 0) {
				Iterator invcit = invc.iterator();
				while (invcit.hasNext()) {
					TransducerLeftTree tlt = (TransducerLeftTree)invcit.next();
					int inNum = tlt.getMapTreeEnd();
					Iterator onvcit = onvc.iterator();
					while (onvcit.hasNext()) {
						TransducerRightTree trt = (TransducerRightTree)onvcit.next();
						// 			Debug.debug(true, "Adding "+tlt.toString()+": ("+tlt.getMapTreeStart()+", "+tlt.getMapTreeEnd()+")"+" and ");
						// 			Debug.debug(true, trt.toString()+": ("+trt.getMapTreeStart()+", "+trt.getMapTreeEnd()+")");
						alignStr.append(inNum+":"+trt.getMapTreeEnd()+" ");
						// 			Debug.debug(true, "Align string now at "+alignStr.toString());
					}
				}
			}
		}
		Vector v = ((TransducerRightTree)rhs).getVariableChildren();
		Iterator it = v.iterator();
		while (it.hasNext()) {
			TransducerRightTree rvar = (TransducerRightTree)it.next(); 
			TransducerLeftTree lvar = trvm.getLHS(rvar);
			retVec.add(new StateTreeTree(rvar.getState(), 
					lvar.getMapTree(), lvar.getMapTreeStart(), lvar.getMapTreeEnd(),
					rvar.getMapTree(), rvar.getMapTreeStart(), rvar.getMapTreeEnd()));
		}
		Vector pairV = new Vector();
		pairV.add(retVec);
		pairV.add(alignStr);
		return pairV;
	}

	// accessor custom to this subclass
	public TransducerRightTree getRHS() { return (TransducerRightTree)rhs; }

	// checks of epsilonity. A rule is leftEpsilon if the input side has no symbol (i.e. no top label).
	// a rule is rightEpsilon if the output side has no symbol (i.e. no top label)
	public boolean isLeftEpsilon() {
		return !getLHS().hasLabel();
	}
	public boolean isRightEpsilon() {
		if (rhs == null)
			return false;
		return !rhs.hasLabel();
	}
	public boolean isExtendedRHS() {
		if (rhs == null)
			return false;
		return ((TransducerRightTree)rhs).isExtended();
	}


	// get right tree as "normal" tree, mapping variable trees to constants in the provided map
	public TreeItem getRightComposableTree(HashMap<Symbol, TransducerRightSide> map) {
		boolean debug = false;
		// first get the variable children
		Iterator<TransducerRightTree> varkids = getRHS().getVariableChildren().iterator();
		// map each one to a symbol, first reusing existing symbols from the map, then creating
		// new ones
		int varnum = 0;
		HashMap<TransducerRightTree, Symbol> backmap = new HashMap<TransducerRightTree, Symbol>();
		for (Symbol sym: map.keySet()) {
			if (varkids.hasNext()) {
				TransducerRightTree val = varkids.next();
				map.put(sym, val);
				backmap.put(val, sym);
				if (debug) Debug.debug(debug, "Re-using "+sym+" to map to "+val.toString());
				varnum++;
			}
			// uninitialize if we run out of variables
			else {
				map.put(sym, null);
				if (debug) Debug.debug(debug, "Uninitializing "+sym);
			}
		}
		// create new symbols for the additional variables
		while (varkids.hasNext()) {
			Symbol newsym = SymbolFactory.getSymbol("TEMP"+varnum);
			TransducerRightTree val = varkids.next();	    
			map.put(newsym, val);
			backmap.put(val, newsym);
			if (debug) Debug.debug(debug, "Creating "+newsym+" to map to "+val.toString());	    
			varnum++;
		}
		// now form the tree, substituting the new symbols where appropriate
		TreeItem ret = getRHS().getImageTree(backmap);
		if (debug) Debug.debug(debug, "Created "+ret.toString()+" out of "+getRHS().toString());
		return ret;
	}

	// get right tree as "normal" tree, putting variable tree stuff in special symbols in leaves
	public TreeItem getRightComposableTree() {
		boolean debug = false;
		return getRHS().getVecVarImageTree();
	}
	
	// version of the above for input epsilon adds
	public TreeItem getEpsInputRightComposableTree(Vector<Symbol> stateVec) {
		boolean debug = false;
		return getRHS().getEpsInputVecVarImageTree(stateVec);
	}
	
	// version of the above for right composition (but it's the left tree, notice)
	
	
	
	// right image of this rule as an RTG, doing special things to variable terminals. This is useful for transducer composition
	public RTGRuleSet getRightImage() {
		TreeItem t = getRHS().getImageTree();
		Debug.debug(true, "Image tree is "+t.toString());
		RTGRuleSet rs = new RTGRuleSet(t, semiring);
		rs.pruneUseless();
		return rs;
	}

	// rule for the rtg left image of the transducer this rule belongs to. This is useful for transducer composition
	public RTGRule getLeftImageRule(RTGRuleSet parentSet) {
		// we assume that this is a non-extended transducer. Thus children, if there are any, should only be variables. 
		// First we figure out the
		// state that goes with each of them
		if (getLHS().getNumChildren() > 0) {
			TreeItem[] kids = new TreeItem [getLHS().getNumChildren()];
			try {
				for (int i = 0; i < getLHS().getNumChildren(); i++) {
					TransducerLeftTree child = getLHS().getChild(i);
					if (!child.hasVariable())
						throw new UnexpectedCaseException("Child "+(i+1)+" is not a variable; Composition only appropriate for unextended transducers");
					HashSet nextState = trvm.getNextStates(child);
					if (nextState.size() > 1)
						throw new UnexpectedCaseException("copying rule in "+child.toString()+"; Composition only appropriate for noncopying transducers");
					if (nextState.size() < 1)
						throw new UnexpectedCaseException("deleting rule in "+child.toString()+"; Composition only appropriate for nondeleting transducers");
					Iterator nsit = nextState.iterator();
					kids[i] = new TreeItem((Symbol)nsit.next());
				}
			}
			catch (UnexpectedCaseException e) {
				System.err.println("While getting left image of "+toString()+"; "+e.getMessage());
				System.exit(-1);
			}
			RTGRule newRule = new RTGRule(parentSet, getState(), new TreeItem(kids.length, getLHS().getLabel(), kids), semiring.ONE(), semiring);
			newRule.tieToTransducerRule(this);
			return newRule;
		}
		else {
			RTGRule r = new RTGRule(parentSet, getState(), new TreeItem(getLHS().getLabel()), semiring.ONE(), semiring);
			r.tieToTransducerRule(this);
			return r;
		}
	}

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
		newrules.add(new TreeTransducerRule(trs, newst, tlt, trvm, wgt, nextStates));
		for (int i = 0; i < tlt.getNumChildren(); i++) {
			if (!tlt.getChild(i).hasVariable())
				makeNonExtended(tlt.getChild(i), nextStates.get(i), trs, newrules, false);
		}
	}

	private TreeTransducerRule() {
		
	}
	public static void main(String argv[]) {
	
		Vector<TreeTransducerRule> v = new Vector<TreeTransducerRule>();
		Vector<Double> d = new Vector<Double>();
		TreeTransducerRuleSet trs = new TreeTransducerRuleSet();
		RealSemiring s = new RealSemiring();
		String rule = "q.x -> x";
		HashSet<Symbol> states = new HashSet<Symbol>();
		states.add(SymbolFactory.getSymbol("q"));
		int n = Integer.parseInt(argv[0]);
		try {
			for (int i = 0; i < n; i++) {
				v.add(new TreeTransducerRule(trs, states, rule, s));
//				d.add((double)1);
			}
		} catch (Exception e)  {
			System.out.println(e.toString());
		}
	}
	
	
}

