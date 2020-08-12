#!/bin/csh

#-- The directory where this script is
set sc=`dirname $0`
set h=`(cd $sc; pwd)`
set g=`(cd $h/..; pwd)`

set jpa=/opt/apache-openjpa-3.1.0

java -classpath $h/lib/'*':$g/jaxb-ri/mod/'*':$jpa/'*':$jpa/lib/'*' \
 edu.wisc.game.engine.RuleSet  $argv[1-]

