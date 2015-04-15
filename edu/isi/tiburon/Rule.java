package edu.isi.tiburon;

import java.io.Serializable;
import java.util.Hashtable;
import java.util.Vector;
//import edu.dickinson.braught.Rounding;
// some sort of grammar rule (rtg or cfg). Symbol lhs, weight.
public abstract class Rule implements Serializable, Derivable {

    /** Comment character for items */
    static final public int COMMENT = '%';

    Symbol lhs;
    Item rhs;
    // subclasses have rhs
    
    // internal value!
    double weight;
    Semiring semiring;

    // for serializing
    int index = -1;

    // for tied rules
    int tieid=0;

    // linking to real rules
 // for composition. not always filled
	private Vector<TransducerRule> tiedRules=null;


	// unique accessors

	public boolean hasTransducerRule() { return tiedRules != null; }
	public Vector<TransducerRule> getTransducerRules() { return tiedRules; }
	public void tieToTransducerRule(TransducerRule ttr) {
		if (tiedRules == null)
			tiedRules = new Vector<TransducerRule>();
		tiedRules.add(ttr);
		setHashCode();
	}
	public void tieToTransducerRule(Vector<TreeTransducerRule> ttr) {
		if (ttr == null)
			return;
		if (tiedRules == null)
			tiedRules = new Vector<TransducerRule>(ttr);
		else
			tiedRules.addAll(ttr);
		setHashCode();
	}
    
    // accessors
    public Symbol getLHS() { return lhs; }
    // for compatability with transducers
    public Symbol getLHSSym() { return getLHS(); }
    public Symbol getLHSCondSym() { return getLHS(); }
    public Item getRHS() { return rhs; }
    public double getWeight() { return weight; }
    public Semiring getSemiring() { return semiring; }
    public int getIndex() { return index;}
    public int getTie() { return tieid; }

    // settors
    public void setLHS(Symbol l) { 
	lhs = l;
	setHashCode();
    }

    public void setWeight(double p) {
	weight = p;
	//	setHashCode();
    }
    public void setIndex(int i) {
	index = i;
    }
    
    public void setTie(int i) {
	tieid = i;
    }
    
    // pre-set hash code
    Hash hsh=null;

    public int hashCode() {
	if (hsh == null) {
	    setHashCode();
	}
	return hsh.bag(Integer.MAX_VALUE);
    }
    public Hash getHash() {
	if (hsh == null)
	    setHashCode();
	return hsh;
    }
    abstract void setHashCode();

    // equals if label is same and rhs are same and weights are same
    abstract public boolean equals(Object o);

    // check if the rhs is in normal form, or if it's been decided to be in normal form
    abstract public boolean isNormal();

    // checks for rhs similarity
    abstract public boolean isSame(Rule r);


    // remove funny looking states from lhs and rhs, given an alias map.
    // let subclasses handle this
    abstract public void makePrintSafe(RuleSet rs, Hashtable<Symbol, Symbol> alias);

    public String toString() {
	String s = lhs.toString()+" -> "+rhs.toString()+" # "+Rounding.round(semiring.internalToPrint(weight), 6);
	//String s = lhs.toString()+" -> "+rhs.toString()+" # "+weight + " ## " + hashCode();
	//	String s = lhs.toString()+": "+lhs.hashCode()+" -> "+rhs.toString()+" # "+weight + " ## " + hashCode();
	return s;
    }


    // for training: getPaths returns an ordered set of the symbols of state, tree tuples, encapsulated into a StateTree
    // the name getPaths is archaic to jon's paper
    
    // this version to be invokable by the generic




}
