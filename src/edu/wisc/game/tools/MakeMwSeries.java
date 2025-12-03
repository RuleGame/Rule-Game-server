package edu.wisc.game.tools;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;
import java.text.*;

import javax.persistence.*;

import edu.wisc.game.util.*;
import edu.wisc.game.rest.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.saved.*;
import edu.wisc.game.parser.RuleParseException;
import edu.wisc.game.math.*;
import edu.wisc.game.formatter.*;

import edu.wisc.game.sql.Episode.Move;
import edu.wisc.game.sql.Episode.CODE;
import edu.wisc.game.tools.MwSeries.MoveInfo;
import edu.wisc.game.tools.MwByHuman.PrecMode;
import edu.wisc.game.tools.AnalyzeTranscripts.P0andR;

/** An auxiliary class for MwByHuman and BuildCurves. Used to convert
    a section of the transcript file to a MwSeries object.
 */
class MakeMwSeries {
    
    /** Who learned what. (playerId : (ruleSetName: learned)). This is
	used to create detailed keys in the EveryCond mode.
     */
    private HashMap<String, HashMap<String, Boolean>> whoLearnedWhat = new HashMap<>();

    // Using these vars:  whoLearnedWhat ,
    final MwByHuman.PrecMode precMode;
    final int	targetStreak;
    final double  defaultMStar, targetR;
    final String target;
    
    MakeMwSeries(String _target, PrecMode _precMode, int _targetStreak, double _targetR, double _defaultMStar) {
	target = _target;
	precMode = _precMode;
	targetStreak = _targetStreak;
	targetR = _targetR;
	defaultMStar = _defaultMStar;
    }

    final boolean debug=false;

