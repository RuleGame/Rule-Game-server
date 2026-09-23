#!/bin/csh

#---------------------------------------------------------------------
# This script runs count-moves.pl on this dir tree or on the specified
# dir tree(s), and then we extract from the so obtained CSV file all
# lines with won=1, divide this set of lines into groups based on the
# rule set name and the value in the "testBoards" column, and count
# lines in each of these subset. This is used to answer Paul's question:
#
# "how predictive is 10 good moves in a row; that is, for what
# fraction of all the cases studied, is 10 good then followed by 45
# good (5 boars without error)?"
# ---------------------------------------------------------------------

#-- if any arguments are supplied, they are passed to count-moves.pl, to scan
#-- logs from the specified directory trees, instead of the dot-rooted trees
/home/vmenkov/w2020/game/gemini-sample-scripts/count-moves.pl $argv[*] > tmp.csv

echo "# Summary for mastery-demonstrating runs for all problems"
grep ',1,' tmp.csv | cut -d , -f 6 | sort | uniq -c

set f=tmp-3.dat
echo "# Summaries for mastery-demonstrating runs for individual problems">$f
grep ',1,' tmp.csv |  cut -d , -f 2,6 | sort | uniq -c >$f
echo "See $f for details"

