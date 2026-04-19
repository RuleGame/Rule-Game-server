#!/bin/tcsh

grep -i 'Victory\|would have had\|Overall' seed-*/gemini-*-seed*.txt | perl -pe 's/seed.*gemini-(.*?)-(seed.*).txt:/$1 $2: /' > cmp.txt

grep Overall cmp.txt | grep -v '45/45' | sort > test.tmp
perl -pe 's/: Overall.*boards: (.*?), good moves: (.*?)/, B=$1 M=$2/' test.tmp > test.csv

grep 'would' cmp.txt | grep -v '2/2 epi' | grep -v '3/3 epi' | grep -v '4/4 epi'  | perl -pe 's/If.*had been played.*rules/... training .../' | sort> past.tmp
perl -pe 's|(.*?):.*?([0-9]+/[0-9]+) moves.*?([0-9]+/[0-9]+) episodes.*|$1, match B=$3 M=$2|' past.tmp > past.csv

grep overall cmp.txt | grep -v 45/45 | sort > future.tmp
perl -pe 's|(.*?):.*boards: ([0-9]+/[0-9]+), good moves.*?([0-9]+/[0-9]+).*|$1, B=$2 M=$3|' future.tmp > future.csv

cat test.tmp past.tmp future.tmp

/home/vmenkov/w2020/game/scripts/paste-csv.pl past.csv future.csv > a.csv
echo "RuleSet seed, Inferred rules vs training data, Test epi on inferred rules, Test epi on hidden rules"> head.csv
/home/vmenkov/w2020/game/scripts/paste-csv.pl a.csv test.csv|sort > a.csv
cat head.csv a.csv >> issues.csv

./classes.pl a.csv > b.csv
cat head.csv b.csv > classes.csv