    /** Creates an MwSeries object for a (player,rule set)
	interaction, and adds it to savedMws, if appropriate.


	@param section All transcript data for one series of episodes
	(i.e. one rule set played by one player), split into
	subsections (one per episode)

        @param chosenMover If it's -1, the entire transcript is
	analyzed. (That's the case for 1PG and C2PG). In A2PG, it is 0
	or 1, and indicates which partner's record we want to extract
	from the transcript.

	@param needCurves If true, save move-by-move data in
	moveInfo. (That's only needed in BuildCurves.) Note that in
	MoveInfo data, successful picks are excluded from the record,
	as they are generally ignorable in the relevant analyses.


     */
    protected MwSeries mkMwSeries(Vector<TranscriptManager.ReadTranscriptData.Entry[]> section,
				  Vector<EpisodeHandle> includedEpisodes,
				  P0andR p0andR, int chosenMover,
				  boolean needCurves)
	throws  IOException, IllegalInputException,  RuleParseException {

	double rValues[] = p0andR.rValues;
	double p0[] = p0andR.p0;
	double mu[] = p0andR.mu;
	
	// this players successes and failures on the rules he's done
	EpisodeHandle eh = includedEpisodes.firstElement();
	MwSeries ser = new MwSeries(eh, null, chosenMover);

	HashMap<String,Boolean> whatILearned = whoLearnedWhat.get(ser.playerId);
	if (whatILearned == null) whoLearnedWhat.put(ser.playerId, whatILearned = new HashMap<>());

	boolean shouldRecord = (target==null) || eh.ruleSetName.equals(target);
	shouldRecord = shouldRecord && !(precMode == PrecMode.Naive && ser.precedingRules.size()>0);
	
	int je =0;
	int streak=0, maxStreak=0;
	double lastR = 0, maxR=0;
	
	
	//-- all attempts from the beginning until "mastery demonstrated"
	int attempts1=0;
	//-- attempts that are parts of the most recent success streak (including successful moves and successful picks)
	int attempts2=0;

	ser.errcnt = 0;
	ser.mStar = defaultMStar;
	if (precMode == PrecMode.EveryCond) {
	    ser.adjustPreceding( whatILearned);
	} else if (precMode == PrecMode.Ignore) {
	    ser.stripPreceding();
	}

	if (debug) System.out.println("Scoring");

	ser.totalMoves = 0;
	
	//int m =  Util.sumLen(section);
	//	if (needCurves) ser.moveInfo = new MoveInfo[m];
	Vector<MoveInfo> vmi = new Vector<>();

	// Is this episode part of a series in which a
	// learning-criterion incentive plan is in use?
	// If yes, then this is reflected in each Episode's xFactor 
	boolean mastery = includedEpisodes.get(0).para.getIncentive().mastery();
    
	// pointer to p0 and r. Increment in the beginning of each loop before use
	int k= -1; 
	int xFactor=0; // will be updated from the last episode
	for(TranscriptManager.ReadTranscriptData.Entry[] subsection: section) { // for each episode
	    eh = includedEpisodes.get(je ++);

	    int xNew =  eh.xFactor[chosenMover<0? 0: chosenMover];
	    if (xNew > xFactor) xFactor=xNew;

	    if (!ser.ruleSetName.equals(eh.ruleSetName)) {
		throw new IllegalArgumentException("Rule set name changed in the middle of a series");
	    }

	    // We will skip the rest of transcript for the rule set (i.e. this
	    // series) if the player has already demonstrated his
	    // mastery of this rule set
	    int j=0;
	    for(; j<subsection.length && !ser.learned; j++) {
		TranscriptManager.ReadTranscriptData.Entry e = subsection[j];
		if (!eh.episodeId.equals(e.eid)) throw new IllegalArgumentException("Array mismatch");
		k++;
		

		boolean wrongPlayer= (chosenMover>0) && (e.mover!=chosenMover);
		if (wrongPlayer) continue;

		//-- as of ver. 8.036 skip successful picks, since they would confuse plots
		if (!(e.pick instanceof Move) && e.code==CODE.ACCEPT) continue;

		
		ser.totalMoves++;
		if (needCurves) {
		    MoveInfo mi = new MoveInfo(e.code==CODE.ACCEPT, p0[k], mu[k]);
		    vmi.add(mi);
		}
		    
		attempts1++;
		if (e.code==CODE.ACCEPT) {
		    attempts2++;
		} else {
		    attempts2=0;
		}
		
		if (e.code==CODE.ACCEPT) {
		    if (e.pick instanceof Episode.Move) {
			streak++;
			if (lastR==0) lastR=1;
			lastR *=  rValues[k];
			if (streak > maxStreak) maxStreak = streak;
			if (lastR > maxR) maxR = lastR;


			if (debug) System.out.println("DEBUG: " + e.eid + "["+j+"], R *=" +rValues[k]+ "=" + lastR);
		    } else {
			if (debug) System.out.println("["+j+"] successful pick");
		    }
		} else {
		    streak = 0;
		    lastR = 0;
		    if (debug)  System.out.println("DEBUG["+j+"] R=" + lastR);
		    ser.errcnt ++;
		    ser.totalErrors++;
		}


		boolean learned =
		    (targetStreak>0 && streak>=targetStreak) ||
		    (targetR>0 && lastR>=targetR);
		
		if (learned) {
		    ser.learned=true;
		    //-- This was in effect through ver 8.028. After that, we switched to
		    //-- counting all move attempts, rather than errors
		    // ser.mStar = Math.min( ser.errcnt, ser.mStar);
		    ser.mStar = Math.min( attempts1 - attempts2 + 1, ser.mStar);
		}
		whatILearned.put(eh.ruleSetName, learned);
	    }

	    // Also count any errors that were made after the learning success
	    for(; j<subsection.length; j++) {
		k++;
		TranscriptManager.ReadTranscriptData.Entry e = subsection[j];
		if (!eh.episodeId.equals(e.eid)) throw new IllegalArgumentException("Array mismatch");

		boolean wrongPlayer= (chosenMover>0) && (e.mover!=chosenMover);
		if (wrongPlayer) continue;
		//-- as of ver. 8.036 skip successful picks, since they would confuse plots
		if (!(e.pick instanceof Move) && e.code==CODE.ACCEPT) continue;

		ser.totalMoves++;
		if (needCurves) {
		    MoveInfo mi = new MoveInfo(e.code==CODE.ACCEPT, p0[k],  mu[k]);
		    vmi.add(mi);
		}
		
		if (e.code!=CODE.ACCEPT) {
		    ser.totalErrors ++;
		}
	    }
	    
	    //	    ser.totalMoves += subsection.length;
	}

	if (needCurves) {
	    ser.moveInfo = vmi.toArray(new MoveInfo[0]);
	}

	// since ver 8.036, ignore empty series (which may occur in 2PG when one
	// player has not had a chance to play; or even in 1PG, when somebody
	// was just doing successful picks without any moves)
	if (ser.totalMoves==0) shouldRecord = false;
	// Do recording only after a successful adjustPreceding (if applicable)
	//if (shouldRecord) 		savedMws.add(ser);


	if (mastery)  checkMasteryMatch(ser, eh, xFactor, maxStreak, maxR);
	return shouldRecord? ser : null;
    }

    /** Check whether the "learning" bit we have set matches the mastery 
	criterion (xFactor==4 on the last episode of the series). */
    static private void checkMasteryMatch(MwSeries ser, EpisodeHandle eh, int xFactor,
				   int maxStreak, double maxR) {

	String s ="MASTERY("+eh.playerId+
	    ":" + eh.trialListId + ":" +  eh.seriesNo +
	    ":rule=" + eh.ruleSetName +")";
	String z = "xFactor=" + xFactor + "; streak=" + maxStreak+", r=" + maxR;

	if (ser.learned) {
	    if (xFactor==4) s=null; //s += ": match - both learned: " + z;
	    else s += ": mismatch: orig not learned, replay learned: " + z;
	} else {
	    if (xFactor==4) s += ": mismatch: orig learned, replay not learned: " + z;
	    else s=null; //s += ": match - none learned: " + z;
	}
	if (s!=null) 	System.out.println(s);
    }
}
