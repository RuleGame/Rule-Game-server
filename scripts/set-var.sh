#-- $h is the main directory (~w2020/game)
set g=`(cd $h/..; pwd)`

set jpa=/opt/apache-openjpa-3.1.0

setenv CLASSPATH $h/lib/'*':$g/jaxb-ri/mod/'*':$g/jaxrs-ri/api/'*':$g/jaxrs-ri/ext/'*':$g/jaxrs-ri/lib/'*':$g/jaxrs-ri/api/'*':$jpa/'*':$jpa/lib/'*':/usr/share/java/mysql-connector-java-8.0.20.jar


  
