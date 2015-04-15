package edu.isi.tiburon;

import java.util.Vector;

// vector of Symbols as a Symbol. Used for composition of states
// possible TODO: wipeout method that gets rid of the vector

public class VecSymbol extends AliasedSymbol {

	private Vector<Symbol> vec;
	private Hash hsh = null;
	public VecSymbol(Vector<Symbol> invec) {
		super(invec.toString());
		vec = invec;
		hsh = new Hash();
		for (Symbol s : vec)
			hsh = hsh.sag(s.getHash());
	}
	public Vector<Symbol> getVec() { return vec; }


	public boolean equals(Object o) {
		boolean debug = false;
		if (debug) Debug.debug(debug, "Comparing "+this+" with "+o);
		if (!o.getClass().equals(this.getClass())) {
			if (debug) Debug.debug(debug, "different classes: "+this.getClass()+" vs "+o.getClass());	    
			return false;
		}
		VecSymbol p = (VecSymbol)o;
		return vec==p.vec;
	}
}