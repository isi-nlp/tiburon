package edu.isi.tiburon;

import java.io.StreamTokenizer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// transducer tree component for the left side of a rule
// a tree is:
//      a label (mandatory if nterm, optional if term)
//      children if nterm
//      optional variable if term (there must be either a label or a variable or both)
//      ???
// later on a tree can receive info about its variable locations...

public class TransducerLeftTree {

	private Symbol label;
	private Symbol variable;
	private TransducerLeftTree[] children;
	private int numChildren;

	// memoized hash code
	private Hash hsh=null;

	// amountConsumed used only in construction
	private int amtConsumed;

	// memoized variable children, in depth-first order
	private Vector varChildren;

	// memoized non-variable children
	private Vector nonVarChildren;

	// tree value mapped to this tree. can be replaced. isn't guaranteed
	private TreeItem mapTree;
	// tree indices. also can be replaced and not guaranteed
	private int mapTreeStart;
	private int mapTreeEnd;

	// global check for comma'd symbols, which leads to a warning
	private static boolean commaTrip;

	// convenience variable just for conversion construction
	private static int nextVarIndex;


	// is this an extended tree? true if children have non-lookaheadlabel or are extended
	private boolean isExtended = false;

	public boolean isExtended() { return isExtended; }

	private boolean isLookahead = false;
	public boolean isLookahead() { return isLookahead; }
	// is this an epsilon tree? true if head is variable
	public boolean isEpsilon() { return hasVariable(); }

	static {
		commaTrip = false;
	}
	public static void setCommaTrip() { commaTrip = true; }

	// to allow for equality tests in set operations
	public int hashCode() {
		if (hsh == null)
			setHashCode();
		return  hsh.bag(Integer.MAX_VALUE);
	}
	public Hash getHash() {
		if (hsh == null)
			setHashCode();
		return hsh;
	}

	private void setHashCode() {
		hsh = new Hash(label == null ? new Hash() : label.getHash());
		hsh = hsh.sag( variable == null ? new Hash() : variable.getHash());
		for (int i = 0; i < numChildren; i++)
			hsh = hsh.sag(children[i].getHash());
	}

	// equal if label and/or variable are same and all children are equal

	public boolean equals(Object o) {
		if (!o.getClass().equals(this.getClass()))
			return false;

		TransducerLeftTree t = (TransducerLeftTree)o;
		if (label == null ^ t.label == null)
			return false;
		if (label != null && !label.equals(t.label))
			return false;
		if (variable == null ^ t.variable == null)
			return false;
		if (variable != null && !variable.equals(t.variable))
			return false;
		if (numChildren != t.numChildren)
			return false;
		for (int i = 0; i < numChildren; i++)
			if (!children[i].equals(t.children[i]))
				return false;
		return true;
	}
	// solo version only used in debugging code called from elsewhere
	public TransducerLeftTree(StringBuffer text) throws DataFormatException {
		this(text, null);
	}

	public TransducerLeftTree(StringBuffer text, TransducerRuleVariableMap trvm) throws DataFormatException {
		this(text, trvm, 0);
	}

