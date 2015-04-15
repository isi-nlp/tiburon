package edu.isi.tiburon;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tree implementation of Item.

 */
public class TreeItem extends Item{

	// memoized tree set
	private TreeItem treeLeaves[] = null;

	// memoized leaf set
	private Symbol leaves[] = null;

	// in normal form?
	private boolean decideNorm;
	private boolean isNorm;

	// does this tree represent a state (used in transducer composition)?
	private boolean isTransducerState=false;
	// if it does, there's a hidden symbol representing variable that doesn't come up in printing
	private Symbol hiddenVariable = null;
	public Symbol getHiddenVariable() { return hiddenVariable; }

	// empty tree
	/** Creates a "dummy" tree with no label or children
	 */
	public TreeItem() {
		super();
		decideNorm = true;
		isNorm = true;

	}

	/** Creates a nullary tree

	@param term the label of the tree
	 */
	public TreeItem(Symbol term) {
		super(term);
		decideNorm = true;
		isNorm = true;
	}

	/** Creates a "special" state/var tree for transducer composition

	@param term the label of the tree
	@param var the hidden var
	 */
	public TreeItem(Symbol term, Symbol var) {

		truncated = false;
		numChildren = 0;
		numNodes = 1;
		// weight is meaningless in the context of this construction
		weight = -1;
		children = null;
		//memoized_leaves = null;
		label = term;
		decideNorm = true;
		isNorm = true;
		isEmptyString = false;
		hiddenVariable = var;
		isTransducerState = true;
	}
	/** Creates a weighted nullary tree

	@param term the label of the tree
	@param w the weight of the tree
	 */
	public TreeItem(Symbol term, double w) {
		super(term, w);
		decideNorm = true;
		isNorm = true;
	}


	/** Creates a item from a file 

     @param file string location of the file
	 */
	public TreeItem(String file) throws DataFormatException, FileNotFoundException, IOException {
		super(file);
	}
	/** Creates a item from a file 

    @param file string location of the file
    @param encoding the character set the file was written in
	 */
	public TreeItem(String file, String encoding) throws DataFormatException, FileNotFoundException, IOException {
		super(file, encoding);
	}

	// read-from-file constructor.
	/** Creates a tree from a buffered reader 

     @param br Reader object holding the tree
	 */
	public TreeItem(BufferedReader br) throws DataFormatException, IOException {
		super(br);
	}

	/** Creates a tree from an stream tokenizer. 
	Not really externally useful. constructor version
	Whatever passes the stream tokenizer should set it up for comments
     @param st stream tokenizer  holding the tree
	 */
	public TreeItem(StreamTokenizer st) throws DataFormatException, IOException {
		createItem(st);
	}



	/** Creates a tree from an stream tokenizer. 
	Not really externally useful
     @param st stream tokenizer  holding the tree
	 */
	void createItem(StreamTokenizer st) throws DataFormatException, IOException {
		boolean debug = false;
		boolean inComment = false;
		StringBuffer treebuf = new StringBuffer();
		while (true) {
			switch (st.nextToken()) {
			case StreamTokenizer.TT_WORD:
				// inside a comment. Ignore it.
				if (inComment)
					break;
				// reached a valid piece. add it.
				if (debug) Debug.debug(debug, "TreeItem: About to add token "+st.sval);
				treebuf.append(st.sval);
				break;
			case '"':
				// inside a comment. Ignore it.
				if (inComment)
					break;
				// reached a valid piece. add it.
				// check for length 0 and triple quote
				if (st.sval.length() == 0) {
					st.ordinaryChar('"');
					if (st.nextToken() == '"') {
						if (debug) Debug.debug(debug, "Found triple quote");
						treebuf.append("\"\"\"");
					}
					else {
						st.pushBack();
						if (debug) Debug.debug(debug, "Found empty double quote");
						treebuf.append("\"\"");
					}
					st.quoteChar('"');
				}
				else {
					if (debug) Debug.debug(debug, "TreeItem: About to add quoted token "+st.sval);
					treebuf.append("\""+st.sval+"\"");
				}
				break;
			case '@':
			case '#':
				if (debug) Debug.debug(debug, "TreeItem: pushing symbol back");
				st.pushBack();
			case StreamTokenizer.TT_EOL:
			case StreamTokenizer.TT_EOF:

				// in a newline. Reset any inComment aspect
				inComment = false;
				if (treebuf.length() > 0) {
					if (debug) Debug.debug(debug, "TreeItem: About to process item "+treebuf.toString());
					TreeItem t = new TreeItem(treebuf);
					children = t.children;
					numChildren = t.numChildren;
					numNodes = t.numNodes;
					truncated = t.truncated;
					label = t.label;
					weight = t.weight;
					isEmptyString = false;
					return;
				}
				else {
					if (debug) Debug.debug(debug, "Skipping empty line");
					return;
				}
				// can't reach here, so no break
			case COMMENT:
				// in a comment. Set inComment flag
				inComment = true;
				break;
			default:
				throw new DataFormatException(" Expected word, but read "+st.ttype+" which is "+(char)st.ttype);

			}
		}
	}
	/** Creates a weighted tree from subtrees and a parent.

     @param n number of children in x. This should match, but there's no check.
     @param l parent label
     @param x child trees. there should be n of these
     @param semiring the appropriate semiring this tree's weight is understood in
	 */
	public TreeItem(int n, Symbol l, TreeItem[] x, Semiring semiring) {

		truncated = false;
		numChildren = n;
		label = l;
		weight = semiring.ONE();
		children = new TreeItem[n];
		//	Debug.debug(true, "New TreeItem Construction: current weight is "+weight);
		//	memoized_leaves = null;
		for (int i = 0; i < numChildren; i++) {
			//  Debug.debug(true, "New TreeItem Construction: weight of child "+i+" is "+x[i].weight);
			children[i] = x[i];
			weight = semiring.times(weight, x[i].weight);
			//	    Debug.debug(true, "New TreeItem Construction: new weight of tree  is "+weight);
		}
		decideNorm = false;
		isNorm = isNormal();
		decideNorm = true;
		isEmptyString = false;
		setNumNodes();
	}

