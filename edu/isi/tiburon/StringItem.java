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
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** String implementation of Item.

 */
public class StringItem extends Item{

	// TODO: turn this into its equivalent forms
	// memoized tree set
	private StringItem treeLeaves[] = null;


	// memoized leaf set == all words in the string
	private Symbol leaves[] = null;


	// does this string represent a state (used in transducer composition)?
	private boolean isTransducerState=false;
	// if it does, there's a hidden symbol representing variable that doesn't come up in printing
	private Symbol hiddenVariable = null;
	public Symbol getHiddenVariable() { return hiddenVariable; }

	// next terminal, based on a rule set. set as needed
	private StringItem nextTerminal = null;
	private boolean isNextTermSet = false;

	// if memoized, return nextTerm. Otherwise, set and return recursively
	public StringItem getNextTerm(RuleSet rs) {
		if (isNextTermSet)
			return nextTerminal;
		// it's either the actual next stringitem, or that item's nextterm
		StringItem next = getNext();
		// if we're at end, nothing set
		if (next == null) {
			isNextTermSet = true;
		}

		// is next a state? if not, it's the next terminal. if so, its nextTerminal is the next terminal
		else {
			if (!rs.states.contains(next.getLabel())) {
				nextTerminal = next;
				isNextTermSet = true;
			}
			else {
				nextTerminal = next.getNextTerm(rs);
				isNextTermSet = true;
			}
		}
		return nextTerminal;
	}


	// empty string
	/** Creates a "dummy" string with no label or children
	 */
	public StringItem() {
		super();
		label = Symbol.getEpsilon();
		setEmptyString();
		numChildren = 0;
		children = new StringItem[1];
	}

	/** Creates a nullary string

	@param term the label of the string
	 */
	public StringItem(Symbol term) {
		super(term);
		if (term == null) {
			label = Symbol.getEpsilon();
			setEmptyString();
		}
		if (term == Symbol.getEpsilon())
			setEmptyString();
		numChildren = 0;
		children = new StringItem[1];
	}

	/** Creates a "special" state/var string for transducer composition

	@param term the label of the string
	@param var the hidden var
	 */
	public StringItem(Symbol term, Symbol var) {	
		truncated = false;
		numChildren = 0;
		children = new StringItem[1];
		numNodes = 1;
		// weight is meaningless in the context of this construction
		weight = -1;
		//memoized_leaves = null;
		label = term;
		isEmptyString = false;
		hiddenVariable = var;
		isTransducerState = true;
	}
	/** Creates a weighted nullary string

	@param term the label of the string
	@param w the weight of the string
	 */
	public StringItem(Symbol term, double w) {
		super(term, w);
		if (term == null) {
			label = Symbol.getEpsilon();
			setEmptyString();
		}
		if (term == Symbol.getEpsilon())
			setEmptyString();
		numChildren = 0;
		children = new StringItem[1];
	}

	public StringItem(String filename, String encoding) throws FileNotFoundException, IOException, DataFormatException  {
		this(new BufferedReader(new InputStreamReader(new FileInputStream(filename), encoding)));
	}
	// read-from-file constructor.
	/** Creates a tree from a buffered reader 

     @param br Reader object holding the tree
	 */
	public StringItem(BufferedReader br) throws DataFormatException, IOException {
		super(br);
	}
	/** Creates a string from an stream tokenizer. 
	Not really externally useful. Constructor version
	Whatever passes the stream tokenizer should set it up for comments
     @param st stream tokenizer  holding the string
	 */
	public StringItem(StreamTokenizer st) throws DataFormatException, IOException {
		createItem(st);
	}

