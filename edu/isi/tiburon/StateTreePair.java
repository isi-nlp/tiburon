package edu.isi.tiburon;

// state tree pair - often used to represent a state when building
// rtg out of transducer rule. equality tests, symbol construction,
// toString..

public class StateTreePair extends AliasedSymbol {
    private Symbol state;
    private TreeItem tree;
    //    private Symbol pairSym=null;
    private Hash hsh=null;
    private int start;
    private int end;
    public StateTreePair(Symbol s, TreeItem t, int ts, int te) {
    	super(s+"."+t+"("+ts+", "+te+")");
	state = s;
	tree = t;
	start = ts;
	end = te;
	hsh = new Hash(s.getHash());
	hsh = hsh.sag(t.getHash());
	hsh = hsh.sag(new Hash(ts));
	hsh = hsh.sag(new Hash(te));
    }
    
    // covered by aliasedsymbol
//    // to avoid temptation
//    public String toString() {
//		return state.toString()+"."+tree.toString()+"("+start+", "+end+")";
//	//return "not available";
//	// causes endless loop unless we actually make a stringified symbol!
//	//	return getSymbol().toString();
//    }
    // bless as a symbol
    public Symbol getSymbol() {
	return this;
	//	 if (pairSym == null)
	//		     pairSym = SymbolFactory.getSymbol(state.toString()+"."+tree.toString()+"("+start+", "+end+")");
	//	 	return pairSym;
    }
    public Symbol getState() {
	return state;
    }
    public int getStart() {
	return start;
    }
    public int getEnd() {
	return end;
    }
    public TreeItem getTree() {
	return tree;
    }
 
 

    public boolean equals(Object o) {
	boolean debug = false;
	if (debug) Debug.debug(debug, "Comparing "+this+" with "+o);
	if (!o.getClass().equals(this.getClass())) {
	    if (debug) Debug.debug(debug, "different classes: "+this.getClass()+" vs "+o.getClass());	    
	    return false;
	}
	StateTreePair p = (StateTreePair)o;
	if (debug && !p.state.equals(state))
	    Debug.debug(debug, "states not equal");
	if (debug && !p.tree.equals(tree))
	    Debug.debug(debug, "trees not equal");
	return (p.state.equals(state) &&
		p.tree.equals(tree) &&
		start == p.start &&
		end == p.end);
    }

}
