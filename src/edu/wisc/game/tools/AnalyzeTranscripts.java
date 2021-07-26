package edu.wisc.game.tools;

import java.io.*;
import java.util.*;
import java.util.regex.*;
// import javax.json.*;
import javax.persistence.*;

import edu.wisc.game.util.*;
import edu.wisc.game.rest.*;
import edu.wisc.game.sql.*;

public class AnalyzeTranscripts {

    /** An auxiliary structure used to keep track who and when played 
	episodes
     */
    static class EpisodeHandle {
	String ruleSetName;
	String exp;
	String trialListId;
	int seriesNo;
	int orderInSeries;
	String episodeId;
	String playerId;
	public String toString() {return episodeId;}
	EpisodeHandle(String _exp, String _trialListId, TrialList t, String _playerId, EpisodeInfo e, int _orderInSeries) {
	    episodeId = e.getEpisodeId();

	    playerId = _playerId;
	    exp = _exp;
	    trialListId = _trialListId;
	    seriesNo= e.getSeriesNo();
	    orderInSeries =  _orderInSeries;
	    ParaSet para = t.get(seriesNo);
	    ruleSetName = para.getRuleSetName();	    
	}
    }


    static EpisodeHandle findEpisodeHandle(Vector<EpisodeHandle> v, String eid) {
	for(EpisodeHandle eh: v) {
	    if (eh.episodeId.equals(eid)) return eh;
	}
	return null;
    }

    
    public static void main(String[] argv) throws Exception {


	EntityManager em = Main.getNewEM();

	// for each rule set name, keep the list of all episodes
	TreeMap<String, Vector<EpisodeHandle>> allHandles= new TreeMap<>();
	// for each player, the list of episodes...
	TreeMap<String,Vector<EpisodeHandle>> ph =new TreeMap<>();

	// for h experiment plan
	for(int j=0; j<argv.length; j++) {
	    String exp = argv[j];
	    System.out.println("Experiment plan=" +exp);
	    Vector<String> trialListNames = TrialList.listTrialLists(exp);

	    HashMap<String,TrialList> trialListMap=new HashMap<>();

	    
	    for(String trialListId: trialListNames) {
		TrialList t = new  TrialList(exp, trialListId);
		trialListMap.put( trialListId,t);
		//		for(int seriesNo=0; seriesNo < t.size(); seriesNo++) {
		//		    ParaSet para = t.get(seriesNo);
		//		    String ruleSetName = para.getRuleSetName();
		//		}
	    }
	    System.out.println("Experiment plan=" +exp+" has " + trialListMap.size() +" trial lists: "+ Util.joinNonBlank(", ", trialListMap.keySet()));
	    
	    Vector<EpisodeHandle> handles= new Vector<>();
	    
	    Query q = em.createQuery("select m from PlayerInfo m where m.experimentPlan=:e");
	    q.setParameter("e", exp);
	    List<PlayerInfo> res = (List<PlayerInfo>)q.getResultList();
	    for(PlayerInfo p:res) {
		String trialListId = p.getTrialListId();
		TrialList t = trialListMap.get( trialListId);
		if (t==null) {
		    System.out.println("ERROR: for player "+p.getPlayerId()+", no trial list is available for id=" +  trialListId +" any more");
		    continue;
		}
		int orderInSeries = 0;
		int lastSeriesNo =0;
		for(EpisodeInfo e: p.getAllEpisodes()) {
		    int seriesNo = e.getSeriesNo();
		    if (seriesNo != lastSeriesNo) orderInSeries = 0;
		    EpisodeHandle eh = new EpisodeHandle(exp, trialListId, t, p.getPlayerId(), e, orderInSeries);
		    handles.add(eh);

		    Vector<EpisodeHandle> v = allHandles.get( eh.ruleSetName);
		    if (v==null)allHandles.put(eh.ruleSetName,v=new Vector<>());
		    v.add(eh);

		    Vector<EpisodeHandle> w = ph.get(eh.playerId);
		    if (w==null) ph.put(eh.playerId, w=new Vector<>());
		    w.add(eh);		     


		    
		    orderInSeries++;
		    lastSeriesNo=seriesNo;
		    
		}
  
	    }

	    System.out.println("For experiment plan=" +exp+", found " + handles.size()+" good episodes");//: "+Util.joinNonBlank(" ", handles));

	}	    

	File base = new File("tmp");
	if (!base.exists() || !base.isDirectory() || !base.canWrite())  throw new IOException("Not a writeable directory: " + base);

	for(String ruleSetName: allHandles.keySet()) {
	    System.out.println("For rule set=" +ruleSetName+", found " + allHandles.get(ruleSetName).size()+" good episodes"); //:"+Util.joinNonBlank(" ",allHandles.get(ruleSetName) ));
	    File d=new File(base, ruleSetName);
	    if (d.exists()) {
		if (!d.isDirectory() || !base.canWrite())  throw new IOException("Not a writeable directory: " + d);
	    } else {
		if (!d.mkdirs()) throw new IOException("Cannot create directory: " + d);
	    }
	}

	Pattern pat = Pattern.compile("^([^,]+),([^,]+),(.*)");
	
	//-- Take a look at each player's transcript and separate
	//-- it into sections pertaining to different rule sets
	for(String playerId: ph.keySet()) {
	    Vector<EpisodeHandle> v = ph.get(playerId);
	    
	    File inFile = Files.transcriptsFile(playerId, true);
	    LineNumberReader reader = new LineNumberReader(new FileReader(inFile));
	    // #pid,episodeId,moveNo,timestamp,y,x,by,bx,code
	    // YS933,20201002-161858-DZV392,0,20201002-161904.226,6,1,7,0,4
	    String inHeader=reader.readLine();
	    String outHeader=inHeader.replaceAll("^#pid,episodeId,", "#ruleSetName,playerId,experimentPlan,trialListId,seriesNo,episodeNo,episodeId,");
	    String lastRid="", s=null;
	    PrintWriter w=null;
	    
	    while((s=reader.readLine())!=null) {
		//		if (s.startsWith("#")) continue;
		Matcher m = pat.matcher(s);
		//System.out.println(s);
		if (!m.find()) throw new IllegalArgumentException("In transcript file "+inFile+", don't know how to parse line " + reader.getLineNumber()+": "+s);
		String pid =m.group(1), eid=m.group(2), rest=m.group(3);
		
		EpisodeHandle eh = findEpisodeHandle(v, eid);
		if (eh==null) {
		    throw new IllegalArgumentException("In file "+inFile+", found unexpected experimentId="+eid);
		}
		    
		String rid=eh.ruleSetName;
		
		
		if (!lastRid.equals(rid)) {
		    if (w!=null) { w.close(); w=null;}
		    File d=new File(base, rid);
		    File g=new File(d, pid + ".split-transcripts.csv");

		    w =  new PrintWriter(new FileWriter(g, false));
		    w.println(outHeader);
		    lastRid=rid;
		}
		w.println(rid+","+pid+","+eh.exp+","+eh.trialListId+","+eh.seriesNo+","+eh.orderInSeries+","+eid+","+rest);
	    }
	    if (w!=null) { w.close(); w=null;}
	   
	    
	}


	/*
	for(String ruleSetName: allHandles.keySet()) {
	    Vector<EpisodeHandle> v = allHandles.get(ruleSetName);
	    
	    // for each player, the list of episodes involving this
	    // rule set
	    TreeMap<String,Vector<EpisodeHandle>> ph =new TreeMap<>();
	    for(EpisodeHandle eh: v) {
		Vector<EpisodeHandle> w = ph.get(eh.playerId);
		if (w==null) ph.put(eh.playerId, w=new Vector<>());
		w.add(eh);		     
	    }

	    File f=new File(base, ruleSetName + ".csv");
	    File d=f.getParentFile();
	    if (d.exists()) {
		if (!d.isDirectory() || !base.canWrite())  throw new IOException("Not a writeable directory: " + d);
	    } else {
		if (!d.mkdirs()) throw new IOException("Cannot create directory: " + d);
	    }
	    System.out.println("For rule set=" + ruleSetName+", will write data to file "+ f +". This will include episodes from " + ph.size() +" players");
	    
	    PrintWriter w = new PrintWriter(new FileWriter(f, false));
	    for(String playerId: ph.keySet()) {
		
	    }	    
	    w.close();		
	}
	*/
    }

}
