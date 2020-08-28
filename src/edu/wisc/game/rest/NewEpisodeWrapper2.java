package edu.wisc.game.rest;

import java.io.*;
import java.util.*;
import javax.json.*;
import javax.persistence.*;


import javax.xml.bind.annotation.XmlElement; 
import javax.xml.bind.annotation.XmlRootElement;

import edu.wisc.game.util.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.sql.*;

//@XmlRootElement(name = "NewEpisode") 

/**
FIXME: need to add periodic purge on episodes 
 */
public class NewEpisodeWrapper2 extends ResponseBase {
    String episodeId=null;
    
    public String getEpisodeId() { return episodeId; }
    @XmlElement
    public void setEpisodeId(String _episodeId) { episodeId = _episodeId; }

    Board board = null;
    public Board getBoard() { return board; }
    @XmlElement
    public void setBoard(Board _b) { board = _b; }

    ParaSet para;
    public ParaSet getPara() { return para; }
    @XmlElement
    public void setPara(ParaSet _para) { para = _para; }

    /** The number of the current series (zero-based) among all series in the trial list.
	This can also be interpreted as the number of the preceding series that have been completed or given up by this player.
     */
    int seriesNo;
    /** The number of this episode within the current series (zero-based).
	This can also be interpreted as the number of the preceding episodes (completed or given up) in this series.
    */
    int episodeNo;
    public int getSeriesNo() { return seriesNo; }
    @XmlElement
    public void setSeriesNo(int _seriesNo) { seriesNo = _seriesNo; }
    public int getEpisodeNo() { return episodeNo; }
    @XmlElement
    public void setEpisodeNo(int _episodeNo) { episodeNo = _episodeNo; }

    /** The number of bonus episodes that have been completed (or given up) prior to
	the beginning of this episode. */
    int bonusEpisodeNo;
    public int getBonusEpisodeNo() { return bonusEpisodeNo; }
    @XmlElement
    public void setBonusEpisodeNo(int _bonusEpisodeNo) { bonusEpisodeNo = _bonusEpisodeNo; }

    
    /** This is set to true if an "Activate Bonus" button can be displayed,
	i.e. the player is eligible to start bonus episodes, but has not done that 
	yet */
    boolean canActivateBonus;
    public boolean getCanActivateBonus() { return canActivateBonus; }
   @XmlElement
   public void setCanActivateBonus(boolean _canActivateBonus) { canActivateBonus = _canActivateBonus; }
    
    boolean bonus;
    public boolean isBonus() { return bonus; }
    @XmlElement
    public void setBonus(boolean _bonus) { bonus = _bonus; }

    /** Totals for the player; only used in web GUI */
    int totalRewardEarned=0;
    public int getTotalRewardEarned() { return totalRewardEarned; }
    @XmlElement
    public void setTotalRewardEarned(int _totalRewardEarned) { totalRewardEarned = _totalRewardEarned; }

    NewEpisodeWrapper2(String pid) {
	try {
	    PlayerInfo x = PlayerResponse.findPlayerInfo(pid);
	    if (x==null) {
		setError(true);
		setErrmsg("Player not found: " + pid);
		return;
	    }
			    
	    EpisodeInfo epi = x.episodeToDo();
	    if (epi==null) {
		setError(true);
		setErrmsg("Failed to find or create episode!");
		return;	
	    }
	    seriesNo = epi.getSeriesNo();
	    episodeNo = x.seriesSize(seriesNo)-1;	    
	    board = epi.getCurrentBoard();
	    episodeId = epi.episodeId;

	    bonus = epi.isBonus();
	    bonusEpisodeNo = bonus? x.countBonusEpisodes(seriesNo)-1 : 0;

	    canActivateBonus = x.canActivateBonus();
	    totalRewardEarned = x.getTotalRewardEarned();
	    System.err.println("NewEpisodeWrapper2: canActivateBonus ="+canActivateBonus +", totalRewardEarned = " + totalRewardEarned);
	    
	    para = x.getPara(epi);

	    
	    setError( false);
	    setErrmsg("Debug:\n" + x.report());
	} catch(Exception ex) {
	    setError(true);
	    setErrmsg(ex.getMessage());
	    System.err.print(ex);
	    ex.printStackTrace(System.err);
	}      
	
    }
    
}