	/** Creates an  unweighted tree from subtrees and a parent.

     @param n number of children in x. This should match, but there's no check.
     @param l parent label
     @param x child trees. there should be n of these
	 */
	public TreeItem(int n, Symbol l, TreeItem[] x) {

		truncated = false;
		numChildren = n;
		label = l;
		children = new TreeItem[n];
		for (int i = 0; i < numChildren; i++) {
			children[i] = x[i];
		}
		decideNorm = false;
		isNorm = isNormal();
		decideNorm = true;
		isEmptyString = false;
		setNumNodes();
	}
	
	
	// build normal form: 
	public TreeItem(Symbol l, Vector<Symbol> c) {
		truncated = false;
		numChildren = c.size();
		label = l;
		children = new TreeItem[numChildren];
		for (int i = 0; i < numChildren; i++)
			children[i] = new TreeItem(c.get(i));
		decideNorm = true;
		isNorm = true;
		isEmptyString = false;
		setNumNodes();
	}
	
	// deep copy constructor
	/** Deep copy constructor

	@param a the tree to copy
	 */
	public TreeItem(TreeItem a) {

		truncated = false;
		label = a.label;
		weight = a.weight;
		numChildren = a.numChildren;
		numNodes = a.numNodes;
		children = new TreeItem[numChildren];
		for (int i = 0; i < numChildren; i++) {
			children[i] = new TreeItem((TreeItem)a.children[i]);
		}
		decideNorm = false;
		isNorm = isNormal();
		decideNorm = true;
		isEmptyString = a.isEmptyString;
		isTransducerState = ((TreeItem)a).isTransducerState;
	}



	// convenience method to set the stream tokenizer to stop on all non-whitespace
	private static void setTokenizerNoWhitespace(StreamTokenizer st) {
		st.resetSyntax();
		st.whitespaceChars(0, 32);
	}

	// same as the code above, but with a stringbuffer, and the input buffer is actually modified
	/** Creates a tree from its string representation

     @param text the string representation of the tree
	 */
	public TreeItem(StringBuffer text) throws DataFormatException, IOException {
		this(text, 0);
		StreamTokenizer st = new StreamTokenizer(new StringReader(text.toString()));
		setTokenizerNoWhitespace(st);
		while (st.nextToken() != StreamTokenizer.TT_EOF)
			throw new DataFormatException(" Unexpected token "+text.toString());
		setNumNodes();
	}

	// node pattern = symbol followed by open paren
	private static Pattern nodePat = Pattern.compile("(\\s*([^\\s\\(\\)\"]+|\"\"\"|\"[^\"]+\")\\s*\\()");
	// leaf pattern = symbol with no open paren
	private static Pattern leafPat = Pattern.compile("(\\s*([^\\s\\(\\)\"]+|\"\"\"|\"[^\"]+\"))");
	// end-of-tree pattern = spaces and right paren
	private static Pattern endPat = Pattern.compile("(\\s*\\))");
	//, optionally followed by weight
	private static Pattern weightPat = Pattern.compile("(\\s*#\\s*(\\S+))");

	// "inner" stringbuffer constructor. allows the outer one to check for cruft
	private TreeItem(StringBuffer text, int level) throws DataFormatException, IOException {
		boolean debug = false;
		// is input the head of a tree?
		Matcher nodeMatch = nodePat.matcher(text.toString());
		if (nodeMatch.lookingAt()) {
			if (debug) Debug.debug(debug, level, "matched "+text+" against node pattern");
			String labStr = nodeMatch.group(2);
			int totalLength = nodeMatch.group(1).length();
			if (debug) Debug.debug(debug, level, "Consuming ["+nodeMatch.group(1)+"]");
			if (debug) Debug.debug(debug, level, "label set to "+labStr);
			label = SymbolFactory.getSymbol(labStr);
			text.delete(0, totalLength);
			// temporary holding place for children
			Vector<TreeItem> kids = new Vector<TreeItem>();
			// add children until we reach the end pattern
			Matcher endMatch = endPat.matcher(text.toString());
			while (!endMatch.lookingAt()) {
				TreeItem kid = new TreeItem(text, level+1);
				if (debug) Debug.debug(debug, level, "adding child "+kid);
				kids.add(kid);
				endMatch = endPat.matcher(text.toString());
			}
			totalLength = endMatch.group(1).length();
			if (debug) Debug.debug(debug, level, "Consuming end group ["+endMatch.group(1)+"]");
			text.delete(0, totalLength);
			Matcher weightMatch = weightPat.matcher(text.toString());
			if (weightMatch.matches()) {
				if (weightMatch.group(2) != null) {
					if (debug) Debug.debug(debug, "Reading weight "+weightMatch.group(1));
					weight = Double.parseDouble(weightMatch.group(2));
				}
				text.delete(0, weightMatch.group(1).length());
			}
			children = new TreeItem[kids.size()];
			numChildren = children.length;
			kids.toArray(children);
			return;
		}
		// is input a leaf?
		Matcher leafMatch = leafPat.matcher(text.toString());
		if (leafMatch.lookingAt()) {
			if (debug) Debug.debug(debug, level, "matched "+text+" against label pattern");
			String labStr = leafMatch.group(2);
			int totalLength = leafMatch.group(1).length();
			if (debug) Debug.debug(debug, level, "Consuming ["+leafMatch.group(1)+"]");
			label = SymbolFactory.getSymbol(labStr);
			if (debug) Debug.debug(debug, level,  "TLT label set to "+labStr);
			text.delete(0, totalLength);
			return;
		}
		throw new DataFormatException("Couldn't match any pattern to "+text);
	}
	
