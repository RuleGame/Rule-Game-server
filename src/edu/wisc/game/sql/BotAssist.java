package edu.wisc.game.sql;

import java.io.*;
import java.util.*;
import java.text.*;

import edu.wisc.game.util.*;
import edu.wisc.game.pseudo.Pseudo;
import edu.wisc.game.rest.ParaSet;

import edu.wisc.game.sql.Episode.Pick;
import edu.wisc.game.sql.Episode.Move;

/** Data structures used in Bot Assist games */
class BotAssist {
    
/** In Bot Assist games, the list of all move suggestions made by the bot in this episode */
    //@Transient
    Vector<Pick> botAssistTranscript = new Vector<>();
    //@Transient
    public Vector<Pick> getBotAssistTranscript() { return botAssistTranscript; }

    /** In BotAssist, this is stored to make it easier for the subsequent move
	attempts to find out whether it follows the suggestion */
    //@Transient
    Move proposed = null;

    /** Checks whether the specified move followed the previous suggestion,
	and if so, sets the didFollow bit in the Move object */
    void didHeFollow(Pick move) {
	if (!(move instanceof Move)) return;
	if (proposed==null) return;
	if (proposed.sameMove(move)) move.setDidFollow(true);
    }
    
    void makeSuggestion(EpisodeInfo epi, EpisodeInfo.ExtendedDisplay q) throws IOException {
	Pseudo task = new Pseudo(epi.getPlayer(), epi, epi.getAttemptCnt());
	proposed = task.proposeMove();
	String chat = null;
	if (proposed==null) {
	    chat = null; //"Bot has no idea";
	} else {
	    botAssistTranscript.add(proposed);
	    chat = "I suggest moving piece " + proposed.getPiece().getLabel() + " to bucket " + proposed.getBucketNo();
	}
	q.setBotAssistChat(chat);
    }
    
}
