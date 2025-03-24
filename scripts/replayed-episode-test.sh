#!/bin/csh

#---------------------------------------------------------------------------------
# Usage:
#  ../w2020/game/scripts/replayed-episode-test.sh playerId
# example:
# scripts/replayed-episode-test.sh -in ../prolific-saved/ FDCL/basic prolific-671af6f9103727d607a138ab-66ad37dcfbd3ee90acd3897b
# scripts/replayed-episode-test.sh -in ../prolific-saved/ FDCL/basic prolific-671d01144718933aeb0729ef-5a3e5fb0b77a5000014a755a
#---------------------------------------------------------------------------------

#-- The directory where this script is
set sc=`dirname $0`
set h=`(cd $sc/..; pwd)`
source "$sc/set-var.sh"


# mkdir tmp
java edu.wisc.game.sql.ReplayedEpisode  $argv[1-]



