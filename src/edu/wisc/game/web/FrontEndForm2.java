package edu.wisc.game.web;

import jakarta.servlet.*;
import jakarta.servlet.http.*;


import java.util.*;
import java.text.*;

import edu.wisc.game.util.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.rest.*;


/** For Tanvi: helps the player to generate a unique playerID
*/
public class FrontEndForm2 extends ContextInfo   {

    /** Used in the ID */
    public String stamp = null;

    public static final DateFormat sdf = new SimpleDateFormat("yyyyMMdd-HHmmss");
    
    public FrontEndForm2(HttpServletRequest request, HttpServletResponse response){
	super(request,response);	
	if (error) return;
	if (exp==null) {
	    giveError("No experiment plan specified");
	    return;
	}
	if (!CheckPlanService.basicCheck(exp)) {
	    giveError("The experiment plan '"+exp+"' is likely bad. The experiment manager may want to <a href=\"check-plan-form.jsp?exp=" +exp+"\">check it with the validator</a>.");
	    return;
	}

	stamp =  sdf.format( new Date()) + "-" +Episode.randomWord(6);
		    
    }

}
