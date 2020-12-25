#!/bin/csh

#-- The directory where this script is
set sc=`dirname $0`
set h=`(cd $sc; pwd)`
source "$h/scripts/set-var-captive.sh"

java -Doutput=FULL edu.wisc.game.engine.Captive  $argv[1-]



