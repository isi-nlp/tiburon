# this is an awk script to verify the lines of a file. If a line is
#  incorrect its number is printed, and if the wrong number of lines
# is found this too is printed

# look for a start state on line 1 and rules with common lhs summing to 1

BEGIN { FS=" # "; }
# start state is line 1
NR==1 && $0 !~ /^[^ ]+[ \t\n\r]*$/ {print NR}
# other lines are well-formed rules
NR>1 && $1 !~ /^[^ ]+ -> / {print NR}
# sum weights by lhs
NR>1 && $1 ~ /^[^ ]+ -> / {
  split($1, a, " -> ");
#  printf("adding %f to %s\n", $2, a[1]);
  totals[a[1]]+=$2;
}
# count number of records
{records++}
END{
  # check record total
  if (records != 9) {printf("%s lines\n", records)}
  # check all weight totals
  for (item in totals) {
    if (totals[item] != 1) {printf("%s sums to %f\n", item, totals[item])}
  }
}

