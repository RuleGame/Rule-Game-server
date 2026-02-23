package edu.wisc.game.tools;

import java.io.*;
import java.util.*;

import edu.wisc.game.util.*;
import edu.wisc.game.rest.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.saved.*;
import edu.wisc.game.saved.TranscriptManager.ReadTranscriptData;

/** Convenience methods for AnalyzeTranscripts. These methods have
    been put into a separate class so that it can be included into
    captive.jar without having to also load math3 classes.

 */
public class AnalyzeTranscriptsUtils {
/** Splits a section of transcript pertaining to a single rule set (i.e. a series of episodes) into subsections, each subsection pertaining to one specific
	episode.
     */
    static public Vector<ReadTranscriptData.Entry[]> splitTranscriptIntoEpisodes(Vector<ReadTranscriptData.Entry> section) {
	Vector<ReadTranscriptData.Entry[]> result = new Vector<>();
	Vector<ReadTranscriptData.Entry> q = new Vector<>();
	String lastEid = "";
	for( ReadTranscriptData.Entry e: section) {
	    String eid = e.eid;
	    if (!eid.equals(lastEid)) {
		if (q.size()>0) {
		    result.add( q.toArray(new ReadTranscriptData.Entry[0]));
		}
		q.clear();
		lastEid = eid;
	    }
	    q.add(e);
	}
	
	if (q.size()>0) {
	    result.add( q.toArray(new ReadTranscriptData.Entry[0]));
	}
	return result;			
    }

}
