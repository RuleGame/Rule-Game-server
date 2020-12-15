#!/bin/csh

#-- The directory where this script is
set sc=`dirname $0`
set h=`(cd $sc/..; pwd)`
source "$sc/set-var.sh"

java edu.wisc.game.rest.PlayerResponse  $argv[1-]



