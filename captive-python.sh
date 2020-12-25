#!/bin/csh

#-- The directory where this script is
set sc=`dirname $0`
set h=`(cd $sc; pwd)`
source "$h/scripts/set-var-captive.sh"

$sc/python/client.py $sc/game-data/rules/rules-01.txt 5





