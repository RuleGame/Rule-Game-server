package edu.wisc.game.reflect;

import java.util.*;
import java.text.*;
import javax.persistence.*;
import javax.json.*;

import java.lang.reflect.*;
import edu.wisc.game.util.Logging;

/** Tools for importing Java objects from JSON structures. The
    conversion is guided by the structure of the destination Java
    objects. Not every Java object can be deserialized by this
    technique; for example, if a Java object has a field of the type
    Vector (or Vector&lt;Object&gt;), rather than (or
    Vector&lt;SomeClass&gt;), this tool will not know what kind of
    objects it is to be filled with. */
public class JsonToJava {


    
    /** Fills a hash map from a JSON object */
    /*
    private static void json2map(JsonObject jo, HashMap dest) throws ReflectiveOperationException {

	//ParameterizedType ty = (ParameterizedType)e.f.getGenericType();
	Type[] fieldArgTypes = dest.getClass().getActualTypeArguments();
	if (fieldArgTypes.length!=2) throw new IllegalArgumentException("Cannot figure element type for " + dest);
	Class valClass = (Class) (fieldArgTypes[1]);
	Reflect r = Reflect.getReflect(  valClass);
	
	for(String key: jo.keySet()) {
	    JsonValue val = jo.get(key);
	    Object z= doJsonValue(val, e);

	    if (z!=null) {
		dest.put(key, val);
	    }
	}
    }
    */
    
    /** Fills the fields of Java object dest from JSON structure jo.
	@param jo A JSON object (e.g. read from disk, or received via internet)
	@param dest A Java object  whose fields will be set, recursively,
	from the JSON objects. This will be done by invoking the appropriate
	setter methods of the relevant fields. 
     */
    public static Object json2java(JsonObject jo, Object dest) throws ReflectiveOperationException {
	return doJsonValue(jo, dest, dest.getClass());
    }


    
    /** Creates a Java object and fills it with the data from a JsonValue
	@param val Input data
	@param f Describes the structure of the destination class, an 
	entity of which will be created to house the data. 
    */
    /*
    private static Object doJsonValue(JsonValue val, Field f)  throws ReflectiveOperationException {
	Class c = f.getType();
	Object z=null;
	//	    System.err.println("key=" + key +", val="+val+", Java class=" + c);

	if (Map.class.isAssignableFrom(c)) {
	    if (!(val instanceof JsonObject)) throw new IllegalArgumentException("Only JsonObject values can be mapped to Java Maps");

	    ParameterizedType ty = (ParameterizedType)f.getGenericType();
	    Type[] fieldArgTypes = ty.getActualTypeArguments();
	    if (fieldArgTypes.length!=2) throw new IllegalArgumentException("Cannot figure key and value type for map " + c);
	    System.out.println("DEBUG: map("+fieldArgTypes[0]+", " +fieldArgTypes[1]+")");
	    Class keyClass = (Class) (fieldArgTypes[0]);
	    Class valClass = (Class) (fieldArgTypes[1]);
	    z = c.newInstance();

	    JsonObject jo = (JsonObject)val;
	    for(String key: jo.keySet()) {
		Object mapKey = understandJsonString(key, keyClass);
		JsonValue uval = jo.get(key);
		Object w;
		if (isPrimitive(uval)) {
		    w = understandJsonPrimitive(uval,  valClass);
		} else {
		    w = valClass.newInstance();
		    json2java((JsonObject)uval, w);
		}
		((Map)z).put( mapKey, w);
	    }
	} else if (val instanceof JsonObject) {
	    //System.err.println("Creating an instance of " + c +"; e=" + e +"; val=" + val);
	    z = c.newInstance();
	    json2java((JsonObject)val, z);
	} else if (val instanceof JsonArray) {
	    JsonArray q = (JsonArray)val;
	    if (c.isArray()) {
		Class cc = c.getComponentType();
		z = Array.newInstance(cc, q.size());
		for(int j=0; j<q.size(); j++) {
		    Object w;
		    //System.err.println("Converting to " + cc);
		    if (Number.class.isAssignableFrom(cc) ||
			isPrimitiveNumberClass(cc)) {
			w =  jsonNumber2java(q.getJsonNumber(j), cc);
		    } else {
			w = cc.newInstance();
			json2java(q.getJsonObject(j), w);
		    }
		    Array.set(z, j, w);
		}
	    } else if (Collection.class.isAssignableFrom(c)) {
		ParameterizedType ty = (ParameterizedType)f.getGenericType();
		Type[] fieldArgTypes = ty.getActualTypeArguments();
		if (fieldArgTypes.length!=1) throw new IllegalArgumentException("Cannot figure element type for " + c);
		Class fieldArgClass = (Class) (fieldArgTypes[0]);
		z = c.newInstance();
		Collection col = (Collection)z;
		for(int j=0; j<q.size(); j++) {
		    Object w;
		    if (Number.class.isAssignableFrom(fieldArgClass) ||
			isPrimitiveNumberClass(fieldArgClass)) {
			w =  jsonNumber2java(q.getJsonNumber(j), fieldArgClass);
		    } else {
			w = fieldArgClass.newInstance();
			json2java(q.getJsonObject(j), w);
		    }
		    col.add(w);
		}
	    }
	} else if (isPrimitive(val)) {
	    return understandJsonPrimitive(val, c);
	}
	return z;
    }
    */
    /*---------------------------------------------------------*/

