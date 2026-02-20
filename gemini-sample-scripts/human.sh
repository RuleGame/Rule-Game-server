#!/bin/csh

#set model=gemini-2.5-flash
set model=gemini-3-flash-preview

#----------- DONE --
# allOfColOrd_BRKY
# col1Ord_BRKY  sha1Ord_qcts
# shaOrdL1_qcts  sha1OrdBuck_tqsc0213  ordL1_Nearby ordL1
# allOfShaOrd_qcts cm_RBKY_cw_0123 colOrdL1_KBYR ordRevOfL1_Remotest
# sm_qcts  cw_0123 cw cm_RBKY allOfShaOrd_csqt  quadMixed1
# allOfColOrd_KRBY buckets_2130 ccw cm_KRBY col1OrdBuck_BRKY0213 col1OrdBuck_BRKY3120 col1Ord_KRBY colOrdL1_BRKY
# ordRevOfL1_Nearby ordRevOfL1 quadNearby sha1OrdBuck_qcts0213 sha1Ord_csqt shaOrdL1_csqt  sm_csqt 
foreach r (quadMixed1)
    echo "Rule $r"

#    set datadir=/home/vmenkov/w2020/tmp-1
    set datadir=.
    set trandir=$datadir/tmp/FDCL/basic/$r
    set xfile=$datadir/xFactor.csv

    set countNL=0
    set countL=0

    
    foreach transcript ($trandir/*.csv)
#    foreach transcript ($trandir/prolific-671d01144718933aeb0729ef-5fb570ad64809807fa926ccd.split-transcripts.csv)
	set player=`basename $transcript .split-transcripts.csv`
	
	echo "Using transcript file $transcript, player=$player"
        grep "$player,0," $xfile
	set xFactor=`grep "$player,0," $xfile | cut -d , -f 3`
	echo "xFactor=$xFactor"
	#exit
	
	set t=`date +%y%m%d-%H%M`

	set rule=/opt/w2020/game-data/rules/FDCL/basic/$r.txt
	set ins=/opt/w2020/game-data/gemini/system-prepared-03.txt
	set p=5

	set need=0
	if ("$xFactor" == "0") then
	    @ countNL = $countNL + 1
	    echo "countNL=$countNL";
	    if ($countNL <= 5) then
	       set need=1
	    endif
	else if    ("$xFactor" == "4") then
	    @ countL = $countL + 1
	    echo "countL=$countL";
	    if ($countL <= 5) then
	       set need=1
	    endif
	else
	endif

	if ("$need" == "1") then

	set out=prepared-${r}-x${xFactor}-${player}.txt	
	set log=log-${r}-x${xFactor}-${player}.csv	

	 /home/vmenkov/w2020/game/scripts/gemini.sh \
    $rule  9 max_requests=1 max_boards=100 wait=1000 model=$model log=$log log.nickname=$model log.run=0 seed=1  human=$transcript \
   instructionsFile=$ins candidateCount=1 xFactor=$xFactor>& $out
        echo "To file $out"
        endif

#   temperature=0

   
	grep -c Moving $out
	exit
     end

     echo "For rule $r, ran $countL learners, $countNL non-learners"
end



