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

/** Tools for the manager dashboard. Essentially, a web interface for what MwByHumans command-line tool does.
 */

@Path("/ManagerDashboardService") 
public class ManagerDashboardService {
    private static HTMLFmter  fm = new HTMLFmter();

    @GET
    @Path("/compareRulesForHumans")
    @Produces(MediaType.TEXT_HTML)
    public String summary(@QueryParam("exp") List<String> _plans,
			  @DefaultValue("10") @QueryParam("targetStreak") int targetStreak,
			  @DefaultValue("0") @QueryParam("targetR") int targetR,
			  @DefaultValue("300") @QueryParam("defaultMStar") double defaultMStar,

			  @DefaultValue("Naive") @QueryParam("prec") String precString,
			  @DefaultValue("false") @QueryParam("mDagger") boolean useMDagger			  
			  
			  ) {

	
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

	    MwByHuman processor = new MwByHuman(precMode, targetStreak, targetR, defaultMStar, fm);

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

}
