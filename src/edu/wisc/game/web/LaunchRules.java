package edu.wisc.game.web;

import java.io.*;
import java.util.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;

import edu.wisc.game.util.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.formatter.*;
import edu.wisc.game.rest.*;


/** The Launch page that allows one to play all rule sets from a specific
    subdirectory of the rules directory, such as rules/APP, rules/BRM, or rules/CGS.
    
    As requested by Paul on 2021-10-12 and 2021-10-13, with an additional
    expansion (CGS) in the fall of 2022.

<pre>
  The M need not provide bonuses, and can use the standard 4 colors and shapes. I'd suggest either 5 to 8 pieces (a random number). People should be able to give up even at the first screen, if that is supported.
</pre>

*/
public class LaunchRules      extends LaunchRulesBase  {

    /** Generates the table for the APP or CGS launch page.
	@param request May contain rule=XXXX, to just use this one set (under the mode's rule directory)
	@param mode Which page to generate? We have several launch page for different audiences.
     */
    public LaunchRules(HttpServletRequest request, HttpServletResponse response, Mode mode)  {
	super(request,response);
	if (error || !loggedIn()) return;

	String chosenRuleSet = request.getParameter("rule");
	if (chosenRuleSet==null || chosenRuleSet.equals("null") ||
	    chosenRuleSet.trim().equals("")) {
	    chosenRuleSet = null;
	}
	
	
	String[] modsLong = {"APP/APP-no-feedback",
			     "APP/APP2-some-feedback",
			     "APP/APP2-more-feedback",
			     "APP/APP-max-feedback" };

	String[] modsShort = {"APP-short/APP-short-no-feedback",
			      "APP-short/APP2-short-some-feedback",
			      "APP-short/APP2-short-more-feedback",
			      "APP-short/APP-short-max-feedback" };

	String[] hm = {"No feedback",
		       "Some feedback",
		       "More feedback",
		       "Max feedback" };

	if (mode==Mode.CGS || mode==Mode.BRM) {
	    modsShort = null;
	    modsLong = new String[] {"APP/APP-no-feedback"};
	    hm = new String[] {""};
	}
    

	
	buildTable(modsShort, modsLong, hm, mode,  chosenRuleSet);
    }


}
