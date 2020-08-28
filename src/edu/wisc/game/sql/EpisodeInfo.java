package edu.wisc.game.sql;

import java.io.*;
import java.util.*;
import java.text.*;
import java.net.*;
import javax.persistence.*;

import org.apache.openjpa.persistence.jdbc.*;

//import javax.xml.bind.annotation.XmlElement; 
//import javax.xml.bind.annotation.XmlRootElement;


import edu.wisc.game.util.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.parser.*;
import edu.wisc.game.rest.ParaSet;

/** Information about an episode stored in the SQL database.
 */

@Entity  
public class EpisodeInfo extends Episode {

    // Back link to the player, for JPA's use
    @ManyToOne(fetch = FetchType.EAGER)
    private PlayerInfo player;
    public PlayerInfo getPlayer() { return player; }
    public void setPlayer(PlayerInfo _player) { player = _player; }


    public static HashMap<String, Episode> globalAllEpisodes = new HashMap<>();
    public static Episode locateEpisode(String eid) {
	return globalAllEpisodes.get(eid);
    }
    
    //    statuc enum FinishCode {}
	
    
    Date endTime;
    int finishCode;
    /** Is this episode part of the bonus series? */
    boolean bonus;
    public boolean isBonus() { return bonus; }
    public void setBonus(boolean _bonus) { bonus = _bonus; }

    /** Set to true if this was a bonus-series episode, and the board was cleared
	quickly enough for the bonus series to continue. */
    boolean earnedBonus;
    /** The standard reward that has been given for this episode */
    int rewardMain;
    /** The bonus reward that has been given for this episode. This value normally
	appears in the last episode of a successful bonus subseries. */
    int rewardBonus;

    /** The total reward earned in this episode */
    int getTotalRewardEarned() { return rewardMain +  rewardBonus; }
    
    /** Indicates the number of the series (within a player's ser of
	episodes) to which this episode belongs. This is used during
	deserialization. */
    int seriesNo;
    public int getSeriesNo() { return seriesNo; }
    // @XmlElement
    public void setSeriesNo(int _seriesNo) { seriesNo = _seriesNo; }
    
    EpisodeInfo(Game game) {
	super(game, Episode.OutputMode.BRIEF, null, null);
    }
    
    /** Creates a new episode, whose rules and initial board are based (with 
	appropriate randomization) on a specified parameter set */
    static EpisodeInfo mkEpisodeInfo(int seriesNo, ParaSet para, boolean bonus)
	throws IOException, RuleParseException {
	//try {

	    String ruleSetName = (String)para.get("rule_id");
	    int nPieces =  Board.random.getInRange(para.getInt("min_objects"),
						   para.getInt("max_objects"));
	    int nShapes = Board.random.getInRange(para.getInt("min_shapes"),
						   para.getInt("max_shapes"));
	    int nColors = Board.random.getInRange(para.getInt("min_colors"),
						   para.getInt("max_colors"));

	    if (nPieces<=0 || nPieces>Board.N * Board.N) throw new IOException("Invalid #pieces=" + nPieces);
	    if (ruleSetName==null ||ruleSetName.trim().equals("")) throw new IOException("No rules set specified");
	    RuleSet rules = AllRuleSets.obtain(ruleSetName);

	   
	    if (nPieces <= 0)  throw new IOException("Number of pieces must be positive");
	    Game game = new  Game(rules, nPieces, nShapes, nColors);
	    EpisodeInfo epi = new EpisodeInfo(game);
	    epi.bonus = bonus;
	    epi.seriesNo = seriesNo;
	    	    
	    globalAllEpisodes.put(epi.episodeId, epi);
	    return epi;
	    
	    //setError( false);
	
	    //} catch(Exception ex) {
	    //	    setError(true);
	    //setErrmsg(ex.getMessage());
	    //	}	      
    }

    /** An episode deserves a bonus if it was part of the bonus series,
	has been completed, and was completed sufficiently quickly */
    boolean deservesBonus(double clearingThreshold ) {
	earnedBonus = earnedBonus ||
	    (bonus && cleared && attemptCnt <= getNPiecesStart() * clearingThreshold);
	return earnedBonus;
    }

    /** An episode was part of a bonus series, but has permanently failed to earn the
	bonus */
    boolean failedBonus(double clearingThreshold ) {
	return bonus && (givenUp || stalemate || cleared && !deservesBonus(clearingThreshold));
    }

    public Display doMove(int y, int x, int by, int bx, int _attemptCnt) {
	Display q = super.doMove(y, x, by, bx, _attemptCnt);
	q.setBonus(isBonus());
	if (isCompleted() && getPlayer()!=null) {
	    getPlayer().ended(this);
	}
	if (getPlayer()!=null) {
	    q.setTotalRewardEarned( getPlayer().getTotalRewardEarned());
	    q.setErrmsg(q.getErrmsg()+"\nDEBUG\n" + getPlayer().report());
	}	       
	return q;
    }

    /** Concise report, handy for debugging */
    public String report() {
	return "["+episodeId+"; FC="+getFinishCode()+"; "+(bonus?"B":"M")+" " + attemptCnt + "/"+getNPiecesStart()  + " $"+getTotalRewardEarned()+"]";
    }
    
}
