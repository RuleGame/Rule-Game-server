package edu.wisc.game.tools;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;

import javax.persistence.*;

import java.sql.SQLException;


import org.apache.commons.math3.optim.*;
import org.apache.commons.math3.optim.nonlinear.scalar.*;
import org.apache.commons.math3.optim.nonlinear.scalar.gradient.*;

import edu.wisc.game.util.*;
import edu.wisc.game.rest.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.saved.*;
import edu.wisc.game.reflect.*;
import edu.wisc.game.parser.RuleParseException;



/** Auxiliary class for AnalyzeTranscripts. This is a map which, for each player, contains the list of episodes
	played by that player.
*/
class EpisodesByPlayer extends TreeMap<String,Vector<EpisodeHandle>> {

	/** for each rule set name, keep the list of all episodes */
	TreeMap<String, Vector<EpisodeHandle>> allHandles= new TreeMap<>();

	/** Adds to this map the data about all episodes played by a specified player.
	    @param p The player in question
	    @param  trialListMap Lists all trial lists of the relevant plan
	 */
	void doOnePlayer(PlayerInfo p,  AnalyzeTranscripts.TrialListMap trialListMap,
			 Vector<EpisodeHandle> handles) {
		
	    String trialListId = p.getTrialListId();
	    TrialList t = trialListMap.get( trialListId);
	    if (t==null) {
		System.out.println("ERROR: for player "+p.getPlayerId()+", no trial list is available for id=" +  trialListId +" any more");
		return;
	    }
	    int orderInSeries = 0;
	    int lastSeriesNo =0;
	    // ... and all episodes of each player
	    for(EpisodeInfo e: p.getAllEpisodes()) {
		int seriesNo = e.getSeriesNo();
		if (seriesNo != lastSeriesNo) orderInSeries = 0;
		EpisodeHandle eh = new EpisodeHandle(p.getExperimentPlan(), trialListId, t, p.getPlayerId(), e, orderInSeries);
		//		    handles.add(eh);
		handles.add(eh);
		Vector<EpisodeHandle> v = allHandles.get( eh.ruleSetName);
		if (v==null) allHandles.put(eh.ruleSetName,v=new Vector<>());
		v.add(eh);
	    
		Vector<EpisodeHandle> w = this.get(eh.playerId);
		if (w==null) this.put(eh.playerId, w=new Vector<>());
		w.add(eh);		     
		
		orderInSeries++;
		lastSeriesNo=seriesNo;
		
	    }
	}

	public String toString() {
	    Vector<String> v = new Vector<>();
	    for(String key: keySet()) {
		Vector<EpisodeHandle> w = get(key);
		v.add(key + ":" + Util.joinNonBlank(", ", w));
	    }
	    return  Util.joinNonBlank("; ", v);
	}
    }

