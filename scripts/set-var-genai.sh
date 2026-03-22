#-- $h is the main directory (~/w2020/game)
set g=`(cd $h/..; pwd)`

set je=$h/lib/jaxrs-ri/ext

#echo je=$je
#ls $je

if (-e "$je") then
#-- compact directory tree, obtained by unzipping of captive.zip
   setenv CLASSPATH $h/lib/captive.jar:$je/'*'
   echo "Compact path: $CLASSPATH"
else
#-- usual arrangement on our server --       
    setenv CLASSPATH $h/lib/captive.jar:$g/genai-lib/'*':$g/jaxrs-ri/ext/'*'
    echo "Usual path: $CLASSPATH"
endif







  
