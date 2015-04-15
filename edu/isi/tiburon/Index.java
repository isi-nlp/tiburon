package edu.isi.tiburon;

// two integers denoting a span in a string
public class Index {
	private static PMap<Integer, PMap<Integer,Index>> memo;
	private static int count;

	static {
		count = 0;
		 memo = new PMap<Integer, PMap<Integer,Index>>();
	}
	private int l;
	private int r;
	private Index(int left, int right) {
		l = left;
		r = right;
	}
	public int left() { return l; }
	public int right() { return r; }
	public static Index get(int left, int right) {
		if (!memo.goc(left).containsKey(right)) {
			memo.get(left).put(right, new Index(left, right));
			count++;
		}
		return memo.get(left).get(right);
	}
	public String toString() { return "["+l+","+r+"]"; }
	public static void clear() {
		boolean debug = false;
		if (debug) Debug.debug(debug, "Clearing "+count);
		count = 0;
		memo = new PMap<Integer, PMap<Integer,Index>>();
	}
}
