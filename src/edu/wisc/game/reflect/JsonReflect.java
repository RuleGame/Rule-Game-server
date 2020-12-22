package edu.wisc.game.reflect;

import java.util.*;
import java.text.*;
import javax.json.*;

import java.lang.reflect.*;
import edu.wisc.game.util.Logging;

/** Tools for exporting Java objects as JSON structures */
public class JsonReflect {

    /* @param g String, Integer, or an arbitrary object */
    private JsonValue toJsonValue(Object g) {
	//	System.out.println("toJsonValue(" + g.getClass()+")");
	JsonArrayBuilder arrayBuilder= toJsonArrayBuider(g);
	JsonArray ar =arrayBuilder.build();
	List<JsonValue>	list = ar.getValuesAs(JsonValue.class);
	return list.size()>0?  list.get(0) :  null;
    }

    /** Creates a JsonArrayBuider with one element. This will make it 
	possible to later extract that element as a JsonValue! 
	@param An arbitrary Java object (could be Integer, Map, Vector
	or anything else)
    */
    private JsonArrayBuilder  toJsonArrayBuider(Object g) {
	JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
	//	System.out.println("toJsonArrayBuider(" + g.getClass()+")");
	if (g instanceof String) {
	    arrayBuilder.add( (String)g);
	} else if (g instanceof Integer) {
	    arrayBuilder.add( (Integer)g);
	} else if (g instanceof Long) {
	    arrayBuilder.add( (Long)g);
	} else if (g instanceof Float) {
	    arrayBuilder.add( (Float)g);
	} else if (g instanceof Double) {
	    arrayBuilder.add( (Double)g);
	} else if (g instanceof Boolean) {
	    arrayBuilder.add( (Boolean)g);
	} else if (g instanceof Enum) {
	    arrayBuilder.add( g.toString());
	} else if (g instanceof Date) {
	    arrayBuilder.add( dateToJsonString((Date)g));
	} else if (g.getClass().isArray()) { // an array
	    JsonArrayBuilder x = doCollection(arrayX2vector(g));
	    arrayBuilder.add(x);		
	    //	} else if (g instanceof Array) { // an array
	    //	    JsonArrayBuilder x = doCollection(array2vector((Array)g));
	    //	    arrayBuilder.add(x);		
	} else if (g instanceof Collection) {
	    JsonArrayBuilder ab = doCollection((Collection)g);
	    arrayBuilder.add(ab);
	} else if (g instanceof Map) {
	    Map h = (Map)g;
	    JsonObjectBuilder ob = doMap(h);
	    arrayBuilder.add(ob);
	} else { // some object
	    JsonObjectBuilder ob = reflectToJSON(g);
	    //	    System.out.println("Treating val=("+g+") as 'some object'");
	    arrayBuilder.add(ob);
	}
	return arrayBuilder;
    }
    
    private JsonArrayBuilder doCollection(Collection col) {
	JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();

	//	System.out.println("doCollection, "+col.getClass()+", size=" + col.size());
	
	for(Object g: col) {
	    if (g==null) {
		if (skipNulls) continue;
		else arrayBuilder.addNull();
	    } else {
		JsonArrayBuilder q =  toJsonArrayBuider(g);
		arrayBuilder.addAll(q);
	    }	    
	}
	return  arrayBuilder;
    }
    
    private JsonObjectBuilder doMap(Map h) {
	//System.out.println("Exporting map: " + h);
	JsonObjectBuilder objectBuilder = Json.createObjectBuilder();
	for(Object _key: h.keySet()) {
	    Object val = h.get(_key);
	    String key = _key.toString();
	    //System.out.println("key="  + key+", val=" + val);
	    if (val==null) {
		if (skipNulls) continue;
		else objectBuilder.addNull(key);
	    } else {
		JsonValue q = toJsonValue(val);
		objectBuilder.add(key, q);
	    }
	}
	return  objectBuilder;
    }

    private final boolean skipNulls;
    private final HashSet<String> excludableNames;
    
    private JsonReflect( boolean _skipNulls, HashSet<String> _excludableNames) {
	skipNulls=_skipNulls;
	excludableNames = _excludableNames;
    }
    
