package edu.wisc.game.rest;

import java.io.*;
import java.util.*;
import java.text.*;
import java.util.concurrent.atomic.AtomicInteger;


import jakarta.servlet.http.HttpServletResponse;
import jakarta.json.*;


import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

// For database work
import javax.persistence.*;


// test
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;

import edu.wisc.game.util.*;
import edu.wisc.game.reflect.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.formatter.*;
import edu.wisc.game.web.LaunchRulesBase;

/** The "Second Batch" of API calls, primarily for use with players constrained by an experiment plan, and playing a sequence of games as outlined in the trial list to which the player is assigned. This API is set up in accordance with Kevin Mui's request, 2020-08-17.
 */

@Path("/GameService2") 
public class GameService2 {

        
    @POST
    @Path("/registerUser") 
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    /** Can be used by the Android app to register the application.
     */
    public UserResponse registerUser(@DefaultValue("null") @FormParam("email") String email,
				     @DefaultValue("null") @FormParam("nickname") String nickname,
				     @DefaultValue("false") @FormParam("anon") boolean anon)
    {

	return new UserResponse(email, nickname, null, anon);
    }

    
    
    @POST
    @Path("/player") 
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    /** @param exp The experiment plan. If not supplied (null), the experiment
	plan will be guessed from the playerId.
     */
    public PlayerResponse player(@DefaultValue("null") @FormParam("playerId") String playerId,
				 @DefaultValue("null") @FormParam("exp") String exp,
				 @DefaultValue("-1") @FormParam("uid") int uid
				 )

    {
	if (playerId!=null && playerId.equals("null")) playerId=null;
	return new PlayerResponse( playerId, exp, uid);
    }

    /** Changed to existing=false on 2022-11-04, due to an error reported by 
	Paul. */
    @POST
    @Path("/mostRecentEpisode") 
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public NewEpisodeWrapper2
	mostRecentEpisode(@FormParam("playerId") String playerId) {
	//return new NewEpisodeWrapper2(playerId, true, false, false);
	return new NewEpisodeWrapper2(playerId, false, false, false);
    }


    @POST
    @Path("/newEpisode") 
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public NewEpisodeWrapper2
	newEpisode(@FormParam("playerId") String playerId,
		   @DefaultValue("false") @FormParam("activateBonus") boolean activateBonus,
		   @DefaultValue("false") @FormParam("giveUp") boolean giveUp) {
	return new NewEpisodeWrapper2(playerId, false, activateBonus, giveUp);
    }

    
    @GET
    @Path("/display") 
    @Produces(MediaType.APPLICATION_JSON)
    public EpisodeInfo.ExtendedDisplay display(@DefaultValue("null") @QueryParam("playerId") String playerId,
					       @QueryParam("episode") String episodeId
					       					       )   {
	TimeInfo timed = new TimeInfo(episodeId);
	EpisodeInfo epi = EpisodeInfo.locateEpisode(episodeId);
	EpisodeInfo.ExtendedDisplay rv=null;
	try {
	    if (epi==null) return rv=dummyEpisode.dummyDisplay(Episode.CODE.NO_SUCH_EPISODE, "# Invalid episode ID");
	    //return epi.dummyDisplay(Episode.CODE.JUST_A_DISPLAY, "Display requested");
	    if (playerId!=null && playerId.equals("null")) playerId=null;
	    return rv = epi.mkDisplay(playerId);
	} finally {
	    Object ro = (rv==null)? "null" : JsonReflect.reflectToJSONObject(rv, true, null, 6);
	    String msg = "/display("+episodeId+") returning: "+ ro;
	    msg += timed.ending0();
	    Logging.info(msg);
	}


    }

    /** Instrumentation: do we run multiple /move, /pick, /display calls for the same player at the same time?
	It's not good...

	We use episodeId as the key, rather than playerId, for historical reasons. (PID was not passed before 
	GS 7.*)
     */
    static HashMap<String,AtomicInteger> callCnt = new HashMap<>();

    static private int callIn(String key) {
	AtomicInteger a = callCnt.get(key);
	if (a==null) {
	    synchronized(callCnt) {
		a = callCnt.get(key);
		if (a==null) callCnt.put(key, a = new AtomicInteger());
	    }
	}
	return a.incrementAndGet();
    }

