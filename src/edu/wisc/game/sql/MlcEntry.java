package edu.wisc.game.sql;

import java.io.*;
import java.util.*;
import java.text.*;

import jakarta.json.*;
import javax.persistence.*;

import edu.wisc.game.util.*;

import jakarta.xml.bind.annotation.XmlElement; 

/** An MlcEntry contains the data for a (participant, ruleset, run)
    tuple. The data is condensed from the appropriate lines of CSV
    file submitted by an MLC participant.
 */

@Entity
public class MlcEntry {
     @Id 
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private long id;

    /* When this entry was uploaded to the server */
    Date uploadTime;    
  public Date getUploadTime() { return uploadTime; }
  @XmlElement
  public void setUploadTime(Date _uploadTime) { uploadTime = _uploadTime; }
    
    @Basic
    private String nickname;
  public String getNickname() { return nickname; }
   @XmlElement
   public void setNickname(String _nickname) { nickname = _nickname; }

    @Basic
    private String ruleSetName;
  public String getRuleSetName() { return ruleSetName; }
    @XmlElement
    public void setRuleSetName(String _ruleSetName) { ruleSetName = _ruleSetName; }

    @Basic
    private int runNo;
      public int getRunNo() { return runNo; }
  @XmlElement
  public void setRunNo(int _runNo) { runNo = _runNo; }
 
    @Basic
    private boolean learned;
   public boolean getLearned() { return learned; }
    @XmlElement
    public void setLearned(boolean _learned) { learned = _learned; }

    @Basic
    private int episodesUntilLearned;
  public int getEpisodesUntilLearned() { return episodesUntilLearned; }
   @XmlElement
   public void setEpisodesUntilLearned(int _episodesUntilLearned) { episodesUntilLearned = _episodesUntilLearned; }

    /** The total number of attempts (successful + failed) until learned, 
	or (if not learned) until the end of transcript. This is rounded up,
	as we have an episode-level granularity, rather than move-level.
     */
    @Basic
    private int movesUntilLearned;
 public int getMovesUntilLearned() { return movesUntilLearned; }
   @XmlElement
   public void setMovesUntilLearned(int _movesUntilLearned) { movesUntilLearned = _movesUntilLearned; }

    /** The total move attempts (including after the "full learning") point */
    @Basic
    private int totalMoves;
   public int getTotalMoves() { return totalMoves; }
   @XmlElement
   public void setTotalMoves(int _totalMoves) { totalMoves = _totalMoves; }

    /** The total number of errors */
    @Basic
    private int totalErrors;
   public int getTotalErrors() { return totalErrors; }
    @XmlElement
    public void setTotalErrors(int _totalErrors) { totalErrors = _totalErrors; }
    
    @Basic
    private int totalEpisodes;
    public int getTotalEpisodes() { return totalEpisodes; }
    @XmlElement
    public void setTotalEpisodes(int _totalEpisodes) { totalEpisodes = _totalEpisodes; }

  
    /** The total number of moves during the series of error-free episodes
	at the end of the transcript. This will be 0 if not even the last episode is error-free */
    @Basic
    private int endStreakMoves;
  public int getEndStreakMoves() { return endStreakMoves; }
    @XmlElement
    public void setEndStreakMoves(int _endStreakMoves) { endStreakMoves = _endStreakMoves; }

    @Basic
    private int endStreakEpisodes;
  public int getEndStreakEpisodes() { return endStreakEpisodes; }
    @XmlElement
    public void setEndStreakEpisodes(int _endStreakEpisodes) { endStreakEpisodes = _endStreakEpisodes; }

    
    public MlcEntry(String _nickname, String _ruleSetName, int _runNo, Date _uploadTime    
 ) {
	nickname = _nickname;
	ruleSetName = _ruleSetName;
	runNo = _runNo;
	uploadTime    =  _uploadTime;
	totalEpisodes = 0;
	endStreakEpisodes =  endStreakMoves = 0;
	learned = false;
	movesUntilLearned = 0;
    };

    public boolean matches(String _nickname, String _ruleSetName, int _runNo ) {
	return 	nickname.equals(_nickname) && ruleSetName.equals(_ruleSetName)
	    && 	runNo == _runNo;

    }

    /** How many error-free episodes must be found at the of the transcript
	to demonstrate "full learning" */
    final int REQUIRED_STREAK_EPISODES = 10;
    
    
    /** @param errmsg An empty StringBuffer, to which an error message can be appended if needed.
	@return true on success, false on error
     */
    public boolean addEpisode( int episodeNo,
			       int number_of_pieces,
			       int number_of_moves,
			       double move_acc,
			       boolean if_clear,
			       StringBuffer errmsg) {

	if (episodeNo!=getTotalEpisodes()) {
	    errmsg.append( "Unexpected episode number (board_id). Expected: " +getTotalEpisodes() + ", found " + episodeNo);
	    return false;
	}
	totalEpisodes ++;

	totalMoves += number_of_moves;

	int err = (int)Math.round(number_of_moves * (1.0 - move_acc));
	if (if_clear) {
	    if (err != number_of_moves-number_of_pieces) {
		errmsg.append( "Unexpected value of  move_acc=" + move_acc +". Expected " + number_of_pieces  +"/"+ number_of_moves + "=" + ((double)number_of_pieces/(double)number_of_moves) );
		 return false;
	    }
	}
		
	totalErrors += err;

	if (if_clear && err==0) {
	    // an error-free full-board-cleared episode
	    endStreakEpisodes ++;
	    endStreakMoves += number_of_moves;
	    learned = (endStreakEpisodes >= REQUIRED_STREAK_EPISODES);
	    
	} else {
	    endStreakEpisodes =  endStreakMoves = 0;
	    learned = false;
	    episodesUntilLearned = totalEpisodes;
	    movesUntilLearned = totalMoves;
	}
	return true;
	
    }
		
    
}

 
