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

/** 2026-02-01: After each "properties": {...}, need to add
    "required": ["inferredRules", "inferredRulesAppliedToTheCompletedEpisodes"]
 */
class ResponseSchemaUtil {
    /*
        "responseSchema": {
            "type": "OBJECT",
	    "properties": {
		"inferredRules": {
		    "type": "STRING",
		    "description": "Please describe here the hidden rules that best explain all completed episodes shown to you."},
		"InferredRulesAppliedToTheCompletedEpisodes": {
		    "type": "ARRAY",
		    "description": "Here you should put the results of the application of the inferred rules to all the completed episodes from which they have been inferred. Each item in this array should correspond to one completed episode."
          "items": {
              "type": "ARRAY",
	       "description": "Here you should put the results of the application of the inferred rules to one of the completed episodes. Each element of the array corresponds to one move attempt",
               "items": {
                    "type":"OBJECT",
                    "description": "Looking at one of Bob's move attempts, what was the actual outcome (as found in the transcript given to you), and what would be the outcome of this move if the hidden rules were the same as those inferred by you?"
                   "properties": {
                       "id":   { "type": "INTEGER", "description": "The object ID of the object Bob wanted to move"},                    
                       "bucketId":   { "type": "INTEGER", "description": "The ID of the bucket into which Bob wanted to move the object"},                    
                       "actualResponse": { "type": "string",
 "enum": ["ACCEPT", "DENY", "IMMOVABLE"], 
"description": "This is the response Bob actually received from the oracle"},                          "responseGivenByInferredRules": { "type": "string",
 "enum": ["ACCEPT", "DENY", "IMMOVABLE"], 
																	 "description": "This is the response that Bob would have received from the oracle if the oracle had been guided by the rules you have inferred"},
		       "matched": { "type": "boolean", "description": "True if the actual response of the oracle was the same as wwhat it would have returned if it had been guided by your inferred rules" }
		   },
		   "propertyOrdering": { "objectID",  "bucketID", "actualResponse",  "responseGivenByInferredRules", "matched" }

	       }}},
		"ProposedMoves": {
		    "type": "ARRAY",
		    "description": "Here you should put your proposed moves which should clear the new board without any errors. Each element of the array corresponds to one move attempt",
		    "items": {
                        "type": "OBJECT",
			"description": "Here you should describe one proposed move",
			"properties": {
			    "id":   { "type": "INTEGER", "description": "The object ID of the object you want to move"},                    
			    "bucketId":   { "type": "INTEGER", "description": "The ID of the bucket into which you want to move the object"}}}}                    
	    }}
    */
    /*
    JsonArrayBuilder mkEnumAb1() {
	JsonArrayBuilder ab = Json.createArrayBuilder();
	for(String x: {"ACCEPT", "DENY", "IMMOVABLE"}) {
	    ab.add(x);
	}
	return ab;
    }
    */

    
    static JsonObjectBuilder schemaInteger(String desc) {
	JsonObjectBuilder ob = Json.createObjectBuilder().
	    add("type", "INTEGER").
	    add( "description", desc);
	return ob;
    }

    static JsonObjectBuilder schemaObject(String desc, MyJsonObjectBuilder prop) {
	JsonObjectBuilder ob = Json.createObjectBuilder().
	    add("type", "OBJECT");
	if (desc!=null) ob.add("description", desc);
	ob.add("properties", prop);
	// zzz
	// 2026-02-01: need "required"
	ob.add("required", prop.listNames());
	return ob;
    }
	
    static JsonObjectBuilder schemaArray(String desc, JsonObjectBuilder items) {
	JsonObjectBuilder ob = Json.createObjectBuilder().
	    add("type", "ARRAY").
	    add("description", desc).
	    add("items", items);
	return ob;
    }

    /*                        "actualResponse": { "type": "string",
 "enum": ["ACCEPT", "DENY", "IMMOVABLE"], 
"description": "This is the response Bob actually received from the oracle"}, 

    */
    

    static JsonObjectBuilder schemaString(String desc) {	
	return Json.createObjectBuilder().
	    add("type", "string").
	    add("description", desc);
    }

