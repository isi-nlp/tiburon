package edu.isi.tiburon;

// state tree tree tuple
// now with alignments!
public class StateTreeTree {
    private Symbol state;
    private TreeItem i;
    private TreeItem o;
    private int[] align;
    private Symbol sttSym;
    private int hshcode;
    private static int nextIndex = 0;
    public static void resetNextIndex() { nextIndex = 0; }
    public static int getNextIndex() { return nextIndex++; }
    public StateTreeTree(Symbol s, TreeItem ti, int is, int ie, TreeItem to, int os, int oe) {
	align = new int[4];
	state = s;
	i = ti;
	align[0] = is;
	align[1] = ie;
	o = to;
	align[2] = os;
	align[3] = oe;
	sttSym = SymbolFactory.getStateSymbol(s.toString()+"."+ti.toString()+"("+align[0]+", "+align[1]+"):"+to.toString()+"("+align[2]+", "+align[3]+")");
	hshcode = sttSym.hashCode();
    }
    public Symbol getSymbol() { return sttSym; }
    public String toString() { return sttSym.toString(); }
    public Symbol state() { return state; }
    public TreeItem in() { return i; }
    public TreeItem out() { return o; }
    public int[] align() { return align; }
    public int hashCode() { return hshcode; }
    // very simple equals: just match symbols
    public boolean equals(Object o) {
	if (!o.getClass().equals(this.getClass()))
	    return false;
	StateTreeTree s = (StateTreeTree)o;
	return sttSym.equals(s.sttSym);
    }
}
