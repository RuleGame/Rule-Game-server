package edu.wisc.game.gemini;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.net.*;
import jakarta.json.*;

class MyJsonObjectBuilder implements JsonObjectBuilder {
	final private JsonObjectBuilder builder = Json.createObjectBuilder();
	private Vector<String> names = new Vector<>();
	
	public MyJsonObjectBuilder	add(java.lang.String name, boolean value) {
	    builder.add(name, value);
	    names.add(name);
	    return this;
	}

	public MyJsonObjectBuilder	add(java.lang.String name, double value) {
	    builder.add(name, value);
	    names.add(name);
	    return this;
	}
	
	public MyJsonObjectBuilder	add(java.lang.String name, int value)  {
	    builder.add(name, value);
	    names.add(name);
	    return this;
	}
	public MyJsonObjectBuilder	add(java.lang.String name, java.math.BigDecimal value)  {
	    builder.add(name, value);
	    names.add(name);
	    return this;
	}
	public MyJsonObjectBuilder	add(java.lang.String name, java.math.BigInteger value)  {
	    builder.add(name, value);
	    names.add(name);
	    return this;
	}
	//Adds a name/JsonNumber pair to the JSON object associated with this object builder.
	public MyJsonObjectBuilder	add(java.lang.String name, JsonArrayBuilder b)  {
	  builder.add(name, b);
	  names.add(name);
	  return this;
	}
	//Adds a name/JsonArray pair to the JSON object associated with this object builder.
	public MyJsonObjectBuilder	add(java.lang.String name, JsonObjectBuilder b)  {
	    builder.add(name, b);
	    names.add(name);
	    return this;
	}
	//Adds a name/JsonObject pair to the JSON object associated with this object builder.
	public MyJsonObjectBuilder	add(java.lang.String name, JsonValue value)   {
	    builder.add(name, value);
	    names.add(name);
	    return this;
	}
	//Adds a name/JsonValue pair to the JSON object associated with this object builder.
	public MyJsonObjectBuilder	add(java.lang.String name, long value)   {
	    builder.add(name, value);
	    names.add(name);
	    return this;
	}
	//Adds a name/JsonNumber pair to the JSON object associated with this object builder.
	public MyJsonObjectBuilder	add(java.lang.String name, java.lang.String value)   {
	    builder.add(name, value);
	    names.add(name);
	    return this;
	}
	//Adds a name/JsonString pair to the JSON object associated with this object builder.
	public MyJsonObjectBuilder	addNull(java.lang.String name)   {
	    builder.addNull(name);
	    names.add(name);
	    return this;
	}
	//Adds a name/JsonValue#NULL pair to the JSON object associated with this object builder where the value is null.
	public JsonObject	build() {
	    return builder.build();
	}


    JsonArrayBuilder listNames() {
    	JsonArrayBuilder ab = Json.createArrayBuilder();
	for(String x: names) {
	    ab.add(x);
	}
	return ab;
    }

}