    static JsonObjectBuilder schemaEnum(String desc, String[] enumValues) {
	JsonArrayBuilder ab = Json.createArrayBuilder();
	for(String x: enumValues) {
	    ab.add(x);
	}

	return schemaString(desc).
	    add("enum", ab);
    }

    
    /*
	"ProposedMoves": {
		    "type": "ARRAY",
		    "description": "Here you should put your proposed moves which should clear the new board without any errors. Each element of the array corresponds to one move attempt",
		    "items": {
                        "type": "OBJECT",
			"description": "Here you should describe one proposed move",
			"properties": {
			    "id":   { "type": "INTEGER", "description": "The object ID of the object you want to move"},                    
			    "bucketId":   { "type": "INTEGER", "description": "The ID of the bucket into which you want to move the object"}}}}                    
	    }}
    */
    static JsonObjectBuilder mkProposedMovesOb() {
	
	JsonObjectBuilder idOb = schemaInteger("The object ID of the object you want to move");

	JsonObjectBuilder bidOb = schemaInteger("The ID of the bucket into which you want to move the object");                    
	
	JsonObjectBuilder itemsOb = schemaObject("Here you should describe one proposed move",
						 (new MyJsonObjectBuilder()).
						 add("id", idOb).
						 add("bucketId", bidOb));

	JsonObjectBuilder oneEpisodeOb = schemaArray("Here you should put your proposed moves for one future episode, which should clear the episode's board without any errors. Each element of the array corresponds to one move attempt. The number of moves in this array should be equal to the number of objects on the episode's board",
			   itemsOb);
	
	return schemaArray("Here you should put your proposed moves for all future episodes. Each element of the array corresponds to one future episode",
			   oneEpisodeOb);
	    	
    }


    /**
		"InferredRulesAppliedToTheCompletedEpisodes": {
		    "type": "ARRAY",
		    "description": "Here you should put the results of the application of the inferred rules to all the completed episodes from which they have been inferred. Each item in this array should correspond to one completed episode."
          "items": {
              "type": "ARRAY",
	       "description": "Here you should put the results of the application of the inferred rules to one of the completed episodes. Each element of the array corresponds to one move attempt",
               "items": {
                    "type":"OBJECT",
                    "description": "Looking at one of Bob's move attempts, what was the actual outcome (as found in the transcript given to you), and what would be the outcome of this move if the hidden rules were the same as those inferred by you?"
                   "properties": {
                       "id":   { "type": "INTEGER", "description": "The object ID of the object Bob wanted to move"},                    
                       "bucketId":   { "type": "INTEGER", "description": "The ID of the bucket into which Bob wanted to move the object"},                    
                       "actualResponse": { "type": "string",
 "enum": ["ACCEPT", "DENY", "IMMOVABLE"], 
"description": "This is the response Bob actually received from the oracle"},
                          "expectedResponse": { "type": "string",
 "enum": ["ACCEPT", "DENY", "IMMOVABLE"], 
																	 "description": "This is the response that Bob would have received from the oracle if the oracle had been guided by the rules you have inferred"},
		       "matched": { "type": "boolean", "description": "True if the actual response of the oracle was the same as wwhat it would have returned if it had been guided by your inferred rules" }
		   },
		   "propertyOrdering": { "objectID",  "bucketID", "actualResponse",  "responseGivenByInferredRules", "matched" }

	       }}},
    */
    static JsonObjectBuilder mkAppliedMovesOb() {
	//"enum": ["ACCEPT", "DENY", "IMMOVABLE"], 
	//	JsonArrayBuilder enumAb = mkEnumAb1();


	JsonObjectBuilder idOb = schemaInteger("The object ID of the object Bob wanted to move");

	JsonObjectBuilder bidOb = schemaInteger("The ID of the bucket into which Bob wanted to move the object");                    
	
	JsonObjectBuilder actualResponseOb = schemaEnum( "This is the response Bob actually received from the oracle",
						   new String[] {"ACCEPT", "DENY", "IMMOVABLE"});

	JsonObjectBuilder expectedResponseOb = schemaEnum( "This is the response that Bob would have received from the oracle if the oracle had been guided by the rules you have inferred",
							   new String[] {"ACCEPT", "DENY", "IMMOVABLE"});

	
	JsonObjectBuilder itemsOb = schemaObject( "Looking at one of Bob's move attempts, what was the actual outcome (as found in the transcript given to you), and what would be the outcome of this move if the hidden rules were the same as those inferred by you?",
						  (new MyJsonObjectBuilder()).
						  add("id", idOb).
						  add("bucketId", bidOb).
						  add("actualResponse", actualResponseOb).
						  add("expectedResponse", expectedResponseOb));


	JsonObjectBuilder oneEpisodeOb = schemaArray( "Here you should put the results of the application of the inferred rules to one of the completed episodes. Each element of the array corresponds to one move attempt",
						    itemsOb);
						    
	return schemaArray("Here you should put the results of the application of the inferred rules to all the completed episodes from which they have been inferred. Each item in this array should correspond to one completed episode.",
			   oneEpisodeOb);

    }

    static JsonObjectBuilder mkResponseSchema() {
	return schemaObject(null,
			    (new MyJsonObjectBuilder()).
			    add("inferredRules", schemaString("Please describe here the hidden rules that best explain all completed episodes shown to you")).
			    add("inferredRulesAppliedToTheCompletedEpisodes", mkAppliedMovesOb()).
			    add("proposedMoves", mkProposedMovesOb()));
			     
    }

    //   MoveLine[] parseResponse(String line) {    }    
    
}
