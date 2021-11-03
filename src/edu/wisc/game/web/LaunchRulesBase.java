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
public class LaunchRulesBase      extends ResultsBase  {

    protected HashMap<String,Vector<PlayerInfo>> allPlayers;

    public String tableText = "NO DATA";

    /** Finds the PlayerInfo object associated with the specified repeat user */
    static HashMap<String,Vector<PlayerInfo>> findPlayers(EntityManager em, int uid) {
	HashMap<String,Vector<PlayerInfo>>  h = new HashMap<>();

	Query q = em.createQuery("select m from PlayerInfo m where m.user.id=:uid");
	q.setParameter("uid", uid);
	List<PlayerInfo> res = (List<PlayerInfo>)q.getResultList();
	if (res==null) return h;
	for(PlayerInfo p: res) {
	    String exp = p.getExperimentPlan();	    
	    Vector<PlayerInfo> v = h.get(exp);
	    if (v==null) h.put(exp, v=  new Vector<>());
	    v.add(p);
	}
	return h;	
    }


    final boolean generatePids = false;
    
    protected String mkForm(String text1, String text2, String exp, String pid, String buttonText) {

	final boolean isDev = cp.startsWith("/w2020-dev");
					    
	//String action="http://sapir.psych.wisc.edu/rule-game/prod/";
	// As of Oct 2021, only dev supports the server= option 
	String action= (atHome || isDev || mustUseDevClient)?
	    "http://sapir.psych.wisc.edu/rule-game/dev/":
	    "http://sapir.psych.wisc.edu/rule-game/prod/";
	
					    
	String text = "<FORM METHOD='GET' ACTION='"+action+"'>\n";
	
	// If we're not running in prod, we need to tell the
	// client which Game Server to hit with REST requests
	if (atHome) {
	    String url="http://localhost:8080/w2020";
	    // http://sapir.psych.wisc.edu:7150/w2020
	    text+=Tools.inputHidden("server", url);
	}  else if (isDev) {
	    String url="http://sapir.psych.wisc.edu:7150/w2020-dev";
	    text+=Tools.inputHidden("server", url);
	}
	text+=Tools.inputHidden("intro", false);
	text+=Tools.inputHidden("exp", exp);
	text+=Tools.inputHidden("uid", uid);
	if (pid==null && generatePids) {
	    pid = "RepeatUser-" + uid + "-" +  Episode.randomWord(6) + "-tmp-" + Episode.sdf.format(new Date());
	}
		
	if (pid!=null) text+=Tools.inputHidden("workerId", pid);
	text+= text1 + fm.button(buttonText) + text2 +"\n";
	text+="</FORM>";
	return text;
    }	    

