package edu.isi.tiburon;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
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
import com.martiansoftware.jsap.stringparsers.LongStringParser;
import com.martiansoftware.jsap.stringparsers.StringStringParser;

// command line options, etc.
public class Tiburon {
	// version number. change this when updating tiburon!
	static final String VERSION = "1.0";

	// xform types
	public enum XF { NONE, CFG, RTG, XR, XRS ;
	private static final String list;
	public static final String getList() { return list;}
	static {
		StringBuffer sb = new StringBuffer();
		for (XF x: XF.values()) {
			sb.append(x.toString()+" ");
		}
		list = sb.toString();
	}
	public static XF get(String s) throws ConfigureException{
		for (XF x : XF.values()) {
			if (x.toString().equals(s))
				return x;
		}	    
		throw new ConfigureException("Invalid xform type ("+s+"); valid values are "+list);
	}
	}

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
	// create a summary of a transducer rule set (xR or xRs transducer) and add it to a buffer
	private static void getTransducerRuleSetCheck(StringBuffer buffer, String name, TransducerRuleSet trs) {
		buffer.append("Transducer info for "+name+":\n");
		buffer.append("\t"+trs.getNumStates()+" states\n");
		buffer.append("\t"+trs.getNumRules()+" rules\n");
	}

	// create a summary of a tree and add it to a buffer
	private static void getTreeCheck(StringBuffer buffer, String name, TreeItem tree) {
		buffer.append("Tree info for "+name+":\n");
		buffer.append("\t"+tree.numNodes() +" nodes\n");
		buffer.append("\t"+tree.getItemLeaves().length+" yield length\n");
	}
	
