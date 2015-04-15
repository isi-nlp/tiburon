package edu.isi.tiburon;

import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.PriorityQueue;
import java.util.Vector;

// finished integrated parsing state. state vector (one state per transducer)
// pointer to next item

public class IPFinState implements IPState, Comparable<IPFinState> {

	private int start;
	private int end;
	private HashSet<IPUnfState> sources;
	// one state per level
	private Vector<Integer> stateSeq;
	// one hashtable per level (most should be empty)
	private Vector<HashMap<Integer, IPFinState>> map;
	// residual weight -- should only be something interesting if the state is not complete
	private double weight;
	
	// viterbi weight -- used to determine viterbi cost of other potential IPFinStates and prevent
	// them from being constructed
	private double viterbi;
	
	// flag for building rtg
	private boolean isDone;
	
	// flag for processing in parser
	private boolean isAdded;
	
	// memo string -- for removing from priorityqueue
	private String memostring;
	
	// used for priority queue trick
	private static IPFinState globalGoldState;

	static {
		globalGoldState = new IPFinState(0, 0, null, null, null, 0, 0, null);
	}
	
	// equality only determined by start, end, and state seq
	public boolean equals(Object o) {
		boolean debug = false;
		if (!o.getClass().equals(this.getClass()))
			return false;
		IPFinState r = (IPFinState)o;
		if (start != start)
			return false;
		if (end != end)
			return false;
		if (!stateSeq.equals(r.stateSeq)) {
			if (debug) Debug.debug(debug, "Not equal: "+stateSeq+" and "+r.stateSeq);
			return false;
		}
		return true;
	}
	
	// for comparator: just based on viterbi
	public int compareTo(IPFinState o) {
		if (viterbi < o.viterbi)
			return -1;
		if (viterbi == o.viterbi)
			return 0;
		return 1;
	}
	
	// edges lead back to IPUnfState
	// private ??? edges
	
	
	// basic constructor
	private IPFinState(int st, int e, IPUnfState src, Vector<Integer> ss, Vector<HashMap<Integer, IPFinState>> m, double w, double v, String ms ) {
		start = st;
		end = e;
		sources = new HashSet<IPUnfState>();
		stateSeq = ss;
		map = m;
		weight = w;
		viterbi = v;
		isDone = false;
		memostring = ms;
		addSource(src);
	}
	// memoized based on start, end indices, then vector of labels and hashmap
	// pruning bins of start, end indices here, too
	private static HashMap<Integer, HashMap<Integer, PriorityQueue<IPFinState>>> pruning_ipfinstate;
	
	// TODO: is map indexing buggy? something more principled should be done
	private static HashMap<String, IPFinState> memo_ipfinstate;
	
	// no change to weights, but source might have to be updated
	// do update the viterbi cost, though
	// use pruning object
	