    /** Creates a Java object and fills it with the data from a JsonValue
	@param val Input data
	@param z A Java object, which is either directly used as a
	destination into which the data are to be put, or (if immutable:
	Integer, Date, etc) as a type indicator. It can be null sometimes,
	(for Integer etc), in which case we need to rely on ty.
	@param ty Additional type information, which is only used when
	it is needed to compensate for type erasure (e.g. if z is a
	Collection or Map)
    */
    private static Object doJsonValue(JsonValue val, Object z, Type ty)  throws ReflectiveOperationException {
	Class c = (z!=null)? z.getClass(): (Class)ty;
	//	System.err.println("DEBUG: val="+val+", type=" + ty);

	if (Map.class.isAssignableFrom(c)) {
	    if (!(val instanceof JsonObject)) throw new IllegalArgumentException("Only JsonObject values can be mapped to Java Maps");
	    ParameterizedType pty = (ParameterizedType)ty;
	    Type[] fieldArgTypes = pty.getActualTypeArguments();
	    if (fieldArgTypes.length!=2) throw new IllegalArgumentException("Cannot figure key and value type for map " + c);
	    //	    System.out.println("DEBUG: map("+fieldArgTypes[0]+", " +fieldArgTypes[1]+")");
	    Class keyClass = (Class) (fieldArgTypes[0]);

	    Type valType=fieldArgTypes[1];	
	    Class valClass;
	    if (fieldArgTypes[1] instanceof Class) { // e.g. Integer
		//System.out.println("DEBUG: casting " + fieldArgTypes[1]+" to class");
		valClass = (Class)(fieldArgTypes[1]);
	    } else {
		ParameterizedType w =  (ParameterizedType)valType;
		//System.out.println("DEBUG:  getRawType=" + w.getRawType());
		//Class valClass = (Class) (fieldArgTypes[1]);
		valClass = (Class) (w.getRawType());
	    }

	    JsonObject jo = (JsonObject)val;
	    for(String key: jo.keySet()) {
		Object mapKey = understandJsonString(key, keyClass);
		JsonValue uval = jo.get(key);
		Object w = valClass.newInstance();
		w = doJsonValue(uval, w, valType);
		((Map)z).put( mapKey, w);
	    }
	} else if (isPrimitive(val)) {
	    //System.out.println("DEBUG: primitive jval=" +val+", c=" + c);
	    z = understandJsonPrimitive(val, c);
	    //System.out.println("gives z=" + z);
	} else if (val instanceof JsonObject) {
	    JsonObject jo = (JsonObject)val;
	    Reflect r = Reflect.getReflect( z.getClass());
		
	    for(String key: jo.keySet()) {
		JsonValue uval = jo.get(key);
		Reflect.Entry e = r.getEntry(key);
		if (e==null) {
		    System.err.println("json2java: Skipping JSON field: " + key);
		    continue;
		}
		Class uc = e.f.getType();
		//ParameterizedType uty = (ParameterizedType)e.f.getGenericType();
		Object w=null;
		try {
		    w = uc.newInstance();
		} catch(InstantiationException ex) {
		    // Integer or Enum etc cannot be instantiated, of course
		}
		w = doJsonValue(uval, w, e.f.getGenericType());

		if (w!=null) {
		    if (e.s==null) {
			throw new ReflectiveOperationException("JSON object has a field for which the Java object has no setter method: " + e.name);
		    }
		    e.s.invoke( z, w);
		}
	    }	    
	} else if (val instanceof JsonArray) {
	    JsonArray q = (JsonArray)val;
	    if (c.isArray()) {
		Class cc = c.getComponentType();
		z = Array.newInstance(cc, q.size());
		for(int j=0; j<q.size(); j++) {
		    Object w = doJsonArrayElement(q, j, cc);
		    Array.set(z, j, w);
		}
	    } else if (Collection.class.isAssignableFrom(c)) {
		Type[] fieldArgTypes = ((ParameterizedType)ty).getActualTypeArguments();
		if (fieldArgTypes.length!=1) throw new IllegalArgumentException("Cannot figure element type for collection " + c);
		Class fieldArgClass = (Class) (fieldArgTypes[0]);
		z = c.newInstance();
		Collection col = (Collection)z;
		for(int j=0; j<q.size(); j++) {
		    Object w = doJsonArrayElement(q, j,fieldArgClass);
		    col.add(w);
		}
	    }
	}
	return z;
    }
   
