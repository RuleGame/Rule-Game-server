#!/bin/csh

#-----------------------------------------
#-- Usage:
#-- random-board.sh out-dir number-of-boards npieces [nshapes ncolors ['shapes-list' 'color-list']]
#-- The shape-list and color-list are semicolon-separated and single-quoted



#-- The directory where this script is
set sc=`dirname $0`
set h=`(cd $sc/..; pwd)`
source "$sc/set-var.sh"

java edu.wisc.game.sql.RandomGameGenerator  $argv[1-]



