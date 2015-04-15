package edu.isi.tiburon;

import java.util.HashMap;

// abstract pair for making tuples
public class Pair<A, B> {
	// 
	private static HashMap<Object, HashMap<Object, Pair>> memo;
	public static Pair get(Object a, Object b) {
		if (memo == null)
			memo = new HashMap<Object, HashMap<Object, Pair>>();
		if (!memo.containsKey(a))
			memo.put(a, new HashMap<Object, Pair>());
		if (!memo.get(a).containsKey(b))
			memo.get(a).put(b, new Pair(a, b));
		return memo.get(a).get(b);
	}
	private A _a;
	private B _b;
	public A l() { return _a; }
	public B r() { return _b; }
	public Pair (A a, B b) {_a = a; _b = b; }
	public String toString() { return "<"+_a+", "+_b+">"; }
	// TODO: Hashcodes, if these are to be stored!
	
}