	// convenience method to set the stream tokenizer the way we want it
/*	private static void setTokenizerNormal(StreamTokenizer st) {
		st.resetSyntax();
		st.wordChars('\u0000', '\u00FF');

		// whitespace
		st.whitespaceChars(0, 32);
		//	st.ordinaryChar(' ');
		//	st.ordinaryChar('\n');
		//	st.ordinaryChar('\t');

		// variable label
		// not in rtg mode
		//	st.ordinaryChar(':');
		// state label
		//st.ordinaryChar('.');
		// part of arrow
		//	st.ordinaryChar('-');
		// conclusion of arrow
		//	st.ordinaryChar('>');
		// reserved?
		st.ordinaryChar('*');
		st.ordinaryChar('{');
		st.ordinaryChar('}');	    
		// escape?
		st.ordinaryChar('\\');

		// trees
		st.ordinaryChar('(');
		st.ordinaryChar(')');

		st.commentChar(COMMENT);
		st.quoteChar('"');
	}*/
	// convenience method to set the stream tokenizer to slurp
/*	private static void setTokenizerSlurp(StreamTokenizer st) {
		st.resetSyntax();
		st.wordChars('\u0000', '\u00FF');
	}
*/
	// "inner" stringbuffer constructor. allows the outer one to check for cruft
/*	    private TreeItem(StringBuffer text, int level) throws DataFormatException, IOException {

	boolean debug = true;
	if (debug) Debug.debug(debug, level, "Tree: Input is "+text.toString());
	StringReader sr = new StringReader(text.toString());
	StreamTokenizer st = new StreamTokenizer(sr);
	// tree is label and children. children are contained within parens
	// so, separate into label, open paren, remainder. From remainder, add
	// children one by one. If an open paren is reached, create a child from
	// that point and clip that text away. If a close paren is reached, the rul
	setTokenizerNormal(st);

	boolean seenLabel = false;
	// temporary holding place for children
	Vector kids = new Vector();
	boolean seenOpen = false;
	while (st.nextToken() != StreamTokenizer.TT_EOF) {
	    if (!seenLabel) {
		//		    Debug.debug(true, "Type is "+(char)st.ttype);
		switch (st.ttype) {
		case '"':
		    // """ case or possibly ""
		    if (st.sval.length() == 0) {
			st.ordinaryChar('"');
			if (st.nextToken() == '"') {
			    if (debug) Debug.debug(debug, "Found triple quote");
			    label = SymbolFactory.getSymbol("\"\"\"");
			}
			else {
			    label = SymbolFactory.getSymbol("\"\"");
			    st.pushBack();
			}
			st.quoteChar('"');
		    }
		    else
			label = SymbolFactory.getSymbol('"'+st.sval+'"');
		    break;
		case StreamTokenizer.TT_WORD:
		    label = SymbolFactory.getSymbol(st.sval);
		    break;
		case StreamTokenizer.TT_NUMBER:
		    label = SymbolFactory.getSymbol(""+st.nval);
		    break;
		default:
		    throw new DataFormatException(" expected word, quoted word, or number for label, read "+(char)st.ttype+" in "+text.toString());
		}

		if (debug) Debug.debug(debug, level, "Tree: Read "+label+" as label");
		seenLabel = true;
		if (st.nextToken() == StreamTokenizer.TT_EOF) {
		    numChildren = 0;
		    // delete the entire text buffer to keep things even.
		    if (debug) Debug.debug(debug, level, "Tree: deleting text, which is "+text.length()+" characters");
		    text.delete(0, text.length());
		    children = null;
		    break;
		}
		if (st.ttype != '(') {
		    throw new DataFormatException(" expected open paren, read "+(char)st.ttype+ " in "+text.toString());

		}
		seenOpen = true;
		continue;
	    }
	    StringBuffer childBuf = new StringBuffer();
	    // should be either a close paren or a word
	    switch (st.ttype) {
	    case '"':
		// """ case or possibly ""
		if (st.sval.length() == 0) {
		    st.ordinaryChar('"');
		    if (st.nextToken() == '"') {
			if (debug) Debug.debug(debug, "Found triple quote");
			childBuf.append("\"\"\"");
		    }
		    else {
			childBuf.append("\"\"");
			st.pushBack();
		    }
		    st.quoteChar('"');
		}
		// normal case
		else
		    childBuf.append("\"\"");
	    case StreamTokenizer.TT_WORD:
		if (st.sval != null && st.sval.length() > 0)
		    childBuf.insert(Math.min(1, childBuf.length()), st.sval);
		Symbol child = SymbolFactory.getSymbol(childBuf.toString());
		if (debug) Debug.debug(debug, level, "Read "+child+" as child");
		// child should be a word. 
		// might be triggered with a quote char

		switch (st.nextToken()) {
		case '"':
		case StreamTokenizer.TT_WORD:

		    // If next child is also a word, this is a 
		    // singleton. add it, push back, and continue
		    if (debug) Debug.debug(debug, level, "Next token after "+child+" is a word, so pushing back and continuing");
		    TreeItem v = new TreeItem(child);
		    kids.add(v);
		    st.pushBack();
		    break;


		case ')':
		    // if next child is a close paren, do the same thing
		    // as for a word, but also do close-out stuff
		    // singleton. add it, push back, and continue
		    if (debug) Debug.debug(debug, level, "Next token after "+child+" is a close paren, so closing out tree and continuing");
		    TreeItem t = new TreeItem(child);
		    kids.add(t);
		    numChildren = kids.size();
		    Object [] kidsarr = kids.toArray();
		    children = new TreeItem[kidsarr.length];
		    for (int i = 0; i < kidsarr.length; i++)
			children[i] = (TreeItem)kidsarr[i];
		    setTokenizerSlurp(st);
		    // delete the entire text stringbuffer
		    text.delete(0, text.length());
		    // and replace it with the remainder
		    if (st.nextToken() != StreamTokenizer.TT_EOF)
			text.append(st.sval);
		    if (debug) Debug.debug(debug, level, "New text is "+text.toString());

		    setTokenizerNormal(st);
		    return;
		case '(':
		    //		    Debug.debug(true, "Next token after "+child+" is an open paren, so recursing");
		    // this is a tree. it should be handled recursively
		    // unset the tokenizer to slurp everything		
		    setTokenizerSlurp(st);
		    // patch it to the other two tokens into a stringbuffer and process
		    StringBuffer sb = new StringBuffer(child.toString());
		    sb.append((char)st.ttype);
		    if (st.nextToken() == StreamTokenizer.TT_EOF) {
			Debug.debug(true, "Read "+st.sval);
			throw new DataFormatException(" reached EOF after open paren in "+text.toString());

		    }
		    sb.append(st.sval);
		    if (debug) Debug.debug(debug, level, "Before extracting tree we have "+sb.toString());
		    TreeItem u = new TreeItem(sb, level+1);
		    if (debug) Debug.debug(debug, level, "Tree just added is "+u.toString());
		    if (debug) Debug.debug(debug, level, "After extracting tree we have "+sb.toString());
		    kids.add(u);
		    // reset the streams, etc.
		    sr.close();
		    sr = new StringReader(sb.toString());
		    st = new StreamTokenizer(sr);
		    setTokenizerNormal(st);
		    break;
		default:
		    throw new DataFormatException(" type is "+st.ttype+" which is "+(char)st.ttype+" when reading "+text.toString());

		}
		break;
	    case ')':
		// end the tree without adding a new child
		numChildren = kids.size();
		Object [] kidsarr = kids.toArray();
		children = new Item[kidsarr.length];
		for (int i = 0; i < kidsarr.length; i++)
		    children[i] = (Item)kidsarr[i];
		setTokenizerSlurp(st);
		// delete the entire text stringbuffer
		text.delete(0, text.length());
		// and replace it with the remainder
		if (st.nextToken() != StreamTokenizer.TT_EOF)
		    text.append(st.sval);
		if (debug) Debug.debug(debug, level, "New text is "+text.toString());

		setTokenizerNormal(st);
		return;
	    default:
		throw new DataFormatException(" expected word or close paren, got type "+(char)st.ttype+", ["+
					      st.sval+"]"+" when reading "+text.toString());

	    }
	}
	// shouldn't be here if open paren was seen
	if (seenOpen)
	    throw new DataFormatException(" mismatched parentheses when reading "+text.toString()+" after having read "+toString());
	decideNorm = false;
	isNorm = isNormal();
	decideNorm = true;
	isEmptyString = false;
    }
*/	 
	/**
       Construct TreeItem from StringItem and information needed to make
       the viable conversion
       @param string The StringItem to convert
       @param parent The Symbol to place on top of the elements in the string
       @param map    conversions of symbols to state forms
	 */
	public TreeItem(StringItem string, Symbol parent, HashMap map) throws ImproperConversionException {
		boolean debug = false;
		// if the string is epsilon, no good
		if (string.isEmptyString())
			throw new ImproperConversionException("Cannot convert epsilon string "+this.toString()+" to tree");
		Symbol [] currleaves = string.getLeaves();
		if (debug) Debug.debug(debug, "Building tree with parent "+parent.toString()+" and "+currleaves.length+" leaves that make up "+string.toString());
		children = new Item[currleaves.length];
		numChildren = currleaves.length;
		truncated = false;
		isEmptyString = false;
		weight = string.getWeight();
		// all children are singletons, so it's normal form
		decideNorm = true;
		isNorm = true;
		label = parent;
		// make singleton TreeItems of the string symbols. Use the map if needed
		for (int i = 0; i < numChildren; i++) {
			if (debug) Debug.debug(debug, "Child "+i+" is "+currleaves[i].toString());
			if (map.containsKey(currleaves[i]))
				children[i] = new TreeItem((Symbol)map.get(currleaves[i]));
			else
				children[i] = new TreeItem(currleaves[i]);
		}
	}


