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
//import edu.wisc.game.formatter.*;
import edu.wisc.game.rest.*;


/** The common base for the Launch pages that allows one to play all
    rule sets from rules/APP, rules/MLC, and others. As requested by Paul on
    2021-10-12 and 2021-10-13.

<pre>
  The M need not provide bonuses, and can use the standard 4 colors and shapes. I'd suggest either 5 to 8 pieces (a random number). People should be able to give up even at the first screen, if that is supported.
</pre>

*/
public class LaunchRulesBase      extends ResultsBase  {

    /** The name refers to the directory from whih trial lists or rule sets 
	are read */
    public enum Mode { APP, MLC, CGS, BRM };

    
    private ContextInfo ci;
       
    protected HashMap<String,Vector<PlayerInfo>> allPlayers;

    public String tableText = "<!-- TABLES START HERE-->\n";

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

	//final boolean isDev = cp.startsWith("/w2020-dev");
					    
	//String action="http://sapir.psych.wisc.edu/rule-game/prod/";
	// As of Oct 2021, only dev supports the server= option 
	//String action= (atHome || ci.dev || mustUseDevClient)?
	//	    "http://sapir.psych.wisc.edu/rule-game/dev/":
	//	    "http://sapir.psych.wisc.edu/rule-game/prod/";
	
	String action= ci.clientUrl;

	
	String text = "<FORM METHOD='GET' ACTION='"+action+"'>\n";
	
	// If we're not running in prod, we need to tell the
	// client which Game Server to hit with REST requests

	text+=Tools.inputHidden("server", ci.serverUrl);

	
	//if (atHome) {
	//	    String url="http://localhost:8080/w2020";
	//	    text+=Tools.inputHidden("server", url);
	//	}  else if (isDev) {
	//	    String url="http://sapir.psych.wisc.edu:7150/w2020-dev";
	//	    text+=Tools.inputHidden("server", url);
	//	}
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
	ci = new ContextInfo(request,  response);
	if (ci.error) {
	    giveError(ci);
	    return;
	}

    }	

    

    /** Creates a description of a trial list, consisting of the 
	descriptions of all rule sets in the list.
	@param knownRuleSetNames The method will add all encountered rule set names to this set.
    */
    protected String describeTrialList(TrialList t, HashSet<String> knownRuleSetNames) throws Exception {
	Vector<String> v = new Vector<>();
	int j=0;
	HashSet<String> h = new HashSet<>();
	for(ParaSet para: t) {
	    String r = para.getRuleSetName();
	    knownRuleSetNames.add(r);
	    RuleSet ruleSet = AllRuleSets.obtain(r);
	    String q = "["+ ++j+"] "+fm.tt(r);
	    if (!h.contains(r)) {
		q += " " + String.join("<br>", ruleSet.description);
		h.add(r);
	    }
	    v.add(q);
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

    /** Builds the Part A table, based on preexisting trial list, with P:-type
	plans constructed around them.
	
       @param knownRuleSetNames Output parameter: here the rule sets used in Part A will be put, so that we won't use them again in Part B.
    */
    private String buildPartA(String[] modsShort,   String[] hm,  final Mode z, File launchFile,   HashSet<String> knownRuleSetNames ) throws Exception {

	CsvData launchList = null;	
	try {
	    launchList = new CsvData(launchFile, true, false, new int[] {2});	
	} catch(Exception ex) {
	    infomsg += "[Ignoring the control file "+launchFile + " due to an error: " + ex.getMessage()+"]";
	}
   
	Vector<String> rows = new Vector<>();
	Vector<String> cells = new Vector<>();
		
	String text = fm.para("Part A: Experiment plans from <tt>trial-lists/"+z+"</tt>");

	rows.add(fm.tr("<TH rowspan=2>Experiment plan &amp; its rule set(s)</TH><TH colspan="+hm.length+">Actions</TH>"));
	cells.clear();
	for(String h: hm) cells.add(fm.th(h));	    
	rows.add(fm.tr(String.join("",cells)));
	    
	String[] allPlanNames = Files.listSAllExperimentPlansInTree(z.name());
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
	

	text += fm.para("This table covers " +  knownRuleSetNames.size() + " rule sets: " + Util.joinNonBlank(", ", knownRuleSetNames));
	text += fm.table( "border=\"1\"", rows);

	return text;

    }

    /** Builds the Part B table. Here, the rule set files are the base, and 
	R:-type dynamic plans are created.
	@param  chosenRuleSet If not null, just show this set
     */
    private String buildPartB(String[] modsLong, String[] hm,  final Mode z,
			      HashSet<String> knownRuleSetNames, String chosenRuleSet ) throws Exception {
	Vector<String> rows = new Vector<>();
	Vector<String> cells = new Vector<>();
	    
	String text = "";
	if (z==Mode.APP) {
	    text += fm.para("Part B: Rule sets from <tt>rules/"+z+"</tt> not covered in Part A");
	}
	    
	rows.add(fm.tr("<TH rowspan=2>Rule Set</TH><TH colspan="+hm.length+">Actions</TH>"));

	for(String h: hm) cells.add(fm.th(h));	    
	rows.add(fm.tr(String.join("",cells)));
	
	    
	String[] allRuleNames;

	if ( chosenRuleSet!=null ) {
	    String r =  z.name() +  File.separator + chosenRuleSet;
	    // make this call in order to have an exception thrown if
	    // the file does not exist or has bad content	   
	    RuleSet ruleSet = AllRuleSets.obtain(r);
	
	    //File f = new File(r + Files.RULES_EXT);
	    //if (!f.exists()) {
	    //	throw new IOException("Rule set file '" + f + "' does not exist");
	    //}
				 
	    allRuleNames = new String[]{ r };
	} else {	    
	    allRuleNames = Files.listAllRuleSetsInTree(z.name());
	}
	

	for(String r:  allRuleNames) {
	    
	    RuleSet ruleSet = AllRuleSets.obtain(r);
	    String descr = String.join("<br>", ruleSet.description);
	    
	    cells.clear();
	    String t = fm.tt(r) + " " + descr;
	    cells.add( fm.td(t));
	    
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
	
	text += fm.table( "border=\"1\"", rows);
	return text;
   }
    
    /** Builds Part A and Part B tables 
	@param z "APP" or "MLC". This is the name of the directory (under the main rule set directory) in which to look for the rule set files.
	@param modsShort Modifiers for Part A. If null, skip this part
	@param modsLong Modifiers for Part B. If null, skip this part.
	@param chosenRuleSet If not null, we just show this rule set in Part B table
    */
    protected void buildTable(String[] modsShort, String[] modsLong,  	    String[] hm,
			      final Mode z, File launchFile, String chosenRuleSet) {

	if (!launchFile.exists()) {
	    infomsg += " [The control file " + launchFile + " does not exist on the server]";
	}

	// The rule sets used in Part A. We won't use them again in Part B
	HashSet<String> knownRuleSetNames = new HashSet<>();
	
	EntityManager em = Main.getNewEM();
	try {

	    allPlayers = findPlayers( em, Integer.parseInt(uid));


	    if (modsShort!=null)   tableText +=  buildPartA(modsShort, hm, z, launchFile, knownRuleSetNames);

	    if (modsLong!=null)   tableText +=  buildPartB(modsLong, hm, z, knownRuleSetNames, chosenRuleSet);


	} catch(Exception ex) {
	    hasException(ex);
	} finally {
	    try { em.close();} catch(Exception ex) {}
	}	
    }    


}
