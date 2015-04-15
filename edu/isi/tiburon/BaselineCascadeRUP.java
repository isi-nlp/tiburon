package edu.isi.tiburon;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.OutputStreamWriter;
import java.util.Date;
import java.util.Vector;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Switch;
import com.martiansoftware.jsap.UnflaggedOption;
import com.martiansoftware.jsap.stringparsers.DoubleStringParser;
import com.martiansoftware.jsap.stringparsers.EnumeratedStringParser;
import com.martiansoftware.jsap.stringparsers.FileStringParser;
import com.martiansoftware.jsap.stringparsers.IntegerStringParser;
import com.martiansoftware.jsap.stringparsers.StringStringParser;


public class BaselineCascadeRUP {

	public static final int RTGPOS = 0;
	public static int TRSPOS = -1;
	public static int STRPOS = -1;
	
	// create a summary of a rule set (cfg or rtg) and add it to a buffer
	private static void getRuleSetCheck(StringBuffer buffer, String name, RuleSet rs) {
		if (rs instanceof RTGRuleSet)
			buffer.append("RTG info for "+name+":\n");
		else if (rs instanceof CFGRuleSet)
			buffer.append("CFG info for "+name+":\n");
		buffer.append("\t"+rs.getNumStates()+" states\n");
		buffer.append("\t"+rs.getNumRules()+" rules\n");
		buffer.append("\t"+rs.getNumTerminals()+" unique terminal symbols\n");
		if (rs.isFinite(false))
			buffer.append("\t"+rs.getNumberOfDerivations()+" derivations\n");
		else
			buffer.append("\t"+"infinite derivations\n");
	}
	// everything having to do with the JSAP parameters and config exceptions based on this. 
	// Sets the jsap object
	private static JSAPResult processParameters(JSAP jsap, String[] argv) throws ConfigureException, JSAPException {

		// HELP OPTION
		Switch helpsw = new Switch("help",
				'h',
				"help",
		"print this help message");
		jsap.registerParameter(helpsw);

		// OPTIONS REGARDING THE FUNDAMENTALS OF DATA INPUT


		// format of the input (and output data) - assumed utf-8 but can be changed here
		FlaggedOption encodingopt = new FlaggedOption("encoding",
				StringStringParser.getParser(),
				"utf-8",
				true,
				'e',
				"encoding",
				"encoding of input and output files, if other than utf-8. Use the same "+
		"naming you would use if specifying this charset in a java program");
		jsap.registerParameter(encodingopt);
		// OPTIONS REGARDING THE DATA THAT IS INPUT

		// semiring specification: how do we combine the numbers?
		FlaggedOption semiringtype = 
			new FlaggedOption("srtype", 
					EnumeratedStringParser.getParser("real; tropical; tropicalaccumulative; truereal"),
					"real",
					true,
					'm',
					"semiring",
					"type of weights: can be real (probabilities), truereal (probabilities with underflow), tropical "+
			"(max log weights), or tropicalaccumulative (summed log weights)");
		jsap.registerParameter(semiringtype);
	

		FlaggedOption beamopt = new FlaggedOption("beam",
				IntegerStringParser.getParser(),
				"0",
				false,
				'b',
				"beam",
				"allow a maximum of <beam> rules per state to be formed when composing, intersecting, or applying.");
		jsap.registerParameter(beamopt);

		FlaggedOption batchsizeopt = new FlaggedOption("batchsize",
				IntegerStringParser.getParser(),
				null,
				false,
				's',
				"batchsize",
				"only process first <batchsize> members.");
		jsap.registerParameter(batchsizeopt);

		FlaggedOption pruneopt1 = new FlaggedOption("prune1",
				DoubleStringParser.getParser(),
				null,
				false,
				'1',
				"prune1",
				"Pruning for all but penultimate step <prune> "+
				"greater than the score of the best tree. ");
		jsap.registerParameter(pruneopt1);
		
		FlaggedOption pruneopt2 = new FlaggedOption("prune2",
				DoubleStringParser.getParser(),
				null,
				false,
				'2',
				"prune2",
				"Pruning for penultimate step <prune> "+
				"greater than the score of the best tree. ");
		jsap.registerParameter(pruneopt2);

		

		// OPTIONS REGARDING THE DATA THAT IS OUTPUT

		// print timing information to stderr. number determines level of information
		FlaggedOption timeopt = new FlaggedOption("time",
				IntegerStringParser.getParser(),
				null,
				false,
				JSAP.NO_SHORTFLAG,
				"time",
				"Print timing information to stderr at a variety of levels: 0+ for "+
		"total operation, 1+ for each processing stage, 2+ for small info");
		jsap.registerParameter(timeopt);

	

		// OPTIONS REGARDING THE FUNDAMENTALS OF DATA OUTPUT

		

		// set of input files. Sometimes it doesn't make sense to have more than one. In these cases
		// a warning is generated and only the first is used
		UnflaggedOption infileopt = new UnflaggedOption("infiles",
				FileStringParser.getParser(),
				null,
				true,
				true,
				"list of input files assumed to be one rtg, one or more tree-tree trans, "+
				"one string trans, and one string batch. ");
		jsap.registerParameter(infileopt);


		JSAPResult config = jsap.parse(argv);
		// make sure train isn't set with bad input options
		return config;
	}
	