	/**
       Construct TreeItem from TransducerLeftTree and trvm, as part of a domain cast
       @param tlt The TransducerLeftTree to convert
       @param trvm The TransducerRuleVariableMap for casting states
       @param map, A mapping between sets of states and canonical symbols
	 */
	public TreeItem(TransducerLeftTree tlt, 
			TransducerRuleVariableMap trvm, 
			HashMap<HashSet<Symbol>, Symbol> map) throws ImproperConversionException {
		boolean debug = false;
		// if we're a variable, map and return
		// TODO: what about variable+label cases?
		if (tlt.hasVariable()) {
			// build the set of states so we know if we have to map
			HashSet<Symbol> stvals = new HashSet<Symbol>();
			for (TransducerRightSide trs : trvm.getRHS(tlt)) {
				if (!trs.hasState())
					throw new ImproperConversionException("TRS "+trs.toString()+"; mapped from "+tlt.toString()+" has no state!");
				stvals.add(trs.getState());
			}
			if (stvals.size() == 1) {
				Symbol[] ar =new Symbol[1];
				stvals.toArray(ar);
				label = ar[0];
				if (debug) Debug.debug(debug, "Turning "+tlt.getLabel()+" into "+label);
			}
			else {
				if (!map.containsKey(stvals))
					throw new ImproperConversionException("No mapped value for set of states "+stvals);
				label = map.get(stvals);
				if (debug) Debug.debug(debug, "Turning "+tlt.getLabel()+" into "+label);
			}
			if (tlt.getNumChildren() > 0)
				throw new ImproperConversionException("Trying to convert tlt "+tlt.toString()+" with non-terminal variables");
			children = null;
			numChildren = 0;
			numNodes = 1;
			decideNorm = true;
			isNorm = true;
			isEmptyString = false;
		}
		else {
			label = tlt.getLabel();
			if (debug) Debug.debug(debug, "Copying "+tlt.getLabel()+" into tree and descending");	
			numChildren = tlt.getNumChildren();
			children = new Item[numChildren];
			for (int i = 0; i < numChildren; i++)
				children[i] = new TreeItem(tlt.getChild(i), trvm, map);
		}
	}

