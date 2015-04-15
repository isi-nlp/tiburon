#!/usr/bin/env bash
### Occasionally use #!/bin/bash -u
# battery of tests for tiburon

progname=$(basename $0)

LANG=C
OS=`uname -a | awk '{print $1}'`;

print_help()
{
    cat <<'EOF'
Usage: run-tests [options]

run tiburon tests

Options:
      --help              print this help message
      --tib-path=path     path to tiburon executable. Default is $PWD
      --script-path=path  path to gold verification scripts. Default is $PWD/qa/scripts
      --infile-path=path  location of various files needed to test tiburon. Default is $PWD/qa/input
      --gold-path=path    location of various comparison files. Default is $PWD/qa/golds
      --temp-path=path    location of temporary directory
      --start=num         start with test num
      --stop=num          stop with test num
      --continue          continue on failed test
      --norun             display command only; don't run test
      --verbose           turn on debugging and other messages for this script
EOF
}

verbose_on()
{
VERBOSE=1;
debug "Verbose mode on"
}

debug()
{
    if [ $VERBOSE -eq 1 ]; then
	echo "### $1";
    fi
}

set_continue()
{
    debug "Continuing on failed tests"
    CONTINUE=1;
}

set_norun()
{
    debug "Not running tests"
    NORUN=1;
}

# execute a test and possibly check results
run_test()
{
    debug "$# arguments";
    if [ $# -lt 1 ]; then
	echo "<SKIP>";
	return;
    fi
    # run this command
    local CMD=$1;
    # to files with this prefix
    local PREFIX=$2;
    # debug with this description
    local DESC=$3;
    debug "Test cmd is $CMD";
    debug "Prefix is $PREFIX";
    debug "Description is $DESC";
    if [ $# -gt 3 ]; then
	# script for comparing stdout file
	local CMPOUT_BIN=$4;
	# gold file to compare stdout file
	local CMPOUT_FILE=$5;
	# script for comparing stderr file
	local CMPERR_BIN=$6;
	# gold file to compare stderr file	
	local CMPERR_FILE=$7;
	debug "out cmp binary is $CMPOUT_BIN";
	debug "out gold is $CMPOUT_FILE";
	debug "err cmp binary is $CMPERR_BIN";
	debug "err gold is $CMPERR_FILE";    

    fi
    # if we need to test a specific file we need both the name of that file and
    # the gold file for it, as well as the binary, of course
    if [ $# -gt 7 ]; then
	local CMPFILE_BIN=$8;
	local CMPFILE_GEN=$9;
	local CMPFILE_GOLD=${10};
	debug "file cmp binary is $CMPFILE_BIN";
	debug "generated file is $CMPFILE_GEN";
	debug "file gold is $CMPFILE_GOLD";
    fi


    STDOUTFILE=$PREFIX.stdout;
    STDERRFILE=$PREFIX.stderr;
    debug "Running $DESC: $CMD > $STDOUTFILE 2> $STDERRFILE";
    printf "${DESC}...";
    if [ $NORUN = 1 ]; then
	echo "$CMD";
    else
	$CMD > $STDOUTFILE 2> $STDERRFILE;
	PASS=1;
	if [ $# -gt 3 ]; then
	    debug "Comparing stderr: $CMPERR_BIN $CMPERR_FILE $STDERRFILE";
	    if ! `$CMPERR_BIN $CMPERR_FILE $STDERRFILE`; then
		echo "FAILED: writing to stderr: compare $STDERRFILE with $CMPERR_FILE";
		echo "Command was $CMD > $STDOUTFILE 2> $STDERRFILE";
		PASS=0;
		if [ $CONTINUE = 0 ]; then
		    if [ $OS = "Darwin" ]; then
			say "Failure at error if $DESC";
		    fi
		    exit 1;
		fi
	    else
		debug "$DESC successful for stderr";
	    fi
	    debug "Comparing stdout: $CMPOUT_BIN $CMPOUT_FILE $STDOUTFILE";
	    if ! `$CMPOUT_BIN $CMPOUT_FILE $STDOUTFILE`; then
		echo "FAILED: writing to stdout: compare $STDOUTFILE with $CMPOUT_FILE";
		echo "Command was $CMD > $STDOUTFILE 2> $STDERRFILE";
		PASS=0;
		if [ $CONTINUE = 0 ]; then
		    if [ $OS = "Darwin" ]; then
			say "Failure at output of $DESC";
		    fi
		    exit 1;
		fi
	    else
		debug "$DESC successful for stdout";
	    fi
	    if [ $# -gt 7 ]; then
		debug "Comparing generated file: $CMPFILE_BIN $CMPFILE_GOLD $CMPFILE_GEN";
		if [ ! -e $CMPFILE_GEN ]; then
		    echo "FAILED: generated file $CMPFILE_GEN not found";
		    echo "Command was $CMD > $STDOUTFILE 2> $STDERRFILE";
		    PASS=0;
		    if [ $CONTINUE = 0 ]; then
			if [ $OS = "Darwin" ]; then
			    say "Failure at external output of $DESC";
			fi
			exit 1;
		    fi
		fi
		if ! `$CMPFILE_BIN $CMPFILE_GOLD $CMPFILE_GEN`; then
		    echo "FAILED: writing to generated file: compare $CMPFILE_GEN with $CMPFILE_GOLD";
		    echo "Command was $CMD > $STDOUTFILE 2> $STDERRFILE";
		    PASS=0;
		    if [ $CONTINUE = 0 ]; then
			if [ $OS = "Darwin" ]; then
			    say "Failure at external output of $DESC";
			fi
			exit 1;
		    fi
		else
		    debug "$DESC successful for generated file $CMPFILE_GEN";
		fi
	    else
		debug "$DESC successful for stderr";
	    fi
	    if [ $PASS = 1 ]; then
		echo "PASSED";
	    fi
	else
	    echo "NOT VERIFIED";
	fi
    fi
}


# default values
VERBOSE=0;
TIBPATH=$PWD;
INPATH=$PWD/qa/input;
TMPPATH="none";
GOLDPATH=$PWD/qa/golds;
SCRIPTPATH=$PWD/qa/scripts;
DELETETMP=0;
START=1;
STOP="end";
CONTINUE=0;
NORUN=0;
LONGOPTS="help,continue,norun,tib-path:,start:,stop:,script-path:,gold-path:,temp-path:,infile-path:,verbose";


if $(getopt -T >/dev/null 2>&1) ; [ $? = 4 ] ; then # New longopts getopt.
    OPTS=$(getopt --longoptions $LONGOPTS -o "x" -n "$progname" -- "$@")
else
    echo "need a better version of getopt";
    exit 1
fi

if [ $? -ne 0 ]; then
    echo "'$progname --help' for more information" 1>&2
    exit 1
fi 
eval set -- "$OPTS" 
while [ $# -gt 0 ]; do 
     case $1 in
        --help)
            print_help
            exit 0
            ;; 
	 --continue)
            set_continue
            shift 1
            ;; 
	 --norun)
            set_norun
            shift 1
            ;; 
	 --tib-path)
            TIBPATH=$2
            shift 2
            ;; 
	 --script-path)
            SCRIPTPATH=$2
            shift 2
            ;; 
	 --start)
            START=$2
            shift 2
            ;; 
	 --stop)
            STOP=$2
            shift 2
            ;; 
	 --temp-path)
            TMPPATH=$2
            shift 2
            ;; 
	 --gold-path)
            GOLDPATH=$2
            shift 2
            ;; 
	 --infile-path)
            INPATH=$2
            shift 2
            ;; 
	 --verbose)
            verbose_on
            shift 1
            ;; 
	 --)
	     shift
	     break
	     ;;
	 *)
	     echo "Internal Error: option processing error: $1" 1>&2
	     exit 1
	     ;;
     esac
done 


debug
debug "Tiburon path is $TIBPATH";
debug "Infile path is $INPATH";
debug "Gold path is $GOLDPATH";
debug "Script path is $SCRIPTPATH";
debug "Starting at test $START";
debug "Ending at test $STOP";

if [ $NORUN -eq 0 ]; then
    if [ $TMPPATH = "none" ]; then
	OUTDIR=`mktemp -dt tiburon.XXXXXX`;
	DELETETMP=1;
    else
	if [ -e $TMPPATH -a -d $TMPPATH ]; then
	    OUTDIR=`mktemp -d $TMPPATH/tiburon.XXXXXX`;
	    DELETETMP=0;
	else
	    echo "$TMPPATH does not exist";
	    exit 1;
	fi
    fi
    if [ $DELETETMP -eq 1 ]; then
	debug "Files will be written to $OUTDIR and then deleted";
    else
	echo "Files will be written to $OUTDIR and not deleted";
    fi
else
    OUTDIR="blah";
fi

if [ -e $TIBPATH -a -d $INPATH ]; then
    TIB=$TIBPATH/tiburon;
else
    TIB=tiburon;
fi