    static private int callOut(String key) {
	AtomicInteger a = callCnt.get(key);
	return a.decrementAndGet();
    }
    

    
    static private DateFormat df = new SimpleDateFormat("HH:mm:ss.SSS");

    static class TimeInfo {
	final Date startTime = new Date();
	final int calls;
	final String  episodeId;
	TimeInfo(String _episodeId) {
	    episodeId = _episodeId;
	    calls = callIn(episodeId);
	}

	/** Timing at the end of the web API call + update the last activity time for the player.
	    (Only used on /move and /pick, and not on /display) */
	String ending(String playerId) {
	    updatedPlayerLastActivityTime(playerId);
	    return ending0();	    
	}

	/** Timing at the end of the web API call */
	String ending0() {
	    Date endTime = new Date();
	    
	    long spent = endTime.getTime() - startTime.getTime();
	    String msg = "time=" + spent + " msec (" + df.format(startTime) + " to " + df.format(endTime);
	    if (calls>1) msg += "; " + calls + " simultaneous calls";
	    callOut(episodeId);
	    return msg;
	}

	/** @param playerId May be null (it legacy code), or the actual mover (in 2PG) */
	private void updatedPlayerLastActivityTime(String playerId) {
	    EpisodeInfo epi = EpisodeInfo.locateEpisode(episodeId);
	    if (epi==null) return;
	    PlayerInfo p = epi.getPlayer();
	    if (playerId != null && !p.getPlayerId().equals(playerId)) {
		p = p.xgetPartner();
		if (p==null || !p.getPlayerId().equals(playerId)) return;
	    }
	    p.setLastActivityTime(startTime);
	}
	
    }	
    





    @POST
    @Path("/move") 
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public EpisodeInfo.ExtendedDisplay move(@DefaultValue("null") @FormParam("playerId") String playerId,
					    @FormParam("episode") String episodeId,
					    @DefaultValue("-1") @FormParam("x") int x, 
					    @DefaultValue("-1") @FormParam("y") int y,
					    @DefaultValue("-1") @FormParam("bx") int bx,
					    @DefaultValue("-1") @FormParam("by") int by,

					    @DefaultValue("-1") @FormParam("id") int pieceId,
					    @DefaultValue("-1") @FormParam("bid") int bucketId,
					    
					    @FormParam("cnt") int cnt
				)   {
	TimeInfo timed = new TimeInfo(episodeId);
	EpisodeInfo.ExtendedDisplay rv=null;
	EpisodeInfo epi = EpisodeInfo.locateEpisode(episodeId);
	if (epi==null) return dummyEpisode.dummyDisplay(Episode.CODE.NO_SUCH_EPISODE, "# Invalid episode ID: "+episodeId);
	if (playerId!=null && playerId.equals("null")) playerId=null;
	try {
	    if (pieceId >= 0) { // modern (GS 8)
		return rv=epi.doMove2(playerId, pieceId, bucketId, cnt);
	    } else { // legacy (GS 1 thru GS 7)
		return rv=epi.doMove(playerId, y,x,by,bx, cnt);
	    }
	} catch( Exception ex) {
	    System.err.print("/move: " + ex);
	    ex.printStackTrace(System.err);
	    return rv=epi.dummyDisplay(Episode.CODE.INVALID_ARGUMENTS, ex.getMessage());
	} finally {
	    Object ro = (rv==null)? "null" : JsonReflect.reflectToJSONObject(rv, true, null, 6);
	    String msg = "/move(epi=" +  episodeId +", ("+x+","+y+") to ("+bx+","+by+"), cnt="+cnt+"), return " + ro;
	    msg += timed.ending(playerId);
	    Logging.info(msg);
	}
    }

