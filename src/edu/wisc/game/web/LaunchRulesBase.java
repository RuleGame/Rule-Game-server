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

        
    protected String mkForm(String text1, String text2, String exp, String pid, String buttonText) {
			    
	String text = "<FORM METHOD='GET' ACTION='"+action+"'>";
	// If we're not running in prod, we need to tell the
	// client which Game Server to hit with REST requests
	if (atHome) {
	    String url="http://localhost:8080/w2020";
	    // http://sapir.psych.wisc.edu:7150/w2020
	    text+="\n"+Tools.inputHidden("server", url);
	}  else if (cp.startsWith("/w2020-dev")) {
	    String url="http://sapir.psych.wisc.edu:7150/w2020-dev";
	    text+="\n"+Tools.inputHidden("server", url);
	}
	text+="\n"+Tools.inputHidden("intro", false);
	text+="\n"+Tools.inputHidden("exp", exp);
	text+="\n"+Tools.inputHidden("uid", uid);
	if (pid==null) {
	    pid = "RepeatUser-" + uid + "-" +  Episode.randomWord(6) + "-tmp-" + Episode.sdf.format(new Date());
	}
		
	text+="\n"+Tools.inputHidden("workerId", pid);
	text+="\n" + text1 + fm.button(buttonText) + text2;
	text+="\n</FORM>";
	return text;
    }	    


    //String action="http://sapir.psych.wisc.edu/rule-game/prod/";
    // Only dev supports the server= option
    private static final String action="http://sapir.psych.wisc.edu/rule-game/dev/";
    
    public LaunchRulesBase(HttpServletRequest request, HttpServletResponse response){
	super(request,response,true);
	if (error || !loggedIn()) return;
    }	

    
    
}
