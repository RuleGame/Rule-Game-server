package edu.wisc.game.engine;

import java.io.*;
import java.util.*;
import java.text.*;

//import javax.json.*;
//import javax.persistence.*;

//import org.apache.openjpa.persistence.jdbc.*;


import edu.wisc.game.util.*;
//import edu.wisc.game.sql.*;
import edu.wisc.game.parser.*;
import edu.wisc.game.engine.*;
//import edu.wisc.game.sql.Board.Pos;
//import edu.wisc.game.engine.RuleSet.BucketSelector;

public class AllRuleSets extends HashMap<String, RuleSet> {

    RuleSet get(String ruleSetName) throws IOException, RuleParseException {
	RuleSet rules = super.get(ruleSetName);
	if (rules==null) {
	    File base = new File("/opt/tomcat/game-data");
	    base = new File(base, "rules");
	    String ext = ".txt";
	    String name = ruleSetName;
	    if (!name.endsWith(ext)) name += ext;

	    File f = new File(base, name);
	    if (!f.canRead())  throw new IOException("Cannot read rule file: " + f);
	    String text = Util.readTextFile(f);
	    rules = new RuleSet(text);
	    super.put(ruleSetName, rules);
	}
	return rules;
    }

    public static RuleSet obtain(String ruleSetName) throws IOException, RuleParseException  {
	return allRuleSets.get(ruleSetName);
    }

    
    private static  AllRuleSets allRuleSets = new  AllRuleSets();
}
