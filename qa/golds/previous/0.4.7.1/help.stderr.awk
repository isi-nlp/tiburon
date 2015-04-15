# this is an awk script to verify the lines of a file. If a line is
#  incorrect its number is printed, and if the wrong number of lines
# is found this too is printed
NR==1 && $0 !~ /^This is Tiburon, version [0-9.]+/ {print NR}
NR==2 && $0 != "Usage: tiburon " {print NR}
NR==3 && $0 != "             [-h|--help] [-s|--stdin] (-e|--encoding) <encoding> (-m|--semiring) <srtype> [(-l|--leftapply)[:<leftapply>]] [(-r|--rightapply)[:<rightapply>]] [-b|--batch] [--print-alignments] [-n|--normalizeweight] [--removeloops] [--normform] [(-p|--prune) <prune>] [(-d|--determinize) <determ>] [(-t|--train) <train>] [--training-deriv-location <trainderivloc>] [--conditional] [--no-deriv] [--randomize] [--union] [--timedebug <time>] [-y|--print-yields] [(-k|--kbest) <kbest>] [(-g|--generate) <krandom>] [-c|--check] [(-o|--outputfile) <outfile>] infiles1 infiles2 ... infilesN" {print NR}
NR==4 && $0 != "" {print NR}
NR==5 && $0 != "  [-h|--help]" {print NR}
NR==6 && $0 != "        print this help message" {print NR}
NR==7 && $0 != "" {print NR}
NR==8 && $0 != "  [-s|--stdin]" {print NR}
NR==9 && $0 != "        application (as signaled with -l or -r) comes from standard in, not a" {print NR}
NR==10 && $0 != "        file. This option is ignored unless -l or -r is used. If both -l and -r" {print NR}
NR==11 && $0 != "        are used, it is assumed that one file is provided and one string is" {print NR}
NR==12 && $0 != "        passed through stdin." {print NR}
NR==13 && $0 != "" {print NR}
NR==14 && $0 != "  (-e|--encoding) <encoding>" {print NR}
NR==15 && $0 != "        encoding of input and output files, if other than utf-8. Use the same" {print NR}
NR==16 && $0 != "        naming you would use if specifying this charset in a java program" {print NR}
NR==17 && $0 != "        (default: utf-8)" {print NR}
NR==18 && $0 != "" {print NR}
NR==19 && $0 != "  (-m|--semiring) <srtype>" {print NR}
NR==20 && $0 != "        type of weights: can be real (probabilities), truereal (probabilities" {print NR}
NR==21 && $0 != "        with underflow), tropical (max log weights), or tropicalaccumulative" {print NR}
NR==22 && $0 != "        (summed log weights) (default: real)" {print NR}
NR==23 && $0 != "" {print NR}
NR==24 && $0 != "  [(-l|--leftapply)[:<leftapply>]]" {print NR}
NR==25 && $0 != "        perform forward application of tree <leftapply> onto transducer input to" {print NR}
NR==26 && $0 != "        form an rtg. cannot be used with -t. When used with -r, the result is a" {print NR}
NR==27 && $0 != "        derivation rtg." {print NR}
NR==28 && $0 != "" {print NR}
NR==29 && $0 != "  [(-r|--rightapply)[:<rightapply>]]" {print NR}
NR==30 && $0 != "        perform backward application of tree or string <rightapply> onto" {print NR}
NR==31 && $0 != "        transducer input to form an rtg <DISABLED>. cannot be used with -t. When" {print NR}
NR==32 && $0 != "        used with -l, the result is a derivation rtg. This is currently the only" {print NR}
NR==33 && $0 != "        supported option" {print NR}
NR==34 && $0 != "" {print NR}
NR==35 && $0 != "  [-b|--batch]" {print NR}
NR==36 && $0 != "        batch mode (currently for RTG only): first file is assumed to be a" {print NR}
NR==37 && $0 != "        sequence of newline-separated trees; subsequent files are RTGs that are" {print NR}
NR==38 && $0 != "        intersected. For each tree in the batch file, intersection is performed" {print NR}
NR==39 && $0 != "        and whatever operation is specified is performed." {print NR}
NR==40 && $0 != "" {print NR}
NR==41 && $0 != "  [--print-alignments]" {print NR}
NR==42 && $0 != "        given sentence pairs and a trained transducer, return the viterbi" {print NR}
NR==43 && $0 != "        alignments." {print NR}
NR==44 && $0 != "" {print NR}
NR==45 && $0 != "  [-n|--normalizeweight]" {print NR}
NR==46 && $0 != "        normalize weights of rtg. This option is only relevant to the real" {print NR}
NR==47 && $0 != "        semiring. It cannot be used with -t" {print NR}
NR==48 && $0 != "" {print NR}
NR==49 && $0 != "  [--removeloops]" {print NR}
NR==50 && $0 != "        remove rules from the rtg that allow an infinite language. can't be used" {print NR}
NR==51 && $0 != "        with -t" {print NR}
NR==52 && $0 != "" {print NR}
NR==53 && $0 != "  [--normform]" {print NR}
NR==54 && $0 != "        convert rtg to chomsky normal form. can't be used with -t" {print NR}
NR==55 && $0 != "" {print NR}
NR==56 && $0 != "  [(-p|--prune) <prune>]" {print NR}
NR==57 && $0 != "        Prune rules that must exist in a tree with score <prune> greater than" {print NR}
NR==58 && $0 != "        the score of the best tree. This is currently implemented for log-based" {print NR}
NR==59 && $0 != "        semirings only (i.e. tropical and tropicalaccumulative). Pruning occurs" {print NR}
NR==60 && $0 != "        before determinization (-d) when both options are included. It cannot be" {print NR}
NR==61 && $0 != "        used with -t." {print NR}
NR==62 && $0 != "" {print NR}
NR==63 && $0 != "  [(-d|--determinize) <determ>]" {print NR}
NR==64 && $0 != "        Determinize the input rtg for <determ> minutes. Determinization requires" {print NR}
NR==65 && $0 != "        normal form and no loops, so --removeloops and --normform do not need to" {print NR}
NR==66 && $0 != "        be specified. (NOTE: A bug in JSAP prevents this from being a qualified" {print NR}
NR==67 && $0 != "        switch). It cannot be used with -t." {print NR}
NR==68 && $0 != "" {print NR}
NR==69 && $0 != "  [(-t|--train) <train>]" {print NR}
NR==70 && $0 != "        perform EM training of an xR or xRs transducer given input, output" {print NR}
NR==71 && $0 != "        training pairs, or a grammar given trees. Train for up to <train>" {print NR}
NR==72 && $0 != "        iterations. The only acceptable output is the weighted transducer or" {print NR}
NR==73 && $0 != "        weighted grammar , thus -k and -g and -c options aren't valid. -p, -d," {print NR}
NR==74 && $0 != "        --normform, --removeloops, -n, -l, -s are similarly disallowed. There" {print NR}
NR==75 && $0 != "        must be at least 2 files in the input list - the first is assumed to be" {print NR}
NR==76 && $0 != "        a set of training pairs or triples. The remaining are the transducer(s)" {print NR}
NR==77 && $0 != "        or grammar(s). Note that in principle multiple grammars or transducers" {print NR}
NR==78 && $0 != "        could be here, but for now only one is allowed when training" {print NR}
NR==79 && $0 != "" {print NR}
NR==80 && $0 != "  [--training-deriv-location <trainderivloc>]" {print NR}
NR==81 && $0 != "        Only valid if -t is used. <trainderivloc> is the file to hold binary" {print NR}
NR==82 && $0 != "        representation of the calculated derivation forests. if not specified," {print NR}
NR==83 && $0 != "        they are written to and read from a temporary file and deleted" {print NR}
NR==84 && $0 != "        afterward." {print NR}
NR==85 && $0 != "" {print NR}
NR==86 && $0 != "  [--conditional]" {print NR}
NR==87 && $0 != "        Train transducers conditionally; i.e. all rules with the same LHS have" {print NR}
NR==88 && $0 != "        probability summing to 1. If not set, training is joint; i.e. all rules" {print NR}
NR==89 && $0 != "        with the same LHS root have probability summing to 1. Eventually this" {print NR}
NR==90 && $0 != "        will be for normalization as well." {print NR}
NR==91 && $0 != "" {print NR}
NR==92 && $0 != "  [--no-deriv]" {print NR}
NR==93 && $0 != "        Only valid if -t is used. If present, do not calculate derivation" {print NR}
NR==94 && $0 != "        forests. Instead, assume they have already been built" {print NR}
NR==95 && $0 != "        (training-deriv-location flag must also be used). Derivation forests are" {print NR}
NR==96 && $0 != "        typically time consuming, but once built don't change." {print NR}
NR==97 && $0 != "" {print NR}
NR==98 && $0 != "  [--randomize]" {print NR}
NR==99 && $0 != "        Randomize the weights of the input structure (grammar or transducer) to" {print NR}
NR==100 && $0 != "        be equivalent to a probability between 0.2 and 0.8. This is mostly" {print NR}
NR==101 && $0 != "        useful for EM training." {print NR}
NR==102 && $0 != "" {print NR}
NR==103 && $0 != "  [--union]" {print NR}
NR==104 && $0 != "        assume input is a single batch file of (weighted) trees output is union" {print NR}
NR==105 && $0 != "        of derivative rtgs." {print NR}
NR==106 && $0 != "" {print NR}
NR==107 && $0 != "  [--timedebug <time>]" {print NR}
NR==108 && $0 != "        Print timing information to stderr at a variety of levels: 0+ for total" {print NR}
NR==109 && $0 != "        operation, 1+ for each processing stage, 2+ for small info" {print NR}
NR==110 && $0 != "" {print NR}
NR==111 && $0 != "  [-y|--print-yields]" {print NR}
NR==112 && $0 != "        print yield of trees instead of trees. no meaning unless -g or -k is" {print NR}
NR==113 && $0 != "        used." {print NR}
NR==114 && $0 != "" {print NR}
NR==115 && $0 != "  [(-k|--kbest) <kbest>]" {print NR}
NR==116 && $0 != "        return the <kbest> highest ranked trees. This option cannot be used with" {print NR}
NR==117 && $0 != "        -g or -c or -t" {print NR}
NR==118 && $0 != "" {print NR}
NR==119 && $0 != "  [(-g|--generate) <krandom>]" {print NR}
NR==120 && $0 != "        generate <krandom> trees stochastically. This option cannot be used with" {print NR}
NR==121 && $0 != "        -k or -c or -t" {print NR}
NR==122 && $0 != "" {print NR}
NR==123 && $0 != "  [-c|--check]" {print NR}
NR==124 && $0 != "        check the number of rules, states, and derivations of the rtg or" {print NR}
NR==125 && $0 != "        transducer " {print NR}
NR==126 && $0 != "" {print NR}
NR==127 && $0 != "  [(-o|--outputfile) <outfile>]" {print NR}
NR==128 && $0 != "        file to write output grammar or tree list or summary. If absent, writing" {print NR}
NR==129 && $0 != "        is done to stdout" {print NR}
NR==130 && $0 != "" {print NR}
NR==131 && $0 != "  infiles1 infiles2 ... infilesN" {print NR}
NR==132 && $0 != "        list of input files. either a set of rtgs, a set of xR transducers, a" {print NR}
NR==133 && $0 != "        single xRs transducer, or, if training, a training file followed by" {print NR}
NR==134 && $0 != "        either of the transducer options. " {print NR}
NR==135 && $0 != "" {print NR}
{records++}
END{if (records != 135) {printf("%s lines\n", records)}}
