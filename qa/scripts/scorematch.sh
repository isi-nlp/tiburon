#!/usr/bin/env bash

# both GOLD and DATA are of the form content # score
# scores should match in order. content doesn't need to

GOLD=$1;
DATA=$2;
TEMPDIR=`mktemp -d scoretreematch.XXXXXX`;
awk -F" # " '{print $2}' $GOLD > $TEMPDIR/gold.score;
awk -F" # " '{print $2}' $DATA > $TEMPDIR/data.score;

diff -q $TEMPDIR/gold.score $TEMPDIR/data.score
if [ $? -eq 1 ]; then
    echo "Scores differ (compared in order)";
    rm -rf $TEMPDIR;
    exit 1;
fi
rm -rf $TEMPDIR