    /** Converts a Java object to a JSON object, to the extent possible */
    public JsonObjectBuilder reflectToJSON(Object o) {
	
	JsonObjectBuilder objectBuilder = Json.createObjectBuilder();
       
	Reflect r = Reflect.getReflect(  o.getClass());
	//	System.out.println("Reflecting on object "+o	+", class="+ o.getClass() +"; reflect=" + r + ", has " + r.entries.length + " entries");
	for(Reflect.Entry e: r.entries) {
	    if (excludableNames!=null && excludableNames.contains(e.name)) continue;
	    Object val = null;
	    try {
		val = e.g.invoke(o);
	    } catch (IllegalAccessException ex) {
		Logging.error(ex.getMessage());
		val = "ACCESS_ERROR";
	    } catch (InvocationTargetException ex) {
		Logging.error(ex.getMessage());
		val = "INVOCATION_TARGET_ERROR";
	    }

	    if (skipNulls && (val==null || val.toString().equals(""))) continue;
	    if (skipNulls && e.name.equals("version")) continue;
	    
	    Class c = val.getClass();
	    //System.out.println("name=" + e.name +", val("+c      +"; "+c.getName()+"; isArray"+c.isArray()+")=" + val);

	    if (val==null) {
		if (skipNulls) continue;
		else objectBuilder.addNull(e.name);
	    } else  if (c.isArray() && c.isInstance(new int[0])) { // an array int[]
		JsonArrayBuilder ab = doCollection(arrayInt2vector((int[])val));
		objectBuilder.add(e.name,ab);		
	    } else  if (c.isArray() && c.isInstance(new double[0])) { // an array double[]
		JsonArrayBuilder ab = doCollection(arrayDouble2vector((double[])val));
		objectBuilder.add(e.name,ab);		
	    } else  if (c.isArray()) {
		//throw new IllegalArgumentException("Sorry, don't know what to do with arbitrary arrays, eh?");
		JsonArrayBuilder ab = doCollection(arrayX2vector(val));
		objectBuilder.add(e.name,ab);		
	    } else {
		JsonValue q = toJsonValue(val);
		objectBuilder.add(e.name, q);
	    }
	}
	return objectBuilder;
    }

    static private Vector array2vector(Array a) {
	Vector v = new Vector();
	for(int i=0; i<Array.getLength(a); i++) {
	    v.add( Array.get(a,i));
	}
	return v;
    }

    /** It is known that a.getClass() is an Array */
    static private Vector arrayX2vector(Object a) {
	Vector v = new Vector();
	for(int i=0; i<Array.getLength(a); i++) {
	    v.add( Array.get(a,i));
	}
	return v;
    }

    // FIXME: need this for other primitive types (besides int and double) too!
    static private Vector arrayInt2vector(int[] a) {
	Vector v = new Vector();
	for(int i=0; i<a.length; i++) {
	    v.add(a[i]);
	}
	return v;
    }
    static private Vector arrayDouble2vector(double[] a) {
	Vector v = new Vector();
	for(int i=0; i<a.length; i++) {
	    v.add(a[i]);
	}
	return v;
    }

    /** Based on 
    http://www.java2s.com/Code/Java/Data-Type/ISO8601dateparsingutility.htm


    // The REST ASPI gives me this: "2020-11-13T16:24:03.685Z[UTC]"
    // This method  gave me this:   "2020-11-13T18:35+00:00"
    //  JavaScript understands this:
    // var d = new Date("2015-03-25T12:00:00Z");
    // var d = new Date("2015-03-25T12:00:00-06:30");
    */
    private static String dateToJsonString( Date date ) {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssz");
        TimeZone tz = TimeZone.getTimeZone( "UTC" );
        df.setTimeZone( tz );
        String output = df.format( date );
        int inset0 = 9;
        int inset1 = 6;
        
        String s0 = output.substring(0, output.length()-inset0);
        String s1 = output.substring(output.length()-inset1, output.length());
	
        //String result = s0 + s1;
        String result = output;
        //result = result.replaceAll( "UTC", "+00:00" );     
        return result;
        
    }

    
    /** Converts a Java object to a JSON object, to the extent possible */
    public static JsonObject reflectToJSONObject(Object o, boolean skipNulls) {
	JsonReflect r = new JsonReflect(skipNulls, null);
	return r.reflectToJSON(o).build();
    }
    
    /** Converts a Java object to a JSON object, to the extent possible.
	@param o Object to convert
	@param skipNulls If true, the output won't contain the fields that have null values in o
	@param  excludableNames If not null, contains the set of field names that should be ignored.
     */
    public static JsonObject reflectToJSONObject(Object o, boolean skipNulls, HashSet<String> excludableNames) {
	JsonReflect r = new JsonReflect(skipNulls, excludableNames);
	return r.reflectToJSON(o).build();
    }


    
}
