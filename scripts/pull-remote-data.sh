#!/bin/csh


#----------------------------------------------------------------
# This script is used to pull accumulated data from a remote Game Server
# such as one of our Plesk hosts. The remote server needs to be properly
# set up, i.e. provided with the script mybin/zip-saved.sh
# Usage:
#   pull-remote-data wwwtest.rulegame
#   pull-remote-data rulegame
#----------------------------------------------------------------

#-- This variable refers to the name of one of the "login path"
#-- (host/database/user/password) combinations defined via
#-- scripts/run-mysql-config-editor.sh
set origin=$1

if ( "$origin" == "wwwtest.rulegame" ) then
   set u=test-rulegame
else if ( "$origin" == "rulegame" ) then
   set u=rulegame
else
    echo "Illegal origin=$origin. It should be either rulegame or wwwtest.rulegame"
    exit
endif

set h=${origin}.wisc.edu
set uh=${u}@${h}
set date=`date +'%Y_%m_%d'`

#-- The script named here needs to have been manually installed on the
#-- remote server
echo "Step 1: Requesting the remote host ($h) to prepare a ZIP file with data. You may be asked for the password of $uh, unless you have set up ssh-agent for password-free login"
ssh $uh mybin/zip-saved.sh

set tmpzip = tmp.zip

if (-e $tmpzip) then
   echo "Removing the old file $tmpzip"
   rm $tmpzip
endif


echo "Step 2: Attempting file transfer from the remote host ($h) by sftp. You may be asked for the password of $uh, unless you have set up ssh-agent for password-free login"
sftp $uh <<EOF
get $tmpzip
EOF

echo "If the file $tmpzip has been transferred, here it is:"
ls -l $tmpzip

if (! -e $tmpzip) then
   echo "Apparently, something wrong has happened, and the file $tmpzip has not been received"
   exit
endif

echo "Step 3: Unpacking the downloaded zip file"

set out=download-${date}
mkdir $out
mv $tmpzip $out
(cd $out; unzip $tmpzip)

echo "This is how much data are now in $out. That's all that's been downloaded"

du $out


#----------------------------------------------------------------------
# Pulling the entire content of a remote database to here.
# The "mysqldump" and "mysql" calls are expected to obtain
# host names and passwords from the "encrypted" (obfuscated)
# file ~/.mylogin.cnf, which has been created by running
# scripts/run-mysql-config-editor.sh
#
# For reference, see
# https://dev.mysql.com/doc/refman/8.0/en/mysqldump.html
#----------------------------------------------------------------------

# Or, using ~/.mylogin.cnf

set tmp=`echo $origin | sed 's/\./_/g'`
set newdb=game_${tmp}_${date}

echo "Part B: Copying the data from the remote database at $origin to new local database $newdb"

set dump=dump-game.sql

if (-e $dump) then
   rm $dump
endif
   
echo "Step 4: Exporting data from $origin to the temporary file $dump. This may take a few minutes"
mysqldump --login-path=$origin --no-tablespaces game > $dump
echo "Dumped remote server's data:"
dir dump-game.sql

echo "Step 5: Removing and recreating database $newdb if it already exists"
mysql --login-path=replicator <<EOF
drop database if exists $newdb;
create database $newdb;
GRANT ALL ON $newdb.* TO 'game'@'localhost';
EOF

echo "Step 6: Filling database $newdb from $dump"
mysql --login-path=replicator $newdb < $dump


echo "Step 7: Building the config file for use with analysis scripts"
set m0="/opt/w2020/w2020.conf"
set m1="w2020_$newdb.conf"

grep -qw FILES_SAVED $m0
if ($?) then
    echo "The master config file $m0 has no line for  FILES_SAVED"
    exit
endif

grep -qw JDBC_DATABASE $m0
if ($?) then
    echo "The master config file $m0 has no line for JDBC_DATABASE"
    exit
endif

      


if (! -r $m0) then
   echo "The master Game Server config script, $m0, does not exist on this machine. Please create it, so that I will know what password to put into the config script for this data set"
   exit
endif

#-- FILES_SAVED =  "/opt/w2020/saved";

set d=`(cd $out; pwd)`/opt/w2020/saved


cat <<EOF > $m1
#---------------------------------------------------------------------------
#-- This is the config file to use with analysis scripts working with
#-- the data that were imported into the database $newdb,
#-- with the data files in $d.
#-- In this way, the analysis script would use these
#-- downloaded data, and not the locally accumulated data.
#-- 
#-- If you move the data directory, you must modify this config file accordingly,
#---------------------------------------------------------------------------
EOF

grep -v '^#' $m0 > t.tmp


sed -E 's|^(FILES_SAVED).*|\1="'"$d"'";|; s|^(JDBC_DATABASE).*|\1="'"$newdb"'";|' t.tmp >> $m1


echo "This is the new config file, $m1, which you should use for the analysis of downloaded data:"

ls -l $m1

echo "The two important lines should be shown below:"
grep 'FILES_SAVED\|JDBC_DATABASE' $m1