# unweighted finite rtg
UFRTG=$INPATH/unweighted_finite.rtg
# weighted infinite rtgs: even and three
RTG_EVEN=$INPATH/weighted_infinite_even.rtg
RTG_THREE=$INPATH/weighted_infinite_three.rtg
# weighted rtg suitable for training without immediate convergence
WRTG_TRAIN=$INPATH/weighted_trainable.rtg
# and accompanying training set
WRTG_TREES=$INPATH/weighted_trainable.trees
# subset used for batch-mode cross-entropy
WRTG_BATCH_TREES=$INPATH/weighted_crossent.trees
# xr transducer, suitable for training (from Alexander Radzievskiy)
XR_TRAIN=$INPATH/unweighted_trainable.xr
# training set for xr transducer
XR_TREES=$INPATH/trainable.ttp
# xrs transducer, japanese string, suitable for training
# has ties
XRS_TIED_EUCJP_TRAIN=$INPATH/unweighted_tied_eucjp_trainable.xrs
# trained version of the above for -k constant behavior
XRS_TIED_EUCJP_TRAINED=$INPATH/weighted_tied_eucjp_trainable.xrs
# training set for xrs transducer
XRS_TSP=$INPATH/eucjp_trainable.tsp
# tree for left-application (as well as both-application) on xr transducer
XR_LEFT_TREE=$INPATH/xr_applicable.tree
# batch of trees for left-application on xr transducer
XR_LEFT_TREE_BATCH=$INPATH/xr_applicable.tree.batch
# batch of trees for right-application on xr transducer
XR_RIGHT_TREE_BATCH=$INPATH/xr_applicable.right.tree.batch
# tree pair for both-application on xr transducer
XR_BOTH_TREE=$INPATH/xr_applicable.twoside
# tree for left-application on xrs transducer
XRS_LEFT_TREE=$INPATH/xrs_applicable.tree
# 5 trees for batch left-application on xrs transducer
XRS_LEFT_TREE_BATCH=$INPATH/xrs_applicable.tree.batch
# string for both-application on xrs transducer
XRS_RIGHT_STRING=$INPATH/xrs_applicable.string
# 5 strings for batch both-application on xrs transducer
XRS_RIGHT_STRING_BATCH=$INPATH/xrs_applicable.string.batch
XRS_BOTH_PAIR=$INPATH/xrs_applicable.both
# cfg, suitable for training
CFG_TRAIN=$INPATH/trainable.cfg
# training strings for the cfg
CFG_STRINGS=$INPATH/trainable.strings
# corner case rtg
RTG_TOUGH=$INPATH/tough.rtg
# corner case cfg
CFG_TOUGH=$INPATH/tough.cfg
# corner case xr
XR_TOUGH=$INPATH/tough.xr
# corner case xrs
XRS_TOUGH=$INPATH/tough.xrs
# composable transducer pair
XR_COMP=$INPATH/comp1.xr
RLN_COMP=$INPATH/comp2.rln
# weighted cfg suitable for training without immediate convergence
WCFG_TRAIN=$INPATH/weighted_trainable.cfg
# weighted infinite even cfg
CFG_EVEN=$INPATH/weighted_infinite_even.cfg
# fun challenge for domain projection in xr and xrs
XR_DOM=$INPATH/domain.xr
XRS_DOM=$INPATH/domain.xrs
# tree to give to RTG_THREE or conversions of it
THREE_TREE=$INPATH/three.tree
# really big tree
TREE_LARGE=$INPATH/big.tree
# tree too big to read without adjustment
TREE_LARGER=$INPATH/bigger.tree
# same as TREE_LARGER with type check
TREE_LARGER_TC=$INPATH/bigger.tree.tc
# really small tree
TREE_SMALL=$INPATH/small.tree
#ambiguous RTG/CFG cast as RTG
TYPE_RTG=$INPATH/type.rtg
#ambiguous RTG/CFG cast as CFG
TYPE_CFG=$INPATH/type.cfg
#ambiguous RTG/CFG cast wrongly
TYPE_BAD=$INPATH/type.bad
# xrs conversion of an rtg suitable for parsing
PARSE_XRS=$INPATH/parse.xrs
# string for parsing PARSE_XRS
PARSE_STR=$INPATH/parse.string
# cfg for parsing
PARSE_CFG=$INPATH/parse.cfg
# string for parsing PARSE_CFG
PARSE_AB=$INPATH/ab.string
# ambiguous corpus cast as tree
AMBIG_TREE=$INPATH/ambig.tree
# ambiguous corpus cast as string
AMBIG_STRING=$INPATH/ambig.string
# ambiguous grammar cast as rtg for training
AMBIG_RTG=$INPATH/ambig.rtg
# ambiguous grammar cast as cfg for training
AMBIG_CFG=$INPATH/ambig.cfg
# corner case xrs for parsing
GO_XRS=$INPATH/go.xrs
# corner case string for parsing
GO_STRING=$INPATH/go.string


# gold files
G_EMPTY=$GOLDPATH/empty 
G_VERSION=$GOLDPATH/version 
G_BASE=$GOLDPATH/base.stderr 
G_HELP=$GOLDPATH/help.stderr.awk 
G_UFRTG_REAL=$GOLDPATH/ufrtg.real.awk 
G_UFRTG_TROP=$GOLDPATH/ufrtg.trop.awk 
G_UFRTG_CHECK=$GOLDPATH/ufrtg.check 
G_UFRTG_SMALLK=$GOLDPATH/ufrtg.top5.awk
G_UFRTG_BIGK=$GOLDPATH/ufrtg.top26 
G_KWARN=$GOLDPATH/topkwarn.awk 
G_RTG_SMALLG=$GOLDPATH/rtg.random5
G_UFRTG_DET=$GOLDPATH/ufrtg.det
G_UFRTG_DET_CHECK=$GOLDPATH/ufrtg.det.check
G_UFRTG_RANDOM_WEIGHTS=$GOLDPATH/ufrtg.random.awk
G_RTG_NORM=$GOLDPATH/rtg.norm.awk
G_WRTG_REAL=$GOLDPATH/wrtg.real
G_WRTG_CHECK=$GOLDPATH/wrtg.check
G_WRTG_BIGK=$GOLDPATH/wrtg.top100
G_WRTG_COMPOSE=$GOLDPATH/rtg.compose
G_WRTG_COMPOSE_SMALLK=$GOLDPATH/rtg.compose.top10
G_TRAINRTG_SMALLK_YIELD=$GOLDPATH/trainrtg.top10.yield
G_TRAINRTG_TRAIN_ERR=$GOLDPATH/train.stderr.awk
G_TRAINRTG_TRAIN_RESULT=$GOLDPATH/trainrtg.it10
G_TRAINRTG_BATCH_OUT=$GOLDPATH/batch.out
G_UXR_REAL=$GOLDPATH/uxr.real
G_UXRS_REAL=$GOLDPATH/uxrs.real
G_UXR_CHECK=$GOLDPATH/uxr.check
G_UXRS_CHECK=$GOLDPATH/uxrs.check
G_UXR_APPLY=$GOLDPATH/uxr.apply
G_UXRS_APPLY=$GOLDPATH/uxrs.apply
G_UXRS_BATCH_APPLY=$GOLDPATH/uxrs.batch.apply
G_UXRS_APPLY_SELECT=$GOLDPATH/uxrs.batch.apply.3.5k
G_UXR_BATCH_APPLY=$GOLDPATH/uxr.batch.apply
G_UXR_APPLY_ALLK=$GOLDPATH/uxr.apply.90k
G_UXR_BATCH_APPLY_5K=$GOLDPATH/uxr.batch.apply.5k
G_UXRS_APPLY_SMALLK=$GOLDPATH/uxrs.apply.5k
G_XRS_APPLY_SMALLK=$GOLDPATH/wxrs.apply.5k
G_UXR_APPLY_DET_NORM_SMALLK=$GOLDPATH/uxr.apply.det.norm.top9
G_UXR_BATCH_APPLY_DET_NORM_SMALLK=$GOLDPATH/uxr.batch.apply.det.norm.top9
G_UXR_BOTH_BATCH_APPLY_1K_LEFT=$GOLDPATH/uxr.both.batch.apply.left.top1
G_UXR_BOTH_BATCH_APPLY_1K_RIGHT=$GOLDPATH/uxr.both.batch.apply.right.top1
G_UXR_APPLY_DERIV=$GOLDPATH/uxr.deriv.apply
G_UXR_APPLY_DERIV_CHECK=$GOLDPATH/uxr.deriv.apply.check
G_UXRS_APPLY_DERIV=$GOLDPATH/uxrs.deriv.apply
G_UXRS_APPLY_DERIV_CHECK=$GOLDPATH/uxrs.deriv.apply.check
G_XR_TRAIN_OUT=$GOLDPATH/train.xr.it5
G_XR_TRAIN_ERR_WARN=$GOLDPATH/train.xr.it5.err.withwarnings
G_XR_TRAIN_ERR_NONORM_NOWARN=$GOLDPATH/train.xr.it5.err.nonorm.nowarnings
G_XR_TRAIN_ERR_NOWARN=$GOLDPATH/train.xr.it5.err.nowarnings
G_XRS_TRAIN_OUT=$GOLDPATH/train.xrs.it5
G_XRS_TRAIN_ERR=$GOLDPATH/train.xrs.it5.err
G_XRS_TRAIN_OUT_COND=$GOLDPATH/train.xrs.cond.it5
G_XRS_TRAIN_ERR_COND=$GOLDPATH/train.xrs.cond.it5.err
G_XRS_TRAIN_OUT_NONORM=$GOLDPATH/train.xrs.nonorm.it5
G_XRS_TRAIN_ERR_NONORM=$GOLDPATH/train.xrs.nonorm.it5.err
G_XRS_TRAIN_OUT_COND_TRUEREAL=$GOLDPATH/train.xrs.cond.tr.it5
G_XRS_TRAIN_ERR_COND_TRUEREAL=$GOLDPATH/train.xrs.cond.tr.it5.err
G_CFG_TRAIN_OUT=$GOLDPATH/train.cfg.it10
G_CFG_TRAIN_ERR=$GOLDPATH/train.cfg.it10.err
G_TOUGH_RTG=$GOLDPATH/tough.rtg
G_TOUGH_CFG=$GOLDPATH/tough.cfg
G_TOUGH_XR=$GOLDPATH/tough.xr
G_TOUGH_XRS=$GOLDPATH/tough.xrs
G_COMPOSE=$GOLDPATH/compose.xr
G_COMP_ERR=$GOLDPATH/compose.err
G_RTG_CFG=$GOLDPATH/wrtg.cfg
G_RTG_XR=$GOLDPATH/rtg.xr
G_RTG_XRS=$GOLDPATH/rtg.xrs
G_CFG_RTG=$GOLDPATH/cfg.rtg
G_TRAINCFG_RTG_SMALLK=$GOLDPATH/traincfg.rtg.top10
G_CFG_XR=$GOLDPATH/cfg.xr
G_CFG_XRS=$GOLDPATH/cfg.xrs
G_XR_RTG=$GOLDPATH/xr.rtg
G_XR_RTG_TOP5=$GOLDPATH/xr.rtg.top5
G_TREE_THREE_RTG=$GOLDPATH/tree_three.rtg
G_TREE_THREE_CFG=$GOLDPATH/tree_three.cfg
G_TREE_LARGE=$GOLDPATH/big.tree
G_TREE_LARGER=$GOLDPATH/bigger.tree
G_TREE_LARGER_ERR=$GOLDPATH/bigger.tree.err
G_TREE_LARGER_CHECK=$GOLDPATH/bigger.tree.stats
G_TREE_SMALL=$GOLDPATH/small.tree
G_TYPE_TOP4=$GOLDPATH/type.top4
G_TYPE_BAD=$GOLDPATH/type.bad.awk
G_LARGE_READ_ERR=$GOLDPATH/largeread.stderr
G_TST_LIMIT=$GOLDPATH/tstlimit
G_PARSE_20=$GOLDPATH/parse.golds
G_CFG_CHECK=$GOLDPATH/cfgparse.stats
G_AMBIG_TRAIN_OUT=$GOLDPATH/ambig.it2
G_AMBIG_TRAIN_ERR=$GOLDPATH/ambig.it2.err
G_GO_TREE=$GOLDPATH/go.tree