	/**
       Construct TreeItem from TransducerLeftTree and state set vector, as part of a domain cast
       @param tlt The TransducerLeftTree to convert
       @param states The Vector of states to end up in
       @param map, A mapping between sets of states and canonical symbols
	 */
	public TreeItem(TransducerLeftTree tlt, 
			Vector<HashSet<Symbol>> states, 
			HashMap<HashSet<Symbol>, Symbol> map) throws ImproperConversionException {
		boolean debug = false;
		if (debug) Debug.debug(debug, "Building tree item from "+tlt+" with remaining state vector "+states);
		// if we're a variable, map and return
		// TODO: what about variable+label cases?
		if (tlt.hasVariable()) {
			HashSet<Symbol> stvals = states.remove(0);
			if (stvals.size() == 1) {
				Symbol[] ar =new Symbol[1];
				stvals.toArray(ar);
				label = ar[0];
				if (debug) Debug.debug(debug, "Turning "+tlt.getLabel()+" into "+label);
			}
			else {
				if (!map.containsKey(stvals))
					throw new ImproperConversionException("No mapped value for set of states "+stvals);
				label = map.get(stvals);
				if (debug) Debug.debug(debug, "Turning "+tlt.getLabel()+" into "+label);
			}
			if (tlt.getNumChildren() > 0)
				throw new ImproperConversionException("Trying to convert tlt "+tlt.toString()+" with non-terminal variables");
			children = null;
			numChildren = 0;
			numNodes = 1;
			decideNorm = true;
			isNorm = true;
			isEmptyString = false;
		}
		else {
			label = tlt.getLabel();
			if (debug) Debug.debug(debug, "Copying "+tlt.getLabel()+" into tree and descending");	
			numChildren = tlt.getNumChildren();
			children = new Item[numChildren];
			for (int i = 0; i < numChildren; i++)
				children[i] = new TreeItem(tlt.getChild(i), states, map);
		}
	}

	/**
	 * Construct TreeItem for RTGRule lhs as part of backwards application onto xRs
	 * @param tlt the transducer rule lhs which will become the rtg rule rhs
	 * @param trvm the map between left side states and right side states
	 * @param states map between right side states and new symbols that the new tree will contain
	 * @throws ImproperConversionException if the wrong number of states is in states or if other 
	 * improper conditions are found.
	 */
	public TreeItem(TransducerLeftTree tlt, TransducerRuleVariableMap trvm, Hashtable<TransducerRightSide, Symbol> states) throws ImproperConversionException {
		boolean debug = false;
		if (debug) Debug.debug(debug, "Building tree item from "+tlt+" with state map "+states);
		// if we're a variable, map and return
		// TODO: what about variable+label cases?
		if (tlt.hasVariable()) {
			label = states.get((TransducerRightSide)trvm.getRHS(tlt).toArray()[0]);
//			if (states.isEmpty())
//				throw new ImproperConversionException("Ran out of states before swapping for "+tlt);
//			label = states.remove(0);
			if (debug) Debug.debug(debug, "Turning "+tlt.getLabel()+" into "+label);
			children = null;
			numChildren = 0;
			numNodes = 1;
			decideNorm = true;
			isNorm = true;
			isEmptyString = false;
		}
		else {
			label = tlt.getLabel();
			if (debug) Debug.debug(debug, "Copying "+tlt.getLabel()+" into tree and descending");	
			numChildren = tlt.getNumChildren();
			children = new Item[numChildren];
			for (int i = 0; i < numChildren; i++)
				children[i] = new TreeItem(tlt.getChild(i), trvm, states);
		}
	}
	
