package edu.isi.tiburon;

import java.util.HashMap;
import java.util.Map;

// "perl map" -- has goc (get or create) method for chained hashmaps
// if the object being acquired is a PMap and doesn't exist, create a new one
public class PMap<K, V> extends HashMap<K, V> {
	public PMap goc(K k) {
		PMap v = (PMap)get(k);
		// TODO: ACK! v will always be PMap! 
		if (v == null)
			put(k, (V)(v = new PMap()));
		return v;
	}
	public static void main(String argv[]) {
		PMap<String, PMap<String, HashMap<String, String>>> a = new PMap<String, PMap<String, HashMap<String, String>>>();
		a.goc("x").goc("y").put("z", "v");
		System.out.println(a);
	}
}
