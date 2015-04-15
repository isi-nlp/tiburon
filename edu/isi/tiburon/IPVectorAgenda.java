package edu.isi.tiburon;

import java.util.Collection;
import java.util.Vector;

// very basic agenda -- filo vector
public class IPVectorAgenda extends Vector<IPState> implements IPAgenda {

	public void addState(IPState state) {
		boolean debug = true;
		if (!state.isAdded()) {
			if (debug) Debug.debug(debug, "Adding "+state);
			state.setAdded();
			add(state);
		}
		else
			if (debug) Debug.debug(debug, "NOT adding "+state);

	}

	public IPState getState() {
		boolean debug = true;
		IPState ret = remove(0);
		if (debug) Debug.debug(debug, "Getting "+ret);
		return ret;
	}

	// isEmpty handled by superclass
	
	// for addAll, do repetitive calls to addState
	public boolean addAll(Collection<? extends IPState> list) {
		for (IPState s : list)
			addState(s);
		return true;
	}
}