# evaluation scripts
EXACT=$SCRIPTPATH/exactmatch.sh
SORT=$SCRIPTPATH/sortmatch.sh
AWK=$SCRIPTPATH/awkmatch.sh
SORT_STATEANON=$SCRIPTPATH/sort_stateanon_match.sh
SCORETREE=$SCRIPTPATH/scoretreematch.sh
SCORE=$SCRIPTPATH/scorematch.sh

# check to make sure programs are here and working
for i in $TIB $EXACT $AWK $SORT $SORT_STATEANON $SCORETREE $SCORE; do
    if ! [ -e $i ]; then
	echo "# $i does not exist!";
	exit 1;
    else
	debug "Program $i exists";
    fi
    if ! [ -x $i ]; then
	echo "# $i is not executable!";
	exit 1;
    else
	debug "Program $i is executable";
    fi
done;

# check to make sure data files are here
for i in $UFRTG $RTG_EVEN $RTG_THREE $WRTG_TRAIN $WRTG_TREES $WRTG_BATCH_TREES \
    $XR_TRAIN $XR_TREES $XRS_TIED_EUCJP_TRAIN $XRS_TIED_EUCJP_TRAINED $XRS_TSP \
    $XR_LEFT_TREE $XR_LEFT_TREE_BATCH $XR_RIGHT_TREE_BATCH $XR_BOTH_TREE \
    $XRS_LEFT_TREE $XRS_RIGHT_STRING $XRS_LEFT_TREE_BATCH $XRS_RIGHT_STRING_BATCH  $XRS_BOTH_PAIR \
    $CFG_TRAIN $CFG_STRINGS $RTG_TOUGH $CFG_TOUGH $XR_TOUGH $XRS_TOUGH $XR_COMP $RLN_COMP \
    $WCFG_TRAIN $XR_DOM $XRS_DOM $THREE_TREE $TREE_LARGE $TREE_LARGER $TREE_LARGER_TC $TREE_SMALL \
    $TYPE_RTG $TYPE_CFG $TYPE_BAD $PARSE_XRS $PARSE_STR $PARSE_CFG $PARSE_AB \
    $AMBIG_TREE $AMBIG_STRING $AMBIG_RTG $AMBIG_CFG \
	$GO_XRS $GO_STRING \
    $G_EMPTY $G_VERSION $G_BASE $G_HELP \
    $G_UFRTG_REAL $G_UFRTG_TROP $G_UFRTG_CHECK \
    $G_UFRTG_SMALLK $G_UFRTG_BIGK $G_KWARN $G_RTG_SMALLG \
    $G_UFRTG_DET $G_UFRTG_DET_CHECK $G_UFRTG_RANDOM_WEIGHTS \
    $G_RTG_NORM $G_WRTG_REAL $G_WRTG_CHECK $G_WRTG_BIGK \
    $G_WRTG_COMPOSE $G_WRTG_COMPOSE_SMALLK $G_TRAINRTG_SMALLK_YIELD \
    $G_TRAINRTG_TRAIN_ERR $G_TRAINRTG_TRAIN_RESULT \
    $G_TRAINRTG_BATCH_OUT \
    $G_UXR_REAL $G_UXRS_REAL $G_UXR_CHECK $G_UXRS_CHECK \
    $G_UXR_APPLY $G_UXR_BATCH_APPLY $G_UXRS_APPLY $G_UXRS_BATCH_APPLY $G_UXRS_APPLY_SELECT \
    $G_UXR_APPLY_ALLK $G_UXRS_APPLY_SMALLK $G_XRS_APPLY_SMALLK \
    $G_UXR_APPLY_DET_NORM_SMALLK $G_UXR_BATCH_APPLY_DET_NORM_SMALLK $G_UXR_APPLY_DERIV $G_UXR_APPLY_DERIV_CHECK \
    $G_UXR_BOTH_BATCH_APPLY_1K_LEFT $G_UXR_BOTH_BATCH_APPLY_1K_RIGHT \
    $G_UXRS_APPLY_DERIV $G_UXRS_APPLY_DERIV_CHECK \
    $G_XR_TRAIN_OUT $G_XR_TRAIN_ERR_WARN $G_XR_TRAIN_ERR_NOWARN $G_XRS_TRAIN_OUT $G_XRS_TRAIN_ERR \
    $G_XR_TRAIN_ERR_NONORM_NOWARN $G_XRS_TRAIN_OUT_COND $G_XRS_TRAIN_ERR_COND \
    $G_XRS_TRAIN_OUT_NONORM $G_XRS_TRAIN_ERR_NONORM \
    $G_XRS_TRAIN_OUT_COND_TRUEREAL $G_XRS_TRAIN_ERR_COND_TRUEREAL \
    $G_CFG_TRAIN_OUT $G_CFG_TRAIN_ERR $G_TOUGH_RTG $G_TOUGH_CFG $G_TOUGH_XR $G_TOUGH_XRS \
    $G_COMPOSE $G_COMP_ERR $G_RTG_CFG $G_RTG_XR $G_RTG_XRS $G_CFG_RTG \
    $G_TRAINCFG_RTG_SMALLK $G_XR_RTG $G_XR_RTG_TOP5 $G_TREE_THREE_RTG $G_TREE_THREE_CFG $G_TREE_LARGE \
    $G_TREE_LARGER $G_TREE_LARGER_ERR $G_TREE_LARGER_CHECK $G_TREE_SMALL \
    $G_TYPE_TOP4 $G_TYPE_BAD $G_LARGE_READ_ERR $G_TST_LIMIT $G_PARSE_20 $G_CFG_CHECK \
    $G_AMBIG_TRAIN_OUT $G_AMBIG_TRAIN_ERR $G_GO_TREE; do
    if ! [ -e $i ]; then
	echo "# $i does not exist!";
	exit 1;
    else
	debug "Input data file $i exists";
    fi
done;

CURRTEST=0;
# Tests with no files

# baseline

tests[$CURRTEST]="$TIB";
prefixes[$CURRTEST]=$OUTDIR/base;
descs[$CURRTEST]="1: executable with no arguments";
binout[$CURRTEST]=$EXACT;
goldout[$CURRTEST]=$G_EMPTY;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_BASE;