	// TODO: dynamically alter pruning statistics
	public static IPFinState get(Vector<Integer> vec, IPUnfState last, Vector<HashMap<Integer, IPFinState>> m, double vit, Semiring semiring) {
		boolean ismemo=true;
		boolean isprune = true;
		boolean debug = true;
		int prunesize = 10;
		// construction of the queue for this object and early exit if it's worse than the worst thing
		PriorityQueue<IPFinState> queue = null;
		if (isprune) {
			if (pruning_ipfinstate == null)
				pruning_ipfinstate = new HashMap<Integer, HashMap<Integer, PriorityQueue<IPFinState>>>();
			boolean wasBuilt = true;
			if (!pruning_ipfinstate.containsKey(last.getStart())) {
				wasBuilt = false;
				pruning_ipfinstate.put(last.getStart(), new HashMap<Integer, PriorityQueue<IPFinState>>());
			}
			if (!pruning_ipfinstate.get(last.getStart()).containsKey(last.getEnd())) {
				wasBuilt = false;
				if (debug) Debug.debug(debug, "Built queue for "+last.getStart()+":"+last.getEnd());
				pruning_ipfinstate.get(last.getStart()).put(last.getEnd(), new PriorityQueue<IPFinState>());
			}
			queue = pruning_ipfinstate.get(last.getStart()).get(last.getEnd());
			if (wasBuilt) {
				if (debug) Debug.debug(debug, "Adding state for "+last.getStart()+":"+last.getEnd());
				if (queue.size() >= prunesize && semiring.better(queue.peek().getViterbi(), vit)) {
					if (debug) Debug.debug(debug, "Queue for "+last.getStart()+", "+last.getEnd()+" too full to allow wgt of "+vit);
					return null;
				}
			}
		}
		if (ismemo) {
			if (memo_ipfinstate == null)
				memo_ipfinstate = new HashMap<String, IPFinState>();
			String str = new String(last.getStart()+":"+last.getEnd()+":"+vec);
			IPFinState ret = null;
			if (!memo_ipfinstate.containsKey(str)) {
				ret = new IPFinState(last.getStart(), last.getEnd(), last, vec, m, semiring.ONE(), vit, str);
				memo_ipfinstate.put(str, ret);
				// if queue is overfull, remove last item
				if (isprune) {
					if (queue.size() >= prunesize) {
						IPFinState rem = queue.poll();
						if (debug) Debug.debug(debug, "Removing "+rem+" with viterbi "+rem.getViterbi()+" replaced with "+ret+"; viterbi of "+ret.getViterbi());
						memo_ipfinstate.remove(rem.getMemoString());
					}
					queue.add(ret);
				}
			}
			else {
				ret = memo_ipfinstate.get(str);
				// POSSIBLY IMPLEMENTATION DEPENDENT: reordering in queue should happen as long
				// as we're not the best thing in there -- to avoid this, add something now.
				if (isprune) {
					globalGoldState.setViterbi(semiring.ZERO());
					queue.add(globalGoldState);
				}
				if (semiring.better(vit, ret.getViterbi()))
					ret.setViterbi(vit);
				ret.addSource(last);
				if (isprune) {
					IPFinState junk = queue.poll();
					if (junk != globalGoldState)
						Debug.debug(true, "Warning: priority queue reordering trick didn't work!");
				}
			}
			return ret;
		}
		else {
			return new IPFinState(last.getStart(), last.getEnd(), last, vec, m, semiring.ONE(), vit, null);
		}
	}
	
