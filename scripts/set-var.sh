#------------------------------------------------------------------------------------------------
# This script is pulled into other csh scripts with the "source" command, in order to correctly
# set the CLASSPATH enviornment variable.
#------------------------------------------------------------------------------------------------

#-- $h is the main directory (~w2020/game)
set g=`(cd $h/..; pwd)`

# set jpa=/opt/apache-openjpa-3.1.0
set jpa=/opt/apache-openjpa

#-- commons-math3-3.6.1 is only needed for analyze-transcript.sh

set weblib=/opt/tomcat/webapps/w2020/WEB-INF/lib

if (-e $h/lib/game.jar) then
   # echo "Using local jar files"
   setenv CLASSPATH $h/lib/game.jar:$g/jaxb-ri/mod/'*':$g/jaxrs-ri/api/'*':$g/jaxrs-ri/ext/'*':$g/jaxrs-ri/lib/'*':$g/jaxrs-ri/api/'*':$g/commons-math3-3.6.1/'*':$jpa/'*':$jpa/lib/'*':/opt/w2020/lib/mysql-connector-java.jar

else if (-e $weblib/game.jar) then
   # echo "Using Tomcat-deployed jar files"
   setenv CLASSPATH $weblib/'*'
else
    echo "Don't know where to find JAR files; no game.jar found in either $h/lib or $weblib"
    exit
endif




  