    @POST
    @Path("/pick") 
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public EpisodeInfo.ExtendedDisplay move(@DefaultValue("null") @FormParam("playerId") String playerId,
					    @FormParam("episode") String episodeId,
					    @DefaultValue("-1") @FormParam("x") int x, 
					    @DefaultValue("-1") @FormParam("y") int y,
					    @DefaultValue("-1") @FormParam("id") int pieceId,
					    @FormParam("cnt") int cnt
					    )   {
	TimeInfo timed = new TimeInfo(episodeId);
	EpisodeInfo.ExtendedDisplay rv=null;
	EpisodeInfo epi = EpisodeInfo.locateEpisode(episodeId);
	if (epi==null) return dummyEpisode.dummyDisplay(Episode.CODE.NO_SUCH_EPISODE, "# Invalid episode ID: "+episodeId);
	if (playerId!=null && playerId.equals("null")) playerId=null;
	try {	    
	    if (pieceId >= 0) { // modern (GS 8)
		return rv=epi.doPick2(playerId, pieceId, cnt);
	    } else { // legacy (GS 1 thru GS 7)
		return rv=epi.doPick(playerId, y,x, cnt);
	    }
	} catch( Exception ex) {
	    System.err.print("/pick: " + ex);
	    ex.printStackTrace(System.err);
	    return rv=epi.dummyDisplay(Episode.CODE.INVALID_ARGUMENTS, ex.getMessage());
	} finally {
	    Object ro = (rv==null)? "null" : JsonReflect.reflectToJSONObject(rv, true);
	    String msg = "/pick(epi=" +  episodeId +", ("+x+","+y+"), cnt="+cnt+"), return " + ro;
	    msg += timed.ending(playerId);
	    Logging.info(msg);
	}
    }

    
    private static EpisodeInfo dummyEpisode = new EpisodeInfo();

    @POST
    @Path("/activateBonus") 
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public  ActivateBonusWrapper
	activateBonus(@FormParam("playerId") String playerId) {
	return new  ActivateBonusWrapper(playerId);
    }

    @POST
    @Path("/giveUp") 
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public  GiveUpWrapper
	giveUp(@FormParam("playerId") String playerId,
		      @FormParam("seriesNo") int seriesNo) {
	return new  GiveUpWrapper(playerId);
    }


    //   @POST
    //    @Path("/player") 
    //    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    //    @Produces(MediaType.APPLICATION_JSON)
    

    /*
    @GET
    @Path("/debug") 
    @Produces(MediaType.APPLICATION_JSON)
    public PlayerResponse debug(@DefaultValue("null") @QueryParam("playerId") String playerId,
				@DefaultValue("null") @QueryParam("exp") String exp,
				@DefaultValue("-1") @FormParam("uid") int uid

				){
	return new PlayerResponse( playerId, exp, uid, true);
    }
    */

    /** Records a player's guess about the rules. This is typically used at the
	end of an episode.
    */
    @POST 
    @Path("/guess")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public FileWriteReport guess(@DefaultValue("null") @FormParam("playerId") String playerId,
				 @FormParam("episode") String episodeId,
				 @FormParam("data") String text,
				 @DefaultValue("-1") @FormParam("confidence") int confidence
				 ) {
	Logging.info("/guess(playerId=" + playerId +", epi=" + episodeId + ", text=" + text);
	if (playerId!=null && playerId.equals("null")) playerId=null;
	return GuessWriteReport.writeGuess( playerId, episodeId, text, confidence);
    }

    /** Returns a hash map that maps each color name (in upper case)
	to a vector of 3 integers, representing RGB values. The data 
	come from the file in game-data/colors. Note that calling this method
	causes the system to re-read the file; so the client may
	want to cache the data.
    */
    @GET
    @Path("/colorMap") 
    @Produces(MediaType.APPLICATION_JSON)
    public ColorMap  colorMap() {
	return new ColorMap();
    }

    /** Lists the names of all shapes. This can be used e.g. in a board editor. */
    @GET
    @Path("/listShapes") 
    @Produces(MediaType.APPLICATION_JSON)
    public  ListShapesWrapper listShapes() {
	return new ListShapesWrapper();
    }

    /** Reports the current version of the server */
    @GET
    @Path("/getVersion") 
    @Produces(MediaType.APPLICATION_JSON)
    public  String getVersion() {
	return Episode.getVersion();
    }

    @GET
    @Path("/findPlans") 
    @Produces(MediaType.APPLICATION_JSON)
    public LaunchRulesBase.AndroidRuleInfoReport findPlans(@DefaultValue("-1") @QueryParam("uid") int uid) {
	return new LaunchRulesBase.AndroidRuleInfoReport(uid);
    }

    
}