	// node (non-leaf) pattern = symbol followed by open paren
	private static Pattern nodePat = Pattern.compile("(\\s*([^\\s\\(\\):\"\\.]+|\"\"\"|\"[^\"]+\")\\s*\\()");
	// variable leaf pattern = colon-divided symbols -- second symbol optional
	// notice the quoted string comes first in the option list after the colon, so that the empty string is the last checked choice.
	private static Pattern varLeafPat = Pattern.compile("(\\s*([^\\s\\(\\):\"\\.]+|\"\"\"|\"[^\"]+\"):(\"\"\"|\"[^\"]+\"|[^\\s\\(\\):\"\\.]*))");
	// non-variable leaf pattern = symbol. Use last!
	private static Pattern leafPat = Pattern.compile("(\\s*([^\\s\\(\\):\"\\.]+|\"\"\"|\"[^\"]+\"))");
	// end-of-tree pattern = spaces and right paren
	private static Pattern endPat = Pattern.compile("(\\s*\\))");
	public TransducerLeftTree(StringBuffer text, TransducerRuleVariableMap trvm, 
			int dbgoffset) throws DataFormatException {
		boolean debug = false;
		label = null;
		variable = null;
		varChildren = null;
		nonVarChildren = null;
		mapTree = null;
		mapTreeStart = mapTreeEnd = 0;
		isExtended = false;
		isLookahead = false;


		// is input a variable leaf?
		Matcher varLeafMatch = varLeafPat.matcher(text.toString());
		if (varLeafMatch.lookingAt()) {
			if (debug) Debug.debug(debug, dbgoffset, "TLT matched "+text+" against variable/label pattern");
			String varStr = varLeafMatch.group(2);
			String labStr = varLeafMatch.group(3);
			int totalLength = varLeafMatch.group(1).length();
			if (debug) Debug.debug(debug, dbgoffset, "Consuming ["+varLeafMatch.group(1)+"]");
			variable = SymbolFactory.getSymbol(varStr);
			if (debug) Debug.debug(debug, dbgoffset,  "TLT variable set to "+varStr);
			if (labStr != null && labStr.length() > 0) {
				if (debug) Debug.debug(debug, dbgoffset,  "TLT variable label set to ["+labStr+"]");
				label = SymbolFactory.getSymbol(labStr);
				isLookahead = true;
			}
			// TODO: comma trip!
			text.delete(0, totalLength);
			return;
		}
		// is input the head of a tree?
		Matcher nodeMatch = nodePat.matcher(text.toString());
		if (nodeMatch.lookingAt()) {
			if (debug) Debug.debug(debug, dbgoffset, "TLT matched "+text+" against node pattern");
			String labStr = nodeMatch.group(2);
			int totalLength = nodeMatch.group(1).length();
			if (debug) Debug.debug(debug, dbgoffset, "Consuming ["+nodeMatch.group(1)+"]");
			if (debug) Debug.debug(debug, dbgoffset, "TLT label set to "+labStr);
			label = SymbolFactory.getSymbol(labStr);
			text.delete(0, totalLength);
			// temporary holding place for children
			Vector<TransducerLeftTree> kids = new Vector<TransducerLeftTree>();
			// add children until we reach the end pattern
			Matcher endMatch = endPat.matcher(text.toString());
			while (!endMatch.lookingAt()) {
				TransducerLeftTree kid = new TransducerLeftTree(text, trvm, dbgoffset+1);
				if (debug) Debug.debug(debug, dbgoffset, "TLT: adding child "+kid);
				if (trvm != null && kid.hasVariable())
					trvm.addLHS(kid);
				kids.add(kid);
				if (kid.isExtended() || kid.label != null) {
					if (debug) Debug.debug(debug, kid+" is extended or has non-null label, so the whole rule is extended");
					isExtended = true;
				}
				if (kid.isLookahead())
					isLookahead = true;
				endMatch = endPat.matcher(text.toString());
			}
			totalLength = endMatch.group(1).length();
			if (debug) Debug.debug(debug, dbgoffset, "Consuming end group ["+endMatch.group(1)+"]");
			text.delete(0, totalLength);
			children = new TransducerLeftTree[kids.size()];
			numChildren = children.length;
			kids.toArray(children);
			return;
		}
		// is input a non-variable leaf?
		Matcher leafMatch = leafPat.matcher(text.toString());
		if (leafMatch.lookingAt()) {
			if (debug) Debug.debug(debug, dbgoffset, "TLT matched "+text+" against label pattern");
			String labStr = leafMatch.group(2);
			int totalLength = leafMatch.group(1).length();
			if (debug) Debug.debug(debug, dbgoffset, "Consuming ["+leafMatch.group(1)+"]");
			label = SymbolFactory.getSymbol(labStr);
			if (debug) Debug.debug(debug, dbgoffset,  "TLT label set to "+labStr);
			// TODO: comma trip!
			text.delete(0, totalLength);
			return;
		}
		throw new DataFormatException("TLT: Couldn't match any pattern to "+text);
	}

