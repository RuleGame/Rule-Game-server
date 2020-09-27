#!/bin/csh

#-- The directory where this script is
set sc=`dirname $0`
set h=`(cd $sc; pwd)`
source "$h/set-var-captive.sh"

java -Dseed=1 -Doutput=FULL edu.wisc.game.engine.Captive  $argv[1-]