    /*=========================================================*/
    /** Extracts and converts the j-th element of a JsonArray q.
	@param cc The Java class into which each component should
	be converted.
	// FIXME should also accommodate q.getJsonString, to deal
	with an array of strings
    */
    static private Object doJsonArrayElement(JsonArray q, int j, Class cc) throws ReflectiveOperationException  {
	//System.err.println("Converting to " + cc);
	if (Number.class.isAssignableFrom(cc) ||
	    isPrimitiveNumberClass(cc)) {
	    return jsonNumber2java(q.getJsonNumber(j), cc);
	} else if (cc.isArray() || Collection.class.isAssignableFrom(cc)) {
	    //w = Array.newInstance(cc, q.size());
	    return doJsonValue(q.getJsonArray(j), null, cc);
	} else {
	    Object w = cc.newInstance();   
	    return doJsonValue(q.getJsonObject(j), w, cc);
	}
    }

 

    /** Should one use  understandJsonPrimitive()? */
    static private boolean isPrimitive(JsonValue val) {
	return  (val instanceof JsonNumber) ||
	    (val == JsonValue.TRUE) ||
	    (val == JsonValue.FALSE) ||
	    (val instanceof JsonString);
    }

    /** Should be called only if isPrimitive(val) returned true.
	@param val A Json value that on which isPrimitive() has returned
	true. Can be e.g. "123" or "foo" or 123.
	@param c Destination class. Can be used to help interpret a string
	or a number.
     */
    static Object understandJsonPrimitive(JsonValue val, Class c) throws ReflectiveOperationException {
	if (val instanceof JsonNumber) {
	    return  jsonNumber2java((JsonNumber)val, c);			
	} else if (val == JsonValue.TRUE) {
	    return true;
	} else if (val == JsonValue.FALSE) {
	    return false;
	} else if (val instanceof JsonString) {
	    JsonString q = (JsonString)val;
	    return understandJsonString(q, c);
	} else {
	    throw new ReflectiveOperationException("Expected a primitive JSON value, found " + val );
	    //return null; // ought not to happen
	}
    }

    /** @param c Destination class
	@return An object of a Number class (Long, Integer, Double, etc) 
     */
    private static Object jsonNumber2java(JsonNumber q, Class c) {
	Object z=null;
	if (c.isAssignableFrom(Long.class) || c==Long.TYPE) {
	    z = q.longValue();
	} else	if (c.isAssignableFrom(Integer.class) || c==Integer.TYPE) {
	    z = q.intValue();
	} else	if (c.isAssignableFrom(Double.class)|| c==Double.TYPE) {
	    z = q.doubleValue();
	} else	if (c.isAssignableFrom(Float.class)|| c==Float.TYPE) {
	    z = q.doubleValue();
	} else {
	    System.err.println("No support for number " + q + " to " + c);
	}
	return z;
    }

    private static boolean isPrimitiveNumberClass(Class c) {
	return  c==Long.TYPE|| c==Integer.TYPE|| c==Double.TYPE|| c==Float.TYPE;
    }


    /** Convert a JsonString to a Java object (String, or Integer etc)
	of a desired class.
	@param c The target class */
    static Object understandJsonString(JsonString q, Class c)  throws ReflectiveOperationException{
	return understandJsonString(q.getString(), c);
    }
    
