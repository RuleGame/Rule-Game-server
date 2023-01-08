package edu.wisc.game.tools;

import java.io.*;
import java.util.*;
//import java.util.regex.*;
//import java.text.*;

import javax.persistence.*;

import edu.wisc.game.util.*;
import edu.wisc.game.rest.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.saved.*;
import edu.wisc.game.reflect.*;
//import edu.wisc.game.parser.RuleParseException;
import edu.wisc.game.math.*;
import edu.wisc.game.formatter.*;

//import edu.wisc.game.sql.Episode.CODE;

/** Lists experiment plans and the number of players who played them, etc */
public class PlanStats {

    /** Plan name */
    public String exp;

    /** How many players have played at least one episode in this plan */
    public int realPlayerCnt; 
    public int episodeCnt; 

    PlanStats(String _exp, int _realPlayerCnt, int _episodeCnt) {
	exp = _exp;
	realPlayerCnt = _realPlayerCnt;
	episodeCnt =  _episodeCnt;
    }
    
    public String toString() {
	return "(" + exp + " : p=" + realPlayerCnt+", e="+ episodeCnt+")";
    }
    
    public static PlanStats[] listPlans() {
	EntityManager em=null;

	Vector<PlanStats> v = new Vector<>();
	
	try {
 
	    em = Main.getNewEM();

	    Query q = em.createQuery("select p.experimentPlan, count(distinct p), count(e) from PlayerInfo p, IN(p.allEpisodes) e group by p.experimentPlan order by p.experimentPlan");

	    List<Object[]> results = (List<Object[]>)q.getResultList();

	    //	    Query q = em.createQuery("select e.player.experimentPlan, count(e) from Episode e group by e.player.experimentPlan order by e.player.experimentPlan");
	    //q = em.createQuery("select p.experimentPlan, count(p.allEpisodes) from PlayerInfo p group by p.experimentPlan order by p.experimentPlan");

	    //List<Object[]>
	    results = (List<Object[]>)q.getResultList();

	    for(Object[] line: results) {
		String exp = (String)(line[0]);
		Long cntP  = (Long)(line[1]);
		Long cntE  = (Long)(line[2]);
		v.add(new PlanStats(exp, (int)cntP.longValue(), (int)cntE.longValue()));
	    }

	    
	} catch(Exception ex) {
	    //hasException(ex);
	    System.err.println(ex);
	} finally {	
	    if (em!=null) try {
		    em.close();
		} catch(Exception ex) {}
	}
	return v.toArray(new PlanStats[0]);

	
    }

   /** Creates an HTML snippet (to be used inside a FORM) listing
	all currently existing experiment plans.
     */
    public static String listSAllExperimentPlansHtml()  throws IOException{
	Vector<String> v=new Vector<>();
	for(PlanStats q: listPlans()) {
	    v.add( Tools.checkbox("exp", q.exp, q.exp + "("+q.realPlayerCnt +" players, "+q.episodeCnt +" episodes)", false));
	}
	return String.join("<br>\n", v);
    }


    
    public static void main(String[] argv) {
	//	for(String x: argv) {
	PlanStats[] v = listPlans();
	System.out.println("" + v.length + " plans with non-trivial usage");
	for(PlanStats q: v) {
	    System.out.println(q);
	}

    }

    
}
    
