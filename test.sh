#!/bin/csh

#-- The directory where this script is
set sc=`dirname $0`
set h=`(cd $sc; pwd)`
set g=`(cd $h/..; pwd)`

set jpa=/opt/apache-openjpa-3.1.0

set conn=/usr/share/java/mysql-connector-java-8.0.20.jar

java -cp $h/lib/game.jar:$g/jaxb-ri/mod/'*':$g/jaxrs-ri/api/'*':$g/jaxrs-ri/ext/'*':$g/jaxrs-ri/lib/'*':$g/jaxrs-ri/api/'*':$jpa/'*':$jpa/lib/'*':$conn edu.wisc.game.sql.Test

# java -cp /opt/tomcat/webapps/w2020/WEB-INF/lib/'*':$conn edu.wisc.game.sql.Test
  
