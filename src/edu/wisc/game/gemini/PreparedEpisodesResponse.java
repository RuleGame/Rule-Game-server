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

import edu.wisc.game.gemini.GeminiPlayer.MoveLine;

public class PreparedEpisodesResponse {
    String inferredRules;
    public String getInferredRules() { return inferredRules; }
    public void setInferredRules(String _inferredRules) { inferredRules = _inferredRules; }

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

    CompletedMove[][] inferredRulesAppliedToTheCompletedEpisodes;
    ProposedMove[][] proposedMoves;

    public CompletedMove[][] getInferredRulesAppliedToTheCompletedEpisodes() { return inferredRulesAppliedToTheCompletedEpisodes; }
    public void setInferredRulesAppliedToTheCompletedEpisodes(CompletedMove[][] _inferredRulesAppliedToTheCompletedEpisodes) { inferredRulesAppliedToTheCompletedEpisodes = _inferredRulesAppliedToTheCompletedEpisodes; }
    public ProposedMove[][] getProposedMoves() { return proposedMoves; }
    public void setProposedMoves(ProposedMove[][] _proposedMoves) { proposedMoves = _proposedMoves; }

    static MoveLine[][] parseResponse(String line) throws 	 ReflectiveOperationException {
	StringReader sr = new StringReader(line);
	JsonReader jsonReader = Json.createReader(sr);
	JsonObject obj = jsonReader.readObject();

	PreparedEpisodesResponse per = new 	PreparedEpisodesResponse();
	JsonToJava.json2java(obj, per);


	if (per.proposedMoves==null) {
	    System.out.println("No proposedMoves included in the response!");
	    return null;	      
	}
	
	MoveLine rr[][] = new MoveLine[ per.proposedMoves.length][];
	for(int k=0; k<rr.length; k++) {
	    rr[k] = new MoveLine[ per.proposedMoves[k].length];
	    int j=0;
	    for(ProposedMove m: per.proposedMoves[k]) {
		rr[k][j++] = new MoveLine(m.id, m.bucketId);
	    }
	}
	return rr;
    }

    /** Unit test */
    public static void main(String argv[]) throws IOException, ReflectiveOperationException  {
	String s = Util.readTextFile(new File(argv[0]));
	MoveLine[][] moves = parseResponse(s);
	System.out.println("Found moves for " + moves.length + " episodes");
    }
    
}