	/**
	 * Construct TreeItem for RTGRule lhs as part of backwards application onto xRs
	 * version with integrated search already has states in right order
	 * @param tlt the transducer rule lhs which will become the rtg rule rhs
	 * @param states vector of states to insert in order
	 * @throws ImproperConversionException if the wrong number of states is in states or if other 
	 * improper conditions are found.
	 */
	public TreeItem(TransducerLeftTree tlt, Vector<Symbol> states) throws ImproperConversionException {
		boolean debug = false;
		if (debug) Debug.debug(debug, "Building tree item from "+tlt+" with state vec "+states);
		// if we're a variable, map and return
		// TODO: what about variable+label cases?
		if (tlt.hasVariable()) {
			if (states.isEmpty())
			throw new ImproperConversionException("Ran out of states before swapping for "+tlt);
			label = states.remove(0);
			if (debug) Debug.debug(debug, "Turning "+tlt+" into "+label);
			children = null;
			numChildren = 0;
			numNodes = 1;
			decideNorm = true;
			isNorm = true;
			isEmptyString = false;
		}
		else {
			label = tlt.getLabel();
			if (debug) Debug.debug(debug, "Copying "+tlt.getLabel()+" into tree and descending");	
			numChildren = tlt.getNumChildren();
			children = new Item[numChildren];
			for (int i = 0; i < numChildren; i++)
				children[i] = new TreeItem(tlt.getChild(i), states);
		}
	}


	
	
	/**
       Construct TreeItem from TransducerRightTree as part of a domain cast
       @param trt The TransducerRightTree to convert
	 */
	public TreeItem(TransducerRightTree trt) throws ImproperConversionException {
		boolean debug = false;
		if (debug) Debug.debug(debug, "Building tree item from "+trt);
		// if we're a state, add it as symbol and return
		if (trt.hasState()) {
			label = trt.getState();
			if (trt.getNumChildren() > 0)
				throw new ImproperConversionException("Trying to convert trt "+trt+" with non-terminal variables");
			children = null;
			numChildren = 0;
			numNodes = 1;
			decideNorm = true;
			isNorm = true;
			isEmptyString = false;
		}
		else {
			label = trt.getLabel();
			if (debug) Debug.debug(debug, "Copying "+trt.getLabel()+" into tree and descending");	
			numChildren = trt.getNumChildren();
			children = new Item[numChildren];
			for (int i = 0; i < numChildren; i++)
				children[i] = new TreeItem(trt.getChild(i));
		}
	}

	
	/**
	 * Construct TreeItem from TransducerRightTree and passed mapping to states, for forming into PairSymbols
	 * Used by cascade training construction
	 */
	public TreeItem(TransducerRightTree trt, HashMap<TransducerRightSide, Symbol> map) {
		// if we're in the map, build the new state and add
		if (map.containsKey(trt)) {
			label = PairSymbol.get(map.get(trt), trt.getState());
			children = null;
			numChildren = 0;
			numNodes = 1;
			decideNorm = true;
			isNorm = true;
			isEmptyString = false;
		}
		else {
			label = trt.getLabel();
			numChildren = trt.getNumChildren();
			children = new Item[numChildren];
			for (int i = 0; i < numChildren; i++)
				children[i] = new TreeItem(trt.getChild(i), map);
		}
	}
	
	
	/** 
	Get a particular child

	@param i which child to return (no check for array index!)
	@return the chosen child tree
	 */
	public TreeItem getChild(int i) { return (TreeItem)children[i]; }

	// check if this tree is in normal form, or if it's been decided to be in normal form
	/**
       Is the tree in CNF?

       @return true if the Tree is in CNF
	 */
	public boolean isNormal() {
		if (decideNorm)
			return isNorm;
		// normal form means all children of tree are singletons
		isNorm = true;
		decideNorm = false;
		for (int i = 0; i < numChildren; i++)
			if (children[i].numChildren > 0) {
				isNorm = false;
				break;
			}
		return isNorm;
	}
	// does this tree represent a transducer state (used in transducer composition)?
	/** 
	does this tree represent a transducer state (used in transducer composition)?
	@return true if the Tree represents a transducer state
	 */
	public boolean isTransducerState() {
		return isTransducerState;
	}

	// dump memoization
	/**
       Add a Tree as the rightmost child of this tree

       @param t the subtree to be added
	 */
	public void addChild(TreeItem t) {
		leaves = null;
		treeLeaves = null;
		decideNorm = false;
		Item newKids[] = new Item[numChildren+1];
		for (int i = 0; i < numChildren; i++)
			newKids[i] = children[i];
		newKids[numChildren] = t;
		children = newKids;
		numChildren++;
		setHashCode();
		setNumNodes();
	}

	// mirror image of a stored into this tree
	// since child image changes, dump any memoized content

	/** Non-useful method. Does deep copying for the constructor

	@param a the tree to copy
	 */
	public void deepCopy(TreeItem a) {
		leaves = null;
		treeLeaves = null;
		label = a.label;
		//	weight = a.weight;
		numChildren = a.numChildren;
		children = new TreeItem[numChildren];
		for (int i = 0; i < numChildren; i++) {
			children[i] = new TreeItem((TreeItem)a.children[i]);
		}
		isEmptyString = a.isEmptyString;
		setHashCode();
		setNumNodes();
	}

