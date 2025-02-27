/* 
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package edu.wisc.game.websocket;

import java.io.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.websocket.*;

import jakarta.websocket.server.ServerEndpoint;


import edu.wisc.game.util.Logging;
import edu.wisc.game.rest.ChatWriteReport;
import edu.wisc.game.rest.FileWriteReport;

//import util.HTMLFilter;

@ServerEndpoint(value = "/websocket/watchPlayer" ,
		encoders = {WatchPlayer.PickEncoder.class,WatchPlayer.ReadyEncoder.class}
		)
public class WatchPlayer {

    private static final String GUEST_PREFIX = "Guest";
    private static final AtomicInteger connectionIds = new AtomicInteger(0);
    private static final Set<WatchPlayer> connections = new CopyOnWriteArraySet<>();

    private final String nickname;
    private Session session;

    /** The ID of the player whom the client associated with this connection
	wants to watch */
    private String myPid=null;
    /** This can be also used as the destination for chat messages */
    private String watchedPid =null;
    private final Date startAt;

    
    /*
     * The queue of messages that may build up while another message is being sent. The thread that sends a message is
     * responsible for clearing any queue that builds up while that message is being sent.
     */
    private Queue<Object> messageBacklog = new ArrayDeque<>();
    private boolean messageInProgress = false;

    public WatchPlayer() {
	startAt = new Date();
        nickname = GUEST_PREFIX + connectionIds.getAndIncrement();
    }

    public String toString() {
	return "WatchPlayer(" + nickname + ": " + watchedPid + "->" + myPid+")";
    }
    

    @OnOpen
    public void start(Session session) {
        this.session = session;
        connections.add(this);
        String message = "At " + startAt + ", watcher " + String.format("* %s %s", nickname, "has joined.");
	Logging.info(toString() + ", started channel");
	sendMessage( "STATUS " + message);
        //broadcast("[B] " + message);
    }


    @OnClose
    public void end() {
	Logging.info(toString() + ", closing connection");
        connections.remove(this);
        //String message = String.format("* %s %s", nickname, "has disconnected.");
        //broadcast(message);
    }


    /** Expects
	<pre>
	WATCH pid
	IAM pid
	CHAT text
	</pre>
    */
    @OnMessage
    public void incoming(String message) {
        // Never trust the client
        //String filteredMessage = String.format("%s: %s", nickname, HTMLFilter.filter(message.toString()));
        //broadcast(filteredMessage);

	String  s = "At " + (new Date()) +", ";
	String watch = null, iam = null, chat = null;
	final String WATCH = "WATCH", IAM = "IAM", CHAT = "CHAT";
	if (message.startsWith(WATCH)) {
	    watch = message.substring(WATCH.length()).trim();
	} else 	if (message.startsWith(IAM)) {
	    iam = message.substring(IAM.length()).trim();
	} else 	if (message.startsWith(CHAT)) {
	    chat = message.substring(CHAT.length()).trim();
	} else {
	    s+= "ignoring message: " + message;
	}
	

	String pid = message.trim();
	//sendMessage("[M2] pid=" + pid);
	if (pid.equals("")) return;

	synchronized (this) {
	    if (watch!=null) {
		if (watchedPid != null) {
		    s += "stopped watching player '" + watchedPid+ "', ";
		}
		watchedPid = watch;
		s += "started watching player '" + watchedPid+ "'. ";
	    } else if (iam!=null) {
		if (myPid != null) {
		    s += "stopped receiving messages for '" + myPid+ "', ";
		}
		myPid = iam;
		s += "started receiving messages for '" + myPid+ "'. ";
			
	    }
	    Logging.info(toString() + ": " + s);
	}

	if (chat!=null) {
	    if (myPid == null) {
		Logging.error("Received a chat message from a WS channel without known myPid: " + chat);
	    } else {
		if (watchedPid==null) {
		    watchedPid =ChatWriteReport.findPartnerPlayerId(myPid);
		}
		if (watchedPid!=null) {
		    sendHimChat(watchedPid, "CHAT " + chat);
		    FileWriteReport r=ChatWriteReport.writeChat(myPid, chat);
		}
	    }
	} else {
	
	//try {

	    Logging.info("Chat(i="+myPid+")(w="+watchedPid+"): " + s);
			   
	    sendMessage("STATUS " + s);
	}
	    //} catch(IOException t) {
	    //Logging.error("Cannot send a websocket message about player "  + watchedPid +"; error=" + t);
	    //}
    }


    @OnError
    public void onError(Throwable t) throws Throwable {

	StringWriter sw = new StringWriter();
	t.printStackTrace(new PrintWriter(sw));
	
        Logging.error("Chat Error: " + t.toString());
        Logging.error("Trace: " + sw);
    }


    /*
     * synchronized blocks are limited to operations that are expected to be quick. More specifically, messages are not
     * sent from within a synchronized block.
     */
    private void sendMessage(Object msg) //throws IOException
    {
	
	try {
	
        synchronized (this) {
            if (messageInProgress) {
                messageBacklog.add(msg);
                return;
            } else {
                messageInProgress = true;
            }
        }

        boolean queueHasMessagesToBeSent = true;

        Object messageToSend = msg;
        do {
	    if (messageToSend instanceof String) {
		session.getBasicRemote().sendText((String)messageToSend);
	    } else {
		try {
		    session.getBasicRemote().sendObject(messageToSend);
		} catch(EncodeException ex) {
		    Logging.error("Send error for object ("+messageToSend+"): " + ex.toString());
		}
		    
	    }


	    Logging.info(toString() +", sent message: " + messageToSend);
	    
            synchronized (this) {
                messageToSend = messageBacklog.poll();
                if (messageToSend == null) {
                    messageInProgress = false;
                    queueHasMessagesToBeSent = false;
                }
            }

        } while (queueHasMessagesToBeSent);


	} catch (IOException e) {
	    Logging.debug("Send Error: Failed to send message to client"+e);
	    if (connections.remove(this)) {
		try {
		    this.session.close();
		} catch (IOException e1) {
		    // Ignore
		}
		String message = String.format("* %s %s",this.nickname, "has been disconnected.");
		broadcast(message);
	    }
	}

     }


    private static void broadcast(String msg) {
        for (WatchPlayer client : connections) {
            //try {
                client.sendMessage(msg);
		//} catch (IOException e) {
		/*
                Logging.debug("Chat Error: Failed to send message to client"+e);
                if (connections.remove(client)) {
                    try {
                        client.session.close();
                    } catch (IOException e1) {
                        // Ignore
                    }
                    String message = String.format("* %s %s", client.nickname, "has been disconnected.");
                    broadcast(message);
                }
		*/
		//}
        }
    }

    public static class WatchMessage {
	Object o;
	WatchMessage(Object _o) { o  = _o; }
    }
    

    private void sendHim1Chat(String pid, String m) {
	if (pid==null  || !pid.equals(myPid)) return;
	sendMessage(m);
    }

    /** @param pid The destination player ID */
    private void tellHim1b(String pid, Ready m) {
	if (pid==null  || !pid.equals(myPid)) return;
	sendMessage(m);
    }

    /** @param pid The sender's player ID. The message is sent only if
	this WatchPlayer is set up to watch that specific pid.
	
     */
    private void tellAbout1a(String pid, String text) {
	if (pid==null  || !pid.equals(watchedPid)) return;
	String s = "At " + (new Date()) + ", action by player '" + pid +"': " + text;
	sendMessage(s);
    }

    private void tellAbout1b(String pid, Object m) {
	if (pid==null  || !pid.equals(watchedPid)) return;
	sendMessage(m);
    }

    /** Messages sent to a GUI client to tell it that something is ready
	for it, and it can make another /newEpisode or /display call */
    public enum Ready {
	EPI,
	DIS;
	public String toString() {
	    return "Ready " + name();
	}
    };

    
    /** Methods handling important events during the game call this method
	to let watchers now about the most recent event
	
	@param pid The sender's player ID. The message will be sent to everyone
	who is watching that player. (In a 2PG, that will be that player's
	partner, of course)
    */
    public static <T> void tellAbout(String pid, T msg) {
	Logging.info("WatchPlayer: The server wants to send a message from " + pid + " to its watchers: " + msg);
	for (WatchPlayer client : connections) {
	    if (msg instanceof String) {
		client.tellAbout1a(pid, (String)msg);
	    } else { //if (msg instanceof WatchMessage) {
		client.tellAbout1b(pid, msg);
		//	    } else {
		//Logging.error("Wrong message type: " + msg.getClass());
	    }
	}
    }

    /** @param pid The destination player ID */
    public static void tellHim(String pid, Ready msg) {
	Logging.info("WatchPlayer: The server wants to send a message to " + pid + ": " + msg);
	for (WatchPlayer client : connections) {
	    client.tellHim1b(pid, msg);
	}
    }
    public static void sendHimChat(String pid, String s) {
	for (WatchPlayer client : connections) {
	    client.sendHim1Chat(pid, s);
	}
    }

    /** See https://docs.oracle.com/javaee/7/tutorial/websocket007.htm or
	https://www.baeldung.com/java-websockets
	for documentation on encoders */
    static public class PickEncoder implements Encoder.Text<edu.wisc.game.sql.Episode.Pick> {
	@Override
	public void init(EndpointConfig ec) { }
	@Override
	public void destroy() { }
	@Override
	/** This method can use reflection to make a nice JSON string out of
	    the object; but at the moment I just use toString.
	*/
	public String encode(edu.wisc.game.sql.Episode.Pick pick) throws EncodeException {
	    // Access msgA's properties and convert to JSON text...
	    return "[pick/move: "+pick+"]";
	}
    }

    static public class ReadyEncoder implements Encoder.Text<Ready> {
	@Override
	public void init(EndpointConfig ec) { }
	@Override
	public void destroy() { }
	@Override
	/** This method can use reflection to make a nice JSON string out of
	    the object; but at the moment we simply send "READY EPI" or "READY DIS",
	    and attach the time stamp.
	*/
	public String encode(Ready msg) throws EncodeException {
	    return "READY " + msg.toString() + " " + (new Date()) ;
	}
    }

    
    
}
