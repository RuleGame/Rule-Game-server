#!/bin/csh

#-- The directory where this script is
set sc=`dirname $0`
set h=`(cd $sc; pwd)`
source "$h/scripts/set-var-captive.sh"

$sc/python/client-socket.py localhost 7501 $sc/game-data/rules/rules-02.txt 5