	// conversion for RTG rule-based trees
	// create a tree from t, replacing states with variables
	// private constructor does the real work
	public TransducerLeftTree(TreeItem t, TransducerRuleVariableMap trvm, HashSet<Symbol> availableSyms, HashSet states) {
		this(t, trvm, availableSyms, states, true);
	}
	public TransducerLeftTree(TreeItem t, TransducerRuleVariableMap trvm, HashSet<Symbol> availableSyms, HashSet states, boolean isStart) {
		boolean debug = false;
		if (isStart)
			nextVarIndex = 0;
		isLookahead = false;
		// if variable, set var value as next index 
		if (states.contains(t.getLabel())) {
			variable = SymbolFactory.getSymbol("x"+(nextVarIndex++));
			label = null;
			children = null;
			numChildren = 0;
			return;
		}
		// normal case is non-var -- copy label, recurse on kids
		label = t.getLabel();
		availableSyms.add(label);
		if (debug) Debug.debug(debug, "Adding "+label+" to availableSyms");
		variable = null;
		numChildren = t.getNumChildren();
		children = new TransducerLeftTree[t.getNumChildren()];
		for (int i = 0; i < numChildren; i++) {
			children[i] = new TransducerLeftTree(t.getChild(i), trvm, availableSyms, states, false);
			if (children[i].hasVariable())
				trvm.addLHS(children[i]);
			// should we set this as extended?
			if (!isExtended) {
				if (children[i].isExtended() || children[i].label != null)
					isExtended = true;
			}
		}
	}

	
	// for rightside composition
	// create a TLT out of the tree. Replace terminal symbols with appropriate variable syntax
	// and seed the trvm.
	public TransducerLeftTree(TreeItem tree, TransducerRuleVariableMap trvm,
			HashMap<Symbol, TransducerRightSide> amap, 
			HashMap<Symbol, StateTreePair> bmap,
			HashMap<TransducerRightSide, Symbol> aSideStateMap) throws ImproperConversionException {
		boolean debug = false;
		if (debug) Debug.debug(debug, "Making TLT out of "+tree);
		// terminal case: if found in bmap, get the rule, break it into state and label,
		//                get the TRT from the label and the variable with the oldtrvm
		if (tree.getNumChildren() == 0) {
			children = null;
			numChildren = 0;
			if (bmap.containsKey(tree.getLabel())) {
				StateTreePair stp = bmap.get(tree.getLabel());
				TransducerRightSide oldVar = amap.get(stp.getTree().getLabel());
				if (oldVar == null)
					throw new ImproperConversionException("No mapping for "+stp.getTree().getLabel());
				variable = oldVar.getVariable();
				if (oldVar.hasLabel()) {
					label = oldVar.getLabel();
					isLookahead = true;
				}
				trvm.addLHS(this);
				TransducerRightSide[] arr = new TransducerRightSide[1];
				aSideStateMap.put(oldVar, stp.getState());
				if (debug) Debug.debug(debug, "Did mapping to build traversed variable in TLT "+toString());
			}
			else {
				label = tree.getLabel();
				if (debug) Debug.debug(debug, "Built terminal TLT "+toString());
			}

		}
		else {
			label = tree.getLabel();
			numChildren = tree.getNumChildren();
			children = new TransducerLeftTree[numChildren];
			for (int i = 0; i < numChildren; i++) {
				children[i] = new TransducerLeftTree(tree.getChild(i), trvm, amap, bmap, aSideStateMap);
				if (!isExtended) {
					if (children[i].isExtended() || children[i].variable == null)
						isExtended = true;
				}
				if (!isLookahead) {
					if (children[i].isLookahead())
						isLookahead = true;
				}
			}
		}
	}
	
