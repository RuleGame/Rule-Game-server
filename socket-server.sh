#!/bin/csh

#-- The directory where this script is
set sc=`dirname $0`
set h=`(cd $sc; pwd)`
source "$h/scripts/set-var-captive.sh"

# -Doutput=STANDARD
java edu.wisc.game.engine.GameSocketServer  $argv[1-]


