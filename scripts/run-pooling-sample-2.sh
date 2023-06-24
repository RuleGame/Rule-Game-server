#!/bin/csh

set sc=~vmenkov/w2020/game/scripts

#--- Extract all data from the transcripts for a particular experiment plan (or plans)
$sc/analyze-transcripts-mwh.sh -export tmp.csv 'pk/explore_1' -precMode EveryCond  > prepare.txt

#---  ECD analysis for a particular target
$sc/ecd.sh -import tmp.csv -target pk/position_A -alpha 0.05 -beta 0.5 -sim KS  > pk-position_A-legend-KS.txt

$sc/ecd.sh -import tmp.csv -target pk/position_A -alpha 0.05 -beta 0.5 -sim MW  > pk-position_A-legend-MW.txt

$sc/ecd.sh -import tmp.csv -target pk/position_A -alpha 0.05 -beta 0.5 -sim Max  > pk-position_A-legend-Max.txt

$sc/ecd.sh -import tmp.csv -target pk/position_A -alpha 0.05 -beta 0.5 -sim Min  > pk-position_A-legend-Min.txt

#---  ECD analysis for another target
# $sc/ecd.sh -import tmp.csv -target vb/clockwiseTwoFree  -alpha 0.05 -beta 0.5 >  vb-clockwiseTwoFree-legend.txt

#----
# $sc/ecd.sh -import tmp.csv -target pk/position_Anearby  -alpha 0.05 -beta 0.5  >  pk-position_Anearby-legend.txt


#  1 pk/colOrd_nrnr,true
#   1 pk/colOrd_oneEach_shBckt,true
#   8 pk/col_oneEach_Ord_nearby,true
#  45 pk/position_A,true
#  43 pk/position_Anearby,true
#  29 pk/position_Aread,true
#  24 vb/clockwiseTwoFree,true
#   9 vb/clockwiseTwoFreeAlt,true
#  19 vb/clockwiseZeroStart,true
#  14 vb/shapesTwoFree,true