	// so I can do KBest with rhs (string assumed) side of Derivation Rules
	// assume single-height for ease
	// designed for kenji model -- not terribly general
	// if rhs has a label, it's used
	// if rhs label, it's EPS
	// X goes on if there is more than one rhs child or if lhs has a label
	public void deepCopyDerivRuleRHS(DerivationRule r) throws UnusualConditionException {
		leaves = null;
		treeLeaves = null;
		// want to avoid putting the Vs in here...
		if (r.isVirtual()) {
			label = SymbolFactory.getSymbol("V");
			//	throw new UnusualConditionException("Shouldn't do RHS-deep copy of virtual rule");
		}
		else {
			if (r.getLabel() == null)
				throw new UnusualConditionException("Shouldn't do RHS-deep copy of rule with no transducer rule");
			if (r.getLabel() instanceof TreeTransducerRule)
				throw new UnusualConditionException("Shouldn't do RHS-deep copy of rule with tree transducer rule");
			StringTransducerRule tr = (StringTransducerRule)r.getLabel();
//			Debug.prettyDebug("Using "+tr);
			if (tr.getRHS().hasLabel()) {
//				Debug.prettyDebug("Using own label");
				label = tr.getRHS().getLabel();
				if (label.equals(Symbol.getEpsilon()))
					label = SymbolFactory.getSymbol("EPS");
			}
			else if (tr.getRHS().getItemLeaves().size() > 1 || (!tr.getLHS().hasVariable())) {
	//			Debug.prettyDebug("Using X label");
				label = SymbolFactory.getSymbol("X");
			}
			else {
		//		Debug.prettyDebug("Using V label");
				label = SymbolFactory.getSymbol("V");
			}
		}
		numChildren = r.getNumChildren();
		children = new TreeItem[numChildren];
		int[] kids = r.getRHS();
		for (int i = 0; i < numChildren; i++) {
			children[i] = new TreeItem(SymbolFactory.getSymbol(""+kids[i]));
		}
		isEmptyString = false;
		setHashCode();
		setNumNodes();
	}
	
	// so I can do KBest with Derivation Rules
	public void deepCopyDerivRule(DerivationRule r) {
		boolean debug = false;
		leaves = null;
		treeLeaves = null;
		if (r.isVirtual())
			label = SymbolFactory.getSymbol("V");
		else {
			label = SymbolFactory.getSymbol("R"+r.getRuleIndex());
			if (debug) Debug.debug(debug, "Label is "+label);
		}
		numChildren = r.getNumChildren();
		children = new TreeItem[numChildren];
		int[] kids = r.getRHS();
		for (int i = 0; i < numChildren; i++) {
			children[i] = new TreeItem(SymbolFactory.getSymbol(""+kids[i]));
		}
		isEmptyString = false;
		setHashCode();
		setNumNodes();
	}

	
	
	
	// so I can do KBest with Cascadable Derivation Rules
	public void deepCopyCascadeDerivRule(CascadeDerivationRule r) {
		boolean debug = false;
		leaves = null;
		treeLeaves = null;
		if (r.isVirtual())
			label = SymbolFactory.getSymbol("V");
		else {
			label = SymbolFactory.getSymbol("R"+r.getRuleIndex());
			if (debug) Debug.debug(debug, "Label is "+label);
		}
		numChildren = r.getNumChildren();
		children = new TreeItem[numChildren];
		int[] kids = r.getRHS();
		for (int i = 0; i < numChildren; i++) {
			children[i] = new TreeItem(SymbolFactory.getSymbol(""+kids[i]));
		}
		isEmptyString = false;
		setHashCode();
		setNumNodes();
	}

