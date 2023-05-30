#!/bin/csh

#-------------------------------------------------------------------------
#-- Sample usage
# scripts/mann-whitney.sh -mode CMP_ALGOS -rule alternateShape2Bucket_color2Bucket -csvOut tmp
# scripts/mann-whitney.sh -mode CMP_RULES -algo Fake-10  -csvOut tmp
#-------------------------------------------------------------------------

#-- The directory where this script is
set sc=`dirname $0`
set h=`(cd $sc/..; pwd)`
source "$sc/set-var.sh"

java edu.wisc.game.tools.pooling.Ecd $argv[1-]



