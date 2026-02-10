#!/bin/csh

#-- This is a sample script that shows how the main Gemini Harness script,
#-- gemini.sh, can be used to run prepared-episodes queries.

#-- Your "main Gemini directory", under which you have unzipped captive.zip
#-- and rules.zip
set main=~/w2020

#-- The directory where gemini.sh is. This assumes that you have
#-- unzipped captive.zip under your $main. 
set scripts=$main/game/scripts

#-- The directory under which the subdirectories "rules" and "gemini" exist,
#-- containing sample rule files and sample gemini instructions files.
#-- This assumes that you have unzipped rules.zip under your $main.
set data=$main/game-data

#-- The Gemini model to use
set model=gemini-2.5-flash
# set model=gemini-3-flash-preview

#-- how many prepared episodes to generate and send to the bot
set p=5



#----------- Various rule sets in FDCL/basic --
# allOfColOrd_BRKY
# col1Ord_BRKY  sha1Ord_qcts
# shaOrdL1_qcts  sha1OrdBuck_tqsc0213  ordL1_Nearby ordL1
# allOfShaOrd_qcts cm_RBKY_cw_0123 colOrdL1_KBYR ordRevOfL1_Remotest
# sm_qcts  cw_0123 cw cm_RBKY allOfShaOrd_csqt  quadMixed1)
# allOfColOrd_KRBY buckets_2130 ccw cm_KRBY col1OrdBuck_BRKY0213 col1OrdBuck_BRKY3120 col1Ord_KRBY colOrdL1_BRKY
# ordRevOfL1_Nearby \
# ordRevOfL1
# quadNearby \
#sha1OrdBuck_qcts0213
# sha1Ord_csqt
#shaOrdL1_csqt 
#sm_csqt 

#-- names of the rule sets to try
foreach x ( colOrdL1_BRKY) 

    set t=`date +%y%m%d-%H%M`

    echo "Rule name = $x"
    #-- The rule set file and the instruction file. Change the locations
    #-- if desired
    set rule=$data/rules/FDCL/basic/$x.txt
    set ins=$data/gemini/pk/pk_system_gemini.txt
#    set ins=$data/gemini/system-prepared-positive-03.txt
    #-- the output file
    set out=prepared-${p}-${x}-$t.txt

    #-- This assumes that you have unzipped captive.zip under ~/w2020.
    #-- If it's in a different location, change accordingly
    $scripts/gemini.sh keyFile=$main/gemini-api-key.txt \
    $rule  9 max_requests=1 model=$model log=${x}-$t.csv log.nickname=$model log.run=0 seed=1   \
    instructionsFile=$ins    prepared_episodes=$p candidateCount=1 >& $out

#        max_boards=100 wait=1000
#   temperature=0
   
    grep -H Overall $out
	        
end



