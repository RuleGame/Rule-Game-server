#!/bin/csh

set sc=/home/vmenkov/w2020/game/scripts
set plan=teaching_all_orders 

mkdir tmp
# $sc/analyze-transcripts.sh  teaching_all_orders > tmp.log
# $sc/analyze-transcripts.sh -p0random teaching_all_orders > tmp.log
# $sc/analyze-transcripts.sh -p0mcp1 teaching_all_orders > tmp.log

cd tmp


cut -d , -f 12 summary-flat.csv  > n-flat.tmp
cut -d , -f 12 summary-p0-COMPLETELY_RANDOM.csv  > n-random.tmp
cut -d , -f 12 summary-p0-MCP1.csv  > n-mcp1.tmp

paste  n-flat.tmp n-mcp1.tmp |grep -v n|perl -pe '/(\d+)\s+(\d+)/; my $x=$1-$2; $_="$1 - $2 = $x\n";'  > n-diff.tmp

