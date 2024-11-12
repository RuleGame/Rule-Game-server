#!/bin/csh

#------------------------------
# Removes "successful picks" line from transcritps and detailed transcripts.
# Run this script in a directory such as /opt/w2020/saved
# The "filtered" files will go to the directories filtered.detailed-transcripts
# and filtered.transcripts
#------------------------------

mkdir filtered.detailed-transcripts

cd detailed-transcripts

foreach x (*.detailed-transcripts.csv)
    echo $x
    egrep -v ',,,,0,[0-9]+$' $x > ../filtered.detailed-transcripts/$x
end

mkdir filtered.transcripts

cd transcripts
foreach x (*.detailed-transcripts.csv)
    echo $x
    egrep -v ',,,,0$' $x > ../filtered.transcripts/$x
end
    
