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
    Vector&lt;SomeClass&gt;), this tool would not know what kind of
    objects it is to be filled with. */
public class JsonToJava {

    /** Fills the fields of Java object dest from JSON structure jo */
    public static void json2java(JsonObject jo, Object dest) throws ReflectiveOperationException {
	Reflect r = Reflect.getReflect(  dest.getClass());
		
	for(String key: jo.keySet()) {
	    JsonValue val = jo.get(key);
	    Reflect.Entry e = r.getEntry(key);
	    if (key==null) {
		//System.err.println("Skipping JSON field: " + key);
		continue;
	    }

	    Class c = e.f.getType();
	    //	    System.err.println("key=" + key +", val="+val+", Java class=" + c);
	    
	    
	    Object z=null;
	    if (val instanceof JsonObject) {
		z = c.newInstance();
		json2java((JsonObject)val, z);
	    } else if (val instanceof JsonArray) {
		JsonArray q = (JsonArray)val;
		if (c.isArray()) {
		    Class cc = c.getComponentType();
		    z = Array.newInstance(cc, q.size());
		    for(int j=0; j<q.size(); j++) {
			JsonObject x = q.getJsonObject(j);
			Object w = cc.newInstance();
			Array.set(z, j, w);
			json2java(x, w);
		    }
		} else if (Collection.class.isAssignableFrom(c)) {
		    ParameterizedType ty = (ParameterizedType)e.f.getGenericType();
		    Type[] fieldArgTypes = ty.getActualTypeArguments();
		    if (fieldArgTypes.length!=1) throw new IllegalArgumentException("Cannot figure element type for " + c);
		    Class fieldArgClass = (Class) (fieldArgTypes[0]);
		    z = c.newInstance();
		    Collection col = (Collection)z;
		    for(int j=0; j<q.size(); j++) {
			JsonObject x = q.getJsonObject(j);
			Object w = fieldArgClass.newInstance();
			col.add(w);
			json2java(x, w);
		    }
		}
	    } else if (val instanceof JsonNumber) {
		JsonNumber q = (JsonNumber)val;
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
	    } else if (val instanceof JsonString) {
		JsonString q = (JsonString)val;
		String s= q.getString();
		if (c.isAssignableFrom(String.class)) {
		    z = s;
		} else if (Enum.class.isAssignableFrom(c)) {
		    // Ad hoc: Convert lower-case values in the GUI app's
		    // output to upper-case constants in the server app.
		    z = Enum.valueOf(c, s.toUpperCase());
		} else if (c.isAssignableFrom(Long.class) || c==Long.TYPE) {
		    //  Ad hoc: Some IDs are strings in JSON, but long in Java
		    // Let's check if the string is really a number then...
		    try {
			z = new Long(s);
		    } catch(NumberFormatException ex) {}
		} else if (val == JsonValue.TRUE) {
		    z = true;		    
		} else if (val == JsonValue.FALSE) {
		    z = false;		    
		} else {
		    System.err.println("No support for string " + q + " to " + c);
		}
	    } 

	    if (z!=null) {
		e.s.invoke( dest, z);
	    }	    
	}

	//System.err.println("Json2Java: input={" + jo + "}, dest.class=" + dest.getClass() +", output={"+dest+"}");


    }

    
}
