#!/bin/csh


#----------------------------------------------------------------
# This script is used to build a local copy of a dataset imported
# via a ZIP file that contains both SQL and CSV files. Such a
# ZIP file is produced by running pull-remote-data.sh
#
# For example, if you want to create on your laptop (which runs
# MySQL) a data set containing a snapshot of the data from a Plesk
# host, such as rulegame.wisc.edu,
# but you don't have a UW netid, you can do it in 2 steps, as follows.
#
# 1) On the CAE host, pull the data from the Plesk host:
#
#   pull-remote-data rulegame
#
# This will create a ZIP file with everything in it, with a name such as
# download-2024_02_01_193059.zip
#
# 2) Transfer that ZIP file to your laptop using sftp
#
# 3) On your laptop, choose a directory under which you want to house the
# imported files, place the ZIP file into that directory, and run this
# import script, e.g.
#   import-from-zip.sh download-2024_02_01_193059.zip
#----------------------------------------------------------------

#--- the zip file to be unpacked
set oz=$1

if ("$oz" ==  "") then
   echo "Please specify the ZIP file to import data from"
   exit
endif

if (! -e $oz) then
   echo "The specified ZIP file does not exist: $oz"
   exit
endif

echo "Will import the data from file ${oz}:"
ls -l $oz

#========================================================================
echo "Step 1: Unpacking the downloaded zip file"

set out=`basename $oz .zip`
unzip -o $oz

if (! -d $out) then
  echo "Something went wrong: the directory named $out has not been created"
  exit
endif

#-- Locating the imported config file
echo "Looking for a config file with a name pattern matching $out/"'*.conf.orig'
set mi=$out/*.conf.orig

if ("$#mi" != "1") then
   echo "It appears that the directory $out contains multiple files matching the pattern "'*.conf.orig'". Don't know which one to use; exiting"
   exit
endif

echo "Assuming that the imported data are described in the config file $mi"

#-- Determining the desired database name
set newdb=`grep JDBC_DATABASE $mi | sed 's/.*=//; s/ *//g; s/"//g; s/;//'`

if ("$newdb" == "") then
   echo "Failed to extract the database name from $mi. Exiting"
   exit
endif

echo "Will be using $newdb as the new database name"

set dump=$out/dump-game.sql

if (! -e $dump) then
   echo "The SQL file does not exist: $dump. Exiting"
   exit
endif

echo "Will be importing SQL data from file $dump"


#========================================================================
echo "Step 2: Removing and recreating database $newdb if it already exists"
mysql --login-path=replicator <<EOF
drop database if exists $newdb;
create database $newdb;
GRANT ALL ON $newdb.* TO 'game'@'localhost';
EOF

#========================================================================
echo "Step 3: Filling database $newdb from $dump"
mysql --login-path=replicator $newdb < $dump

#========================================================================
echo "Step 4: Building the config file for use with analysis scripts"

set m0="/opt/w2020/w2020.conf"
set m1=`basename $mi .orig`

#-- Determining the old saved-files locations
set oldsaved=`grep FILES_SAVED $mi | sed 's/.*=//; s/ *//g; s/"//g; s/;//'`


# FILES_SAVED="/Users/vmenkov/w2020/game/tmp/download-2024_02_01/opt/w2020/saved";

#-- The new saved-files location
set d=`(cd $out; pwd)`/opt/w2020/saved
set masterConf=/opt/w2020/w2020.conf

if (! -r $m0) then
   echo "The master Game Server config script, $m0, does not exist on this machine. Please create it, so that I will know what password to put into the config script for this data set"
   exit
endif

grep -qw FILES_SAVED $m0
if ($?) then
    echo "The master config file $m0 has no line for  FILES_SAVED"
    exit
endif

grep -qw JDBC_PASSWORD $m0
if ($?) then
    echo "The master config file $m0 has no line for JDBC_PASSWORD"
    exit
endif


echo "Replacing references to $oldsaved with $d, and inserting the DB password from "$m0

sed "s|$oldsaved|$d|" $mi | grep -v JDBC_PASSWORD > $m1
grep JDBC_PASSWORD $m0 >> $m1


#========================================================================
echo "================== IMPORTANT MESSAGE ==========================="

echo "This is the new config file, $m1, which you should use for the analysis of downloaded data:"

ls -l $m1

echo "The two important lines should be shown below:"
grep 'FILES_SAVED\|JDBC_DATABASE' $m1



exit

#####################################

#========================================================================
echo "Step 7: Building the config file for use with analysis scripts"
set m1="w2020_$newdb.conf"


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
#-- adjusting the value of FILE_SAVED as appropriate
#---------------------------------------------------------------------------
EOF

grep -v '^#' $m0 > t.tmp


sed -E 's|^(FILES_SAVED).*|\1="'"$d"'";|; s|^(JDBC_DATABASE).*|\1="'"$newdb"'";|' t.tmp >> $m1


