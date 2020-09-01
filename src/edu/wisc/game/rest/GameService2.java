package edu.wisc.game.rest;

import java.io.*;
import java.util.*;
import javax.servlet.http.HttpServletResponse;
import javax.json.*;


import javax.ws.rs.*;
import javax.ws.rs.core.*;

// For database work
import javax.persistence.*;


// test
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;

import edu.wisc.game.util.*;
import edu.wisc.game.reflect.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.engine.*;

@Path("/GameService2") 

/** This API is set up in accordance with Kevin Mui's request, 2020-08-17.
 */
public class GameService2 {


    @POST
    @Path("/player") 
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public PlayerResponse player(@FormParam("playerId") String playerId){
	return new PlayerResponse( playerId);
    }


    @POST
    @Path("/newEpisode") 
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public NewEpisodeWrapper2
	newEpisode(@FormParam("playerId") String playerId) {
	return new NewEpisodeWrapper2(playerId);
    }

    
    @GET
    @Path("/display") 
    @Produces(MediaType.APPLICATION_JSON)
    public Episode.Display display(@QueryParam("episode") String episodeId)   {
	Episode epi = EpisodeInfo.locateEpisode(episodeId);
	if (epi==null) return dummyEpisode.new Display(Episode.CODE.NO_SUCH_EPISODE, "# Invalid episode ID");
	return epi.new Display(Episode.CODE.JUST_A_DISPLAY, "Display requested");
    }
  
    @POST
    @Path("/move") 
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Episode.Display move(@FormParam("episode") String episodeId,
				    @FormParam("x") int x,
				    @FormParam("y") int y,
				    @FormParam("bx") int bx,
				    @FormParam("by") int by,
				    @FormParam("cnt") int cnt
				    )   {
	EpisodeInfo epi = (EpisodeInfo)EpisodeInfo.locateEpisode(episodeId);
	if (epi==null) return dummyEpisode.new Display(Episode.CODE.NO_SUCH_EPISODE, "# Invalid episode ID: "+episodeId);
	try {	    
	    return epi.doMove(y,x,by,bx, cnt);
	} catch( Exception ex) {
	    System.err.print("/move: " + ex);
	    ex.printStackTrace(System.err);
	    return epi.new Display(Episode.CODE.INVALID_ARGUMENTS, ex.getMessage());
	}
    }

    Episode dummyEpisode = new Episode();

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
	activateBonus(@FormParam("playerId") String playerId,
		      @FormParam("seriesNo") int seriesNo) {
	return new  GiveUpWrapper(playerId, seriesNo);
    }

    
    @GET
    @Path("/debug") 
    @Produces(MediaType.APPLICATION_JSON)
    public PlayerResponse debug(@QueryParam("playerId") String playerId){
	return new PlayerResponse( playerId, true);
    }


    @POST 
    @Path("/guess")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public FileWriteReport guess(@FormParam("episode") String episodeId,
				 @FormParam("data") String text) {
	try {
	    EpisodeInfo epi = (EpisodeInfo)EpisodeInfo.locateEpisode(episodeId);
	    if (epi==null)  {
		return new FileWriteReport(true,"No such episode: " + episodeId);
	    }
	    PlayerInfo x = epi.getPlayer();
	    String pid = x.getPlayerId();

	    ParaSet para = x.getPara(epi);
	    String ruleSetName = para.getRuleSetName();

	    File d = new File("/opt/tomcat/saved/guesses");
	    if (d.exists()) {
		if (!d.isDirectory() || !d.canWrite())  throw new IOException("Not a writeable directory: " + d);
	    } else {
		if (!d.mkdirs())  throw new IOException("Failed to create directory: " + d);
	    }
	    
	    File f= new File(d, pid + ".csv");	    
	    PrintWriter w = new PrintWriter(new FileWriter(f, true));

	    
	    String ss[] = { pid, episodeId, ruleSetName, text};
	    String data = ImportCSV.escape(ss);
	    w.println(data);
	    w.close();	    
	    return  new	FileWriteReport(f, f.length());
	} catch(IOException ex) {
	    return  new	    FileWriteReport(true,ex.getMessage());
	}

    }

    
}
