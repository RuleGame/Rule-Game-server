package edu.wisc.game.web;

import jakarta.servlet.*;
import jakarta.servlet.http.*;


import java.util.*;
import java.text.*;

import edu.wisc.game.util.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.rest.*;


/** For UW students: mandatory prefix for the playerID. Also, like form-2, helps generate a unique playerID
*/
public class FrontEndForm4 extends ContextInfo   {

    /** Used in the ID */
    public String stamp = null, prefix="";
    public boolean intro = true;

    public static final DateFormat sdf = new SimpleDateFormat("yyyyMMdd-HHmmss");
    
    public FrontEndForm4(HttpServletRequest request, HttpServletResponse response){
	super(request,response);	
	if (error) return;

	
	if (exp==null) {
	    //giveError("No experiment plan specified");
	    //return;
	} else {
	    if (!CheckPlanService.basicCheck(exp)) {
		giveError("The experiment plan '"+exp+"' is likely bad. The experiment manager may want to <a href=\"check-plan-form.jsp?exp=" +exp+"\">check it with the validator</a>.");
		return;
	    }
	}

	stamp =  sdf.format( new Date()) + "-" +Episode.randomWord(6);
	prefix=  request.getParameter("prefix");
	if (prefix==null || prefix.equals("null")) prefix = "";

	String s= request.getParameter("intro");
	intro  = (s==null) || (!s.equals("false"));


    }

}