CURRTEST=$(($CURRTEST+1));

# extended help
tests[$CURRTEST]="$TIB --help";
prefixes[$CURRTEST]=$OUTDIR/help;
descs[$CURRTEST]="2: get extended help";
binout[$CURRTEST]=$EXACT;
goldout[$CURRTEST]=$G_EMPTY;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_HELP;

CURRTEST=$(($CURRTEST+1));

### tests with unweighted finite rtg

# spit file back in real semiring through stdout
tests[$CURRTEST]="$TIB $UFRTG";
prefixes[$CURRTEST]=$OUTDIR/realpipe;
descs[$CURRTEST]="3: read unweighted rtg in real semiring, print to stdout";
binout[$CURRTEST]=$AWK;
goldout[$CURRTEST]=$G_UFRTG_REAL;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));

# spit file back in real semiring through -o
tests[$CURRTEST]="$TIB -o $OUTDIR/ufrtg_real_o.fout $UFRTG";
prefixes[$CURRTEST]=$OUTDIR/ufrtg_real_o;
descs[$CURRTEST]="4: read unweighted rtg in real semiring, print to specified file";
binout[$CURRTEST]=$EXACT;
goldout[$CURRTEST]=$G_EMPTY;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;
bingen[$CURRTEST]=$AWK;
gen[$CURRTEST]=$OUTDIR/ufrtg_real_o.fout;
goldgen[$CURRTEST]=$G_UFRTG_REAL;

CURRTEST=$(($CURRTEST+1));

# spit file back in tropical semiring
tests[$CURRTEST]="$TIB -m tropical $UFRTG";
prefixes[$CURRTEST]=$OUTDIR/ufrtg_trop;
descs[$CURRTEST]="5: read unweighted rtg in trop semiring";
binout[$CURRTEST]=$AWK;
goldout[$CURRTEST]=$G_UFRTG_TROP;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));

# check file
tests[$CURRTEST]="$TIB -c $UFRTG";
prefixes[$CURRTEST]=$OUTDIR/ufrtg_check;
descs[$CURRTEST]="6: check unweighted rtg";
binout[$CURRTEST]=$EXACT;
goldout[$CURRTEST]=$G_UFRTG_CHECK;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));

# k best small
tests[$CURRTEST]="$TIB -k 5 $UFRTG";
prefixes[$CURRTEST]=$OUTDIR/ufrtg_smallk;
descs[$CURRTEST]="7: print top 5 of unweighted rtg";
binout[$CURRTEST]=$AWK;
goldout[$CURRTEST]=$G_UFRTG_SMALLK;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));

# k best too big
tests[$CURRTEST]="$TIB -k 26 $UFRTG";
prefixes[$CURRTEST]=$OUTDIR/ufrtg_bigk;
descs[$CURRTEST]="8: print top 26 (too many) of unweighted rtg";
binout[$CURRTEST]=$SORT;
goldout[$CURRTEST]=$G_UFRTG_BIGK;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_KWARN;

CURRTEST=$(($CURRTEST+1));

# 5 random
tests[$CURRTEST]="$TIB -g 5 $UFRTG";
prefixes[$CURRTEST]=$OUTDIR/ufrtg_5g;
descs[$CURRTEST]="9: print random 5 of unweighted rtg";
binout[$CURRTEST]=$AWK;
goldout[$CURRTEST]=$G_RTG_SMALLG;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));

# determinize and check
tests[$CURRTEST]="$TIB -d 2 -c $UFRTG";
prefixes[$CURRTEST]=$OUTDIR/ufrtg_detcheck;
descs[$CURRTEST]="10: determinize and check unweighted rtg";
binout[$CURRTEST]=$SORT_STATEANON;
goldout[$CURRTEST]=$G_UFRTG_DET_CHECK;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));

# determinize and print
tests[$CURRTEST]="$TIB -d 2 $UFRTG";
prefixes[$CURRTEST]=$OUTDIR/ufrtg_det;
descs[$CURRTEST]="11: determinize unweighted rtg";
binout[$CURRTEST]=$SORT_STATEANON;
goldout[$CURRTEST]=$G_UFRTG_DET;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));

# add random weights to rtg

tests[$CURRTEST]="$TIB --randomize $UFRTG";
prefixes[$CURRTEST]=$OUTDIR/ufrtg_rand;
descs[$CURRTEST]="12: set random weights on unweighted rtg";
binout[$CURRTEST]=$AWK;
goldout[$CURRTEST]=$G_UFRTG_RANDOM_WEIGHTS;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));

# add random weights and normalize: sums add to 1

tests[$CURRTEST]="$TIB -n --randomize $UFRTG";
prefixes[$CURRTEST]=$OUTDIR/ufrtg_rand_norm;
descs[$CURRTEST]="13: normalize random weights on unweighted rtg: weights sum to 1";
binout[$CURRTEST]=$AWK;
goldout[$CURRTEST]=$G_RTG_NORM;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

### tests with weighted infinite rtg (even and three)
CURRTEST=$(($CURRTEST+1));

# spit back weighted file
tests[$CURRTEST]="$TIB $RTG_EVEN";
prefixes[$CURRTEST]=$OUTDIR/rtgeven;
descs[$CURRTEST]="14: spit back infinite weighted rtg";
binout[$CURRTEST]=$SORT;
goldout[$CURRTEST]=$G_WRTG_REAL;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));

# check weighted infinite file
tests[$CURRTEST]="$TIB -c $RTG_EVEN";
prefixes[$CURRTEST]=$OUTDIR/rtgevencheck;
descs[$CURRTEST]="15: check infinite weighted rtg";
binout[$CURRTEST]=$EXACT;
goldout[$CURRTEST]=$G_WRTG_CHECK;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

# TODO: determinization of infinite rtg should fail!!

CURRTEST=$(($CURRTEST+1));

# top 100 of weighted infinite file
tests[$CURRTEST]="$TIB -k 100 $RTG_EVEN";
prefixes[$CURRTEST]=$OUTDIR/rtgeven_bigk;
descs[$CURRTEST]="16: print top 100 from infinite weighted rtg (real semiring)";
binout[$CURRTEST]=$SCORETREE;
goldout[$CURRTEST]=$G_WRTG_BIGK;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));

# 5 random shouldn't hang
tests[$CURRTEST]="$TIB -g 5 $RTG_EVEN";
prefixes[$CURRTEST]=$OUTDIR/rtgeven_5g;
descs[$CURRTEST]="17: print random 5 from infinite weighted rtg (real semiring)";
binout[$CURRTEST]=$AWK;
goldout[$CURRTEST]=$G_RTG_SMALLG;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));

# compose
tests[$CURRTEST]="$TIB $RTG_EVEN $RTG_THREE";
prefixes[$CURRTEST]=$OUTDIR/compose;
descs[$CURRTEST]="18: compose two weighted rtgs (real semiring)";
binout[$CURRTEST]=$SORT_STATEANON;
goldout[$CURRTEST]=$G_WRTG_COMPOSE;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));

# 10 best of compose (l-r)
tests[$CURRTEST]="$TIB -k 20 $RTG_EVEN $RTG_THREE";
prefixes[$CURRTEST]=$OUTDIR/compose_lr_10k;
descs[$CURRTEST]="19: compose two weighted rtgs and print top 20 (real semiring)";
binout[$CURRTEST]=$SORT;
goldout[$CURRTEST]=$G_WRTG_COMPOSE_SMALLK;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));

# 10 best of compose (r-l)
tests[$CURRTEST]="$TIB -k 20 $RTG_THREE $RTG_EVEN";
prefixes[$CURRTEST]=$OUTDIR/compose_rl_10k;
descs[$CURRTEST]="20: repeat experiment 19 with rtgs in swapped position";
binout[$CURRTEST]=$SORT;
goldout[$CURRTEST]=$G_WRTG_COMPOSE_SMALLK;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));

# foreign rtg (not sure if I've ever built one of these...)
# TODO: get one from xrs kenji application; better than nothing, right?

### tests with training rtg (from treebankrtg project)

# yield of k-best
tests[$CURRTEST]="$TIB -yk 10 $WRTG_TRAIN";
prefixes[$CURRTEST]=$OUTDIR/train_10k;
descs[$CURRTEST]="21: yield of top 10 from trainable rtg";
binout[$CURRTEST]=$EXACT;
goldout[$CURRTEST]=$G_TRAINRTG_SMALLK_YIELD
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_LARGE_READ_ERR;

CURRTEST=$(($CURRTEST+1));

# train rtg
tests[$CURRTEST]="$TIB -t 10 -o $OUTDIR/train.fout $WRTG_TREES $WRTG_TRAIN";
prefixes[$CURRTEST]=$OUTDIR/train;
descs[$CURRTEST]="22: 10 iterations of RTG training, writing result to output file";
binout[$CURRTEST]=$EXACT;
goldout[$CURRTEST]=$G_EMPTY;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_TRAINRTG_TRAIN_ERR
bingen[$CURRTEST]=$SORT;
gen[$CURRTEST]=$OUTDIR/train.fout;
goldgen[$CURRTEST]=$G_TRAINRTG_TRAIN_RESULT;

