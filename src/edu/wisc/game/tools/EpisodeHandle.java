package edu.wisc.game.tools;

import java.io.*;
import java.util.*;

import edu.wisc.game.util.*;
import edu.wisc.game.rest.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.tools.AnalyzeTranscripts.TrialListMap;

/** An auxiliary structure containing an episode's metadata. (Most of
    them come from the episode's EpisodeInfo structure, completed with
    the context info from the relevant TrialList and PlayerInfo). It
    is used to keep track of who and when played which episodes.
*/
public class EpisodeHandle {
    final public String ruleSetName;
    /** Which other rules preceded this rule in the trial list? (This is just copied from the trial list) */
    final Vector<String> precedingRules;
    final String exp;
    final public String trialListId;
    final int seriesNo;
    final int orderInSeries;
    final public String episodeId;
    /** The player who played the episode (in 1PG), or Player 0 (in 2PG) */
    final String playerId;
    /** The partner's (Player 1's) playerId in adversarial 2PG, where
	we need to separate the two players' moves; null otherwise
	(i.e. in 1PG or C2PG) */
    final String neededPartnerPlayerId;
    final boolean useImages;
    final public ParaSet para;
    public String toString() {return episodeId;}


    EpisodeHandle(String _exp, String _trialListId, TrialList t,
		  String _playerId, String _neededPartnerPlayerId,
		  EpisodeInfo e, int _orderInSeries) {
	episodeId = e.getEpisodeId();
	
	playerId = _playerId;
	neededPartnerPlayerId = _neededPartnerPlayerId;
	exp = _exp;
	trialListId = _trialListId;
	seriesNo= e.getSeriesNo();
	orderInSeries =  _orderInSeries;
	para = t.get(seriesNo);
	ruleSetName = para.getRuleSetName();
	useImages = (para.imageGenerator!=null);
	    
	int seriesNo = e.getSeriesNo();
	precedingRules = new Vector<>();	
	for(int j=0; j<seriesNo; j++) {
	    precedingRules.add( t.get(j).getRuleSetName());
	}
    }

    /*

      	for(CsvData.LineEntry _e: csv.entries) {
	    CsvData.BasicLineEntry e= (CsvData.BasicLineEntry )_e;
	    h.put(rid,eid);
	}
    */

    /** Creates a somewhat incomplete EisodeHandle object based on a line of data from
	detailed transcript. This is used in unit testing without access to SQL server

	@param e A line from the detailed transcript:
		<pre>
	% more ./detailed-transcripts/RU-FDCL-basic-auto-20241105-113759-EPVDYK.detailed-transcripts.csv 
#playerId,trialListId,seriesNo,ruleId,episodeNo,episodeId,moveNo,timestamp,reactionTime,objectType,objectId,y,x,bucketId,by,bx,code,objectCnt
RU-FDCL-basic-auto-20241105-113759-EPVDYK,basic-07-A,0,FDCL/basic/ordL1,0,20241105-113932-D9DX8Y,0,20241105-113934.266,2.149,RED_SQUARE,0,1,1,,,,7,9
</pre>
    */
    public EpisodeHandle(String _exp, TrialListMap trialListMap, CsvData.BasicLineEntry e ) {
	exp = _exp;

	int j = 0;
	playerId = e.getCol(j++);	
	trialListId = 	e.getCol(j++);
	seriesNo= e.getColInt(j++);
	ruleSetName = e.getCol(j++);
	int episodeNo = e.getColInt(j++);
	episodeId = 	e.getCol(j++);

	neededPartnerPlayerId = null; // incorrect - FIXME

	TrialList t = trialListMap.get( trialListId);

	orderInSeries =  0; // incorrect - FIXME
	para = t.get(seriesNo);

	useImages = false; // incorrect - FIXME
	    
	//	int seriesNo = e.getSeriesNo();
	precedingRules = new Vector<>();	// incomplete - FIXME
	//for(int j=0; j<seriesNo; j++) {
	//  precedingRules.add( t.get(j).getRuleSetName());
	//}
    } 
}