	// version with weight changing: add weights if the state already exists
	public static IPFinState get(Vector<Integer> vec, IPUnfState last, Vector<HashMap<Integer, IPFinState>> m, double w, double vit, Semiring semiring) {
		boolean ismemo=true;
		boolean isprune = true;
		boolean debug = true;
		int prunesize = 10;
		// construction of the queue for this object and early exit if it's worse than the worst thing
		PriorityQueue<IPFinState> queue = null;
		if (isprune) {
			if (pruning_ipfinstate == null)
				pruning_ipfinstate = new HashMap<Integer, HashMap<Integer, PriorityQueue<IPFinState>>>();
			boolean wasBuilt = true;
			if (!pruning_ipfinstate.containsKey(last.getStart())) {
				wasBuilt = false;
				pruning_ipfinstate.put(last.getStart(), new HashMap<Integer, PriorityQueue<IPFinState>>());
			}
			if (!pruning_ipfinstate.get(last.getStart()).containsKey(last.getEnd())) {
				wasBuilt = false;
				if (debug) Debug.debug(debug, "Built queue for "+last.getStart()+":"+last.getEnd());

				pruning_ipfinstate.get(last.getStart()).put(last.getEnd(), new PriorityQueue<IPFinState>());
			}
			queue = pruning_ipfinstate.get(last.getStart()).get(last.getEnd());
			if (wasBuilt) {
				if (debug) Debug.debug(debug, "Adding state for "+last.getStart()+":"+last.getEnd());
				if (queue.size() >= prunesize && semiring.better(queue.peek().getViterbi(), vit)) {
					if (debug) Debug.debug(debug, "Queue for "+last.getStart()+", "+last.getEnd()+" too full to allow wgt of "+vit);
					return null;
				}
			}
		}
		if (ismemo) {
			if (memo_ipfinstate == null)
				memo_ipfinstate = new HashMap<String, IPFinState>();
			String str = new String(last.getStart()+":"+last.getEnd()+":"+vec+":"+m);
			IPFinState ret = null;
			if (!memo_ipfinstate.containsKey(str)) {
				ret = new IPFinState(last.getStart(), last.getEnd(), last, vec, m, w, vit, str);
				memo_ipfinstate.put(str, ret);
				// if queue is overfull, remove last item
				if (isprune) {
					if (queue.size() >= prunesize) {
						IPFinState rem = queue.poll();
						if (debug) Debug.debug(debug, "Removing "+rem+" with viterbi "+rem.getViterbi()+" replaced with "+ret+"; viterbi of "+ret.getViterbi());
						memo_ipfinstate.remove(rem.getMemoString());
					}
					queue.add(ret);
				}
			}
			else {
				ret = memo_ipfinstate.get(str);
				// POSSIBLY IMPLEMENTATION DEPENDENT: reordering in queue should happen as long
				// as we're not the best thing in there -- to avoid this, add something now.
				if (isprune) {
					globalGoldState.setViterbi(semiring.ZERO());
					queue.add(globalGoldState);
				}
				if (semiring.better(vit, ret.getViterbi()))
					ret.setViterbi(vit);
				ret.setWeight(semiring.plus(ret.getWeight(), w));
				ret.addSource(last);
				if (isprune) {
					IPFinState junk = queue.poll();
					if (junk != globalGoldState)
						Debug.debug(true, "Warning: priority queue reordering trick didn't work!");
				}
			}
			return ret;
		}
		else {
			return new IPFinState(last.getStart(), last.getEnd(), last, vec, m, w, vit, null);
		}
	}
	// version just used for top state retrieval
	public static IPFinState get(Vector<Integer> vec, int start, int end) {
		boolean ismemo = true;
		if (ismemo) {
			if (memo_ipfinstate == null)
				memo_ipfinstate = new HashMap<String, IPFinState>();
			String str = new String(start+":"+end+":"+vec);
			if (!memo_ipfinstate.containsKey(str)) {
				memo_ipfinstate.put(str, new IPFinState(start, end, null, vec, null, 0, 0, str));
			}
				
			return memo_ipfinstate.get(str);
		}
		else {
			return new IPFinState(start, end, null, vec, null, 0, 0, null);
		}
	}
	
	// top member is base
	public int getBaseState() {
		return stateSeq.get(0);
	}

	public int getEnd() {
		return end;
	}

	public int getStart() {
		return start;
	}

	// get the state for the specified level
	public int getState(int level) {
		return stateSeq.get(level);
	}
	
	// get the residual weight
	public double getWeight() { return weight; }
	
	// get viterbi value for this state
	public double getViterbi() { return viterbi; }
	// can be set after creation -- viterbi condition not enforced here!
	public void setViterbi(double v) { viterbi = v; }
	// get the source UnfState
	public Iterable<IPUnfState> getSources() { return sources; }
	// add a source to the set
	public void addSource(IPUnfState source) { 
		boolean debug = false;
		if (debug) Debug.debug(debug, "Adding "+source+" to "+this);
		sources.add(source); 
	}
	// set the weight -- used to add in weight when things are multiply defined
	public void setWeight(double w) { weight = w; }
	
	public HashMap<Integer, IPFinState> getMap(int level) { return map.get(level); }
	
	public boolean isAdded() { return isAdded; }
	public void setAdded() { isAdded = true; }
	public boolean isDone() { return isDone; }
	public void setDone() { isDone = true; }
	
	public Vector<Integer> getSeq() { return stateSeq; }
	
	public String getMemoString() { return memostring; }
	// finish: given a IPUnfState pointing at the end of its list, look at the last-added
	// edge and recursively generate all
	// sequences of IPFinStates that could lead to it by descending through edges.
	// for each of these sequences, get the set of IPFin states that could be formed by
	// traversing through the cascade
		
