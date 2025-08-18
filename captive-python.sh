#!/bin/csh

#-- The directory where this script is
set sc=`dirname $0`
set h=`(cd $sc; pwd)`
source "$h/scripts/set-var-captive.sh"

$sc/python/clientGemini.py $sc/game-data/rules/MLC/BMK/counterClockwise.txt 9



# 9 pieces 2 traiining episodes 3 test episodes

