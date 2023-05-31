#!/bin/csh

#-------------------------------------------------------------------------
#-- Sample usage
# scripts/label-map.sh "true.ruleA;true.ruleB" "false.ruleA"
#-------------------------------------------------------------------------

#-- The directory where this script is
set sc=`dirname $0`
set h=`(cd $sc/..; pwd)`
source "$sc/set-var.sh"

java edu.wisc.game.tools.pooling.LabelMap $argv[1-]



