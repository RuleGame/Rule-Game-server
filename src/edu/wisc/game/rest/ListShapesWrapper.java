package edu.wisc.game.rest;

import java.io.*;
import java.util.*;
import javax.json.*;
import javax.persistence.*;


import javax.xml.bind.annotation.XmlElement; 
import javax.xml.bind.annotation.XmlRootElement;

import edu.wisc.game.util.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.sql.*;


/** Lists all available shapes */
public class ListShapesWrapper extends ResponseBase {
    Vector<String> values=null;
    public Vector<String> getValues() { return values; }
    @XmlElement
    public void setValues(Vector<String> _values) { values = _values; }
    public  ListShapesWrapper() {
	try {
	    values = Files.listAllShapesRecursively();
	} catch(Exception ex) {
            setError(true);
            setErrmsg(ex.getMessage());
            System.err.print(ex);
            ex.printStackTrace(System.err);
        }     
    }
}