	// read in files, assumed to be an rtg, one or more tree-tree transducers, one tree-string transducer,
	// and one file with multiple strings
	private static Object[] readAutomata(Vector<BufferedReader> brs, File[] infiles, 
			JSAPResult config, String encoding, 
			Semiring ts, int timeLevel, OutputStreamWriter w) throws IOException, 
			DataFormatException, ConfigureException {
		boolean debug = false;
		if (debug) Debug.debug(debug, "Debugging on");
		

		
		// read in all items (batch file is deferred)
		Object[] fileObjects = new Object[infiles.length];
		// read in rtg and convert to tree-tree trans
		Date preRTGTime = new Date();
		RTGRuleSet rtg = new RTGRuleSet(brs.get(RTGPOS), ts);
		rtg.makeNormal();
		fileObjects[RTGPOS] = rtg;
		Date postRTGTime = new Date();
		Debug.dbtime(timeLevel, 1, preRTGTime, postRTGTime,  "read rtg "+infiles[RTGPOS].getName());

		// read in set of tree-tree trans
		for (int i = RTGPOS+1; i < TRSPOS; i++) {
			Date preTTTime = new Date();
			fileObjects[i] = new TreeTransducerRuleSet(brs.get(i), ts);
			Date postTTTime = new Date();
			Debug.dbtime(timeLevel, 1, preTTTime, postTTTime,  "read ttt "+infiles[i].getName());

		}
		
		// read in tree-string trans
		Date preTSTime = new Date();
		fileObjects[TRSPOS] = new StringTransducerRuleSet(brs.get(TRSPOS), ts);
		Date postTSTime = new Date();
		Debug.dbtime(timeLevel, 1, preTSTime, postTSTime,  "read tst "+infiles[TRSPOS].getName());

		// read in string set
		Date preStrTime = new Date();
		fileObjects[STRPOS] = CFGTraining.readItemSet(brs.get(STRPOS), false, ts);
		Date postStrTime = new Date();
		Debug.dbtime(timeLevel, 1, preStrTime, postStrTime,  "read string batch "+infiles[STRPOS].getName());
		return fileObjects;
	}
	
	
	// load files into buffered readers. detect stdin here and prevent multiple stdins.
	private static Vector<BufferedReader> loadFiles(File[] infiles, String encoding) throws ConfigureException, FileNotFoundException, 
	IOException {
		boolean debug = false;
		Vector<BufferedReader> ret = new Vector<BufferedReader>();
		// track stdin; should only see once
		boolean seenstdin = false;
		for (int i = 0; i < infiles.length; i++) {
			File f = infiles[i];
			BufferedReader br = null;
			if (f.getName().equals("-")) {
				if (seenstdin)
					throw new ConfigureException("Can only reference stdin (-) once in the list of files");
				seenstdin = true;
				Debug.debug(debug, "Reading from stdin");
				br = new BufferedReader(new InputStreamReader(System.in, encoding));
			}
			else {
				Debug.debug(debug, "Reading from "+f.getName());
				br = new BufferedReader(new InputStreamReader(new FileInputStream(f), encoding));
			}
			ret.add(br);
		}
		return ret;
	}
	
