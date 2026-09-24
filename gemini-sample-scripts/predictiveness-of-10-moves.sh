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
# good (5 boards without error)?"
# ---------------------------------------------------------------------

#-- if any arguments are supplied, they are passed to count-moves.pl, to scan
#-- logs from the specified directory trees, instead of the dot-rooted trees
# $argv[*]

#-- In each directory, create the counts.csv file and clean it up from the
#-- data irrelevant for this analysis (tmp.csv)
foreach x (gemini-play gemini-play-2 gemini-play-formal gemini-play-formal-2)
    echo "Processing runs in $x"
    (cd $x; /home/vmenkov/w2020/game/gemini-sample-scripts/count-moves.pl > counts.csv; grep -vw bak counts.csv | grep -v '^t[0-9]'| grep -v '/t[0-9]'     > tmp.csv)
    ls -l $x/tmp.csv
    wc $x/tmp.csv
end

#-- The runs from gemini-play-2 supersede the corresponding runs from gemini-play (some problems, higher resource bounds). So we replace the appropriate lines in gemini-play/tmp.csv with the matching lines gemini-play-2/tmp.csv.

#vmenkov-ThinkPad-X1-Yoga-Gen-5:~/gemini> grep gemini-special_spiralInward-seed gemini-play/counts.csv | sort
#./seed-01/gemini-special_spiralInward-seed1.txt,special_spiralInward,0,3,50,5/5
#./seed-02/gemini-special_spiralInward-seed2.txt,special_spiralInward,1,2,31,0/5
#./seed-03/gemini-special_spiralInward-seed3.txt,special_spiralInward,1,4,48,5/5
#./seed-04/gemini-special_spiralInward-seed4.txt,special_spiralInward,0,3,50,0/5
#./seed-05/gemini-special_spiralInward-seed5.txt,special_spiralInward,1,3,35,5/5
#vmenkov-ThinkPad-X1-Yoga-Gen-5:~/gemini> grep gemini-special_spiralInward-seed gemini-play-2/counts.csv | sort
#./gemini-special_spiralInward-seed1.txt,special_spiralInward,1,4,61,5/5
#./gemini-special_spiralInward-seed2.txt,special_spiralInward,1,2,31,0/5
#./gemini-special_spiralInward-seed3.txt,special_spiralInward,1,4,48,5/5
#./gemini-special_spiralInward-seed4.txt,special_spiralInward,1,4,69,2/5
#./gemini-special_spiralInward-seed5.txt,special_spiralInward,1,3,35,5/5

(cd gemini-play;  \
grep -v gemini-special_spiralInward-seed tmp.csv > tmp-1.csv; \
grep gemini-special_spiralInward-seed ../gemini-play-2/tmp.csv >> tmp-1.csv; \
mv tmp-1.csv tmp.csv)

#-- the data from gemini-play-formal-2 are added to those from gemini-play

grep -v '#path' gemini-play-formal-2/tmp.csv >> gemini-play-formal/tmp.csv

echo "These are the two groups of relevant runs:"
ls -l gemini-play/tmp.csv gemini-play-formal/tmp.csv
wc gemini-play/tmp.csv gemini-play-formal/tmp.csv

foreach x (gemini-play gemini-play-formal)
echo "# Summary for mastery-demonstrating runs for all problems in $x/tmp.csv"
grep ',1,' $x/tmp.csv | cut -d , -f 6 | sort | uniq -c

set f=$x-predict.dat
echo "# Summaries for mastery-demonstrating runs for individual problems">$f
grep ',1,' $x/tmp.csv |  cut -d , -f 2,6 | sort | uniq -c >$f
echo "See $f for details for this group of runs"
end

rm -f tmp.zip
zip tmp.zip README.txt */README.txt */counts.csv *-predict.dat
