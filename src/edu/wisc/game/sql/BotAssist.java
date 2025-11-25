package edu.wisc.game.sql;

import java.io.*;
import java.util.*;
import java.text.*;

import edu.wisc.game.util.*;
import edu.wisc.game.pseudo.*;
import edu.wisc.game.rest.ParaSet;

import edu.wisc.game.sql.Episode.Pick;
import edu.wisc.game.sql.Episode.Move;

/** Data structures used for the Bot Assist functionality for a
    particular episode in Bot Assist games */
class BotAssist {

    final BotParams params;
    
    BotAssist(BotParams _params) {
	params = _params;
    }

    
/** In Bot Assist games, the list of all move suggestions made by the bot in this episode */

    /** The bot's proposed moves during this episode */
    Vector<Pick> botAssistTranscript = new Vector<>();
    public Vector<Pick> getBotAssistTranscript() { return botAssistTranscript; }

    /** The most recent proposed move. This is stored to make it
	easier for the subsequent move attempts to find out whether it
	follows the suggestion */
    Move proposed = null;

    /** Checks whether the specified move followed the previous suggestion,
	and if so, sets the didFollow bit in the Move object.
	@param move The object in which the "didFollow" flag is to be set if appropriate.
    */
    void didHeFollow(Pick move) {
	if (!(move instanceof Move)) return;
	if (proposed==null) return;
	if (proposed.sameMove(move)) move.setDidFollow(true);
    }

    /** The last chat message generate by this assistant. It is updated every time a new suggestion is generated. */
    private String chat=null;
    String getChat() { return chat; }

    
    /** Proposes one more move, adds it to the bot assist transcript,
	and stores it in "proposed". Creates a human-readable chat
	message to be sent to the player, saves it in this.chat, and returns it

	@return the new suggestion text that can be sent to the player
    */
    String makeSuggestion(EpisodeInfo epi)	throws IOException {
	// FIXME: once other bots exist, cast may fail.
	Pseudo task = new Pseudo((Pseudo.Params)params, epi.getPlayer(), epi, epi.getAttemptCnt());
	proposed = task.proposeMove();
	chat = null;
	if (proposed==null) {
	    chat = null; //"Bot has no idea";
	    Logging.info("BotAssist: no suggestion");
	} else {
	    botAssistTranscript.add(proposed);
	    chat = "I suggest moving piece " + proposed.getPiece().getLabel() + " to bucket " + proposed.getBucketNo();
	    int pc = (int)(task.confidence * 100);
	    chat += ". I am "+pc+"% confident in this move";
	    Logging.info("BotAssist: " + chat);
	}
	return chat;
    }
    
}
