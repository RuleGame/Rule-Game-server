#!/bin/csh

#---------------------------------------------------------------------------------
# Usage example:
#  ../w2020/game/scripts/export-transcripts.sh -pid -file prolific_export_671*.csv
#  ../w2020/game/scripts/export-transcripts.sh -plan 'FDCL/basic'
#---------------------------------------------------------------------------------

#-- The directory where this script is
set sc=`dirname $0`
set h=`(cd $sc/..; pwd)`
source "$sc/set-var.sh"


# mkdir tmp
java edu.wisc.game.tools.AnalyzeTranscripts -jf $argv[1-]