    /** Creates a table cell for a given P: or R: plan, with a "PLAY!"
	button and information about any previous rounds.
     */
    protected String mkCell(String exp) {
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



    
    /** This will be set to true in the MLC page (LaunchMain), to guarantee
     */
    protected boolean mustUseDevClient = false;

    /*
    void setActionDev() {
	action="http://sapir.psych.wisc.edu/rule-game/dev/";
    }
    void setActionProd() {
	action="http://sapir.psych.wisc.edu/rule-game/prod/";
    }
    */
    
    public LaunchRulesBase(HttpServletRequest request, HttpServletResponse response){
	super(request,response,true);
	if (error || !loggedIn()) return;
    }	

    

    /** Creates a description of a trial list, consisting of the 
	descriptions of all rule sets in the list.
	@param knownRuleSetNames The method will add all encountered rule set names to this set.
    */
    protected String describeTrialList(TrialList t, HashSet<String> knownRuleSetNames) throws Exception {
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

    /*
    protected HashMap<String, String> readPlanDescriptions() {
	File launchFile = Files.getLaunchFile();
	if (!launchFile.exists()) throw new IOException("The control file " + launchFile + " does not exist on the server");;
	CsvData csv = new CsvData(launchFile, true, false, new int[] {2});	
	for(CsvData.LineEntry _e: csv.entries) {
	    CsvData.BasicLineEntry e= (CsvData.BasicLineEntry)_e;
	    String plan = e.getKey();
	    String descr = e.getCol(1);
	}
    }
    */
    
    /** @param launchList List from a CSV file in the launch directory
	@param  allPlanNames List of plan directories from APP
	@param csvPlanDescriptions Output paramter */
    private String[] mergePlanLists( CsvData launchList, String[] allPlanNames,
				     HashMap<String,String> csvPlanDescriptions
				     ) {
	if (launchList==null) return  allPlanNames;
	Vector<String> q =new Vector<>();
	for(CsvData.LineEntry _e: launchList.entries) {
	    CsvData.BasicLineEntry e= (CsvData.BasicLineEntry)_e;
	    String plan = e.getKey();
	    q.add(plan);
	    String descr = e.getCol(1);
	    csvPlanDescriptions.put(plan, descr);
	}
	for(String plan:  allPlanNames) {
	    if (!csvPlanDescriptions.containsKey(plan)) q.add(plan);
	}
	return q.toArray(new String[0]);
    }

    
    /** Builds Part A and Part B tables 
	@param z "APP" or "MLC"
	@param modsShort Modifiers for Part A
	@param modsLong Modifiers for Part B
    */
    protected void buildTable(String[] modsShort, String[] modsLong,   final String z, File launchFile) {

	if (!launchFile.exists()) {
	    infomsg += " [The control file " + launchFile + " does not exist on the server]";
	}
	CsvData launchList = null;	
	try {
	    launchList = new CsvData(launchFile, true, false, new int[] {2});	
	} catch(Exception ex) {
	    infomsg += "[Ignoring the control file "+launchFile + " due to an error: " + ex.getMessage()+"]";
	}
   
	
	EntityManager em = Main.getNewEM();
	try {

	    allPlayers = findPlayers( em,  new Integer(uid));

	    Vector<String> rows = new Vector<>();
	    Vector<String> cells = new Vector<>();

	    String[] hm = {"No feedback",
			   "Some feedback",
			   "More feedback",
			   "Max feedback" };
	    
	    tableText = fm.para("Part A: Experiment plans from <tt>trial-lists/"+z+"</tt>");

	    rows.add(fm.tr("<TH rowspan=2>Experiment plan &amp; its rule set(s)</TH><TH colspan="+hm.length+">Actions</TH>"));
	    cells.clear();
	    for(String h: hm) cells.add(fm.th(h));	    
	    rows.add(fm.tr(String.join("",cells)));

	    // The rule sets used in Part A. We won't use them again in Part B
	    HashSet<String> knownRuleSetNames = new HashSet<>();
	    
	    String[] allPlanNames = Files.listSAllExperimentPlansInTree(z);
	    HashMap<String, String> csvPlanDescriptions = new HashMap<>();
	    allPlanNames = mergePlanLists( launchList,  allPlanNames,
					   csvPlanDescriptions);

	    for(String p:  allPlanNames) {
		cells.clear();

		
		Vector<String> trialLists = TrialList.listTrialLists(p);

		if (trialLists.size()!=1) {
		    cells.add(fm.td("Cannot use experiment plan " + p +": it has " + trialLists.size() + " trial lists, and we want exactly 1"));
		    rows.add(fm.tr(String.join("",cells)));
		    continue;
		}

		TrialList tlist = new TrialList( p, trialLists.get(0));

		String descr2 = csvPlanDescriptions.get(p);
		
		String descr = "Plan: " + fm.strong(fm.tt(p));
		if (descr2!=null) descr += " " + descr2;
		descr += ":<br>" +
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

	    tableText += fm.para("Part B: Rule sets from <tt>rules/"+z+"</tt> not covered in Part A");
	    
	    rows.clear();
	    rows.add(fm.tr("<TH rowspan=2>Rule Set</TH><TH colspan="+hm.length+">Actions</TH>"));


	    cells.clear();
	    for(String h: hm) cells.add(fm.th(h));	    
	    rows.add(fm.tr(String.join("",cells)));
	    
	    String[] allRuleNames = Files.listAllRuleSetsInTree(z);
	    for(String r:  allRuleNames) {

		RuleSet ruleSet = AllRuleSets.obtain(r);
		String descr = String.join("<br>", ruleSet.description);

		cells.clear();
		String text = fm.tt(r) + " " + descr;
		cells.add( fm.td(text));

		if (knownRuleSetNames.contains(r)) {
		    cells.add( fm.wrap("td","colspan="+modsLong.length, "See Part A"));
		}  else {		
		    for(String mod: modsLong) {
			String exp = "R:" + r + ":"+mod;
			cells.add( mkCell(exp));
		    }
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


}
