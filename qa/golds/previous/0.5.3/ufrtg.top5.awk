# this is an awk script to verify a top 5 file with mungible
# leaves
$0 !~ /^A\(B\(C\([EY] [EY]\)\) B\(C\([EY] [EY]\)\)\) \# 1.000000$/ {print NR}
{records++}
END{
    # check record total
  if (records != 5) {printf("%s lines\n", records)}
}
