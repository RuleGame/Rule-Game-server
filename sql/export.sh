#!/bin/csh
#-----------------------------------------------------------------------
# You may need to be a member of the UNIX user group "mysql" in order
# to successfully run all commands in this script. You can do "newgrp mysql"
# before running this script.
#
# You can use the UNIX command "groups" to see all groups to which you
# belong, and the "id" command to see as a member of which group you are
# working now.
#
# You also need to have a MySQL server account with the "FILE" privilege.
# Note that the MySQL server won't ask you for a MySQL password, because
# we have configured the MySQL server to allow login from your account
# based on its UNIX user name, using the 'auth_socket' plugin.
#
# For more info on exporting table data, see
# https://dev.mysql.com/doc/refman/5.7/en/select-into.html
#---------------------------------------------------------------------

#-- The directory where this script is
set sc=`dirname $0`
set h=`(cd $sc; pwd)`

#-- The temporary data dump directory (to which MySQL export will write, as per export.sql)
#-- The location should match the value given by 'SELECT @@secure_file_priv;'
#-- This is the location for our Linux machines. On MacOS, unfortunately, @@secure_file_priv is null,
#-- so this script won't work.
#
#-- If this script does not work on your machine, try scripts/export-table.sh

set dump=/var/lib/mysql-files

if (! -d $dump) then
   echo "Temporary directory $dump does not exist. Exiting"
   exit
endif

ls -ld $dump

echo "Using directory $dump for temporary files. You may want to remove them manually later, if this script fails to do so"
#-- Make the directory world-writeable, for the MySQL server to be able to write into it
# chmod a+rwX $dump

#-- remove previously saved files
rm  $dump/tmp-*.csv

#-- export tables from database "game" to files in  /var/lib/mysql-files/
mysql game < $h/export.sql

#-- Prepare headers
$h/get-header.pl PlayerInfo > PlayerInfo.csv
$h/get-header.pl Episode > Episode.csv
$h/get-header.pl Episode ",bonusActivate" > Episode-2.csv

#-- process bit fields and NULL values for readability
perl $h/post-export.pl $dump/tmp-PlayerInfo.csv >> PlayerInfo.csv
perl $h/post-export.pl $dump/tmp-Episode.csv >> Episode.csv
perl $h/post-export.pl $dump/tmp-Episode-2.csv >> Episode-2.csv