	// for vector-based rightside composition
	// create a TLT out of the tree. Replace symbolvec symbols with appropriate variable syntax
	// and seed the trvm.
	public TransducerLeftTree(TreeItem tree, TransducerRuleVariableMap trvm) throws ImproperConversionException {
		boolean debug = false;
		if (debug) Debug.debug(debug, "Making TLT out of "+tree);
		// terminal case: if a vector symbol try to rebuild the tree
		if (tree.getNumChildren() == 0) {
			children = null;
			numChildren = 0;
			if (tree.label instanceof VecSymbol) {
				Vector<Symbol> vec = ((VecSymbol)tree.label).getVec();
				if (vec.size() < 1 || vec.size() > 2)
					throw new ImproperConversionException("Weird element in transducer left tree: "+vec);
				variable = vec.firstElement();
				if (vec.size() == 2) {
					label = vec.lastElement();
					isLookahead = true;
				}
				else
					label = null;
				trvm.addLHS(this);
				if (debug) Debug.debug(debug, "Built traversed variable in TLT "+toString());
			}
			else {
				label = tree.getLabel();
				if (debug) Debug.debug(debug, "Built terminal TLT "+toString());
			}

		}
		else {
			label = tree.getLabel();
			numChildren = tree.getNumChildren();
			children = new TransducerLeftTree[numChildren];
			for (int i = 0; i < numChildren; i++) {
				children[i] = new TransducerLeftTree(tree.getChild(i), trvm);
				if (!isExtended) {
					if (children[i].isExtended() || children[i].label != null)
						isExtended = true;
				}
				if (!isLookahead) {
					if (children[i].isLookahead())
						isLookahead = true;
				}
			}
		}
	}
	
	
	// simple symbol constructor
	public TransducerLeftTree(Symbol sym) {
		label = sym;
		variable = null;
		children = null;
		numChildren = 0;
	}

	// create TLT with given label and n variable children, numbered sequentially
	public TransducerLeftTree(Symbol sym, int n, TransducerRuleVariableMap trvm) {
		label = sym;
		numChildren = n;
		if (numChildren > 0) {
			children = new TransducerLeftTree[n];
			for (int i = 0; i < n; i++) {
				children[i] = new TransducerLeftTree(i);
				trvm.addLHS(children[i]);
			}
		}
		isExtended = false;
		isLookahead = false;

		variable = null;
	}
	// create simple variable TLT
	public TransducerLeftTree(int n) {
		label = null;
		variable = SymbolFactory.getSymbol("x"+n);
		children = null;
		numChildren = 0;
		isExtended = false;
		isLookahead = false;
	}


	// convenience method to set the stream tokenizer the way we want it
	private void setTokenizerNormal(StreamTokenizer st) {
		st.resetSyntax();
		st.wordChars(0, '\u00FF');
		st.ordinaryChar(':');
		st.quoteChar('"');
		st.ordinaryChar('(');
		st.ordinaryChar(')');
		st.ordinaryChar(9);
		st.ordinaryChar(32);
	}
	// just used for construction
	int getConsumed() { return amtConsumed;}

	// clone constructor used by composition
	// pretty much make an exact copy, though the trvm map is a new one, so that all has to be done again
	public TransducerLeftTree(TransducerLeftTree tlt, TransducerRuleVariableMap trvm) {
		if (tlt.label == null)
			label = null;
		else
			label = tlt.label;
		if (tlt.variable == null)
			variable = null;
		else
			variable = tlt.variable;
		if (tlt.numChildren > 0) {
			numChildren = tlt.numChildren;
			children = new TransducerLeftTree[numChildren];
			for (int i = 0; i < numChildren; i++) {
				children[i] = new TransducerLeftTree(tlt.children[i], trvm);
				
			}
		}
		else {
			numChildren = 0;
			children = null;
		}
		isExtended = tlt.isExtended;
		isLookahead = tlt.isLookahead;
		if (trvm != null && hasVariable())
			trvm.addLHS(this);	
	}


