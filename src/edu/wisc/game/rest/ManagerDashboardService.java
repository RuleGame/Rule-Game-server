package edu.wisc.game.rest;

import java.io.*;
import java.util.*;
import java.text.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.json.*;

import javax.persistence.*;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import edu.wisc.game.util.*;
import edu.wisc.game.reflect.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.formatter.*;
import edu.wisc.game.math.*;
import edu.wisc.game.tools.MwByHuman;
import edu.wisc.game.tools.BuildCurves;

/** Tools for the manager dashboard. Essentially, a web interface for what MwByHumans command-line tool does.
 */

@Path("/ManagerDashboardService") 
public class ManagerDashboardService {
    private static HTMLFmter  fm = new HTMLFmter();

    @GET
    @Path("/compareRulesForHumans")
    @Produces(MediaType.TEXT_HTML)
    public String summary(@QueryParam("exp") List<String> _plans,
			  @DefaultValue("0") @QueryParam("targetStreak") int targetStreak,
			  @DefaultValue("0") @QueryParam("targetR") int targetR,
			  @DefaultValue("300") @QueryParam("defaultMStar") double defaultMStar,

			  @DefaultValue("Naive") @QueryParam("prec") String precString,
			  @DefaultValue("false") @QueryParam("mDagger") boolean useMDagger			  
			  
			  ) {


	if (targetStreak <=0 && targetR <=0) targetStreak = 10;

	
	MwByHuman.PrecMode precMode = Enum.valueOf(MwByHuman.PrecMode.class, 
						   precString);

	
	String title="", body="", errmsg = null;
	EntityManager em=null;

	try {
	    Vector<String> plans = new Vector<>();
	    plans.addAll(_plans);
	    Vector<String> pids = new Vector<>();
	    Vector<String> nicknames = new Vector<>();
	    Vector<Long> uids = new Vector<>();

	    title = "Comparing rule sets w.r.t. their difficulty for human players, using " + (useMDagger? "mStar" : "mDagger");

	    body += fm.para("Taking into account players assigned to the following experiment plans: " + fm.tt( String.join(", " , plans)));

	    MwByHuman processor = new MwByHuman(precMode, targetStreak, targetR, defaultMStar,
						ReplayedEpisode.RandomPlayer.COMPLETELY_RANDOM,
						fm);

	    // Extract the data from the transcript, and put them into savedMws
	    processor.processStage1(plans, pids, nicknames, uids);

	    // M-W test on the data from savedMws
	    processor.processStage2(false, useMDagger, null);

	    body += processor.getReport();
	    
	} catch(Exception ex) {
	    title = "Error";
	    body = fm.para(ex.toString());
	    ex.printStackTrace(System.err);
	} finally {	
	    if (em!=null) try {
		    em.close();
		} catch(Exception ex) {}
	}

	return fm.html(title, body);		 
    }

    //------------------------------------------------------------------
    @GET
    @Path("/buildCurves")
    @Produces(MediaType.TEXT_HTML)
    public String buildCurves(@QueryParam("exp") List<String> plans,
			      @DefaultValue("0") @QueryParam("targetStreak") int targetStreak,
			      @DefaultValue("0") @QueryParam("targetR") int targetR,
			      @DefaultValue("300") @QueryParam("defaultMStar") double defaultMStar,
			      
			      @DefaultValue("Naive") @QueryParam("prec") String precString,
			      @DefaultValue("false") @QueryParam("mDagger") boolean useMDagger,			  
			      @DefaultValue("COMPLETELY_RANDOM") @QueryParam("randomPlayerModel") String randomPlayerModelString,
			      @DefaultValue("false") @QueryParam("doRandom") boolean doRandom,
			      @DefaultValue("W") @QueryParam("curveMode") String curveModeString,
			      @DefaultValue("C") @QueryParam("curveArgMode") String curveArgModeString,
			      @DefaultValue("Real") @QueryParam("medianMode") String medianModeString,
			      @DefaultValue("false") @QueryParam("doAnn") boolean doAnn,
			      @DefaultValue("false") @QueryParam("doOverlap") boolean doOverlap
			  ) {


	
	if (targetStreak <=0 && targetR <=0) targetStreak = 10;


	MwByHuman.RunParams p = new 	MwByHuman.RunParams();
	p.plans.addAll( plans);
	p.targetStreak = targetStreak;
	p.targetR = targetR;	
	//	p.precMode = Enum.valueOf(MwByHuman.PrecMode.class, precString);
	p.precMode = MwByHuman.PrecMode.valueOf(precString);
	p.defaultMStar = defaultMStar;
	p.randomPlayerModel = ReplayedEpisode.RandomPlayer.valueOf1(randomPlayerModelString);

	p.doRandom = doRandom;
	p.curveMode = MwByHuman.CurveMode.valueOf(curveModeString);
	p.curveArgMode = MwByHuman.CurveArgMode.valueOf(curveArgModeString);
	p.medianMode = MwByHuman.MedianMode.valueOf(medianModeString);
	p.doAnn = doAnn;
	p.doOverlap = doOverlap;

	//randomMakeMwSeries = new MakeMwSeries(target,  PrecMode.Ignore,  300,  1e9, p.defaultMStar);


	BuildCurves processor = new BuildCurves(p, fm);

       	
	String title="", body="", errmsg = null;
	EntityManager em=null;

	try {
	    processor.processStage1(p.plans, p.pids, p.nicknames, p.uids);

	    /// zzz need a file under /opt/tomcat/webapps/tmp/xxxx
	    /// zzz need to return a list of files, to insert into HTML page
	    File webapps = new File("/opt/tomcat/webapps");
	    String appMain = "/tmp";
	    File app = new File(webapps, appMain);
	    final DateFormat sdf = new SimpleDateFormat("yyyyMMdd-HHmmss");	   
	    File d = new File(webapps, appMain  + "/run-" +   sdf.format( new Date()));
	    String msg = ". Please ask your webmaster to allow the Rule Game Server to create data directories under "+app;
	    if (d.exists()) {
		if (!d.isDirectory() || !d.canWrite())  throw new IOException("Not a writeable directory: " + d + msg);
	    } else if (!d.mkdirs()) {
		throw new IOException("Failed to create directory: " + d + msg);
	    }
 
	    // A map that maps e.g. "W_C" to a DataMap that stores SVG file of "W_C" for various experiences.

	    Vector<String> v = new Vector<>();
	    HashMap<String, BuildCurves.DataMap> bigMap =  processor.doCurves(d);
	    for(String y_x : bigMap.keySet()) {
		BuildCurves.DataMap h = bigMap.get(y_x);
		for(String key : h.keySet()) {
		    v.add(fm.h2(y_x + " curve " + key));
		    v.add(fm.para("File = " + h.get(key).file));
		}
	    }
	    //if (p.doPairs) {
	    //	processor.doPairCurves();
	    //}
	    body += Util.joinNonBlank("\n", v);

	} catch(Exception ex) {
	    title = "Error";
	    body = fm.para(ex.toString());
	    ex.printStackTrace(System.err);
	} finally {	
	    if (em!=null) try {
		    em.close();
		} catch(Exception ex) {}
	}


	
	return fm.html(title, body);		 
    }

}
