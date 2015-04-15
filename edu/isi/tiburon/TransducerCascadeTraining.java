
package edu.isi.tiburon;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.io.StreamTokenizer;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;

import gnu.trove.TIntHashSet;
import gnu.trove.TIntIterator;
import gnu.trove.TObjectDoubleHashMap;
import gnu.trove.TObjectDoubleIterator;
import gnu.trove.TObjectIntHashMap;

// this will be built  bit by bit...
public class TransducerCascadeTraining {


	public static boolean ISCONDITIONAL = true;
	public static void setConditional(boolean t) { ISCONDITIONAL = t; }

	// a pair of weighted tree, something
	public static abstract class TreePair implements Serializable {
		public TreeItem in;
		public double weight;
		abstract public String toString();
		abstract public int getSize();
		// TODO: hashcode and equals
	}

	// a pair of weighted tree, trees
	public static  class TreeTreePair extends TreePair{
		public TreeItem out;
		public TreeTreePair(TreeItem i, TreeItem o, double w) { in = i; out = o; weight = w; }
		public String toString() { return in.toString()+", "+out.toString()+", "+weight; }
		// number of nodes in both trees
		public int getSize() {
			return in.numNodes()+out.numNodes();
		}

		// TODO: hashcode and equals
	}
	// a pair of weighted tree, strings
	public static class TreeStringPair extends TreePair{
		public TrainingString out;
		public TreeStringPair(TreeItem i, TrainingString o, double w) { in = i; out = o; weight = w; }
		public String toString() { return in.toString()+", "+out.toString()+", "+weight; }
		// number of nodes in tree + size of string
		public int getSize() {
			return in.numNodes()+out.getSize();
		}
		// TODO: hashcode and equals
	}

	// read a list of tree/tree or tree/string pairs from file
	// format of a record is:
	// count (optional)
	// intree
	// out(tree|string)
	// derivfile(optional) (NOT YET IMPLEMENTED)
	// determine what you're reading using file differentiation

	// all training pairs are written as a big temporary file and re-loaded
	// as needed

	// return vector contains a File and an Integer (so I don't have to change the signature for now...)
	static public Vector<Comparable> readTreeSet(BufferedReader br, 
			boolean isTriple, boolean readTree, 
			Semiring sr)  throws DataFormatException, 
			FileNotFoundException, 
			IOException {

		Vector<Comparable> v = new Vector<Comparable>();
		boolean debug = false;
		StreamTokenizer st = new StreamTokenizer(br);
		st.resetSyntax();
		st.eolIsSignificant(false);
		st.wordChars(32,  '\u00FF');
		// whitespace is only newline stuff!
		st.whitespaceChars(10, 13);
		//	st.parseNumbers();
		st.commentChar('%');

		// the output file
		File of = null;
		of = File.createTempFile("treeset", "tmp");
		of.deleteOnExit();
		ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(of));

		int counter = 0;
		Date readTime = new Date();
		try {
			while (st.nextToken() != StreamTokenizer.TT_EOF) {
				// first get a number
				double count;
				if (isTriple) {
					if (st.ttype != StreamTokenizer.TT_WORD)
						throw new DataFormatException("Expected word but got "+st.ttype);
					if (debug) Debug.debug(debug, "Reading count "+st.sval);
					count = sr.convertFromReal(Double.parseDouble(st.sval));
					st.nextToken();
				}
				else
					count = sr.convertFromReal(1);
				if (debug) Debug.debug(debug, "Reading intree "+st.sval);

				TreeItem intree = new TreeItem(new StringBuffer(st.sval));
				st.nextToken();
				if (debug) Debug.debug(debug, "Reading out element "+st.sval);
				if (readTree) {
					TreeItem outtree = new TreeItem(new StringBuffer(st.sval));
					if (debug) Debug.debug(debug, "Read tree tree triple "+count+", "+intree.toString()+", "+outtree.toString());
					TreeTreePair ttp = new TreeTreePair(intree, outtree, count);
					if (debug) Debug.debug(debug, "Made pair");

					if (debug) Debug.debug(debug, "About to write object");
					oos.writeObject(ttp);
					oos.reset();
					if (debug) Debug.debug(debug, "Wrote object");
					ttp = null;
					outtree = null;
				} 
				else {
					TrainingString outstring = new TrainingString(new StringBuffer(st.sval));
					if (debug) Debug.debug(debug, "Read tree string triple "+count+", "+intree.toString()+", "+outstring.toString());
					TreeStringPair tsp = new TreeStringPair(intree, outstring, count);
					oos.writeObject(tsp);
					oos.reset();
					tsp = null;
					outstring = null;
				}
				intree = null;
				counter++;
				if (counter >= 10000 && counter % 10000 == 0) {
					Date pause = new Date();
					long lapse = pause.getTime() - readTime.getTime();
					readTime = pause;
					Debug.prettyDebug("Read "+counter+" training examples: "+lapse);		    
				}
			}
		}
		catch (IOException e) {
			System.err.println("Problem while trying to write pair from line "+counter+" to temporary file; "+e.getMessage());
			System.exit(1);
		}
		catch (DataFormatException e) {
			throw new DataFormatException("At example "+counter+": "+e.getMessage());
		}
		oos.close();
		v.add(of);
		v.add(new Integer(counter));

