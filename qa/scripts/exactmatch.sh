#!/usr/bin/env bash

# files should match (return 0). Diff to stderr and return 1 otherwise
A=$1;
B=$2;

RES=`diff $A $B | grep ^[0-9]`;
if [ -n "$RES" ]; then
    echo "$RES" 1>&2;
    exit 1;    
else
    exit 0;
fi;