	// create a summary of a derivation rule set (should be like an rtg) and add it to a buffer
	private static void getDerivationRuleSetCheck(StringBuffer buffer, String name, DerivationRuleSet rs) {
		buffer.append("DRS info for "+name+":\n");
		buffer.append("\t"+rs.getNumStates()+" states\n");
		buffer.append("\t"+rs.getNumRules()+" rules\n");
		if (rs.isFinite())
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
	
	
		// infileopt is at the end for nice placement in the usage statement

		// override default lookahead
		FlaggedOption lookaheadopt = new FlaggedOption("lookahead",
				IntegerStringParser.getParser(),
				null,
				false,
				JSAP.NO_SHORTFLAG,
				"lookahead",
		"Sets the lookahead bytes for file type detection to <lookahead> (default is 10480)");
		jsap.registerParameter(lookaheadopt);

		// OPTIONS REGARDING THE OPERATIONS TO PERFORM:

		// get k best alignments for sentence pairs. Not valid with any rtg-modification options (-n, -p, -d,) EXCEPT training
		// and assumes the input files are a 
		// sentence pair (i.e. training) file and a transducer or sequence of transducers. Returns, for each pair, one-based colon
		// format alignments
		FlaggedOption alignopt = new FlaggedOption("align",
				IntegerStringParser.getParser(),
				null,
				false,
				'a',
				"align",
		"given sentence pairs and a trained transducer, return the <align> best alignments.");
		jsap.registerParameter(alignopt);

		// left associativity. That is, combine automata starting from the left
		// default unless rightmost automaton is a grammar
		Switch leftsw = new Switch("left",
				'l',
				"left",
				"left associative composition/application of transducers/automata: (file1*file2) * file3 ... * fileN "+
		"This is the default unless the rightmost automaton is a grammar");
		jsap.registerParameter(leftsw);

		// right associativity. That is, combine automata starting from the right
		// default if rightmost automaton is a grammar
		Switch rightsw = new Switch("right",
				'r',
				"right",
				"right associative composition/application of transducers/automata: file1* ... * (fileN-1 * fileN)"+
		"This is the default if the rightmost automaton is a grammar");
		jsap.registerParameter(rightsw);

		// normalize rtg and transducer weights. ignored unless semiring type is real
		Switch normsw = new Switch("norm", 
				'n', 
				"normalizeweight", 
		"normalize weights of RTG/CFG. This option is only relevant to the real semiring.");
		jsap.registerParameter(normsw);

		// no normalization, even where there normally would be some. ignored unless semiring type is real
		Switch nonormsw = new Switch("nonorm", 
				JSAP.NO_SHORTFLAG, 
				"no-normalize", 
				"Don't normalize weights, even when they would be by default. This option is only relevant "+
		"to the real semiring. It cannot be used with -n (obviously)");
		jsap.registerParameter(nonormsw);

/*		// remove loops from rtg. used by determinize. rarely used on its own
		Switch remloopsw = new Switch("remloop", 
				JSAP.NO_SHORTFLAG, 
				"removeloops",
		"remove rules from the RTG/CFG that allow an infinite language. can't be used with -t");
		jsap.registerParameter(remloopsw);
*/
		// make rtg in chomsky normal form. used by determinize. rarely used on its own
		Switch normformsw = new Switch("normform", 
				JSAP.NO_SHORTFLAG, 
				"normform",
		"convert RTG/CFG to chomsky normal form. can't be used with -t");
		jsap.registerParameter(normformsw);

		FlaggedOption beamopt = new FlaggedOption("beam",
				IntegerStringParser.getParser(),
				"0",
				false,
				'b',
				"beam",
				"allow a maximum of <beam> rules per state to be formed when composing, intersecting, or applying.");
		jsap.registerParameter(beamopt);

		// prune rules that are more than some factor (of score) away from the best sentence
		// if pruning and determinization are flagged, pruning happens before determinization
		FlaggedOption pruneopt = new FlaggedOption("prune",
				DoubleStringParser.getParser(),
				null,
				false,
				'p',
				"prune",
				"Prune rules that must exist in a tree with score <prune> "+
				"greater than the score of the best tree. "+
				"Pruning occurs before determinization (-d) when both options are included. "+
		"It cannot be used with -t.");
		jsap.registerParameter(pruneopt);

		// perform weighted determinization for some number of minutes. remloop and normform assumed
		FlaggedOption detopt = new FlaggedOption("determ",
				IntegerStringParser.getParser(),
				null,
				false,
				'd',
				"determinize",
				"Determinize the input RTG for <determ> minutes. Determinization requires "+
				"normal form (done implicitly) and no loops. It cannot be used with -t.");

		jsap.registerParameter(detopt);

		// make rtg in chomsky normal form. used by determinize. rarely used on its own
		Switch borchardtsw = new Switch("borchardt", 
				JSAP.NO_SHORTFLAG, 
				"borchardt",
		"determinize borchardt-style");
		jsap.registerParameter(borchardtsw);
		
		
		// remove epsilons. also used without explicit mention by other algorithms
		// only applies to rtg and cfg input. performed on result of combination
		Switch rmepsilonsw = new Switch("rmepsilon",
				JSAP.NO_SHORTFLAG,
				"rmepsilon",
				"remove epsilon rules from grammar");
		jsap.registerParameter(rmepsilonsw);
		
		// perform em training of a transducer given a set of input/output pairs, or a grammar given a set of trees
		// This option is incompatible with most of the other options and significantly affects how the input files are treated
		FlaggedOption trainopt = new FlaggedOption("train",
				IntegerStringParser.getParser(),
				null,
				false,
				't',
				"train",
				"perform EM training of a transducer given (input, output) training pairs, or "+
				"a grammar given trees/strings. Train for "+
				"up to <train> iterations. The only acceptable output is the weighted transducer or weighted grammar "+
				", thus "+
				"-k and -g and -c options aren't valid. -p, -d, --normform, --removeloops, -n, -l, -s are "+
				"similarly disallowed. There must be at least 2 files in the input list - the first is "+
		"assumed to be a set of training pairs or triples. The remaining are the transducer(s) or grammar(s). ");
		jsap.registerParameter(trainopt);

		// transform the input automaton into the specified automaton, if possible
		FlaggedOption transformopt = new FlaggedOption("xform",
				StringStringParser.getParser(),
				null,
				false,
				'x',
				"xform",
				"transform the input automaton, string file, or tree file into <xform>. Possible values "+
				"are "+XF.getList()+". Some information may be lost by virtue of the type of transformation "+
				"performed. This operation is performed after any intersection or composition, but before "+
		"-d, -p, -k, -g, -l, -r. Transformation of transducers to grammars is domain projection.");
		jsap.registerParameter(transformopt);

		// when training, write derivations to this directory
		// if not specified, derivations are written to /tmp
		FlaggedOption trainderivlocopt = new FlaggedOption("trainderivloc",
				StringStringParser.getParser(),
				null,
				false,
				JSAP.NO_SHORTFLAG,
				"training-deriv-location",
				"Only valid if -t is used. <trainderivloc> is the file to hold binary representation "+
				"of the calculated derivation forests. "+
				"if not specified, they are written to and read from a temporary file and deleted "+
		"afterward.");
		jsap.registerParameter(trainderivlocopt);

		Switch condsw = new Switch("conditional",
				JSAP.NO_SHORTFLAG,
				"conditional",
				"Train transducers conditionally; i.e. all rules with the same LHS have probability summing to 1. If not set, training "+
				"is joint; i.e. all rules with the same LHS root have probability summing to 1. Eventually this will be "+
		"for normalization as well.");
		jsap.registerParameter(condsw);

		// if set, do not compute derivation forests - assume they have already been created
		Switch noderivsw = new Switch("noderiv",
				JSAP.NO_SHORTFLAG,
				"no-deriv",
				"Only valid if -t is used. If present, do not calculate derivation forests. Instead, assume they have already "+
				"been built (training-deriv-location flag must also be used). Derivation forests are typically time consuming, "+
		"but once built don't change.");
		jsap.registerParameter(noderivsw);

		// if set, do not compute derivation forests - assume they have already been created
		Switch overwritesw = new Switch("overwrite",
				JSAP.NO_SHORTFLAG,
				"overwrite",
				"Only valid if -t is used and only meaningful if training-deriv-location is used. This flag allows the deriv file to be overwritten. "+
				"It overrides the safety measure that prohibits this.");
		jsap.registerParameter(overwritesw);


		// if set, randomize the weights of the input rtg or transducer. this is mostly useful for em training
		Switch randomsw = new Switch("random",
				JSAP.NO_SHORTFLAG,
				"randomize",
				"Randomize the weights of the input structure (grammar or transducer) to be equivalent "+
		"to a probability between 0.2 and 0.8. This is mostly useful for EM training.");
		jsap.registerParameter(randomsw);


		// OPTIONS REGARDING THE DATA THAT IS OUTPUT

		// print timing information to stderr. number determines level of information
		FlaggedOption timeopt = new FlaggedOption("time",
				IntegerStringParser.getParser(),
				null,
				false,
				JSAP.NO_SHORTFLAG,
				"timedebug",
				"Print timing information to stderr at a variety of levels: 0+ for "+
		"total operation, 1+ for each processing stage, 2+ for small info");
		jsap.registerParameter(timeopt);

		// print yields of trees instead of trees. ignored unless -g or -k
		Switch yieldsw = new Switch("yield", 
				'y', 
				"print-yields", 
		"print yield of trees instead of trees. no meaning unless -g or -k is used on an RTG.");
		jsap.registerParameter(yieldsw);


		// return k best option - takes a number to determine k
		// this option conflicts with and cannot be used with g
		FlaggedOption kopt = new FlaggedOption("kbest",
				IntegerStringParser.getParser(),
				null,
				false,
				'k',
				"kbest",
				"return the <kbest> highest ranked items in a grammar. This option cannot be "+
		"used with -g or -c or -t");
		jsap.registerParameter(kopt);

		// return k stochastically generated option - takes a number to
		// determine k. This option conflicts with and cannot be used with k
		FlaggedOption gopt = new FlaggedOption("krandom",
				IntegerStringParser.getParser(),
				null,
				false,
				'g',
				"generate",
				"generate <krandom> trees from an RTG or strings from a CFG stochastically. Subject to --glimit (see below). This option cannot be "+
		"used with -k or -c or -t");
		jsap.registerParameter(gopt);

		// seed for krandom
		FlaggedOption gseedopt = new FlaggedOption("krandomseed",
				LongStringParser.getParser(),
				null,
				false,
				's',
				"krandomseed",
				"when --generate is used, seed the RNG with this number (long)");
		jsap.registerParameter(gseedopt);

		FlaggedOption glimitopt = new FlaggedOption("randomlimit",
				IntegerStringParser.getParser(),
				"20",
				false,
				JSAP.NO_SHORTFLAG,
				"glimit",
				"Stop randomly generating after <glimit> internal expansions. 0 = no limit. Default is 20");
		jsap.registerParameter(glimitopt);



		// return information about the "output object" - currently the rtg - that is formed as a result of application
		// or intersection (and determinization, normal form, etc). This is a sort of "check".
		// if application is done, also return information about the transducer being applied
		Switch csw = new Switch("check",
				'c',
				"check",
				"check the number of rules, states, and derivations "+
		"of the grammar or transducer ");
		jsap.registerParameter(csw);


		// OPTIONS REGARDING THE FUNDAMENTALS OF DATA OUTPUT

		// output file - if specified, whatever is written is written here. otherwise to stdout
		FlaggedOption outfileopt = 
			new FlaggedOption("outfile", 
					FileStringParser.getParser(), 
					null,
					false,
					'o',
					"outputfile",
					"file to write output grammar or tree list or summary. If absent, writing is done "+
			"to stdout");
		jsap.registerParameter(outfileopt);

		// set of input files. Sometimes it doesn't make sense to have more than one. In these cases
		// a warning is generated and only the first is used
		UnflaggedOption infileopt = new UnflaggedOption("infiles",
				FileStringParser.getParser(),
				null,
				true,
				true,
				"list of input files. If using training mode (-t) the first file must be "+
				"a list of training items. Subsequent files are transducers, grammars, trees, or strings "+
				"that will be composed in the 'obvious' way if possible, using either the default associativity (right "+
				"if the rightmost file is a grammar or tree, left otherwise) or that set by -l/-r. The special symbol '-'"+
				"(no quote) may be specified up to one time in the file sequence to indicate reading from STDIN. Illegal "+
				"composition sequences, such as intersection of two CFGs, a grammar followed by a copying transducer, or "+
		"attempted composition of two extended transducers will result in an error message.");
		jsap.registerParameter(infileopt);


		JSAPResult config = jsap.parse(argv);
		// make sure train isn't set with bad input options
		if (config.contains("train") && 
				(config.getBoolean("normform") ||
						config.contains("prune") ||
						config.contains("determ") ||
						config.contains("xform")))
			throw new ConfigureException("Cannot use train (-t) with  --normform, -p, -d, or -x");

		// make sure align isn't set with any rtg-modification options
		if (config.contains("align") &&
				(config.getBoolean("normform") ||
						config.contains("prune") ||
						config.contains("determ") ||
						config.contains("xform")))
			throw new ConfigureException("Cannot get alignments (-a) with  --normform, -p, -d, or -x");

		// can't normalize and no-normalize
		if (config.getBoolean("norm") && config.getBoolean("nonorm"))
			throw new ConfigureException("--normalizeweight and --no-normalize are contradictory flags");

		// make sure there aren't too many switches on
		int numActive = 0;
		if (config.contains("krandom"))
			numActive++;
		if (config.contains("kbest"))
			numActive++;
		if (config.contains("train"))
			numActive++;
		if (config.getBoolean("check"))
			numActive++;
		if (config.contains("align") && !config.contains("train"))
			numActive++;
		if (numActive > 1)
			throw new ConfigureException("Can have at most one of -k, -g, -c, -t, -a arguments!");

		// make sure both left and right aren't set
		if (config.getBoolean("left") && config.getBoolean("right"))
			throw new ConfigureException("Can't specify both left and right associativity (-l and -r)!");

		return config;
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


	// detect all file types, then make sure the sequence is valid
	private static FileType[] detectFiles(Vector<BufferedReader> brs, File[] infiles, 
			String encoding, JSAPResult config) throws ConfigureException, FileNotFoundException, 
			IOException, DataFormatException, 
			UnusualConditionException {
		boolean debug = false;
		if (debug) Debug.debug(debug, "Debugging on");
		Vector<File> fileVec = new Vector<File>();
		Vector<FileType> fileTypes = new Vector<FileType>();
		int fileCounter = 0;
		for (BufferedReader br : brs) {
			fileVec.add(infiles[fileCounter]);
			FileType currtype = null;
			if (config.contains("lookahead"))
				currtype = new FileType(br, config.getInt("lookahead"));
			else
				currtype = new FileType(br);
			if (debug) Debug.debug(debug, "File type for "+infiles[fileCounter].getName()+" is "+currtype.getType());
			fileTypes.add(currtype);
			fileCounter++;
		}
		// build return set
		FileType[] ret = new FileType[fileTypes.size()];
		fileTypes.toArray(ret);


		// currently allowable file sequences:
		// train? (xRie* RLN* RLNs? | CFG)
		// train? (RTG|TREE) (RL | RTG | TREE)* (RLs STRING?)?
		// train? xRL (xRL | RTG | TREE)* (RTG|TREE)

		// check first type for train status. if so, strip it off from the vectors
		// first file tests
		if (debug) Debug.debug(debug, infiles[0].getName()+" is "+fileTypes.get(0).getType());
		if (config.contains("train")) {
			if (fileTypes.get(0).getType() == FileType.TYPE.TRAIN) {
				if (infiles.length < 2)
				throw new ConfigureException("Need a transducer or rtg file after training file "+infiles[0].getName());
				fileTypes.remove(0);
				fileVec.remove(0);
			}
			else {
				throw new ConfigureException("In training mode; expected training file in "+infiles[0].getName());
			}
		}

		// first non-training file tests
		File currFile = fileVec.get(0);
		FileType currType = fileTypes.get(0);
		if (debug) Debug.debug(debug, currFile.getName()+" is "+currType.getType());
		// checking on trees
		boolean isTree = false;
		switch (currType.getType()) {
		//  if CFG or XRS, can have string next but nothing after
		case STRING_TRANS:
		case CFG:
			if (fileVec.size() > 2)
				throw new ConfigureException("Only one file allowed after "+currType.getType()+" "+currFile.getName());
			if (fileVec.size() == 2) {
				File nextFile = fileVec.get(1);
				FileType nextType = fileTypes.get(1);
				switch (nextType.getType()) {
				case TRAIN:
					if (nextType.getTrainType() != FileType.TRAINTYPE.STRING)
						throw new ConfigureException(nextFile.getName()+" is of illegal type "+nextType.getType()+" -- "+nextType.getTrainType()+" -- expecting string file");
				case STRING:
					if (debug) Debug.debug(debug, "Saw string after "+currType.getType()+", which is okay");
					break;
				default:
					throw new ConfigureException(nextFile.getName()+" is of type "+nextType.getType()+"; expected STRING");						
				}
			}
			break;
		case TRAIN:
			if (currType.getTrainType() != FileType.TRAINTYPE.TREE && 
				currType.getTrainType() != FileType.TRAINTYPE.TREE_TREE )
				throw new ConfigureException(currFile.getName()+" is of illegal type "+currType.getType()+" -- "+currType.getTrainType()+" -- expecting grammar, transducer, or tree file");
		case TREE:
			isTree = true;
		case RTG:
			for (int i = 1; i < fileVec.size(); i++) {
				File nextFile = fileVec.get(i);
				FileType nextType = fileTypes.get(i);
				if (debug) Debug.debug(debug, "next file "+nextFile.getName()+" is "+nextType.getType());
				// fall-throughs intentional!
				switch (nextType.getType()) {
				case STRING_TRANS:
					if (i < fileVec.size()-2)
						throw new ConfigureException(nextFile.getName()+" is tree-to-string; only allowed with at most one additional file in chain");
				case TREE_TRANS:
					if (!isTree) {
						if (nextType.isExtend())
							throw new ConfigureException(nextFile.getName()+" is extended; forward application not supported");
						if (nextType.isCopy())
							throw new ConfigureException(nextFile.getName()+" is copying; forward application not supported");
					}
				case RTG:
					isTree = false;
					break;
				case TRAIN:
					if (currType.getTrainType() == FileType.TRAINTYPE.TREE_STRING)
					throw new ConfigureException(currFile.getName()+" is of illegal type "+currType.getType()+" -- "+currType.getTrainType()+" -- expecting grammar, transducer, or tree file");
				case TREE:
				case STRING:
					// we handle string by converting the RTG to XRS
					break;
				default:
					throw new ConfigureException(nextFile.getName()+" is of type "+nextType.getType()+"; expected RTG or RL");
				}
			}
			break;
			// tree transducer can be followed by any number of nondeleting, noncopying, nonepsilon tree transducers and
			// at most one nondeleting, noncopying, nonepsilon string transducer, and then a string
			// if followed by RTG/TREE, this is taken as an RLN.

			// it can be followed by any number of noncopying tree transducers (and RTG/TREE),i.e. the other restrictions are listed, 
			// as long as there is a RTG/TREE at the end
		case TREE_TRANS:
		case TRANSDUCER:
			FileType lastType = fileTypes.get(fileVec.size()-1);
			boolean justSawStringTrans=false;
			for (int i = 1; i < fileVec.size(); i++) {
				File nextFile = fileVec.get(i);
				FileType nextType = fileTypes.get(i);
				if (debug) Debug.debug(debug, "next file "+ nextFile.getName()+" is "+nextType.getType());
				switch (nextType.getType()) {
				case TREE_TRANS:
				case TRANSDUCER:
					justSawStringTrans=false;
					if (nextType.isCopy())
						throw new ConfigureException(nextFile.getName()+" is a copying transducer; composition may be impossible");
					if (nextType.isDelete() && lastType.getGenType() != FileType.TYPE.GRAMMAR)
						throw new ConfigureException(nextFile.getName()+" is a deleting transducer; composition may be impossible");
					if (nextType.isInEps() && lastType.getGenType() != FileType.TYPE.GRAMMAR)
						throw new ConfigureException(nextFile.getName()+" is an input epsilon transducer; composition may be impossible");
					if (nextType.isExtend() && lastType.getGenType() != FileType.TYPE.GRAMMAR)
						throw new ConfigureException(nextFile.getName()+" is an extended-input transducer; composition may be impossible");
					break;
				case STRING_TRANS:
					justSawStringTrans=true;
					if ( i >= (fileVec.size()-2))
						break;
					throw new ConfigureException(nextFile.getName()+" is a string transducer; can only appear at end or pentultimate place in chain");
				case TRAIN:
					switch (nextType.getTrainType()) {
					case STRING:
						if (!justSawStringTrans)
							throw new ConfigureException(nextFile.getName()+" is a file of strings; can only appear after string transducer in chain");
					case TREE:
					case TREE_TREE:
						justSawStringTrans=false;
						// tree is okay. string okay subject to above. all else isn't.
						break;
					default:
						throw new ConfigureException(currFile.getName()+" is of illegal type "+currType.getType()+" -- "+currType.getTrainType()+" -- expecting grammar, transducer, or tree file");
					}
					
				case RTG:
				case TREE:
					break;
				case STRING:
					if (justSawStringTrans) {
						justSawStringTrans = false;
						break;
					}
				default:
					throw new ConfigureException(nextFile.getName()+" is "+nextType.getType()+" and can't be composed "+
					"with or applied to the previous transducers");
				}
			}
			break;
		default:
			throw new ConfigureException(currFile.getName()+" is of illegal type "+currType.getType()+
			"; expecting grammar, transducer, or tree file");
		}
		return ret;
	}

	// make sure the file sequence and configuration commands are logically sound
	// throw a configuration exception otherwise
	private static FileType.TYPE verifySequence(FileType[] fileTypes, JSAPResult config, XF xformtype) throws ConfigureException {

		boolean debug = false;
		if (debug) Debug.debug(debug, "Debugging on");

		// what is the result of composition/intersection?
		FileType.TYPE inputType = FileType.TYPE.UNKNOWN;

		// first, check first automaton
		// TODO: what about string?
		boolean isLeftGrammar = false;
		boolean sawBatch = false;
		switch (fileTypes[0].getType()) {
		// pre-set for CFG
		case CFG:
			inputType = FileType.TYPE.CFG;
		case RTG:
			if (debug) Debug.debug(debug, "saw grammar on left so setting isLeftGrammar");
			isLeftGrammar = true;
		case TREE_TRANS:
		case STRING_TRANS:
			inputType = fileTypes[0].getType();
			if (debug) Debug.debug(debug, "setting type to leftmost item: "+inputType);
			break;
		case TREE:
			isLeftGrammar = true;
			inputType = FileType.TYPE.RTG;
			if (debug) Debug.debug(debug, "saw tree on left so setting rtg");
			break;
		case TRAIN:
			if (debug) Debug.debug(debug, "first item is training");
			if (config.contains("train")) {
				switch (fileTypes[1].getType()) {
				case RTG:
				case CFG:
					if (debug) Debug.debug(debug, "saw grammar on left so setting isLeftGrammar");
					isLeftGrammar = true;
				case TREE_TRANS:
				case STRING_TRANS:
					inputType = fileTypes[1].getType();
					if (debug) Debug.debug(debug, "setting type to leftmost item: "+inputType);
					break;
				case TREE:
					isLeftGrammar = true;
					inputType = FileType.TYPE.RTG;
					if (debug) Debug.debug(debug, "saw tree on left so setting rtg");
					break;
				default:
					throw new ConfigureException("First automaton after training should be grammar or transducer "+
							"but is "+fileTypes[1].getType());
				}
			}
			else {
				switch (fileTypes[0].getTrainType()) {
				case TREE:
				case TREE_TREE:
					if (debug) Debug.debug(debug, "set of trees, so setting to RTG");
					isLeftGrammar = true;
					inputType = FileType.TYPE.RTG;
					break;
				default:
					throw new ConfigureException("Can't have just a batch of "+fileTypes[0].getTrainType());						
				}
				break;
			}
			break;
		default:
			throw new ConfigureException("First automaton should be grammar or transducer "+
					"but is "+fileTypes[0].getType());
		}


		// now check the right side
		switch (fileTypes[fileTypes.length-1].getType()) {
		case RTG:
		case CFG:
			inputType = fileTypes[fileTypes.length-1].getType();
			if (debug) Debug.debug(debug, "setting type to rightmost item: "+inputType);
			break;
		case TREE:
			inputType = FileType.TYPE.RTG;
			if (debug) Debug.debug(debug, "saw tree on right so setting rtg");
			break;
		case TREE_TRANS:
			// should revert to whatever the left is 
				if (debug) Debug.debug(debug, "leaving type alone: "+inputType);
			break;
		case STRING_TRANS:
			if (isLeftGrammar) {
				inputType = FileType.TYPE.RTG;
				if (debug) Debug.debug(debug, "started with tree but ending with string transducer so RTG (?!)");
			}
			else {
				inputType = fileTypes[fileTypes.length-1].getType();
				if (debug) Debug.debug(debug, "setting type to rightmost item: "+inputType);
			}
			break;
		case STRING:
			
			if (inputType == FileType.TYPE.CFG) {
				inputType = FileType.TYPE.CFG;
				if (debug) Debug.debug(debug, "saw string on right and were cfg so setting cfg");
			}
			// should be xrs or rtg, but result is now rtg
			else {
				if (debug) Debug.debug(debug, "saw string on right and were not cfg so setting rtg");
				inputType = FileType.TYPE.RTG;
			}
			break;
		case TRAIN:
			// train is either treated like tree or string, depending on what it is
			switch (fileTypes[fileTypes.length-1].getTrainType()) {
			case TREE:
			case TREE_TREE:
				inputType = FileType.TYPE.RTG;
				if (debug) Debug.debug(debug, "saw batch of tree on right so setting rtg");
				break;
			
			case STRING:
				if (inputType == FileType.TYPE.CFG) {
					inputType = FileType.TYPE.CFG;
					if (debug) Debug.debug(debug, "saw batch of string on right and were cfg so setting cfg");
				}
				// should be xrs or rtg, but result is now rtg
				else {
					if (debug) Debug.debug(debug, "saw batch of string on right and were not cfg so setting rtg");
					inputType = FileType.TYPE.RTG;
				}
			break;
			default:
				throw new ConfigureException("Last automaton should be grammar or transducer or string or tree "+
						"but is "+fileTypes[fileTypes.length-1].getType()+" - "+fileTypes[fileTypes.length-1].getTrainType());
			}
			break;
		default:
			throw new ConfigureException("Last automaton should be grammar or transducer or string or tree "+
					"but is "+fileTypes[fileTypes.length-1].getType());
		}

		// now check if we're changing:

		switch (xformtype) {
		case NONE:
			break;
		case RTG:
			inputType = FileType.TYPE.RTG;
			break;
		case CFG:
			inputType = FileType.TYPE.CFG;
			break;
		case XR:
			inputType = FileType.TYPE.TREE_TRANS;
			break;
		case XRS:
			inputType = FileType.TYPE.STRING_TRANS;
			break;
		}





 

		// then, based on what the input type will be, forbid certain flags
		switch (inputType) {
		// transducers can't do -k, -g, -d
		case TREE_TRANS:
		case STRING_TRANS:
		case TRANSDUCER:
			if (config.contains("krandom"))
				throw new ConfigureException("Can't use -g with transducer (yet)");
			if (config.contains("kbest"))
				throw new ConfigureException("Can't use -k with transducer (yet)");
			if (config.contains("determ"))
				throw new ConfigureException("Can't use -d with transducer");
			if (config.getBoolean("yield")) 
				throw new ConfigureException("Can't use -y with transducer");
			if (config.contains("prune")) 
				throw new ConfigureException("Can't use -p with transducer");
		}


		return inputType;
	}

	// read in files
	private static Object[] readAutomata(Vector<BufferedReader> brs, File[] infiles, FileType[] fileTypes, 
			JSAPResult config, String encoding, 
			Semiring ts, FileType.TYPE inputType,
			int timeLevel, StringBuffer outputBuffer) throws IOException, 
			DataFormatException, ConfigureException {
		boolean debug = false;
		if (debug) Debug.debug(debug, "Debugging on");
		if (debug) Debug.debug(debug, "Input type is "+inputType);

		// read in grammars or transducers for application or transducers for training, 
		// don't read in training/batch files, cause they might be too large
		// add check info here, if appropriate
		Object[] fileObjects = new Object[infiles.length];
		int i = 0;
		for (BufferedReader br : brs) {
			Date preReadObjectTime = new Date();
			switch (fileTypes[i].getType()) {
			case TRAIN:
				if (debug) Debug.debug(debug, "We're training");
				FileType.TRAINTYPE trainingType = fileTypes[i].getTrainType();
				boolean isFirstTree = trainingType.isFirstTree();
				boolean isSecondTree = trainingType.isSecondTree();
				// assume no count if we can't tell (though that would be odd)
				boolean isCount = trainingType.isCount();

				switch (trainingType) {
				case TREE:
				case TREE_COUNT:
					fileObjects[i] = RuleSetTraining.readItemSet(br, isCount, isFirstTree, ts);
					break;
				case STRING:
				case STRING_COUNT:
					fileObjects[i] = CFGTraining.readItemSet(br, isCount, ts);
					break;
				case TREE_TREE:
					// tree tree is ambiguous, so check if we're intending to train
					if (config.contains("train") && inputType.getGeneral() == FileType.TYPE.TRANSDUCER) {
						if (debug) Debug.debug(debug, "We're training a transducer, so reading in tree-tree as in transducer training");
						fileObjects[i] = TransducerTraining.readTreeSet(br, isCount, isSecondTree, ts);
					}
					else {
						if (debug) Debug.debug(debug, "We're not training a transducer, so reading in tree-tree as in rule set training");
						fileObjects[i] = RuleSetTraining.readItemSet(br, isCount, isFirstTree, ts);
					}
					break;
				case TREE_TREE_COUNT:
				case TREE_STRING:
				case TREE_STRING_COUNT:
					fileObjects[i] = TransducerTraining.readTreeSet(br, isCount, isSecondTree, ts);
					break;
				default:
					throw new ConfigureException("Unsure what type of training to read for "+
							infiles[i].getName()+" on "+inputType);
				}
				break;
			case RTG:
				if (debug) Debug.debug(debug, "We're a rtg");
				fileObjects[i] = new RTGRuleSet(br, ts);
				if (config.getBoolean("check"))
					getRuleSetCheck(outputBuffer, "input rtg "+infiles[i].getName(), (RTGRuleSet)fileObjects[i]);
				break;
			case CFG:
				if (debug) Debug.debug(debug, "We're a cfg");
				fileObjects[i] = new CFGRuleSet(br, ts);		    
				if (config.getBoolean("check"))
					getRuleSetCheck(outputBuffer, "input cfg "+infiles[i].getName(), (CFGRuleSet)fileObjects[i]);
				break;
			case TREE_TRANS:
			case TRANSDUCER:
				if (debug) Debug.debug(debug, "We're a tree transducer");
				fileObjects[i] = new TreeTransducerRuleSet(br, ts);
				if (config.getBoolean("check"))
					getTransducerRuleSetCheck(outputBuffer, "input tree transducer "+infiles[i].getName(), 
							(TransducerRuleSet)fileObjects[i]);
				break;
			case STRING_TRANS:
				if (debug) Debug.debug(debug, "We're a string transducer");
				fileObjects[i] = new StringTransducerRuleSet(br, ts);
				if (config.getBoolean("check"))
					getTransducerRuleSetCheck(outputBuffer, "input string transducer "+infiles[i].getName(), 
							(TransducerRuleSet)fileObjects[i]);
				break;
			case TREE:
				if (debug) Debug.debug(debug, "We're a tree");
				fileObjects[i] = new TreeItem(br);
				if (config.getBoolean("check"))
					getTreeCheck(outputBuffer, "input tree "+infiles[i].getName(), (TreeItem)fileObjects[i]);

				((TreeItem)fileObjects[i]).semiring = ts;
				break;
			case STRING:
				if (debug) Debug.debug(debug, "We're a string");
				fileObjects[i] = new StringItem(br);
				break;
			default:
				throw new ConfigureException("Can't read in file "+infiles[i].getName()+"; it's of unexpected type "+fileTypes[i].getType());
			}
			Date postReadObjectTime = new Date();
			Debug.dbtime(timeLevel, 2, preReadObjectTime, postReadObjectTime,  "read file "+infiles[i].getName());
			i++;
		}
		return fileObjects;
	}

	
	// combination that uses vector composition to run en masse
	private static FileType.TYPE vectorCombineAutomata(
			TransducerRuleSet[] trsarr, RuleSet[] rsarr, TreeItem[] treearr,
			FileType[] fileTypes, Object[] fileObjects, 
			JSAPResult config,
			int timeLevel, StringBuffer outputBuffer, Semiring ts) throws ConfigureException, 
			ImproperConversionException,
			UnusualConditionException {
		boolean debug = false;;
		
		RuleSet rs = null;
		TransducerRuleSet trs = null;
		StringItem str = null;
		TreeItem tree = null;
		if (debug) Debug.debug(debug, "Debug on");
		// if in training mode, start with file 1. else, file 0
		int startFile = (config.contains("train")) ? 1 : 0;
		FileType.TYPE ret = FileType.TYPE.UNKNOWN;
		
		// figure out which direction we're operating in
		// if set in config, take it from there.
		// if not, transducer chain can change things
		boolean isLeft = true;
		boolean isSideSet = false;
		// if configuration is set, go with that
		if (config.getBoolean("right")) {
			if (debug) Debug.debug(debug, "config chose right associativity");
			isLeft = false;
			isSideSet = true;
		}
		else if (config.getBoolean("left")) {
			if (debug) Debug.debug(debug, "config chose left associativity");
			isLeft = true;
			isSideSet = true;
		}
		// what we do depends on how many files there are
		if (debug) Debug.debug(debug, "start file is "+startFile+" and there are "+fileObjects.length+" objects");
		switch (fileObjects.length - startFile) {
		case 0:
			// 0 is weird
			throw new UnusualConditionException("No files to combine");
		case 1:
			if (debug) Debug.debug(debug, "one file");

			// 1 is a no-op -- place the item and return it
			switch (fileTypes[startFile].getType()) {
			case TRAIN:
				if (fileTypes[startFile].getTrainType() != FileType.TRAINTYPE.TREE &&
						fileTypes[startFile].getTrainType() != FileType.TRAINTYPE.TREE_TREE)
					throw new ConfigureException(fileTypes[startFile].getTrainType()+" is not a handled input type");
			case TREE:
				if (debug) Debug.debug(debug, "positioning terminal tree");
				tree = (TreeItem)fileObjects[startFile];
				ret = FileType.TYPE.TREE;
				break;
			case RTG:
			case CFG:
				if (debug) Debug.debug(debug, "positioning terminal grammar");
				rs = (RuleSet)fileObjects[startFile];
				ret = fileTypes[startFile].getType();
				break;
			case TREE_TRANS:
			case STRING_TRANS:
			case TRANSDUCER:
				if (debug) Debug.debug(debug, "positioning terminal transducer");
				trs = (TransducerRuleSet)fileObjects[startFile];
				ret = fileTypes[startFile].getType();
				break;
			default:	
				throw new ConfigureException(fileTypes[startFile].getType()+" is not a handled input type");
			}
			break;
		case 2:
			if (debug) Debug.debug(debug, "two files");

			// CFG + STRING | STRING + CFG = CFG (parsing)
			if (fileTypes[startFile].getType() == FileType.TYPE.CFG && 
					(fileTypes[startFile+1].getType() == FileType.TYPE.STRING ||
							fileTypes[startFile+1].getTrainType() == FileType.TRAINTYPE.STRING)) {
				if (debug) Debug.debug(debug, "Intersecting CFG and string");
				Date preIsectTime = new Date();
				rs = new CFGRuleSet((CFGRuleSet)fileObjects[startFile], (StringItem)fileObjects[startFile+1], config.getInt("beam"), timeLevel);
				Date postIsectTime = new Date();
				Debug.dbtime(timeLevel, 2, preIsectTime, postIsectTime, "CFG Intersection with string ");	
				ret = FileType.TYPE.CFG;
				break;
			}
			else if (fileTypes[startFile+1].getType() == FileType.TYPE.CFG && 
					(fileTypes[startFile].getType() == FileType.TYPE.STRING ||
							fileTypes[startFile].getTrainType() == FileType.TRAINTYPE.STRING)) {
				if (debug) Debug.debug(debug, "Intersecting CFG and string");
				Date preIsectTime = new Date();
				rs = new CFGRuleSet((CFGRuleSet)fileObjects[startFile+1], (StringItem)fileObjects[startFile], config.getInt("beam"), timeLevel);
				Date postIsectTime = new Date();
				Debug.dbtime(timeLevel, 2, preIsectTime, postIsectTime, "CFG Intersection with string ");	
				ret = FileType.TYPE.CFG;
				break;
			}
			// TREE|RTG + TREE|RTG = RTG (intersection) 
			else if ((fileTypes[startFile].getType() == FileType.TYPE.TREE || 
					fileTypes[startFile].getTrainType() == FileType.TRAINTYPE.TREE ||
					fileTypes[startFile].getTrainType() == FileType.TRAINTYPE.TREE_TREE || 
					fileTypes[startFile].getType() == FileType.TYPE.RTG) &&
					(fileTypes[startFile+1].getType() == FileType.TYPE.TREE ||
							fileTypes[startFile+1].getTrainType() == FileType.TRAINTYPE.TREE ||
							fileTypes[startFile+1].getTrainType() == FileType.TRAINTYPE.TREE_TREE || 
							fileTypes[startFile+1].getType() == FileType.TYPE.RTG)){
				RTGRuleSet rsa = null;
				RTGRuleSet rsb = null;
				Date preIsectTime = new Date();
				if (fileTypes[startFile].getType() == FileType.TYPE.TREE || 
						fileTypes[startFile].getTrainType() == FileType.TRAINTYPE.TREE ||
						fileTypes[startFile].getTrainType() == FileType.TRAINTYPE.TREE_TREE) {
					if (debug) Debug.debug(debug, "converting tree to rtg");
					rsa = new RTGRuleSet((TreeItem)fileObjects[startFile], ts);
				}
				else
					rsa = (RTGRuleSet)fileObjects[startFile];
				if (fileTypes[startFile+1].getType() == FileType.TYPE.TREE || 
						fileTypes[startFile+1].getTrainType() == FileType.TRAINTYPE.TREE ||
						fileTypes[startFile+1].getTrainType() == FileType.TRAINTYPE.TREE_TREE) {
					if (debug) Debug.debug(debug, "converting tree to rtg");
					rsb = new RTGRuleSet((TreeItem)fileObjects[startFile+1], ts);
				}
				else
					rsb = (RTGRuleSet)fileObjects[startFile+1];
				if (debug) Debug.debug(debug, "Intersecting RTGs (possibly originally trees)");
				rsa.removeEpsilons();
				rsa.makeNormal();
				rsb.removeEpsilons();
				rsb.makeNormal();
				rs = Intersect.intersectRuleSets(rsa, rsb);
				Date postIsectTime = new Date();
				Debug.dbtime(timeLevel, 2, preIsectTime, postIsectTime, "RTG Intersection ");	
				ret = FileType.TYPE.RTG;
				break;
			}
			else {
				// !TRANS + !TRANS = error
				boolean sawtrans=false;
				switch (fileTypes[startFile].getType()) {
				case TREE_TRANS:
				case STRING_TRANS:
				case TRANSDUCER:
					sawtrans = true;
					break;
				}
				switch (fileTypes[startFile+1].getType()) {
				case TREE_TRANS:
				case STRING_TRANS:
				case TRANSDUCER:
					sawtrans = true;
					break;
				}
				if (!sawtrans)
					throw new ConfigureException("Can't combine types "+fileTypes[startFile].getType()+" and "+fileTypes[startFile+1].getType());
			}
			// else, fall to case 3+
		default:
			if (debug) Debug.debug(debug, "more than two files or a transducer");

			// compose everything, converting if need be
			// exception: if the last two are STRING_TRANS/STRING, we can't yet do integrated composition
			// compose/intersect the two together and use that with the rest of the transducers
		Date preComposeTime = new Date();
		Vector<Object> items = new Vector<Object>();
		boolean sawString = false;
		boolean leftGrammar = false;
		boolean rightGrammar = false;
		for (int i = fileObjects.length-1; i>= startFile; i--) {
			boolean isTrainString = false;
			switch (fileTypes[i].getType()) {
			case TRAIN:				
				switch (fileTypes[i].getTrainType()) {
				case STRING:
					isTrainString = true;
					break;
				case TREE:
				case TREE_TREE:
					break;
				default:
					throw new ConfigureException(fileTypes[startFile].getTrainType()+" is not a handled input type");
				}
			case STRING:
				// need to double check because of the above
				if (isTrainString || fileTypes[i].getType() == FileType.TYPE.STRING) {
					sawString = true;
					rightGrammar = true;
					// special handling for parsing
					if (i != fileObjects.length-1)
						throw new ConfigureException("Can't have STRING in any multi-item position but last");
					if (fileTypes[i-1].getType() != FileType.TYPE.STRING_TRANS)
						throw new ConfigureException("Can't have "+fileTypes[i-1].getType()+" next to STRING at end of multi-item");
					if (debug) Debug.debug(debug, "Combining terminal XRS and string "+fileObjects[i]);
					Date preIsectTime = new Date();
					RTGRuleSet rtg = new RTGRuleSet((StringTransducerRuleSet)fileObjects[i-1], (StringItem)fileObjects[i], config.getInt("beam"), timeLevel);
//					if (debug) Debug.debug(debug, rtg.toString());
					if (config.getBoolean("check"))			
						getRuleSetCheck(outputBuffer, "Terminal XRS back applied with string", rtg);
					// avoid converting this to TST if there is nothing to combine it with
					if (i-1 != startFile)
						items.add(0, new StringTransducerRuleSet(rtg));
					else
						rs = rtg;
					Date postIsectTime = new Date();
					Debug.dbtime(timeLevel, 2, preIsectTime, postIsectTime, "parsing XRS with string");	
					i--;
					break;	
				}
			case TREE:
				if (i == fileObjects.length-1)
					rightGrammar = true;
				if (i == startFile)
					leftGrammar = true;
				if (debug) Debug.debug(debug, "tree->xr: "+fileObjects[i]);

				items.add(0, new TreeTransducerRuleSet(new RTGRuleSet((TreeItem)fileObjects[i], ts)));
				break;
			case CFG:
				sawString = true;
				rightGrammar = true;
				if (i != fileObjects.length-1)
					throw new ConfigureException("Can't have CFG in any multi-item position but last");
				if (debug) Debug.debug(debug, "terminal cfg -> xrs");
				items.add(0, new StringTransducerRuleSet(new RTGRuleSet((CFGRuleSet)fileObjects[i])));
				break;
			case STRING_TRANS:
				if (i != fileObjects.length-1)
					throw new ConfigureException("Can't have STRING_TRANS in any multi-item position but last");
				if (debug) Debug.debug(debug, "terminal xrs");
				StringTransducerRuleSet strtrans = (StringTransducerRuleSet)fileObjects[i];
				strtrans.makeNonLookahead();
				if (!isSideSet && strtrans.isExtended())
					isLeft = false;
				items.add(0, strtrans);
				sawString = true;
				break;
			case TREE_TRANS:
				if (debug) Debug.debug(debug, "xr");
				TreeTransducerRuleSet treetrans = (TreeTransducerRuleSet)fileObjects[i];
				treetrans.makeNonLookahead();
				if (!isSideSet && treetrans.isExtended())
					isLeft = false;
				items.add(0, treetrans);
				break;

			case RTG:
				if (i == fileObjects.length-1)
					rightGrammar = true;
				if (i == startFile)
					leftGrammar = true;
				if (debug) Debug.debug(debug, "rtg->xr");
				RTGRuleSet temprtg = (RTGRuleSet)fileObjects[i];
				temprtg.makeNormal();
				items.add(0, new TreeTransducerRuleSet(temprtg));
				break;
			default:
				throw new ConfigureException(fileTypes[i].getType()+" is not a handled input type for multi-item");
			}
		}
		if (sawString) {
			// form a string transducer rule set
			Vector<TransducerRuleSet> vec = new Vector<TransducerRuleSet>();
			for (Object o : items) {
				vec.add((TransducerRuleSet)o);
			}
			if (debug) Debug.debug(debug, "string composition");

			StringTransducerRuleSet strs = null;
			if (vec.size() > 1) {
				strs = new StringTransducerRuleSet(vec, isLeft, config.getInt("beam"));
				strs.pruneUseless();
			}
			else {
				if (debug) Debug.debug(debug, "No composition after parsing");
				break;
			}
			if (leftGrammar && (!rightGrammar || !isLeft)) {
				if (debug) Debug.debug(debug, "range projection");

				rs = new CFGRuleSet(strs);
				ret = FileType.TYPE.CFG;
				if (config.getBoolean("check"))			
					getRuleSetCheck(outputBuffer, "composed large sequence of transducers, projected range to cfg", rs);				
			}
			else if (rightGrammar && (!leftGrammar || isLeft)) {
				if (debug) Debug.debug(debug, "domain projection");
//				if (debug) Debug.debug(debug, strs.toString());
				rs = new RTGRuleSet(strs);
//				if (debug) Debug.debug(debug, rs.toString());

				ret = FileType.TYPE.RTG;
				if (config.getBoolean("check"))			
					getRuleSetCheck(outputBuffer, "composed large sequence of transducers, projected domain to rtg", rs);
			}
			else {
				trs = strs;
				ret = FileType.TYPE.STRING_TRANS;
				if (config.getBoolean("check"))			
					getTransducerRuleSetCheck(outputBuffer, "composed large sequence of transducers", trs);
			}
			Date postComposeTime = new Date();
			Debug.dbtime(timeLevel, 2, preComposeTime, postComposeTime, "composed large sequence");		
		}
		else {
			// form a tree transducer rule set
			// form a string transducer rule set
			Vector<TreeTransducerRuleSet> vec = new Vector<TreeTransducerRuleSet>();
			for (Object o : items) {
				vec.add((TreeTransducerRuleSet)o);
			}
			if (debug) Debug.debug(debug, "tree composition");

			TreeTransducerRuleSet ttrs = new TreeTransducerRuleSet(vec, isLeft, config.getInt("beam"));
			ttrs.pruneUseless();
			if (leftGrammar && (!rightGrammar || !isLeft)) {
				if (debug) Debug.debug(debug, "range projection");

				rs = new RTGRuleSet(ttrs);
				ret = FileType.TYPE.RTG;
				if (config.getBoolean("check"))			
					getRuleSetCheck(outputBuffer, "composed large sequence of tree transducers, projected range to rtg", rs);

			}
			else if (rightGrammar && (!leftGrammar || isLeft)) {
				if (debug) Debug.debug(debug, "domain projection");

				rs = new RTGRuleSet((TransducerRuleSet)ttrs);
				ret = FileType.TYPE.RTG;
				if (config.getBoolean("check"))			
					getRuleSetCheck(outputBuffer, "composed large sequence of tree transducers, projected domain to rtg", rs);
	
				}
			else {
				trs = ttrs;
				ret = FileType.TYPE.TREE_TRANS;
				if (config.getBoolean("check"))			
					getTransducerRuleSetCheck(outputBuffer, "composed large sequence of tree transducers", trs);
			}
			Date postComposeTime = new Date();
			Debug.dbtime(timeLevel, 2, preComposeTime, postComposeTime, "composed large sequence");		

		}
		}
		trsarr[0] = trs;
		rsarr[0] = rs;
		treearr[0] = tree;
		return ret;
	}

	
	// form either a trs or an rs (put in trs or rs) by composition/intersection

	private static FileType.TYPE combineAutomata(TransducerRuleSet[] trsarr, RuleSet[] rsarr, TreeItem[] treearr,
			FileType[] fileTypes, Object[] fileObjects, 
			JSAPResult config,
			int timeLevel, StringBuffer outputBuffer, Semiring ts) throws ConfigureException, 
			ImproperConversionException,
			UnusualConditionException {
		boolean debug = false;
		boolean checkNormForm = true;
		RuleSet rs = null;
		TransducerRuleSet trs = null;
		StringItem str = null;
		TreeItem tree = null;
		if (debug) Debug.debug(debug, "Debug on");
		// if in training mode, start with file 1. else, file 0
		int startFile = (config.contains("train")) ? 1 : 0;
		FileType.TYPE ret = FileType.TYPE.UNKNOWN;

		// figure out which direction we're operating in
		boolean isLeft = true;
		// if configuration is set, go with that
		if (config.getBoolean("right")) {
			if (debug) Debug.debug(debug, "config chose right associativity");
			isLeft = false;
		}
		// otherwise, check if last automaton is a grammar or primitive (tree/string/train)
		else if (!config.getBoolean("left")) {
			if (fileTypes[fileTypes.length-1].getGenType() == FileType.TYPE.GRAMMAR) {
				if (debug) Debug.debug(debug, "last automaton is non-transducer; default is right associativity");
				isLeft = false;
			}
			else 
				if (debug) Debug.debug(debug, "last automaton is not grammar; default is left associativity");
		}
		else {
			if (debug) Debug.debug(debug, "config chose left associativity");
		}
		// left associativity
		if (isLeft) {
			if (debug) Debug.debug(debug, "Left associativity");
			for (int i = startFile; i < fileTypes.length; i++) {
				RTGRuleSet rtgrs1 = null;
				switch (fileTypes[i].getType()) {
				case TRAIN:
					boolean isDone = false;
					switch (fileTypes[i].getTrainType()) {
					case STRING:
						// should be after cfg
						// CFG + [STRING] = CFG
						if (rs != null && tree == null && trs == null && str == null) {
							if (rs instanceof CFGRuleSet) {
								if (debug) Debug.debug(debug, "Intersecting CFG and string");
								Date preIsectTime = new Date();
								ret = FileType.TYPE.CFG;
								rs = new CFGRuleSet((CFGRuleSet)rs, (StringItem)fileObjects[i], config.getInt("beam"), timeLevel);
								if (config.getBoolean("check"))			
									getRuleSetCheck(outputBuffer, "CFG composed with string", rs);
								Date postIsectTime = new Date();
								Debug.dbtime(timeLevel, 2, preIsectTime, postIsectTime, "CFG Intersection with string "+i);	
								isDone = true;
								break;
							}
							// TODO: allow rtg string intersection as backward application
							else {
								throw new ConfigureException("Cannot intersect RTG with string");
							}
						}
						else {
							StringBuffer have = new StringBuffer();
							if (trs != null)
								have.append("transducer ");
							if (tree != null)
								have.append("tree ");
							if (str != null)
								have.append("string ");
							if (rs != null)
								have.append("automaton ");
							throw new ConfigureException("Expected CFG by itself before seeing string; we have "+have);			
						}
					case TREE:
					case TREE_TREE:
						// handled below
						break;
					default:
						throw new ConfigureException(fileTypes[i].getType()+" - "+fileTypes[i].getTrainType()+" is not a handled input type");
					}
					// break out if we were processing string here. tree falls through
					if (isDone)
						break;
				case TREE:

					// if nothing else exists, leave it alone. Otherwise convert it
					if (rs == null && trs == null && tree == null) {
						if (debug) Debug.debug(debug, "positioning terminal tree");
						tree = (TreeItem)fileObjects[i];
						ret = FileType.TYPE.TREE;
						break;
					}
					// if there is already another tree, pull it out and compose it
					// with this one into rtgrs1. Otherwise, just make this into
					// rtgrs1 by falling through
					if (debug) Debug.debug(debug, "converting tree to rtg");
					rtgrs1 = new RTGRuleSet((TreeItem)fileObjects[i], ts);
					if (debug) Debug.debug(debug, "converted "+((TreeItem)fileObjects[i])+" to "+rtgrs1);

					if (tree != null) {
						RTGRuleSet rtgrs0 = new RTGRuleSet(tree, ts);
						tree = null;
						Date preIsectTime = new Date();
						rtgrs1 = Intersect.intersectRuleSets(rtgrs0, rtgrs1);
						Date postIsectTime = new Date();
						Debug.dbtime(timeLevel, 2, preIsectTime, postIsectTime, "intersect two trees ");	
					}
				case RTG:

					// tree and RTG are similar but start off differently. tree is converted into RTG
					// intersection
					if (rtgrs1 == null)
						rtgrs1 = (RTGRuleSet)fileObjects[i];
					// prepare, unless this is the only file
					if (fileTypes.length > (startFile+1)) {
						if (debug) Debug.debug(debug, "prepping rtg for combination");
						Date preRemoveEpsilonTime = new Date();
						rtgrs1.removeEpsilons();
						Date postRemoveEpsilonTime = new Date();
						Debug.dbtime(timeLevel, 2, preRemoveEpsilonTime, postRemoveEpsilonTime, "remove epsilons from rule set "+i);
						if(checkNormForm) {
							Date preMakeNormalTime = new Date();
							rtgrs1.makeNormal();
							Date postMakeNormalTime = new Date();
							Debug.dbtime(timeLevel, 2, preMakeNormalTime, postMakeNormalTime, "made rule set "+i+" normal");

						}
						else
							Debug.prettyDebug("WARNING: not checking normal form!");
					}
					// left assoc rules:

					// if there is a tree, turn it into a rs and intersect
					// if there is a rs, intersect
					// if there is a trs, transform into RLN and compose (remember this is left assoc. only!)
					// if there is neither, just keep the rs as is.

					// none case
					// _ + [RTG] = RTG
					if (tree == null && rs == null && trs == null) {
						if (debug) Debug.debug(debug, "positioning terminal rtg");
						rs = rtgrs1;
						ret = FileType.TYPE.RTG;
					}
					// tree only case
					// TREE + [RTG] = RTG
					else if (tree != null && rs == null && trs == null) {
						if (debug) Debug.debug(debug, "converting tree to rtg");
						rs = new RTGRuleSet(tree, ts);
						if (debug) Debug.debug(debug, "converted "+((TreeItem)fileObjects[i])+" to "+rtgrs1);
						tree = null;
						if (debug) Debug.debug(debug, "intersecting new rtg into  tree");
						Date preIsectTime = new Date();
						RTGRuleSet rtgrscomp = Intersect.intersectRuleSets((RTGRuleSet)rs, rtgrs1);
						rs = rtgrscomp;
						Date postIsectTime = new Date();
						Debug.dbtime(timeLevel, 2, preIsectTime, postIsectTime, "intersect tree into rule set "+i);
						if (config.getBoolean("check"))			
							getRuleSetCheck(outputBuffer, "tree-intersected RTG", rtgrscomp);
						ret = FileType.TYPE.RTG;
					}
					// rs only case
					// RTG + [RTG] = RTG
					else if (tree == null && rs != null && trs == null) {
						if (debug) Debug.debug(debug, "prepping rtg for combination");
						RTGRuleSet rtgrs0 = (RTGRuleSet)rs;
						Date preRemoveEpsilonTime = new Date();
						rtgrs0.removeEpsilons();
						Date postRemoveEpsilonTime = new Date();
						Debug.dbtime(timeLevel, 2, preRemoveEpsilonTime, postRemoveEpsilonTime, "remove epsilons from extant rule set ");
						if(checkNormForm) {
							Date preMakeNormalTime = new Date();
							rtgrs0.makeNormal();
							Date postMakeNormalTime = new Date();
							Debug.dbtime(timeLevel, 2, preMakeNormalTime, postMakeNormalTime, "made extant rule set normal");

						}
						else
							Debug.prettyDebug("WARNING: not checking normal form!");

						if (debug) Debug.debug(debug, "intersecting new rtg into extant rtg");
						Date preIsectTime = new Date();
						RTGRuleSet rtgrscomp = Intersect.intersectRuleSets(rtgrs0, rtgrs1);
						rs = rtgrscomp;
						Date postIsectTime = new Date();
						Debug.dbtime(timeLevel, 2, preIsectTime, postIsectTime, "intersect rule set "+i);
						if (config.getBoolean("check"))			
							getRuleSetCheck(outputBuffer, "intersected RTG", rtgrscomp);
						ret = FileType.TYPE.RTG;
					}
					// trs only case
					// R + [RTG] => R + RLN = R
					else if (tree == null && trs != null && rs == null) {
						if (debug) Debug.debug(debug, "converting rtg into RLN and composing with previous R");
						Date preComposeTime = new Date();
						TreeTransducerRuleSet rtgtrs = new TreeTransducerRuleSet(rtgrs1);
						
						// left side seems right here...
						Vector<TreeTransducerRuleSet> vec = new Vector<TreeTransducerRuleSet>();
						trs.makeNonLookahead();
						vec.add((TreeTransducerRuleSet)trs);
						vec.add(rtgtrs);
						trs = new TreeTransducerRuleSet(vec, true, config.getInt("beam"));
						//trs = new TreeTransducerRuleSet((TreeTransducerRuleSet)trs, rtgtrs, config.getInt("beam"));
						trs.pruneUseless();
						Date postComposeTime = new Date();
						Debug.dbtime(timeLevel, 2, preComposeTime, postComposeTime, "composed with rtg "+i+" transformed to RLN");
						if (config.getBoolean("check"))			
							getTransducerRuleSetCheck(outputBuffer, "composed tree transducer with rtg as RLN", trs);
						ret = FileType.TYPE.TREE_TRANS;
					}
					else
						throw new UnusualConditionException("While combining automata, we have at least two of a tree, rs and trs simultaneously");
					break;
				case TRANSDUCER:
				case TREE_TRANS:

					// left assoc ruls:

					// if there is a tree, do forward application with fewer restrictions
					// if there is a rs, do forward application
					// if there is a trs, do composition
					// if there is none, just keep the trs as is

					// none case
					// _ + [R] = R
					if (tree == null && rs == null && trs == null) {
						if (debug) Debug.debug(debug, "positioning terminal R");
						trs = (TransducerRuleSet)fileObjects[i];
						ret = fileTypes[i].getType();
					}

					// trs only case
					// R + [RLN] = R
					else if (tree == null && trs != null && rs == null) {
						if (debug) Debug.debug(debug, "composing R(LN?) with previous R");
						Date preComposeTime = new Date();
						TreeTransducerRuleSet trs2 = (TreeTransducerRuleSet)fileObjects[i];
						trs.makeNonLookahead();
						trs2.makeNonLookahead();
						// late check of appropriateness; if these conditions exist user probably overrode defaults
						if (trs2.isCopying())
							throw new ConfigureException("Can't compose with a copying transducer");			
						if (trs2.isDeleting())
							throw new ConfigureException("Can't compose with a deleting transducer");			
						if (trs2.isExtended())
							throw new ConfigureException("Can't compose with an extended-input transducer");
						if (trs2.isInEps())
							throw new ConfigureException("Can't compose with an input-epsilon transducer");			

						Vector<TreeTransducerRuleSet> vec = new Vector<TreeTransducerRuleSet>();
						vec.add((TreeTransducerRuleSet)trs);
						vec.add(trs2);

						TreeTransducerRuleSet ttrs = new TreeTransducerRuleSet(vec, true, config.getInt("beam"));
						
						
						ttrs.pruneUseless();
						ret = FileType.TYPE.TREE_TRANS;
						Date postComposeTime = new Date();
						Debug.dbtime(timeLevel, 2, preComposeTime, postComposeTime, "composed with transducer rule set "+i);
						trs = ttrs;
						// add composed transducer check
						if (config.getBoolean("check"))
							getTransducerRuleSetCheck(outputBuffer, "composed Tree Transducer", ttrs);
					}
					// tree only case
					// TREE -> [xR] = RTG  
					else if (tree != null && rs == null && trs == null) {
						if (debug) Debug.debug(debug, "forward application of tree onto xR");
						Date preApplyTime = new Date();
						rs = new RTGRuleSet(tree, (TreeTransducerRuleSet)fileObjects[i]);
						Date postApplyTime = new Date();
						Debug.dbtime(timeLevel, 2, preApplyTime, postApplyTime, "forward application with transducer rule set "+i);
						tree = null;
						if (config.getBoolean("check"))
							getRuleSetCheck(outputBuffer, "forward application of tree", rs);

					}
					// rs only case
					// RTG -> [RL] = RTG
					else if (tree == null & rs != null && trs == null) {
						if (rs instanceof RTGRuleSet) {
							if (debug) Debug.debug(debug, "forward application of previous rtg onto R(L?)");
							Date preApplyTime = new Date();
							TreeTransducerRuleSet rtgtrs = new TreeTransducerRuleSet((RTGRuleSet)rs);
							TreeTransducerRuleSet targettrs = (TreeTransducerRuleSet)fileObjects[i];
							targettrs.makeNonLookahead();
							Vector<TreeTransducerRuleSet> vec = new Vector<TreeTransducerRuleSet>();
							vec.add(rtgtrs);
							vec.add(targettrs);

							// "badcomp" because the domain is not guaranteed
							TreeTransducerRuleSet badcomp = new TreeTransducerRuleSet(vec, (!targettrs.isExtended() && !targettrs.isInEps()), config.getInt("beam"));

							
//							TreeTransducerRuleSet badcomp = new TreeTransducerRuleSet(rtgtrs, (TreeTransducerRuleSet)fileObjects[i], config.getInt("beam"));
							badcomp.pruneUseless();
							rs = new RTGRuleSet(badcomp);
							Date postApplyTime = new Date();
							Debug.dbtime(timeLevel, 2, preApplyTime, postApplyTime, "forward application with transducer rule set "+i);
							if (config.getBoolean("check"))
								getRuleSetCheck(outputBuffer, "forward application of rtg", rs);
						}
						else 
							throw new ConfigureException("tried to do forward application of a CFG (should catch this earlier)");
					}
					else
						throw new UnusualConditionException("While combining automata, we have a rs and trs simultaneously");
					break;
				case STRING_TRANS:

					// left assoc ruls:

					// if there is a tree, do forward application with fewer restrictions
					// if there is a rs, do forward application
					// if there is a trs, do composition
					// if there is none, just keep the trs as is

					// none case
					// _ + [Rs] = Rs
					if (tree == null && rs == null && trs == null) {
						if (debug) Debug.debug(debug, "positioning terminal Rs");
						trs = (TransducerRuleSet)fileObjects[i];
						ret = fileTypes[i].getType();
					}
					// trs only case
					// R + [RLNs] = Rs
					else if (tree == null && trs != null && rs == null) {
						if (debug) Debug.debug(debug, "composing previous R with R(LN?)s");
						Date preComposeTime = new Date();
						StringTransducerRuleSet trs2 = (StringTransducerRuleSet)fileObjects[i];
						// late check of appropriateness; if these conditions exist user probably overrode defaults
						if (trs2.isCopying())
							throw new ConfigureException("Can't compose with a copying transducer");			
						if (trs2.isDeleting())
							throw new ConfigureException("Can't compose with a deleting transducer");		

						if (trs2.isExtended())
							throw new ConfigureException("Can't compose with an extended-input transducer");
						if (trs2.isInEps())
							throw new ConfigureException("Can't compose with an input-epsilon transducer");			

						Vector<TransducerRuleSet> vec = new Vector<TransducerRuleSet>();
						trs.makeNonLookahead();
						trs2.makeNonLookahead();
						vec.add(trs);
						vec.add(trs2);
						StringTransducerRuleSet ttrs = new StringTransducerRuleSet(vec, true, config.getInt("beam"));
//						StringTransducerRuleSet ttrs = new StringTransducerRuleSet((TreeTransducerRuleSet)trs, trs2, config.getInt("beam")); 

						ttrs.pruneUseless();
						ret = FileType.TYPE.STRING_TRANS;
						Date postComposeTime = new Date();
						Debug.dbtime(timeLevel, 2, preComposeTime, postComposeTime, "composed with transducer rule set "+i);
						trs = ttrs;
						// add composed transducer check
						if (config.getBoolean("check"))
							getTransducerRuleSetCheck(outputBuffer, "composed String Transducer", ttrs);
					}
					// tree only case
					// TREE -> [xRs] = CFG  
					else if (tree != null && rs == null && trs == null) {
						if (debug) Debug.debug(debug, "forward application of tree onto xRs");
						Date preApplyTime = new Date();
						StringTransducerRuleSet strtrans = (StringTransducerRuleSet)fileObjects[i];
						strtrans.makeNonLookahead();
						rs = new CFGRuleSet(tree, strtrans);
						Date postApplyTime = new Date();
						Debug.dbtime(timeLevel, 2, preApplyTime, postApplyTime, "forward application with transducer rule set "+i);
						tree = null;
						if (config.getBoolean("check"))
							getRuleSetCheck(outputBuffer, "forward application of tree", rs);
					}
					// rs only case
					// RTG -> [RLs] = CFG
					else if (tree == null && rs != null && trs == null) {
						if (rs instanceof RTGRuleSet) {
							if (debug) Debug.debug(debug, "forward application of previous rtg onto R(L?)s");
							Date preApplyTime = new Date();
							TreeTransducerRuleSet rtgtrs = new TreeTransducerRuleSet((RTGRuleSet)rs);
							StringTransducerRuleSet strtrans = (StringTransducerRuleSet)fileObjects[i];
							strtrans.makeNonLookahead();
							// "badcomp" because the domain is not guaranteed
							Vector<TransducerRuleSet> vec = new Vector<TransducerRuleSet>();
							vec.add(rtgtrs);
							vec.add(strtrans);
							// forward here (not sure why...seems like backward could work??)
							StringTransducerRuleSet badcomp = new StringTransducerRuleSet(vec, true, config.getInt("beam"));

//							StringTransducerRuleSet badcomp = new StringTransducerRuleSet(rtgtrs, (StringTransducerRuleSet)fileObjects[i], config.getInt("beam"));
							badcomp.pruneUseless();
							//if (debug) Debug.debug(debug, "formed bad composition of identity RTG and RLs: "+badcomp);
							rs = new CFGRuleSet(badcomp);
							Date postApplyTime = new Date();
							Debug.dbtime(timeLevel, 2, preApplyTime, postApplyTime, "forward application with transducer rule set "+i);
							if (config.getBoolean("check"))
								getRuleSetCheck(outputBuffer, "forward application of rtg", rs);
							//if (debug) Debug.debug(debug, "forward application yields "+rs);
						}
						else 
							throw new ConfigureException("tried to do forward application of a CFG (should catch this earlier)");
					}
					else
						throw new UnusualConditionException("While combining automata, we have at least two of a tree, rs and trs simultaneously");
					break;
				case CFG:
					// must appear solo
					// _ + [CFG] = CFG
					if (tree == null && rs == null && trs == null) {
						if (debug) Debug.debug(debug, "positioning terminal CFG");
						ret = fileTypes[i].getType();
						// for now, nothing more to do
						rs = (RuleSet)fileObjects[i];
					}
					else
						throw new ConfigureException("While combining automata, we have an automaton before reading CFG");
					break;
				case STRING:
					// should be after cfg
					// CFG + [STRING] = CFG
					if (rs != null && tree == null && trs == null && str == null) {
						if (rs instanceof CFGRuleSet) {
							if (debug) Debug.debug(debug, "Intersecting CFG and string");
							Date preIsectTime = new Date();
							ret = FileType.TYPE.CFG;
							rs = new CFGRuleSet((CFGRuleSet)rs, (StringItem)fileObjects[i], config.getInt("beam"), timeLevel);
							if (config.getBoolean("check"))			
								getRuleSetCheck(outputBuffer, "CFG composed with string", rs);
							Date postIsectTime = new Date();
							Debug.dbtime(timeLevel, 2, preIsectTime, postIsectTime, "CFG Intersection with string "+i);	
							break;
						}
						// TODO: allow rtg string intersection as backward application
						else {
							throw new ConfigureException("Cannot intersect RTG with string");
						}
					}
					else {
						StringBuffer have = new StringBuffer();
						if (trs != null)
							have.append("transducer ");
						if (tree != null)
							have.append("tree ");
						if (str != null)
							have.append("string ");
						if (rs != null)
							have.append("automaton ");
						throw new ConfigureException("Expected CFG by itself before seeing string; we have "+have);					}
				default:
					throw new ConfigureException(fileTypes[i].getType()+" is not a handled input type");
				}
			}	
		}
		else {
			// right associativity
			if (debug) Debug.debug(debug, "Right associativity");
			for (int i = fileTypes.length - 1; i >= startFile; i--) {
				RTGRuleSet rtgrs1 = null;
				switch (fileTypes[i].getType()) {
				case STRING:
					if (debug) Debug.debug(debug, "recognizing string");
					str = (StringItem)fileObjects[i];
					break;
				case TRAIN:
					boolean wasString = false;
					switch (fileTypes[i].getTrainType()) {
					case STRING:
						if (debug) Debug.debug(debug, "recognizing string");
						str = (StringItem)fileObjects[i];
						wasString = true;
						break;
					default:
						break;
					}
					if (wasString)
						break;
				case TREE:
					// if nothing else exists, leave it alone. Otherwise convert it
					if (rs == null && trs == null && str == null && tree == null) {
						if (debug) Debug.debug(debug, "positioning terminal tree");
						tree = (TreeItem)fileObjects[i];
						ret = FileType.TYPE.TREE;
						break;
					}
					if (str != null)
						throw new ConfigureException("Can't have string to the right of tree");
					if (debug) Debug.debug(debug, "converting tree to rtg");
					if (rtgrs1 == null)
						rtgrs1 = new RTGRuleSet((TreeItem)fileObjects[i], ts);
				case RTG:
					if (str != null)
						throw new ConfigureException("Can't have string to the right of rtg");
					// tree and RTG are similar but start off differently. tree is converted into RTG
					// intersection
					if (rtgrs1 == null)
						rtgrs1 = (RTGRuleSet)fileObjects[i];
					// prepare, unless this is the only file
					if (fileTypes.length > (startFile+1)) {
						if (debug) Debug.debug(debug, "prepping rtg for combination");
						Date preRemoveEpsilonTime = new Date();
						rtgrs1.removeEpsilons();
						Date postRemoveEpsilonTime = new Date();
						Debug.dbtime(timeLevel, 2, preRemoveEpsilonTime, postRemoveEpsilonTime, "remove epsilons from rule set "+i);
						if(checkNormForm) {
							Date preMakeNormalTime = new Date();
							rtgrs1.makeNormal();
							Date postMakeNormalTime = new Date();
							Debug.dbtime(timeLevel, 2, preMakeNormalTime, postMakeNormalTime, "made rule set "+i+" normal");

						}
						else
							Debug.prettyDebug("WARNING: not checking normal form!");
					}
					// right assoc rules:

					// if there is a rs, intersect
					// if there is a trs, do forward application (right assoc)
					// if there is a tree, convert it to rs and intersect
					// if there is neither, just keep the rs as is.

					// tree case. prep for below
					if (tree != null && rs == null) {
						rs = new RTGRuleSet(tree, ts);
						tree = null;
					}
					
					// neither case
					// [RTG] + _ = RTG
					if (rs == null && trs == null) {
						if (debug) Debug.debug(debug, "positioning terminal rtg");
						rs = rtgrs1;
						ret = FileType.TYPE.RTG;
					}
					// rs only case
					// [RTG] + RTG = RTG
					else if (rs != null && trs == null) {
						if (rs instanceof RTGRuleSet) {
							if (debug) Debug.debug(debug, "intersecting new rtg into extant rtg");
							// must prepare extant rtg
							if (debug) Debug.debug(debug, "prepping extant rtg for combination");
							Date preRemoveEpsilonTime = new Date();
							rs.removeEpsilons();
							Date postRemoveEpsilonTime = new Date();
							Debug.dbtime(timeLevel, 2, preRemoveEpsilonTime, postRemoveEpsilonTime, "remove epsilons from rule set "+i);
							if(checkNormForm) {
								Date preMakeNormalTime = new Date();
								rs.makeNormal();
								Date postMakeNormalTime = new Date();
								Debug.dbtime(timeLevel, 2, preMakeNormalTime, postMakeNormalTime, "made rule set "+i+" normal");

							}
							else
								Debug.prettyDebug("WARNING: not checking normal form!");
		
							Date preIsectTime = new Date();
							
							// "natural" intersection -- seems buggy!
							RTGRuleSet rtgrscomp = Intersect.intersectRuleSets(rtgrs1, (RTGRuleSet)rs);
							rs = rtgrscomp;
							
							// intersection via composition and projection -- probably slower than "natural"
//							TreeTransducerRuleSet trs_rtgrs1 = new TreeTransducerRuleSet(rtgrs1);
//							TreeTransducerRuleSet trs_rs = new TreeTransducerRuleSet((RTGRuleSet)rs);
//							TreeTransducerRuleSet comp = new TreeTransducerRuleSet(trs_rtgrs1, trs_rs);
//							RTGRuleSet rtgrscomp = new RTGRuleSet(comp);
//							rs = rtgrscomp;
							
							Date postIsectTime = new Date();
							Debug.dbtime(timeLevel, 2, preIsectTime, postIsectTime, "intersect rule set "+i);
							if (config.getBoolean("check"))			
								getRuleSetCheck(outputBuffer, "intersected RTG", rtgrscomp);
							ret = FileType.TYPE.RTG;
						}
						else 
							throw new ConfigureException("tried to intersect a RTG with a CFG (should catch this earlier)");
					}
					// trs only case -- turn this into RLN, compose, take range projection

					// TODO: application to xRL
					else if (trs != null && rs == null) {
						Date preComposeTime = new Date();
						TreeTransducerRuleSet rtgtrs = new TreeTransducerRuleSet(rtgrs1);
						trs.makeNonLookahead();
						// [RTG] -> RL = RTG			
						if (trs instanceof TreeTransducerRuleSet) {
							if (debug) Debug.debug(debug, "forward application of RTG onto previous R(L?)");
							
							Vector<TreeTransducerRuleSet> vec = new Vector<TreeTransducerRuleSet>();
							vec.add(rtgtrs);
							vec.add((TreeTransducerRuleSet)trs);
							TreeTransducerRuleSet forwardtrs = new TreeTransducerRuleSet(vec, (!trs.isExtended() && !trs.isInEps()), config.getInt("beam"));

//							TreeTransducerRuleSet forwardtrs = new TreeTransducerRuleSet(rtgtrs, (TreeTransducerRuleSet)trs, config.getInt("beam"));
							forwardtrs.pruneUseless();
							rs = new RTGRuleSet(forwardtrs);
							ret = FileType.TYPE.RTG;
							if (config.getBoolean("check"))	{		
								getTransducerRuleSetCheck(outputBuffer, "forward application with tree transducer", trs);
								getRuleSetCheck(outputBuffer, "forward application", rs);
							}
						}
						// [RTG] -> RLs = CFG
						else {
							if (debug) Debug.debug(debug, "forward application of RTG onto previous R(L?)s");
							Vector<TransducerRuleSet> vec = new Vector<TransducerRuleSet>();
							vec.add(rtgtrs);
							vec.add(trs);
							// backward here because rtgrs definitely flat but the new transducer might be extended
							StringTransducerRuleSet forwardtrs = new StringTransducerRuleSet(vec, false, config.getInt("beam"));

//							StringTransducerRuleSet forwardtrs = new StringTransducerRuleSet(rtgtrs, (StringTransducerRuleSet)trs, config.getInt("beam"));
							forwardtrs.pruneUseless();
							rs = new CFGRuleSet(forwardtrs);
							ret = FileType.TYPE.CFG;
							if (config.getBoolean("check"))	{		
								getTransducerRuleSetCheck(outputBuffer, "forward application with string transducer", trs);
								getRuleSetCheck(outputBuffer, "forward application", rs);
							}
						}
						trs = null;
						Date postComposeTime = new Date();
						Debug.dbtime(timeLevel, 2, preComposeTime, postComposeTime, "applied rtg "+i+" as forward application");
					}
					else
						throw new UnusualConditionException("While combining automata, we have a rs and trs simultaneously");
					break;
				case TRANSDUCER:
				case TREE_TRANS:
					if (str != null)
						throw new ConfigureException("Can't have string to the right of tree transducer");
					// right assoc ruls:

					// if there is a tree, do direct backward application
					// if there is a rs, do backward application
					// if there is a trs, do composition
					// if there is neither, just keep the trs as is

					// neither case
					// [R] + _ = R
					if (rs == null && trs == null && tree == null) {
						if (debug) Debug.debug(debug, "positioning terminal R");
						trs = (TransducerRuleSet)fileObjects[i];
						ret = fileTypes[i].getType();
					}
					// trs only case
					else if (trs != null && rs == null && tree == null) {
						Date preComposeTime = new Date();
						trs.makeNonLookahead();
						TreeTransducerRuleSet treetrans = (TreeTransducerRuleSet)fileObjects[i];
						treetrans.makeNonLookahead();
						// [RL] + RLN = RL
						if (trs instanceof TreeTransducerRuleSet) {
							if (debug) Debug.debug(debug, "composing R(L?) with previous R(LN?)");
							
							Vector<TreeTransducerRuleSet> vec = new Vector<TreeTransducerRuleSet>();
							vec.add(treetrans);
							vec.add((TreeTransducerRuleSet)trs);
							trs = new TreeTransducerRuleSet(vec, false, config.getInt("beam"));

//							trs = new TreeTransducerRuleSet((TreeTransducerRuleSet)fileObjects[i], (TreeTransducerRuleSet)trs, config.getInt("beam"));
							trs.pruneUseless();
							ret = FileType.TYPE.TREE_TRANS;
							if (config.getBoolean("check"))
								getTransducerRuleSetCheck(outputBuffer, "composed Tree Transducer", trs);
						}
						// [RL] + RLNs = RLs
						else {
							if (debug) Debug.debug(debug, "composing R(L?) with previous R(LN?)s");
							Vector<TransducerRuleSet> vec = new Vector<TransducerRuleSet>();
							vec.add(treetrans);
							vec.add(trs);
							// backward here because rtgrs definitely flat but the new transducer might be extended
							trs = new StringTransducerRuleSet(vec, false, config.getInt("beam"));

//							trs = new StringTransducerRuleSet((TreeTransducerRuleSet)fileObjects[i], (StringTransducerRuleSet)trs, config.getInt("beam"));
							trs.pruneUseless();
							ret = FileType.TYPE.STRING_TRANS;
							if (config.getBoolean("check"))
								getTransducerRuleSetCheck(outputBuffer, "composed String Transducer", trs);
						}
						Date postComposeTime = new Date();
						Debug.dbtime(timeLevel, 2, preComposeTime, postComposeTime, "composed with transducer rule set "+i);
					}
					// rs only case
					// TODO: make backward app use native backward app internally
					else if (rs != null && trs == null && tree == null) {
						// [RL] <- RTG = RTG
						if (rs instanceof RTGRuleSet) {
							if (debug) Debug.debug(debug, "backward application of previous rtg onto R(L?)");
							Date preApplyTime = new Date();
							((RTGRuleSet)rs).removeEpsilons();
							TreeTransducerRuleSet rtgtrs = new TreeTransducerRuleSet((RTGRuleSet)rs);
//							if (debug) Debug.debug(debug, ">>>Turned rtg:\n"+rs+">>>Into\n"+rtgtrs);
							TreeTransducerRuleSet lefttrs = (TreeTransducerRuleSet)fileObjects[i];
							lefttrs.makeNonLookahead();
							Vector<TreeTransducerRuleSet> vec = new Vector<TreeTransducerRuleSet>();
							vec.add(lefttrs);
							vec.add(rtgtrs);
							TransducerRuleSet comp = new TreeTransducerRuleSet(vec, true, config.getInt("beam"));
							//TransducerRuleSet comp = new TreeTransducerRuleSet(lefttrs, rtgtrs, config.getInt("beam"));
							//if (debug) Debug.debug(debug, "comp before pruning: "+comp);
							comp.pruneUseless();
						    if (debug) Debug.debug(debug, "comp after pruning: "+comp);
							if (comp.isExtended()) {
								comp.makeNonExtended();
			//				    if (debug) Debug.debug(debug, "comp after nonextended: "+comp);
							}
							rs = new RTGRuleSet(comp);
							Date postApplyTime = new Date();
							Debug.dbtime(timeLevel, 2, preApplyTime, postApplyTime, "backward application with transducer rule set "+i);
							if (config.getBoolean("check"))	{		
								getTransducerRuleSetCheck(outputBuffer, "backward application with tree transducer", lefttrs);
								getRuleSetCheck(outputBuffer, "backward application", rs);
							}
						}
						else 
							throw new ConfigureException("tried to do backward application of a CFG (should catch this earlier)");
					}
					// tree only case
					else if (rs == null && trs == null && tree != null) {
						// [RL(?)] <- TREE = RTG
						if (debug) Debug.debug(debug, "backward application of previous tree onto R(L?)");
						
						// can't use direct tree application if transducer is copying -- convert to rtg
						if (((TreeTransducerRuleSet)fileObjects[i]).isCopying()) {
							Date preApplyTime = new Date();
							RTGRuleSet treers = new RTGRuleSet(tree, ts);
							TreeTransducerRuleSet rtgtrs = new TreeTransducerRuleSet(treers);
//							if (debug) Debug.debug(debug, ">>>Turned rtg:\n"+rs+">>>Into\n"+rtgtrs);
							TreeTransducerRuleSet lefttrs = (TreeTransducerRuleSet)fileObjects[i];
							lefttrs.makeNonLookahead();
							Vector<TreeTransducerRuleSet> vec = new Vector<TreeTransducerRuleSet>();
							vec.add(lefttrs);
							vec.add(rtgtrs);
							TransducerRuleSet comp = new TreeTransducerRuleSet(vec, true, config.getInt("beam"));
							//TransducerRuleSet comp = new TreeTransducerRuleSet(lefttrs, rtgtrs, config.getInt("beam"));
							//if (debug) Debug.debug(debug, "comp before pruning: "+comp);
							comp.pruneUseless();
						    if (debug) Debug.debug(debug, "comp after pruning: "+comp);
							if (comp.isExtended()) {
								comp.makeNonExtended();
			//				    if (debug) Debug.debug(debug, "comp after nonextended: "+comp);
							}
							rs = new RTGRuleSet(comp);
							Date postApplyTime = new Date();
							Debug.dbtime(timeLevel, 2, preApplyTime, postApplyTime, "backward application with transducer rule set "+i);							
						}
						else {
							Date preApplyTime = new Date();
							TreeTransducerRuleSet treetrans = (TreeTransducerRuleSet)fileObjects[i];
							treetrans.makeNonLookahead();
							rs = new RTGRuleSet(treetrans, tree);
							Date postApplyTime = new Date();
							Debug.dbtime(timeLevel, 2, preApplyTime, postApplyTime, "backward application with transducer rule set "+i);
						}
						tree = null;
						if (config.getBoolean("check"))
							getRuleSetCheck(outputBuffer, "backward application of tree", rs);
					}
					else
						throw new UnusualConditionException("While combining automata, we have a rs and trs simultaneously");
					break;
				case STRING_TRANS:

					// backward application of strings, i.e. decoding
					if (str != null) {
						if (debug) Debug.debug(debug, "applying string to XRS");
						ret = FileType.TYPE.RTG;
						StringTransducerRuleSet lefttst = (StringTransducerRuleSet)fileObjects[i];
						lefttst.makeNonLookahead();
						rs = new RTGRuleSet(lefttst, str, config.getInt("beam"), timeLevel);
						if (config.getBoolean("check"))
							getRuleSetCheck(outputBuffer, "RTG from string right-applied to XRS", rs);
						str = null;
					}
					
					// if there is any automaton, problem
					// if there is neither, keep as is
					else if (rs == null && trs == null) {
						if (debug) Debug.debug(debug, "positioning terminal Rs");
						trs = (TransducerRuleSet)fileObjects[i];
						ret = fileTypes[i].getType();
					}
					else
						throw new ConfigureException("Can't have any items to right of string transducer!");
					break;
				case CFG:
					// [CFG] <- STRING = CFG
					if (rs == null && trs == null && str != null) {
						if (debug) Debug.debug(debug, "Intersecting CFG and string");
						ret = fileTypes[i].getType();
						rs = new CFGRuleSet((CFGRuleSet)fileObjects[i], str, config.getInt("beam"), timeLevel);
						if (config.getBoolean("check"))			
							getRuleSetCheck(outputBuffer, "CFG composed with string", rs);
						str = null;
					}
					// _ + [CFG] = CFG
					else if (str == null && rs == null && trs == null) {
						if (debug) Debug.debug(debug, "positioning terminal CFG");
						ret = fileTypes[i].getType();
						// for now, nothing more to do
						rs = (RuleSet)fileObjects[i];
					}
					else
						throw new ConfigureException("While combining automata, we have something other than string  before reading CFG");
					break;
				default:
					throw new ConfigureException(fileTypes[i].getType()+" is not a handled input type");
				}
			}

		}
		// print-safe if rs and there was more than one file
		if (rs != null && fileTypes.length > (startFile+1)) {
			Date preMakeSafeTime = new Date();
//			Debug.debug(true, "Not making print safe!");
			rs.makePrintSafe();
			Date postMakeSafeTime = new Date();
			Debug.dbtime(timeLevel, 2, preMakeSafeTime, postMakeSafeTime, "Make grammar print safe");	
		}
		if (debug) {
			if (rs == null) Debug.debug(debug, "rs is null");
			if (trs == null) Debug.debug(debug, "trs is null");
		}
		trsarr[0] = trs;
		rsarr[0] = rs;
		treearr[0] = tree;
		return ret;
	}


	// given a conversion type and an automaton (one of trs, rs should be null) make the conversion
	private static FileType.TYPE convertAutomata(XF xformtype, FileType.TYPE currType, 
			TransducerRuleSet[] trsarr, RuleSet[] rsarr, TreeItem[] treearr,
			JSAPResult config, int timeLevel,
			StringBuffer outputBuffer) throws ConfigureException, 
			ImproperConversionException {
		boolean debug = false;
		RuleSet rs = rsarr[0];
		TransducerRuleSet trs = trsarr[0];
		TreeItem tree = treearr[0];
		if (debug) {
			if (rs == null) Debug.debug(debug, "rs is null");
			if (trs == null) Debug.debug(debug, "trs is null");
			if (tree == null) Debug.debug(debug, "tree is null");
			else Debug.debug(debug, "tree is "+tree);
		}
		// transformation: from currtype to xformtype
		FileType.TYPE retType = FileType.TYPE.UNKNOWN;
		switch (xformtype) {

		case NONE:
			// no xformtype. probably should never be here
			break;

		case RTG:
			// rtg: projection of transdcuers and nonterm info of cfgs

			if (debug) Debug.debug(debug, "Converting to RTG from "+currType);
			switch (currType) {
			case RTG:
				Debug.prettyDebug("Warning: Specified conversion from RTG to RTG; no action taken");
				retType = currType;
				break;
			case CFG:
				rs = new RTGRuleSet((CFGRuleSet)rs);
				// make conversions print safe
				Date preMakeSafeTime = new Date();
				rs.makePrintSafe();
				Date postMakeSafeTime = new Date();
				Debug.dbtime(timeLevel, 2, preMakeSafeTime, postMakeSafeTime, "Make grammar print safe");	
				if (config.getBoolean("check"))			
					getRuleSetCheck(outputBuffer, "converted CFG", rs);
				retType = FileType.TYPE.RTG;
				break;
			case TRANSDUCER:
			case TREE_TRANS:
			case STRING_TRANS:
				if (trs.isExtended())
					trs.makeNonExtended();
				// no longer setting weights when taking domain...
				//		trs.normalizeWeights();
				rs = new RTGRuleSet(trs);
				// make conversions print safe
				preMakeSafeTime = new Date();
				rs.makePrintSafe();
				postMakeSafeTime = new Date();
				Debug.dbtime(timeLevel, 2, preMakeSafeTime, postMakeSafeTime, "Make grammar print safe");	
				if (config.getBoolean("check"))			
					getRuleSetCheck(outputBuffer, "converted RTG", rs);
				trs = null;
				retType = FileType.TYPE.RTG;
				break;
			case TREE:
				rs = new RTGRuleSet(tree, tree.semiring);
				// make conversions print safe
				preMakeSafeTime = new Date();
				rs.makePrintSafe();
				postMakeSafeTime = new Date();
				Debug.dbtime(timeLevel, 2, preMakeSafeTime, postMakeSafeTime, "Make grammar print safe");	
				if (config.getBoolean("check"))			
					getRuleSetCheck(outputBuffer, "converted RTG", rs);
				tree = null;
				retType = FileType.TYPE.RTG;
				break;				
			default:
				throw new ConfigureException("Attempted to transform non-valid automaton type "+currType+" to RTG");
			}
			break;

		case CFG:
			// cfg: loss of info for rtgs, range projection for XRS
			if (debug) Debug.debug(debug, "Converting to CFG");
			switch (currType) {
			case RTG:
				rs = new CFGRuleSet((RTGRuleSet)rs);
				// make conversions print safe
				Date preMakeSafeTime = new Date();
				rs.makePrintSafe();
				Date postMakeSafeTime = new Date();
				Debug.dbtime(timeLevel, 2, preMakeSafeTime, postMakeSafeTime, "Make grammar print safe");	
				if (config.getBoolean("check"))			
					getRuleSetCheck(outputBuffer, "converted CFG", rs);
				retType = FileType.TYPE.CFG;
				break;
			case CFG:
				Debug.prettyDebug("Warning: Specified conversion from CFG to CFG; no action taken");
				retType = currType;
				break;

			case STRING_TRANS:
				rs = new CFGRuleSet((StringTransducerRuleSet)trs);
				trs = null;
				// make conversions print safe
				preMakeSafeTime = new Date();
				rs.makePrintSafe();
				postMakeSafeTime = new Date();
				Debug.dbtime(timeLevel, 2, preMakeSafeTime, postMakeSafeTime, "Make grammar print safe");	
				if (config.getBoolean("check"))			
					getRuleSetCheck(outputBuffer, "converted CFG", rs);
				retType = FileType.TYPE.CFG;
				break;
				
			default:
				throw new ConfigureException("Attempted to transform non-valid automaton type "+currType+" to CFG");
			}
			break;

		case XR:
			// xr: identity transducer from RTG, double conversion for CFG
			if (debug) Debug.debug(debug, "Converting to XR");
			switch (currType) {
			case CFG:
				// CFG->XR is CFG->RTG->XR
				rs = new RTGRuleSet((CFGRuleSet)rs);
				if (config.getBoolean("check"))			
					getRuleSetCheck(outputBuffer, "intermediate converted RTG", rs);
			case RTG:
				trs = new TreeTransducerRuleSet((RTGRuleSet)rs);
				if (config.getBoolean("check"))
					getTransducerRuleSetCheck(outputBuffer, "converted Tree Transducer", trs);
				rs = null;
				retType = FileType.TYPE.TREE_TRANS;
				break;
			case TRANSDUCER:
			case TREE_TRANS:
			case STRING_TRANS:
				Debug.prettyDebug("Warning: Specified conversion from TRANSDUCER to XR; no action taken");
				retType = currType;
				break;
			default:
				throw new ConfigureException("Attempted to transform non-valid automaton type "+currType+" to XR");
			}
			break;

		case XRS:
			// xrs: double conversion for cfg
			if (debug) Debug.debug(debug, "Converting to XRS");
			switch (currType) {
			case CFG:
				// CFG->XRS is CFG->RTG->XRS
				rs = new RTGRuleSet((CFGRuleSet)rs);
				if (config.getBoolean("check"))			
					getRuleSetCheck(outputBuffer, "intermediate converted RTG", rs);
			case RTG:
				trs = new StringTransducerRuleSet((RTGRuleSet)rs);
				if (config.getBoolean("check"))
					getTransducerRuleSetCheck(outputBuffer, "converted Tree Transducer", trs);
				rs = null;
				retType = FileType.TYPE.STRING_TRANS;
				break;
			case TRANSDUCER:
				Debug.prettyDebug("Warning: Specified conversion from TRANSDUCER to XRS; no action taken");
				retType = currType;
				break;
			default:
				throw new ConfigureException("Attempted to transform non-valid automaton type "+currType+" to XRS");
			}
			break;

		default:
			throw new ConfigureException("Invalid conversion type "+xformtype);
		}
		rsarr[0] = rs;
		trsarr[0] = trs;
		treearr[0] = tree;
		return retType;
	}

	// depending on configuration, RTG gets some preparation before output
	private static void prepareRTGForOutput(RTGRuleSet rtg, JSAPResult config, int timeLevel, StringBuffer outputBuffer) throws UnusualConditionException {
		boolean debug = false;
		// normal form and or making finite. 
		// Either it's explicit (via a switch) or it's implicit (because of pruning or determinization)

		// finite: explicit with "remloop". Implicit with determ
		// explicit print level is lower than implicit
//		if (config.getBoolean("remloop") || 
//				config.contains("determ")) {
//			int printLevel = (config.contains("determ") ? 2 : 1);
//			Date preFiniteTime = new Date();
//			if (debug) Debug.debug(debug, "making finite");
//			rtg.makeFinite();
//			Date postFiniteTime = new Date();
//			Debug.dbtime(timeLevel, printLevel, preFiniteTime, postFiniteTime, "make finite");
			// add finite rtg to check info
//			if (config.getBoolean("check"))
//				getRuleSetCheck(outputBuffer, "made finite (loop removal)", rtg);
//		}

		

		// pruning
		if (config.contains("prune")) {
			Date preNormTime = new Date();
			if (debug) Debug.debug(debug, "making normal");
			//		rs1.makeEfficientNormal();
			rtg.makeNormal();
			Date postNormTime = new Date();
			int printLevel = (config.contains("determ") || config.contains("prune") ? 2 : 1);
			Debug.dbtime(timeLevel, printLevel, preNormTime, postNormTime, "make normal");
			// add normal form rtg to check info
			if (config.getBoolean("check"))
				getRuleSetCheck(outputBuffer, "after normal form", rtg);
			Date prePruneTime = new Date();
			double prune = config.getDouble("prune");
			KBest k = new KBest(rtg);
			if (debug) Debug.debug(debug, "pruning rules");
			k.pruneRules(prune);
			Date postPruneTime = new Date();
			Debug.dbtime(timeLevel, 1, prePruneTime, postPruneTime, "prune rule set");
			// add pruned rtg to check info
			if (config.getBoolean("check"))
				getRuleSetCheck(outputBuffer, "pruned to "+prune, rtg);
		}

		
	}



	// do some operation on a grammar automaton to generate output to a specified handle
	// if the outputBuffer has content and there are no other calls for content (-g, -k), dump it to w. 
	// If it has content and there are calls for content, dump it to stderr
	// If there are no calls for content, just write the rule set.
	private static void generateOutput(RuleSet rs, JSAPResult config, FileType.TYPE finalType,
			OutputStreamWriter w, int timeLevel, StringBuffer outputBuffer) throws IOException, 
			ConfigureException,
			UnusualConditionException {
		boolean debug = false;
		boolean hadContent = false;
		Semiring ts = rs.getSemiring();

		if (config.contains("kbest")) {
			Date preKCreateTime = new Date();
			hadContent = true;
			int num = config.getInt("kbest", 1);
			KBest k = new KBest(rs);
			Date postKCreateTime = new Date();
			Debug.dbtime(timeLevel, 2, preKCreateTime, postKCreateTime, "Create the kbest object");
			Item[] topItems;
			// get top trees if a rtg, top strings if a cfg
			Date preKBestTime = new Date();
			topItems = k.getKBestItems(num);
			Date postKBestTime = new Date();
			Debug.dbtime(timeLevel, 2, preKBestTime, postKBestTime, "Obtain the kbest items");
			for (int i = 0; i < topItems.length; i++) {
				String output = null;
				if (topItems[i] == null)
					output = "0\n";
				else if (config.getBoolean("yield"))
					output = topItems[i].toYield()+" # "+Rounding.round(ts.internalToPrint(topItems[i].weight), 6)+"\n";
				else
					output = topItems[i].toString()+" # "+Rounding.round(ts.internalToPrint(topItems[i].weight), 6)+"\n";
				w.write(output);
			}
		}
		else if (config.contains("krandom")) {
			Date preKCreateTime = new Date();
			hadContent = true;
			int num = config.getInt("krandom", 1);
			KBest k = null;
			Long seed = config.contains("krandomseed") ? config.getLong("krandomseed") : null;
			if (finalType == FileType.TYPE.RTG) {
				RTGRuleSet rtgrs = (RTGRuleSet)rs;
				rtgrs.makeNormal();
				k = new KBest(rtgrs, seed);
			}
			else {
				k = new KBest(rs, seed);
			}
			Date postKCreateTime = new Date();
			Debug.dbtime(timeLevel, 2, preKCreateTime, postKCreateTime, "Create the kbest object");
			Date preKBestTime = new Date();
			for (int i = 0; i < num; i++) {
				Item t = k.getRandomItem(config.getInt("randomlimit"));
				String output = null;
				if (config.getBoolean("yield"))
					output = t.toYield()+" # "+Rounding.round(ts.internalToPrint(t.weight), 6)+"\n";
				else
					output = t.toString()+" # "+Rounding.round(ts.internalToPrint(t.weight), 6)+"\n";
				w.write(output);
			}
			Date postKBestTime = new Date();
			Debug.dbtime(timeLevel, 2, preKBestTime, postKBestTime, "Obtain the k random items");
		}
		if ((config.contains("check") || config.contains("align")) && outputBuffer.length() > 0) {
			if (hadContent)
				Debug.prettyDebug(outputBuffer.toString());
			else
				w.write(outputBuffer.toString());
			outputBuffer = new StringBuffer();
			hadContent = true;
		}
		if (!hadContent) {
			Date prePrintTime = new Date();
			rs.print(w);
			Date postPrintTime = new Date();
			Debug.dbtime(timeLevel, 2, prePrintTime, postPrintTime, "print grammar");
		}
	}

	// do some operation on a transducer to generate output to a specified handle
	// this is basically just writing the rule set or the output buffer for now.
	// eventually we should have -k and -g functionality here...

	private static void generateOutput(TransducerRuleSet trs, JSAPResult config, OutputStreamWriter w, 
			int timeLevel, StringBuffer outputBuffer) throws IOException, ConfigureException {
		boolean debug = false;
		boolean hadContent = false;
		if ((config.contains("check") || config.contains("align")) && outputBuffer.length() > 0) {
			if (hadContent)
				Debug.prettyDebug(outputBuffer.toString());
			else
				w.write(outputBuffer.toString());
			outputBuffer = new StringBuffer();
			hadContent = true;
		}
		if (!hadContent) {
			Date prePrintTime = new Date();
			trs.print(w);
			Date postPrintTime = new Date();
			Debug.dbtime(timeLevel, 2, prePrintTime, postPrintTime, "print transducer");
		}
	}    

	// either get k-best alignments or convert to RTGRS and print a Derivation Rule Set
	private static void generateOutput(DerivationRuleSet drs, JSAPResult config, OutputStreamWriter w, 
			int timeLevel, StringBuffer outputBuffer) throws IOException, ConfigureException, UnusualConditionException {
		boolean debug = false;
		// get k-best aligns
		if (config.contains("align")) {
			int num = config.getInt("align", 1);
			if (drs == null)
				w.write("0 :: NULL\n");
			else {
				DRSKBest k = new DRSKBest(drs);
//				TreeItem[] trees = k.getKBestTrees(num, false);
				String [] als = k.getKBestAlignments(num);
				int i = 0;
				for (; i < als.length; i++) {
					w.write(i+" :: "+als[i]+"\n");
				}
				for (; i < num; i++) {
					w.write(i+" :: 0\n");
				}
				i = 0;
	//			for (; i < trees.length; i++)
		//			w.write(trees[i].toString()+"\n");
			}
		}
		else if (config.contains("check") && outputBuffer.length() > 0) {
			w.write(outputBuffer.toString());
		}
		else {
			Date preConvertTime = new Date();				
			RTGRuleSet rs = new RTGRuleSet(drs);
			rs.makePrintSafe();
			Date postConvertTime = new Date();
			Debug.dbtime(timeLevel, 2, preConvertTime, postConvertTime, "convert derivation rtg to regular rtg");
			w.write(rs.toString());
		}
	}
	
	// print a tree. maybe print info. if yield, print strin

	private static void generateOutput(TreeItem t, JSAPResult config, OutputStreamWriter w, 
			int timeLevel, StringBuffer outputBuffer) throws IOException, ConfigureException {
		boolean debug = false;
		boolean hadContent = false;
		if ((config.contains("check") || config.contains("align")) && outputBuffer.length() > 0) {
			if (hadContent)
				Debug.prettyDebug(outputBuffer.toString());
			else
				w.write(outputBuffer.toString());
			outputBuffer = new StringBuffer();
			hadContent = true;
		}
		if (!hadContent) {
			Date prePrintTime = new Date();
			if (config.getBoolean("yield"))
				w.write(t.toYield()+"\n");
			else
				w.write(t.toString()+"\n");
			Date postPrintTime = new Date();
			Debug.dbtime(timeLevel, 2, prePrintTime, postPrintTime, "print tree");
		}
	}    

	public static void main(String argv[]) throws Exception {
		boolean debug = false;

		Debug.prettyDebug("This is Tiburon, version "+VERSION);

		Date startTime = new Date();
		// parameter processor and configuration settings
		JSAP jsap = new JSAP();
		JSAPResult config = null;
		int timeLevel = -1;

		// encoding of read and written files
		String encoding = null;
		// semiring of all operations
		Semiring ts = null;
		// destination transformation performed, if any
		XF xformtype = XF.NONE;
		// temporary writing location
		StringBuffer initOutputBuffer = new StringBuffer();
		// where we're writing
		File outfile = null;
		// what we're reading
		File infiles[] = null;
		// the type of input files, as detected later on
		FileType fileTypes[] = null;

		// the input files, once they're read in
		// they can be lots of different things
		Object[] fileObjects = null;

		// the main objects we read into
		RuleSet rs1 = null;
		TransducerRuleSet trs1 = null;
		TreeItem tree1 = null;

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

			if (config.contains("xform")) {
				xformtype = XF.get(config.getString("xform"));
			}


			// pruning only allowed for log-type semirings
//			if (srtype.equals("real") &&
//					config.contains("prune"))
//				throw new ConfigureException("Can't prune with real semiring. Use tropical or tropicalaccumulative");

			outfile = config.getFile("outfile");
			infiles = config.getFileArray("infiles");
			fileObjects = new Object[infiles.length];
			if (config.getBoolean("conditional")) 
				TransducerTraining.setConditional(true);
			else
				TransducerTraining.setConditional(false);

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

			// figure out what the infiles are
			Date preDetectTime = new Date();
			fileTypes = detectFiles(brs, infiles, encoding, config);
			Date postDetectTime = new Date();
			Debug.dbtime(timeLevel, 1, preDetectTime, postDetectTime,  "detect file types");


			// TODO: this is mostly useless but is here because of ambiguity 
			// when reading batch/training files
			// make sure the sequence of infiles is kosher and determine the ultimate type
			Date preVerifyTime = new Date();
			FileType.TYPE inputType = verifySequence(fileTypes, config, xformtype);
			Date postVerifyTime = new Date();
			Debug.dbtime(timeLevel, 1, preVerifyTime, postVerifyTime,  "verify file sequence");

			// read in the automata and pre-process the training info
			Date preReadObjectTime = new Date();
			fileObjects = readAutomata(brs, infiles, fileTypes, config, encoding, ts, inputType, timeLevel, initOutputBuffer);
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
		catch (UnusualConditionException e) {
			// TODO: include context info here
			System.err.println("Unusual condition while detecting input file: "+e.getMessage());
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

			// do all intersections and compositions
			Date preCombineTime = new Date();
			// one-slot arrays for passing multiple objects by reference
			TransducerRuleSet[] trsarr = new TransducerRuleSet[1];
			RuleSet[] rsarr = new RuleSet[1];
			TreeItem[] treearr = new TreeItem[1];
			// find the elements in the file objects list that are batches and save them off for loading
			ArrayList<Integer> batchIndices = new ArrayList<Integer>();
			ArrayList<ObjectInputStream> batchObjects = new ArrayList<ObjectInputStream>();
			int batchSize = 1;
			if (!config.contains("train")) {
				for (int i = 0; i < fileTypes.length; i++) {
					if (fileTypes[i].getType() == FileType.TYPE.TRAIN) {
						batchIndices.add(i);
						ObjectInputStream ois = new ObjectInputStream(new FileInputStream((File)((Vector)fileObjects[i]).get(0)));
						batchObjects.add(ois);
						int numIterations = ((Integer)((Vector)fileObjects[i]).get(1)).intValue();
						if (debug) Debug.debug(debug, "Batch file has "+numIterations+" members");
						if (batchSize == 1)
							batchSize = numIterations;
						else if (batchSize != numIterations)
							throw new DataFormatException("Mismatched batch files: read "+batchSize+" previously but now have "+numIterations);
					}
				}
			}
			
			// batch output gets put in one file per item if files are specified
			if (outfile != null) {
				if (batchSize == 1)
					w = new OutputStreamWriter(new FileOutputStream(outfile), encoding);
			}
			else if (w == null)
				w = new OutputStreamWriter(System.out, encoding);
			
			for (int itNum = 0; itNum < batchSize; itNum++) {
				if (debug) Debug.debug(debug, "Batch item "+itNum+" of "+batchSize);
				StringBuffer outputBuffer = new StringBuffer(initOutputBuffer);
				// open outstream for batch if needed
				if (outfile != null && batchSize > 1) {
					w = new OutputStreamWriter(new FileOutputStream(outfile.getAbsolutePath()+"."+itNum), encoding);
				}
				for (int i = 0; i < batchObjects.size(); i++) {
					int replaceItem = batchIndices.get(i);
					ObjectInputStream ois = batchObjects.get(i);
					Item item = (Item)ois.readObject();
					if (debug) Debug.debug(debug, "Batch item "+itNum+"; putting "+item+" into slot "+replaceItem);
					fileObjects[replaceItem] = item;
				}
//				// memory check at beginning: 
//				System.gc(); System.gc(); System.gc(); System.gc();
//				long mem0= Runtime.getRuntime().totalMemory() -
//			      Runtime.getRuntime().freeMemory();
//				long mema = mem0;
//				Debug.prettyDebug("Before processing: "+mem0+" used");
				
				
				// replace member of fileObjects with read in object
//				SymbolFactory.resetSTP();
				FileType.TYPE currtype = combineAutomata(trsarr, rsarr, treearr, fileTypes, fileObjects, config, timeLevel, outputBuffer, ts);			
				//FileType.TYPE currtype = vectorCombineAutomata(trsarr, rsarr, treearr, fileTypes, fileObjects, config, timeLevel, outputBuffer, ts);

				rs1 = rsarr[0];
				trs1 = trsarr[0];
				tree1 = treearr[0];
				Date postCombineTime = new Date();
				Debug.dbtime(timeLevel, 1, preCombineTime, postCombineTime,  "combine automata");

				if (debug) {
					if (rs1 == null) Debug.debug(debug, "rs1 is null");
					if (trs1 == null) Debug.debug(debug, "trs1 is null");
					if (tree1 == null) Debug.debug(debug, "tree1 is null");
					else Debug.debug(debug, "tree1 is "+treearr[0]);
				}

//				System.gc(); System.gc(); System.gc(); System.gc();
//				long memb= Runtime.getRuntime().totalMemory() -
//			      Runtime.getRuntime().freeMemory();
//				Debug.prettyDebug("Combining: "+(memb-mema)+" delta");
//				mema=memb;
				Date preXFormTime = new Date();
				FileType.TYPE finaltype = convertAutomata(xformtype, currtype, trsarr, rsarr, treearr, config, timeLevel, outputBuffer);
				rs1 = rsarr[0];
				trs1 = trsarr[0];
				tree1 = treearr[0];
				Date postXFormTime = new Date();
				Debug.dbtime(timeLevel, 1, preXFormTime, postXFormTime,  "convert automata");
				if (debug) {
					if (rs1 == null) Debug.debug(debug, "rs1 is null");
					if (trs1 == null) Debug.debug(debug, "trs1 is null");
					if (tree1 == null) Debug.debug(debug, "tree1 is null");
				}
//				System.gc(); System.gc(); System.gc(); System.gc();
//				memb= Runtime.getRuntime().totalMemory() -
//			      Runtime.getRuntime().freeMemory();
//				Debug.prettyDebug("Converting: "+(memb-mema)+" delta");
//				mema=memb;
				
				// weight setting: randomization and normalization
				// if appropriate, randomize weights
				if (config.getBoolean("random")) {
					if (debug) Debug.debug(debug, "randomizing rule weights");
					if (rs1 != null)
						rs1.randomizeRuleWeights();
					if (trs1 != null)
						trs1.randomizeRuleWeights();
				}
				
				// normalization for real semiring
				// done if specified or if training and not unspecified
				if((ts instanceof RealSemiring && config.getBoolean("norm")) ||
						(config.contains("train") && !config.getBoolean("nonorm"))) {
					if (debug) Debug.debug(debug, "normalizing rule weights");
					Date preNormalizeTime = new Date();
					if (rs1 != null)
						rs1.normalizeWeights();
					if (trs1 != null)
						trs1.normalizeWeights();
					Date postNormalizeTime = new Date();
					Debug.dbtime(timeLevel, 1, preNormalizeTime, postNormalizeTime, "normalize weights");
				}

				
				
				// rtg-only things (formerly in "prepare for output")
				// normal form
				if (rs1 != null && rs1 instanceof RTGRuleSet) {
					if (config.getBoolean("normform"))  {

						Date preNormTime = new Date();
						if (debug) Debug.debug(debug, "making normal");
						//		rs1.makeEfficientNormal();
						((RTGRuleSet)rs1).makeNormal();
						Date postNormTime = new Date();
						int printLevel = (config.contains("determ") || config.contains("prune") ? 2 : 1);
						Debug.dbtime(timeLevel, printLevel, preNormTime, postNormTime, "make normal");

					}
					
					// determinization. remove epsilon rules first

					if (config.contains("determ")) {
						Date preNormTime = new Date();
						if (debug) Debug.debug(debug, "making normal");
						//		rs1.makeEfficientNormal();
						((RTGRuleSet)rs1).makeNormal();
						Date postNormTime = new Date();
						int printLevel = (config.contains("determ") || config.contains("prune") ? 2 : 1);
						Debug.dbtime(timeLevel, printLevel, preNormTime, postNormTime, "make normal");
						// add normal form rtg to check info
						if (config.getBoolean("check"))
							getRuleSetCheck(outputBuffer, "after normal form",((RTGRuleSet)rs1));
						Date preDetermTime = new Date();
						((RTGRuleSet)rs1).removeEpsilons();
						Date postRemoveEpsilonTime = new Date();
						Debug.dbtime(timeLevel, 2, preDetermTime, postRemoveEpsilonTime, "remove epsilon rules from rule set");
						int minutes = config.getInt("determ", 1);
//						if (!rtg.isFinite(false))
//							throw new UnusualConditionException("Cannot determinize infinite grammar (yet).");
						if (!((RTGRuleSet)rs1).weightedDeterminize(minutes*60*1000, config.getBoolean("borchardt")))
							throw new UnusualConditionException("Couldn't determinize in "+minutes+" minutes. Try re-running with -d:x where x > "+minutes);
						Date postDetermTime = new Date();
						Debug.dbtime(timeLevel, 1, preDetermTime, postDetermTime, "determinize rule set (including remove epslion rules)");
						// add determinized rtg to check info
						if (config.getBoolean("check"))
							getRuleSetCheck(outputBuffer, "after determinization", ((RTGRuleSet)rs1));
					}
				}
				
				// remove epsilons here if specified
				if (config.getBoolean("rmepsilon")) {
					if (debug) Debug.debug(debug, "removing epsilons");
					if (rs1 != null)
						rs1.removeEpsilons();
				}
//				System.gc(); System.gc(); System.gc(); System.gc();
//				memb= Runtime.getRuntime().totalMemory() -
//			      Runtime.getRuntime().freeMemory();
//				Debug.prettyDebug("Rand/norm: "+(memb-mema)+" delta");
//				mema=memb;
				// interaction between examples (trees, strings, pairs): either training or some sort of application


				// training case
				// TODO: should be able to unify transducer training with grammar training!

				if (config.contains("train")) {
					// number of iterations
					if (debug) Debug.debug(debug, "Beginning training");
					int maxiter = config.getInt("train");
					// training items
					Vector trainingSet = (Vector)fileObjects[0];
					int trainingSetSize = ((Integer)trainingSet.get(1)).intValue();
					File trainingSetFile = (File)trainingSet.get(0);


					// derivation of each training item - written to file. If skipped, simply read from file
					String derivloc = null;
					if (config.contains("trainderivloc")) {
						derivloc = config.getString("trainderivloc");
						File test = new File(derivloc);
						if (test.exists() && !config.getBoolean("noderiv") && !config.getBoolean("overwrite"))
							throw new ConfigureException("Tried to overwrite derivation file "+derivloc+"; use --overwrite to allow");
					}

					// derivations is a File of DRS corresponding to the trainingSetFile
					File derivationsFile = null;
					if (config.getBoolean("noderiv")) {
						if (!config.contains("trainderivloc"))
							throw new ConfigureException("Cannot use --noderiv flag without --training-deriv-location");
						derivationsFile = new File(derivloc);
					}

					if (rs1 == null && trs1 != null) {
						if (debug) Debug.debug(debug, "Training a transducer");		    
						boolean isTree = fileTypes[0].getTrainType().isSecondTree();
						if (derivationsFile == null) {
							Date preDerivTime = new Date();
							derivationsFile = TransducerTraining.getAllDerivationRuleSets(trs1, trainingSetSize, 
									trainingSetFile, derivloc, 
									isTree, timeLevel);
							Date postDerivTime = new Date();
							Debug.dbtime(timeLevel, 1, preDerivTime, postDerivTime, "built derivation set for training");
						}

						Date preTrainTime = new Date();
						trs1 = TransducerTraining.train(trs1, trainingSetFile, 
								trainingSetSize, derivationsFile, 
								0, maxiter, !config.getBoolean("nonorm"));
						Date postTrainTime = new Date();
						Debug.dbtime(timeLevel, 1, preTrainTime, postTrainTime, "performed training");

						Date prePruneUselessTime = new Date();
						trs1.pruneUseless();
						Date postPruneUselessTime = new Date();
						Debug.dbtime(timeLevel, 1, prePruneUselessTime, postPruneUselessTime, "pruned useless after training");
					}
					else if (trs1 == null && rs1 != null) {
						if (debug) Debug.debug(debug, "Training a grammar");		    

						if (derivationsFile == null) {
							Date preDerivTime = new Date();
							if (rs1 instanceof RTGRuleSet)
								derivationsFile = RuleSetTraining.getAllDerivationRuleSets(rs1, trainingSetSize, 
										trainingSetFile, derivloc, 
										timeLevel);
							else {
								if (!((CFGRuleSet)rs1).isFinite(true))
									throw new DataFormatException("Can't (yet) train on a CFG with monadic chains");
								derivationsFile = CFGTraining.getAllDerivationRuleSets((CFGRuleSet)rs1, trainingSetSize, 
										trainingSetFile, derivloc, 
										timeLevel);
							}
							Date postDerivTime = new Date();
							Debug.dbtime(timeLevel, 1, preDerivTime, postDerivTime, "built derivation set for training");
						}

						Date preTrainTime = new Date();
						if (rs1 instanceof RTGRuleSet)
							rs1 = RuleSetTraining.train(rs1, trainingSetFile, 
									trainingSetSize, derivationsFile, 
									0, maxiter, timeLevel, !config.getBoolean("nonorm"));
						else
							rs1 = CFGTraining.train((CFGRuleSet)rs1, trainingSetFile, 
									trainingSetSize, derivationsFile, 
									0, maxiter, timeLevel, !config.getBoolean("nonorm"));
						Date postTrainTime = new Date();
						Debug.dbtime(timeLevel, 1, preTrainTime, postTrainTime, "performed training");

						Date prePruneUselessTime = new Date();
						rs1.pruneUseless();
						Date postPruneUselessTime = new Date();
						Debug.dbtime(timeLevel, 1, prePruneUselessTime, postPruneUselessTime, "pruned useless after training");

					}
					else if (trs1 == null && rs1 == null)
						throw new UnusualConditionException("Attempting to train, but can't find non-null transducer or grammar");
					else
						throw new UnusualConditionException("Programming error: should not have both a transducer and grammar in memory");


					// for each training example, get a DRS, get however many alignments are needed, and write to a single file
					if (config.contains("align")) {
						ObjectInputStream drsois = new ObjectInputStream(new FileInputStream(derivationsFile));
						for (int i = 0; i < trainingSetSize; i++) {
							DerivationRuleSet drs =	(DerivationRuleSet)drsois.readObject();
							if (drs == null) {
								continue;
							}
							drs.revive(trs1, ts);
							drs.pruneUselessAndZero();
							generateOutput(drs, config, w, timeLevel, outputBuffer);
						}
					}
				}
//				System.gc(); System.gc(); System.gc(); System.gc();
//				memb= Runtime.getRuntime().totalMemory() -
//			      Runtime.getRuntime().freeMemory();
//				Debug.prettyDebug("Train: "+(memb-mema)+" delta");
//				mema=memb;
				if (debug) Debug.debug(debug, "About to generate output");
				// make output
				// we have a rule set
				if (rs1 != null && trs1 == null && tree1 == null) {
					if (rs1 instanceof RTGRuleSet)
						prepareRTGForOutput((RTGRuleSet)rs1, config, timeLevel, outputBuffer);
					generateOutput(rs1, config, finaltype,  w, timeLevel, outputBuffer);
				}
				// we have a transducer, but we may have to convert it to a rule set or drs if we have left and right
				// forward and backward application now handled through automata combination
				// TODO: what about derivation rtgs? this code is commented below for posterity
				else if (trs1 != null && rs1 == null && tree1 == null) {
					generateOutput(trs1, config, w, timeLevel, outputBuffer);
				}
				// we have just a tree. print it.
				else if (trs1 == null && rs1 == null && tree1 !=null) {
					generateOutput(tree1, config, w, timeLevel, outputBuffer);
				}
				else if (trs1 == null && rs1 == null && tree1 == null)
					throw new UnusualConditionException("We have neither a transducer, grammar, nor tree");
				else
					throw new UnusualConditionException("We have more than one of a transducer, grammar, and tree");
				// only close if we're in batch mode and notwriting to stdout
				if (batchSize > 1 && outfile != null)
					w.close();
				
				
				// memory check at end: usage from after last batch item read in to now should be
				// unchanged except for permanent symbol construction
				rs1 =null;
				trs1 = null;
				tree1 = null;
//				System.gc(); System.gc(); System.gc(); System.gc();
//				System.gc(); System.gc(); System.gc(); System.gc();
//				memb= Runtime.getRuntime().totalMemory() -
//			      Runtime.getRuntime().freeMemory();
//				Debug.prettyDebug("Output: "+(memb-mema)+" delta");
//				mema=memb;
//				System.gc(); System.gc(); System.gc(); System.gc();
//				long mem1 = Runtime.getRuntime().totalMemory() -
//			      Runtime.getRuntime().freeMemory();
//				Debug.prettyDebug("After output: "+mem1+" used; delta of "+(mem1-mem0));
			}
			// now we can definitely close unless we just did
			if (batchSize == 1 || outfile == null)
				w.close();
			
			// TODO: put cross entropy for batch back!
//			if (rs1 != null && trs1 == null) {
//				double crossEnt;
//				double corpusprob;
//				if (ts.ZERO() < ts.ONE())
//					corpusprob = -Math.log(probCount);
//				else
//					corpusprob = probCount;
//				crossEnt = corpusprob/allNodeCount;
//				Debug.prettyDebug("Cross-Entropy of "+crossEnt+"; corpus probability is e^"+(-corpusprob)+"\n");
//			}
//			Date endBatchTime = new Date();
//			Debug.dbtime(timeLevel, 1, startBatchTime, endBatchTime, "completed all batch operations");
		}

		catch (FileNotFoundException e) {
			System.err.println("Input file not found: "+e.getMessage());
			System.exit(1);
		}
		catch (DataFormatException e) {
			System.err.println("Improper data specified: "+e.getMessage());
			System.exit(1);
		}
		catch (ImproperConversionException e) {
			System.err.println("Improper conversion: "+e.getMessage());
			System.exit(1);

		}
		catch (OutOfMemoryError e) {
			Runtime runtime = Runtime.getRuntime();
			System.err.println("Out of memory with "+runtime.freeMemory()+" left: "+e.getMessage());
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