	public static Vector<IPFinState> finish(IPUnfState input, IPTemplateAccess[] cascade, HashSet<IPFinState> stateList, Semiring semiring) {
		boolean debug = false;
		boolean minidebug = true;
		Vector<IPFinState> ret = new Vector<IPFinState>();
		
		
		// only operate on last added edge
		Vector<Vector<IPFinState>> sequences = input.getLastEdge().getSequences();
		if (debug) Debug.debug(debug, "Sequences for "+input.getLastEdge()+" are "+sequences);		
		if (minidebug || debug) Debug.debug(minidebug || debug, "About to process "+sequences.size()+" sequences");
		for (Vector<IPFinState> seq : sequences) {
			double residual = semiring.ONE();
			double vitbase = semiring.ONE();
			Vector<Integer> levelseq = new Vector<Integer>();
			for (IPFinState state : seq) {
				levelseq.add(state.getState(0));
				residual = semiring.times(residual, state.getWeight());
				vitbase = semiring.times(vitbase, state.getViterbi());
			}
			if (debug) Debug.debug(debug, "Viterbi base is "+vitbase);
			// add in literals for first level
			int i = 0;
			for (int item : input.getRHS()) {
				if (item <= IPUnfState.getLastLiteral())
					levelseq.add(i, item);
				i++;
			}
			
			
			DstStructure dsts = getDsts(input.getBaseState(), seq, levelseq, residual, cascade, 0);
			i = 0;
			// TODO: use viterbis in seq and components in dsts to get viterbi cost of this newly 
			// constructed state
			// TODO: IDEA: can potentially screen out dsts by getting rid of bad rule sequences.
			//       naturally, can avoid creating members here, too
			// TODO: have to be able to query weights for a particular span of finished rule!
			for (Vector<Integer> dst : dsts.seq) {
				IPFinState cand =  null;
				double candvit = semiring.times(vitbase, dsts.vit.get(i));
				
				// if state i is partial, sum weights. otherwise treat as ONE.
				// TODO: pass in potential viterbi cost, assuming if it is better it will stick 
				if (dsts.rtg.get(i) != null)
					cand = get(dst, input, dsts.map.get(i), dsts.vit.get(i), semiring);
				else
					cand = get(dst, input, dsts.map.get(i), dsts.wgt.get(i), dsts.vit.get(i), semiring);
				if (cand != null) {
					if (debug) Debug.debug(debug, "Viterbi cost calculated for "+cand+" is "+candvit);
					if (!stateList.contains(cand)) {
						stateList.add(cand);
						ret.add(cand);
					}
				}
				i++;
			}
		
		}		
		return ret;
	}
	
	
	// get rtg rule sequences from this state and determine other states that should be traversed
	public void getRuleElements(IPTemplateAccess[] cascade, 
			Semiring semiring, 
			HashMap<IPFinState, HashMap<Vector<IPFinState>, HashMap<Integer, Double>>> builtRules,
			Vector<IPFinState> todoList) {
		boolean debug = false;
		if (debug) Debug.debug(debug, "Doing "+this+" with "+sources.size()+" sources");
		for (IPUnfState input : getSources()) {
			Vector<Vector<IPFinState>> sequences = input.getSequences();
			if (debug) Debug.debug(debug, "Sequences for "+input+" are "+sequences);
			for (Vector<IPFinState> seq : sequences) {
				double residual = semiring.ONE();
				Vector<Integer> levelseq = new Vector<Integer>();
				for (IPFinState state : seq) {
					levelseq.add(state.getState(0));
					residual = semiring.times(residual, state.getWeight());
				}
				// add in literals for first level
				int i = 0;
				for (int item : input.getRHS()) {
					if (item <= IPUnfState.getLastLiteral())
						levelseq.add(i, item);
					i++;
				}
				WeightDstStructure dsts = getRuleDsts(input.getBaseState(), seq, levelseq, residual, cascade, 0);
				i = 0;
				for (Vector<Integer> dst : dsts.seq) {
					IPFinState cand = null;
					// if there is a rule for this state, get it and ONE-out the weight 
					if (dsts.rtg.get(i) != null) {
						// this type of get assumes weight of ONE, which is for rule-bearing states
						cand = get(dst, input, dsts.map.get(i), semiring.ONE(), semiring);
						if (!builtRules.containsKey(cand)) {
							builtRules.put(cand, new HashMap<Vector<IPFinState>, HashMap<Integer, Double>>());
						}
						if (!builtRules.get(cand).containsKey(dsts.rtg.get(i).rhs))
							builtRules.get(cand).put(dsts.rtg.get(i).rhs, new HashMap<Integer, Double>());
						if (!builtRules.get(cand).get(dsts.rtg.get(i).rhs).containsKey(dsts.rtg.get(i).label)) {
							builtRules.get(cand).get(dsts.rtg.get(i).rhs).put(dsts.rtg.get(i).label, dsts.wgt.get(i));
							if (debug) Debug.debug(debug, "extracted the rule "+cand+" -> "+dsts.rtg.get(i).label+"("+dsts.rtg.get(i).rhs+") # "+dsts.wgt.get(i));
							for (IPFinState rhsstate : dsts.rtg.get(i).rhs) {
								if (!rhsstate.isDone()) {
									if (debug) Debug.debug(debug, "Going to do "+rhsstate);
									todoList.add(rhsstate);
									rhsstate.setDone();
								}
							}
						}
						// don't bother with rules if they've already been extracted
					//	else {
					//		builtRules.get(cand).get(dsts.rtg.get(i).rhs).put(dsts.rtg.get(i).label, 
					//				semiring.plus(builtRules.get(cand).get(dsts.rtg.get(i).rhs).get(dsts.rtg.get(i).label), dsts.wgt.get(i)));
					//		if (debug) Debug.debug(debug, "re-extracted the rule "+cand+" -> "+dsts.rtg.get(i).label+"("+dsts.rtg.get(i).rhs+") # "+dsts.wgt.get(i)+
					//				"; total is now "+builtRules.get(cand).get(dsts.rtg.get(i).rhs).get(dsts.rtg.get(i).label));
					//	}
					}
					else {
//						Debug.debug(true, "Skipping virtual states");
					}
					i++;
				}
			}	
		}

	}
	
