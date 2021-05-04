#!/bin/csh
git pull origin master
/opt/ant/bin/ant javadoc war
cp ../w2020.war /opt/tomcat/webapps

