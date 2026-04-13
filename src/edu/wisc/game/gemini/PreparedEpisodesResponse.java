package edu.wisc.game.gemini;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.net.*;
import jakarta.json.*;

import edu.wisc.game.util.*;
import edu.wisc.game.reflect.*;
import edu.wisc.game.rest.*;
import edu.wisc.game.engine.*;

/** A Java object whose structure matches the JSON schema used for our
    responseSchema. Used for parsing the response.
 */
public class PreparedEpisodesResponse {

    static class MoveLine {
	final int pieceId, bucketNo;
	MoveLine(int p, int b) {
	    pieceId=p;
	    bucketNo=b;
	}
	int [] asPair() {
	    return new int[] {pieceId, bucketNo};
	}
	public String toString() { return "(" + pieceId + ", " + bucketNo+ ")";  }
    }


    /** Description of the rules in English */
    String inferredRules;
    public String getInferredRules() { return inferredRules; }
    public void setInferredRules(String _inferredRules) { inferredRules = _inferredRules; }

    /** Description of the rules in our formal language  */
    String inferredRulesFormal;
    public String getInferredRulesFormal() { return inferredRulesFormal; }
    public void setInferredRulesFormal(String _inferredRulesFormal) { inferredRulesFormal = _inferredRulesFormal; }

    


    public static class CompletedMove {
	String actualResponse;
	String expectedResponse;
	int id;
	int bucketId;

	public String getActualResponse() { return actualResponse; }
        public void setActualResponse(String _actualResponse) { actualResponse = _actualResponse; }
        public String getExpectedResponse() { return expectedResponse; }
        public void setExpectedResponse(String _expectedResponse) { expectedResponse = _expectedResponse; }
        public int getId() { return id; }
        public void setId(int _id) { id = _id; }
        public int getBucketId() { return bucketId; }
        public void setBucketId(int _bucketId) { bucketId = _bucketId; }
	
    }

    public static class ProposedMove {
	int id;
	int bucketId;
        public int getId() { return id; }
        public void setId(int _id) { id = _id; }
        public int getBucketId() { return bucketId; }
        public void setBucketId(int _bucketId) { bucketId = _bucketId; }
    }

    CompletedMove[][] inferredRulesAppliedToOldEpisodes;
    ProposedMove[][] proposedMoves;

    public CompletedMove[][] getInferredRulesAppliedToOldEpisodes() { return inferredRulesAppliedToOldEpisodes; }
    public void setInferredRulesAppliedToOldEpisodes(CompletedMove[][] _inferredRulesAppliedToOldEpisodes) { inferredRulesAppliedToOldEpisodes = _inferredRulesAppliedToOldEpisodes; }
    public ProposedMove[][] getProposedMoves() { return proposedMoves; }
    public void setProposedMoves(ProposedMove[][] _proposedMoves) { proposedMoves = _proposedMoves; }

    /** Parses the response in prepared-episodes mode, or the final
       response in play mode.
       
       @param line A string representing the JSON content of the
	response received from Gemini for our final request */
    static PreparedEpisodesResponse parseResponse(String line) throws 	 ReflectiveOperationException {
	StringReader sr = new StringReader(line);
	JsonReader jsonReader = Json.createReader(sr);
	JsonObject obj = jsonReader.readObject();

	PreparedEpisodesResponse per = new 	PreparedEpisodesResponse();
	JsonToJava.json2java(obj, per);
	return per;
    }

    /** After a PreparedEpisodesResponse has been created with
	parseResponse(line), this method gets from it the list of
	proposed moves.
     */
    MoveLine[][] getMoves() throws 	 ReflectiveOperationException {

	if (proposedMoves==null) {
	    System.out.println("No proposedMoves included in the response!");
	    return null;	      
	}
	
	MoveLine rr[][] = new MoveLine[ proposedMoves.length][];
	for(int k=0; k<rr.length; k++) {
	    rr[k] = new MoveLine[ proposedMoves[k].length];
	    int j=0;
	    for(ProposedMove m: proposedMoves[k]) {
		rr[k][j++] = new MoveLine(m.id, m.bucketId);
	    }
	}
	return rr;
    }


    /** @param line A string representing the JSON content of the
	single-move response received from Gemini during the play
	phase */
    static MoveLine parseMoveResponse(String line) throws 	 ReflectiveOperationException {
	StringReader sr = new StringReader(line);
	JsonReader jsonReader = Json.createReader(sr);
	JsonObject obj = jsonReader.readObject();

	ProposedMove m = new ProposedMove();
	m.setId(-1);
	JsonToJava.json2java(obj, m);

	if (m.id<0) {
	    System.out.println("No move info found in the response! obj="+obj);
	    return null;	      
	}
	
	MoveLine r = new MoveLine(m.id, m.bucketId);
	return r;
    }

    
    /** Unit test */
    public static void main(String argv[]) throws IOException, ReflectiveOperationException  {
	String s = Util.readTextFile(new File(argv[0]));
	PreparedEpisodesResponse per = PreparedEpisodesResponse.parseResponse(s);
	MoveLine[][] moves =  per.getMoves();
	System.out.println("Found moves for " + moves.length + " episodes");
    }
    
}
