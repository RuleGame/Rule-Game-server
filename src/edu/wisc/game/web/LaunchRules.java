package edu.wisc.game.web;

import java.io.*;
import java.util.*;
import java.text.*;
import javax.servlet.*;
import javax.servlet.http.*;
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
	
	EntityManager em = Main.getNewEM();
	try {
	    HashMap<String,Vector<PlayerInfo>> allPlayers = findPlayers( em,  new Integer(uid));

	    String[] allRuleNames = Files.listAllRuleSetsInTree("APP");

	    Vector<String> rows = new Vector<>();

	    String[] hm = {"No feedback",
			   "Some feedback",
			   "More feedback",
			   "Max feedback" };
	    String[] mods = {"APP/APP-no-feedback",
			     "APP/APP-some-feedback",
			     "APP/APP-more-feedback",
			     "APP/APP-max-feedback" };

	    rows.add(fm.tr("<TH rowspan=2>Rule Set</TH><TH colspan="+hm.length+">Actions</TH>"));



	    Vector<String> cells = new Vector<>();
	    for(String h: hm) cells.add(fm.th(h));	    
	    rows.add(fm.tr(String.join("",cells)));
	    
	    for(String r:  allRuleNames) {

		RuleSet ruleSet = AllRuleSets.obtain(r);
		String descr = String.join("<br>", ruleSet.description);

		cells.clear();
		String text = fm.tt(r) + " " + descr;
		cells.add( fm.td(text));


		for(String mod: mods) {
		    String exp = "R:" + r + ":"+mod;	
		    Vector<PlayerInfo> players = allPlayers.get(exp);	    
		    Vector<String> pv = new Vector<>();

		    int buttonCnt=0;
		    if (players!=null) {
			for(PlayerInfo p: players) {
			    String t;
			    int ne = p.getAllEpisodes().size();
			    if (p.getCompletionCode()!=null) {
				t = "[COMPLETED ROUND (" +ne+ " episodes)]";
			    } else {
				t=mkForm("[STARTED (done "+ne+" episodes); ","]",
					 exp, p.getPlayerId(), "CONTINUE!");
				buttonCnt++;
			    }
			    pv.add(t);
			}
		    }

		    if (buttonCnt==0) {
			String bt = (pv.size()==0)? "PLAY!": "Play another round!";
			text =mkForm("","", exp, null, bt);
			pv.add(text);
		    }
		    
		    text = String.join(" ", pv);
		    cells.add( fm.td(text));
		}
		rows.add(fm.tr(String.join("",cells)));
	    }


	    tableText = fm.table( "border=\"1\"", rows);
	} catch(Exception ex) {
	    hasException(ex);
	} finally {
	    try { em.close();} catch(Exception ex) {}
	}	
    }    
    
}
