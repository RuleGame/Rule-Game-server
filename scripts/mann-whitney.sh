#!/bin/csh

#-------------------------------------------------------------------------
#-- Sample usage
# scripts/mann-whitney.sh CMP_ALGOS Fake-10 alternateShape2Bucket_color2Bucket
# scripts/mann-whitney.sh CMP_RULES Fake-10 alternateShape2Bucket_color2Bucket
#-------------------------------------------------------------------------

#-- The directory where this script is
set sc=`dirname $0`
set h=`(cd $sc/..; pwd)`
source "$sc/set-var.sh"

java edu.wisc.game.math.MannWhitneyComparison $argv[1-]



