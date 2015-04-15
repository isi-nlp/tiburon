package edu.isi.tiburon;

import java.util.Vector;

// slim representation of a rule: state, symbol, state vector, weight
public abstract class GrammarRule {
	
	abstract public Symbol getState(); 
	abstract public Symbol getLabel();
	abstract public Vector<Symbol> getChildren();
	// left corner: for FS. 
	// TODO: allow left corner that is not leftmost child!
	private boolean touched;
	// for dotted rules
	abstract public Symbol getChild(int i);
// left corner: for FS. 
	
	public Symbol getLeftCorner() {
		return getChild(0);
	}
	
	abstract public double getWeight(); 
	// sets touched. returns true if was previously not touched
	public boolean touch() { 
		boolean change = !touched;
//		if(change)
//			Debug.prettyDebug("Touching "+toString());
		touched = true;
		return change;
	}
	// superclass!
	public GrammarRule() {
		touched = false;
	}
}