		if (debug) Debug.debug(debug, "Done reading");
		return v;
	}



	// if no derivations have been built...
	//     static public TransducerRuleSet train(TransducerRuleSet intrs, 
	// 					  Vector treePairs,
	// 					  double epsilon,
	// 					  int maxit,
	// 					  boolean debug) {

	// 	Semiring semiring = intrs.getSemiring();

	// 	//create all the derivation forests and write them to file, and get pointers
	// 	HashMap derivMap = getAllDerivationRuleSets(intrs, treePairs, null);
	// 	return train(intrs, derivMap, epsilon, maxit, debug);

	//     }

	// jon's train algorithm - set weights on transducer rules
	// TODO: add normalization function
	// TODO: add prior counts
	// info passed either with files on disk or with structures in memory
	static public void train(Vector<TransducerRuleSet> intrsvec, 
			File setFile,
			int setSize,
			File derivFile,
			Vector<CascadeDerivationRuleSet> drsSet,
			Vector<TreePair> trainSet,
			double epsilon,
			int maxit,
			boolean wasNorm,
			boolean isDisk) {
		boolean debug = false;
		boolean getOneBests = false;
		if (ISCONDITIONAL) {
			if (debug) Debug.debug(debug, "doing conditional training");
		}
		Semiring semiring = intrsvec.get(0).getSemiring();
		// used to not modify old trs -- now we do
		Vector<TransducerRuleSet> trsvec  = intrsvec;

		

		// log likelihood change
		double delta = epsilon;
		double lastL = Double.NEGATIVE_INFINITY;
		int itno = 0;
		//  	long beforeUsage = Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();
		//  	Debug.prettyDebug("Before training: heap at "+beforeUsage);
		//	Debug.debug(debug, "Input transducer is"+trs.toString());

		// em core
		// for calculating cross-entropy
		int totalSize = 0;
		boolean totalSizeSet = false;
		
		while ((epsilon == 0 || delta >= epsilon) && itno < maxit) {
			// TODO - use dirichilet priors
			// TODO - not have to do this every iteration
			// for now, if the rule isn't in the hashmap, count is assumed to be 0
			//	    boolean tempdebug = debug;
			//	    debug = true;
			if (debug) Debug.debug(debug, "Iteration "+itno);
			//	    debug = tempdebug;
			PMap<Integer, PMap<Derivable, Double>> counts = new PMap<Integer, PMap<Derivable, Double>>();
			// TODO - use variable method for Z?
			PMap<Integer, PMap<Symbol, Double>> Z = new PMap<Integer, PMap<Symbol, Double>>();
			double L = semiring.ONE();

			ObjectInputStream setois = null;
			ObjectInputStream drsois = null;
			if (isDisk) {
				try {
					setois = new ObjectInputStream(new FileInputStream(setFile));
					drsois = new ObjectInputStream(new FileInputStream(derivFile));
				}
				catch (IOException e) {
					System.err.println("Problem in training while trying to open the info files: "+e.getMessage());
					System.exit(1);
				} 
			}
			long revTime = 0;
			long revCallTime = 0;
			long calcTime = 0;
			long countTime = 0;
			
			// estimate step
			//	    Debug.prettyDebug("Set size is "+setSize);
			for (int setSizeCounter = 0; setSizeCounter < setSize; setSizeCounter++) {
				// read in the training file and derivation forest
				CascadeDerivationRuleSet drs = null;
				TreePair tp = null;
				Date preReviveTime = new Date();
				if (isDisk) {
					try {
						tp = (TreePair)setois.readObject();
						drs = (CascadeDerivationRuleSet)drsois.readObject();
						if (drs == null)
							continue;
						Date preRCTime = new Date();
						drs.revive(intrsvec, semiring);
						Date postRCTime = new Date();
						revCallTime += postRCTime.getTime()-preRCTime.getTime();

					}
					catch (IOException e) {
						System.err.println("Problem while trying to read in a derivation and tree pair "+e.getMessage());
						System.exit(1);
					}
					catch (ClassNotFoundException e) {
						System.err.println("Class-casting problem while trying to read in a derivation and tree pair "+e.getMessage());
						System.exit(1);
					}

					if (drs == null || tp == null) {
						Debug.prettyDebug("Reading of training example completed, but objects are null; continuing...");
						continue;
					}
					Date postReviveTime = new Date();
					revTime += postReviveTime.getTime()-preReviveTime.getTime();
				}
				else {
					drs = drsSet.get(setSizeCounter);
					tp = trainSet.get(setSizeCounter);
					Date postReviveTime = new Date();
					revTime += postReviveTime.getTime()-preReviveTime.getTime();
				}
				//Debug.dbtime(1, 1, preReviveTime, postReviveTime, "revive drs");

				// 		Debug.prettyDebug("Derivation Rule Set for "+tp.toString()+" has "+drs.getNumRules()+" rules");
				//  		and "+drs.getNumberOfDerivations()+" derivations");
				// calculateWeights is based on the tr, so changing that changes the dr
				Date preCalcTime = new Date();

				drs.calculateWeights();
				Date postCalcTime = new Date();
				calcTime += postCalcTime.getTime()-preCalcTime.getTime();

//				Debug.dbtime(1, 1, preCalcTime, postCalcTime, "calculate weights");

				Date preCountTime = new Date();

				
				for (CascadeDerivationRule dr : drs.getRules()) {
				

					if (debug) {
						if (dr.isVirtual())
							Debug.debug(debug, "\tGamma for virtual "+dr.toString()+" is "+drs.getAlpha(dr.getLHS()));
						else
							Debug.debug(debug, "\tGamma for "+dr.toString()+" is "+drs.getAlpha(dr.getLHS())+" times "+dr.getWeight());
					}
					double gamma = dr.isVirtual() ? drs.getAlpha(dr.getLHS()) : semiring.times(drs.getAlpha(dr.getLHS()), dr.getWeight());
					int [] drrhs = drs.getLeafChildren(dr);
					for (int i = 0; i < drrhs.length; i++) {
						int s = drrhs[i];
						try {
							gamma = semiring.times(gamma, drs.getBeta(s));
							if (debug) Debug.debug(debug, " times "+drs.getBeta(s));
						}
						catch (UnusualConditionException e) {
							Debug.debug(true, "Unusual Condition getting beta "+i+" for rule "+dr.toString()+": "+e.getMessage());
						}
					}
					if (debug) Debug.debug(debug, " equals "+gamma);
					if (dr.isVirtual()) {
						if (debug) Debug.debug(debug, "Ignoring estimation step for virtual rule");
						continue;
					}
					if (dr.getLabel() == null) {
						if (debug) Debug.debug(debug, "Skipping untied rule "+dr);
						continue;
					}
					double startBeta = semiring.ZERO();
					try {
						startBeta = drs.getBeta(drs.getStartState());
					}
					catch (UnusualConditionException e) {
						Debug.debug(true, "Unusual Condition getting start beta: "+e.getMessage());
					}
					// the portion being added. the same for all rules and how much we increase Z by
					double contribution = semiring.times(tp.weight, (semiring.times(gamma, semiring.inverse(startBeta))));
					if (debug) Debug.debug(debug, "Contribution for derivation rule "+dr+": "+tp.weight+"*("+gamma+"/"+startBeta+") = "+contribution);
					for (Derivable tr : dr.getLabel()) {
						// which transducer is this rule from?
						int transid = 0;
						if (tr instanceof TransducerRule) {
							transid = ((TransducerRule)tr).parent.getID();
						}
						
						// the new value for the count of this rule
						double newCount = semiring.ZERO();
						
						if (counts.goc(transid).containsKey(tr)) {
							if (debug) Debug.debug(debug, "Increasing prior count prediction of "+counts.get(transid).get(tr).doubleValue());
							newCount = semiring.plus(newCount, counts.get(transid).get(tr).doubleValue());
						}
						else {
							if (debug) Debug.debug(debug, "First time for "+tr.toString()+" this iteration");
						}
						
						
						if (!dr.isVirtual())
							if (debug) Debug.debug(debug, tr.toString()+" : "+tp.in+" : "+dr.getLHS()+" : "+drs.getAlpha(dr.getLHS()));
						//		    Debug.debug(debug, tr.toString()+" : "+newCountPortion+"+("+tp.weight+"*"+gamma+"/"+startBeta+") = ");
						
						newCount = semiring.plus(newCount, contribution);

						if (debug) Debug.debug(debug, "Estimating counts of "+tr.toString()+" at "+newCount);

						counts.get(transid).put(tr, newCount);
						// conditional is the bucket of z we're storing in
						// it should be LHS ROOT (i.e., the state and left symbol only, no children!)
						// in conditional mode, it should be entire LHS!
						Symbol cond;
						if (ISCONDITIONAL) {
							cond = tr.getLHSCondSym();
							//			Debug.prettyDebug("Z for "+dr.getLabel().toString()+" is "+cond.toString());
						}
						else {
							cond = tr.getLHSSym();
						}
						if (!Z.goc(transid).containsKey(cond))
							Z.get(transid).put(cond, contribution);
						else
							Z.get(transid).put(cond, semiring.plus(Z.get(transid).get(cond), contribution));
						if (debug) Debug.debug(debug, "Setting Z of "+cond.toString()+" in transducer "+transid+" to "+Z.get(transid).get(cond));
					}
					//		    Z += newCount;
				}
				Date postCountTime = new Date();
				countTime += postCountTime.getTime()-preCountTime.getTime();
//				Debug.dbtime(1, 1, preCountTime, postCountTime, "calculate weights");
				double startBeta = semiring.ZERO();
				try {
					startBeta = drs.getBeta(drs.getStartState());
				}
				catch (UnusualConditionException e) {
					Debug.debug(true, "Unusual Condition getting start beta after iteration: "+e.getMessage());
				}
				if (debug) Debug.debug(debug, "done with estimation");
				if (debug) Debug.debug(debug, "Likelihood of "+drs.getStartState()+" is "+startBeta);
				// 		if (debug && semiring.ZERO() < semiring.ONE())
				// 		    Debug.debug(debug, "neg log is "+(-Math.log(drs.getBeta(drs.getStartState()))));
				if (debug) Debug.debug(debug, "item weight is "+tp.weight+", i.e. "+semiring.internalToPrint(tp.weight));
				//		Debug.prettyDebug("Adding "+startBeta+" * "+tp.weight);
				L = semiring.times(L, semiring.times(startBeta, tp.weight));
				if (!totalSizeSet)
					totalSize += (tp.getSize()*semiring.internalToPrint(tp.weight));
				drs = null;
			}
			totalSizeSet=true;
			//	    Debug.prettyDebug("After estimation, L is at "+L+"; in print form that's "+semiring.internalToPrint(L)+" and size is "+totalSize);
			if (debug) Debug.debug(debug, "After estimation, L is at "+L+"; in print form that's "+semiring.internalToPrint(L)+" and size is "+totalSize);
			// 	    if (debug && semiring.ZERO() < semiring.ONE())
			// 		Debug.debug(debug, "neg log of L is "+(-Math.log(L)));

			// cross entropy is -ln(p(corpus)). L is p(corpus) if "real", -ln(p(corpus) if of log form.

			double crossentropy;
			double corpusprob;
			if (semiring.ZERO() < semiring.ONE())
				corpusprob = -Math.log(L);
			else
				corpusprob = L;
			crossentropy = corpusprob/totalSize;
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
			// do this for each transducer!!
			// first check on tied rules, then do the rest

			Date preMaxTime = new Date();

			for (TransducerRuleSet trs : trsvec) {
				if (debug) Debug.debug(debug, "Doing maximize for transducer "+trs.getID());
				// 1 - scaling factors for appropriate lhs. if not present, we can assume the scale is 1 (i.e. the 1 - scale is 0)
				HashMap<Symbol, Double> scaling = new HashMap<Symbol, Double>();

				// for each tie class, get the combined count of all rules and the combined sum of all denominators
				HashMap<Symbol, Double> Zsubtracts = new HashMap<Symbol, Double>();
				int trsid = trs.getID();			
				for (int tie : trs.getTies()) {

					double rulecount = semiring.ZERO();
					double denomcount = semiring.ZERO();

					if (debug) Debug.debug(debug, "Setting weights for tie class "+tie);
					// collect all the counts
					for (TransducerRule tr : trs.getTiedRules(tie)) {
						Symbol lhs;
						if (ISCONDITIONAL)
							lhs = tr.getLHSCondSym();
						else
							lhs = tr.getLHSSym();
						Double thisrulecount = counts.get(trsid).get(tr);
						if (thisrulecount == null)
							thisrulecount = new Double(semiring.ZERO());

						rulecount = semiring.plus(rulecount, thisrulecount);
						if (Z.containsKey(trsid) && Z.get(trsid).containsKey(lhs)) {
							if (debug) Debug.debug(debug, "Rule "+tr+" has lhs "+lhs+
									" with counts "+semiring.internalToPrint(thisrulecount)+
									" and "+semiring.internalToPrint(Z.get(trsid).get(lhs)));
							denomcount = semiring.plus(denomcount, Z.get(trsid).get(lhs));
						}
						if (debug) Debug.debug(debug, "Changed rulecount for "+tie+" to "+semiring.internalToPrint(rulecount)+
								"; denomcount to "+semiring.internalToPrint(denomcount));
					}
					// what if we make this or instead of and?
					if (semiring.betteroreq(semiring.ZERO(), rulecount) && semiring.betteroreq(semiring.ZERO(), denomcount)) {
						//		if (rulecount == semiring.ZERO() || denomcount == semiring.ZERO()) {
						if (debug) Debug.debug(debug, "No counts for any member; continuing");
						continue;
					}
					// figure out the value
					double tieweight = semiring.times(rulecount, semiring.inverse(denomcount));
					//		    Debug.debug(true, rulecount+" / "+denomcount+" = "+tieweight);
					if (debug) Debug.debug(debug, "Tie weight is "+semiring.internalToPrint(tieweight));
					// now go back and set all the rules, increase all the scaling factors, and determine how much weight to remove from the Z
					for (TransducerRule tr : trs.getTiedRules(tie)) {
						Symbol lhs;
						if (ISCONDITIONAL)
							lhs = tr.getLHSCondSym();
						else
							lhs = tr.getLHSSym();
						Double reductionobj = counts.get(trsid).get(tr);
						double reduction;
						if (reductionobj == null)
							reduction = semiring.ZERO();
						else
							reduction = reductionobj.doubleValue();
						if (Zsubtracts.containsKey(lhs)) {
							Zsubtracts.put(lhs, semiring.plus(Zsubtracts.get(lhs), reduction));
						}
						else {
							Zsubtracts.put(lhs, reduction);
						}
						if (debug) Debug.debug(debug, "Amount subtracted for Z of "+lhs.toString()+" is "+semiring.internalToPrint(Zsubtracts.get(lhs)));
						Debug.debug(debug, "Setting tied rule "+tr.toString()+" to "+tieweight);
						tr.setWeight(tieweight);

						// 		    if (!trs.getRules().contains(tr)) {
						// 			Debug.debug(true, tr.toString()+" not found in main rules after changing weight normally!");
						// 			Debug.debug(true, trs.toString());
						// 			System.exit(1);
						// 		    }

						if (scaling.containsKey(lhs))
							scaling.put(lhs, semiring.plus(scaling.get(lhs), tieweight));
						else
							scaling.put(lhs, tieweight);
						if (debug) Debug.debug(debug, "Scale for "+lhs.toString()+" is "+semiring.internalToPrint(scaling.get(lhs)));
					}
				}
				// now set Z appropriately, subtracting the stored value, as long as there's something to subtract
				for (Symbol lhs : Zsubtracts.keySet()) {
					if (!Z.get(trsid).containsKey(lhs))
						continue;
					double oldZ = Z.get(trsid).get(lhs);
					double reduction = Zsubtracts.get(lhs);


					double newZ = semiring.minus(oldZ, reduction);

					if (debug) Debug.debug(debug, "Reducing Z of "+lhs+" by "+semiring.internalToPrint(reduction)+
							"; from "+semiring.internalToPrint(oldZ)+" to "+semiring.internalToPrint(newZ));
					if (semiring.betteroreq(semiring.ZERO(), newZ)) {
						//			Debug.debug(true, "Subtracted "+reduction.doubleValue()+" from "+oldZ.doubleValue()+
						//				    " to make a Z value for "+lhs.toString()+" less than ZERO: "+newZ.doubleValue()+"; setting to ZERO ("+semiring.ZERO()+")");
						newZ = semiring.ZERO();
					}
					Z.goc(trsid).put(lhs, newZ);

				}
				for (TransducerRule tr : trs.getRules()) {
					Symbol lhs;
					if (ISCONDITIONAL)
						lhs = tr.getLHSCondSym();
					else
						lhs = tr.getLHSSym();
					if (tr.getTie() > 0) {
						if (debug) Debug.debug(debug, "skipping "+tr+" because it's tied");
						continue;
					}
					// TODO: make sure these rules are reflected in the rules that are stored as indices for count
					if (debug) Debug.debug(debug, "Changing "+tr+" to ");
					if (!counts.goc(trsid).containsKey(tr) || semiring.betteroreq(semiring.ZERO(), counts.get(trsid).get(tr))) {
						tr.setWeight(semiring.ZERO());
						// 		    if (!trs.getRules().contains(tr)) {
						// 			Debug.debug(true, tr.toString()+"not found in main rules after changing weight to zero!");
						// 			System.exit(1);
						// 		    }

						if (debug) Debug.debug(debug, semiring.ZERO()+" (null rule)");
					}
					else {
						double rulecount = counts.get(trsid).get(tr);
						double scale = semiring.ONE();
						if (scaling.containsKey(lhs)) {
							//			double oldscale = scale;
							scale = semiring.minus(scale, scaling.get(lhs));
							if (debug) Debug.debug(debug, "scaling "+lhs+" to "+scale);
							if (semiring.betteroreq(semiring.ZERO(), scale)) {
								//			    Debug.debug(true, "Subtracted "+scaling.get(lhs)+" from "+oldscale+" to get a scale for "+
								//					lhs.toString()+" less than ZERO; "+scale+"; setting to ZERO("+semiring.ZERO()+")");
								scale = semiring.ZERO();
							}
						}
						// nip potential division errors in the bud: if scale or Z is zero, the weight is zero
						if (semiring.betteroreq(semiring.ZERO(), scale) || semiring.betteroreq(semiring.ZERO(), Z.get(trsid).get(lhs))) {
							Debug.debug(debug, "Setting weight of "+tr.toString()+" to ZERO because either scale or Z is ZERO");
							tr.setWeight(semiring.ZERO());
							// 			if (!trs.getRules().contains(tr)) {
							// 			    Debug.debug(true, tr.toString()+"not found in main rules after changing weight to zero based on scale, Z!");
							// 			    System.exit(1);
							// 			}

						}
						// sometimes division seems to not work quite so right, so make sure x/x = 1
						else if (rulecount == Z.get(trsid).get(lhs)) {
							if (debug) Debug.debug(debug, scale+"");
							tr.setWeight(scale);
						}
						else {
							double newwgt = semiring.times(scale, semiring.times(rulecount, semiring.inverse(Z.get(trsid).get(lhs))));
							if (debug) Debug.debug(debug, newwgt+"");
							tr.setWeight(newwgt);
						}
							// 		    if (!trs.getRules().contains(tr)) {
						// 			Debug.debug(true, tr.toString()+"not found in main rules after changing weight via scaling!");
						// 			Debug.debug(true, trs.toString());
						// 			System.exit(1);
						// 		    }

						//		    if (scale == semiring.ZERO())
						//			Debug.debug(true, "WARNING: scale of ZERO for "+lhs.toString()+" trying to set "+tr.toString());
						// 		    if (semiring.better(tr.getWeight(), semiring.ONE())) {
						// 			Debug.debug(true, "rule with value greater than ONE ("+semiring.ONE()+"):"+tr.toString()+" : "+scale+" * "+rulecount.doubleValue()+" / "+((Double)Z.get(lhs)).doubleValue()+" = "+tr.getWeight());
						// 			System.exit(0);
						// 		    }
						if (scaling.containsKey(lhs))
							Debug.debug(debug, tr.toString()+" : "+scale+" * "+rulecount+" / "+Z.get(lhs)+" = "+tr.getWeight());
					}
				} 
				Debug.debug(debug, "At end of iteration "+(itno-1)+", rule set "+trsid+" is:\n"+trs.toString());
			}
			Date postMaxTime = new Date();
			long maxTime = postMaxTime.getTime()-preMaxTime.getTime();
			Debug.debug(true, revTime+" = revive ("+revCallTime+" revcall)"+calcTime+" = calc "+countTime+" = count "+maxTime+" = max");
			delta = (L - lastL)/Math.abs(L);
			if (debug) Debug.debug(debug, "L is "+L+", lastL is "+lastL);
			if (debug) Debug.debug(debug, "Iteration changed L by "+delta);
			lastL = L;
			itno++;
			if (isDisk) {
				try {
					setois.close();
					drsois.close();
				}
				catch (IOException e) {
					System.err.println("Problem in training while trying to close the info files: "+e.getMessage());
					System.exit(1);
				} 
			}
		}
		if (debug) {
			for (TransducerRuleSet trs : trsvec)
				Debug.debug(debug, "Final rule set "+trs.getID()+" is:\n"+trs.toString());
		}
		// output 1-best of the derivation forest
		if (getOneBests) {
			ObjectInputStream drsois = null;
			if (isDisk) {
				try {
					drsois = new ObjectInputStream(new FileInputStream(derivFile));
				}
				catch (IOException e) {
					System.err.println("Problem in training while trying to open the info files: "+e.getMessage());
					System.exit(1);
				} 
			}
			//	    Debug.prettyDebug("Set size is "+setSize);
			for (int setSizeCounter = 0; setSizeCounter < setSize; setSizeCounter++) {

				// read in the training file and derivation forest
				CascadeDerivationRuleSet drs = null;
				if (isDisk) {
					try {
						drs = (CascadeDerivationRuleSet)drsois.readObject();
						if (drs == null)
							continue;
						drs.revive(intrsvec, semiring);
						drs.pruneUselessAndZero();
					}
					catch (IOException e) {
						System.err.println("Problem while trying to read in a derivation and tree pair "+e.getMessage());
						System.exit(1);
					}
					catch (ClassNotFoundException e) {
						System.err.println("Class-casting problem while trying to read in a derivation and tree pair "+e.getMessage());
						System.exit(1);
					}
					if (drs == null) {
						Debug.prettyDebug("Reading of training example completed, but objects are null; continuing...");
						continue;
					}
				}
				else {
					drs = drsSet.get(setSizeCounter);
				}
//				drs.calculateWeights();

				// output 1-best of the derivation forest
				try {
					CascadeDRSKBest dkb = new CascadeDRSKBest(drs);
					TreeItem[] oneBestDeriv = dkb.getKBestTrees(1);
					for (int i = 0; i < oneBestDeriv.length; i++) {
						Debug.prettyDebug(""+oneBestDeriv[i]);
					}
				}
				catch (UnusualConditionException e) {
					System.err.println("Weird when getting DRSKBest: "+e.getMessage());
					System.exit(1);
				}
			}
			if (isDisk) {
				try {
					drsois.close();
				}
				catch (IOException e) {
					System.err.println("Problem in training while trying to close the info files: "+e.getMessage());
					System.exit(1);
				} 
			}
		}		
		return;
	}

	
	// timers and reset/report stuff
	static private long producetime;
	static private long drstime;
	static private long writetime;
	static private long gramtime;
	static private long matchtime;
	static private long getpathstime;

	
	static private void resetTimers() { 
		producetime = 
			drstime = 
				writetime = 
					gramtime = 
						matchtime =
							getpathstime = 
					0;
	}
	static private void printTimes() {
		Debug.prettyDebug(producetime+" to produce");
		Debug.prettyDebug("\t"+gramtime+" to call grammar");
		Debug.prettyDebug("\t"+matchtime+" to match");
		Debug.prettyDebug("\t"+getpathstime+" to get paths");

		Debug.prettyDebug(drstime+" to form drs");
		Debug.prettyDebug(writetime+" to write");

	}
	
	

	// get all the rule sets given files pointing to tree pairs, write them to files, and return pointers to the files.
	//  Used by train and to generate graehl data
	// TODO: be able to build first grammar with cascadetransducer!!
	
	static public File getAllDerivationRuleSets(TransducerRuleSet firstTrans, Vector<CascadeTransducer> othertrs, int setSize, File setFile, String derivFileName, boolean isTree, boolean isOTF, int timeLevel) 
	throws UnusualConditionException{
		boolean debug = false;
		HashMap derivMap = new HashMap();
		if (debug) Debug.debug(debug, "Calculating derivation rule sets for "+setSize+" pairs");
		int goodPairs = 0;
		int badPairs = 0;
		int reportInterval = 1000;
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
		for (int i = 0; i < setSize; i++) {
			if (i!= 0 && i % reportInterval == 0) {
				Debug.prettyDebug("At "+i+" derivations");
				printTimes();
				resetTimers();
			}
			TreePair tp = null;
			//	    Date preReadTime = new Date();

			try {
				tp = (TreePair)tsois.readObject();
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

			//    Date postReadTime = new Date();
			//Debug.dbtime(timeLevel, 3, preReadTime, postReadTime, "read in training object "+tp.toString());

			CascadeDerivationRuleSet drs = null;
			if (debug) Debug.debug(debug, "Attempting to derive "+tp.toString());
			Date preDeriveTime = new Date();

			Date preCompTime = new Date();
			// build the first part of the cascade: tree + gram
			boolean isNorm = othertrs.size() > 0;
			BSRuleSet gram = new BucketTreeTransBSRuleSet(tp.in, firstTrans, isNorm);
			//				OTFTreeTransBSRuleSet gram = new OTFTreeTransBSRuleSet(tp.in, (TreeTransducerRuleSet)intrs);

			// construct the grammar all the way until the last transducer
			for (int j = 0; j < othertrs.size()-1; j++)
				gram = new GramTransBSRuleSet(gram, othertrs.get(j), true, true, isOTF);
			if (othertrs.size() > 0)
				gram = new GramTransBSRuleSet(gram, othertrs.get(othertrs.size()-1), false, isTree, isOTF);

//				if (isOTF) {
//					for (int j = 0; j < othertrs.size()-1; j++)
//						gram = new OTFGramTransBSRuleSet(gram, othertrs.get(j), true, true);
//					if (othertrs.size() > 0)
//						gram = new OTFGramTransBSRuleSet(gram, othertrs.get(othertrs.size()-1), false, true);
//
//				}
//				else {
//					for (int j = 0; j < othertrs.size()-1; j++)
//						gram = new BucketGramTransBSRuleSet(gram, othertrs.get(j), true, true);
//					if (othertrs.size() > 0)
//						gram = new BucketGramTransBSRuleSet(gram, othertrs.get(othertrs.size()-1), false, true);
//				}
				// 
			Date postCompTime = new Date();
			Debug.dbtime(timeLevel, 3, preCompTime, postCompTime, "get image-producing grammar");
			if (isTree) {
				Date preDerivTime = new Date();
				drs = deriv(gram, ((TreeTreePair)tp).out, firstTrans.getSemiring(), timeLevel);
				if (debug) Debug.debug(debug, "Done deriving");		

				Date postDerivTime = new Date();
				Debug.dbtime(timeLevel, 3, preDerivTime, postDerivTime, "calculate derivation rule set");
				if (drs == null || drs.getNumRules() == 0) {
					badPairs++;
					Debug.prettyDebug("Warning: Tree tree pair "+tp.in.toString()+", "+((TreeTreePair)tp).out.toString()+" could not be explained by transducer. Removing from training set");
					Debug.prettyDebug("So far: "+badPairs+" bad sentence pairs, and "+goodPairs+" good sentence pairs");
				}
				else {
					goodPairs++;
					Debug.debug(true, drs.getNumRules()+" rules in drs before pruning");
					Date prePruneTime = new Date();
					drs.pruneUseless();
					Date postPruneTime = new Date();
					Debug.dbtime(timeLevel, 3, prePruneTime, postPruneTime, "prune drs");
					if (debug) Debug.debug(debug, "Done pruning");
					Date preCalcTime = new Date();
					drs.calculateWeights();
					Date postCalcTime = new Date();
					Debug.dbtime(timeLevel, 3, preCalcTime, postCalcTime, "calculate weights");
					if (debug) Debug.debug(debug, "Derivation Rule Set for "+tp.toString()+" has "+drs.getNumRules()+" rules");
					//if (debug) Debug.debug(debug, drs.toString()+"\n");
				}
				Date preWriteTime = new Date();
				try {
					if (debug) Debug.debug(debug, "About to write file...");
					dsoos.writeObject(drs);
					dsoos.reset();
					if (debug) Debug.debug(debug, "Done writing file");
				}
				catch (IOException e) {
					System.err.println("Problem while trying to write derivation of "+
							tp.toString()+" to file: "+e.getMessage());
					System.exit(1);
				}
				Date postWriteTime = new Date();
				writetime += postWriteTime.getTime()-preWriteTime.getTime();
				Debug.dbtime(timeLevel, 3, preWriteTime, postWriteTime, "write drs");
				Debug.debug(true, gram.getRuleCount()+" rules for grammar");
				Debug.debug(true, drs.getNumRules()+" rules in drs");
				gram.reportRules();
			}
			else {
				
//				Date preCompTime = new Date();
//				// build the first part of the cascade: tree + gram
//				boolean isNorm = othertrs.size() > 0;
//				BSRuleSet gram = new BucketTreeTransBSRuleSet(tp.in, firstTrans, isNorm);
//				//				OTFTreeTransBSRuleSet gram = new OTFTreeTransBSRuleSet(tp.in, (TreeTransducerRuleSet)intrs);
//
//				// construct the grammar all the way until the last transducer
//				// last one is string
//				if (isOTF) {
//					for (int j = 0; j < othertrs.size()-1; j++)
//						gram = new OTFGramTransBSRuleSet(gram, othertrs.get(j), true, true);
//					if (othertrs.size() > 0)
//						gram = new OTFGramTransBSRuleSet(gram, othertrs.get(othertrs.size()-1), false, false);
//
//				}
//				else {
//					for (int j = 0; j < othertrs.size()-1; j++)
//						gram = new BucketGramTransBSRuleSet(gram, othertrs.get(j), true, true);
//					if (othertrs.size() > 0)
//						gram = new BucketGramTransBSRuleSet(gram, othertrs.get(othertrs.size()-1), false, false);
//				}
//				// 
//				Date postCompTime = new Date();
//				Debug.dbtime(timeLevel, 3, preCompTime, postCompTime, "get image-producing grammar");

				
				Date preDerivTime = new Date();
				drs = deriv(gram, ((TreeStringPair)tp).out, firstTrans.getSemiring());
				if (debug) Debug.debug(debug, "Done deriving");		

				Date postDerivTime = new Date();
				Debug.dbtime(timeLevel, 3, preDerivTime, postDerivTime, "calculate derivation rule set");
				if (drs == null || drs.getNumRules() == 0) {
					badPairs++;
					Debug.prettyDebug("Warning: Tree string pair "+tp.in.toString()+", "+((TreeStringPair)tp).out.toString()+" could not be explained by transducer. Removing from training set");
					Debug.prettyDebug("So far: "+badPairs+" bad sentence pairs, and "+goodPairs+" good sentence pairs");
				}
				else {
					goodPairs++;
					Date prePruneTime = new Date();
					drs.pruneUseless();
					if (debug) Debug.debug(debug, "Done pruning");

					Date postPruneTime = new Date();
					Debug.dbtime(timeLevel, 3, prePruneTime, postPruneTime, "prune drs");
					Date preCalcTime = new Date();
					drs.calculateWeights();
					Date postCalcTime = new Date();
					Debug.dbtime(timeLevel, 3, preCalcTime, postCalcTime, "calculate weights");
					if (debug) Debug.debug(debug, "Done calculating weights");
					if (debug) Debug.debug(debug, "Derivation Rule Set for "+tp.toString()+" has "+drs.getNumRules()+" rules");
					//		    if (debug) Debug.debug(debug, drs.toString()+"\n");
				}
				Date preWriteTime = new Date();

				try {
					dsoos.writeObject(drs);
					dsoos.reset();
					if (debug) Debug.debug(debug, "Done writing file");
				}
				catch (IOException e) {
					System.err.println("Problem while trying to write derivation of "+
							tp.toString()+" to file: "+e.getMessage());
					System.exit(1);
				}
				Date postWriteTime = new Date();
				writetime += postWriteTime.getTime()-preWriteTime.getTime();
			}
			Date postDeriveTime = new Date();
			Debug.dbtime(timeLevel, 2, preDeriveTime, postDeriveTime, "total derivation");

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


	static public void getAllMemDerivationRuleSets(
			TransducerRuleSet firstTrans, 
			Vector<CascadeTransducer> othertrs, 
			int setSize, 
			Vector<TreePair> pairSet,
			Vector<CascadeDerivationRuleSet> drsSet,
			File setFile,
			boolean isTree, boolean isOTF, int timeLevel) 
	throws UnusualConditionException{
		boolean debug = false;
		HashMap derivMap = new HashMap();
		if (debug) Debug.debug(debug, "Calculating derivation rule sets for "+setSize+" pairs");
		int goodPairs = 0;
		int badPairs = 0;
		int reportInterval = 1000;
		ObjectInputStream tsois = null;
		File of = null;
		try {
			tsois = new ObjectInputStream(new FileInputStream(setFile));
		}
		catch (IOException e) {
			System.err.println("IO Problem preparing to get all derivation rule sets: "+e.getMessage());
			System.exit(1);
		}
		for (int i = 0; i < setSize; i++) {
			if (i != 0 && i % reportInterval == 0) {
				Debug.prettyDebug("At "+i+" derivations");
				printTimes();
				resetTimers();
			}
			TreePair tp = null;
			//	    Date preReadTime = new Date();

			try {
				tp = (TreePair)tsois.readObject();
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

			//    Date postReadTime = new Date();
			//Debug.dbtime(timeLevel, 3, preReadTime, postReadTime, "read in training object "+tp.toString());

			CascadeDerivationRuleSet drs = null;
			if (debug) Debug.debug(debug, "Attempting to derive "+tp.toString());
			Date preDeriveTime = new Date();
			Vector<BSRuleSet> grams = new Vector<BSRuleSet>();
		
			Date preCompTime = new Date();
			// build the first part of the cascade: tree + gram
			boolean isNorm = othertrs.size() > 0;
			BSRuleSet gram = new BucketTreeTransBSRuleSet(tp.in, firstTrans, isNorm);
			grams.add(gram);
			//				OTFTreeTransBSRuleSet gram = new OTFTreeTransBSRuleSet(tp.in, (TreeTransducerRuleSet)intrs);

			// construct the grammar all the way until the last transducer
			for (int j = 0; j < othertrs.size()-1; j++) {
				gram = new GramTransBSRuleSet(gram, othertrs.get(j), true, true, isOTF);
				grams.add(gram);
			}
			if (othertrs.size() > 0) {
				gram = new GramTransBSRuleSet(gram, othertrs.get(othertrs.size()-1), false, isTree, isOTF);
				grams.add(gram);
			}

			// 
			Date postCompTime = new Date();
			Debug.dbtime(timeLevel, 3, preCompTime, postCompTime, "get image-producing grammar");

			if (isTree) {
				Date preDerivTime = new Date();
				drs = deriv(gram, ((TreeTreePair)tp).out, firstTrans.getSemiring(), timeLevel);
				if (debug) Debug.debug(debug, "Done deriving");		

				Date postDerivTime = new Date();
				Debug.dbtime(timeLevel, 3, preDerivTime, postDerivTime, "calculate derivation rule set");
				if (drs == null || drs.getNumRules() == 0) {
					badPairs++;
					Debug.prettyDebug("Warning: Tree tree pair "+tp.in.toString()+", "+((TreeTreePair)tp).out.toString()+" could not be explained by transducer. Removing from training set");
					Debug.prettyDebug("So far: "+badPairs+" bad sentence pairs, and "+goodPairs+" good sentence pairs");
				}
				else {
					goodPairs++;
					Date prePruneTime = new Date();
					drs.pruneUseless();
					Date postPruneTime = new Date();
					Debug.dbtime(timeLevel, 3, prePruneTime, postPruneTime, "prune drs");
					if (debug) Debug.debug(debug, "Done pruning");
					Date preCalcTime = new Date();
					drs.calculateWeights();
					Date postCalcTime = new Date();
					Debug.dbtime(timeLevel, 3, preCalcTime, postCalcTime, "calculate weights");
					if (debug) Debug.debug(debug, "Derivation Rule Set for "+tp.toString()+" has "+drs.getNumRules()+" rules");
					//if (debug) Debug.debug(debug, drs.toString()+"\n");
				}
				Date preWriteTime = new Date();
				pairSet.add(tp);
				drsSet.add(drs);
				Date postWriteTime = new Date();
				Debug.dbtime(timeLevel, 3, preWriteTime, postWriteTime, "write drs");
			}
			else {
				
				Date preDerivTime = new Date();
				drs = deriv(gram, ((TreeStringPair)tp).out, firstTrans.getSemiring());
				if (debug) Debug.debug(debug, "Done deriving");		

				Date postDerivTime = new Date();
				Debug.dbtime(timeLevel, 3, preDerivTime, postDerivTime, "calculate derivation rule set");
				if (drs == null || drs.getNumRules() == 0) {
					badPairs++;
					Debug.prettyDebug("Warning: Tree string pair "+tp.in.toString()+", "+((TreeStringPair)tp).out.toString()+" could not be explained by transducer. Removing from training set");
					Debug.prettyDebug("So far: "+badPairs+" bad sentence pairs, and "+goodPairs+" good sentence pairs");
				}
				else {
					goodPairs++;
					Date prePruneTime = new Date();
					drs.pruneUseless();
					if (debug) Debug.debug(debug, "Done pruning");

					Date postPruneTime = new Date();
					Debug.dbtime(timeLevel, 3, prePruneTime, postPruneTime, "prune drs");
					Date preCalcTime = new Date();
					drs.calculateWeights();
					Date postCalcTime = new Date();
					Debug.dbtime(timeLevel, 3, preCalcTime, postCalcTime, "calculate weights");
					if (debug) Debug.debug(debug, "Done calculating weights");
					if (debug) Debug.debug(debug, "Derivation Rule Set for "+tp.toString()+" has "+drs.getNumRules()+" rules");
					//		    if (debug) Debug.debug(debug, drs.toString()+"\n");
				}
				pairSet.add(tp);
				drsSet.add(drs);
			}
			Date postDeriveTime = new Date();
			Debug.dbtime(timeLevel, 2, preDeriveTime, postDeriveTime, "total derivation");
//			Debug.debug(true, grams.size()+" for "+tp.in);
//			for (BSRuleSet gr : grams) {
//				Debug.debug(true, gr.getRuleCount()+" in rule set");
//			}
			// 	    if (debug) Debug.debug(debug, "Memo now has "+memo.size()+" entries");
			// 	    long afterwrite = Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();
			// 	    if (debug) Debug.debug(debug, "after write: heap at "+afterwrite);
			// TODO: if not done already, prune useless deriv weights
		}
		try {
			tsois.close();
		}
		catch (IOException e) {
			System.err.println("Problem in training while trying to open the info files: "+e.getMessage());
			System.exit(1);
		} 
		return;
	}

	
	
	static private int nextIndex = 0;
	// jon's deriv algorithm - build one drs given an image-taking grammar and an output item to match against

	static public CascadeDerivationRuleSet deriv(BSRuleSet gram, TreeItem o, Semiring semi, int timeLevel) throws UnusualConditionException {
		boolean debug = false;
		Date preSetupTime = new Date();
//		TIntHashSet states = new TIntHashSet();
		HashSet<CascadeDerivationRule> rules = new HashSet<CascadeDerivationRule>();
		// memoize production decisions
		HashMap<StateTreePair, Boolean> memo = new HashMap<StateTreePair, Boolean>();
		// track unique pairs in this derivation and represent them as ints in the drs
		HashMap<StateTreePair, Integer> pairMap = new HashMap<StateTreePair, Integer>();
		StateTreePair startState = SymbolFactory.getStateTreePair(gram.getStartState(), o, 1, o.getLeaves().length);
		nextIndex = 1;
		int startStateId = nextIndex++;
		pairMap.put(startState, startStateId);
		Date postSetupTime = new Date();
		Debug.dbtime(timeLevel, 4, preSetupTime, postSetupTime, "setup for deriv");
		// create an empty drs to begin with, and replace it if a derivation comes up
		CascadeDerivationRuleSet drs = new CascadeDerivationRuleSet(startStateId, nextIndex, rules, semi);
		Date preProduceTime = new Date();
		if (produce(gram, semi, startState, 
				//states, 
				rules, memo, pairMap, timeLevel, 0)) {
			Date postProduceTime = new Date();
			producetime += postProduceTime.getTime()-preProduceTime.getTime();
			if (debug) Debug.debug(debug, "produce was successful");
			Debug.dbtime(timeLevel, 4, preProduceTime, postProduceTime, "do produce");
			Date preDRSTime = new Date();
			drs = new CascadeDerivationRuleSet(pairMap.get(startState), nextIndex, rules, semi);
			Date postDRSTime = new Date();
			drstime += postDRSTime.getTime()-preDRSTime.getTime();
			Debug.dbtime(timeLevel, 4, preDRSTime, postDRSTime, "make the drs");
		}
		else {
			// zero it out so we don't confuse this for success
			if (debug) Debug.debug(debug, "produce NOT successful");
			drs = null;
		}
		return drs;
	}


	// jon's deriv algorithm for xrs - build one drs given a trs and a pair

	static public CascadeDerivationRuleSet deriv(BSRuleSet gram, TrainingString o, Semiring semi) throws UnusualConditionException {
		boolean debug = false;
//		TIntHashSet states = new TIntHashSet();
		HashSet<CascadeDerivationRule> rules = new HashSet<CascadeDerivationRule>();
		// memoize production decisions
		HashMap<CascadeVariableCluster, Boolean> memo = new HashMap<CascadeVariableCluster, Boolean>();
		// track unique pairs in this derivation and represent them as ints in the drs
		// map variablecluster -> int
		TObjectIntHashMap pairMap = new TObjectIntHashMap();
		CascadeVariableCluster.resetNextIndex();
		if (debug) Debug.debug(debug, "getting derivation to "+o.toString()+":"+o.getStartIndex()+", "+o.getEndIndex());
		CascadeVariableCluster startState = new CascadeVariableCluster(gram.getStartState(), o);
		pairMap.put(startState, CascadeVariableCluster.getNextIndex());
		if (debug) Debug.debug(debug, "Associating "+startState.toString()+" with "+pairMap.get(startState));
		// create an empty drs to begin with, and replace it if a derivation comes up
		CascadeDerivationRuleSet drs = new CascadeDerivationRuleSet(pairMap.get(startState), CascadeVariableCluster.getNextIndex(), rules, semi);

		Date preProduceTime = new Date();
		if (produce(gram, semi, startState, 
				//states, 
				rules, memo, pairMap, 0)) {
			Date postProduceTime = new Date();
			producetime += postProduceTime.getTime()-preProduceTime.getTime();
			//	    Debug.dbtime(3, preProduce, ""+(i.getTreeLeaves().length+o.getSize()));
			//	    Debug.dbtime(2, preProduce, "calculated derviation forest");
			//	    Debug.debug(true, states.size()+"\t"+rules.size()+"\t"+memo.size()+"\t"+pairMap.size());
			Date preDRSTime = new Date();

			drs = new CascadeDerivationRuleSet(pairMap.get(startState), CascadeVariableCluster.getNextIndex(), rules, semi);
			Date postDRSTime = new Date();
			drstime += postDRSTime.getTime()-preDRSTime.getTime();
			if (debug) Debug.debug(debug, "produce successful");
		}
		else {
			// zero it out so we don't confuse this for success
			if (debug) Debug.debug(debug, "produce NOT successful");
			drs = null;
		}
		return drs;
	}


	// custom tree checker: tree a might have states
	static private boolean isTreeMatch(TreeItem a, TreeItem b, BSRuleSet gram) {
		if (a.getNumChildren() > 0) {
			if (a.getNumChildren() != b.getNumChildren())
				return false;
			if (!a.getLabel().equals(b.getLabel()))
				return false;
			for (int i = 0; i < a.getNumChildren(); i++) {
				if (!isTreeMatch(a.getChild(i), b.getChild(i), gram))
					return false;
			}
			return true;
		}
		else {
			if (gram.isState(a.getLabel()))
				return true;
			if (b.getNumChildren() > 0)
				return false;
			return a.getLabel().equals(b.getLabel());					
		}
	}
	
	// similar to the custom tree checker but we assume the trees match and
	// we only exist to make a vector of state, out-tree pairs
	static private void getTreeMatchDescendants(
			TreeItem a, 
			TreeItem b, 
			BSRuleSet gram,
			Vector<StateTreePair> vec) {
		if (a.getNumChildren() > 0) {
			for (int i = 0; i < a.getNumChildren(); i++) {
				getTreeMatchDescendants(a.getChild(i), b.getChild(i), gram, vec);
			}
			return;
		}
		else {
			if (gram.isState(a.getLabel()))
				vec.add(SymbolFactory.getStateTreePair(a.getLabel(), b, 1, b.getLeaves().length));
			return;		
		}
	}
	
	// from StringTransducerRule.getAllAlignments
	// given a sequence of variable clusters and symbols
	// find all the ways the spans of a string can correspond to these 
	 // traverse through the rule until the end is reached, adding elements along the way
	// TODO: build the vectors for clusters elsewhere!
	public static Vector<Vector<CascadeVariableCluster>> 
	getStringAlignments(
			BSRuleSet gram, 
			CFGRule rule, 
			TrainingString string) throws UnusualConditionException  {
		boolean debug = false;
		Vector<Vector<CascadeVariableCluster>> ret = new Vector<Vector<CascadeVariableCluster>>();
		HashMap<Integer, Vector<Symbol>> clusterMap = new HashMap<Integer, Vector<Symbol>>();
		Vector<Symbol> pattern = new Vector<Symbol>();
		StringItem patternString = (StringItem)rule.getRHS();
		Vector<Symbol> currCluster = new Vector<Symbol>();
		int currIndex = 0;
		while (patternString != null) {
			// state: add to current cluster
			if (gram.isState(patternString.getLabel())) {
				currCluster.add(patternString.getLabel());
				patternString = patternString.getNext();
				continue;
			}
			// label: clear last cluster, add new element to pattern
			else {
				if (currCluster.size() > 0) {
					clusterMap.put(currIndex++, currCluster);
					currCluster = new Vector<Symbol>();
					pattern.add(null);
				}
				pattern.add(patternString.getLabel());
				patternString = patternString.getNext();
				currIndex++;
			}
		}
		// last cluster
		if (currCluster.size() > 0) {
			clusterMap.put(currIndex++, currCluster);
			currCluster = new Vector<Symbol>();
			pattern.add(null);
		}
		Vector<Symbol> stringVec = new Vector<Symbol>();
		TrainingString ptr = string;
		while (ptr != null && ptr.getLabel() != null) {
			stringVec.add(ptr.getLabel());
			ptr = ptr.next();
		}
		Symbol[] pattArr = new Symbol[pattern.size()];
		pattern.toArray(pattArr);
		Symbol[] strArr = new Symbol[stringVec.size()];
		stringVec.toArray(strArr);
		for (HashMap<Integer, Index> map : getStringAlignments(pattArr, 0, strArr, 0)) {
			Vector<CascadeVariableCluster> al = new Vector<CascadeVariableCluster>();
			for (int clustId = 0; clustId < pattArr.length; clustId++) {
				if (pattArr[clustId] == null) {
					Index span = map.get(clustId);
					Vector<Symbol> vars = clusterMap.get(clustId);
					if (debug) Debug.debug(debug, "Getting substring of "+string+" on span "+span+"; offset is "+string.getStartIndex());
					TrainingString subString = string.isEpsilon() ? string : string.getSubString(span.left()+string.getStartIndex(), span.right()+string.getStartIndex());
					al.add(new CascadeVariableCluster(subString, vars));
				}
			}
			ret.add(al);
		}
		if (ret.size() == 0)
			ret.add(new Vector<CascadeVariableCluster>());
		return ret;
		
	}
    private static Vector<HashMap<Integer, Index>> getStringAlignments(
    		Symbol[] pattern, int patIdx,
    		Symbol[] string, int strIdx) throws UnusualConditionException {
    	boolean debug = false;
    	Vector<HashMap<Integer, Index>> ret = new Vector<HashMap<Integer, Index>>();
    	// if pattern has epsilon string should be empty
    	if (pattern.length > 0 && pattern[patIdx] != null && pattern[patIdx].equals(Symbol.getEpsilon())) {
    		if (string.length > 0)
    			throw new UnusualConditionException("Tried to align epsilon pattern to non-empty string "+string);
    		return ret;
    	}
    	// should only have non-null pattern[patIdx] at the first entry
    	while (patIdx < pattern.length && pattern[patIdx] != null) {
    		if (debug) Debug.debug(debug, "Looking for "+pattern[patIdx]+" at position "+strIdx);
    		if (pattern[patIdx] != string[strIdx])
    			throw new UnusualConditionException("pattern at "+patIdx+" is "+pattern[patIdx]+" and string at "+strIdx+" is "+string[strIdx]);
    		patIdx++;
    		strIdx++;
    	}
    	
    	// if off the end, return empty
    	if (patIdx >= pattern.length)
    		return ret;
    	// if patIdx is the end, then match the rest of the string. only thing to do
    	if (patIdx == pattern.length-1) {
    		HashMap<Integer, Index> map = new HashMap<Integer, Index>();
    		map.put(patIdx, Index.get(strIdx, string.length));
    		ret.add(map);
    		return ret;
    	}
    	// find next symbol span
    	int symStart = patIdx+1;
    	int symEnd = symStart;
    	while (symEnd+1 < pattern.length && pattern[symEnd] != null)
    		symEnd++;
    	// find all matches from strIdx to sym span
    	for (int srchIdx = strIdx; srchIdx < string.length; srchIdx++) {
    		if (pattern[symStart] == string[srchIdx]) {
    			boolean isMatch = true;
    			int patscan = symStart;
    			int strscan = srchIdx;
    			for (; patscan < symEnd; patscan++) {
    				if (pattern[patscan] != string[strscan]) {
    					isMatch = false;
    					break;
    				}
    				strscan++;
    			}
    			if (!isMatch) {
    				break;
    			}
    			
    			// get the rest, if there's more to the pattern
    			if (symEnd < pattern.length) {
    				for (HashMap<Integer, Index> cdrmap : getStringAlignments(pattern, symEnd, string, strscan)) {
    					HashMap<Integer, Index> map = new HashMap<Integer, Index>();
    	    			map.put(patIdx, Index.get(strIdx, srchIdx));
    					map.putAll(cdrmap);
    					ret.add(map);
    				}
    			}
    			else {
    				HashMap<Integer, Index> map = new HashMap<Integer, Index>();
	    			map.put(patIdx, Index.get(strIdx, srchIdx));
	    			ret.add(map);
    			}	
    		}
    	}
    	return ret;
    }
    	
    
	
	
	// jon's produce algorithm - return true if something was created. Also add to the hash sets passed along
	// build CascadeDerivationRules, which are lhs states, rhs transducer rule, and a list of rhs states
	// as it goes, add the states too

	// memoization: memo maps qio -> Boolean
	static public boolean produce(
			BSRuleSet gram,
			Semiring semi,
			StateTreePair qio, 
//			TIntHashSet stateSet, 
			HashSet<CascadeDerivationRule> ruleSet,
			HashMap<StateTreePair, Boolean> memo, 		
			HashMap<StateTreePair, Integer> pairMap, 
			int timeLevel, int indent) throws UnusualConditionException {
		boolean debug = false;
		// memoization check
		Date preMemoTime = new Date();
		if (memo.containsKey(qio)) {
			Boolean entry = memo.get(qio);
			if (debug) Debug.debug(debug, indent, ((entry.booleanValue()) ? "Success: " : "Fail: ")+qio.toString()+" (memo) ");
			Date postMemoTime = new Date();
			Debug.dbtime(timeLevel, 4, preMemoTime, postMemoTime, "check memoization and succeed");
			return entry.booleanValue();

		}
		Date postMemoTime = new Date();
		Debug.dbtime(timeLevel, 4, preMemoTime, postMemoTime, "check memoization and fail");
		// potentially matching rules
		boolean foundMatch = false;
		Date preGramTime = new Date();
		PIterator<Rule> ruleit = gram.getBSIter(qio.getState());
		Date postGramTime = new Date();
		gramtime += postGramTime.getTime()-preGramTime.getTime();
		while (ruleit.hasNext()) {
			RTGRule rule = (RTGRule)ruleit.next();
			Date preMatchTime = new Date();
			boolean matchResult = isTreeMatch((TreeItem)rule.getRHS(), qio.getTree(), gram);
			Date postMatchTime = new Date();
			matchtime += postMatchTime.getTime()-preMatchTime.getTime();
			if (!matchResult)
				continue;
			if (debug) Debug.debug(debug, indent, "Attempting to produce: "+qio.toString());
					// get vector of state, intree, outtree triples
			Date preGetPaths = new Date();
			Vector<StateTreePair> v = new Vector<StateTreePair>();
			getTreeMatchDescendants(
					(TreeItem)rule.getRHS(), 
					qio.getTree(), gram,
					v);
			Date postGetPaths = new Date();
			getpathstime += postGetPaths.getTime()-preGetPaths.getTime();
			Debug.dbtime(timeLevel, 5, preGetPaths, postGetPaths, "get training path");
//			StringBuffer alignStr = (StringBuffer)pairV.get(1);
//			// index version of v. for derivationrule
			int[] sv = new int[v.size()];
			int svidx = 0;
			// depth-first check
			// must be able to produce in children to continue;
			boolean allOkay = true;
			for (StateTreePair child : v) {
				if (!produce(gram, semi, child, 
						//stateSet, 
						ruleSet, memo, pairMap, timeLevel, (indent+1))) {
					if (debug) Debug.debug(debug, indent, "Can't produce in "+child);
					allOkay = false;
					break;
				}
				if (!pairMap.containsKey(child))
					pairMap.put(child, nextIndex++);
				sv[svidx++] = pairMap.get(child);
			}
			if (!allOkay)
				continue;
			if (debug) Debug.debug(debug, indent, "Successfully produced "+qio);
			Date preCreate = new Date();
			// create the derivation rule and states
			if (!pairMap.containsKey(qio))
				pairMap.put(qio, nextIndex++);
			int qioidx = pairMap.get(qio);
			
			// removing generics to avoid java weirdness
			CascadeDerivationRule dr = new CascadeDerivationRule(qioidx, (Vector)rule.getTransducerRules(), sv, semi);

			if (debug) Debug.debug(debug, indent, "Rule created is "+dr);
			ruleSet.add(dr);
//			stateSet.add(qioidx);
//			for (int j = 0; j < sv.length; j++)
//				stateSet.add(sv[j]);
			foundMatch = true;
			Date postCreate = new Date();
			Debug.dbtime(timeLevel, 5, preCreate, postCreate, "create rule");
		}
		// archive the decision
		memo.put(qio, Boolean.valueOf(foundMatch));
		return foundMatch;
	}



	// jon's produce algorithm for xrs - return true if something was created. Also add to the hash sets passed along
	// build CascadeDerivationRules, which are lhs states, rhs string transducer rule, and a list of rhs states
	// as it goes, add the states too

	// memoization: memo maps qio -> Boolean.
	// pairMap: maps qio hashcode (int) -> int. Why hashcode? Because some clusters 
	// have to look like qio's too and that's an easy way to
	// do it.
	static public boolean produce(
			BSRuleSet gram,
			Semiring semi,
			CascadeVariableCluster qio, 
//			TIntHashSet stateSet, 
			HashSet<CascadeDerivationRule> ruleSet, 
			HashMap<CascadeVariableCluster, Boolean> memo,  
			TObjectIntHashMap pairMap, int indent) throws UnusualConditionException {
		boolean debug = false;
		// memoization check
		if (memo.containsKey(qio)) {
			Boolean entry = memo.get(qio);
			//	    if (debug) Debug.debug(debug, indent, entry.booleanValue()+":"+qio.toString()+" (memo) ");
			return entry.booleanValue();
		}
		//	if (debug) Debug.debug(debug, indent, "deferring the decision by assuming true for "+qio.toString());
		memo.put(qio, Boolean.TRUE);
		// potentially matching rules
		boolean foundMatch = false;
		Symbol q = qio.state();
		// potentially matching rules
		Date preGramTime = new Date();
		PIterator<Rule> ruleit = gram.getBSIter(q);
		Date postGramTime = new Date();
		gramtime += postGramTime.getTime()-preGramTime.getTime();
		if (ruleit == null) {
			memo.put(qio, false);
			return false;
		}
		TrainingString o = qio.out();
		if (debug) Debug.debug(debug, indent, "Trying to match "+q+" to "+o);
		while (ruleit.hasNext()) {
			CFGRule rule = (CFGRule)ruleit.next();
			if (!((StringItem)rule.getRHS()).isItemMatch(o, gram))
				continue;

			


			//   if (debug) Debug.debug(debug, indent, "Attempting: "+qio.toString()+" with "+tr.toString());

			// it's possible this rule could help to produce the ostring from the itree a number 
			// of ways -- these are the various "p"s from the k/g journal paper.
			// alignments is a vector of sequences of consecutive variables (nonterminals) 
			// paired with literals from a training string
			// .(a vector of vectors). 
			// Each "cluster" variables needs to be processed
			// to see if we can accept the alignment
			//  Date preGetAlignments = new Date();
			Date preGetPaths = new Date();

			Vector<Vector<CascadeVariableCluster>> alignments = getStringAlignments(gram, rule, o);
			//	    Debug.dbtime(3, preGetAlignments, "get alignments for "+qio.toString());
			
			
			Date postGetPaths = new Date();
			getpathstime += postGetPaths.getTime()-preGetPaths.getTime();
			for (Vector<CascadeVariableCluster> v : alignments) {
				
				// index version of v. for derivationrule
				int[] sv = new int[v.size()];
				
				// 		if (debug) {
				// 		    System.err.print("Checking alignment [");
				// 		    while (vit.hasNext()) {
				// 			CascadeVariableCluster c = (CascadeVariableCluster)vit.next();
				// 			System.err.print("("+c.toString()+") ");
				// 		    }
				// 		    Debug.debug(true, "]");
				// 		    vit = v.iterator();
				// 		}

				boolean allOkay = true;
				int svidx = 0;
				// check each item of the alignment vector for a correct derivation. If a span is wrong, the whole
				// alignment is wrong. If a span is undecided, the whole alignment is undecided and must be deferred
				for (CascadeVariableCluster c : v) {
					if (!spanToSpan(gram, semi, c, 
							//stateSet, 
							ruleSet, memo, pairMap, debug, indent+1)) {
						//			if (debug) Debug.debug(debug, indent, "Nonterminal Cluster "+c.toString()+" could not be resolved");
						allOkay = false;
						break;
					}
					// otherwise, make an int representation for the cluster
					else {
						if (!pairMap.containsKey(c)) {
							pairMap.put(c, CascadeVariableCluster.getNextIndex());
							//			    if (debug) Debug.debug(debug, indent, "Associating "+c.hashCode()+" with "+pairMap.get(c.hashCode()));
						}
						sv[svidx++] = pairMap.get(c);
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
				if (!pairMap.containsKey(qio)) {
					pairMap.put(qio, CascadeVariableCluster.getNextIndex());
					//		    if (debug) Debug.debug(debug, indent, "Associating "+qio.hashCode()+" with "+pairMap.get(qio.hashCode()));
				}
				int qioidx = pairMap.get(qio);

				// sanity check
				//		if (debug) Debug.debug(debug, indent, "About to create rule with head "+qio.toString()+" ("+qioidx+")");
				if (debug) {
					for (int j = 0; j< v.size(); j++) {
						CascadeVariableCluster clust = (CascadeVariableCluster)v.get(j);
						//			if (debug) Debug.debug(debug, indent, "Child "+j+" = "+clust.toString()+" ("+sv[j]+")");
					}
				}

				// actually build the rule
				
				CascadeDerivationRule dr = new CascadeDerivationRule(qioidx, (Vector)rule.getTransducerRules(), sv, semi);
				if (debug) Debug.debug(debug, indent, "Created new rule "+dr.toString()+" in produce");

				// archive the rule
				ruleSet.add(dr);
//				stateSet.add(qioidx);
//				for (int j = 0; j < sv.length; j++)
//					stateSet.add(sv[j]);

			}
		}
		// archive the decision (it was already set to true, so no need to re-update unless it's now false)
		//	if (debug) Debug.debug(debug, indent, "deciding "+qio.toString()+": "+foundMatch);
		if (!foundMatch)
			memo.put(qio, Boolean.valueOf(foundMatch));
		return foundMatch;
	}


	// span to span - historical to the km journal paper. this is similar to 
	// produce but we have to do dynamic programming since
	// there may be more than one nonterminal in the nonterminal cluster. 
	// In such a case, we create a virtual node in the rule set.

	// memoization: memo maps c -> Boolean
	// pairMap: maps c hash (int) -> int. See produce for why c hash and not c itself.

	static public boolean spanToSpan(
			BSRuleSet gram, 
			Semiring semi,
			CascadeVariableCluster c, 
//			TIntHashSet stateSet, 
			HashSet<CascadeDerivationRule> ruleSet, 
			HashMap<CascadeVariableCluster, Boolean> memo, 
			TObjectIntHashMap pairMap, boolean debug, int indent) throws UnusualConditionException{
		// one non-terminal case - go back to produce
		if (c.numVariables() < 2)
			return produce(gram, semi, 
					new CascadeVariableCluster(c.getVariable(0),c.getString()), 
					//stateSet, 
					ruleSet, memo, pairMap, indent);

		// memoization check
		if (memo.containsKey(c)) {
			Boolean entry = memo.get(c);
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
			CascadeVariableCluster leftSide =  new CascadeVariableCluster(
					c.getVariable(0), 
					c.getString());
			//	    if (debug) Debug.debug(debug, "Epsilon spantospan: trying to match "+leftSide.toString()+"...");
			// if left side not bad, try to get right
			if (produce(gram, semi, leftSide, 
					//stateSet, 
					ruleSet, memo, pairMap, indent+1)) {
				CascadeVariableCluster rightSide = c.getSubCluster(0);
				//		if (debug) Debug.debug(debug, "Epsilon spantospan: ...and now trying the remaining "+rightSide.toString());

				// recurse on the remainder (there must be some - no one-state clusters get to here
				if (spanToSpan(gram, semi, rightSide, 
						//stateSet, 
						ruleSet, memo, pairMap, debug, indent+1)) {
					if (debug) Debug.debug(debug, indent, "Success: "+c.toString()+"["+c.hashCode()+"] "+" with division ["+leftSide.toString()+"["+leftSide.hashCode()+"] "+", "+rightSide.toString()+"["+c.hashCode()+"] "+"]");
					// this cluster alignment is okay. 
					// build a virtual rule, adding each element to pairMap if necessary
					if (!pairMap.containsKey(c)) {
						pairMap.put(c, CascadeVariableCluster.getNextIndex());
						//			if (debug) Debug.debug(debug, "Associating "+c.hashCode()+" with "+pairMap.get(c.hashCode()));
					}
					if (!pairMap.containsKey(leftSide)) {
						pairMap.put(leftSide, CascadeVariableCluster.getNextIndex());
						//			if (debug) Debug.debug(debug, "Associating "+leftSide.hashCode()+" with "+pairMap.get(leftSide.hashCode()));
					}
					if (!pairMap.containsKey(rightSide)) {
						pairMap.put(rightSide, CascadeVariableCluster.getNextIndex());
						//			if (debug) Debug.debug(debug, "Associating "+rightSide.hashCode()+" with "+pairMap.get(rightSide.hashCode()));
					}
					int [] v = {pairMap.get(leftSide), pairMap.get(rightSide)};
					CascadeDerivationRule dr = new CascadeDerivationRule(pairMap.get(c), (Vector)null, v, semi);
					if (debug) Debug.debug(debug, indent, "Created new rule "+dr.toString()+" in epsilon s2s");
					ruleSet.add(dr);
//					stateSet.add(pairMap.get(c));
//					stateSet.add(v[0]);
//					stateSet.add(v[1]);
					foundMatch = true;
				}
			}
		}
		else {
			// normal case - binarize as follows: decide a dividing point. Try to match the first nonterm to that point and the rest to the rest.
			//	    if (debug) Debug.debug(debug, "String span is "+c.getString().getStartIndex()+", "+c.getString().getEndIndex());
			for (int i = c.getString().getEndIndex(); i >= c.getString().getStartIndex(); i--) {
				// produce on the first nonterminal the chosen subspan
				CascadeVariableCluster leftSide =  new CascadeVariableCluster(c.getVariable(0), c.getString().getSubString(c.getString().getStartIndex(), i));
				if (debug) Debug.debug(debug, "spantospan: trying to match "+leftSide.toString()+"...");
				if (!produce(gram, semi, leftSide, 
						//stateSet, 
						ruleSet, memo, pairMap, indent+1))
					continue;
				CascadeVariableCluster rightSide = c.getSubCluster(i);
				if (debug) Debug.debug(debug, "spantospan: ...and now trying the remaining "+rightSide.toString());
				// recurse on the remainder (there must be some - no one-state clusters get to here)
				if (spanToSpan(gram, semi, rightSide, 
						//stateSet, 
						ruleSet, memo, pairMap, debug, indent+1)) {
					//		    if (debug) Debug.debug(debug, indent, "Success: "+c.toString()+" with division ["+leftSide.toString()+", "+rightSide.toString()+"]");
					// build a virtual rule, adding each element to pairMap if necessary
					if (!pairMap.containsKey(c)) {
						pairMap.put(c, CascadeVariableCluster.getNextIndex());
						//			if (debug) Debug.debug(debug, "Associating "+c.hashCode()+" with "+pairMap.get(c.hashCode()));
					}
					if (!pairMap.containsKey(leftSide)) {
						pairMap.put(leftSide, CascadeVariableCluster.getNextIndex());
						//			if (debug) Debug.debug(debug, "Associating "+leftSide.hashCode()+" with "+pairMap.get(leftSide.hashCode()));
					}
					if (!pairMap.containsKey(rightSide)) {
						pairMap.put(rightSide, CascadeVariableCluster.getNextIndex());
						//			if (debug) Debug.debug(debug, "Associating "+rightSide.hashCode()+" with "+pairMap.get(rightSide.hashCode()));
					}
					int [] v = {pairMap.get(leftSide), pairMap.get(rightSide)};
					CascadeDerivationRule dr = new CascadeDerivationRule(pairMap.get(c), (Vector)null, v, semi);
					if (debug) Debug.debug(debug, indent, "Created new rule "+dr.toString()+" in regular s2s");
					ruleSet.add(dr);
//					stateSet.add(pairMap.get(c));
//					stateSet.add(v[0]);
//					stateSet.add(v[1]);
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
	
	
	public static void main(String argv[]) {
		try {
//		TrueRealSemiring s = new TrueRealSemiring();
			RealSemiring s = new RealSemiring();

			String encoding = "euc-jp";
			final boolean isDisk = true;
			final boolean isTree = true;
			final boolean isOTF = false;
			final int timeLevel = 3;
			final boolean doRandomize = false;
			final int maxiter = Integer.parseInt(argv[0]);
			//			TransducerCascadeTraining.setConditional(false);
			Vector trainingSet = TransducerCascadeTraining.readTreeSet(new BufferedReader(new InputStreamReader(new FileInputStream(argv[1]), encoding)), false, isTree, s);
			Vector<TransducerRuleSet> trsvec = new Vector<TransducerRuleSet>();
			TransducerRuleSet trs;
			if (argv.length > 3 || isTree)
				trs = new TreeTransducerRuleSet(argv[2], encoding, s);
			else
				trs = new StringTransducerRuleSet(argv[2], encoding, s);
			trs.setID(0);
			if (doRandomize)
				trs.randomizeRuleWeights();
			trsvec.add(trs);
			Vector<CascadeTransducer> cascvec = new Vector<CascadeTransducer>();
			for (int i = 3; i < argv.length-1; i++) {
				TreeTransducerRuleSet nexttrs = new TreeTransducerRuleSet(argv[i], encoding, s);
				if (doRandomize)
					nexttrs.randomizeRuleWeights();
				nexttrs.setID(i-2);
				trsvec.add(nexttrs);
				cascvec.add(new CascadeBSTreeTransducerRuleSet(nexttrs));
			}
			if (argv.length > 3) { 
				// last transducer. tree or string
				if (isTree) {
					TreeTransducerRuleSet nexttrs = new TreeTransducerRuleSet(argv[argv.length-1], encoding, s);
					if (doRandomize)
						nexttrs.randomizeRuleWeights();
					nexttrs.setID(argv.length-3);
					trsvec.add(nexttrs);
					cascvec.add(new CascadeBSTreeTransducerRuleSet(nexttrs));
				}
				else {
					StringTransducerRuleSet nexttrs = new StringTransducerRuleSet(argv[argv.length-1], encoding, s);
					if (doRandomize)
						nexttrs.randomizeRuleWeights();
					nexttrs.setID(argv.length-3);
					trsvec.add(nexttrs);
					cascvec.add(new CascadeBSStringTransducerRuleSet(nexttrs));	
				}
			}
			int trainingSetSize = ((Integer)trainingSet.get(1)).intValue();
			File trainingSetFile = (File)trainingSet.get(0);
			File derivationsFile = null;
			Vector<CascadeDerivationRuleSet> drsVec = new Vector<CascadeDerivationRuleSet>();
			Vector<TreePair> pairVec = new Vector<TreePair>();
			if (isDisk) {
				derivationsFile = TransducerCascadeTraining.getAllDerivationRuleSets(trs, cascvec, trainingSetSize, 
					trainingSetFile, null, isTree, isOTF, timeLevel);
			}
			else {
				TransducerCascadeTraining.getAllMemDerivationRuleSets(trs, cascvec, trainingSetSize, 
						pairVec, drsVec, trainingSetFile, isTree, isOTF, timeLevel);
			}
			TransducerCascadeTraining.train(trsvec, trainingSetFile, 
					trainingSetSize, derivationsFile, drsVec, pairVec, 
					0, maxiter, false, isDisk);
			for (int i = 2; i < argv.length; i++) {
				TransducerRuleSet traintrs = trsvec.get(i-2);
				//Debug.debug(true, "Before pruning, transducer "+(i-1)+" is\n\n"+traintrs);
				String outFile = argv[i]+".trained";
				traintrs.pruneUseless();
//				Debug.debug(true, "After pruning, transducer "+(i-1)+" is\n\n"+traintrs);

				OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(outFile), encoding);
				w.write(traintrs.toString());
				w.close();
			}
//			trs.pruneUseless();
//			OutputStreamWriter w = new OutputStreamWriter(System.out, encoding);
//			w.write(trs.toString());
			// first, just create the deriv files and exit
			// 	    TransducerRuleSet trs = new StringTransducerRuleSet(argv[1], encoding, s, false);
			// 	    //	    Debug.debug(true, trs.toString());
			// 	    Vector trainingSet = TransducerTraining.readTreeSet(new File(argv[0]), encoding);
			// 	    HashMap drsmap = TransducerTraining.getAllDerivationRuleSets(trs, trainingSet);

			// now, just do training.
			// 	    TransducerRuleSet trs = new StringTransducerRuleSet(argv[1], encoding, s, false);
			// 	    int trainingType = FileDifferentiation.differentiateTraining(new File(argv[0]), encoding);
			// 	    boolean isTree = trainingType >= FileDifferentiation.UNK_TREE_TRAINING;
			// 	    boolean isCount = (trainingType % 3 == FileDifferentiation.COUNT_UNK_TRAINING);
			// 	    Vector trainingSet = TransducerTraining.readTreeSet(new File(argv[0]), encoding, isCount, isTree, s);
			// 	    HashMap table = new HashMap();
			// 	    String filedir = "/tmp/";
			// 	    for (int i = 0; i < trainingSet.size(); i++) {
			// 		File f = new File(filedir+i);
			// 		table.put(trainingSet.get(i), f);
			// 	    }
			// 	    TransducerRuleSet trainedtrs = TransducerTraining.train(trs, table, 0, 5, false);
			// 	    Debug.debug(true, trainedtrs.toString());

			// 	    OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(argv[2]), encoding);
			// 	    Iterator it = drsmap.keySet().iterator();
			// 	    while (it.hasNext()) {
			// 		TreePair tp = (TreePair)it.next();
			// 		w.write(tp.toString()+"\n");
			// 		w.write(((CascadeDerivationRuleSet)drsmap.get(tp)).toString());
			// 	    }
			// 	    w.close();
			//	    TransducerRuleSet newtrs = train(trs, trainingSet, 0, 5, false);
			//	    Debug.debug(true, newtrs.toString());
			// 	    TransducerTraining.generateGraehlData(new File(argv[2]), new File(argv[3]),
			// 						  new File(argv[4]), trs, trainingSet, false);

			// 	    Debug.debug(true, trs.toString());
			// 	    Vector is = Tree.readTreeSet(new File(argv[1]), "utf-8");
			// 	    Vector os = Tree.readTreeSet(new File(argv[2]), "utf-8");
			// 	    Iterator iit = is.iterator();
			// 	    Iterator oit = os.iterator();
			// 	    Vector v = new Vector();
			// 	    while (iit.hasNext() && oit.hasNext()) {
			// 		Tree i = (Tree)iit.next();
			// 		Tree o = (Tree)oit.next();
			// 		Debug.debug(true, i.toString()+", "+o.toString());
			// 		v.add(new TreePair(i, o, 1));
			// 	    }
			// 	    TreeTransducerRuleSet newtrs = train(trs, v, 0, 10, false);
			// 	    Debug.debug(true, newtrs.toString());
			//	    CascadeDerivationRuleSet drs = deriv(trs, i, o);
			// experiment: if I modify the weights of the trs that this drs was made from,
			// is the change propagated to the tr hiding in each dr?
			// 	    Debug.debug(true, drs.toString());
			// 	    HashSet trules = trs.getRules();
			// 	    Iterator trit = trules.iterator();
			// 	    while (trit.hasNext()) {
			// 		TransducerRule tr = (TransducerRule)trit.next();
			// 		tr.setWeight(99);
			// 	    }
			// 	    Debug.debug(true, drs.toString());

			// 	    drs.calculateWeights();
		}
		catch (DataFormatException e) {
			System.err.println("Bad data format reading rtg "+argv[0]);
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
		catch (IOException e) {
			System.err.println("IO error reading rtg "+argv[0]);
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
		catch (UnusualConditionException e) {
			System.err.println("Unusual Condition!");
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