	public String toString() {
		StringBuffer l = new StringBuffer();
		if (hasVariable())
			l.append(variable.toString()+":");
		if (hasLabel())
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
		return l.toString();
	}

	// checks and accesses on label and variable
	public boolean hasLabel() { return label != null; }
	public boolean hasVariable() { return variable != null; }
	public Symbol getLabel() { return label; }
	public Symbol getVariable() { return variable; }
	public int getNumChildren() { return numChildren; }
	public TransducerLeftTree getChild(int i) { return children[i]; }

	//    settors change the hash. shouldn't rely on hash code!
	public void setLabel(Symbol s) {
		label = s;
	}


	// isTreeMatch: given an actual tree, is this left hand side valid?
	// if there is a variable and no label, yes (if the transformation matches but that's not part of this)
	// if there is a variable and a label, check the type of the tree's label -- if it is a state-bearing
	// symbol, then okay. if not, label must match
	
	// if the label matches and the number of children is identical
	// and for each child, isTreeMatch matches, yes

	// TODO: join this up with a method that acquires the subtrees and new states that must be explored
	//       to really answer the question
	public boolean isTreeMatch(TreeItem t) {
		boolean debug = false;
		if (hasVariable() && !hasLabel())
			return true;
		if (hasVariable() && hasLabel()) {
			if (t.label instanceof AliasedSymbol)
				return true;
			else
				return getLabel() == t.label;
		}
		if (!t.getLabel().equals(label)) {
			if (debug) Debug.debug(debug, "LEFT SIDE Fail: "+label.toString()+" not equal to "+t.getLabel().toString());
			return false;
		}
		if (hasVariable())
			return true;
		if (t.getNumChildren() != numChildren) {
			if (debug) Debug.debug(debug, "LEFT SIDE Fail: "+t.getNumChildren()+" in "+t.toString()+" not the same number of children as "+numChildren+" in "+toString());
			return false;
		}
		for (int i = 0; i < numChildren; i++) {
			if (!children[i].isTreeMatch(t.getChild(i)))
				return false;
		}
		return true;
	}

	// returns a HashMap of tree->variable pairs that need to be explored
	// TODO: figure out a way to keep from having to do this twice!
	// right now I'm assuming the tree matches this left tree
	public HashMap getTreeMatchPairs(TreeItem t) {
		HashMap h = new HashMap();
		if (hasVariable()) {
			h.put(t, this);
			return h;
		}
		for (int i = 0; i < numChildren; i++) {
			h.putAll(children[i].getTreeMatchPairs(t.getChild(i)));
		}
		return h;
	}

	// one of these is the more useful...
	public HashMap<TransducerLeftTree, TreeItem> getInverseTreeMatchPairs(TreeItem t) {
		HashMap<TransducerLeftTree, TreeItem> h = new HashMap<TransducerLeftTree, TreeItem>();
		if (hasVariable()) {
			h.put(this, t);
			return h;
		}
		for (int i = 0; i < numChildren; i++) {
			h.putAll(children[i].getInverseTreeMatchPairs(t.getChild(i)));
		}
		return h;
	}

	// memoized calculation of variable children (i.e. deep children)
	public Vector getVariableChildren() {
		if (varChildren == null) {
			varChildren = new Vector();
			recurseAddVarChildren(varChildren);
		}
		return varChildren;
	}
	// where the work is done
	private void recurseAddVarChildren(Vector v) {
		if (variable != null)
			v.add(this);
		for (int i = 0; i < numChildren; i++)
			children[i].recurseAddVarChildren(v);
	}

