package edu.isi.tiburon;


// abstract parent of transducerRightString and TransducerRightTree
// used for trvm's sake
// all common stuff is in here


public abstract class TransducerRightSide {


    Symbol label;
    Symbol variable;
    Symbol state;
    Hash hsh = null;

    // global check for comma'd symbols, which leads to a warning
    static boolean commaTrip;

    static {
	commaTrip = false;
    }
    public static void setCommaTrip() { commaTrip = true; }

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

    abstract void setHashCode();

    // equal if label and/or variable are same and all children are equal

    abstract public boolean equals(Object o);

    // is rhs epsilon? tree and string have different meanings w/r/t this...
    abstract public boolean isEpsilon();

    // checks and accesses on label, variable, and state
    public boolean hasLabel() { return label != null; }
    public boolean hasVariable() { return variable != null; }
    public boolean hasState() { return state != null; }
    public Symbol getLabel() { return label; }
    public Symbol getVariable() { return variable; }
    public Symbol getState() { return state; }

    // an after-the-fact set
    void setLabel(Symbol l) {label = l; setHashCode();}
    void setState(Symbol s) {state = s; setHashCode();}
    void setVariable(Symbol v) {variable = v; setHashCode();}


}