	// spillover cascade. parse a string with TreeString Transducer, then each of the
	// tree-tree transducers, then take domain projection

	private static RTGRuleSet combineAutomata( Object[] fileObjects, 
			JSAPResult config,
			int timeLevel, OutputStreamWriter w, Semiring ts) throws ConfigureException, 
			ImproperConversionException,
			IOException,
			UnusualConditionException {
		boolean debug = true;
		
		StringBuffer outputBuffer = new StringBuffer();

		if (debug) Debug.debug(debug, "Debug on");
		if (debug) Debug.debug(debug, "Beam of "+config.getInt("beam"));

		// parse string on tree-string transducer
		Date preParseTime = new Date();
		StringItem str = (StringItem)fileObjects[STRPOS];
		StringTransducerRuleSet tst = (StringTransducerRuleSet)fileObjects[TRSPOS];
		RTGRuleSet rtg = new RTGRuleSet(tst, str, config.getInt("beam"), timeLevel);
		if (config.contains("prune1")) {
			double p1 = config.getDouble("prune1");
			KBest k = new KBest(rtg);
			if (debug) Debug.debug(debug, "Pruning with "+p1);
			k.pruneRules(p1);
			getRuleSetCheck(outputBuffer, "RTG from string right-applied to XRS, pruned to "+p1, rtg);
		}
		else {
			getRuleSetCheck(outputBuffer, "RTG from string right-applied to XRS (no pruning)", rtg);
		}
		w.write(outputBuffer.toString());
		w.flush();
		outputBuffer = new StringBuffer();
		Date postParseTime = new Date();
		Debug.dbtime(timeLevel, 3, preParseTime, postParseTime, "Parse string on TST");
		

		
		// pass rtg through each tree-tree transducer
		for (int i = TRSPOS-1; i > RTGPOS; i--) {
			Date preCompTime = new Date();
			rtg.removeEpsilons();
			TreeTransducerRuleSet rightttt = new TreeTransducerRuleSet(rtg);
			//		if (debug) Debug.debug(debug, ">>>Turned rtg:\n"+rs+">>>Into\n"+rtgtrs);
			TreeTransducerRuleSet leftttt = (TreeTransducerRuleSet)fileObjects[i];

			Vector<TreeTransducerRuleSet> vec = new Vector<TreeTransducerRuleSet>();
			vec.add(leftttt);
			vec.add(rightttt);
			TransducerRuleSet comp = new TreeTransducerRuleSet(vec, true, config.getInt("beam"));
			//TransducerRuleSet comp = new TreeTransducerRuleSet(lefttrs, rtgtrs, config.getInt("beam"));
			comp.pruneUseless();
			if (comp.isExtended()) {
				comp.makeNonExtended();
			}
			rtg = new RTGRuleSet(comp);
			// special penultimate case
			if (i == RTGPOS+1 && config.contains("prune2")) {
				double p2 = config.getDouble("prune2");
				KBest k = new KBest(rtg);
				if (debug) Debug.debug(debug, "Pruning with "+p2);
				k.pruneRules(p2);
				getRuleSetCheck(outputBuffer, "RTG from result through TTT"+i+", pruned to "+p2, rtg);

			}
			else if (config.contains("prune1")) {
				double p1 = config.getDouble("prune1");
				KBest k = new KBest(rtg);
				if (debug) Debug.debug(debug, "Pruning with "+p1);
				k.pruneRules(p1);
				getRuleSetCheck(outputBuffer, "RTG from result through TTT"+i+", pruned to "+p1, rtg);
			}
			else {
				getRuleSetCheck(outputBuffer, "RTG from result through TTT"+i+" (no pruning)", rtg);
			}
			w.write(outputBuffer.toString());
			w.flush();

			outputBuffer = new StringBuffer();
			Date postCompTime = new Date();
			Debug.dbtime(timeLevel, 3, preCompTime, postCompTime, "Pass result through TTT "+i);
		}

		// intersect with rtg
		Date preIsectTime = new Date();
		RTGRuleSet rtga = (RTGRuleSet)fileObjects[RTGPOS];
		rtga.removeEpsilons();
		rtga.makeNormal();
		rtg.removeEpsilons();
		rtg.makeNormal();
		rtg = Intersect.intersectRuleSets(rtga, rtg);
		
		KBest k = new KBest(rtg);
		getRuleSetCheck(outputBuffer, "RTG from intersection with front RTG (no pruning)", rtg);
		w.write(outputBuffer.toString());
		w.flush();
		
		return rtg;
	}

	
	// calculate inside cost and 1-best for rule set
	
