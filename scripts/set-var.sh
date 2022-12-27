#-- $h is the main directory (~w2020/game)
set g=`(cd $h/..; pwd)`

# set jpa=/opt/apache-openjpa-3.1.0
set jpa=/opt/apache-openjpa

#-- commons-math3-3.6.1 is only needed for analyze-transcript.sh

setenv CLASSPATH $h/lib/'*':$g/jaxb-ri/mod/'*':$g/jaxrs-ri/api/'*':$g/jaxrs-ri/ext/'*':$g/jaxrs-ri/lib/'*':$g/jaxrs-ri/api/'*':$g/commons-math3-3.6.1/'*':$jpa/'*':$jpa/lib/'*':/opt/w2020/lib/mysql-connector-java.jar


  