	// given a state
	public static boolean validateState(IPFinState state) {
		
		
		return true;
	}
	
	public String toString() {
		StringBuffer ret = new StringBuffer(stateSeq.toString());
		ret.append("["+getStart()+":"+getEnd()+"]");
		return ret.toString();
	}
	
	// vector of vector of integers, each of which will become state
	// vector of map from lhs position to previous IPFinState coming from lower states
	// Vector of weights, coming from each rule traversed through
	// vector of viterbi weights, the best way to this potential state
	// vector of rtg structures, each of which corresponds to a new state
	private static class DstStructure {
		Vector<Vector<Integer>> seq;
		Vector<Vector<HashMap<Integer, IPFinState>>> map;
		Vector<Double> wgt;
		Vector<Double> vit;
		Vector<RtgStructure> rtg;
		public String toString() {
			return seq+"\n"+map+"\n"+wgt+"\n"+rtg;
		}
	}
	private static class WeightDstStructure extends DstStructure {
		
	}
	// elements that will be made into rtg rules: label, state sequence. join with DstStructure to get lhs and wgt
	private static class RtgStructure {
		int label;
		Vector<IPFinState> rhs;
		public RtgStructure(int l, Vector<IPFinState> r) { label=l; rhs=r; }
	}
	// given a head (state or label), a sequence of FinStates, a template, a transducer level, and a database of templates and semantics,
	// attempt to build an IPFinState recursively
	
