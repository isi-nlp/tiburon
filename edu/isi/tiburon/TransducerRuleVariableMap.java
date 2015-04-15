package edu.isi.tiburon;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Vector;


// transducer rule variable map - basically a complex set of hash maps from
// variable locations in lhs and rhs of transducer rule. also maps the symbols that
// represent these variables

// map is created bfeore lhs or rhs. It is filled first when lhs is created and then further filled
// when rhs is created. If it is invalid, there is an error in the lhs or rhs

public class TransducerRuleVariableMap {

	// keep track of variable order
	private int nextVariableIndex;
	// map the order in which the variable appears to its symbol
	private HashMap<Integer, Symbol> lhsSyms;
	// map the symbol of the variable to its appearance in the lhs tree
	private HashMap<Symbol, TransducerLeftTree> lhsTrees;
	// map the lhs appearance to the set of appearances in the rhs tree (1:many)
	private HashMap<TransducerLeftTree, HashSet<TransducerRightSide>> lhsRhs;
	// map an rhs appearance to the lhs appearance (1:1 but not onto)
	private HashMap<TransducerRightSide, TransducerLeftTree> rhsLhs;

	// rhs variables, ordered by the order they appear 
	private ArrayList<TransducerRightSide> rhsInOrder;
	// the set of variable-enabled rhs trees. mostly used for validity checks
	//    private HashSet rhsTrees;

	// the set of all states;
	//    private HashSet<Symbol> allStates;

	// the states, in the order we come upon them
	//    private Vector<Symbol> stateSeq;

	// the set of states a lhs can lead to. Calculated en masse on demand, to save space
	private HashMap lhsNextStates;

	// a logic error in the map. True unless detected otherwise
	private boolean valid;
	private StringBuffer invalidmsg;

	private boolean isCopying = false;
	private boolean isCopyingSet = false;

	private boolean isDeleting = false;
	private boolean isDeletingSet = false;

	public TransducerRuleVariableMap() {
		nextVariableIndex = 0;
		valid = true;
		invalidmsg = new StringBuffer();
		isCopyingSet = false;
		isDeletingSet = false;
	}

	// seed a trvm with an already-built TLT
	public TransducerRuleVariableMap(TransducerLeftTree tlt) {
		this();
		traverse(tlt);
	}

	// recursively walk down a TLT and add variable information
	private void traverse(TransducerLeftTree tlt) {
		if (tlt.hasVariable())
			addLHS(tlt);
		for (int i = 0; i < tlt.getNumChildren(); i++)
			traverse(tlt.getChild(i));
	}

	// recursively walk down a TRT and add variable information
	void traverse(TransducerRightTree trt) {
		if (trt.hasVariable())
			addRHS(trt, true);
		for (int i = 0; i < trt.getNumChildren(); i++)
			traverse(trt.getChild(i));
	}

	// recursively walk down a TRS and add variable information
	void traverse(TransducerRightString trs) {
		if (trs.hasVariable())
			addRHS(trs, true);
		if (trs.next() != null)
			traverse(trs.next());
	}

	// check validity
	public boolean isValid() {
		return valid;
	}
	// useful for debugging: check reason behind invalidity
	public String getInvalidMessage() {
		return invalidmsg.toString();
	}
	// check for copying property: if lhs has more than one rhs
	public boolean isCopying() {
		if (isCopyingSet)
			return isCopying;
		isCopyingSet = true;
		isCopying = false;
		if (lhsRhs != null) {
			for (HashSet<TransducerRightSide> hs : lhsRhs.values()) {
				if (hs.size() > 1) {
					isCopying = true;
					break;
				}
			}
		}
		return isCopying;
	}

	// check for deleting property: if lhs has zero rhs
	public boolean isDeleting() {
		if (isDeletingSet)
			return isDeleting;
		isDeletingSet = true;
		isDeleting = false;
		if (lhsRhs != null) {
			for (HashSet<TransducerRightSide> hs : lhsRhs.values()) {
				if (hs.size() == 0) {
					isDeleting = true;
					break;
				}
			}
		}
		return isDeleting;
	}


	// typically added to by parent of lhs during construction
	void addLHS(TransducerLeftTree t) {
		isCopyingSet = false;
		isDeletingSet = false;
		// there must be a variable there
		if (!t.hasVariable()) {
			valid = false;
			invalidmsg.append(t.toString()+" was an added lhs but has no variable\n");
			return;
		}
		if (lhsTrees == null)
			lhsTrees = new HashMap<Symbol, TransducerLeftTree>();
		if (lhsRhs == null)
			lhsRhs = new HashMap<TransducerLeftTree, HashSet<TransducerRightSide>>();
		// variable must be unused
		if (lhsTrees.containsKey(t.getVariable())) {
			valid = false;
			invalidmsg.append(t.toString()+" was an added lhs but repeated the use of its variable\n");
			return;
		}
		// tree must be unused
		if (lhsRhs.containsKey(t)) {
			valid = false;
			invalidmsg.append(t.toString()+" was an added lhs but has been added as an lhs before\n");
			return;
		}

		lhsTrees.put(t.getVariable(), t);
		if (lhsSyms == null)
			lhsSyms = new HashMap<Integer, Symbol>();
		lhsSyms.put(nextVariableIndex++, t.getVariable());
		lhsRhs.put(t, new HashSet<TransducerRightSide>());
	}

