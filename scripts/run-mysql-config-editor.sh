#!/bin/sh

#-----------------------------------------------------
# This script uses mysql_config_editor to create the "encrypted"
# (really, just obfuscated) file
# ~/.mylogin.cnf, which will help your mysql client to log in
# to some servers without password.
# The default port, 3306, does not need to be specified.
# The user will have to type the MySQL server' password when running
# this script.
# Guide:
# https://www.prisma.io/dataguide/mysql/tools/mysql-config-editor
# https://dev.mysql.com/doc/refman/8.0/en/mysql-config-editor.html
#-----------------------------------------------------

echo "You will be asked for the password for game@rulegame.wisc.edu"
mysql_config_editor set --login-path=rulegame \
--user=game \
--password \
--host=rulegame.wisc.edu 

echo "You will be asked for the password for game@wwwtest.rulegame.wisc.edu"
mysql_config_editor set --login-path=wwwtest.rulegame \
--user=game \
--password \
--host=wwwtest.rulegame.wisc.edu 

echo "You will be asked for the password for game@localhost"
mysql_config_editor set --login-path=local \
--user=game \
--password \
--host=localhost

echo "You will be asked for the password for replicator@localhost"
mysql_config_editor set --login-path=replicator \
--user=replicator \
--password \
--host=localhost

					    
