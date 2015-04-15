package edu.isi.tiburon;

// state tree trainingString tuple
// now with alignments!
public class StateTreeString {
    private Symbol state;
    private TreeItem i;
    private TrainingString o;
    private int[] align;
    private Hash hsh=null;
    private static int nextIndex = 0;
    public static void resetNextIndex() { nextIndex = 0; }
    public static int getNextIndex() { return nextIndex++; }
    public StateTreeString(Symbol s, TreeItem ti, int is, int ie, TrainingString to, int os, int oe) {
	align = new int[4];
	state = s;
	i = ti;
	align[0] = is;
	align[1] = ie;
	o = to;
	align[2] = os;
	align[3] = oe;
	// to match variable cluster -- hashcode of symbol, tree, tree indices, and training string alone (no indices)
	//	Debug.debug(true, "StateTreeString: hash of "+toString());

	hsh = new Hash(s.getHash());
	hsh = hsh.sag(ti.getHash());
	hsh = hsh.sag(new Hash(is));
	hsh = hsh.sag(new Hash(ie));
	hsh = hsh.sag(to.getHash());
    }
    public StateTreeString(StateTreePair st, TrainingString to) {
	this(st.getState(), st.getTree(), st.getStart(), st.getEnd(), to, to.getStartIndex(), to.getEndIndex());
    }
    //    public Symbol getSymbol() { return stsSym; }


    public Symbol state() { return state; }
    public TreeItem in() { return i; }
    public TrainingString out() { return o; }
    public int[] align() { return align; }
    // to avoid temptation
    public String toString() { 
	//		return state.toString()+"."+i.toString()+"("+align[0]+", "+align[1]+"):"+o.toString()+"("+align[2]+", "+align[3]+")";
			return "toString not available"; 
    }
    public int hashCode() { return  hsh.bag(Integer.MAX_VALUE);}
    public Hash getHash() { return hsh; }
    public boolean equals(Object obj) {
	if (!obj.getClass().equals(this.getClass()))
	    return false;
	StateTreeString s = (StateTreeString)obj;
	boolean ans = (state.equals(s.state) &&
		i.equals(s.i) &&
		o.equals(s.o) &&
		align[0] == s.align[0] &&
		align[1] == s.align[1] &&
		align[2] == s.align[2] &&
		align[3] == s.align[3]);
	return ans;
    }
}
