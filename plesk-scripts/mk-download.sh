#!/bin/bash

#--------------------------------------------------------------
# This script is used to prepare the content in the directory
# webapps/download of the Tomcat server.
#--------------------------------------------------------------

if [ -d tomcat ]; then
    to=tomcat
elif [ -d /opt/tomcat ]; then
    to=/opt/tomcat
else
    echo "Don't know where tomcat lives on this host"
    exit 1
fi

wa=$to/webapps

if [ ! -d $wa ]; then
   echo "Could not find the weabapps directory '$wa'"
   exit 1
fi

cd $wa

if [ ! -d download ]; then
    mkdir download
    if [ ! $? -eq 0 ]; then
	echo "Failed to create directory 'download' in " `pwd`
	exit 1
    fi     
fi


index=download/index.html

echo '<html><head><title>Downloadable files</title></head>' > $index
echo '<body><h1>Downloadable files</h1><ul>' >> $index

for war in w2020.war rule-game.war
do
    if [ -f $war ]; then
	cp -pa $war download/
	if [ ! $? -eq 0 ]; then
	    echo "Failed to copy file $war to directory 'download'"
	else
	    echo "<li><a href=\"$war\">$war</a> " `(cd download; ls -l $war)` >> $index
	    ls -l download/$war
	fi     	
    else
	echo "Skipping non-existent file $war"
    fi
done

echo "</ul></body></html>" >> $index

(cd $download; pwd; ls -l )

echo "Produced the following index file:"
cat $index

