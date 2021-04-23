package edu.wisc.game.rest;

import java.io.*;
import java.util.*;
import java.text.*;
import java.net.*;
//import javax.persistence.*;

//import org.apache.openjpa.persistence.jdbc.*;

import javax.xml.bind.annotation.XmlElement; 
import javax.xml.bind.annotation.XmlRootElement;

import edu.wisc.game.util.*;

//@XmlRootElement(name = "report") 
/** This data structure is converted to JSON and send to the client in response to the /writeFile web API call. */
public class FileWriteReport extends ResponseBase {
    String path;
    long byteCnt;

    FileWriteReport() {
	error=false;
	errmsg=null;
	path=null;
	byteCnt=0;
    }

    FileWriteReport(boolean _error, String msg) {
	super(_error, msg);
    }

    
    FileWriteReport(File f, long _byteCnt) {
	this();
	path=f.getPath();
	byteCnt= _byteCnt;
    }

	
    public String getPath() { return path; }    
    @XmlElement
    public void setPath(String _path) { path = _path; }
    
    public long getByteCnt() { return byteCnt; }
    @XmlElement
    public void setByteCnt(long _byteCnt) { byteCnt = _byteCnt; }

    
}
