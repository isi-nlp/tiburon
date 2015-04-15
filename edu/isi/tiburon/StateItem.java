package edu.isi.tiburon;

// state item tuple
public class StateItem {
    private Symbol state;
    private Item i;
    private int[] align;
    //    private Symbol stSym;
    private Hash hsh;
    private static int nextIndex = 0;
    public static void resetNextIndex() { nextIndex = 0; }
    public static int getNextIndex() { return nextIndex++; }
    public StateItem(Symbol s, Item ti, int is, int ie) {
	align = new int[2];
	state = s;
	i = ti;
	align[0] = is;
	align[1] = ie;
	hsh = new Hash(s.getHash());
	hsh = hsh.sag(ti.getHash());
	hsh = hsh.sag(new Hash(is));
	hsh = hsh.sag(new Hash(ie));
	//	stSym = SymbolFactory.getSymbol(s.toString()+"."+ti.toString()+"("+align[0]+", "+align[1]+")");
    }

    //    public Symbol getSymbol() { return stSym; }
    public String toString() { return "toString not available"; }
    public Symbol state() { return state; }
    public Item item() { return i; }
    public int[] align() { return align; }
    public int hashCode() { return  hsh.bag(Integer.MAX_VALUE);}
    public Hash getHash() { return hsh; }
    // very simple equals: just match symbols
    public boolean equals(Object o) {
	if (!o.getClass().equals(this.getClass()))
	    return false;
	StateItem s = (StateItem)o;
	return state.equals(s.state) &&
	    i.equals(s.i) &&
	    align[0] == s.align[0] &&
	    align[1] == s.align[1];
    }
}
