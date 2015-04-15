#!/usr/bin/env bash

# both GOLD and DATA are of the form content: score
# scores should match in order. content should match but not necessarily in order

GOLD=$1;
DATA=$2;
TEMPDIR=`mktemp -d scoretreematch.XXXXXX`;
awk -F" # " '{print $2}' $GOLD > $TEMPDIR/gold.score;
awk -F" # " '{print $2}' $DATA > $TEMPDIR/data.score;
awk -F" # " '{print $1}' $GOLD | sort > $TEMPDIR/gold.content;
awk -F" # " '{print $1}' $DATA | sort > $TEMPDIR/data.content;
diff -q $TEMPDIR/gold.score $TEMPDIR/data.score
if [ $? -eq 1 ]; then
    echo "Scores differ (compared in order)";
    rm -rf $TEMPDIR;
    exit 1;
fi
diff -q $TEMPDIR/gold.content $TEMPDIR/data.content
if [ $? -eq 1 ]; then
    echo "Content differs (compared sorted)";
    rm -rf $TEMPDIR;
    exit 1;
fi

rm -rf $TEMPDIR
