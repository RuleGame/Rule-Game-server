#!/bin/csh

#-- The directory where this script is
set sc=`dirname $0`
set h=`(cd $sc; pwd)`
set g=`(cd $h/..; pwd)`

set jpa=/opt/apache-openjpa-3.1.0

java -cp $h/lib/'*':$g/jaxb-ri/mod/'*':$g/jaxrs-ri/api/'*':$g/jaxrs-ri/ext/'*':$g/jaxrs-ri/lib/'*':$g/jaxrs-ri/api/'*':$jpa/'*':$jpa/lib/'*' edu.wisc.game.engine.Captive  $argv[1-] 
  
