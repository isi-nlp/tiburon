package edu.isi.tiburon;

import java.io.StreamTokenizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TransducerRightTree extends TransducerRightSide {


	private TransducerRightTree[] children;
	private int numChildren;

	// amountConsumed used only in construction
	private int amtConsumed;

	// memoized variable children, in depth-first order
	private Vector varChildren;

	// memoized non-variable children
	private Vector<TransducerRightTree> nonVarChildren;

	// memoized tree leaves
	private Symbol[] leaves;

	// tree value mapped to this tree. can be replaced. isn't guaranteed
	private TreeItem mapTree;
	// tree indices. also can be replaced and not guaranteed
	private int mapTreeStart;
	private int mapTreeEnd;

	// convenience variable just for conversion construction
	private static int nextVarIndex;

	// last time this tree was cloned, the node that resulted
	private TransducerRightTree lastCloned = null;

	// immediate parent. used by parser-driven composition
	public TransducerRightTree parent;
	
	// is this an extended tree? true if children have label or are extended
	private boolean isExtended = false;

	public boolean isExtended() { return isExtended; }

	// epsilon if no label
	public boolean isEpsilon() {
		return label == null;
	}

	public void setLastCloned(TransducerRightTree t) { lastCloned = t; }
	public TransducerRightTree getLastCloned() {
		try {
			if (lastCloned == null)
				throw new UnusualConditionException("Attempted to get cloned tree of "+toString()+" that doesn't exist");
		}
		catch (UnusualConditionException e) {
			System.err.println(e.getMessage());
			System.exit(-1);
		}
		return lastCloned;
	}

	void setHashCode() {
		hsh = new Hash();
		if (variable != null) {
			hsh = hsh.sag(variable.getHash());
			hsh = hsh.sag(state.getHash());
		}
		// label not relevant if a variable exists
		else  if (label != null)
			hsh = hsh.sag(label.getHash());

		for (int i = 0; i < numChildren; i++)
			hsh = hsh.sag(children[i].getHash());
	}

	// equal if label and/or variable are same and all children are equal

	public boolean equals(Object o) {
		if (!o.getClass().equals(this.getClass()))
			return false;

		TransducerRightTree t = (TransducerRightTree)o;
		if (variable == null ^ t.variable == null)
			return false;
		if (variable != null && (!variable.equals(t.variable) || !state.equals(t.state)))
			return false;
		// label only relevant if variable doesn't exist
		if (variable == null) {
			if (label == null ^ t.label == null)
				return false;
			if (label != null && !label.equals(t.label))
				return false;
		}
		if (numChildren != t.numChildren)
			return false;
		for (int i = 0; i < numChildren; i++)
			if (!children[i].equals(t.children[i]))
				return false;
		return true;
	}

	// empty version used for cloning
	public TransducerRightTree() {
		label = null;
		variable = null;
		state = null;
		varChildren = null;
		nonVarChildren = null;
		mapTree = null;
		leaves = null;
		parent = null;
		mapTreeStart = mapTreeEnd = 0;
		isExtended = false;
	}

	// simple symbol constructor
	public TransducerRightTree(Symbol sym) {
		label = sym;
		mapTreeStart=mapTreeEnd=0;
		numChildren = 0;
		parent = null;
		isExtended = false;
	}

	// solo version only used in debugging code called from elsewhere
	public TransducerRightTree(StringBuffer text) throws DataFormatException {
		this(text, new HashSet<Symbol>(), null);
	}

	public TransducerRightTree(StringBuffer text, HashSet<Symbol> states, TransducerRuleVariableMap trvm) throws DataFormatException {
		this(text, states, trvm, 0);
	}

	// node (non-leaf) pattern = symbol followed by open paren
	private static Pattern nodePat = Pattern.compile("(\\s*([^\\s\\(\\):\"]+|\"\"\"|\"[^\"]+\")\\s*\\()");
	// variable leaf pattern = period-divided symbols (both mandatory)
	private static Pattern varLeafPat = Pattern.compile("(\\s*([^\\s\\(\\):\"\\.]+|\"\"\"|\"[^\"]+\")\\.([^\\s\\(\\):\"\\.]+|\"\"\"|\"[^\"]+\"))");
	// non-variable leaf pattern = symbol. Use last!
	private static Pattern leafPat = Pattern.compile("(\\s*([^\\s\\(\\):\"\\.]+|\"\"\"|\"[^\"]+\"))");
	// end-of-tree pattern = spaces and right paren
	private static Pattern endPat = Pattern.compile("(\\s*\\))");
	public TransducerRightTree(StringBuffer text, HashSet<Symbol> states, TransducerRuleVariableMap trvm, int dbgoffset) throws DataFormatException {
		boolean debug = false;
		label = null;
		variable = null;
		state = null;
		varChildren = null;
		nonVarChildren = null;
		mapTree = null;
		leaves = null;
		parent = null;
		mapTreeStart = mapTreeEnd = 0;
		isExtended = false;

		// BEGIN FASTER REGEX CODE
		// is input a variable leaf?
		Matcher varLeafMatch = varLeafPat.matcher(text.toString());
		if (varLeafMatch.lookingAt()) {
			if (debug) Debug.debug(debug, dbgoffset, "TRT matched "+text+" against variable/label pattern");
			String stateStr = varLeafMatch.group(2);
			String varStr = varLeafMatch.group(3);
			int totalLength = varLeafMatch.group(1).length();
			if (debug) Debug.debug(debug, dbgoffset, "Consuming ["+varLeafMatch.group(1)+"]");
			state = SymbolFactory.getSymbol(stateStr);
			states.add(state);
			if (debug) Debug.debug(debug, dbgoffset,  "TRT state set to "+stateStr);
			variable = SymbolFactory.getSymbol(varStr);
			if (debug) Debug.debug(debug, dbgoffset,  "TRT variable set to "+varStr);
			// TODO: comma trip!
			text.delete(0, totalLength);
			return;
		}
		// is input the head of a tree?
		Matcher nodeMatch = nodePat.matcher(text.toString());
		if (nodeMatch.lookingAt()) {
			if (debug) Debug.debug(debug, dbgoffset, "TRT matched "+text+" against node pattern");
			String labStr = nodeMatch.group(2);
			int totalLength = nodeMatch.group(1).length();
			if (debug) Debug.debug(debug, dbgoffset, "Consuming ["+nodeMatch.group(1)+"]");
			if (debug) Debug.debug(debug, dbgoffset, "TRT label set to "+labStr);
			label = SymbolFactory.getSymbol(labStr);
			text.delete(0, totalLength);
			// temporary holding place for children
			Vector<TransducerRightTree> kids = new Vector<TransducerRightTree>();
			// add children until we reach the end pattern
			Matcher endMatch = endPat.matcher(text.toString());
			while (!endMatch.lookingAt()) {
				TransducerRightTree kid = new TransducerRightTree(text, states, trvm, dbgoffset+1);
				if (debug) Debug.debug(debug, dbgoffset, "TRT: adding child "+kid);
				if (trvm != null && kid.hasVariable())
					trvm.addRHS(kid, true);
				kid.parent = this;
				kids.add(kid);
				if (kid.isExtended() || kid.label != null) {
					if (debug) Debug.debug(debug, kid+" is extended or has non-null label, so the whole rule is extended");
					isExtended = true;
				}
				endMatch = endPat.matcher(text.toString());
			}
			totalLength = endMatch.group(1).length();
			if (debug) Debug.debug(debug, dbgoffset, "Consuming end group ["+endMatch.group(1)+"]");
			text.delete(0, totalLength);
			children = new TransducerRightTree[kids.size()];
			numChildren = children.length;
			kids.toArray(children);
			return;
		}
		// is input a non-variable leaf?
		Matcher leafMatch = leafPat.matcher(text.toString());
		if (leafMatch.lookingAt()) {
			if (debug) Debug.debug(debug, dbgoffset, "TRT matched "+text+" against label pattern");
			String labStr = leafMatch.group(2);
			int totalLength = leafMatch.group(1).length();
			if (debug) Debug.debug(debug, dbgoffset, "Consuming ["+leafMatch.group(1)+"]");
			label = SymbolFactory.getSymbol(labStr);
			if (debug) Debug.debug(debug, dbgoffset,  "TRT label set to "+labStr);
			// TODO: comma trip!
			text.delete(0, totalLength);
			return;
		}
		throw new DataFormatException("TRT: Couldn't match any pattern to "+text);

	}
	// convenience method to set the stream tokenizer the way we want it
	private void setTokenizerNormal(StreamTokenizer st) {
		st.resetSyntax();
		st.wordChars(0, '\u00FF');
		st.ordinaryChar('.');
		st.quoteChar('"');
		st.ordinaryChar('(');
		st.ordinaryChar(')');
		st.ordinaryChar(9);
		st.ordinaryChar(32);
	}
	// just used for construction
	int getConsumed() { return amtConsumed;}



	// 
	// for leftside composition
	// create a TRT out of the tree. Replace terminal symbols with appropriate variable syntax
	// and join the state referenced in the A transducer with the one found in the B map.
	public TransducerRightTree(TreeItem tree, TransducerRuleVariableMap trvm,
			HashMap<Symbol, TransducerRightSide> amap, 
			HashMap<Symbol, StateTreePair> bmap) throws ImproperConversionException {
		boolean debug = false;
		if (debug) Debug.debug(debug, "Making TRT out of "+tree);
		// terminal case: if found in bmap, get the rule, break it into state and label,
		//                get the TRT from the label and the variable with the oldtrvm
		if (tree.getNumChildren() == 0) {
			children = null;
			numChildren = 0;
			if (bmap.containsKey(tree.getLabel())) {
				StateTreePair stp = bmap.get(tree.getLabel());
				TransducerRightTree oldVar = (TransducerRightTree)amap.get(stp.getTree().getLabel());
				if (oldVar == null)
					throw new ImproperConversionException("No mapping for "+stp.getTree().getLabel());
				Vector<Symbol> vecstate = new Vector<Symbol>();
				vecstate.add(oldVar.getState());
				vecstate.add(stp.getState());
				state = SymbolFactory.getVecSymbol(vecstate);
				variable = oldVar.getVariable();
				trvm.addRHS(this, true);
				if (debug) Debug.debug(debug, "Did mapping to build traversed variable in TRT "+toString());
			}
			else {
				label = tree.getLabel();
				if (debug) Debug.debug(debug, "Built terminal TRT "+toString());
			}

		}
		else {
			label = tree.getLabel();
			numChildren = tree.getNumChildren();
			children = new TransducerRightTree[numChildren];
			for (int i = 0; i < numChildren; i++) {
				children[i] = new TransducerRightTree(tree.getChild(i), trvm, amap, bmap);
				children[i].parent = this;
				if (!isExtended) {
					if (children[i].isExtended() || children[i].label != null)
						isExtended = true;
				}
			}
		}
		parent = null;
	}

	// 
	// for multi-transducer leftside composition
	// create a TRT out of the tree. Replace special terminal symbols with appropriate variable syntax
	// and join the state referenced in the A transducer with the one found in the B map.
	public TransducerRightTree(TreeItem tree, TransducerRuleVariableMap trvm) throws ImproperConversionException {
		boolean debug = false;
		if (debug) Debug.debug(debug, "Making TRT out of "+tree);
		// terminal case: if found in bmap, get the rule, break it into state and label,
		//                get the TRT from the label and the variable with the oldtrvm
		if (tree.getNumChildren() == 0) {
			children = null;
			numChildren = 0;
			if (tree.getLabel() instanceof VecSymbol) {
				Vector<Symbol> vecstate = new Vector<Symbol>(((VecSymbol)tree.getLabel()).getVec());
				variable = vecstate.remove(0);
				state = SymbolFactory.getVecSymbol(vecstate);
				trvm.addRHS(this, true);
				if (debug) Debug.debug(debug, "Did mapping to build traversed variable in TRT "+toString());
			}
			else {
				label = tree.getLabel();
				if (debug) Debug.debug(debug, "Built terminal TRT "+toString());
			}

		}
		else {
			label = tree.getLabel();
			numChildren = tree.getNumChildren();
			children = new TransducerRightTree[numChildren];
			for (int i = 0; i < numChildren; i++) {
				children[i] = new TransducerRightTree(tree.getChild(i), trvm);
				children[i].parent = this;
				if (!isExtended) {
					if (children[i].isExtended() || children[i].label != null)
						isExtended = true;
				}
			}
		}
		parent = null;
	}

	
	// for rightside composition
	// build TRT from old TRT, adding its variables to the new trvm and incorporating states from
	// the A rule
	public TransducerRightTree(TransducerRightTree oldrhs, TransducerRuleVariableMap trvm,
			HashMap<TransducerRightSide, Symbol> aSideStateMap) throws ImproperConversionException {
		boolean debug = false;
		if (debug) Debug.debug(debug, "Making TRT out of "+oldrhs);
		// terminal case: if it's a variable, figure out what state the a rule ended up in 
		// and incorporate its state
		if (oldrhs.getNumChildren() == 0) {
			children = null;
			numChildren = 0;
			if (aSideStateMap.containsKey(oldrhs)) {
				Symbol aState = aSideStateMap.get(oldrhs);
				Symbol bState = oldrhs.getState();
				Vector<Symbol> vecstate = new Vector<Symbol>();
				vecstate.add(aState);
				vecstate.add(bState);
				state = SymbolFactory.getVecSymbol(vecstate);
				variable = oldrhs.getVariable();
				trvm.addRHS(this, true);
				if (debug) Debug.debug(debug, "Did mapping to build traversed variable in TRT "+toString());
			}
			else {
				label = oldrhs.getLabel();
				if (debug) Debug.debug(debug, "Built terminal TRT "+toString());
			}

		}
		else {
			label = oldrhs.getLabel();
			numChildren = oldrhs.getNumChildren();
			children = new TransducerRightTree[numChildren];
			for (int i = 0; i < numChildren; i++) {
				children[i] = new TransducerRightTree(oldrhs.getChild(i), trvm, aSideStateMap);
				children[i].parent = this;
				if (!isExtended) {
					if (children[i].isExtended() || children[i].label != null)
						isExtended = true;
				}
			}
		}
		parent = null;
	}
	
	
	// for input epsilon rightside chain composition
	// build TRT from old TRT, with no trvm update, and adding the specified state
	// to all variables in the rhs
	public TransducerRightTree(TransducerRightTree oldrhs, Symbol aState) {
		boolean debug = false;
		if (debug) Debug.debug(debug, "Making TRT out of "+oldrhs);
		// terminal case: if it's a variable, figure out what state the a rule ended up in 
		// and incorporate its state
		if (oldrhs.getNumChildren() == 0) {
			children = null;
			numChildren = 0;
			if (oldrhs.hasVariable()) {
				if (oldrhs.getState() instanceof VecSymbol)
					state = SymbolFactory.getVecSymbol(aState, ((VecSymbol)oldrhs.getState()));
				else {
					Vector<Symbol> vec = new Vector<Symbol>();
					vec.add(aState);
					vec.add(oldrhs.getState());
					state = SymbolFactory.getVecSymbol(vec);
				}
				variable = oldrhs.getVariable();
			}
			if (oldrhs.hasLabel())
				label = oldrhs.getLabel();
			if (debug) Debug.debug(debug, "Built terminal TRT "+toString());
		}
		else {
			label = oldrhs.getLabel();
			numChildren = oldrhs.getNumChildren();
			children = new TransducerRightTree[numChildren];
			for (int i = 0; i < numChildren; i++) {
				children[i] = new TransducerRightTree(oldrhs.getChild(i), aState);
				children[i].parent = this;
				if (!isExtended) {
					if (children[i].isExtended() || children[i].label != null)
						isExtended = true;
				}
			}
		}
		parent = null;
	}
	
	
	// conversion for RTG rule-based trees
	// create a tree from t, replacing states with state/variables
	// private constructor does the real work
	public TransducerRightTree(TreeItem t, TransducerRuleVariableMap trvm, HashSet states) {
		this(t, trvm, states, true);
	}
	public TransducerRightTree(TreeItem t, TransducerRuleVariableMap trvm, HashSet states, boolean isStart) {
		if (isStart) {
			nextVarIndex = 0;
		}

		// if variable, set var value as next index 
		if (states.contains(t.getLabel())) {
			state = t.getLabel();
			variable = SymbolFactory.getSymbol("x"+(nextVarIndex++));
			label = null;
			children = null;
			numChildren = 0;
			return;
		}
		// normal case is non-var -- copy label, recurse on kids
		label = t.getLabel();
		variable = null;
		state = null;
		numChildren = t.getNumChildren();
		children = new TransducerRightTree[t.getNumChildren()];
		for (int i = 0; i < numChildren; i++) {
			children[i] = new TransducerRightTree(t.getChild(i), trvm, states, false);
			children[i].parent = this;
			if (children[i].hasVariable())
				trvm.addRHS(children[i], true);
			if (!isExtended) {
				if (children[i].isExtended() || children[i].label != null)
					isExtended = true;
			}
		}
		parent = null;
	}

	// build TRT from variable in TLT, specified state
	public TransducerRightTree(TransducerLeftTree tlt, Symbol currst) {
		label = null;
		variable = tlt.getVariable();
		state = currst;
		children = null;
		parent = null;
		numChildren = 0;
		isExtended = false;
	}

	// build TRT from specified variable and state
	public TransducerRightTree(Symbol currvar, Symbol currst) {
		label = null;
		variable = currvar;
		state = currst;
		children = null;
		parent = null;
		numChildren = 0;
		isExtended = false;
	}

	
	// build TRT from parent symbol, vector of variable trts
	public TransducerRightTree(Symbol lbl, Vector<TransducerRightTree> kids, TransducerRuleVariableMap trvm) {
		label = lbl;
		variable = null;
		state = null;
		numChildren = kids.size();
		children = new TransducerRightTree[kids.size()];
		kids.toArray(children);
		for (TransducerRightTree kid : kids) {
			kid.parent = this;
			trvm.addRHS(kid, true);
			if (!isExtended) {
				if (kid.isExtended() || kid.label != null)
					isExtended = true;
			}
		}
		parent = null;
	}

	


	// for epsilon-input rules
	// build TRT from old TRT, join of its states and the passed in state, and replacement of its variables 
	// with this one. Done recursively!
	public TransducerRightTree(TreeTransducerRuleSet trs, TransducerRightTree oldrhs, 
			TransducerRuleVariableMap trvm, Symbol aState, 
			Symbol var) {
		boolean debug = false;
		parent = null;
		// variable case: use the var symbol, make a join)
		if (oldrhs.hasVariable()) {
			Vector<Symbol> stvec = new Vector<Symbol>();
			stvec.add(aState);
			stvec.add(oldrhs.getState());
			state = SymbolFactory.getVecSymbol(stvec);
			variable = var;
			label = null;
			numChildren = 0;
			children = null;
			isExtended = false;
			if (debug) Debug.debug(debug, "Replaced variable "+oldrhs+" with "+toString());
			return;
		}
		if (oldrhs.hasLabel()) {
			label = oldrhs.getLabel();
			variable = null;
			state = null;
			numChildren = oldrhs.numChildren;
			children = new TransducerRightTree[numChildren];
			for (int i = 0; i < numChildren; i++) {
				children[i] = new TransducerRightTree(trs, oldrhs.getChild(i), trvm, aState, var);
				children[i].parent = this;
				if (children[i].hasVariable())
					trvm.addRHS(children[i], true);
				if (children[i].isExtended() || children[i].label != null)
					isExtended = true;
			}
			if (debug) Debug.debug(debug, "Replaced variable "+oldrhs+" with "+toString());
			return;
		}
	}
	
	// for rightside composition
	// given tree with VecSymbol leaves and existing TRT, make new TRT that passes in the states
	// at the leaves of the tree to the current state symbols
	
	// also, strip those symbols from the tree
	TransducerRightTree(TreeItem t, TransducerRightTree trt) {
		boolean debug = false;
		// first, map from variables to the new states that will be added
		if (debug) Debug.debug(debug, "Before building varstatemap: "+t);
		HashMap<Symbol, Symbol> map = buildVarStateMap(t);
		if (debug) Debug.debug(debug, "After building varstatemap: "+t);
		parent = null;
		// special case: if trt has no children, operate on the map here
		if (trt.hasVariable()) {
			if (trt.getState() instanceof VecSymbol)
				state = SymbolFactory.getVecSymbol(map.get(trt.getVariable()), (VecSymbol)trt.getState());
			else {
				Vector<Symbol> vec = new Vector<Symbol>();
				vec.add(map.get(trt.getVariable()));
				vec.add(trt.getState());
				state = SymbolFactory.getVecSymbol(vec);
			}
			variable = trt.getVariable();
			if (trt.hasLabel())
				label = trt.getLabel();
			else
				label = null;
			numChildren = 0;
			children = null;
			isExtended = false;
			if (debug) Debug.debug(debug, "Replaced "+trt+" with "+toString());
			return;
		}
		// normal case: start off the tree, then let the more specific constructor handle the rest
		else {
			label = trt.getLabel();
			variable = null;
			state = null;
			numChildren = trt.numChildren;
			children = new TransducerRightTree[numChildren];
			for (int i = 0; i < numChildren; i++) {
				children[i] = new TransducerRightTree(map, trt.getChild(i));
				children[i].parent = this;
				if (children[i].isExtended() || children[i].label != null)
					isExtended = true;
			}
		}		
		if (debug) Debug.debug(debug, "Replaced "+trt+" with "+toString());
		return;
	}
	
	// internal constructor for rightside construction
	private TransducerRightTree(HashMap<Symbol, Symbol> map, TransducerRightTree trt) {
		boolean debug = false;
		parent = null;
		// special case: if trt has no children, operate on the map here
		if (trt.hasVariable()) {
			if (trt.getState() instanceof VecSymbol)
				state = SymbolFactory.getVecSymbol(map.get(trt.getVariable()), (VecSymbol)trt.getState());
			else {
				Vector<Symbol> vec = new Vector<Symbol>();
				vec.add(map.get(trt.getVariable()));
				vec.add(trt.getState());
				state = SymbolFactory.getVecSymbol(vec);
			}
			variable = trt.getVariable();
			if (trt.hasLabel())
				label = trt.getLabel();
			else
				label = null;
			numChildren = 0;
			children = null;
			isExtended = false;
			if (debug) Debug.debug(debug, "Replaced "+trt+" with "+toString());
			return;
		}
		// normal case: start off the tree, then let the more specific constructor handle the rest
		else {
			label = trt.getLabel();
			variable = null;
			state = null;
			numChildren = trt.numChildren;
			children = new TransducerRightTree[numChildren];
			for (int i = 0; i < numChildren; i++) {
				children[i] = new TransducerRightTree(map, trt.getChild(i));
				children[i].parent = this;
				if (children[i].isExtended() || children[i].label != null)
					isExtended = true;
			}
		}		
		if (debug) Debug.debug(debug, "Replaced "+trt+" with "+toString());
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
	
	// map bits of a backward-applied tree onto here 
	// used by both backward application and rightward composition
	public void getInverseTreeMatchPairs(TreeItem t, HashMap<TransducerRightTree, TreeItem> h) {
		// from rightward composition
		if (t.label instanceof VecSymbol) {
			h.put(this, t);
			return;
		}
		// from backward application
		else if (hasVariable()) {
			h.put(this, t);
			return;
		}
		for (int i = 0; i < numChildren; i++) {
			children[i].getInverseTreeMatchPairs(t.getChild(i), h);
		}
		return;
	}

	
	public String toString() {
		return toString(0);
	}

	// can display varying levels of explicity
	public String toString(int exlv) {
		StringBuffer l = new StringBuffer();
		if (state != null && variable != null) {
			l.append(state.toString()+"."+variable.toString());
			if (exlv >= 1 && label != null)
				l.append("["+label.toString()+"]");
		}
		else if (label != null) {
			l.append(label.toString());
			if (numChildren > 0) {
				l.append("(");
				for (int i = 0; i < numChildren; i++) {
					if (i > 0)
						l.append(" ");
					l.append(children[i].toString());
				}
				l.append(")");
			}
		}
		return l.toString();
	}

	// clone constructor -- uses deep copy
	public TransducerRightTree(TransducerRightTree a) {
		this();
		deepCopy(a);
	}
	// deep copy - make a clone of passed in tree into this structure. also set its lastcopied pointer

	public void deepCopy(TransducerRightTree a) {
		label = a.getLabel();
		variable = a.getVariable();
		state = a.getState();
		numChildren = a.getNumChildren();
		children = new TransducerRightTree[numChildren];
		for (int i = 0; i < numChildren; i++) {
			children[i] = new TransducerRightTree();
			children[i].deepCopy(a.getChild(i));
		}
		a.setLastCloned(this);
	}
	// tree-specific accessors

	public int getNumChildren() { return numChildren; }
	public TransducerRightTree getChild(int i) { return children[i]; }


	// isTreeMatch: given an actual tree, is this right hand side valid?
	// if there is a variable and no label, yes (if the transformation matches but that's not part of this)
	// if the label matches and the number of children is identical
	// and for each child, isTreeMatch matches, yes

	public boolean isTreeMatch(TreeItem t) {
		boolean debug = false;
		if (hasVariable() && !hasLabel())
			return true;

		if (!t.getLabel().equals(label)) {
			if (debug) Debug.debug(debug, "RIGHT SIDE Fail: ["+label.toString()+"] not equal to ["+t.getLabel().toString()+"]");
			return false;
		}
		if (hasVariable())
			return true;
		if (t.getNumChildren() != numChildren) {
			if (debug) Debug.debug(debug, "RIGHT SIDE Fail: "+t.getNumChildren()+" in "+t.toString()+" not the same number of children as "+numChildren+" in "+toString());
			return false;
		}
		for (int i = 0; i < numChildren; i++) {
			if (!children[i].isTreeMatch(t.getChild(i)))
				return false;
		}
		return true;
	}
	
	
	
	

	// memoized calculation of variable children (i.e. deep children)
	public Vector<TransducerRightTree> getVariableChildren() {
		if (varChildren == null) {
			varChildren = new Vector<TransducerRightTree>();
			recurseAddVarChildren(varChildren);
		}
		return varChildren;
	}
	// where the work is done
	private void recurseAddVarChildren(Vector<TransducerRightTree> v) {
		if (variable != null)
			v.add(this);
		for (int i = 0; i < numChildren; i++)
			children[i].recurseAddVarChildren(v);
	}
	// memoized calculation of non-variable leaf children
	public Vector<TransducerRightTree> getNonVariableChildren() {
		if (nonVarChildren == null) {
			nonVarChildren = new Vector<TransducerRightTree>();
			recurseAddNonVarChildren(nonVarChildren);
		}
		return nonVarChildren;
	}
	// where the work is done
	private void recurseAddNonVarChildren(Vector<TransducerRightTree> v) {
		if (numChildren == 0 && variable == null)
			v.add(this);
		for (int i = 0; i < numChildren; i++)
			children[i].recurseAddNonVarChildren(v);
	}

	// map a tree onto this tree. exception if the data format doesn't match
	public void mapTree(TreeItem t, int start, int end) throws Exception {
		varChildren = null;
		nonVarChildren = null;
		if (label != null && !label.equals(t.getLabel()))
			throw new Exception("TransducerRightTree "+toString()+" doesn't match tree "+t.toString()+
					": label error, "+label.toString()+" vs. "+t.getLabel().toString());
		if (variable == null && numChildren != t.getNumChildren())
			throw new Exception("TransducerRightTree "+toString()+" doesn't match tree "+t.toString()+
					": children error, "+numChildren+" vs. "+t.getNumChildren());
		if (end < start)
			throw new Exception("TransducerRightTree "+toString()+" maps "+t.toString()+" with illegal indices ("+start+", "+end+")");
		mapTree = t;
		mapTreeStart = start;
		mapTreeEnd = end;
		if (variable == null) {
			int nextStart = start;
			for (int i = 0; i < numChildren; i++) {
				TreeItem child = t.getChild(i);
				int nextEnd = nextStart+child.getLeaves().length;
				children[i].mapTree(child, nextStart, nextEnd);
				nextStart = nextEnd;
			}
		}
	}
	// get the map. might be null!
	public TreeItem getMapTree() { 
		return mapTree;
	}
	// get the indices
	public int getMapTreeStart() {
		return mapTreeStart;
	}
	public int getMapTreeEnd() {
		return mapTreeEnd;
	}


	// get leaves: recursive way of generating the yield language.
	// note that this method is memoized
	/** Get all terminal leaves or states as symbols.

	@return an array of Symbols for each tree leaf
	 */
	public Symbol[] getLeaves() {
		if (leaves == null) {
			if (numChildren == 0) {
				if (hasLabel())
					leaves = new Symbol[] {label};
				else if (hasState())
					leaves = new Symbol[] {state};
				else {
					Debug.debug(true, "Problem while running getLeaves for TransducerRightTree: leaf symbol has neither label nor state: "+toString());
					System.exit(1);
				}
				return leaves;
			}
			Symbol[][] leafSets = new Symbol[numChildren][];
			int numItems = 0;
			// gather the arrays
			for (int i = 0; i < numChildren; i++) {
				leafSets[i] = children[i].getLeaves();
				numItems += leafSets[i].length; 
			}
			// compress them into one, then give it to the above
			leaves = new Symbol[numItems];
			int currItem = 0;
			for (int i = 0; i < numChildren; i++) {
				for (int j = 0; j < leafSets[i].length; j++) {
					leaves[currItem++] = leafSets[i][j];
				}
			}
		}
		return leaves;
	}

	// return a Tree that can be turned into an RTG. Mark state/variable leaves specially
	public TreeItem getImageTree() {
		// terminal case: form tree, mark if necessary, return
		if (numChildren == 0) {
			if (hasState())
				return new TreeItem(getState(), getVariable());
			else
				return new TreeItem(getLabel());
		}
		// non-terminal case: process all children, and process parent
		else {
			TreeItem[] kids = new TreeItem[children.length];
			for (int i = 0; i < children.length; i++) 
				kids[i] = children[i].getImageTree();
			return new TreeItem(kids.length, getLabel(), kids);
		}
	}

	// return a Tree by replacing variables with designated values
	public TreeItem getImageTree(HashMap<TransducerRightTree, Symbol> backmap) {
		// terminal case: replace if variable
		if (numChildren == 0) {
			if (backmap.containsKey(this))
				return new TreeItem(backmap.get(this));
			return new TreeItem(getLabel());
		}
		else {
			TreeItem[] kids = new TreeItem[children.length];
			for (int i = 0; i < children.length; i++) 
				kids[i] = children[i].getImageTree(backmap);
			return new TreeItem(kids.length, getLabel(), kids);
		}
	}
	
	// return a tree by turning variable/state children into vectors
	// like getImageTree above but without the map
	public TreeItem getVecVarImageTree() {
		// terminal case: replace if variable
		if (numChildren == 0) {
			if (hasVariable()) {
				Vector<Symbol> vec = new Vector<Symbol>();
				vec.add(getVariable());
				vec.add(getState());
				return new TreeItem(SymbolFactory.getVecSymbol(vec));
			}
			return new TreeItem(getLabel());
		}
		else {
			TreeItem[] kids = new TreeItem[children.length];
			for (int i = 0; i < children.length; i++) 
				kids[i] = children[i].getVecVarImageTree();
			return new TreeItem(kids.length, getLabel(), kids);
		}
	}

	// return a tree by turning variable/state children into vectors
	// like getImageTree above but without the map
	// for left-oriented general epsilon input adds: insert the Vector of state symbols in front of the extant state
	public TreeItem getEpsInputVecVarImageTree(Vector<Symbol> states) {
		// terminal case: replace if variable
		if (numChildren == 0) {
			if (hasVariable()) {
				Vector<Symbol> vec = new Vector<Symbol>();
				vec.add(getVariable());
				for (Symbol s: states)
					vec.add(s);
				vec.add(getState());
				return new TreeItem(SymbolFactory.getVecSymbol(vec));
			}
			return new TreeItem(getLabel());
		}
		else {
			TreeItem[] kids = new TreeItem[children.length];
			for (int i = 0; i < children.length; i++) 
				kids[i] = children[i].getEpsInputVecVarImageTree(states);
			return new TreeItem(kids.length, getLabel(), kids);
		}
	}
	
	// return a tree by turning variable/state children into vectors
	// like getImageTree above but without the map
	// for left-oriented initial epsilon output adds: insert the State symbol after the extant state
	public TreeItem getEpsOutputVecVarImageTree(Symbol nextState) {
		// terminal case: replace if variable
		if (numChildren == 0) {
			if (hasVariable()) {
				Vector<Symbol> vec = new Vector<Symbol>();
				vec.add(getVariable());
				vec.add(getState());
				vec.add(nextState);
				return new TreeItem(SymbolFactory.getVecSymbol(vec));
			}
			return new TreeItem(getLabel());
		}
		else {
			TreeItem[] kids = new TreeItem[children.length];
			for (int i = 0; i < children.length; i++) 
				kids[i] = children[i].getEpsOutputVecVarImageTree(nextState);
			return new TreeItem(kids.length, getLabel(), kids);
		}
	}

}
