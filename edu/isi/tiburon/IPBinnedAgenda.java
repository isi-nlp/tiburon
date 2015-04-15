package edu.isi.tiburon;

import java.util.Collection;
import java.util.TreeMap;
import java.util.Vector;

// short before long, unfinished before finished
// not the smartest access strategy
public class IPBinnedAgenda implements IPAgenda {

	private TreeMap<Integer, Vector<IPUnfState>> unfinisheds;
	private TreeMap<Integer, Vector<IPFinState>> finisheds;
	
	public boolean addAll(Collection<? extends IPState> list) {
		for (IPState s : list)
			addState(s);
		return true;
	}

	public void addState(IPState state) {
		boolean debug = false;
		if (state.isAdded()) {
			if (debug) Debug.debug(debug, "NOT adding "+state);
			return;
		}
		if (debug) Debug.debug(debug, "Adding "+state);
		state.setAdded();
		int size = state.getEnd()-state.getStart();
		if (unfinisheds == null)
			unfinisheds = new TreeMap<Integer, Vector<IPUnfState>>();
		if (finisheds == null)
			finisheds = new TreeMap<Integer, Vector<IPFinState>>();
		if (state instanceof IPUnfState) {
			if (!unfinisheds.containsKey(size))
				unfinisheds.put(size, new Vector<IPUnfState>());
			unfinisheds.get(size).add((IPUnfState)state);
		}
		else {

			if (!finisheds.containsKey(size))
				finisheds.put(size, new Vector<IPFinState>());
			finisheds.get(size).add((IPFinState)state);
		}
	}

	public IPState getState() {
		boolean debug = false;
		IPState ret = null;
		// try to get shortest unfinisheds before finisheds of similar size
		for (int key : unfinisheds.keySet()) {
			if (!unfinisheds.get(key).isEmpty()) {
				ret = unfinisheds.get(key).remove(0);
				break;
			}
			if (finisheds.containsKey(key) && !finisheds.get(key).isEmpty()) {
				ret = finisheds.get(key).remove(0);
				break;
			}
		}
		// try to get finisheds skipped by the above
		if (ret == null) {
			for (int key : finisheds.keySet()) {
				if (unfinisheds.containsKey(key))
					continue;
				if (!finisheds.get(key).isEmpty()) {
					ret = finisheds.get(key).remove(0);
					break;
				}
			}
		}
		if (debug) Debug.debug(debug, "Getting "+ret);
		return ret;
	}

	public boolean isEmpty() {
		if (unfinisheds == null && finisheds == null)
			return true;
		for (int key : unfinisheds.keySet()) {
			if (!unfinisheds.get(key).isEmpty())
				return false;
		}
		for (int key : finisheds.keySet()) {
			if (!finisheds.get(key).isEmpty()) {
				return false;
			}
		}
		return true;
	}

}