CURRTEST=$(($CURRTEST+1));

# no longer possible
# # batch mode to calculate cross-entropy of tree set
# tests[$CURRTEST]="$TIB -b $WRTG_BATCH_TREES $WRTG_TRAIN";
# prefixes[$CURRTEST]=$OUTDIR/batch;
# descs[$CURRTEST]="23: batch mode calculates cross-entropy of small set";
# binout[$CURRTEST]=$EXACT;
# goldout[$CURRTEST]=$G_TRAINRTG_BATCH_OUT;
# binerr[$CURRTEST]=$AWK;
# golderr[$CURRTEST]=$G_LARGE_READ_ERR;

CURRTEST=$(($CURRTEST+1));

### transducer tests. xr and xrs tested together where applicable
# xr transducer from Alexander Radzievskiy
# xrs transducer from kenji experiment

# read xr
tests[$CURRTEST]="$TIB $XR_TRAIN";
prefixes[$CURRTEST]=$OUTDIR/xr;
descs[$CURRTEST]="24: load and spit back unweighted xr transducer";
binout[$CURRTEST]=$SORT;
goldout[$CURRTEST]=$G_UXR_REAL
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));

# read xrs
tests[$CURRTEST]="$TIB -e euc-jp $XRS_TIED_EUCJP_TRAIN";
prefixes[$CURRTEST]=$OUTDIR/xrs;
descs[$CURRTEST]="25: load and spit back unweighted xrs transducer with japanese";
binout[$CURRTEST]=$SORT;
goldout[$CURRTEST]=$G_UXRS_REAL
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));

# read xrs to out file
tests[$CURRTEST]="$TIB -e euc-jp -o $OUTDIR/xrso.fout $XRS_TIED_EUCJP_TRAIN";
prefixes[$CURRTEST]=$OUTDIR/xrso;
descs[$CURRTEST]="26: repeat exp 25 with output file instead of stdout";
binout[$CURRTEST]=$EXACT;
goldout[$CURRTEST]=$G_EMPTY;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION
bingen[$CURRTEST]=$SORT;
gen[$CURRTEST]=$OUTDIR/xrso.fout;
goldgen[$CURRTEST]=$G_UXRS_REAL;


CURRTEST=$(($CURRTEST+1));

# check xr
tests[$CURRTEST]="$TIB -c $XR_TRAIN";
prefixes[$CURRTEST]=$OUTDIR/xrcheck;
descs[$CURRTEST]="27: check stats of xr transducer";
binout[$CURRTEST]=$EXACT;
goldout[$CURRTEST]=$G_UXR_CHECK
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));

# check xrs
tests[$CURRTEST]="$TIB -e euc-jp -c $XRS_TIED_EUCJP_TRAIN";
prefixes[$CURRTEST]=$OUTDIR/xrscheck;
descs[$CURRTEST]="28: check stats of xrs transducer";
binout[$CURRTEST]=$EXACT;
goldout[$CURRTEST]=$G_UXRS_CHECK
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));

# apply to xr
tests[$CURRTEST]="$TIB $XR_LEFT_TREE $XR_TRAIN";
prefixes[$CURRTEST]=$OUTDIR/xrapply;
descs[$CURRTEST]="29: apply test tree to unweighted xr transducer";
binout[$CURRTEST]=$SORT_STATEANON;
goldout[$CURRTEST]=$G_UXR_APPLY;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));

# can't do this test because | cannot be put into quoted strings and still retain its property
# until it's necessary to make another run semantics, leave this test out
# tests[$CURRTEST]="cat $XR_LEFT_TREE | $TIB -ls $XR_TRAIN";
# prefixes[$CURRTEST]=$OUTDIR/xrapplystdin;
# descs[$CURRTEST]="29a: repeat test 29 passing the tree through stdin";
# binout[$CURRTEST]=$EXACT;
# goldout[$CURRTEST]=$G_UXR_APPLY;
# binerr[$CURRTEST]=$AWK;
# golderr[$CURRTEST]=$G_VERSION;

#CURRTEST=$(($CURRTEST+1));

# apply and 90best (all) to xr

tests[$CURRTEST]="$TIB -k 90 $XR_LEFT_TREE $XR_TRAIN";
prefixes[$CURRTEST]=$OUTDIR/xrapply_90k;
descs[$CURRTEST]="30: apply test tree to unweighted xr transducer and get all 90 trees";
binout[$CURRTEST]=$SORT;
goldout[$CURRTEST]=$G_UXR_APPLY_ALLK;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));

# apply to xrs
tests[$CURRTEST]="$TIB -e euc-jp $XRS_LEFT_TREE $XRS_TIED_EUCJP_TRAIN";
prefixes[$CURRTEST]=$OUTDIR/xrsapply;
descs[$CURRTEST]="31: apply test tree to unweighted xrs transducer";
binout[$CURRTEST]=$SORT_STATEANON;
goldout[$CURRTEST]=$G_UXRS_APPLY;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));

# apply to xrs output file
tests[$CURRTEST]="$TIB -e euc-jp -o $OUTDIR/xrsapplyo.fout $XRS_LEFT_TREE $XRS_TIED_EUCJP_TRAIN";
prefixes[$CURRTEST]=$OUTDIR/xrsapplyo;
descs[$CURRTEST]="32: repeat exp 31 with output file instead of stdout";
binout[$CURRTEST]=$EXACT;
goldout[$CURRTEST]=$G_EMPTY;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION
bingen[$CURRTEST]=$SORT_STATEANON;
gen[$CURRTEST]=$OUTDIR/xrsapplyo.fout;
goldgen[$CURRTEST]=$G_UXRS_APPLY;

CURRTEST=$(($CURRTEST+1));

# apply and 5best to xrs
tests[$CURRTEST]="$TIB -k 5 -e euc-jp $XRS_LEFT_TREE $XRS_TIED_EUCJP_TRAINED";
prefixes[$CURRTEST]=$OUTDIR/xrsapply_5k;
descs[$CURRTEST]="33: apply test tree to weighted xrs transducer and get 5 best strings";
binout[$CURRTEST]=$EXACT;
goldout[$CURRTEST]=$G_XRS_APPLY_SMALLK;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_TST_LIMIT;

CURRTEST=$(($CURRTEST+1));

# complex on xr
tests[$CURRTEST]="$TIB -k 9 -n -d 1 $XR_LEFT_TREE $XR_TRAIN";
prefixes[$CURRTEST]=$OUTDIR/xrcomplex;
descs[$CURRTEST]="34: apply test tree to xr, determinize, normalize, get top 9 trees";
binout[$CURRTEST]=$SORT;
goldout[$CURRTEST]=$G_UXR_APPLY_DET_NORM_SMALLK;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));

# note: the equivalent of this used to give a deriv forest, but I'm not sure how much that's wanted anymore...
# lr on xr
tests[$CURRTEST]="$TIB $XR_LEFT_TREE $XR_TRAIN $XR_LEFT_TREE";
prefixes[$CURRTEST]=$OUTDIR/xrboth;
descs[$CURRTEST]="35: two-sided apply to xr";
binout[$CURRTEST]=$SORT_STATEANON;
goldout[$CURRTEST]=$G_UXR_APPLY_DERIV;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));

# lr check on xr
tests[$CURRTEST]="$TIB -c $XR_LEFT_TREE $XR_TRAIN $XR_LEFT_TREE";
prefixes[$CURRTEST]=$OUTDIR/xrbothcheck;
descs[$CURRTEST]="36: check of two-sided apply to xr";
binout[$CURRTEST]=$EXACT;
goldout[$CURRTEST]=$G_UXR_APPLY_DERIV_CHECK;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));

# lr on xrs
tests[$CURRTEST]="$TIB -e euc-jp -l $XRS_LEFT_TREE $XRS_TIED_EUCJP_TRAIN $XRS_RIGHT_STRING";
prefixes[$CURRTEST]=$OUTDIR/xrsboth;
descs[$CURRTEST]="37: two-sided apply to xrs";
binout[$CURRTEST]=$SORT_STATEANON;
goldout[$CURRTEST]=$G_UXRS_APPLY_DERIV;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));

# lr check on xrs
tests[$CURRTEST]="$TIB -e euc-jp -c -l $XRS_LEFT_TREE $XRS_TIED_EUCJP_TRAIN $XRS_RIGHT_STRING";
prefixes[$CURRTEST]=$OUTDIR/xrsbothcheck;
descs[$CURRTEST]="38: check of two-sided apply to xrs";
binout[$CURRTEST]=$EXACT;
goldout[$CURRTEST]=$G_UXRS_APPLY_DERIV_CHECK;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));

# train on xr with normalization
tests[$CURRTEST]="$TIB -t 5 --training-deriv-location $OUTDIR/xrtrain.deriv $XR_TREES $XR_TRAIN";
prefixes[$CURRTEST]=$OUTDIR/xrtrain;
descs[$CURRTEST]="39: train xr 5 iterations saving derivations";
binout[$CURRTEST]=$SORT;
goldout[$CURRTEST]=$G_XR_TRAIN_OUT;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_XR_TRAIN_ERR_WARN;

CURRTEST=$(($CURRTEST+1));

