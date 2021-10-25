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

    HashMap<String,Vector<PlayerInfo>> allPlayers;

    String[] modsLong = {"APP/APP-no-feedback",
			 "APP/APP-some-feedback",
			 "APP/APP-more-feedback",
			 "APP/APP-max-feedback" };

    String[] modsShort = {"APP-short/APP-short-no-feedback",
			  "APP-short/APP-short-some-feedback",
			  "APP-short/APP-short-more-feedback",
			  "APP-short/APP-short-max-feedback" };

    
    public LaunchRules(HttpServletRequest request, HttpServletResponse response)  {
	super(request,response);
	if (error || !loggedIn()) return;
	
	EntityManager em = Main.getNewEM();
	try {

	    allPlayers = findPlayers( em,  new Integer(uid));


	    Vector<String> rows = new Vector<>();
	    Vector<String> cells = new Vector<>();

	    String[] hm = {"No feedback",
			   "Some feedback",
			   "More feedback",
			   "Max feedback" };

	    tableText = "";
	    tableText += fm.para("Part A: Experiment plans from <tt>trial-lists/APP</tt>");

	    rows.clear();
	    rows.add(fm.tr("<TH rowspan=2>Experiment plan &amp; its rule set(s)</TH><TH colspan="+hm.length+">Actions</TH>"));
	    cells.clear();
	    for(String h: hm) cells.add(fm.th(h));	    
	    rows.add(fm.tr(String.join("",cells)));

	    // The rule sets used in Part A. We won't use them again in Part B
	    HashSet<String> knownRuleSetNames = new HashSet<>();
	    
	    String[] allPlanNames = Files.listSAllExperimentPlansInTree("APP");
	    for(String p:  allPlanNames) {
		cells.clear();

		
		Vector<String> trialLists = TrialList.listTrialLists(p);

		if (trialLists.size()!=1) {
		    cells.add(fm.td("Cannot use experiment plan " + p +": it has " + trialLists.size() + " trial lists, and we want exactly 1"));
		    rows.add(fm.tr(String.join("",cells)));
		    continue;
		}

		TrialList tlist = new TrialList( p, trialLists.get(0));

		String descr = "Plan: " + fm.strong(fm.tt(p)) + ":<br>" +
		    describeTrialList(tlist, knownRuleSetNames);
		cells.add( fm.td(descr));
	
		for(String mod: modsShort) {
		    String exp = "P:" + p + ":"+mod;
		    cells.add( mkCell(exp));
		}
		rows.add(fm.tr(String.join("",cells)));
	    }


	    tableText += fm.para("This table covers " +  knownRuleSetNames.size() + " rule sets: " + Util.joinNonBlank(", ", knownRuleSetNames));


	    tableText += fm.table( "border=\"1\"", rows);


	    tableText += fm.para("Part B: Rule sets from <tt>rules/APP</tt> not covered in Part A");
	    

	    rows.clear();
	    rows.add(fm.tr("<TH rowspan=2>Rule Set</TH><TH colspan="+hm.length+">Actions</TH>"));



	    cells.clear();
	    for(String h: hm) cells.add(fm.th(h));	    
	    rows.add(fm.tr(String.join("",cells)));
	    
	    String[] allRuleNames = Files.listAllRuleSetsInTree("APP");
	    for(String r:  allRuleNames) {

		RuleSet ruleSet = AllRuleSets.obtain(r);
		String descr = String.join("<br>", ruleSet.description);

		cells.clear();
		String text = fm.tt(r) + " " + descr;
		cells.add( fm.td(text));

		if (knownRuleSetNames.contains(r)) {
		    cells.add( fm.wrap("td","colspan="+modsLong.length, "See Part A"));
		    rows.add(fm.tr(String.join("",cells)));
		    continue;
		} 
		

		for(String mod: modsLong) {
		    String exp = "R:" + r + ":"+mod;
		    cells.add( mkCell(exp));
		}
		rows.add(fm.tr(String.join("",cells)));
	    }


	    tableText += fm.table( "border=\"1\"", rows);
	} catch(Exception ex) {
	    hasException(ex);
	} finally {
	    try { em.close();} catch(Exception ex) {}
	}	
    }    


    /** Creates a table cell for a given P: or R: plan, with a "PLAY!"
	button and information about any previous rounds.
     */
    private String mkCell(String exp) {
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
	    pv.add( mkForm("","", exp, null, bt));
	}
		    
	return  fm.td( String.join(" ", pv));
    }

    /** Creates a description of a trial list, consisting of the 
	descriptions of all rule sets in the list.
	@param knownRuleSetNames The method will add all encountered rule set names to this set.
    */
    private String describeTrialList(TrialList t, HashSet<String> knownRuleSetNames) throws Exception {
	Vector<String> v = new Vector<>();
	for(ParaSet para: t) {
	    String r = para.getRuleSetName();
	    knownRuleSetNames.add(r);
	    RuleSet ruleSet = AllRuleSets.obtain(r);
	    String descr = String.join("<br>", ruleSet.description);	    
	    v.add( fm.tt(r) + " " + descr);
	}
	return String.join("<br>",v);
    }
    
    
}