	// typically added to by parent of rhs during construction
	// if lhs references a symbol it is given to rhs at this time
	// otherwise null is given
	// TODO: add checks for prohibitions on certain kinds of relations (L, R, etc.)
	Symbol addRHS(TransducerRightSide t, boolean normalOrder) {
		isDeletingSet = false;
		// there must be a variable and a state there
		if (!t.hasVariable() || !t.hasState()) {
			valid = false;
			invalidmsg.append(t.toString()+" was an added rhs but doesn't have either a variable or state\n");
			return null;
		}
		// variable must be *used*
		if (lhsTrees == null || !lhsTrees.containsKey(t.getVariable())) {
			valid = false;
			invalidmsg.append(t.toString()+" was an added rhs but contains a variable not in the lhs\n");
			invalidmsg.append("variable is "+t.getVariable()+"\n");
			invalidmsg.append("lhs variable set is "+lhsTrees+"\n");
			return null;
		}
		TransducerLeftTree lt = lhsTrees.get(t.getVariable());
		// if tree is unused, add it
		//	if (!rhsTrees.contains(t)) {
		//	    rhsTrees.add(t);
		//	allStates.add(t.getState());
		if (rhsLhs == null)
			rhsLhs = new HashMap<TransducerRightSide, TransducerLeftTree>();
		rhsLhs.put(t, lt);
		//	}
		//	stateSeq.add(t.getState());

		lhsRhs.get(lt).add(t);
		if (rhsInOrder == null)
			rhsInOrder = new ArrayList<TransducerRightSide>() ;
		if (normalOrder)
			rhsInOrder.add(t);
		else
			rhsInOrder.add(0, t);
		return lt.getLabel();
	}

	// used for implicitly setting the rhs label
	// valid check is left to the TransducerRightSide calling method
	Symbol getLHSLabel(TransducerRightSide t) {
		if (rhsLhs == null)
			rhsLhs = new HashMap<TransducerRightSide, TransducerLeftTree>();
		return rhsLhs.get(t).getLabel();
	}

	// used for getting a pure lhs
	TransducerLeftTree getLHS(TransducerRightSide t) {
		// sanity check
		// 	Iterator a = rhsLhs.keySet().iterator();
		// 	while (a.hasNext()) {
		// 	    TransducerRightSide r = (TransducerRightSide)a.next();
		// 	    Debug.debug(true, "rhslhs has "+r.toString());
		// 	    TransducerLeftTree l = (TransducerLeftTree)rhsLhs.get(r);
		// 	    Debug.debug(true, "it goes to "+l.toString());
		// 	}
		if (rhsLhs == null)
			rhsLhs = new HashMap<TransducerRightSide, TransducerLeftTree>();
		return rhsLhs.get(t);
	}

	// used for getting a rhs in the order in which it appears
	public ArrayList<TransducerRightSide> getRHSInOrder() {
		if (rhsInOrder == null)
			rhsInOrder = new ArrayList<TransducerRightSide>();
		return rhsInOrder;
	}


	// used for determining set of rhs a lhs can exist in
	HashSet<TransducerRightSide> getRHS(TransducerLeftTree t) {
		if (lhsRhs == null)
			lhsRhs = new HashMap<TransducerLeftTree, HashSet<TransducerRightSide>>();
		return lhsRhs.get(t);
	}

	// used for determining set of rhs a variable can exist in
	HashSet<TransducerRightSide> getRHSByVariable(Symbol var) throws UnusualConditionException {
		if (lhsTrees != null && lhsTrees.containsKey(var))
			return getRHS(lhsTrees.get(var));
		else
			throw new UnusualConditionException("No tree maps to variable symbol "+var);
	}

	// used for determining set of rhs a variable can exist in
	Symbol getVariableByIndex(int i) throws UnusualConditionException {
		if (lhsSyms != null && lhsSyms.containsKey(i))
			return lhsSyms.get(i);
		else
			throw new UnusualConditionException("No variable in position "+i);
	}


	// used for determining set of states a variable can exist in
	// info here is memoized and determined en masse
	// the rationale is if one variable of a rule is checked they all will be
	// this can be modified later if the assumption is wrong
	HashSet getNextStates(TransducerLeftTree t) {
		if (lhsNextStates == null) {
			//	    Debug.debug(true, "Filling getNextStates");
			lhsNextStates = new HashMap();
			if (lhsRhs != null) {
				for (TransducerLeftTree l : lhsRhs.keySet()) {
					HashSet states = new HashSet();
					for (TransducerRightSide r : lhsRhs.get(l)) {
						states.add(r.getState());
					}
					//		Debug.debug(true, "Adding "+states.size()+" members for "+l.toString());
					lhsNextStates.put(l, states);
				}
			}

		}
		return (HashSet)lhsNextStates.get(t);
	}

	// get all the states that are represented in this rule
	// and their count
	//     HashSet<Symbol> getStates() {
	// 	return allStates;
	//     }

	// get the states we can go to, in their appropriate order
	//     Vector<Symbol> getStateSeq() {
	// 	return stateSeq;
	//     }

	// get all the TLTs that have a RHS mapped
	Iterable<TransducerLeftTree> getLHSKeys() {
		if (lhsRhs == null)
			lhsRhs = new HashMap<TransducerLeftTree, HashSet<TransducerRightSide>>();
		return lhsRhs.keySet();
	}

	public static void main(String argv[]) {
		
		Vector<TransducerRuleVariableMap> v = new Vector<TransducerRuleVariableMap>();
		int n = Integer.parseInt(argv[0]);
		try {
			for (int i = 0; i < n; i++) {
				v.add(new TransducerRuleVariableMap());
			}
		} catch (Exception e)  {
			System.out.println(e.toString());
		}
	}
}
