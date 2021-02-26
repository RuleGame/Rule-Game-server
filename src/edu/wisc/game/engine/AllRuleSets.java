package edu.wisc.game.engine;

import java.io.*;
import java.util.*;
import java.text.*;

import edu.wisc.game.util.*;
import edu.wisc.game.parser.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.rest.Files;

/** Stores rule sets, and allows lookup by name. 
    FIXME: need a web UI for calling clear() on this table, whenever
    a new version of the same rule set file is created.
*/
public class AllRuleSets extends HashMap<String, RuleSet> {

    public static RuleSet read(File f) throws IOException, RuleParseException {
	if (!f.canRead())  throw new IOException("Cannot read rule file: " + f);
	String text = Util.readTextFile(f);
	return new RuleSet(text);
    }

    /** Gets the RuleSet from the table (if it's already cached in the table),
	or from the file. */
    RuleSet get(String ruleSetName) throws IOException, RuleParseException {
	RuleSet rules = super.get(ruleSetName);
	if (rules==null) {
	    File f = Files.rulesFile(ruleSetName);
	    rules = read(f);
	    if (Files.rulesCanBeCached(ruleSetName)) {
		super.put(ruleSetName, rules);
	    }
	}
	return rules;
    }

    /** @param  ruleSetName Either a name with no path or extension
	(which will be mapped to a rule set file in the tomcat directory),
	or a full file path name (beginning with a "/" and ending in ".txt").	
     */
    public static RuleSet obtain(String ruleSetName) throws IOException, RuleParseException  {
	if (ruleSetName==null ||ruleSetName.trim().equals("")) throw new IOException("No rules set name specified");
	return allRuleSets.get(ruleSetName);
    }

    /** Can be used to ensure that the rule sets will be reloaded */
    public static void clearAll() {
	allRuleSets.clear();
    }
    
    private static  AllRuleSets allRuleSets = new  AllRuleSets();
}
