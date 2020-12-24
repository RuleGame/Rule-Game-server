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
import edu.wisc.game.formatter.*;

/** The "Check my experiment plan" service
 */

@Path("/CheckPlanService") 
public class CheckPlanService extends GameService2 {
    private static HTMLFmter  fm = new HTMLFmter(null);

    @POST
    @Path("/checkPlan") 
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    /** @param exp The experiment plan. 
     */
    public String playerHtml( @FormParam("exp") String exp){
	exp = exp.trim();
	Vector<String> v = new Vector<>();
	int errcnt=0;
	
	String title = "Checking experiment plan " + exp;    
	String title1 = "Checking experiment plan " + fm.tt( exp);    

	v.add(fm.h1(title1));

	//---- color map 
	v.add(fm.h2("Checking the color map"));
	v.add(fm.para("Note: the same global color map is used for all experiment plans"));
	ColorMap cm = new ColorMap();

	Object o = cm.get("error");
	if (o!=null && o.equals(Boolean.TRUE)) {
	    v.add(fm.para("Error: " + cm.get("errmsg")));
	    errcnt ++;
	}
	Vector<String> rows = new Vector<>();
	for(String key: cm.keySet()) {
	    if (key.equals("error")||key.equals("errmsg")) continue;
	    String hexColor = "#" + cm.getHex(key,false);
	    //String hex1 = "#" + cm.getHex(key,true);
	    rows.add(fm.tr(fm.td(key) + fm.td("bgcolor=\"" + hexColor+"\"", fm.space(10))));
	}
	v.add( fm.table("border='1'", rows));
	v.add( fm.para("Loaded the total of " + rows.size()+ " colors"));

	//-- trial list
	v.add(fm.h2("Checking the trial lists"));
	try {
	    Vector<String> lists = TrialList.listTrialLists(exp);
	    v.add(fm.para("Found " + lists.size() + " trial lists for experiment plan " + fm.tt(exp)));
	    for(String key: lists) {
		v.add(fm.para("Checking trial list " + fm.tt(key)));
		TrialList trialList  = new TrialList(exp, key);
		int npara= trialList.size();
		v.add(fm.para("... the trial list has " + npara + " parameter sets"));
		int j=0;
		for( ParaSet para: trialList) {
		    j++;
		    v.add(fm.para("Checking para set no. " + j + " out of "+npara+"..."));
		    para.checkColors(cm);
		    
		    GameGenerator gg = GameGenerator.mkGameGenerator(para);
		    Game game = gg.nextGame();
		    //allSeries.add(new Series(para));

		    if (gg instanceof PredefinedBoardGameGenerator) {
			v.add(fm.para("Checking predefined boards..."));
			((PredefinedBoardGameGenerator)gg).checkShapesAndColors(cm);
		    }	    
		}
   
	       
	    }

	} catch(Exception ex) {
	    v.add(fm.para("Error: " + ex));
	    errcnt ++;
	}

	
	//-- put together
	v.add("<hr>");
	if (errcnt>0) {
	    v.add(fm.para("Found " + errcnt + " errors. You may want to fix them before inviting players into this experiment plan"));
	} else {
	    v.add(fm.para("Found no errors."));
	}

		
	String body = String.join("\n", v);

	return fm.html(title, body);	

    }

    

}
	
