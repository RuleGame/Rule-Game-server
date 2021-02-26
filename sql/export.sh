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

#-- remove previously saved files
rm  /var/lib/mysql-files/tmp-*.csv

#-- export tables to files in  /var/lib/mysql-files/
mysql game < $h/export.sql

#-- Prepare headers
$h/get-header.pl PlayerInfo > PlayerInfo.csv
$h/get-header.pl Episode > Episode.csv
$h/get-header.pl Episode ",bonusActivate" > Episode-2.csv

#-- process bit fields and NULL values for readability
perl $h/post-export.pl /var/lib/mysql-files/tmp-PlayerInfo.csv >> PlayerInfo.csv
perl $h/post-export.pl /var/lib/mysql-files/tmp-Episode.csv >> Episode.csv
perl $h/post-export.pl /var/lib/mysql-files/tmp-Episode-2.csv >> Episode-2.csv


