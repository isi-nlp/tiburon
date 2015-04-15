# this is an awk script to verify an rtg that may have slight state
# name variance

# look for:
# 1) a start state on line 1
# 2) 9 total lines
# 3) each rule line having 1.000000 as the weight
# 4) 2 lines with C, 1 with A, 2 with E, 1 with Y, 1 with B
#    and 1 with another state in the rhs
BEGIN { FS=" # "; cs=0; es=0; as=0; ys=0; bs=0; sts=0; }
# start state is line 1
NR==1 && $0 != "a" {print NR}
# other lines are well-formed rules with 1.000000 endings
NR>1 && ($1 !~ /^[^ ]+ -> / || $2 != 1.000000) {print NR}
# tally various labelings
NR>1 && $1 ~ /-> A\(/ {as++;}
NR>1 && $1 ~ /-> C\(/ {cs++;}
NR>1 && $1 ~ /-> B\(/ {bs++;}
NR>1 && $1 ~ /-> E$/ {es++;}
NR>1 && $1 ~ /-> Y$/ {ys++;}
NR>1 && $1 ~ /-> j$/ {sts++;}
# count number of records
{records++}
END{
  # check record total
  if (records != 9) {printf("%s lines\n", records)}
  # check labeling totals
  if (as != 1) {printf("%s rules with A; should be 1\n", as)}
  if (cs != 2) {printf("%s rules with C; should be 2\n", cs)}
  if (es != 2) {printf("%s rules with E; should be 2\n", es)}
  if (ys != 1) {printf("%s rules with Y; should be 1\n", ys)}
  if (bs != 1) {printf("%s rules with B; should be 1\n", bs)}
  if (sts != 1) {printf("%s rules with state; should be 1\n", sts)}
}
