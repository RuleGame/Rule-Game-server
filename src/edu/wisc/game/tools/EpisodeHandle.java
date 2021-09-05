package edu.wisc.game.tools;

import java.io.*;
import java.util.*;


import edu.wisc.game.util.*;
import edu.wisc.game.rest.*;
import edu.wisc.game.sql.*;

/** An auxiliary structure used to keep track who and when played 
    episodes
*/
class EpisodeHandle {
    final String ruleSetName;
    final String exp;
    final String trialListId;
    final int seriesNo;
    final int orderInSeries;
    final String episodeId;
    final String playerId;
    final boolean useImages;
    
    public String toString() {return episodeId;}


    EpisodeHandle(String _exp, String _trialListId, TrialList t, String _playerId, EpisodeInfo e, int _orderInSeries) {
	    episodeId = e.getEpisodeId();

	    playerId = _playerId;
	    exp = _exp;
	    trialListId = _trialListId;
	    seriesNo= e.getSeriesNo();
	    orderInSeries =  _orderInSeries;
	    ParaSet para = t.get(seriesNo);
	    ruleSetName = para.getRuleSetName();
	    useImages = (para.images!=null);
    }
}