	private static DstStructure getDsts(int head, Vector<IPFinState> seq, Vector<Integer> levelseq, double residual, IPTemplateAccess[] cascade, int level) {
		boolean debug = false;
		boolean outputdebug = false;
		// at this level, match seq, match state (if specified), match label (if available), giving a template
		// if state specified, we only have one.
		DstStructure ret = new DstStructure();
		ret.seq = new Vector<Vector<Integer>>();
		ret.map = new Vector<Vector<HashMap<Integer, IPFinState>>>();
		ret.rtg = new Vector<RtgStructure>();
		ret.vit = new Vector<Double>();
		ret.wgt = new Vector<Double>();

		// for all semantics within the template, reorder seq based on semantics, and getDsts at the next level if there is one
		if (debug) Debug.debug(debug, level, "About to get templates at level "+level+" with head "+head+
				" and level sequence "+levelseq+" and residual "+residual);
		for (IPRule rule : cascade[level].getTemplates(head, levelseq)) {
			if (debug) Debug.debug(debug, level, "Got "+rule);
			for (IPRule.Semantics sem : rule.getSemantics()) {
				// reorder, setting mapping if necessary
				if (sem.getLabel() >= 0) {
					Vector<IPFinState> newseq = reorderStateSeq(seq, sem.getSemChildren(), sem.getMap());
					if (debug) Debug.debug(debug, level, "reordered to form vector of length "+newseq.size()+": "+newseq);
					for (IPFinState seqmem : seq) {
						for (int key : seqmem.getMap(level).keySet()) {
							if (debug) Debug.debug(debug, level, seqmem+" has mapping "+key+" -> "+seqmem.getMap(level).get(key));
							newseq.set(key, seqmem.getMap(level).get(key));
						}
					}
					if (debug) Debug.debug(debug, level, "after bringing up insertions: "+newseq);

					// if we can go further, do so
					if (level < cascade.length-1) {
						// prepare integer sequence for next level
						// also calculate residual
						// have to do it like this because level 0 is treated differently.
						Vector<Integer> nextlevelseq = new Vector<Integer>();
						//double nextresidual = rule.semiring.ONE();
						for (IPFinState state : newseq) {
							nextlevelseq.add(state.getState(level+1));
							//if (debug) Debug.debug(debug, level, "Incorporating weight of "+state.getWeight()+" for "+state+" in next residual");
							//nextresidual = rule.semiring.times(nextresidual, state.getWeight());
						}
						if (debug) Debug.debug(debug, level, "Going to level "+(level+1)+" with label "+sem.getLabel()+", real seq "+newseq+", and seq "+nextlevelseq);
						DstStructure recursestruct = getDsts(sem.getLabel(), newseq, nextlevelseq, residual, cascade, level+1);
						int i = 0;
						for (Vector<Integer> partial : recursestruct.seq) {
							Vector<Integer> complete = new Vector<Integer>(partial);
							// add in this level's state to top
							complete.add(0, rule.getState());
							ret.seq.add(complete);
							// pass map along, adding in an empty level
							Vector<HashMap<Integer, IPFinState>> map = new Vector<HashMap<Integer, IPFinState>>(recursestruct.map.get(i));
							map.add(0, new HashMap<Integer, IPFinState>());
							ret.map.add(map);
							// multiply recursively obtained weight times the weight of the rule used here 
							double weight = rule.semiring.times(recursestruct.wgt.get(i), sem.getWeight());

							// viterbi component is rule weight and previous rule weights only (no residuals used here)
							double viterbi = rule.semiring.times(recursestruct.vit.get(i), sem.getVitWeight());
							// residual weight is not included -- it gets used at the end of a sequence only!
							if (debug) Debug.debug(debug, level, "At level "+level+", rule is "+sem.getWeight());
							ret.wgt.add(weight);
							ret.vit.add(viterbi);
							ret.rtg.add(recursestruct.rtg.get(i));

							i++;
						}
					}
					else {
						ret.rtg.add(new RtgStructure(sem.getLabel(), newseq));

						if (debug||outputdebug) Debug.debug(debug||outputdebug, "Should export rhs of "+sem.getLabel()+"( "+newseq+" ) with result and weight of "+sem.getWeight());
						// create base items
						Vector<Integer> base = new Vector<Integer>();
						base.add(0, rule.getState());
						ret.seq.add(base);
						Vector<HashMap<Integer, IPFinState>> mapvec = new Vector<HashMap<Integer, IPFinState>>();
						mapvec.add(new HashMap<Integer, IPFinState>());
						ret.map.add(mapvec);
						// multiply weight of the rule used here times any weight from
						// partially constructed bits (unfinished rules)
						double weight = rule.semiring.times(sem.getWeight(), residual);
						ret.wgt.add(weight);
						// viterbi is just the rule chain
						ret.vit.add(sem.getVitWeight());
					}
				}
				// no label here, so stop for now
				// not using map, so pass it up
				// pass up any descendants at the same time
				// not at the end, so insert null for rtg slot
				// pass along any residual weights
				else {
					if (debug) Debug.debug(debug, level, "Stopping at level "+level+" and saving map "+sem.getMap());
					Vector<Integer> base = new Vector<Integer>();
					base.add(0, rule.getState());
					ret.seq.add(base);
					HashMap<Integer, IPFinState> map = new HashMap<Integer, IPFinState>();
					for (int i = 0; i < seq.size(); i++) {
						// declared in this map
						if (sem.getMap().containsKey(i))
							map.put(sem.getMap().get(i), seq.get(i));
						// promoted up from virtual
						else
							seq.get(i).promoteMapMembers(map, level);
					}
					
					Vector<HashMap<Integer, IPFinState>> mapvec = new Vector<HashMap<Integer, IPFinState>>();
					mapvec.add(map);
					ret.map.add(mapvec);	
					ret.rtg.add(null);
					
					ret.wgt.add(residual);
					// viterbi is just the rule chain
					ret.vit.add(sem.getVitWeight());
				}
			}
		}
		return ret;
	}
	
