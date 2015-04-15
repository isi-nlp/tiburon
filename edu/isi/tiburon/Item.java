package edu.isi.tiburon;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.io.StreamTokenizer;
import java.util.Vector;

/** basic recursive unit described by a FSM. Conventionally either a String
    or a Tree.
 */
public abstract class Item implements Serializable, Trainable {
    /** Comment character for items */
    static final public int COMMENT = '%';

    /** item children -- items are recursive so they are made of items*/
    public Item children[];

    /** how many children in the item. For trees this is useful,
	for strings it's always 1. 
	note this can be evilly monkeyed with */
    public int numChildren;
    // for testing - a flag
    /** for testing. 
	don't bother with this */
    public boolean truncated;
    /** the parent label */
    public Symbol label;
    
    // memoized hash code
    Hash hsh=null;

    // memoized node count
    int numNodes;

    /** if it makes sense to be weighted, the weight of the item */
    // internal form, so it has to be converted by semiring, which isn't 
    // attached to this object!
    public double weight;

    /** for the interface */
    public double getWeight() { return weight; }

    // is this item the empty string (*e*)?
    boolean isEmptyString;

    // carry along in case it's needed. Normally null
    public Semiring semiring = null;
  
    // empty item
    /** Creates a "dummy" item with no label or children
     */
    public Item() {
	
	truncated = false;
	numChildren = 0;
	numNodes = 0;
	// weight is meaningless in the context of this construction
	weight = -1;
	children = null;
	label = null;
	isEmptyString = false;
    }

    /** Creates a nullary item, at this stage both a tree and a string

	@param term the label of the item
    */
    public Item(Symbol term) {
	
	truncated = false;
	numChildren = 0;
	numNodes = 1;
	// weight is meaningless in the context of this construction
	weight = -1;
	children = null;
	//memoized_leaves = null;
	label = term;
	isEmptyString = false;
    }

    /** Creates a weighted nullary item

	@param term the label of the item
	@param w the weight of the item
    */
    public Item(Symbol term, double w) {
	
	truncated = false;
	numChildren = 0;
	numNodes = 1;
	weight = w;
	children = null;
	//memoized_leaves = null;
	label = term;
	isEmptyString = false;
    }

    /** Creates a item from a file 

     @param file string location of the file
    */
    public Item(String file) throws DataFormatException, FileNotFoundException, IOException {
				    this(file, "utf-8");
    }
    /** Creates a item from a file 

    @param file string location of the file
    @param encoding the character set the file was written in
    */
    public Item(String file, String encoding) throws DataFormatException, FileNotFoundException, IOException {
	this(new BufferedReader(new InputStreamReader(new FileInputStream(file), encoding)));
    }

    // read-from-file constructor.
    /** Creates a tree from a file 

     @param br reader object holding the tree
    */
    public Item(BufferedReader br) throws DataFormatException, IOException {
	this(new StreamTokenizer(br));
    }

    /** Creates a string from an stream tokenizer. 
	Not really externally useful. Constructor version

     @param st stream tokenizer  holding the string
    */
    public Item(StreamTokenizer st) throws DataFormatException, IOException {
	// prepare to read single-line item: only break for comment and EOL.
	st.resetSyntax();
	st.commentChar(COMMENT);
	st.wordChars(0, COMMENT-1);
	st.wordChars(COMMENT+1, '\u00FF');
	st.whitespaceChars(10, 12);
	st.eolIsSignificant(true);
	createItem(st);
    }

    /** Creates a string from an stream tokenizer. 
	Not really externally useful. Was originally a constructor

     @param st stream tokenizer  holding the string
    */
    abstract void createItem(StreamTokenizer st) throws DataFormatException, IOException;

    // accessors, cause maybe this'll be private some day
    /** 
	Set the parent label

	@param s the new label
    */
    public void setLabel(Symbol s) {
	label = s;
	setHashCode();
    }
    /** 
	Get the parent label

	@return the parent label
    */
    public Symbol getLabel() { return label; }
    /** 
	Get the number of children

	@return the number of children
    */
    public int getNumChildren() { return numChildren; }
	


    /** Represent the Item in standard parenthesized notation.
	Tiburon tree representation represents nullary trees by their symbols, and trees
	with higher rank by the symbol and a set of parentheses, with children in the parentheses
	in left-to-right order corresponding to their position. So a tree with a root label A, with
	leaf children B and C, would be represented as A(B C).

	@return the string representation of the item
    */
    abstract public String toString();

    // to yield gets rid of any intermediary nodes
    /** Represent the item in a yield form, which may or may not be different
	from the string form.

	@return the string representation of the item yield.
    */
    abstract public String toYield();

    // useful as pointer objects, like in stochastic generation
    // note that this method is memoized
    /** Get all leaves as Item objects.

	@return an array of item
    */
    abstract public Item[] getItemLeaves();

    // how many nodes in this tree? A node is a label
    /** The number of labels in the tree.
	The root label, all nonterminals, and all terminals are counted. For example, the tree A(B C(D E) F(G(H)))
	has 8 nodes.

	@return the number of nodes.
    */
    public int numNodes() {
	return numNodes;
    }

    /** for the interface */
    public int getSize() {
	return numNodes();
    }

    public void setNumNodes() {
	boolean debug = false;
	if (debug) Debug.debug(debug, "label is "+label.toString()+"; "+numChildren+" children");
	int total = 1;
	for (int i = 0; i < numChildren; i++) {
	    children[i].setNumNodes();
	    total += children[i].numNodes();
	}
	numNodes = total;
    }

    // to allow for equality tests in set operations
    public int hashCode() {
	if (hsh == null)
	    setHashCode();
	return hsh.bag(Integer.MAX_VALUE);
    }
    public Hash getHash() {
	if (hsh == null)
	    setHashCode();
	return hsh;
    }

    void setHashCode() {
	hsh = new Hash(label.getHash());
	for (int i = 0; i < numChildren; i++)
	    hsh = hsh.sag( children[i].getHash());
    }

    // equal if label is same and all children are equal
    public boolean equals(Object o) {
	if (!o.getClass().equals(this.getClass()))
	    return false;

	Item t = (Item)o;
	if (!label.equals(t.label))
	    return false;
	if (numChildren != t.numChildren)
	    return false;
	for (int i = 0; i < numChildren; i++)
	    if (!children[i].equals(t.children[i]))
		return false;
	return true;
    }
    
    /** Get all symbols that would be in a yield of this object
	@return an array of Symbols for each yield object
    */

    abstract public Symbol[] getLeaves();


    // make this item recognized as empty string
    /** Set this item as the empty string.

	This is used by StringTransducerRule and shouldn't be separately called.
    */
    public void setEmptyString() { isEmptyString=true; }

    // accessor for isEmptyString
    public boolean isEmptyString() { return isEmptyString; }
	

    /** map an item onto this item. return a vector of stateItem objects.
	exception if the data format doesn't match. this is done recursively
	@param t The item to map
	@param rs The RuleSet governing states, and stuff
	@return a vector of StateItem objects corresponding to the mappings of subtrees to state points
    */
    public Vector mapItem(Item t, int start, int end, RuleSet rs) throws Exception {
	Vector v = new Vector();
	if (rs.states.contains(label)) {
	    StateItem st = new StateItem(label, t, start, end);
	    v.add(st);
	    return v;
	}
	int nextStart = start;
	for (int c = 0; c < numChildren; c++) {
	    Item child = t.children[c];
	    int nextEnd = nextStart+child.getLeaves().length;
	    v.addAll(children[c].mapItem(child, nextStart, nextEnd, rs));
	    nextStart = nextEnd;
	}
	return v;
    }
}