	// get leaves: recursive way of generating the yield language.
	// note that this method is memoized
	/** Get all terminal leaves as symbols. This method is memoized.

	@return an array of Symbols for each tree leaf
	 */
	public Symbol[] getLeaves() {
		if (leaves == null) {
			if (numChildren == 0) {
				leaves = new Symbol[] {label};
				return leaves;
			}
			Symbol[][] leafSets = new Symbol[numChildren][];
			int numItems = 0;
			// gather the arrays
			for (int i = 0; i < numChildren; i++) {
				leafSets[i] = ((TreeItem)children[i]).getLeaves();
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

	/** Check if this item, which is assumed to be a rule RHS, matches a passed in item
	RuleSet used to check state-ness of leaves
	@param i The item to check against this item
	@param rs The RuleSet which contains state info
	@return true if the items match
	 */
	public boolean isItemMatch(Item i, RuleSet rs) {
		// if this tree is headed by a state, it's all good
		if (rs.states.contains(label))
			return true;
		if (!i.label.equals(label))
			return false;
		if (i.numChildren != numChildren)
			return false;
		for (int c = 0; c < numChildren; c++) {
			if (!((TreeItem)children[c]).isItemMatch(i.children[c], rs))
				return false;
		}
		return true;
	}


	
	// isTransducerRightTreeMatch: given a right side, is this tree valid?
	// if there is a state, yes (if the transformation matches but that's not part of this)
	// if the label matches and the number of children is identical
	// and for each child, isTransducerRightTreeMatch matches, yes

	// used for backwards application as well as rightward composition, hopefully
	
	public boolean isTransducerRightTreeMatch(TransducerRightTree t) {
		boolean debug = false;
		if (debug) Debug.debug(debug, "Matching "+t+" to "+this);
		// for rightward composition
		if (label instanceof VecSymbol)
			return true;
	
		// for backwards application
		else if (t.hasVariable() && !t.hasLabel())
			return true;
		
		if (!t.getLabel().equals(label)) {
			if (debug) Debug.debug(debug, "RIGHT SIDE Fail: ["+label.toString()+"] not equal to ["+t.getLabel().toString()+"]");
			return false;
		}

		if (t.getNumChildren() != numChildren) {
			if (debug) Debug.debug(debug, "RIGHT SIDE Fail: "+t.getNumChildren()+" in "+t.toString()+" not the same number of children as "+numChildren+" in "+toString());
			return false;
		}
		for (int i = 0; i < numChildren; i++) {
			if (!((TreeItem)children[i]).isTransducerRightTreeMatch(t.getChild(i)))
				return false;
		}
		return true;
	}
	
	// useful as pointer objects, like in stochastic generation
	// note that this method is memoized
	/** Get all terminal leaves as trees.

	@return an array of nullary trees for each tree leaf
	 */
	public Item[] getItemLeaves() {
		if (treeLeaves == null) {
			if (numChildren == 0) {
				treeLeaves =  new TreeItem[] {this};
				return treeLeaves;
			}
			Item[][] leafSets = new Item[numChildren][];
			int numItems = 0;
			// gather the arrays
			for (int i = 0; i < numChildren; i++) {
				leafSets[i] = children[i].getItemLeaves();
				numItems += leafSets[i].length; 
			}
			// compress them into one, then give it to the above
			treeLeaves = new TreeItem[numItems];
			int currItem = 0;
			for (int i = 0; i < numChildren; i++) {
				for (int j = 0; j < leafSets[i].length; j++) {
					treeLeaves[currItem++] = (TreeItem)leafSets[i][j];
				}
			}
		}
		return treeLeaves;
	}

	/** Represent the tree in standard parenthesized notation.
	Tiburon tree representation represents nullary trees by their symbols, and trees
	with higher rank by the symbol and a set of parentheses, with children in the parentheses
	in left-to-right order corresponding to their position. So a tree with a root label A, with
	leaf children B and C, would be represented as A(B C).

	@return the string representation of the tree
	 */
	public String toString() {
		if (label == null && numChildren <= 0)
			return "()";
		StringBuffer ret = new StringBuffer(label.toString());
		if (numChildren > 0) {
			ret.append("("+children[0].toString());
			for (int i = 1; i < numChildren; i++) {
				ret.append(" "+children[i].toString());
			}
			ret.append(")");
		}
		//	else
		//	    ret.append(":"+label.hashCode());
		return ret.toString();
	}

	// to yield gets rid of any intermediary nodes
	/** Represent the tree by its leaf symbols arranged as a string.
	Leaf symbols are taken in a left-to-right manner and concatenated to form the yield string. 
	For example, if this operation were called on the tree A(B C(D E) F(G(H))), the yield returned
	would be "B D E H".

	@return the string representation of the tree yield.
	 */
	public String toYield() {
		if (label == null && numChildren <= 0)
			return "";
		if (isEmptyString)
			return "";
		if (numChildren == 0)
			return label.toString();
		StringBuffer ret = new StringBuffer();
		if (numChildren > 0) {
			for (int i = 0; i < numChildren; i++) {
				ret.append(children[i].toYield());
				if (i < (numChildren-1) && !children[i].isEmptyString)
					ret.append(" ");
			}
		}
		//	else
		//	    ret.append(":"+label.hashCode());
		return ret.toString();
	}


	// clear out all memoized info, including that of children
	public void unmemoize() {
		if (children != null) {	
			for (Item i : children) {
				((TreeItem)i).unmemoize();
			}
		}
		treeLeaves = null;
		leaves = null;
	}
	
	
	// read a list of trees from file
	/** Create a  {@link Vector} contianing a corpus size and reference to the temporary file
	containing objects that may be re-read
	This method is only used in batch mode so far. It has been changed to mimic the form of 
	the training read-in methods, that read in the objects from string data and store them to a 
	temporary file. 

	@param file the File containing the trees
	@param encoding the character set the file was written in
	@return a Vector containing the size in its first location and the temporary reference file in the second.
	 */
	public static Vector readTreeSet(File file, String encoding) throws FileNotFoundException, IOException {
		Vector v = new Vector();
		StreamTokenizer st = new StreamTokenizer(new BufferedReader(new InputStreamReader(new FileInputStream(file), encoding)));

		// mark all characters as normal, then isolate the comment character, turn on line breaks, and make the rest word
		// characters. This should segment the file into processable lines and comment fields which terminate at the end of a line.

		st.resetSyntax();
		//st.whitespaceChars(0, 32);
		st.commentChar(COMMENT);
		st.wordChars(0, COMMENT-1);
		st.wordChars(COMMENT+1, '\u00FF');
		st.whitespaceChars(10, 12);
		st.eolIsSignificant(true);

		int counter = 0;
		// the output file
		File of = null;
		of = File.createTempFile("treeset", "tmp");
		of.deleteOnExit();
		ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(of));

		Date readTime = new Date();
		while (st.ttype != StreamTokenizer.TT_EOF) {
			TreeItem t = null;
			try {
				t = new TreeItem(st);
			}
			catch (DataFormatException e) {
				System.err.println("Error with tree data format: "+e.getMessage());
				System.exit(1);
			}
			if (t.label == null)
				continue;
			oos.writeObject(t);
			oos.reset();
			counter++;
			if (counter % 10000 == 0) {
				Date pause = new Date();
				long lapse = pause.getTime() - readTime.getTime();
				readTime = pause;
				Debug.debug(true, "Read "+counter+" training examples: "+lapse);		    
			}
		}
		oos.close();
		v.add(of);
		v.add(new Integer(counter));
		st = null;
		return v;
	}


	// test code
	/** Test code.
	The results of this call change and are unpredictable. Don't use it.
	 */
	public static void main(String argv[]) {
		try {
			File f = new File(argv[0]);
			Vector v = readTreeSet(f, "utf-8");
			ObjectInputStream ois = new ObjectInputStream(new FileInputStream((File)v.get(0)));
			int size = ((Integer)v.get(1)).intValue();
			Debug.debug(true, "Read "+size+" trees");
			for (int i = 0; i < size; i++) {
				TreeItem t = (TreeItem)ois.readObject();
				Debug.debug(true, t.numNodes()+" nodes: "+t.toString());
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
