package edu.wisc.game.web;

import java.io.*;
import java.util.*;
import java.text.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import javax.persistence.*;


import edu.wisc.game.util.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.formatter.*;
import edu.wisc.game.rest.*;
//import edu.wisc.game.parser.*;


/** The Launch page that allows one to play all rule sets from rules/APP.
    As requested by Paul on 2021-10-12 and 2021-10-13.

<pre>
  The M need not provide bonuses, and can use the standard 4 colors and shapes. I'd suggest either 5 to 8 pieces (a random number). People should be able to give up even at the first screen, if that is supported.
</pre>

*/
public class LaunchRules      extends LaunchRulesBase  {

    public LaunchRules(HttpServletRequest request, HttpServletResponse response)  {
	super(request,response);
	if (error || !loggedIn()) return;
	String[] modsLong = {"APP/APP-no-feedback",
			 "APP/APP2-some-feedback",
			 "APP/APP2-more-feedback",
			 "APP/APP-max-feedback" };

	String[] modsShort = {"APP-short/APP-short-no-feedback",
			  "APP-short/APP2-short-some-feedback",
			  "APP-short/APP2-short-more-feedback",
			  "APP-short/APP-short-max-feedback" };

	File launchFile = Files.getLaunchFileAPP();
	buildTable(modsShort, modsLong, "APP", launchFile);
    }


}
