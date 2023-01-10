#!/bin/csh

#-- The directory where this script is
set sc=`dirname $0`
set h=`(cd $sc/..; pwd)`
source "$sc/set-var.sh"

#-- For usage, see tools/analyze-transcripts-mwh.html

# mkdir tmp
java edu.wisc.game.tools.MwByHuman $argv[1-]



