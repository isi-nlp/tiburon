
package edu.isi.tiburon;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.StreamTokenizer;
import java.util.Date;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;

import gnu.trove.TIntHashSet;
import gnu.trove.TObjectIntHashMap;

// TODO: this is just a syntactic sugar overlay of tree-tree training and is 
// probably not ideal. I'm doing this for quickness!
public class RuleSetTraining {

	// read a list of items from file
	// format of a record is:
	// count (optional)
	// item

	// determine what you're reading using file differentiation

	// all items are written as temporary files and re-loaded
	// as needed (probably a bad idea, though...)

	/** Comment character for items */
	static final public int COMMENT = '%';


	// return vector contains a File and an Integer.
	static public Vector readItemSet(BufferedReader br,
			boolean isWeight, boolean isTree, Semiring sr)  throws DataFormatException, 
			IOException {
		Vector v = new Vector();
		boolean debug = false;
		StreamTokenizer st = new StreamTokenizer(br);
		st.resetSyntax();
		st.eolIsSignificant(false);
		//st.whitespaceChars(0, 32);
		st.wordChars(32,  '\u00FF');
		st.whitespaceChars(10, 13);
		st.commentChar(COMMENT);


		// the output file
		File of = null;
		of = File.createTempFile("itemset", "tmp");
		of.deleteOnExit();
		ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(of));
		int counter = 0;
		Date readTime = new Date();
		while (st.nextToken() != StreamTokenizer.TT_EOF) {
			// first get a number
			double count;
			if (isWeight) {
				if (st.ttype != StreamTokenizer.TT_WORD)
					throw new DataFormatException("Expected word but got "+st.ttype);
				if (debug) Debug.debug(debug, "Reading count "+st.sval);
				count = sr.convertFromReal(Double.parseDouble(st.sval));
				st.nextToken();
			}
			else
				count = sr.convertFromReal(1);
			if (isTree) {
				if (debug) Debug.debug(debug, "Reading tree ["+st.sval+"]");
				TreeItem item = new TreeItem(new StringBuffer(st.sval));
				item.semiring =sr;
				if (item.label == null) {
					if (debug) Debug.debug(debug, "Reached null item");
					continue;
				}
				item.weight = count;
				oos.writeObject(item);
				oos.reset();
				if (debug) Debug.debug(debug, "Writing item "+counter+"; "+item.toString());
			}
			else {
				if (debug) Debug.debug(debug, "Reading string ["+st.sval+"]");
				TrainingString item = new TrainingString(new StringBuffer(st.sval));
				if (item == null || item.isEpsilon()) {
					if (debug) Debug.debug(debug, "Reached null item");
					continue;
				}
				item.weight = count;
				oos.writeObject(item);
				oos.reset();
				if (debug) Debug.debug(debug, "Writing item "+counter+"; "+item.toString());
			}
			if (debug) Debug.debug(debug, "Wrote file");
			counter++;
			if (counter >= 10000 && counter % 10000 == 0) {
				Date pause = new Date();
				long lapse = pause.getTime() - readTime.getTime();
				readTime = pause;
				Debug.prettyDebug("Read "+counter+" training examples: "+lapse);
			}
		}
		oos.close();
		v.add(of);
		v.add(new Integer(counter));
		if (debug) Debug.debug(debug, "Done reading "+counter+" items");
		return v;
	}




	// jon's train algorithm - set weights on rules
	// TODO: add normalization function
	// TODO: add prior counts
	static public RuleSet train(RuleSet inrs, 
			File setFile,
			int setSize,
			File derivFile,
			double epsilon,
			int maxit,
			int timeLevel,
			boolean wasNorm) throws UnusualConditionException {
		boolean debug = false;
		Semiring semiring = inrs.getSemiring();
		// don't modify old rs
		RuleSet rs;
		if (inrs instanceof RTGRuleSet)
			rs = new RTGRuleSet(inrs);
		else if (inrs instanceof CFGRuleSet)
			rs = new CFGRuleSet(inrs);
		else
			throw new UnusualConditionException("Attempted to train a RuleSet that was neither RTG nor CFG. Probable programming error");

		// log likelihood change
		double delta = epsilon;
		double lastL = Double.NEGATIVE_INFINITY;
		int itno = 0;
		//  	long beforeUsage = Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();
		//  	Debug.prettyDebug("Before training: heap at "+beforeUsage);
		// em core
		// for calculating cross-entropy
		int totalNodes = 0;
		boolean totalNodesSet = false;
		while ((epsilon == 0 || delta >= epsilon) && itno < maxit) {
			// TODO - use dirichilet priors
			// TODO - not have to do this every iteration
			// for now, if the rule isn't in the hashmap, count is assumed to be 0

			Date preIt = new Date();
			if (debug) Debug.debug(debug, "Iteration "+itno);
			//	    debug = tempdebug;
			Hashtable<Rule, Double> counts = new Hashtable<Rule, Double>();
			// TODO - use variable method for Z?
			Hashtable<Symbol, Double> Z = new Hashtable<Symbol, Double>();
			double L = semiring.ONE();

			ObjectInputStream setois = null;
			ObjectInputStream drsois = null;
			try {
				setois = new ObjectInputStream(new FileInputStream(setFile));
				drsois = new ObjectInputStream(new FileInputStream(derivFile));
			}
			catch (IOException e) {
				System.err.println("Problem in training while trying to open the info files: "+e.getMessage());
				System.exit(1);
			} 
			// estimate step
			for (int setSizeCounter = 0; setSizeCounter < setSize; setSizeCounter++) {
				// read in the training file and derivation forest
				DerivationRuleSet drs = null;
				Trainable item = null;
				try {
					item = (Trainable)setois.readObject();
					drs = (DerivationRuleSet)drsois.readObject();
					if (drs == null)
						continue;
					drs.revive(inrs, semiring);
				}
				catch (IOException e) {
					System.err.println("Problem while trying to read in a derivation and item "+e.getMessage());
					System.exit(1);
				}
				catch (ClassNotFoundException e) {
					System.err.println("Class-casting problem while trying to read in a derivation and item "+e.getMessage());
					System.exit(1);
				}
				if (drs == null || item == null) {
					System.err.println("Reading of training example completed, but objects are null; continuing...");
					continue;
				}
				if (debug) Debug.debug(debug, "item is "+item);
				// calculateWeights is based on the rule, so changing that changes the dr

				drs.calculateWeights();

				Iterator drsruleit = drs.getRules().iterator();
				// collect counts
				while (drsruleit.hasNext()) {
					DerivationRule dr = (DerivationRule)drsruleit.next();
					if (dr.isVirtual()) {
//						if (debug) Debug.debug(debug, "Ignoring estimation step for virtual rule");
						continue;
					}
					if (debug)System.err.print("Gamma for "+dr.toString()+" is alpha "+drs.getAlpha(dr.getLHS())+" x weight "+dr.getWeight());
					double gamma = semiring.times(drs.getAlpha(dr.getLHS()), dr.getWeight());
					int [] drrhs = drs.getLeafChildren(dr);
					for (int i = 0; i < drrhs.length; i++) {
						int s = drrhs[i];
						try {
							gamma = semiring.times(gamma, drs.getBeta(s));
							if (debug) System.err.print(" x beta "+drs.getBeta(s));
						}
						catch (UnusualConditionException e) {
							Debug.debug(true, "Unusual Condition getting beta "+i+" for rule "+dr.toString()+": "+e.getMessage());
						}
					}
					if (debug) System.err.print(" = "+gamma+"\n");
					
					Rule rule = dr.getAltLabel();
					// the new value for the count of this rule
					double newCount = semiring.ZERO();
					
					
					if (counts.containsKey(rule)) {
						if (debug) Debug.debug(debug, "Increasing prior count prediction of "+counts.get(rule));
						newCount = semiring.plus(newCount, counts.get(rule));
					}
					else 
						if (debug) Debug.debug(debug, "First time for "+rule.toString()+" this iteration");
					double startBeta = semiring.ZERO();
					try {
						startBeta = drs.getBeta(drs.getStartState());
					}
					catch (UnusualConditionException e) {
						Debug.debug(true, "Unusual Condition getting start beta: "+e.getMessage());
					}
					// the portion we're adding. This is so we can properly increase Z
					double newCountPortion = semiring.times(item.getWeight(), 
									(semiring.times(gamma,	semiring.inverse(startBeta))));
					newCount = semiring.plus(newCount, newCountPortion);
					if (debug) Debug.debug(debug, "Estimating counts of "+rule.toString()+" at "+newCount);
					counts.put(rule, newCount);
					// conditional is the bucket of z we're storing in
					Symbol cond = dr.getAltLabel().getLHS();
					if (!Z.containsKey(cond))
						Z.put(cond, newCountPortion);
					else
						Z.put(cond, semiring.plus(Z.get(cond), newCountPortion));
					//		    Debug.prettyDebug("Setting Z of "+cond.toString()+" to "+((Double)Z.get(cond)).doubleValue());
					if (debug) Debug.debug(debug, "Adding "+newCountPortion+" to Z of "+cond);
					if (debug) Debug.debug(debug, "Setting Z of "+cond.toString()+" to "+Z.get(cond));

					//		    Z += newCount;
				}
				double startBeta = semiring.ZERO();
				try {
					startBeta = drs.getBeta(drs.getStartState());
				}
				catch (UnusualConditionException e) {
					Debug.debug(true, "Unusual Condition getting start beta after iteration for "+item.toString()+": "+e.getMessage());
				}
				if (debug) Debug.debug(debug, "done with estimation");
				if (debug) Debug.debug(debug, "Likelihood of "+drs.getStartState()+" is "+startBeta);
				//		Debug.prettyDebug("Adding "+startBeta+" * "+item.getWeight());
				L = semiring.times(L, semiring.times(startBeta, item.getWeight()));
				if (!totalNodesSet)
					totalNodes += (item.getSize()*semiring.internalToPrint(item.getWeight()));
				drs = null;
				if (debug) {
					Debug.debug(debug, "Rules:");
					for (Rule crule : counts.keySet())
						Debug.debug(debug, crule+" at "+counts.get(crule));
					Debug.debug(debug, "Zs:");
					for (Symbol zsym : Z.keySet())
						Debug.debug(debug, zsym+" at "+Z.get(zsym));
				}
			}
			totalNodesSet=true;
			if (debug) Debug.debug(debug, "After estimation, L is at "+L);
			// cross entropy is -ln(p(corpus)). L is p(corpus) if "real", -ln(p(corpus) if of log form.
			double crossentropy;
			double corpusprob;
			if (semiring.ZERO() < semiring.ONE())
				corpusprob = -Math.log(L);
			else
				corpusprob = L;
			crossentropy = corpusprob/totalNodes;
			String prefix;
			if (itno == 0)
				if (wasNorm)
					prefix = "Cross entropy with normalized initial weights is ";
				else
					prefix = "Cross entropy with non-normalized initial weights is ";
			else
				prefix = "Cross entropy after "+itno+" iterations is ";
			Debug.prettyDebug(prefix+crossentropy+"; corpus prob is e^"+(-corpusprob));	    
			// maximize step
			Iterator rsruleit = rs.getRules().iterator();
			while (rsruleit.hasNext()) {
				Rule rule = (Rule)rsruleit.next();
				// TODO: make sure these rules are reflected in the rules that are stored as indices for count
				if (debug) System.err.print("Changing "+rule.toString()+" to ");
				Double rulecount = (Double)counts.get(rule);
				if (rulecount == null || semiring.betteroreq(semiring.ZERO(), rulecount.doubleValue())) {
					rule.setWeight(semiring.ZERO());
					if (debug) Debug.debug(debug, semiring.ZERO()+" (null rule)");
				}
				else {
					rule.setWeight(semiring.times(rulecount.doubleValue(), semiring.inverse(Z.get(rule.getLHS()))));
					if (debug) Debug.debug(debug, rulecount.doubleValue()+" / "+Z.get(rule.getLHS()).doubleValue()+" = "+rule.getWeight());
				}
			}

			delta = (L - lastL)/Math.abs(L);
			if (debug) Debug.debug(debug, "L is "+L+", lastL is "+lastL);
			if (debug) Debug.debug(debug, "Iteration changed L by "+delta);
			lastL = L;
			Date postIt = new Date();
			Debug.dbtime(timeLevel, 2, preIt, postIt, "perform iteration "+itno);
			itno++;
			if (debug) Debug.debug(debug, "At end of iteration, rule set is:\n"+rs);

		}
		if (debug) Debug.debug(debug, "Final rule set is:\n"+rs);
		return rs;
	}



	// get all the rule sets given a file pointing to items, write them to a file, and return a pointers to the file.
	static public File getAllDerivationRuleSets(RuleSet inrs, int setSize, File setFile, String derivFileName, int timeLevel) {
		boolean debug = false;
		Hashtable derivMap = new Hashtable();
		if (debug) Debug.debug(debug, "Calculating derivation rule sets for "+setSize+" items");
		int goodItems = 0;
		int badItems = 0;
		ObjectInputStream tsois = null;
		ObjectOutputStream dsoos = null;
		File of = null;
		try {
			tsois = new ObjectInputStream(new FileInputStream(setFile));
			if (derivFileName == null) {
				of = File.createTempFile("drs", "tmp");
				of.deleteOnExit();
			}
			else
				of = new File(derivFileName);
			dsoos = new ObjectOutputStream(new FileOutputStream(of));
		}
		catch (IOException e) {
			System.err.println("IO Problem preparing to get all derivation rule sets: "+e.getMessage());
			System.exit(1);
		}

		// choose between tree and strings based on rule set class
		boolean isTree = (inrs instanceof RTGRuleSet);
		for (int i = 0; i < setSize; i++) {
			DerivationRuleSet drs = null;
			Object item = null;
			try {
				item = tsois.readObject();
			}
			catch (IOException e) {
				System.err.println("Problem while trying to get derivation forest of training example: "+
						(i+1)+" of "+setSize+": "+e.getMessage());
				System.exit(1);
			}
			catch (ClassNotFoundException e) {
				System.err.println("Class-casting problem while trying to get derivation forest of training example: "+
						(i+1)+" of "+setSize+": "+e.getMessage());
				System.exit(1);
			}
			if (debug) Debug.debug(debug, "Attempting to derive "+i+" of "+setSize+": "+item.toString());
			Date preDerivTime = new Date();
			if (isTree) {
				drs = deriv(inrs, (TreeItem)item);
			}
			else {
				drs = deriv(inrs, (TrainingString)item);
			}
			Date postDerivTime = new Date();
			Debug.dbtime(timeLevel, 3, preDerivTime, postDerivTime, "calculate derivation rule set");

			if (drs == null) {
				badItems++;
				Debug.prettyDebug("Warning: Item "+item.toString()+" could not be explained by transducer. Removing from training set");
				Debug.prettyDebug("So far: "+badItems+" bad items, and "+goodItems+" good items");
			}
			else {
				goodItems++;
				drs.pruneUseless();
				if (debug) Debug.debug(debug, "Done pruning");
				drs.calculateWeights();
				if (debug) Debug.debug(debug, "Derivation Rule Set for "+item.toString()+" has "+drs.getNumRules()+" rules");
				//		if (debug) Debug.debug(debug, drs.toString()+"\n");
			}
			try {
				dsoos.writeObject(drs);
				dsoos.reset();
			}
			catch (IOException e) {
				System.err.println("Problem while trying to write derivation of "+
						item.toString()+" to file: "+e.getMessage());
				System.exit(1);
			}
			drs = null;
			// 	    if (debug) Debug.debug(debug, "Memo now has "+memo.size()+" entries");
			// 	    long afterwrite = Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();
			// 	    if (debug) Debug.debug(debug, "after write: heap at "+afterwrite);
			// TODO: if not done already, prune useless deriv weights
		}
		try {
			tsois.close();
			dsoos.close();
		}
		catch (IOException e) {
			System.err.println("Problem in training while trying to open the info files: "+e.getMessage());
			System.exit(1);
		}
		return of;
	}



	// jon's deriv algorithm for trees - build one drs given a rs and a item

	// NOTE: Even though this is for "Items", it only actually runs on TreeItems!!
	static public DerivationRuleSet deriv(RuleSet rs, Item i) {
		boolean debug = false;
		TIntHashSet states = new TIntHashSet();
		HashSet rules = new HashSet();
		// memoize production decisions
		Hashtable memo = new Hashtable();
		// track unique items in this derivation and represent them as ints in the drs
		TObjectIntHashMap itemMap = new TObjectIntHashMap();
		StateItem.resetNextIndex();
		StateItem startState = new StateItem(rs.getStartState(), i, 0, i.getLeaves().length);
		itemMap.put(startState, StateItem.getNextIndex());
		DerivationRuleSet drs = null;
		if (produce(rs, startState, states, rules, memo, itemMap, debug, 0))
			drs = new DerivationRuleSet(itemMap.get(startState), states, rules, rs.getSemiring());
		return drs;
	}

	// jon's deriv algorithm for strings - build one drs given a rs and a item

	static public DerivationRuleSet deriv(RuleSet rs, TrainingString i) {
		boolean debug = false;
		if (debug) Debug.debug(debug, " Attempting to derive "+i.toString());	   
		TIntHashSet states = new TIntHashSet();
		HashSet rules = new HashSet();
		// memoize production decisions
		Hashtable memo = new Hashtable();
		// track unique items in this derivation and represent them as ints in the drs
		TObjectIntHashMap itemMap = new TObjectIntHashMap();
		VariableCluster.resetNextIndex();
		VariableCluster startState = new VariableCluster(rs.getStartState(), 
				null, 0, 0, 
				i, i.getStartIndex(), i.getEndIndex());
		if (debug) Debug.debug(debug, " Created start state "+startState.toString());	   
		itemMap.put(startState, VariableCluster.getNextIndex());
		DerivationRuleSet drs = null;
		if (produce(rs, startState, states, rules, memo, itemMap, debug, 0))
			drs = new DerivationRuleSet(itemMap.get(startState), states, rules, rs.getSemiring());
		return drs;
	}





	// jon's produce algorithm - return true if something was created. Also add to the hash sets passed along
	// build DerivationRules, which are lhs states, rhs transducer rule, and a list of rhs states
	// as it goes, add the states too

	// memoization: memo maps qi -> Boolean
	static public boolean produce(RuleSet rs, StateItem qi, TIntHashSet stateSet, HashSet ruleSet,
			Hashtable memo, TObjectIntHashMap itemMap) {
		return produce(rs, qi, stateSet, ruleSet, memo, itemMap, false, 0);
	}

	static public boolean produce(RuleSet rs, StateItem qi, TIntHashSet stateSet, HashSet ruleSet,
			Hashtable memo, TObjectIntHashMap itemMap, boolean debug, int indent) {
		// memoization check
		if (memo.containsKey(qi)) {
			Boolean entry = (Boolean)memo.get(qi);
			if (debug) Debug.debug(debug, ((entry.booleanValue()) ? "Success: " : "Fail: ")+qi.toString()+" (memo) ");
			return entry.booleanValue();
		}
		// potentially matching rules
		boolean foundMatch = false;
		Symbol q = qi.state();
		Item i = qi.item();
		if (debug) Debug.debug(debug, indent, "Attempting to produce: "+qi.toString());
		HashSet rsrules = null;
		try {
			rsrules = ((RTGRuleSet)rs).getTrainingRules(q, i);
		}
		catch (UnexpectedCaseException e) {
			System.err.println("Unexpected when getting training rules for "+qi.toString()+"; "+e.getMessage());
			return false;
		}
		Iterator rit = rsrules.iterator();
		while (rit.hasNext()) {
			RTGRule rule = (RTGRule)rit.next();
			if (debug) Debug.debug(debug, indent, "Attempting: "+qi.toString()+" with "+rule.toString());
			// get vector of state, initem pairs
			Vector v = rule.getPaths(qi, rs);
			// index version of v. for derivationrule
			int[] sv = new int[v.size()];
			// must be able to produce in children to continue;
			Iterator vit = v.iterator();
			boolean allOkay = true;
			int svidx = 0;
			while (vit.hasNext()) {
				StateItem st = (StateItem)vit.next();
				if (!produce(rs, st, stateSet, ruleSet, memo, itemMap, debug, indent+1)) {
					if (debug) Debug.debug(debug, indent, "Can't produce in "+st.toString());
					allOkay = false;
					break;
				}
				if (!itemMap.containsKey(st))
					itemMap.put(st, StateItem.getNextIndex());
				sv[svidx++] = itemMap.get(st);
			}
			if (!allOkay)
				continue;
			if (debug) Debug.debug(debug, indent, "Successfully produced "+qi.toString()+" with "+rule.toString());
			// create the derivation rule and states
			if (!itemMap.containsKey(qi))
				itemMap.put(qi, StateItem.getNextIndex());
			int qiidx = itemMap.get(qi);
			DerivationRule dr = new DerivationRule(qiidx, rule, sv, rs.getSemiring(), qi.align());
			if (debug) Debug.debug(debug, indent, "Rule created is "+dr.toString());
			ruleSet.add(dr);
			stateSet.add(qiidx);
			for (int j = 0; j < sv.length; j++)
				stateSet.add(sv[j]);
			foundMatch = true;
		}
		// archive the decision
		memo.put(qi, Boolean.valueOf(foundMatch));
		return foundMatch;
	}

	// jon's produce algorithm for CFG training - return true if something was created. Also add to the hash sets passed along
	// build DerivationRules, which are lhs states, rhs transducer rule, and a list of rhs states
	// as it goes, add the states too

	// memoization: memo maps qi -> Boolean
	static public boolean produce(RuleSet rs, VariableCluster qi, TIntHashSet stateSet, HashSet ruleSet,
			Hashtable memo, TObjectIntHashMap itemMap) {
		return produce(rs, qi, stateSet, ruleSet, memo, itemMap, false, 0);
	}

	static public boolean produce(RuleSet rs, VariableCluster qi, TIntHashSet stateSet, HashSet ruleSet,
			Hashtable memo, TObjectIntHashMap itemMap, boolean debug, int indent) {
		// memoization check
		if (memo.containsKey(qi)) {
			Boolean entry = (Boolean)memo.get(qi);
			//	    if (debug) Debug.debug(debug, indent, ((entry.booleanValue()) ? "Success: " : "Fail: ")+qi.toString()+" (memo) ");
			return entry.booleanValue();
		}
		// potentially matching rules
		boolean foundMatch = false;
		Symbol q = qi.getSymVariable(0);
		TrainingString i = qi.out();
		//	if (debug) Debug.debug(debug, indent, "Attempting to produce: "+qi.toString());
		HashSet rsrules = null;
		try {
			rsrules = ((CFGRuleSet)rs).getTrainingRules(q, i);
		}
		catch (UnexpectedCaseException e) {
			System.err.println("Unexpected when getting training rules for "+qi.toString()+"; "+e.getMessage());
			return false;
		}
		Iterator rit = rsrules.iterator();
		while (rit.hasNext()) {
			CFGRule tr = (CFGRule)rit.next();
			// it's possible this rule could help to produce the string in  a number 
			// of ways -- these are the various "p"s from the k/g journal paper.
			// alignments is a vector of sequences of consecutive variables (nonterminals) 
			// paired with literals from a training string
			// .(a vector of vectors). 
			// Each "cluster" variables needs to be processed
			// to see if we can accept the alignment
			Date preGetAlignments = new Date();

			// unlike the xrs training, getAllAlignments just returns a single vector
			Vector alignments = tr.getAllAlignments(qi, rs);
			//	    Debug.dbtime(3, preGetAlignments, "get alignments for "+qio.toString());
			Iterator alit = alignments.iterator();
			while (alit.hasNext()) {
				Vector v = (Vector)alit.next();

				// index version of v. for derivationrule
				int[] sv = new int[v.size()];
				Iterator vit = v.iterator();
				// 		if (debug) {
				// 		    System.err.print("Checking alignment [");
				// 		    while (vit.hasNext()) {
				// 			VariableCluster c = (VariableCluster)vit.next();
				// 			System.err.print("("+c.toString()+") ");
				// 		    }
				// 		    Debug.debug(true, "]");
				// 		    vit = v.iterator();
				// 		}

				boolean allOkay = true;
				int svidx = 0;
				// check each item of the alignment vector for a correct derivation. If a span is wrong, the whole
				// alignment is wrong. If a span is undecided, the whole alignment is undecided and must be deferred
				while (vit.hasNext()) {
					VariableCluster c = (VariableCluster)vit.next();
					if (!spanToSpan(rs, c, stateSet, ruleSet, memo, itemMap, indent+1)) {
						//			if (debug) Debug.debug(debug, indent, "Nonterminal Cluster "+c.toString()+" could not be resolved");
						allOkay = false;
						break;
					}
					// otherwise, make an int representation for the cluster
					else {
						if (!itemMap.containsKey(c)) {
							itemMap.put(c, VariableCluster.getNextIndex());
							//			    if (debug) Debug.debug(debug, indent, "Associating "+c.hashCode()+" with "+pairMap.get(c.hashCode()));
						}
						sv[svidx++] = itemMap.get(c);
					}
				}
				// check for failure at some point. if so, no need to make the rule
				if (!allOkay) {
					//		    if (debug) Debug.debug(debug, indent, "Fail: "+qio.toString()+" with "+tr.toString());
					continue;
				}
				// since we're creating at least one rule, we can declare this state true
				foundMatch = true;		
				//		if (debug) Debug.debug(debug, indent, "Success: "+qio.toString()+" with "+tr.toString());
				// create the derivation rule and states

				// if the head state has no int representation, create it 
				if (!itemMap.containsKey(qi)) {
					itemMap.put(qi, VariableCluster.getNextIndex());
					//		    if (debug) Debug.debug(debug, indent, "Associating "+qio.hashCode()+" with "+pairMap.get(qio.hashCode()));
				}
				int qiidx = itemMap.get(qi);
				// sanity check
				//		if (debug) Debug.debug(debug, indent, "About to create rule with head "+qio.toString()+" ("+qioidx+")");
				if (debug) {
					for (int j = 0; j< v.size(); j++) {
						VariableCluster clust = (VariableCluster)v.get(j);
						//			if (debug) Debug.debug(debug, indent, "Child "+j+" = "+clust.toString()+" ("+sv[j]+")");
					}
				}

				// actually build the rule
				DerivationRule dr = new DerivationRule(qiidx, tr, sv, rs.getSemiring(), qi.align());
				if (debug) Debug.debug(debug, indent, "Created new rule "+dr.toString()+" in produce");

				// archive the rule
				ruleSet.add(dr);
				stateSet.add(qiidx);
				for (int j = 0; j < sv.length; j++)
					stateSet.add(sv[j]);

			}
		}
		// archive the decision (it was already set to true, so no need to re-update unless it's now false)
		//	if (debug) Debug.debug(debug, indent, "deciding "+qio.toString()+": "+foundMatch);
		if (!foundMatch)
			memo.put(qi, Boolean.valueOf(foundMatch));
		return foundMatch;
	}


	// span to span - historical to the km journal paper. this is similar to 
	// produce but we have to do dynamic programming since
	// there may be more than one nonterminal in the nonterminal cluster. 
	// In such a case, we create a virtual node in the rule set.

	// memoization: memo maps c -> Boolean
	// itemMap: maps c hash (int) -> int. See produce for why c hash and not c itself.

	static public boolean spanToSpan(RuleSet trs, VariableCluster c, TIntHashSet stateSet, HashSet ruleSet, 
			Hashtable memo, TObjectIntHashMap itemMap, int indent) {
		boolean debug = false;
		// one non-terminal case - go back to produce
		if (c.numVariables() < 2)
			return produce(trs, new VariableCluster(c.getSymVariable(0), 
					null, 0, 0,
					c.getString(), c.getString().getStartIndex(), c.getString().getEndIndex()), 
					stateSet, ruleSet, memo, itemMap, debug, indent);
		// memoization check
		if (memo.containsKey(c)) {
			Boolean entry = (Boolean)memo.get(c);
			//	    if (debug) Debug.debug(debug, indent, entry.booleanValue()+": "+c.toString()+" (memo) ");
			return entry.booleanValue();
		}
		//	if (debug) Debug.debug(debug, indent, "deferring the decision by deciding true for "+c.toString());
		memo.put(c, Boolean.TRUE);
		//	if (debug) Debug.debug(debug, indent, "Attempting: "+c.toString());
		boolean foundMatch = false;
		// normal epsilon case - binarize, but string is always epsilon
		if (c.getString().isEpsilon()) {
			// produce on the first nonterminal the chosen subspan
			VariableCluster leftSide =  new VariableCluster(c.getSymVariable(0), c.getString());
			//	    if (debug) Debug.debug(debug, "Epsilon spantospan: trying to match "+leftSide.toString()+"...");
			// if left side not bad, try to get right
			if (produce(trs, leftSide, stateSet, ruleSet, memo, itemMap, debug, indent+1)) {
				VariableCluster rightSide = c.getSubCluster(0);
				//		if (debug) Debug.debug(debug, "Epsilon spantospan: ...and now trying the remaining "+rightSide.toString());
				// recurse on the remainder (there must be some - no one-state clusters get to here
				if (spanToSpan(trs, rightSide, stateSet, ruleSet, memo, itemMap, indent+1)) {
					if (debug) Debug.debug(debug, indent, "Success: "+c.toString()+"["+c.hashCode()+"] "+" with division ["+leftSide.toString()+"["+leftSide.hashCode()+"] "+", "+rightSide.toString()+"["+c.hashCode()+"] "+"]");
					// this cluster alignment is okay. 
					// build a virtual rule, adding each element to itemMap if necessary
					if (!itemMap.containsKey(c)) {
						itemMap.put(c, VariableCluster.getNextIndex());
						//			if (debug) Debug.debug(debug, "Associating "+c.hashCode()+" with "+itemMap.get(c.hashCode()));
					}
					if (!itemMap.containsKey(leftSide)) {
						itemMap.put(leftSide, VariableCluster.getNextIndex());
						//			if (debug) Debug.debug(debug, "Associating "+leftSide.hashCode()+" with "+itemMap.get(leftSide.hashCode()));
					}
					if (!itemMap.containsKey(rightSide)) {
						itemMap.put(rightSide, VariableCluster.getNextIndex());
						//			if (debug) Debug.debug(debug, "Associating "+rightSide.hashCode()+" with "+itemMap.get(rightSide.hashCode()));
					}
					int [] v = {itemMap.get(leftSide), itemMap.get(rightSide)};
					DerivationRule dr = new DerivationRule(itemMap.get(c), (Rule)null, v, trs.getSemiring());
					if (debug) Debug.debug(debug, indent, "Created new virtual rule "+dr.toString()+" in epsilon s2s");
					ruleSet.add(dr);
					stateSet.add(itemMap.get(c));
					stateSet.add(v[0]);
					stateSet.add(v[1]);
					foundMatch = true;
				}
			}
		}
		else {
			// normal case - binarize as follows: decide a dividing point. Try to match the first nonterm to that point and the rest to the rest.
			//	    if (debug) Debug.debug(debug, "String span is "+c.getString().getStartIndex()+", "+c.getString().getEndIndex());
			for (int i = c.getString().getEndIndex(); i >= c.getString().getStartIndex(); i--) {
				// produce on the first nonterminal the chosen subspan
				VariableCluster leftSide =  new VariableCluster(c.getSymVariable(0), c.getString().getSubString(c.getString().getStartIndex(), i));
				//		if (debug) Debug.debug(debug, "spantospan: trying to match "+leftSide.toString()+"...");
				if (!produce(trs, leftSide, stateSet, ruleSet, memo, itemMap, debug, indent+1))
					continue;
				VariableCluster rightSide = c.getSubCluster(i);
				//		if (debug) Debug.debug(debug, "spantospan: ...and now trying the remaining "+rightSide.toString());
				// recurse on the remainder (there must be some - no one-state clusters get to here)
				if (spanToSpan(trs, rightSide, stateSet, ruleSet, memo, itemMap, indent+1)) {
					//		    if (debug) Debug.debug(debug, indent, "Success: "+c.toString()+" with division ["+leftSide.toString()+", "+rightSide.toString()+"]");
					// build a virtual rule, adding each element to itemMap if necessary
					if (!itemMap.containsKey(c)) {
						itemMap.put(c, VariableCluster.getNextIndex());
						//			if (debug) Debug.debug(debug, "Associating "+c.hashCode()+" with "+itemMap.get(c.hashCode()));
					}
					if (!itemMap.containsKey(leftSide)) {
						itemMap.put(leftSide, VariableCluster.getNextIndex());
						//			if (debug) Debug.debug(debug, "Associating "+leftSide.hashCode()+" with "+itemMap.get(leftSide.hashCode()));
					}
					if (!itemMap.containsKey(rightSide)) {
						itemMap.put(rightSide, VariableCluster.getNextIndex());
						//			if (debug) Debug.debug(debug, "Associating "+rightSide.hashCode()+" with "+itemMap.get(rightSide.hashCode()));
					}
					int [] v = {itemMap.get(leftSide), itemMap.get(rightSide)};
					DerivationRule dr = new DerivationRule(itemMap.get(c), (Rule)null, v, trs.getSemiring());
					if (debug) Debug.debug(debug, indent, "Created new rule "+dr.toString()+" in regular s2s");
					ruleSet.add(dr);
					stateSet.add(itemMap.get(c));
					stateSet.add(v[0]);
					stateSet.add(v[1]);
					foundMatch = true;
				}
			}
		}
		// archive the decision
		// actually only need to if it's false, since we assumed true
		//	if (debug) Debug.debug(debug, indent, "deciding "+c.toString()+": "+foundMatch);
		if (!foundMatch)
			memo.put(c, Boolean.valueOf(foundMatch));
		return foundMatch;
	}

	// test code
	// takes: training set, untrained rtg, trained rtg location, number iterations, debug level 
	public static void main(String argv[]) {
		RealSemiring semiring = new RealSemiring();

		try {
			int iterations = Integer.parseInt(argv[3]);
			int timeLevel = Integer.parseInt(argv[4]);
			Date preSet = new Date();

			BufferedReader br1 = new BufferedReader(new InputStreamReader(new FileInputStream(argv[0])));
			Vector trainingSet = RuleSetTraining.readItemSet(br1, false, true, semiring);
			int size = ((Integer)trainingSet.get(1)).intValue();
			File treeFile = (File)trainingSet.get(0);
			Date postSet = new Date();
			Debug.dbtime(timeLevel, 1, preSet, postSet, "read in training set");
			Date preRTG = new Date();
			RTGRuleSet rs = new RTGRuleSet(argv[1], "utf-8", semiring);
			Date postRTG = new Date();
			Debug.dbtime(timeLevel, 1, preRTG, postRTG, "read in rtg");
			Date preDeriv = new Date();
			Debug.prettyDebug("There are "+size+" items");
			File derivFile = RuleSetTraining.getAllDerivationRuleSets(rs, size, treeFile, null, timeLevel);
			Date postDeriv = new Date();
			Debug.dbtime(timeLevel, 1, preDeriv, postDeriv, "calculated deriv set");
			Date preTrain = new Date();
			RuleSet outrs = RuleSetTraining.train(rs, treeFile, size, derivFile, 0, iterations, timeLevel, true);
			Date postTrain = new Date();
			Debug.dbtime(timeLevel, 1, preTrain, postTrain, "trained");
			OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(argv[2]), "utf-8");
			w.write(outrs.toString());
			w.close();
		}
		catch (NumberFormatException e) {
			System.err.println("Expected a number of iterations as the fourth argument and debug level as the fifth; got "
					+argv[3]+" and "+argv[4]);
			System.err.println(e.getMessage());
			System.exit(1);
		}
		catch (Exception e) {
			System.err.println("Throwing generic exception of type "+e.getClass().toString());
			StackTraceElement elements[] = e.getStackTrace();
			int n = elements.length;
			for (int i = 0; i < n; i++) {       
				System.err.println(e.toString()+": "+elements[i].getFileName() + ":" 
						+ elements[i].getLineNumber() 
						+ ">> " 
						+ elements[i].getMethodName() + "()");
			}
			System.exit(-1);
		}	 
	}
}
