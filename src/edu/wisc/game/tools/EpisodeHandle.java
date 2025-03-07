package edu.wisc.game.tools;

import java.io.*;
import java.util.*;

import edu.wisc.game.util.*;
import edu.wisc.game.rest.*;
import edu.wisc.game.sql.*;

/** An auxiliary structure containing an episode's metadata. (Most of
    them come from the episode's EpisodeInfo structure, completed with
    the context info from the relevant TrialList and PlayerInfo). It
    is used to keep track of who and when played which episodes.
*/
class EpisodeHandle {
    final String ruleSetName;
    /** Which other rules preceded this rule in the trial list? (This is just copied from the trial list) */
    final Vector<String> precedingRules;
    final String exp;
    final String trialListId;
    final int seriesNo;
    final int orderInSeries;
    final String episodeId;
    /** The player who played the episode. (Or Player 0 in 2PG) */
    final String playerId;
    /** Only set in adversarial 2PG, where we need to separate the two players'
	moves; null otherwise */
    final String neededPartnerPlayerId;
    final boolean useImages;
    final ParaSet para;
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
}