# train on xr with no normalization, reloading from deriv 
tests[$CURRTEST]="$TIB -t 5 --no-normalize --no-deriv --training-deriv-location $OUTDIR/xrtrain.deriv $XR_TREES $XR_TRAIN";
prefixes[$CURRTEST]=$OUTDIR/xrtrainnonorm;
descs[$CURRTEST]="40: train xr 5 iterations not normalized, using saved derivations -- same result! (must run experiment 39 first!)";
binout[$CURRTEST]=$SORT;
goldout[$CURRTEST]=$G_XR_TRAIN_OUT;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_XR_TRAIN_ERR_NONORM_NOWARN;

CURRTEST=$(($CURRTEST+1));

# train on xr with normalization reloading 
tests[$CURRTEST]="$TIB -t 5 --no-deriv --training-deriv-location $OUTDIR/xrtrain.deriv $XR_TREES $XR_TRAIN";
prefixes[$CURRTEST]=$OUTDIR/xrtrainredo;
descs[$CURRTEST]="41: repeat experiment 39, using saved derivations (must run experiment 39 first!)";
binout[$CURRTEST]=$SORT;
goldout[$CURRTEST]=$G_XR_TRAIN_OUT;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_XR_TRAIN_ERR_NOWARN;

CURRTEST=$(($CURRTEST+1));

# train on xrs with normalization 
tests[$CURRTEST]="$TIB -e euc-jp --training-deriv-location $OUTDIR/xrstrain.deriv -t 5 $XRS_TSP $XRS_TIED_EUCJP_TRAIN";
prefixes[$CURRTEST]=$OUTDIR/xrstrain;
descs[$CURRTEST]="42: train euc-jp and tied xrs 5 iterations saving derivations";
binout[$CURRTEST]=$SORT;
goldout[$CURRTEST]=$G_XRS_TRAIN_OUT;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_XRS_TRAIN_ERR;

CURRTEST=$(($CURRTEST+1));

# train on xrs with no normalization, reloading from deriv
tests[$CURRTEST]="$TIB -e euc-jp -t 5 --no-deriv --no-normalize --training-deriv-location $OUTDIR/xrstrain.deriv $XRS_TSP $XRS_TIED_EUCJP_TRAIN";
prefixes[$CURRTEST]=$OUTDIR/xrstrainnonorm;
descs[$CURRTEST]="43: train xrs 5 iterations not normalized using saved derivations (must run experiment 42 first!)";
binout[$CURRTEST]=$SORT;
goldout[$CURRTEST]=$G_XRS_TRAIN_OUT_NONORM;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_XRS_TRAIN_ERR_NONORM;

CURRTEST=$(($CURRTEST+1));

tests[$CURRTEST]="$TIB -e euc-jp -t 5 --no-deriv --training-deriv-location $OUTDIR/xrstrain.deriv $XRS_TSP $XRS_TIED_EUCJP_TRAIN";
prefixes[$CURRTEST]=$OUTDIR/xrstrainquick;
descs[$CURRTEST]="44: repeat experiment 42, using saved derivations (must run experiment 42 first!)";
binout[$CURRTEST]=$SORT;
goldout[$CURRTEST]=$G_XRS_TRAIN_OUT;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_XRS_TRAIN_ERR;

CURRTEST=$(($CURRTEST+1));

tests[$CURRTEST]="$TIB -e euc-jp --conditional -t 5 --no-deriv --training-deriv-location $OUTDIR/xrstrain.deriv $XRS_TSP $XRS_TIED_EUCJP_TRAIN";
prefixes[$CURRTEST]=$OUTDIR/xrstrainquickcond;
descs[$CURRTEST]="45: repeat experiment 44, using conditional probs instead of joint (must run experiment 43 first!)";
binout[$CURRTEST]=$SORT;
goldout[$CURRTEST]=$G_XRS_TRAIN_OUT_COND;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_XRS_TRAIN_ERR_COND;

CURRTEST=$(($CURRTEST+1));

tests[$CURRTEST]="$TIB -e euc-jp -m truereal --conditional -t 5 --no-deriv --training-deriv-location $OUTDIR/xrstrain.deriv $XRS_TSP $XRS_TIED_EUCJP_TRAIN";
prefixes[$CURRTEST]=$OUTDIR/xrstrainquickcondtruereal;
descs[$CURRTEST]="46: repeat experiment 45, using truereal semiring (no log internals) (must run experiment 43 first!)";
binout[$CURRTEST]=$SORT;
goldout[$CURRTEST]=$G_XRS_TRAIN_OUT_COND_TRUEREAL;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_XRS_TRAIN_ERR_COND_TRUEREAL;

CURRTEST=$(($CURRTEST+1));

# train cfg
tests[$CURRTEST]="$TIB -t 10 --training-deriv-location $OUTDIR/traincfg.deriv $CFG_STRINGS $CFG_TRAIN";
prefixes[$CURRTEST]=$OUTDIR/traincfg;
descs[$CURRTEST]="47: 10 iterations of CFG training";
binout[$CURRTEST]=$SORT;
goldout[$CURRTEST]=$G_CFG_TRAIN_OUT;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_CFG_TRAIN_ERR;

CURRTEST=$(($CURRTEST+1));

# train cfg using saved derivations
tests[$CURRTEST]="$TIB -t 10 --training-deriv-location $OUTDIR/traincfg.deriv --no-deriv $CFG_STRINGS $CFG_TRAIN";
prefixes[$CURRTEST]=$OUTDIR/traincfgnoderiv;
descs[$CURRTEST]="48: repeat experiment 47, using saved derivations (must run experiment 47 first!)";
binout[$CURRTEST]=$SORT;
goldout[$CURRTEST]=$G_CFG_TRAIN_OUT;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_CFG_TRAIN_ERR;


CURRTEST=$(($CURRTEST+1));

# read and write a tricky rtg
tests[$CURRTEST]="$TIB $RTG_TOUGH";
prefixes[$CURRTEST]=$OUTDIR/toughrtg;
descs[$CURRTEST]="49: read and write a tricky rtg";
binout[$CURRTEST]=$SORT;
goldout[$CURRTEST]=$G_TOUGH_RTG;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));

# read and write a tricky cfg
tests[$CURRTEST]="$TIB $CFG_TOUGH";
prefixes[$CURRTEST]=$OUTDIR/toughcfg;
descs[$CURRTEST]="50: read and write a tricky cfg";
binout[$CURRTEST]=$SORT;
goldout[$CURRTEST]=$G_TOUGH_CFG;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));

# compose transducers
tests[$CURRTEST]="$TIB $XR_COMP $RLN_COMP";
prefixes[$CURRTEST]=$OUTDIR/compose;
descs[$CURRTEST]="51: compose copying, deleting, extended lhs transducer with RLN";
binout[$CURRTEST]=$SORT_STATEANON;
goldout[$CURRTEST]=$G_COMPOSE;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));

tests[$CURRTEST]="$TIB $RLN_COMP $XR_COMP";
prefixes[$CURRTEST]=$OUTDIR/compwrong;
descs[$CURRTEST]="52: attempt to compose transducer with nonRLN";
binout[$CURRTEST]=$EXACT;
goldout[$CURRTEST]=$G_EMPTY;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_COMP_ERR;

CURRTEST=$(($CURRTEST+1));

tests[$CURRTEST]="$TIB -x CFG $WRTG_TRAIN";
prefixes[$CURRTEST]=$OUTDIR/rtg2cfg;
descs[$CURRTEST]="53: convert wRTG to wCFG";
binout[$CURRTEST]=$SORT_STATEANON;
goldout[$CURRTEST]=$G_RTG_CFG;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_LARGE_READ_ERR;

CURRTEST=$(($CURRTEST+1));

tests[$CURRTEST]="$TIB -k 10 -x CFG $WRTG_TRAIN";
prefixes[$CURRTEST]=$OUTDIR/rtg2cfgprint;
descs[$CURRTEST]="54: convert wRTG to wCFG, print 10 best strings";
binout[$CURRTEST]=$EXACT;
goldout[$CURRTEST]=$G_TRAINRTG_SMALLK_YIELD;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_LARGE_READ_ERR;

CURRTEST=$(($CURRTEST+1));

tests[$CURRTEST]="$TIB -x XR $RTG_THREE";
prefixes[$CURRTEST]=$OUTDIR/rtg2xr;
descs[$CURRTEST]="55: convert RTG to xR";
binout[$CURRTEST]=$SORT;
goldout[$CURRTEST]=$G_RTG_XR;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));

# tests[$CURRTEST]="$TIB -b --leftapply -x XR $THREE_TREE $RTG_THREE";
# prefixes[$CURRTEST]=$OUTDIR/rtg2xrpass;
# descs[$CURRTEST]="56: convert RTG to xR, pass tree through lhs (TODO: change run syntax!)";
# binout[$CURRTEST]=$EXACT;
# goldout[$CURRTEST]=$G_TREE_THREE_RTG;
# binerr[$CURRTEST]=$AWK;
# golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));

tests[$CURRTEST]="$TIB -x XRS $RTG_THREE";
prefixes[$CURRTEST]=$OUTDIR/rtg2xrs;
descs[$CURRTEST]="57: convert RTG to xRs";
binout[$CURRTEST]=$SORT;
goldout[$CURRTEST]=$G_RTG_XRS;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;


