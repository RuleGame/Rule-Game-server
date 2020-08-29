package edu.wisc.game.engine;

import java.io.*;
import java.util.*;
import java.text.*;

import edu.wisc.game.util.Util;
import edu.wisc.game.sql.*;
import edu.wisc.game.parser.*;


import javax.json.*;
    
          
/** This class is used for a one-off project: converting the list of rules found in 
    Kevin's GUI (stored in a JSON file there) to text files describing rules
    in a similar, but slightly different, format used by our server.
 */
public class ConvertRules {
    /** Reads Kevin's file such as  Games.21-38.json, and converts it to Game Server rule set files.
<pre>
{"id":"XTkJxZg3w","version":"0.0.0",
 "games":{
     "9cSFaI_Kp":{"id":"9cSFaI_Kp","boardObjectsArrays":[],"name":"TD-01","ruleArray":"wN2Z5TaTz","useRandomBoardObjects":true,"numRandomBoardObjects":7,"restartIfNotCleared":true},
     "51PK72Yeg":{"id":"51PK72Yeg","boardObjectsArrays":[],"name":"TD-02","ruleArray":"EG_KL7GZR","useRandomBoardObjects":true,"numRandomBoardObjects":7,"restartIfNotCleared":true},
  ... },
 
 "ruleArrays":{
     "un4Q-9EI":{"id":"un4Q-9EI","stringified":"(10,square,*,*,[1,2]) (10,*,blue,10,[2,3])\n(*,*,*,*,[ps,pc])\n(*,*,*,*,[(p+1)%4])","name":"1"},
     "G6jrd4pUG":{"id":"G6jrd4pUG","stringified":"(*,*,*,*,[Nearby])","name":"try-01"},
     "DdG1IEUys":{"id":"DdG1IEUys","stringified":"(*,*,*,Farthest,*)","name":"farthest"},
....
}
}
</pre>

    */
    public static void main(String argv[]) throws IOException,  RuleParseException {
    
    String fname = argv[0];
	String outdirName = argv[1];
	File outdir = new File(outdirName);
	if (!outdir.isDirectory()) usage("Not a directory: " + outdir);
	//	String text = Util.readTextFile(new File(fname));
	JsonReader jsonReader = Json.createReader(new FileReader(fname));

	JsonObject obj = jsonReader.readObject();

	jsonReader.close();
	JsonObject games=obj.getJsonObject("games");
	JsonObject ruleArrays=obj.getJsonObject( "ruleArrays");

	// Contains IDs of RuleArrays that are used in Game objects, mapping them
	// to Game names
	HashMap<String, String> ruleArrayToGame = new HashMap<>();	
	for(JsonValue val: games.values()) {
	    JsonObject r  = (JsonObject)val;
	    // {"id":"LrJhY6bTe","boardObjectsArrays":[],"name":"TD-05","ruleArray":"vLRMG9G3J","useRandomBoardObjects":true,"numRandomBoardObjects":7,"restartIfNotCleared":true},
	    ruleArrayToGame.put( r.getString("ruleArray"), r.getString("name"));
	    
	}
	
	for(String key: ruleArrays.keySet()) {
	    // {"id":"un4Q-9EI","stringified":"(10,square,*,*,[1,2]) (10,*,blue,10,[2,3])\n(*,*,*,*,[ps,pc])\n(*,*,*,*,[(p+1)%4])","name":"1"},
	    JsonObject r = ruleArrays.getJsonObject(key);
	    String stringified = r.getString("stringified");
	    String origName = r.getString("name");
	    String gameName = ruleArrayToGame.get(key); 
	    String name = (gameName!=null) ? gameName : origName;
	    
	    JsonArray oa = null;
	    if (r.containsKey("order")) {
		oa = r.getJsonArray("order");
	    }

	    
	    File g = new File(outdir, name + ".txt");
	    PrintWriter w = new PrintWriter(new FileWriter(g));
	    w.println("# ruleArrayName=" + origName);
	    if (gameName!=null)  w.println("# used in game=" + gameName);
	    if (stringified.startsWith("\"") && stringified.endsWith("\"")) {
		// Strip superfluous quotes, which are sometimes seen in Kevin's JSON file;
		stringified = stringified.replaceFirst("\"", "").replaceFirst("\"$", "");
	    }
				      
	    try {
		if (oa!=null) {
		    
		    w.println("Order Custom=" + oa.toString().trim() );
		    RuleSet z = new RuleSet(stringified);
		    z.forceOrder("Custom");
		    w.println( z.toSrc());
		} else {
		    RuleSet z = new RuleSet(stringified);
		    w.println( z.toSrc());
		    //w.println(stringified);
		}
	    } catch(RuleParseException ex) {
		System.err.println("Parse exception when working with exp=" + stringified);
		throw ex;
	    }
	    w.close();
	}


}

    static private void usage() {
	usage(null);
    }
    static private void usage(String msg) {
	System.err.println("Usage:\n");
	System.err.println("  java [options]  edu.wisc.game.engine.ConverRules input.json outdir");
	if (msg!=null) 	System.err.println(msg + "\n");
	System.exit(1);
    }
    
}
