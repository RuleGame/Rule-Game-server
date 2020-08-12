package edu.wisc.game.rest;

import java.io.*;
import java.util.*;
import java.text.*;
import java.net.*;
import javax.persistence.*;

import org.apache.openjpa.persistence.jdbc.*;

import javax.xml.bind.annotation.XmlElement; 
import javax.xml.bind.annotation.XmlRootElement;


import edu.wisc.game.util.*;


@XmlRootElement(name = "report") 


public class FileWriteReport {
    boolean error;
    String errMsg;
    String path;
    long byteCnt;

    FileWriteReport() {
	error=false;
	errMsg=null;
	path=null;
	byteCnt=0;
    }

    FileWriteReport(File f, long _byteCnt) {
	this();
	path=f.getPath();
	byteCnt= _byteCnt;
    }

    public boolean getError() { return error; }
    @XmlElement
    public void setError(boolean _error) { error = _error; }
    
    public String getErrMsg() { return errMsg; }   
    @XmlElement
    public void setErrMsg(String _errMsg) { errMsg = _errMsg; }
	
    public String getPath() { return path; }    
    @XmlElement
    public void setPath(String _path) { path = _path; }
    
    public long getByteCnt() { return byteCnt; }
    @XmlElement
    public void setByteCnt(long _byteCnt) { byteCnt = _byteCnt; }

    
}
