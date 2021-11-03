package edu.wisc.game.web;

import java.io.*;
import java.util.*;
import java.text.*;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.persistence.*;


import edu.wisc.game.util.*;
//import edu.wisc.game.reflect.*;
import edu.wisc.game.sql.*;
//import edu.wisc.game.engine.*;
import edu.wisc.game.formatter.*;
import edu.wisc.game.rest.*;
//import edu.wisc.game.parser.*;


/** As requested by Paul, "Rule Game -- challenge UWisc faculty and staff",
    2021-09-23.

<pre> What I am thinking is a table with rows corresponding to
specific rules, and columns that would lead to a way to play the game
(with a logic like what we have at Selected Games to Isolate Facets
(rutgers.edu) which makes a workerId such as
"pbk_pkZhistory_A_7412708" [The Z was because I was not sure that "/"
is allowed in a workerId; the last part is some digits from the time,
which lets me continue as pbk whenever I use it] and brings the player
into the game.

I'd like  for you  to select about  a dozen rules  that you  think are
interesting and kind  of span the syntax, and, in  particular, what we
have already worked with.

Then, could you set up the corresponding trial files?.  No need to
ever offer the bonus, I think, and we can let people play a lot of
boards (say, as many as 10).

New rules in the same "learning class" as existing ones are also OK,
and thinking about that may advance the arm dealing with the
smartphone version. In that arm, we probably want to be able to say
something like "try easy games first" and, as you suggested, be able
to generate new ones in various classes.

The columns of the table for this researcher challenge should provide these options:

1 play with minimal information; no Xs; no test tubes or numbers displayed
2 play with memory aids -- test tubes and numbers displayed
3 with memory aids and movability signs.
4 "click this to see the rule itself"
</pre>

*/
public class LaunchMain      extends LaunchRulesBase  {

  public LaunchMain(HttpServletRequest request, HttpServletResponse response)  {
	super(request,response);
	if (error || !loggedIn()) return;
	String z = "APP"; // can use APP's mods for now
	String[] modsLong = {z + "/" + z + "-no-feedback",
			     z + "/" + z + "-some-feedback",
			     z + "/" + z + "-more-feedback",
			     z + "/" + z + "-max-feedback"};

	String z1= z+"-short";
	String[] modsShort = {z1 + "/" + z1 + "-no-feedback",
			     z1 + "/" + z1 + "-some-feedback",
			     z1 + "/" + z1 + "-more-feedback",
			     z1 + "/" + z1 + "-max-feedback"};


	// As per Paul's request, give them debug mode
	mustUseDevClient = false;
	z = "MLC";
	File launchFile = Files.getLaunchFileAPP();
	buildTable(modsShort, modsLong, z, launchFile);
    }

    
    public void old_LaunchMain(HttpServletRequest request, HttpServletResponse response){
	//super(request,response);
	if (error || !loggedIn()) return;

	mustUseDevClient = true;
	
	EntityManager em = Main.getNewEM();

	try {

	    allPlayers = findPlayers( em,  new Integer(uid));

	    //String[] allRuleNames = Files.listAllRuleSetsInTree("APP");

	    Vector<String> rows = new Vector<>();

	    String[] hm = {"No feedback",
			   "Some feedback",
			   "More feedback",
			   "Max feedback" };
	    String[] mods = {"APP/APP-no-feedback",
			     "APP/APP-some-feedback",
			     "APP/APP-more-feedback",
			     "APP/APP-max-feedback" };

	    rows.add(fm.tr("<TH rowspan=2>Experiment plan</TH><TH colspan="+hm.length+">Actions</TH>"));
	    Vector<String> cells = new Vector<>();
	    for(String h: hm) cells.add(fm.th(h));	    
	    rows.add(fm.tr(String.join("",cells)));

	    
	    File launchFile = Files.getLaunchFileMLC();
	    if (!launchFile.exists()) throw new IOException("The control file " + launchFile + " does not exist on the server");;
	    CsvData csv = new CsvData(launchFile, true, false, new int[] {2});	
	    for(CsvData.LineEntry _e: csv.entries) {
		cells.clear();

		CsvData.BasicLineEntry e= (CsvData.BasicLineEntry)_e;
		String plan = e.getKey();
		String descr = e.getCol(1);

		cells.add( fm.td(fm.strong(fm.code(plan)) + ": " + descr));

		int ntr = 0;
		try {
		    ntr = TrialList.listTrialLists(plan).size();
		} catch(IOException ex) {}
		if (ntr==0) {
		    cells.add( fm.wrap("td", "colspan=" + mods.length, "Plan not valid - check the plan directory!"));		    
		    rows.add(fm.tr(String.join("",cells)));
		    continue;
		}
		

		for(String mod: mods) {
		    String exp = "P:" + plan + ":"+mod;	
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
			String t =mkForm("","", exp, null, bt);
			pv.add(t);
		    }
		    
		    String t = String.join(" ", pv);
		    cells.add( fm.td(t));
		}
		rows.add(fm.tr(String.join("",cells)));
	    }

	    tableText = fm.table( "border=\"1\"", rows);
	} catch(Exception ex) {
	    hasException(ex);
	}

	
      }


    //    private 

    
}