	// recursively traverses IPFinState to insert mapped elements
	private void promoteMapMembers(HashMap<Integer, IPFinState> topmap, int level) {
		for (int key : map.get(level).keySet()) {
			IPFinState val = map.get(level).get(key);
			topmap.put(key, val);
			val.promoteMapMembers(topmap, level);
		}
		
	}
	
	// given a head (state or label), a sequence of FinStates, an already-calculated residual weight, a template, a transducer level, and a database of templates and semantics,
	// attempt to build an IPFinState recursively and the eventual rules
	private static WeightDstStructure getRuleDsts(int head, Vector<IPFinState> seq, Vector<Integer> levelseq, double residual, IPTemplateAccess[] cascade, int level) {
		boolean debug = false;
		boolean outputdebug = false;
		// at this level, match seq, match state (if specified), match label (if available), giving a template
		// if state specified, we only have one.
		WeightDstStructure ret = new WeightDstStructure();
		ret.seq = new Vector<Vector<Integer>>();
		ret.map = new Vector<Vector<HashMap<Integer, IPFinState>>>();
		ret.rtg = new Vector<RtgStructure>();
		ret.wgt = new Vector<Double>();

		// for all semantics within the template, reorder seq based on semantics, and getDsts at the next level if there is one
		if (debug) Debug.debug(debug, level, "About to get templates at level "+level+" with head "+head+
				" and level sequence "+levelseq+" and residual "+residual);
		for (IPRule rule : cascade[level].getTemplates(head, levelseq)) {
			if (debug) Debug.debug(debug, level, "Got "+rule);
			for (IPRule.Semantics sem : rule.getSemantics()) {
				// reorder, setting mapping if necessary
				if (sem.getLabel() >= 0) {
					Vector<IPFinState> newseq = reorderStateSeq(seq, sem.getSemChildren(), sem.getMap());
					if (debug) Debug.debug(debug, level, "reordered to form vector of length "+newseq.size()+": "+newseq);
					for (IPFinState seqmem : seq) {
						for (int key : seqmem.getMap(level).keySet()) {
							if (debug) Debug.debug(debug, level, seqmem+" has mapping "+key+" -> "+seqmem.getMap(level).get(key));
							newseq.set(key, seqmem.getMap(level).get(key));
						}
					}
					if (debug) Debug.debug(debug, level, "after bringing up insertions: "+newseq);

					// if we can go further, do so
					if (level < cascade.length-1) {
						// prepare integer sequence for next level
						// also calculate residual
						// have to do it like this because level 0 is treated differently.
						Vector<Integer> nextlevelseq = new Vector<Integer>();
						//double nextresidual = rule.semiring.ONE();
						for (IPFinState state : newseq) {
							nextlevelseq.add(state.getState(level+1));
							//if (debug) Debug.debug(debug, level, "Incorporating weight of "+state.getWeight()+" for "+state+" in next residual");
							//nextresidual = rule.semiring.times(nextresidual, state.getWeight());
						}
						if (debug) Debug.debug(debug, level, "Going to level "+(level+1)+" with label "+sem.getLabel()+", real seq "+newseq+", and seq "+nextlevelseq);
						WeightDstStructure recursestruct = getRuleDsts(sem.getLabel(), newseq, nextlevelseq, residual, cascade, level+1);
						int i = 0;
						for (Vector<Integer> partial : recursestruct.seq) {
							Vector<Integer> complete = new Vector<Integer>(partial);
							// add in this level's state to top
							complete.add(0, rule.getState());
							ret.seq.add(complete);
							// pass map along, adding in an empty level
							Vector<HashMap<Integer, IPFinState>> map = new Vector<HashMap<Integer, IPFinState>>(recursestruct.map.get(i));
							map.add(0, new HashMap<Integer, IPFinState>());
							ret.map.add(map);
							// multiply recursively obtained weight times the weight of the rule used here 
							// residual weight is not included -- it gets used at the end of a sequence only!
							if (debug) Debug.debug(debug, level, "At level "+level+", weight from below is "+recursestruct.wgt.get(i)+" and rule is "+sem.getWeight());
							double weight = rule.semiring.times(recursestruct.wgt.get(i), sem.getWeight());
							
							if (debug && level == 0) Debug.debug(debug, level, "Storing "+i+"th total weight for this state: "+weight);
							ret.wgt.add(weight);
							// pass the rtg along
							ret.rtg.add(recursestruct.rtg.get(i));
							i++;
						}
					}
					else {
						
						ret.rtg.add(new RtgStructure(sem.getLabel(), newseq));
						if (debug||outputdebug) Debug.debug(debug||outputdebug, "Should export rhs of "+sem.getLabel()+"( "+newseq+" ) with result and weight of "+sem.getWeight());
						// create base items
						Vector<Integer> base = new Vector<Integer>();
						base.add(0, rule.getState());
						ret.seq.add(base);
						Vector<HashMap<Integer, IPFinState>> mapvec = new Vector<HashMap<Integer, IPFinState>>();
						mapvec.add(new HashMap<Integer, IPFinState>());
						ret.map.add(mapvec);
						// multiply weight of the rule used here times any weight from
						// partially constructed bits (unfinished rules)
						double weight = rule.semiring.times(sem.getWeight(), residual);
						ret.wgt.add(weight);
					}
				}
				// no label here, so stop for now
				// not using map, so pass it up
				// not at the end, so insert null for rtg slot
				// pass along any residual weights
				else {
					if (debug) Debug.debug(debug, level, "Stopping at level "+level+" and saving map "+sem.getMap());
					Vector<Integer> base = new Vector<Integer>();
					base.add(0, rule.getState());
					ret.seq.add(base);
					HashMap<Integer, IPFinState> map = new HashMap<Integer, IPFinState>();
					for (int i : sem.getMap().keySet()) {
						map.put(sem.getMap().get(i), seq.get(i));
					}
					Vector<HashMap<Integer, IPFinState>> mapvec = new Vector<HashMap<Integer, IPFinState>>();
					mapvec.add(map);
					ret.map.add(mapvec);
					ret.rtg.add(null);
					ret.wgt.add(residual);
					
				}
			}
		}
		return ret;
	}
	
	// given a mapping of current pos -> new pos and a vector of anything, map things to new pos and return
	private static <T> Vector<T> reorderStateSeq(Vector<T> invec, int outsize, HashMap<Integer, Integer> map)  {
		boolean debug = false;
		if (debug) Debug.debug(debug, "Reordering "+invec+" on "+map);
		Vector<T> outvec = new Vector<T>(outsize);
		outvec.setSize(outsize);
		for (int key : map.keySet()) {
			outvec.set(map.get(key), invec.get(key));
		}
		return outvec;
	}

}
