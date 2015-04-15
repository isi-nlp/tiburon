package edu.isi.tiburon;

import java.util.HashMap;
import java.util.Vector;

public class IPTreeState {
	
	// one state per level
	private Vector<Integer> stateSeq;
	// one hashtable per level (most should be empty)
	private Vector<HashMap<Integer, Vector<IPTreeState>>> map;
	// connection to input tree
	private IPTree tree;
	
	// get the state or map for the specified level
	public int getState(int level) {
		return stateSeq.get(level);
	}
	public HashMap<Integer, Vector<IPTreeState>> getMap(int level) { 
		if (level >= map.size())
			return null;
		return map.get(level); 
	}
	public IPTreeState(IPTree t, Vector<Integer> ss, Vector<HashMap<Integer, Vector<IPTreeState>>> m) {
		stateSeq = ss;
		tree = t;
		map = m;
	}
	public String toString() { return tree+":"+stateSeq+":"+map; }
}