CURRTEST=$(($CURRTEST+1));

# tests[$CURRTEST]="$TIB -b --leftapply -x XRS $THREE_TREE $RTG_THREE";
# prefixes[$CURRTEST]=$OUTDIR/rtg2xrspass;
# descs[$CURRTEST]="58: convert RTG to xRs, pass tree through lhs (TODO: change run syntax!)";
# binout[$CURRTEST]=$EXACT;
# goldout[$CURRTEST]=$G_TREE_THREE_CFG;
# binerr[$CURRTEST]=$AWK;
# golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));

tests[$CURRTEST]="$TIB -x RTG $CFG_TRAIN";
prefixes[$CURRTEST]=$OUTDIR/cfg2rtg;
descs[$CURRTEST]="59: convert CFG to RTG";
binout[$CURRTEST]=$SORT_STATEANON;
goldout[$CURRTEST]=$G_CFG_RTG;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));

tests[$CURRTEST]="$TIB -k 10 -x RTG $WCFG_TRAIN";
prefixes[$CURRTEST]=$OUTDIR/rtg2cfgprint;
descs[$CURRTEST]="60: convert wCFG to wRTG, print 10 best trees";
binout[$CURRTEST]=$EXACT;
goldout[$CURRTEST]=$G_TRAINCFG_RTG_SMALLK;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_LARGE_READ_ERR;

CURRTEST=$(($CURRTEST+1));

tests[$CURRTEST]="$TIB -x XR $CFG_EVEN";
prefixes[$CURRTEST]=$OUTDIR/cfg2xr;
descs[$CURRTEST]="61: convert CFG to xR";
binout[$CURRTEST]=$SORT;
goldout[$CURRTEST]=$G_CFG_XR;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));

# descs[$CURRTEST]="62: convert CFG to xR, pass tree through lhs (can't do yet)";

CURRTEST=$(($CURRTEST+1));

tests[$CURRTEST]="$TIB -x XRS $CFG_EVEN";
prefixes[$CURRTEST]=$OUTDIR/cfg2xrs;
descs[$CURRTEST]="63: convert CFG to xRs";
binout[$CURRTEST]=$SORT;
goldout[$CURRTEST]=$G_CFG_XRS;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));

# read and write a tricky rtg
tests[$CURRTEST]="$TIB $XR_TOUGH";
prefixes[$CURRTEST]=$OUTDIR/toughxr;
descs[$CURRTEST]="64: read and write a tricky xr";
binout[$CURRTEST]=$SORT;
goldout[$CURRTEST]=$G_TOUGH_XR;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));

# read and write a tricky xrs
tests[$CURRTEST]="$TIB $XRS_TOUGH";
prefixes[$CURRTEST]=$OUTDIR/toughxrs;
descs[$CURRTEST]="65: read and write a tricky xrs";
binout[$CURRTEST]=$SORT;
goldout[$CURRTEST]=$G_TOUGH_XRS;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));

# descs[$CURRTEST]="66: convert CFG to xRs, pass tree through lhs (can't do yet)";


CURRTEST=$(($CURRTEST+1));

tests[$CURRTEST]="$TIB -x RTG $XR_DOM";
prefixes[$CURRTEST]=$OUTDIR/r2rtg;
descs[$CURRTEST]="67: convert R to RTG (domain projection)";
binout[$CURRTEST]=$SORT;
goldout[$CURRTEST]=$G_XR_RTG;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));

tests[$CURRTEST]="$TIB -k 5 -x RTG $XR_DOM";
prefixes[$CURRTEST]=$OUTDIR/r2rtgtop5;
descs[$CURRTEST]="68: convert R to RTG (domain projection) and print 5 best trees";
binout[$CURRTEST]=$EXACT;
goldout[$CURRTEST]=$G_XR_RTG_TOP5;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;


CURRTEST=$(($CURRTEST+1));
# descs[$CURRTEST]="69: convert xR to xRs (can't do yet)";

CURRTEST=$(($CURRTEST+1));

tests[$CURRTEST]="$TIB -x RTG $XRS_DOM";
prefixes[$CURRTEST]=$OUTDIR/rs2rtg;
descs[$CURRTEST]="70: convert Rs to RTG (domain projection)";
binout[$CURRTEST]=$SORT;
goldout[$CURRTEST]=$G_XR_RTG;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));
descs[$CURRTEST]="71: convert Rs to RTG, print 5 best trees";
tests[$CURRTEST]="$TIB -k 5 -x RTG $XRS_DOM";
prefixes[$CURRTEST]=$OUTDIR/rs2rtgtop5;
binout[$CURRTEST]=$EXACT;
goldout[$CURRTEST]=$G_XR_RTG_TOP5;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));
# descs[$CURRTEST]="72: convert xRs to xR (can't do yet)";
CURRTEST=$(($CURRTEST+1));
#descs[$CURRTEST]="73: convert RTG to xR, pass tree through rhs ";
CURRTEST=$(($CURRTEST+1));

tests[$CURRTEST]="$TIB -k 20 $PARSE_XRS $PARSE_STR";
prefixes[$CURRTEST]=$OUTDIR/parse;
descs[$CURRTEST]="74: pass string through rhs of weighted rtg converted (previously) to weighted xrs, get top 20";
binout[$CURRTEST]=$EXACT;
goldout[$CURRTEST]=$G_PARSE_20;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;



CURRTEST=$(($CURRTEST+1));

tests[$CURRTEST]="$TIB $TREE_LARGE";
prefixes[$CURRTEST]=$OUTDIR/largetree;
descs[$CURRTEST]="75: read a fairly large tree (testing reset capabilities of detection)";
binout[$CURRTEST]=$EXACT;
goldout[$CURRTEST]=$G_TREE_LARGE;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;



CURRTEST=$(($CURRTEST+1));

tests[$CURRTEST]="$TIB $TREE_SMALL";
prefixes[$CURRTEST]=$OUTDIR/smalltree;
descs[$CURRTEST]="76: read a one-node tree (testing corner case found by kevin)";
binout[$CURRTEST]=$EXACT;
goldout[$CURRTEST]=$G_TREE_SMALL;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));

tests[$CURRTEST]="$TIB -k 4 $TYPE_RTG";
prefixes[$CURRTEST]=$OUTDIR/typertg;
descs[$CURRTEST]="77: read a type-specified rtg and get top 4";
binout[$CURRTEST]=$SORT;
goldout[$CURRTEST]=$G_TYPE_TOP4;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));

tests[$CURRTEST]="$TIB -k 4 $TYPE_CFG";
prefixes[$CURRTEST]=$OUTDIR/typecfg;
descs[$CURRTEST]="78: read a type-specified cfg and get top 4";
binout[$CURRTEST]=$SORT;
goldout[$CURRTEST]=$G_TYPE_TOP4;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));

tests[$CURRTEST]="$TIB -k 4 $TYPE_BAD";
prefixes[$CURRTEST]=$OUTDIR/typebad;
descs[$CURRTEST]="79: read a badly type-specified file and try to get top 4";
binout[$CURRTEST]=$EXACT;
goldout[$CURRTEST]=$G_EMPTY;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_TYPE_BAD;


CURRTEST=$(($CURRTEST+1));

tests[$CURRTEST]="$TIB -c $PARSE_CFG $PARSE_AB";
prefixes[$CURRTEST]=$OUTDIR/parsecfg;
descs[$CURRTEST]="80: backwards application of a small string on an epsilon-containing cfg and get status";
binout[$CURRTEST]=$EXACT;
goldout[$CURRTEST]=$G_CFG_CHECK;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));

# apply batch to xr
tests[$CURRTEST]="$TIB -o $OUTDIR/xrbatchapply $XR_LEFT_TREE_BATCH $XR_TRAIN";
prefixes[$CURRTEST]=$OUTDIR/xrbatchapply;
descs[$CURRTEST]="81: apply test tree batch to unweighted xr transducer (like 28), check the .1 file";
binout[$CURRTEST]=$EXACT;
goldout[$CURRTEST]=$G_EMPTY;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;
bingen[$CURRTEST]=$SORT_STATEANON;
gen[$CURRTEST]=$OUTDIR/xrbatchapply.1;
goldgen[$CURRTEST]=$G_UXR_BATCH_APPLY;

CURRTEST=$(($CURRTEST+1));

# apply batch to xr and get top 5 of each
tests[$CURRTEST]="$TIB -k 5 $XR_LEFT_TREE_BATCH $XR_TRAIN ";
prefixes[$CURRTEST]=$OUTDIR/xrbatchapply_5k;
descs[$CURRTEST]="82: apply test tree batch to unweighted xr transducer and get 5 trees from each";
binout[$CURRTEST]=$SCORE;
goldout[$CURRTEST]=$G_UXR_BATCH_APPLY_5K;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));

