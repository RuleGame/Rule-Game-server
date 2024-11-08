#!/bin/csh
#-----------------------------------------------------------------------
# This script is easier to use than export.sh, because it does not rely
# on MySQL and Linux permissions and restrictions. It uses a Java application
# to save the content of database tables into CSV files.
#
# To export other tables in the same fashion, try scripts/export-table.sh
# Usage: [-config configFile.conf]
#    scripts/export-2-any.sh -query "select count(*) from PlayerInfo" t.tmp
#
# scripts/export-2-any.sh "select experimentPlan, count(*) CNT from PlayerInfo group by experimentPlan" t.tmp
#
# Run this script in the directory where you want the output files to be produced.
#
# You need to specify the config name if you want to export tables not
# from the database named "game" (as specified in the default confg
# file, /opt/w2020/w2020.csv), but from some other database, whose
# name is specified in the config file produced by pull-remote-data.sh
# ---------------------------------------------------------------------

#-- The directory where this script is
set sc=`dirname $0`
set h=`(cd  $sc/..; pwd)`
source "$sc/set-var.sh"

# echo "cp=$CLASSPATH"

echo java edu.wisc.game.tools.ExportTable "$argv[1]" "$argv[2-]" $argv[3-]
java edu.wisc.game.tools.ExportTable  "$argv[1]"  "$argv[2-]" $argv[3-]
#echo java edu.wisc.game.tools.ExportTable  $@
#java edu.wisc.game.tools.ExportTable  $@



