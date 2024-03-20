#!/bin/csh
#-----------------------------------------------------------------------
# This script is easier to use than export.sh, because it does not rely
# on MySQL and Linux permissions and restrictions. It uses a Java application
# to save the content of database tables into CSV files.
#
# To export other tables in the same fashion, try scripts/export-table.sh
# Usage: export-2.sh [-config configFile.conf]
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

foreach t (PlayerInfo Episode)
    echo "Saving table $t into file ${t}.csv"
    java edu.wisc.game.tools.ExportTable $t ${t}.csv  $argv[1-]
end


