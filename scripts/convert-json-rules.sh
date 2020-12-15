#!/bin/csh

#-- The directory where this script is
set sc=`dirname $0`
set h=`(cd $sc/..; pwd)`
source "$sc/set-var-captive.sh"

mkdir tmp
java -Doutput=STANDARD edu.wisc.game.engine.ConvertRules ../tmp.json tmp