	/** Creates a string from an stream tokenizer. 
	Not really externally useful

     @param st stream tokenizer  holding the string
	 */
	void createItem(StreamTokenizer st) throws DataFormatException, IOException {
		boolean debug = false;
		boolean inComment = false;
		StringBuffer stringbuf = new StringBuffer();
		while (true) {
			switch (st.nextToken()) {
			case StreamTokenizer.TT_WORD:
				// inside a comment. Ignore it.
				if (inComment)
					break;
				// reached a valid piece. add it.
				if (debug) Debug.debug(debug, "StringItem: About to add token "+st.sval);
				stringbuf.append(st.sval);
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
						stringbuf.append("\"\"\"");
					}
					else {
						st.pushBack();
						if (debug) Debug.debug(debug, "Found empty double quote");
						stringbuf.append("\"\"");
					}
					st.quoteChar('"');
				}
				else {
					if (debug) Debug.debug(debug, "StringItem: About to add quoted token "+st.sval);
					stringbuf.append("\""+st.sval+"\"");
				}
				break;
			case '@':
			case '#':
				if (debug) Debug.debug(debug, "StringItem: pushing symbol back");
				st.pushBack();
			case StreamTokenizer.TT_EOL:
			case StreamTokenizer.TT_EOF:
				inComment = false;
				if (stringbuf.length() > 0) {
					// reached a valid line. Process it and return
					if (debug) Debug.debug(debug, "About to process token "+stringbuf.toString());
					StringItem t = new StringItem(stringbuf);
					children = t.children;
					numChildren = t.numChildren;
					numNodes = t.numNodes;
					truncated = t.truncated;
					label = t.label;
					weight = t.weight;
					isEmptyString = t.isEmptyString;
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

	/** Creates a weighted string from a substring item and a parent.

     @param l parent label
     @param x the child string.
     @param semiring the appropriate semiring this string's weight is understood in
	 */
	public StringItem(Symbol l, StringItem x, Semiring semiring) {

		truncated = false;
		numChildren = 1;
		label = l;
		weight = semiring.ONE();
		children = new StringItem[1];
		children[0] = x;
		weight = semiring.times(weight, x.weight);
		//	    Debug.debug(true, "New StringItem Construction: new weight of string  is "+weight);
		isEmptyString = false;
		setNumNodes();
	}

	/** Creates an  unweighted string from substring and a parent.

     @param l parent label
     @param x child string.
	 */
	public StringItem(Symbol l, StringItem x) {

		truncated = false;
		numChildren = 1;
		label = l;
		children = new StringItem[1];
		children[0] = x;
		isEmptyString = false;
		setNumNodes();
	}
	// deep copy constructor
	/** Deep copy constructor

	@param a the string to copy
	 */
	public StringItem(StringItem a) {

		truncated = false;
		label = a.label;
		weight = a.weight;
		numChildren = a.numChildren;
		numNodes = a.numNodes;
		children = new StringItem[1];
		if (numChildren > 0)
			children[0] = new StringItem((StringItem)a.children[0]);
		isEmptyString = a.isEmptyString;
		isTransducerState = ((StringItem)a).isTransducerState;
	}


	// convenience method to set the stream tokenizer to stop on all non-whitespace
	private static void setTokenizerNoWhitespace(StreamTokenizer st) {
		st.resetSyntax();
		st.whitespaceChars(0, 32);
	}

	// same as the code above, but with a stringbuffer, and the input buffer is actually modified
	/** Creates a string from its string representation

     @param text the string representation of the string
	 */
	public StringItem(StringBuffer text) throws DataFormatException, IOException {
		// actually build the item with the inner constructor
		this(text, 0);
		boolean debug = false;
		// look for any excess stuff
		StreamTokenizer st = new StreamTokenizer(new StringReader(text.toString()));
		setTokenizerNoWhitespace(st);
		while (st.nextToken() != StreamTokenizer.TT_EOF)
			throw new DataFormatException(" Unexpected token "+text.toString());
		setNumNodes();
		if (debug) Debug.debug(debug, "StringItem: Created string "+toString()+"; empty status is "+(isEmptyString()));
	}

	// StringItem elements may be any number of:
	// 1) a raw symbol with no (), ., *, %  and no spaces 
	// 2) a pair of double quotes with anything except another quote in between
	// 3) a triple double quote
	// OR
	// 1) special symbol *e* (epsilon)
	
	// epsilon takes precedent
	private static Pattern termPat = Pattern.compile("\\G(^\\s*\\*e\\*\\s*$|\"\"\"|\"[^\"]+\"|[^\\s\\(\\)\"@]+)\\s*");

	// "inner" stringbuffer constructor. allows the outer one to check for cruft
	private StringItem(StringBuffer text, int level) throws DataFormatException, IOException {

		boolean debug = false;
		if (debug) Debug.debug(debug, level, "String: Input is "+text.toString());

		Matcher match = termPat.matcher(text);
		Vector<String> v = new Vector<String>();
		boolean hitEnd = false;
		while (match.find()) {
			if (debug) Debug.debug(debug, "Matched "+match.group(1)+" in "+text+" at "+match.start());
			hitEnd = match.hitEnd();
			v.add(match.group(1));
		}
		if (v.size() == 0)
			throw new DataFormatException("Didn't match anything into StringItem "+text);
		if (!hitEnd)
			throw new DataFormatException("Didn't match entire string into StringItem: "+text);

		// if we have *e*, add it
		if (v.size() == 1 && v.get(0).equals("*e*")) {
			if (debug) Debug.debug(debug, "Saw epsilon symbol. returning epsilon string item");
			truncated = false;
			numNodes = 0;
			weight = -1;
			label = Symbol.getEpsilon();
			setEmptyString();
			numChildren = 0;
			children = new StringItem[1];
			text.delete(0, text.length());
			return;
		}

		// now build up the StringItem from back to front
		StringItem lastitem = null;
		for (int i =  v.size()-1; i >= 0; i--) {
			StringItem curritem = new StringItem(lastitem, SymbolFactory.getSymbol(v.get(i)), debug);
			lastitem = curritem;
		}
		// finally, explode the current one to this one
		truncated = false;
		numChildren = lastitem.numChildren;
		numNodes = lastitem.numNodes;
		weight = lastitem.weight;
		label = lastitem.label;
		children = lastitem.children;
		isEmptyString = lastitem.isEmptyString;
		// delete the amount of text processed so we don't worry about cruft above
		text.delete(0, text.length());
	}

	// given a vector of Symbols, employ the recursive building
	public StringItem(Vector<Symbol> v) throws DataFormatException {
		this(v.toArray());
	}
	public StringItem(Object[] v) throws DataFormatException {
		boolean debug = false;
		// now build up the StringItem from back to front
		StringItem lastitem = null;
		for (int i =  v.length-1; i >= 0; i--) {
			StringItem curritem = new StringItem(lastitem, (Symbol)v[i], debug);
			if (debug) Debug.debug(debug, "Progressively built StringItem "+curritem.toString());
			lastitem = curritem;
		}
		// explode lastItem to this one
		truncated = false;
		numChildren = lastitem.numChildren;
		numNodes = lastitem.numNodes;
		weight = lastitem.weight;
		label = lastitem.label;
		children = lastitem.children;
		isEmptyString = lastitem.isEmptyString;
		if (debug) Debug.debug(debug, "Done building StringItem "+toString());
	}

	// allows the recursive building of stringitems by the above
	private StringItem(StringItem nxt, Symbol inlabel, boolean debug) throws DataFormatException {
		if (nxt == null) {
			numChildren = 0;
			numNodes = 1;
			children = new StringItem[1];
			if (debug) Debug.debug(debug, "Adding "+inlabel.toString()+" to end of new StringItem");
			label = inlabel;
			if (label == Symbol.getEpsilon())
				setEmptyString();
		}
		else {
			if (nxt.isEmptyString())
				throw new DataFormatException("Epsilon at end of multi-symbol string");
			children = new StringItem[1];
			children[0] = nxt;
			numChildren = 1;
			numNodes = 1+nxt.numNodes();
			if (debug) Debug.debug(debug, "Adding "+inlabel.toString()+" to end of StringItem "+nxt.toString());	    
			label = inlabel;
			if (label == Symbol.getEpsilon())
				throw new DataFormatException("Epsilon in middle of multi-symbol string");

		}
	}

	/**
       Construct StringItem from TreeItem
       @param tree The TreeItem to convert
	 */
	public StringItem(TreeItem tree) throws DataFormatException {
		this(tree.getLeaves());
	}

	/**
       Construct StringItem from TransducerRightString as part of a range cast
       @param trs The TransducerRightString to convert
	 */
	public StringItem(TransducerRightString trs) throws DataFormatException {
		super();
		boolean debug = false;
		// copy state or label
		isEmptyString = false;

		if (trs.hasState())
			label = trs.getState();
		else if (trs.hasLabel()) {
			label = trs.getLabel();
			if (label == Symbol.getEpsilon()) {
				setEmptyString();
			}
		}
		else if (trs.next() == null)
			setEmptyString();
		else
			throw new DataFormatException("No label or state for building non-terminal StringItem from "+trs);

		// not at the end case
		if (trs.next() != null) {
			numChildren = 1;
			children = new StringItem[1];
			children[0] = new StringItem(trs.next());

		}
		else {
			numChildren = 0;
			children = new StringItem[1];
		}
		setNumNodes();
		if (debug) Debug.debug(debug, "Built StringItem "+toString()+" from "+trs+"; isEmptyString status is "+isEmptyString);
	}

	
	/**
    Construct StringItem from TransducerRightString and passed mapping to states, 
    for forming into PairSymbols
	 * Used by cascade training construction
	 */
	public StringItem(TransducerRightString trs,  HashMap<TransducerRightSide, Symbol> map)  {
		super();
		boolean debug = false;
		// copy state or label
		isEmptyString = false;
		if (trs.isEpsilon())
			setEmptyString();
		if (map.containsKey(trs)) {
			label = PairSymbol.get(map.get(trs), trs.getState());
		}
		else {
			label = trs.getLabel();
			if (label == Symbol.getEpsilon()) {
				setEmptyString();
			}
		}
		
		// not at the end case
		if (trs.next() != null) {
			numChildren = 1;
			children = new StringItem[1];
			children[0] = new StringItem(trs.next(), map);

		}
		else {
			numChildren = 0;
			children = new StringItem[1];
		}
		setNumNodes();
		if (debug) Debug.debug(debug, "Built StringItem "+toString()+" from "+trs+"; isEmptyString status is "+isEmptyString);
	}
	

	// does this string represent a transducer state (used in transducer composition)?
	/** 
	does this string represent a transducer state (used in transducer composition)?
	@return true if the String represents a transducer state
	 */
	public boolean isTransducerState() {
		return isTransducerState;
	}


	// mirror image of a stored into this string
	// since child image changes, dump any memoized content
	// if there was a child pointer, hook that in after the item is copied

	/** Does deep copying for the constructor

	@param a the string to copy
	 */
	public void deepCopy(StringItem a) {
		boolean debug = false;
		if (debug) Debug.debug(debug, "StringItem starts as "+toString()+"; stitching in "+a.toString());
		// reset memoized things
		leaves = null;
		treeLeaves = null;
		// swap label
		label = a.label;
		// save off old chain
		Item next = children[0];
		// add in new chain
		if (a.getNumChildren() > 0)
			children[0] = new StringItem((StringItem)a.children[0]);
		else
			children[0] = null;
		// if both old chain and new chain are null, we're done
		if (children[0] == null && next == null) {
			if (debug) Debug.debug(debug, "No stitching done; both chains are null");
			numChildren = 0;
		}
		// if old chain was null, just replace it with new chain
		else if (next == null) {
			if (debug) Debug.debug(debug, "Simple stitching done; old chain was null");
			numChildren = 1;
		}
		// if old chain was not null, must move it to the end of new chain
		else {
			if (debug) Debug.debug(debug, "Normal stitching done; move old chain to the end of new");	
			numChildren = 1;
			StringItem walker = this;
			while (walker.children[0] != null) {
				if (debug) Debug.debug(debug, "Walker at "+walker.toString());	
				walker = walker.getNext();
			}
			// stitch in next
			walker.numChildren = 1;
			walker.children[0] = next;
		}
		setNumNodes();
		isEmptyString = a.isEmptyString;
		setHashCode();
		if (debug) Debug.debug(debug, "StringItem ends as "+toString());
	}


	/** 
	Get the next member

	@return the next word in this stringItem
	 */
	public StringItem getNext() { return (StringItem)children[0]; }


	/** Represent the string in standard notation.

	@return the string representation
	 */
	public String toString() {
		String ret = innerToString();
		if (ret.length() > 0)
			return ret;
		return Symbol.getEpsilon().toString();
	}
	private String innerToString() {
		if (label == null && numChildren <= 0)
			return "";
		StringBuffer ret = new StringBuffer("");
		if (label != Symbol.getEpsilon())
			ret.append(label.toString());
		if (numChildren > 0) {
			if (label != Symbol.getEpsilon())
				ret.append(" ");
			ret.append(((StringItem)children[0]).innerToString());
		}
		return ret.toString();
	}

	// to yield is the same as toString here
	/** The same thing as toString
	@return the string representation
	 */
	public String toYield() {
		return toString();
	}

	// useful as pointer objects, like in stochastic generation
	// note that this method is memoized
	/** Get all words as individual item objects

	@return an array of item objects for each word
	 */
	public Item[] getItemLeaves() {
		if (treeLeaves == null) {
			if (numChildren == 0) {
				treeLeaves =  new StringItem[] {this};
				return treeLeaves;
			}
			// could do this recursively, but we'll probably only want
			// the leaf set at the top of this string item
			// the try/check allows us to trap for incorrect numNodes and avoid looping
			try {
				treeLeaves = new StringItem[numNodes()];
				int counter = 0;
				StringItem walker = this;
				while (true) {
					treeLeaves[counter] = walker;
					if (walker.getNumChildren() == 0)
						break;
					walker = walker.getNext();
					counter++;
					if (counter >= numNodes())
						throw new UnexpectedCaseException("Reached counter value of "+counter+" when numNodes for "+this.toString()+" is only "+numNodes());
				}
			}
			catch (UnexpectedCaseException e) {
				System.err.println("UnexpectedCaseException: "+e.getMessage());
				e.printStackTrace();
			}

		}
		return treeLeaves;
	}

	// get the ith member of the string. could cause OutOfBounds Exception
	public Symbol getSym(int i) {
		if (treeLeaves == null)
			getItemLeaves();
		return treeLeaves[i].label;
	}

	// overrides parent -- gets number of item leaves instead
	public int getSize() {
		return getItemLeaves().length;
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
	public static Vector readStringSet(File file, String encoding) throws FileNotFoundException, IOException {
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
		of = File.createTempFile("stringset", "tmp");
		of.deleteOnExit();
		ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(of));

		Date readTime = new Date();
		while (st.ttype != StreamTokenizer.TT_EOF) {
			StringItem t = null;
			try {
				t = new StringItem(st);
			}
			catch (DataFormatException e) {
				System.err.println("Error with string data format: "+e.getMessage());
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

	/** Get all symbols in this string as an array of symbols.
	Compare to the getLeaves of TreeItem, which traverses down the
	tree; here, all symbols are leaves. This method is memoized.

	@return an array of symbols.
	 */
	public Symbol[] getLeaves() {
		boolean debug = false;
		if (leaves == null) {
			if (debug) Debug.debug(debug, "Setting leaves value for stringItem "+toString());
			if (numChildren == 0) {
				leaves = new Symbol[] {label};
				if (debug) Debug.debug(debug, "No children. Leaves is simply "+leaves[0].toString());
				return leaves;
			}
			// could do this recursively, but we'll probably only want
			// the leaf set at the top of this string item
			leaves = new Symbol[numNodes()];
			leaves[0] = label;
			int counter = 1;
			StringItem copy = (StringItem)children[0];
			while (copy != null) {
				leaves[counter++] = copy.getLabel();
				copy = copy.getNext();
			}
		}
		return leaves;
	}

	// TODO: integrate this back into the upper class

	/** Check if this StringItem, which is assumed to be a rule RHS, matches a passed in item
	RuleSet used to check state-ness of leaves
	@param i The item to check against this item
	@param rs The RuleSet which contains state info
	@return true if the items match
	 */
	public boolean isItemMatch(TrainingString i, CFGRuleSet rs) {
		boolean debug = false;
		if (debug) Debug.debug(debug, "Checking "+toString()+" against "+i.toString());

		// empty: training string must be empty
		if (isEmptyString()) {
			if (debug) Debug.debug(debug, "Rule is epsilon");
			if (i.isEpsilon()) {
				if (debug) Debug.debug(debug, "string is epsilon, so match");
				return true;
			}
			if (debug) Debug.debug(debug, "string is not epsilon, so no match");
			return false;
		}

		// otherwise don't bother
		// literal: must match and remainder must match
		if (!rs.states.contains(label)) {
			if (debug) Debug.debug(debug, "Literal match to "+label.toString());
			if (i == null || i.isEpsilon() || !label.equals(i.getLabel())) {
				if (debug) Debug.debug(debug, "null or mismatch");
				return false;
			}
			TrainingString nextString = i.next();
			// if both nexts are null, match is good
			// if our next is null but string's isn't, match is bad
			// if our next isn't null, recurse
			StringItem next = getNext();
			if (next == null) {
				if (debug) Debug.debug(debug, "End of rule. Match good if string is empty");
				return (nextString == null);
			}
			else {
				if (debug) Debug.debug(debug, "Recursive check on "+next.toString());
				return next.isItemMatch(nextString, rs);
			}
		}
		// state: next literal/terminal, if it exists, must match this entire string (i.e. this state matches empty)
		// or must match one of the next instances of that literal (i.e. this state matches everything up to that)
		// if no more literals, okay (because this state could match the remainder, no matter what
		else  {
			if (debug) Debug.debug(debug, "State match to "+label.toString());
			StringItem nextTerm = getNextTerm(rs);
			if (nextTerm == null) {
				if (debug) Debug.debug(debug, "No next terminal. match okay");	
				return true;
			}
			// if no more string, this is bad because the next term must match something
			if (i == null || i.isEpsilon()) {
				if (debug) Debug.debug(debug, "No more string. match bad");
				return false;
			}
			// first try to match with this variable (and any adjacent variables) aligned to empty string
			if (nextTerm.isItemMatch(i, rs)) {
				if (debug) Debug.debug(debug, "Matched with "+label.toString()+" aligned to empty");
				return true;
			}
			// now try to match with this variable (and any adjacent variables) aligned to progressively larger blocks
			TrainingString nextString = i.next(nextTerm.label);
			while (nextString != null) {
				if (nextTerm.isItemMatch(nextString, rs)) {
					if (debug) Debug.debug(debug, "Matched variable and subsequent "+nextTerm.toString()+" aligned to "+nextString.toString());
					return true;
				}
				if (debug) Debug.debug(debug, "Couldn't match subsequent "+nextTerm.toString()+" aligned to "+nextString.toString());
				nextString = nextString.next(nextTerm.label);
			}
			return false;
		}
	}

	
	/** Check if this StringItem, which is assumed to be a rule RHS, matches a passed in item
	RuleSet used to check state-ness of leaves
	This version uses BSRuleSet, the lazily-explored grammar
	@param i The item to check against this item
	@param rs The RuleSet which contains state info
	@return true if the items match
	 */
	public boolean isItemMatch(TrainingString i, BSRuleSet rs) {
		boolean debug = false;
		if (debug) Debug.debug(debug, "Checking "+toString()+" against "+i.toString());

		// empty: training string must be empty
		if (isEmptyString()) {
			if (debug) Debug.debug(debug, "Rule is epsilon");
			if (i.isEpsilon()) {
				if (debug) Debug.debug(debug, "string is epsilon, so match");
				return true;
			}
			if (debug) Debug.debug(debug, "string is not epsilon, so no match");
			return false;
		}

		// otherwise don't bother
		// literal: must match and remainder must match
		if (!rs.isState(label)) {
			if (debug) Debug.debug(debug, "Literal match to "+label.toString());
			if (i == null || i.isEpsilon() || !label.equals(i.getLabel())) {
				if (debug) Debug.debug(debug, "null or mismatch");
				return false;
			}
			TrainingString nextString = i.next();
			// if both nexts are null, match is good
			// if our next is null but string's isn't, match is bad
			// if our next isn't null, recurse
			StringItem next = getNext();
			if (next == null) {
				if (debug) Debug.debug(debug, "End of rule. Match good if string is empty");
				return (nextString == null);
			}
			else {
				if (debug) Debug.debug(debug, "Recursive check on "+next.toString());
				return next.isItemMatch(nextString, rs);
			}
		}
		// state: next literal/terminal, if it exists, must match this entire string (i.e. this state matches empty)
		// or must match one of the next instances of that literal (i.e. this state matches everything up to that)
		// if no more literals, okay (because this state could match the remainder, no matter what
		else  {
			if (debug) Debug.debug(debug, "State match to "+label.toString());
			StringItem nextTerm = getNext();
			while (nextTerm != null && rs.isState(nextTerm.getLabel()))
				 nextTerm = nextTerm.getNext();
			if (nextTerm == null) {
				if (debug) Debug.debug(debug, "No next terminal. match okay");	
				return true;
			}
			// if no more string, this is bad because the next term must match something
			if (i == null || i.isEpsilon()) {
				if (debug) Debug.debug(debug, "No more string. match bad");
				return false;
			}
			// first try to match with this variable (and any adjacent variables) aligned to empty string
			if (nextTerm.isItemMatch(i, rs)) {
				if (debug) Debug.debug(debug, "Matched with "+label.toString()+" aligned to empty");
				return true;
			}
			// now try to match with this variable (and any adjacent variables) aligned to progressively larger blocks
			TrainingString nextString = i.next(nextTerm.label);
			while (nextString != null) {
				if (nextTerm.isItemMatch(nextString, rs)) {
					if (debug) Debug.debug(debug, "Matched variable and subsequent "+nextTerm.toString()+" aligned to "+nextString.toString());
					return true;
				}
				if (debug) Debug.debug(debug, "Couldn't match subsequent "+nextTerm.toString()+" aligned to "+nextString.toString());
				nextString = nextString.next(nextTerm.label);
			}
			return false;
		}
	}

	// test code
	/** Test code.
	The results of this call change and are unpredictable. Don't use it.
	 */
	public static void main(String argv[]) {
		try {
			File f = new File(argv[0]);
			Vector v = readStringSet(f, "utf-8");
			ObjectInputStream ois = new ObjectInputStream(new FileInputStream((File)v.get(0)));
			int size = ((Integer)v.get(1)).intValue();
			Debug.debug(true, "Read "+size+" strings");
			for (int i = 0; i < size; i++) {
				StringItem t = (StringItem)ois.readObject();
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
	
//	// attempt to do epsilon removal of the ith item of the string, which must be in the provided hashmap
//	// insert the value of the symbol being replaced into store and return it as well
//	public StringItem epsRemove(HashMap<Symbol, Double> epsSyms, HashMap<StringItem, Double> store, int pos) {
//		boolean debug = true;
//	
//	}
	
	
	
}
