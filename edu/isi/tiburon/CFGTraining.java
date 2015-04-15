package edu.isi.tiburon;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StreamTokenizer;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Stack;
import java.util.Vector;

import mjr.heap.Heap;

import edu.isi.tiburon.DerivationRuleSet.RuleHeapKey;

// do cfg training with earley parser instead of generic TTT methods
// matches the important methods of RuleSetTraining

public class CFGTraining {
	
	// return vector contains a File and an Integer.
	// unlike RuleSetTraining version, creates StringItems, not TrainingStrings.
	/** Comment character for items */
	static final public int COMMENT = '%';
	
	static public Vector readItemSet(BufferedReader br,
			boolean isWeight, Semiring sr)  throws DataFormatException, 
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
			if (debug) Debug.debug(debug, "Reading string ["+st.sval+"]");
			StringItem item = new StringItem(new StringBuffer(st.sval));
			if (item == null) {
				if (debug) Debug.debug(debug, "Reached null item");
				continue;
			}
			item.weight = count;
			oos.writeObject(item);
			oos.reset();
			if (debug) Debug.debug(debug, "Writing item "+counter+"; "+item);
	

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
	
	// get all the rule sets given a file pointing to items, write them to a file, and return a pointers to the file.
	static public File getAllDerivationRuleSets(CFGRuleSet cfg, int setSize, File setFile, String derivFileName, int timeLevel) {
		boolean debug = false;
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

		for (int i = 0; i < setSize; i++) {
//			long preread = Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();
	// 	    if (debug) Debug.debug(debug, "before read: heap at "+preread);
			StringItem item = null;
			try {
				item = (StringItem)tsois.readObject();
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
	//		long postread = Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();
	 //	    if (debug) Debug.debug(debug, "after read: heap at "+postread);
			if (debug) Debug.debug(debug, "Attempting to derive "+i+" of "+setSize+": "+item);
			Date preDerivTime = new Date();
	//		long preparse = Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();
	// 	    if (debug) Debug.debug(debug, "before parse: heap at "+preparse);
			// no beaming in training
			HashSet<EarleyState>[][] chart = cfg.parse(item, 0, timeLevel);
	//		long postparse = Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();
	// 	    if (debug) Debug.debug(debug, "after parse: heap at "+postparse);

			

			int start = cfg.s2i(cfg.startState);
			Vector<EarleyState> prunedChart = new Vector<EarleyState>();
			// TODO: what about top items that don't reach the whole span?
			if (chart == null || chart[start][0] == null) {
				badItems++;
				Debug.prettyDebug("Warning: Item "+item.toString()+" could not be explained by transducer. Removing from training set");
				Debug.prettyDebug("So far: "+badItems+" bad items, and "+goodItems+" good items");
			}
			else {
				boolean foundOne = false;
				for (EarleyState es : chart[start][0]) {
					if (es.stringEndPos == item.getSize()) {
						foundOne = true;
						prunedChart.add(es);
						if (debug) Debug.debug(debug, "Adding "+es+" to pruned chart");
					}
				}
				if (foundOne) {
					goodItems++;
				}
				else {
					badItems++;
					Debug.prettyDebug("Warning: Item "+item.toString()+" could not be explained by transducer. Removing from training set");
					Debug.prettyDebug("So far: "+badItems+" bad items, and "+goodItems+" good items");

				}
			}
			Date postDerivTime = new Date();
			Debug.dbtime(timeLevel, 3, preDerivTime, postDerivTime, "calculate derivation rule set");
		//	long prewrite = Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();
	 	  //  if (debug) Debug.debug(debug, "before write: heap at "+prewrite);
			Date preWriteTime = new Date();
			try {
				if (debug) Debug.debug(debug, "Writing pruned chart to disk");
				dsoos.writeObject(prunedChart);
				dsoos.reset();
			}
			catch (IOException e) {
				System.err.println("Problem while trying to write derivation of "+
						item+" to file: "+e.getMessage());
				System.exit(1);
			}
			chart = null;
			prunedChart = null;
			Date postWriteTime = new Date();
			Debug.dbtime(timeLevel, 3, preWriteTime, postWriteTime, "write derivation rule set to disk");
			// 	    if (debug) Debug.debug(debug, "Memo now has "+memo.size()+" entries");
			//System.gc();
		//	long afterwrite = Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();
		//	if (debug) Debug.debug(debug, "after write: heap at "+afterwrite);
			int diff = EarleyState.statesMade-EarleyState.statesKilled;
			// Debug.prettyDebug(diff+": made "+EarleyState.statesMade+", killed "+EarleyState.statesKilled);
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

	// jon's train algorithm - set weights on rules
	// TODO: add normalization function
	// TODO: add prior counts
	static public CFGRuleSet train(CFGRuleSet cfg, 
			File setFile,
			int setSize,
			File derivFile,
			double epsilon,
			int maxit,
			int timeLevel,
			boolean wasNorm) throws UnusualConditionException {
		boolean debug = false;
		Semiring semiring = cfg.getSemiring();
		// don't modify old rs
		CFGRuleSet newcfg = new CFGRuleSet(cfg);
	
		
//		// assign ints to rules
//		Hashtable<Rule, Integer> r2i = new Hashtable();
//		Hashtable<Integer, Rule> i2r = new Hashtable();
//		
//		int nextRule=0;
//		for (Rule r : cfg.rules) {
//			r2i.put(r, nextRule);
//			i2r.put(nextRule++, r);
//		}
		
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
		
		// easiest access to rules
		CFGRule[] rulearr = newcfg.getRuleArr();
		
		while ((epsilon == 0 || delta >= epsilon) && itno < maxit) {
			// TODO - use dirichilet priors
			// TODO - not have to do this every iteration
			// for now, if the rule isn't in the hashmap, count is assumed to be 0

			Date preIt = new Date();
			if (debug) Debug.debug(debug, "Iteration "+itno);
			//	    debug = tempdebug;
			double[] counts = new double[cfg.getNumRules()];
			// TODO - use variable method for Z?
			double[] Z = new double[cfg.getNumStates()];
			
			
			// initialize for appropriate semiring
			for (int i = 0; i < counts.length; i++)
				counts[i] = semiring.ZERO();
			// initialize for appropriate semiring
			for (int i = 0; i < Z.length; i++)
				Z[i] = semiring.ZERO();
			
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
				Vector<EarleyState> chartvec = null;
				StringItem item = null;
				try {
					item = (StringItem)setois.readObject();
					chartvec = (Vector<EarleyState>)drsois.readObject();
					if (chartvec == null || chartvec.size() == 0)
						continue;
				}
				catch (IOException e) {
					System.err.println("Problem while trying to read in a derivation and item "+e.getMessage());
					System.exit(1);
				}
				catch (ClassNotFoundException e) {
					System.err.println("Class-casting problem while trying to read in a derivation and item "+e.getMessage());
					System.exit(1);
				}
				if (chartvec == null || item == null) {
					System.err.println("Reading of training example completed, but objects are null; continuing...");
					continue;
				}
				if (debug) Debug.debug(debug, "item is "+item);
				
				
				// calculate initial betas and alphas
				
				// stolcke propagation method
				for (EarleyState es : chartvec) {
					es.resetBeta();
					es.resetAlpha(semiring);
				}
				double totalBeta = semiring.ZERO();
				Stack<EarleyState> alphaList = new Stack<EarleyState>();
				for (EarleyState es : chartvec) {
					es.setBeta(semiring, rulearr, alphaList);
					totalBeta = semiring.plus(totalBeta, es.beta);
				}
				if (debug) Debug.debug(debug, "Total beta is "+totalBeta);
				// initial alphas = PI(beta of siblings)
//				if (chartvec.size() > 1) {
//					for (EarleyState es1 : chartvec) {
//						double temp = semiring.ZERO();
//						for (EarleyState es2 : chartvec) {
//							if (es2 == es1)
//								continue;
//							temp = semiring.plus(temp, es2.beta);
//						}
//						if (debug) Debug.debug(debug, "Alpha for "+es1+" is "+temp);
//						es1.alpha = temp;
//					}
//				}
//				else {
				
					//chartvec.get(0).alpha = semiring.ONE();
				//}

				// top alphas all 1? 
				for (EarleyState es : chartvec) {
					es.alpha = semiring.ONE();
					if (debug) Debug.debug(debug, "Set alpha of top state "+es+" to "+es.alpha);
				}
				
				
				// go through the list in the reverse order we added it (adds went to end)
				while (!alphaList.isEmpty()) {
					EarleyState es = alphaList.pop();
					es.setAlpha(semiring, rulearr);
				}
				
				
//				// collect counts for rule, z
//				double[] localcounts = new double[cfg.getNumRules()];
//				// TODO - use variable method for Z?
//				double[] localZ = new double[cfg.getNumStates()];
//				
//				// initialize for appropriate semiring
//				for (int i = 0; i < counts.length; i++)
//					localcounts[i] = semiring.ZERO();
//				// initialize for appropriate semiring
//				for (int i = 0; i < Z.length; i++)
//					localZ[i] = semiring.ZERO();
				
				// modify inner counts by division by beta of entire thing
				double inv = semiring.inverse(totalBeta);
				for (EarleyState es : chartvec) {
					es.collectCounts(semiring, counts, Z, rulearr, inv);
				}
				// update global counts
//				if (debug) Debug.debug(debug, "Updating globals with locals divided by "+totalBeta);
//				
//				if (debug) Debug.debug(debug, "Rules");
//				
//				for (int i = 0; i < counts.length; i++) {
//					double adjcount = semiring.times(localcounts[i], inv);
//					double newcount = semiring.plus(counts[i], adjcount);
////					if (debug) Debug.debug(debug, i+" from "+counts[i]+" with "+localcounts[i]+" / "+totalBeta+" = "+newcount);
//					counts[i] = newcount;
//				}
//				if (debug) Debug.debug(debug, "Zs");
//				for (int i = 0; i < Z.length; i++) {
//					double adjZ = semiring.times(localZ[i], inv);
//					double newZ = semiring.plus(Z[i], adjZ);
//	//				if (debug) Debug.debug(debug, i+" from "+Z[i]+" with "+localZ[i]+" / "+totalBeta+" = "+newZ);
//					Z[i] = newZ;
//				}
				L = semiring.times(L, semiring.times(totalBeta, item.getWeight()));
				if (!totalNodesSet)
					totalNodes += (item.getSize()*semiring.internalToPrint(item.getWeight()));
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
			// update rule weights
			for (int i = 0; i < rulearr.length; i++) {
				Rule r = rulearr[i];
				double count = counts[i];
				double denom = Z[cfg.s2i(r.lhs)];
				double inv = semiring.inverse(denom);
				double newval;
				if (semiring.betteroreq(semiring.ZERO(), count))
					newval = semiring.ZERO();
				else
					newval = semiring.times(count, inv);
				if (debug) Debug.debug(debug, "Setting "+r+" to "+count+" / "+denom+" = "+newval);
				r.setWeight(newval);
			}
			delta = (L - lastL)/Math.abs(L);
			if (debug) Debug.debug(debug, "L is "+L+", lastL is "+lastL);
			if (debug) Debug.debug(debug, "Iteration changed L by "+delta);
			lastL = L;
			Date postIt = new Date();
			Debug.dbtime(timeLevel, 2, preIt, postIt, "perform iteration "+itno);
			if (debug) {
				Debug.prettyDebug("At end of iteration, rule set is ");
				Debug.prettyDebug(newcfg.toString());
			}
			
			itno++;
		}
		return newcfg;
	}

	// 
	
	// update betas array by traversing the chart
	// beta as array is state, start, end, isFinished (nonvirtual). 0=virtual, 1=non-virtual
/*	private static void calculateBetas(
			CFGRuleSet.EarleyState item,
			double[][][][] betas,
			boolean[][][][] seenbetas,
//			Hashtable<EarleyState, Double> betas, 
			int indent,
			Semiring semiring,
			boolean isFirst) {
		boolean debug = true;
		
//		// already done
		//if (betas.containsKey(item)) {
		if (!isFirst && seenbetas[item.src][item.stringStartPos][item.stringEndPos][item.isFinished ? 1 : 0]) {
			if (debug) Debug.debug(debug, indent, "Beta for "+item+" already determined");
			return;
		}
		if (debug) Debug.debug(debug, indent, "Getting beta for "+item);

		// sum up each member in the vector
		// virtuals are not completed. non-virtuals are completed
		// beta for a rule is its weight times the beta of its children
		// if no rule weight (ie it is a virtual) it's just the children
		double newBeta = semiring.ZERO();
		if (item.next != null) {
			for (CFGRuleSet.EarleyState[] es : item.next) {
				if (debug) Debug.debug(debug, indent, "Getting beta for Rule "+item);
				double contrib = (item.isFinished ? item.rule.getWeight() : semiring.ONE()); 
				double origcontrib = contrib;
				if (es[0].next != null) {
					calculateBetas(es[0], betas, seenbetas, indent+1, semiring, false);				
				//	contrib = semiring.times(contrib, betas.get(es[0]));
					contrib = semiring.times(contrib, betas[es[0].src][es[0].stringStartPos][es[0].stringEndPos][es[0].isFinished ? 1 : 0]);
					if (debug) Debug.debug(debug, indent, "partial Beta for rule"+item+" to "+contrib+" : added "+betas[es[0].src][es[0].stringStartPos][es[0].stringEndPos][es[0].isFinished ? 1 : 0]);

				}
				if (es[1] != null && es[1].next != null) {
					calculateBetas(es[1], betas, seenbetas, indent+1, semiring, false);
					//contrib = semiring.times(contrib, betas.get(es[1]));
					contrib = semiring.times(contrib, betas[es[1].src][es[1].stringStartPos][es[1].stringEndPos][es[1].isFinished ? 1 : 0]);
					if (debug) Debug.debug(debug, indent, "partial Beta for rule"+item+" to "+contrib+" : added "+betas[es[1].src][es[1].stringStartPos][es[1].stringEndPos][es[1].isFinished ? 1 : 0]);

					if (debug) Debug.debug(debug, indent, "For "+item+", adding "+
							origcontrib+" * "+
							betas[es[0].src][es[0].stringStartPos][es[0].stringEndPos][es[0].isFinished ? 1 : 0]+" * "+
							betas[es[1].src][es[1].stringStartPos][es[1].stringEndPos][es[1].isFinished ? 1 : 0]+" = "+
							contrib+" to "+newBeta);
				}
//				else {
//					if (debug) Debug.debug(debug, indent, "For "+item+", adding "+
//							origcontrib+" * "+
//							betas[es[0].src][es[0].stringStartPos][es[0].stringEndPos][es[0].isFinished ? 1 : 0]+" = "+
//							contrib+" to "+newBeta);
//					if (debug) Debug.debug(debug, indent, "Adding "+contrib+" to "+newBeta);
//				}
				if (debug) Debug.debug(debug, indent, "Adding "+newBeta+" to "+contrib);
				newBeta = semiring.plus(newBeta, contrib);
				if (debug) Debug.debug(debug, indent, "beta for "+item+" to "+newBeta);

			}
		}
//		if (debug) Debug.debug(debug, indent, "beta for "+item+" finished at "+newBeta);
		if (seenbetas[item.src][item.stringStartPos][item.stringEndPos][item.isFinished ? 1 : 0]) {
			double oldval = betas[item.src][item.stringStartPos][item.stringEndPos][item.isFinished ? 1 : 0];
			double oldNewBeta = newBeta;
			newBeta = semiring.plus(oldval, oldNewBeta);
	//		if (debug) Debug.debug(debug, "adding "+oldNewBeta+" to "+oldval+" to make "+newBeta);
		}
		betas[item.src][item.stringStartPos][item.stringEndPos][item.isFinished ? 1 : 0] = newBeta;
		seenbetas[item.src][item.stringStartPos][item.stringEndPos][item.isFinished ? 1 : 0] = true;
//		betas.put(item, newBeta);
	}
*/	
	// alpha of a state x_i is sum, for all r: z -> x_1 ... x_i-1, x_i, x_i+1 ..., x_k
	// p(r) * alpha(z) * PI_n:1..i-1 beta(x_n) * PI_m:i+1..k beta(x_m)
	// but tails are only unary or binary
	
	// we're actually calculating alphas on the tails of this item, not the item itself!
	// alphas should be seeded with root alpha!
	
	// TODO: if this doesn't work it could be due to alphas being used before they're finished, in which case my traversal
	// strategy must change!
/*	private static void calculateAlphas(
			CFGRuleSet.EarleyState item,
			Hashtable<EarleyState, Double> betas,
			Hashtable<EarleyState, Double> alphas,
			int indent,
			Semiring semiring) {
		boolean debug = true;
		if (item.next == null)
			return;
		for (CFGRuleSet.EarleyState[] es : item.next) {
			// set alpha for cell 0
			double partial = alphas.get(item);
			// virtuals are not completed. non-virtuals are completed
			// beta for a rule is its weight times the beta of its children
			// if no rule weight (ie it is a virtual) it's just the children
			partial = semiring.times(partial, (item.isFinished ? item.rule.getWeight() : semiring.ONE()));
			// if there's a sibling, get beta for cell 1 and repeat this with the other
			if (debug) Debug.debug(debug, indent, "Contributing alpha for "+es[0]+" coming from "+item+" with "+partial);
			if (es[1] != null) {
				double full = semiring.times(partial, betas.get(es[1]));
				if (debug) Debug.debug(debug, indent, "With beta of "+es[1]+" = "+betas.get(es[1])+" it's "+full);
				if (alphas.containsKey(es[0])) {
					full = semiring.plus(full, alphas.get(es[0]));
					if (debug) Debug.debug(debug, indent, "With previous alpha value it's "+full);
				}
				alphas.put(es[0], full);
				if (debug) Debug.debug(debug, indent, "Contributing alpha for "+es[1]+" coming from "+item+" with "+partial);
				full = semiring.times(partial, betas.get(es[0]));
				if (debug) Debug.debug(debug, indent, "With beta of "+es[0]+" = "+betas.get(es[0])+" it's "+full);
				if (alphas.containsKey(es[1])) {
					full = semiring.plus(full, alphas.get(es[1]));
					if (debug) Debug.debug(debug, indent, "With previous alpha value it's "+full);
				}
				alphas.put(es[1], full);
				calculateAlphas(es[0], betas, alphas, indent+1, semiring);
				calculateAlphas(es[1], betas, alphas, indent+1, semiring);
			}
			else {
				double full = partial;
				if (debug) Debug.debug(debug, indent, "With no siblings it stays "+full);
				if (alphas.containsKey(es[0])) {
					full = semiring.plus(full, alphas.get(es[0]));
					if (debug) Debug.debug(debug, indent, "With previous alpha value it's "+full);
				}
				alphas.put(es[0], full);
				calculateAlphas(es[0], betas, alphas, indent+1, semiring);
			}
		}
	}
*/	

	public static void main(String argv[]) {
		RealSemiring semiring = new RealSemiring();

		try {
		
			BufferedReader br1 = new BufferedReader(new InputStreamReader(new FileInputStream(argv[0])));
			Vector trainingSet = readItemSet(br1, false, semiring);
			int size = ((Integer)trainingSet.get(1)).intValue();
			File treeFile = (File)trainingSet.get(0);
			CFGRuleSet cfg = new CFGRuleSet(argv[1], "utf-8", semiring);

			File derivFile = getAllDerivationRuleSets(cfg, size, treeFile, "a.deriv", 0);
			train(cfg, treeFile, size, derivFile, 0, 2, 0, false);
			Debug.prettyDebug(cfg+"");
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
		
