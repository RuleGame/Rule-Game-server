package edu.wisc.game.tools;

import java.io.*;
import java.util.*;
import java.text.*;

import edu.wisc.game.util.*;
import edu.wisc.game.rest.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.saved.*;
import edu.wisc.game.parser.RuleParseException;

import edu.wisc.game.sql.Episode.Pick;
import edu.wisc.game.sql.Episode.Move;
import edu.wisc.game.sql.Episode.CODE;
import edu.wisc.game.tools.MwSeries.MoveInfo;
import edu.wisc.game.tools.AnalyzeTranscripts.P0andR;
import edu.wisc.game.tools.MwByHuman.PrecMode;
     
class RandomPlay {

    /** Just used to create random player names */
    static int randomCnt = 0;
    
    /** Has random players play epsiodes with the rules and intiial boards as in includedEpisodes0
     */
    protected static Vector<MwSeries>
	randomPlay(Vector<TranscriptManager.ReadTranscriptData.Entry[]> subsections,
		   Vector<EpisodeHandle> includedEpisodes0,
		   int chosenMover,
		   ReplayedEpisode.RandomPlayer randomPlayerModel,
		   MakeMwSeries makeMwSeries
		   )
	throws  IOException, IllegalInputException,  RuleParseException,  CloneNotSupportedException {

	final int N = 3;

	Vector<MwSeries> result = new Vector<>();
	//Vector<TranscriptManager.ReadTranscriptData.Entry[]>[] result = new
	//    Vector<TranscriptManager.ReadTranscriptData.Entry[]>[N];

	EpisodeHandle eh0 = includedEpisodes0.firstElement();
	
	RuleSet rules = AllRuleSets.obtain( eh0.ruleSetName);

	// fixme: this does not include previous para sets from the same player
	HashMap <String,Boolean> useImages = new HashMap<>();
	for(EpisodeHandle eh: includedEpisodes0) {
	    useImages.put(eh.episodeId, eh.useImages);
	}

	File boardsFile =  Files.boardsFile( eh0.playerId, true);
	HashMap<String,Board> boards = BoardManager.readBoardFile(boardsFile, useImages);	

       
	for(int i=0; i<N; i++) {

	    Vector<TranscriptManager.ReadTranscriptData.Entry[]> randomSection = new Vector<>();
	    
	    Vector<Double> p0=new Vector<>(), r=new Vector<>();
	    int k=0;

	    Vector<EpisodeHandle> includedEpisodes = new Vector<>();
	    EpisodeHandle eh = eh0.clone();
	    String randomPlayerId = "random_" + (randomCnt++);

	    int je=0;
	    for(TranscriptManager.ReadTranscriptData.Entry[] subsection: subsections) {
		eh = includedEpisodes0.get(je++).clone();
		eh.playerId = randomPlayerId;
		includedEpisodes.add(eh);

		String episodeId0 = subsection[0].eid;
		String episodeId =  episodeId0 + "-random-" + i;

		//TranscriptManager.ExtraTranscriptInfo eti = new TranscriptManager.ExtraTranscriptInfo(eh);
	       
		
		Board board = boards.get(episodeId0);
		Game game = new Game(rules, board);
		ReplayedEpisode rep = new ReplayedEpisode(episodeId, eh.para, game, randomPlayerModel);

		Vector<TranscriptManager.ReadTranscriptData.Entry> w = new Vector<>();
		
		// play an episode with random moves
		int moveNo = 0;
		while(!rep.isCompleted()) {
		    Pick move = rep.generateRandomMove();
		    move.setMover(chosenMover);
		    p0.add( rep.computeP0(move, false));
	    
		    //-- replay the move/pick attempt 
		    int code = rep.accept(move);
		    r.add( move.getRValue());

		    TranscriptManager.ReadTranscriptData.Entry e = new TranscriptManager.ReadTranscriptData.Entry(eh, moveNo++, move);
		    w.add(e);
		}
		randomSection.add(w.toArray(new TranscriptManager.ReadTranscriptData.Entry[0]));
	    }

	    P0andR p0andR = new P0andR(p0,r);
	    MwSeries ser = makeMwSeries.mkMwSeries(randomSection, includedEpisodes, p0andR, chosenMover, true);

	    result.add(ser);
	    
	}
	return result;
    }

}