    static Object understandJsonString(String s, Class c) throws ReflectiveOperationException {
	if (c.isAssignableFrom(String.class)) {
	    return s;
	} else if (Date.class.isAssignableFrom(c)) {
	    try {
		return  parseJsonDate(s);
	    } catch (java.text.ParseException ex) {
		throw new ReflectiveOperationException("Could not parse JSON date: " +s);
	    }
	} else if (Enum.class.isAssignableFrom(c)) {
	    // Ad hoc: Convert lower-case values in the GUI app's
	    // output to upper-case constants in the server app.
	    return Enum.valueOf(c, s.toUpperCase());
	} else if (c.isAssignableFrom(Long.class) || c==Long.TYPE) {
	    //  Ad hoc: Some IDs are strings in JSON, but long in Java
	    // Let's check if the string is really a number then...
	    try {
		return new Long(s);
	    } catch(NumberFormatException ex) { return null;}
	} else if (c.isAssignableFrom(Integer.class) || c==Integer.TYPE) {
	    //  Ad hoc: Some IDs are strings in JSON, but long in Java
	    // Let's check if the string is really a number then...
	    try {
		return new Integer(s);
	    } catch(NumberFormatException ex) { return null;}
	} else if (c.isAssignableFrom(Double.class) || c==Double.TYPE) {
	    try {
		return new Double(s);
	    } catch(NumberFormatException ex) { return null;}
	} else if (c.isAssignableFrom(edu.wisc.game.sql.Piece.Color.class)) {
	    return edu.wisc.game.sql.Piece.Color.findColor(s);
	} else if (c.isAssignableFrom(edu.wisc.game.sql.Piece.Shape.class)) {
	    return edu.wisc.game.sql.Piece.Shape.findShape(s);
	} else {
	    System.err.println("No support for string " + s + " to " + c);
	    return null;
	}
    }

    /** Based on 
    http://www.java2s.com/Code/Java/Data-Type/ISO8601dateparsingutility.htm
 

    // 2004-06-14T19:GMT20:30Z
    // 2004-06-20T06:GMT22:01Z

    // http://www.cl.cam.ac.uk/~mgk25/iso-time.html
    //    
    // http://www.intertwingly.net/wiki/pie/DateTime
    //
    // http://www.w3.org/TR/NOTE-datetime
    //
    // Different standards may need different levels of granularity in the date and
    // time, so this profile defines six levels. Standards that reference this
    // profile should specify one or more of these granularities. If a given
    // standard allows more than one granularity, it should specify the meaning of
    // the dates and times with reduced precision, for example, the result of
    // comparing two dates with different precisions.

    // The formats are as follows. Exactly the components shown here must be
    // present, with exactly this punctuation. Note that the "T" appears literally
    // in the string, to indicate the beginning of the time element, as specified in
    // ISO 8601.

    //    Year:
    //       YYYY (eg 1997)
    //    Year and month:
    //       YYYY-MM (eg 1997-07)
    //    Complete date:
    //       YYYY-MM-DD (eg 1997-07-16)
    //    Complete date plus hours and minutes:
    //       YYYY-MM-DDThh:mmTZD (eg 1997-07-16T19:20+01:00)
    //    Complete date plus hours, minutes and seconds:
    //       YYYY-MM-DDThh:mm:ssTZD (eg 1997-07-16T19:20:30+01:00)
    //    Complete date plus hours, minutes, seconds and a decimal fraction of a
    // second
    //       YYYY-MM-DDThh:mm:ss.sTZD (eg 1997-07-16T19:20:30.45+01:00)

    // where:

    //      YYYY = four-digit year
    //      MM   = two-digit month (01=January, etc.)
    //      DD   = two-digit day of month (01 through 31)
    //      hh   = two digits of hour (00 through 23) (am/pm NOT allowed)
    //      mm   = two digits of minute (00 through 59)
    //      ss   = two digits of second (00 through 59)
    //      s    = one or more digits representing a decimal fraction of a second
    //      TZD  = time zone designator (Z or +hh:mm or -hh:mm)
    */
    private static Date parseJsonDate(String input) throws java.text.ParseException {

        //NOTE: SimpleDateFormat uses GMT[-+]hh:mm for the TZ which breaks
        //things a bit.  Before we go on we have to repair this.
        SimpleDateFormat df = new SimpleDateFormat( "yyyy-MM-dd'T'HH:mm:ssz" );
        
        //this is zero time so we need to add that TZ indicator for 
        if ( input.endsWith( "Z" ) ) {
            input = input.substring( 0, input.length() - 1) + "GMT-00:00";
        } else {
            int inset = 6;
        
            String s0 = input.substring( 0, input.length() - inset );
            String s1 = input.substring( input.length() - inset, input.length() );

            //input = s0 + "GMT" + s1;
        }
        
        return df.parse( input );
        
    }
}
