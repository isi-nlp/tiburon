package edu.isi.tiburon;

import java.util.Hashtable;
import java.util.Vector;



public class SlimStateSeq implements StateSeqMember {
	// a state seq is a vector of mappings to tree rule tuples with heuristic and known weights
	// two types of mappings:
	// 1) NONE = no value
	// 2) SOLO = "normal" case: single free treeruletuple
	// 3) bound: mappings from variables of rule above to more than one tree rule tuple
	// left and right are tracked for symbolification

	// factory memoizer
	static private Hashtable<SlimEarleyState, Hashtable<Vector<Hashtable<Symbol, StateSeqMember>>, SlimStateSeq>> table = new Hashtable<SlimEarleyState, Hashtable<Vector<Hashtable<Symbol, StateSeqMember>>, SlimStateSeq>>();

	static final Symbol SOLO = SymbolFactory.getSymbol("STATESEQ-SOLO");
	Vector<Hashtable<Symbol, StateSeqMember>> states;
	int left;
	int right;
	double known;
	double heur;
	// src state.
	SlimEarleyState es;
	// src external state
	Vector<Symbol> stateChain;
	// dst states (only filled for full rules)
	Vector<SlimEarleyState> childVec;
	// dst external states (only filled for full rules)
	Vector<Vector<Symbol>> childStateChain;
	
	
	// created on demand
	Symbol stateSym = null;
	// equality stuff
	private Hash hsh=null;
	public int hashCode() { 
		if (hsh == null) {
			setHashCode();
		}
		return hsh.bag(Integer.MAX_VALUE);
	}

	private void setHashCode() {
		hsh = new Hash(known);
		hsh = hsh.sag(new Hash(heur));
		for (Hashtable<Symbol, StateSeqMember> h : states)
			for (StateSeqMember t : h.values())
				hsh = hsh.sag(t.getHash());
	}

	public Hash getHash() {
		if (hsh == null) {
			setHashCode();
		}
		return hsh;
	}
	public boolean equals(Object o) {
		if (!o.getClass().equals(this.getClass()))
			return false;
		SlimStateSeq r = (SlimStateSeq)o;
		if (r.known != known)
			return false;
		if (r.heur != heur)
			return false;
		if (!r.states.equals(states))
			return false;
		return true;
	}
	SlimStateSeq(SlimEarleyState e, TreeRuleTuple s, double k, double h) { 
		Vector<Hashtable<Symbol, StateSeqMember>> sobj = new Vector<Hashtable<Symbol, StateSeqMember>> ();
		sobj.add(new Hashtable<Symbol, StateSeqMember>());
		sobj.get(0).put(SOLO, s);
		states = sobj;
//		if (!table.containsKey(e))
//			table.put(e, new Hashtable<Vector<Hashtable<Symbol, StateSeqMember>>, SlimStateSeq>());
//		if (!table.get(e).containsKey(sobj))
//			table.get(e).put(sobj, new SlimStateSeq(e, ))
//			return table.
		es=e;
		known=k; heur=h; 
	}
	SlimStateSeq(SlimEarleyState e, double k, double h) {
		es=e;
		states = new Vector<Hashtable<Symbol, StateSeqMember>>();
		known = k;
		heur = h;
	}
	SlimStateSeq(SlimEarleyState e, Symbol scb, Vector<SlimEarleyState> c, Vector<Vector<Symbol>> csv, double k, double h) {
		es=e;
		childVec = c;
		childStateChain = csv;
		stateChain = new Vector<Symbol>();
		stateChain.add(scb);
		states = new Vector<Hashtable<Symbol, StateSeqMember>>();
		known = k;
		heur = h;
	}
	
	// if any weight is in holding, all weight goes in there
	SlimStateSeq(SlimEarleyState e, SlimStateSeq a, SlimStateSeq b, Semiring s) {
	
		es=e;
		if (b.childVec != null && b.stateChain != null) {
			childVec = b.childVec;
			stateChain = b.stateChain;
			childStateChain = b.childStateChain;
		}
		states = new Vector<Hashtable<Symbol, StateSeqMember>>();
		states.addAll(a.states);
		states.addAll(b.states);
		if (a.heur != s.ONE() || b.heur != s.ONE()) {
			heur = s.times(a.heur, s.times(b.heur, s.times(a.known, b.known)));
			known = s.ONE();
		}
		else {
			known = s.times(a.known, b.known);
			heur = s.times(a.heur, b.heur);
		}
	}
	SlimStateSeq(SlimEarleyState e, Hashtable<Symbol, StateSeqMember> table, double k, double h) {
		es=e;
		states = new Vector<Hashtable<Symbol, StateSeqMember>>();
		states.add(table);
		known = k;
		heur = h;
	}
	SlimStateSeq(SlimStateSeq old, Vector<Hashtable<Symbol, StateSeqMember>> s) {
		es=old.es;
		childVec = old.childVec;
		childStateChain = old.childStateChain;
		stateChain = old.stateChain;
		states = s;
		known = old.known;
		heur = old.heur;
	}
	
	SlimStateSeq(SlimEarleyState e, Vector<Hashtable<Symbol, StateSeqMember>> s, Symbol scb, Vector<SlimEarleyState> c, Vector<Vector<Symbol>> csv, double k, double h) {
		es=e;
		states = s;
		known = k;
		heur = h;
		es=e;
		childVec = c;
		childStateChain = csv;
	}
	
	// remove n members from states and return the rest as a new SlimStateSeq
	public SlimStateSeq shift(int n) {
		Vector<Hashtable<Symbol, StateSeqMember>> news = new Vector<Hashtable<Symbol, StateSeqMember>>(states);
		for (int i = 0; i < n; i++)
			news.remove(0);
		return new SlimStateSeq(this, news);
	}

	
	public String toString() { return states+":"+known+":"+heur; }

	// shouldn't get sym of a SlimStateSeq with non-finished states, or more than one!
	public Symbol getSym() throws UnusualConditionException {
		if (stateSym != null)
			return stateSym;
		StringBuffer exStates = new StringBuffer();
		for (Hashtable<Symbol, StateSeqMember> tab : states) {
			if (tab.size() > 0 && !tab.containsKey(SOLO))
				throw new UnusualConditionException("Requested symbol for incomplete state "+states);
			if (tab.size() > 0) {
				if (tab.get(SOLO) instanceof SlimStateSeq)
					throw new UnusualConditionException("Requested symbol for incomplete state "+states);
				TreeRuleTuple tup = (TreeRuleTuple)tab.get(SOLO);
				exStates.append(tup.rule.getState()+":");
			}
			stateSym = SymbolFactory.getStateSymbol(exStates.toString()+":"+left+":"+right);
		}
		return stateSym;	
	}

}