	// memoized calculation of non-variable leaf children
	public Vector getNonVariableChildren() {
		if (nonVarChildren == null) {
			nonVarChildren = new Vector();
			recurseAddNonVarChildren(nonVarChildren);
		}
		return nonVarChildren;
	}
	// where the work is done
	private void recurseAddNonVarChildren(Vector v) {
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
			throw new Exception("TransducerLeftTree "+toString()+" doesn't match tree "+t.toString()+
					": label error, "+label.toString()+" vs. "+t.getLabel().toString());
		if (variable == null && numChildren != t.getNumChildren())
			throw new Exception("TransducerLeftTree "+toString()+" doesn't match tree "+t.toString()+
					": children error, "+numChildren+" vs. "+t.getNumChildren());
		if (end < start)
			throw new Exception("TransducerLeftTree "+toString()+" maps "+t.toString()+" with illegal indices ("+start+", "+end+")");
		mapTree = t;
		mapTreeStart = start;
		mapTreeEnd = end;
		//	Debug.debug(true, "Mapping "+toString()+" to "+t.toString());
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
	
	// return a Tree by replacing variables with designated values
	public TreeItem getImageTree(HashMap<TransducerLeftTree, Symbol> backmap) {
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
	
	
	// return a tree by replacing variables with VecSymbol equivalent (sort of hacky)
	public TreeItem toTree() {
		// terminal case: replace if variable
		if (numChildren == 0) {
			if (hasVariable()) {
				Vector<Symbol> vec = new Vector<Symbol>();
				vec.add(getVariable());
				if (hasLabel())
					vec.add(getLabel());
				return new TreeItem(SymbolFactory.getVecSymbol(vec));
			}
			else {
				return new TreeItem(getLabel());
			}
		}
		else {
			TreeItem[] kids = new TreeItem[children.length];
			for (int i = 0; i < children.length; i++) 
				kids[i] = children[i].toTree();
			return new TreeItem(kids.length, getLabel(), kids);
		}
	}
	
	// for debugging. get size traverses!
	public int getSize() {
		int total = 1;
		for (int i = 0; i < numChildren; i++)
			total += children[i].getSize();
		return total;
	}
	
	// remove lookahead symbols and capture that fact
	// descend recursively
	// if we hit a combination known to not result in any productions, return false
	public boolean makeNonLookahead(TransducerRuleVariableMap trvm, HashMap<Symbol, HashMap<Symbol, Symbol>> map,
			HashMap<Symbol, HashSet<Symbol>> newadds,
			TransducerRule theRule) {
		if (getNumChildren() > 0) {
			for (int i = 0; i < getNumChildren(); i++) {
				if (!getChild(i).makeNonLookahead(trvm, map, newadds, theRule))
					return false;
			}
		}
		if (hasVariable() && hasLabel()) {
			// non-lookahead not made for deleting rules
			// make sure there's a target before deleting 
			boolean foundRHS = false;
			for (TransducerRightSide trs : trvm.getRHS(this)) {
				foundRHS = true;
				Symbol staterepl = null;
				if (!map.containsKey(trs.getState())) {
					map.put(trs.getState(), new HashMap<Symbol, Symbol>());
					newadds.put(trs.getState(), new HashSet<Symbol>());
				}
				if (!map.get(trs.getState()).containsKey(getLabel())) {
					staterepl = SymbolFactory.getStateSymbol();
//					if (theRule.parent instanceof StringTransducerRuleSet)
//						((StringTransducerRuleSet)theRule.parent).addToI2S(staterepl);
					
					map.get(trs.getState()).put(getLabel(), staterepl);
					if (!newadds.containsKey(trs.getState()))
						newadds.put(trs.getState(), new HashSet<Symbol>());
					newadds.get(trs.getState()).add(getLabel());
				}
				else {
					staterepl = map.get(trs.getState()).get(getLabel());
					
				}
				if (staterepl == Symbol.getStar())
					return false;
				trs.state = staterepl;
			}
			if (foundRHS) {
				label = null;
				
			}
		}
		// are we still lookahead? either check self or all children
		// benefit of the doubt
		isLookahead = false;
		if (getNumChildren() > 0) {
			for (int i = 0; i < getNumChildren(); i++) {
				if (getChild(i).isLookahead()) {
					isLookahead = true;
					break;
				}
			}
		}
		else {
			if (hasVariable() && hasLabel())
				isLookahead = true;
		}
		return true;
	}
}
