#!/bin/csh

set sc=~vmenkov/w2020/game/scripts

$sc/analyze-transcripts-mwh.sh -export tmp.csv 'pk/explore_1' -precMode EveryCond

# $sc/analyze-transcripts-mwh.sh -export tmp.csv 'pk/explore_1' -precMode EveryCond -target pk/position_A 

#-- the default alpha=0.05 results in no pairs selected by HB; so let's try
#-- a much larger alpha just for illustration
$sc/ecd.sh -import tmp.csv -target pk/position_A -alpha 0.5

$sc/ecd.sh -import tmp.csv -target vb/clockwiseTwoFree  -alpha 0.5

#-- A good example of 2 well-separated curves
$sc/ecd.sh -import tmp.csv -target pk/position_Anearby  -alpha 0.5


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