	private static void generateOutput(RTGRuleSet rtg, JSAPResult config, 
			int timeLevel, OutputStreamWriter w) throws IOException, 
			ConfigureException,
			UnusualConditionException {
		boolean debug = true;
		if (rtg.rules == null || rtg.rules.size() == 0) {
			w.write("NO OUTPUT\n");
			w.flush();
			return;
		}
		KBest k = new KBest(rtg);
		double modelCost = k.getModelCost();
		TreeItem[] top = (TreeItem[])k.getKBestItems(1);
		w.write("model cost: "+modelCost+"\n");
		w.write(top[0]+" # "+rtg.getSemiring().internalToPrint(top[0].getWeight())+"\n");
		w.flush();
	}
	
	
	public static void main(String argv[]) throws Exception {
		boolean debug = false;

		Debug.prettyDebug("Baseline experiment for backward string cascade with RUP in between");

		Date startTime = new Date();
		// parameter processor and configuration settings
		JSAP jsap = new JSAP();
		JSAPResult config = null;
		int timeLevel = -1;

		// encoding of read and written files
		String encoding = null;
		// semiring of all operations
		Semiring ts = null;
		
	
		
		// what we're reading
		File infiles[] = null;
		

		// the input files, once they're read in
		// they can be lots of different things
		Object[] fileObjects = null;

		// the main object we write to
		OutputStreamWriter w = null;



		// 1) Set up all parameters. Die on bad combinations.

		Date registerAllParametersTime = new Date();          	
		try {
			config =  processParameters(jsap, argv);
			String srtype = config.getString("srtype");
			encoding = config.getString("encoding");
			Debug.setEncoding(encoding);
			if (config.contains("time")) {
				timeLevel = config.getInt("time", -1);
				Debug.setDbLevel(timeLevel);
			}
			if (srtype.equals("real"))
				ts = new RealSemiring();
			else if (srtype.equals("tropical"))
				ts = new TropicalSemiring();
			else if (srtype.equals("tropicalaccumulative"))
				ts = new TropicalAccumulativeSemiring();
			else if (srtype.equals("truereal"))
				ts = new TrueRealSemiring();
			else
				throw new ConfigureException("Unexpected semiringtype: "+srtype);

			

			
			infiles = config.getFileArray("infiles");
			fileObjects = new Object[infiles.length];

		}
		catch (JSAPException e) {
			System.err.println("Tiburon options improperly configured: "+e.getMessage());
			System.err.println("Try 'tiburon -h` for a detailed help message");
			System.exit(1);
		}

		catch (ConfigureException e) {
			System.err.println("Tiburon options improperly configured: "+e.getMessage());
			System.err.println("Try 'tiburon -h` for a detailed help message");
			System.exit(1);
		}


		if (config.getBoolean("help")) {
			Debug.prettyDebug("Usage: tiburon ");
			Debug.prettyDebug("             "+jsap.getUsage());
			Debug.prettyDebug("");
			Debug.prettyDebug(jsap.getHelp());
			System.exit(0);
		}


		if (!config.success()) {
			for (java.util.Iterator errs = config.getErrorMessageIterator();
			errs.hasNext();) {
				Debug.prettyDebug("Error: " + errs.next());
			}

			Debug.prettyDebug("Usage: tiburon ");
			Debug.prettyDebug("             "+jsap.getUsage());
			System.exit(1);
		}
		Date configureParametersTime = new Date();
		Debug.dbtime(timeLevel, 2, registerAllParametersTime, configureParametersTime,  "register and configure parameters");

		// 2) Detect all file types. Read in automata. Open output handles

		try {
			// load the files as bufferedreaders
			Date preLoadTime = new Date();
			Vector<BufferedReader> brs = loadFiles(infiles, encoding);
			Date postLoadTime = new Date();
			Debug.dbtime(timeLevel, 1, preLoadTime, postLoadTime,  "loaded files");

			// set up file numbers for future reference. should be at least 4 items
			if (infiles.length < 4)
				throw new ConfigureException("Need at least 4 items in chain");
			TRSPOS = infiles.length-2;
			STRPOS = infiles.length-1;

			// read in the automata and pre-process the training info
			Date preReadObjectTime = new Date();
			fileObjects = readAutomata(brs, infiles, config, encoding, ts, timeLevel, w);
			Date postReadObjectTime = new Date();
			Debug.dbtime(timeLevel, 1, preReadObjectTime, postReadObjectTime,  "read all files");

		}
		catch (ConfigureException e) {
			System.err.println("Illegal sequence of input files: "+e.getMessage());
			System.exit(1);
		}

		catch (FileNotFoundException e) {
			System.err.println("Input file not found: "+e.getMessage());
			System.exit(1);
		}
		catch (DataFormatException e) {
			// TODO: include context info here
			System.err.println("Syntax error while detecting or reading input file: "+e.getMessage());
			System.exit(1);

		}
		catch (IOException e) {
			System.err.println("Problem processing input file: "+e.getMessage());
			System.exit(1);
		}
		catch (Exception e) {
			System.err.println("Throwing generic exception while detecting input file of type "+e.getClass().toString());
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


		// 3) form final automata and obtain desired output

		try {
			// either do all members of batch or first n if specified in config
			int batchSize = 1;
			if (config.contains("batchsize")) {
				batchSize = config.getInt("batchsize");
			}
			else {
				batchSize = ((Integer)((Vector)fileObjects[STRPOS]).get(1)).intValue();
			}
			
			w = new OutputStreamWriter(System.out, encoding);
			
			// holds all batch items
			ObjectInputStream stringis = new ObjectInputStream(new FileInputStream((File)((Vector)fileObjects[STRPOS]).get(0)));

			// do all intersections and compositions, obtain inside cost and best item
			Date preMasterCombineTime = new Date();
			for (int itNum = 0; itNum < batchSize; itNum++) {
				if (debug) Debug.debug(debug, "Batch item "+itNum+" of "+batchSize);
				Date preCombineTime = new Date();
				StringItem str = (StringItem)stringis.readObject();
				fileObjects[STRPOS] = str;
				RTGRuleSet rtg = combineAutomata(fileObjects, config, timeLevel, w, ts);
				generateOutput(rtg, config, timeLevel, w);
				Date postCombineTime = new Date();
				Debug.dbtime(timeLevel, 2, preCombineTime, postCombineTime,  "full cascade on member "+itNum);

			}
			Date postMasterCombineTime = new Date();
			Debug.dbtime(timeLevel, 1, preMasterCombineTime, postMasterCombineTime,  "full cascade on all "+batchSize+" members");
			w.close();
		}

		catch (FileNotFoundException e) {
			System.err.println("Input file not found: "+e.getMessage());
			System.exit(1);
		}
		catch (ImproperConversionException e) {
			System.err.println("Improper conversion: "+e.getMessage());
			System.exit(1);

		}
		// this one should be from associativity force
		catch (ConfigureException e) {
			System.err.println(e.getMessage()+"; if you received this error after waiting too long, either your transducer had a property "+
					"(like copying or extended) that was not detected within the first 1k of the file, or you specified an "+
					"associativity (-l or -r) that was not the default, resulting in an impossible composition. Try reordering "+
			"some rules in your transducer and/or accepting the default associativity");
			System.exit(1);
		}

		catch (UnusualConditionException e) {
			// TODO: include context info here
			System.err.println("Unusual condition: "+e.getMessage());
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
