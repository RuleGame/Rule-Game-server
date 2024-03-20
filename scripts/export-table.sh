#!/bin/csh

#--------------------------------------------------------------------
# Usage:
#  export-table.sh [-config /opt/w2020/w2020.conf] tableName outputFile.csv
#--------------------------------------------------------------------

#-- The directory where this script is
set sc=`dirname $0`
set h=`(cd $sc/..; pwd)`
source "$sc/set-var.sh"



java edu.wisc.game.tools.ExportTable $argv[1-]



