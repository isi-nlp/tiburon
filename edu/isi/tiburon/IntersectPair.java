package edu.isi.tiburon;
import java.util.HashSet;
import java.util.Iterator;

class IntersectPair {
    Symbol a;
    Symbol b;
    int hshcode=-1;
    String str = null;
    public IntersectPair(Symbol sa, Symbol sb) { a = sa; b = sb;}
    // NOTE: this is not a long term solution!
    public Symbol getJoin() {
	Symbol s = SymbolFactory.getStateSymbol(toString());
	return s;
    }
    public boolean equals(Object o) {
	if (!o.getClass().equals(this.getClass()))
	    return false;
	IntersectPair p = (IntersectPair)o;
	return (p.a.equals(a) && p.b.equals(b));
    }
    public String toString() {
	if (str == null)
	    str = a.toString()+"_"+b.toString();
	return str;
    }
    public int hashCode() {
	if (hshcode == -1)
	    setHashCode();
	return hshcode;
    }
    private void setHashCode() {
	hshcode = toString().hashCode();
    }

    /** given A and B, return A x B */
    public static HashSet<IntersectPair> getAllJoins(HashSet aset, HashSet bset) {
	boolean debug = false;
	HashSet<IntersectPair> ret = new HashSet<IntersectPair>();
	Iterator ita = aset.iterator();
	while (ita.hasNext()) {
	    Symbol asym = (Symbol)ita.next();
	    Iterator itb = bset.iterator();
	    while (itb.hasNext()) {
		Symbol bsym = (Symbol)itb.next();
		if (debug) Debug.debug(debug, "Joining "+asym+" and "+bsym);
		ret.add(new IntersectPair(asym, bsym));
	    }
	}
	return ret;
    }
}