# apply batch to xrs and get top 5
tests[$CURRTEST]="$TIB -e euc-jp -k 5 $XRS_LEFT_TREE_BATCH $XRS_TIED_EUCJP_TRAINED ";
prefixes[$CURRTEST]=$OUTDIR/xrsbatchapply;
descs[$CURRTEST]="83: apply test tree batch to weighted xrs transducer and get top 5";
binout[$CURRTEST]=$EXACT;
goldout[$CURRTEST]=$G_UXRS_BATCH_APPLY;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_TST_LIMIT;

CURRTEST=$(($CURRTEST+1));

# apply batch to xrs and get top 5 to output file (check #3) 
tests[$CURRTEST]="$TIB -e euc-jp -k 5 -o $OUTDIR/xrsbatchapply_5k $XRS_LEFT_TREE_BATCH $XRS_TIED_EUCJP_TRAINED";
prefixes[$CURRTEST]=$OUTDIR/xrsapply_5k;
descs[$CURRTEST]="84: repeat exp 83 with output file instead of stdout; check file 3";
binout[$CURRTEST]=$EXACT;
goldout[$CURRTEST]=$G_EMPTY;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_TST_LIMIT;
bingen[$CURRTEST]=$EXACT;
gen[$CURRTEST]=$OUTDIR/xrsbatchapply_5k.3;
goldgen[$CURRTEST]=$G_UXRS_APPLY_SELECT;

CURRTEST=$(($CURRTEST+1));

# complex on batch xr
tests[$CURRTEST]="$TIB -k 9 -n -d 1 $XR_LEFT_TREE_BATCH $XR_TRAIN";
prefixes[$CURRTEST]=$OUTDIR/xrbatchcomplex;
descs[$CURRTEST]="85: apply test tree batch to xr, determinize, normalize, get top 9 trees (like 34)";
binout[$CURRTEST]=$SCORE;
goldout[$CURRTEST]=$G_UXR_BATCH_APPLY_DET_NORM_SMALLK;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));

tests[$CURRTEST]="$TIB -k 1 $XR_LEFT_TREE_BATCH $XR_TRAIN $XR_RIGHT_TREE_BATCH";
prefixes[$CURRTEST]=$OUTDIR/xrbothbatch;
descs[$CURRTEST]="86: two-sided batch apply to xr (like 35), get 1 best";
binout[$CURRTEST]=$EXACT;
goldout[$CURRTEST]=$G_UXR_BOTH_BATCH_APPLY_1K_LEFT;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));

tests[$CURRTEST]="$TIB -l -k 1 $XR_LEFT_TREE_BATCH $XR_TRAIN $XR_RIGHT_TREE_BATCH";
prefixes[$CURRTEST]=$OUTDIR/xrbothbatchleft;
descs[$CURRTEST]="87: two-sided batch apply to xr (like 35), left direction, get 1 best";
binout[$CURRTEST]=$SORT_STATEANON;
goldout[$CURRTEST]=$G_UXR_BOTH_BATCH_APPLY_1K_RIGHT;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));

tests[$CURRTEST]="$TIB $TREE_LARGER"
prefixes[$CURRTEST]=$OUTDIR/largererr;
descs[$CURRTEST]="88: read tree too large to read";
binout[$CURRTEST]=$EXACT;
goldout[$CURRTEST]=$G_EMPTY;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_TREE_LARGER_ERR;

CURRTEST=$(($CURRTEST+1));

tests[$CURRTEST]="$TIB --lookahead 50000 $TREE_LARGER"
prefixes[$CURRTEST]=$OUTDIR/largela;
descs[$CURRTEST]="89: read tree too large to read with lookahead mod";
binout[$CURRTEST]=$EXACT;
goldout[$CURRTEST]=$G_TREE_LARGER;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));

tests[$CURRTEST]="$TIB --lookahead 50000 -c $TREE_LARGER"
prefixes[$CURRTEST]=$OUTDIR/largelacheck;
descs[$CURRTEST]="90: read tree too large to read with lookahead mod, get stats";
binout[$CURRTEST]=$EXACT;
goldout[$CURRTEST]=$G_TREE_LARGER_CHECK;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));

tests[$CURRTEST]="$TIB $TREE_LARGER_TC"
prefixes[$CURRTEST]=$OUTDIR/largelacheck;
descs[$CURRTEST]="91: read tree too large to read with type check";
binout[$CURRTEST]=$EXACT;
goldout[$CURRTEST]=$G_TREE_LARGER;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;

CURRTEST=$(($CURRTEST+1));

tests[$CURRTEST]="$TIB -t 2 $AMBIG_TREE $AMBIG_RTG"
prefixes[$CURRTEST]=$OUTDIR/ambigtree;
descs[$CURRTEST]="92: rtg training with ambiguous training set cast as tree";
binout[$CURRTEST]=$EXACT;
goldout[$CURRTEST]=$G_AMBIG_TRAIN_OUT;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_AMBIG_TRAIN_ERR;

CURRTEST=$(($CURRTEST+1));

tests[$CURRTEST]="$TIB -t 2 $AMBIG_STRING $AMBIG_CFG"
prefixes[$CURRTEST]=$OUTDIR/ambigstring;
descs[$CURRTEST]="93: cfg training with ambiguous training set cast as string";
binout[$CURRTEST]=$EXACT;
goldout[$CURRTEST]=$G_AMBIG_TRAIN_OUT;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_AMBIG_TRAIN_ERR;

CURRTEST=$(($CURRTEST+1));

tests[$CURRTEST]="$TIB -k 1 $GO_XRS $GO_STRING"
prefixes[$CURRTEST]=$OUTDIR/goparse;
descs[$CURRTEST]="94: simple 1-best xrs parse corner case victoria found";
binout[$CURRTEST]=$EXACT;
goldout[$CURRTEST]=$G_GO_TREE;
binerr[$CURRTEST]=$AWK;
golderr[$CURRTEST]=$G_VERSION;



#descs[$CURRTEST]="88: tree trans training with ambiguous training set cast as tree";
#descs[$CURRTEST]="88: tree trans training with ambiguous training set cast as tree-string";

# # lr check on xr
# tests[$CURRTEST]="$TIB -c $XR_LEFT_TREE $XR_TRAIN $XR_LEFT_TREE";
# prefixes[$CURRTEST]=$OUTDIR/xrbothcheck;
# descs[$CURRTEST]="36: check of two-sided apply to xr";
# binout[$CURRTEST]=$EXACT;
# goldout[$CURRTEST]=$G_UXR_APPLY_DERIV_CHECK;
# binerr[$CURRTEST]=$AWK;
# golderr[$CURRTEST]=$G_VERSION;

# CURRTEST=$(($CURRTEST+1));

# # lr on xrs
# tests[$CURRTEST]="$TIB -e euc-jp -l $XRS_LEFT_TREE $XRS_TIED_EUCJP_TRAIN $XRS_RIGHT_STRING";
# prefixes[$CURRTEST]=$OUTDIR/xrsboth;
# descs[$CURRTEST]="37: two-sided apply to xrs";
# binout[$CURRTEST]=$SORT_STATEANON;
# goldout[$CURRTEST]=$G_UXRS_APPLY_DERIV;
# binerr[$CURRTEST]=$AWK;
# golderr[$CURRTEST]=$G_VERSION;

# CURRTEST=$(($CURRTEST+1));

# # lr check on xrs
# tests[$CURRTEST]="$TIB -e euc-jp -c -l $XRS_LEFT_TREE $XRS_TIED_EUCJP_TRAIN $XRS_RIGHT_STRING";
# prefixes[$CURRTEST]=$OUTDIR/xrsbothcheck;
# descs[$CURRTEST]="38: check of two-sided apply to xrs";
# binout[$CURRTEST]=$EXACT;
# goldout[$CURRTEST]=$G_UXRS_APPLY_DERIV_CHECK;
# binerr[$CURRTEST]=$AWK;
# golderr[$CURRTEST]=$G_VERSION;

# CURRTEST=$(($CURRTEST+1));


# TODO: integrated search test, forward and backward. beaming test on composition, parsing on xrs.

if [ $STOP = "end" ]; then
    STOP=$CURRTEST;
else
    STOP=$(($STOP-1));
fi

if [[ $OS == "Darwin" ]]; then
    SEQCMD="jot - $(($START-1)) $STOP 1";
else
    SEQCMD="seq $(($START-1)) $STOP";
fi
debug "sequence Command is $SEQCMD";
# run tests
for i in `$SEQCMD`; do
    debug "About to launch  run_test ${tests[$i]} ${prefixes[$i]} ${descs[$i]} \
${binout[$i]} ${goldout[$i]} ${binerr[$i]} ${golderr[$i]} \
${bingen[$i]} ${gen[$i]} ${goldgen[$i]}";
    # unquoted members are optional but have to not contain spaces, or this will mess up!
    run_test "${tests[$i]}" "${prefixes[$i]}" "${descs[$i]}" ${binout[$i]} ${goldout[$i]} ${binerr[$i]} ${golderr[$i]} ${bingen[$i]} ${gen[$i]} ${goldgen[$i]};
done;
if [ $OS = "Darwin" ]; then
    say "The test is over";
fi

# cleanup

if [ $DELETETMP -eq 1 -a $NORUN -eq 0 ]; then
    rm -rf $OUTDIR;
    debug "Deleted $OUTDIR";
fi
