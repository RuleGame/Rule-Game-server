#!/bin/csh

#-- The directory where this script is
set sc=`dirname $0`
set h=`(cd $sc/..; pwd)`
source "$sc/set-var.sh"


# mkdir tmp
java edu.wisc.game.tools.AnalyzeTranscripts $argv[1] "$argv[2]"



