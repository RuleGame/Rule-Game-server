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
//package websocket.chat;
package edu.wisc.game.websocket;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Set;
import java.util.Date;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

//import org.apache.juli.logging.Log;
//import org.apache.juli.logging.LogFactory;

import edu.wisc.game.util.Logging;

import util.HTMLFilter;

@ServerEndpoint(value = "/websocket/watchPlayer")
public class WatchPlayer {

    //    private static final Log log = LogFactory.getLog(WatchPlayer.class);

    private static final String GUEST_PREFIX = "Guest";
    private static final AtomicInteger connectionIds = new AtomicInteger(0);
    private static final Set<WatchPlayer> connections = new CopyOnWriteArraySet<>();

    private final String nickname;
    private Session session;

    /** The ID of the player whom the client associated with this connection
	wants to watch */
    private String watchedPid =null;
    private final Date startAt;

    
    /*
     * The queue of messages that may build up while another message is being sent. The thread that sends a message is
     * responsible for clearing any queue that builds up while that message is being sent.
     */
    private Queue<String> messageBacklog = new ArrayDeque<>();
    private boolean messageInProgress = false;

    public WatchPlayer() {
	startAt = new Date();
        nickname = GUEST_PREFIX + connectionIds.getAndIncrement();
    }


    @OnOpen
    public void start(Session session) {
        this.session = session;
        connections.add(this);
        String message = "[V1] At " + startAt + ", watcher " + String.format("* %s %s", nickname, "has joined.");
	sendMessage( message);
        //broadcast("[B] " + message);
    }


    @OnClose
    public void end() {
        connections.remove(this);
        //String message = String.format("* %s %s", nickname, "has disconnected.");
        //broadcast(message);
    }


    @OnMessage
    public void incoming(String message) {
        // Never trust the client
        //String filteredMessage = String.format("%s: %s", nickname, HTMLFilter.filter(message.toString()));
        //broadcast(filteredMessage);


	//sendMessage("[M1] " + message);
	//        broadcast("[B] " + message);


	String pid = message.replaceAll("^\\s+", "").replaceAll("\\s$", "");
	//sendMessage("[M2] pid=" + pid);
	if (pid.equals("")) return;
	String  s = "At " + (new Date()) +", ";
	synchronized (this) {
	    if (watchedPid != null) {
		s += "stopped watching player '" + watchedPid+ "', ";
	    }
	    watchedPid = pid;
	    s += "started watching player '" + watchedPid+ "'. ";
	}
	//try {
	    sendMessage("[Y] " + s);
	    //} catch(IOException t) {
	    //Logging.error("Cannot send a websocket message about player "  + watchedPid +"; error=" + t);
	    //}
    }


    @OnError
    public void onError(Throwable t) throws Throwable {
        Logging.error("Chat Error: " + t.toString());
    }


    /*
     * synchronized blocks are limited to operations that are expected to be quick. More specifically, messages are not
     * sent from within a synchronized block.
     */
    private void sendMessage(String msg) //throws IOException
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

        String messageToSend = msg;
        do {
            session.getBasicRemote().sendText(messageToSend);
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

    private void showMe(String pid, String text) {
	if (pid==null  || !pid.equals(watchedPid)) return;
	String s = "At " + (new Date()) + ", action by player '" + pid +"': " + text;
	sendMessage(s);
    }

    /** Methods handling important events during the game call this method
	to let watchers now about the most recent event */
    public static void showThem(String pid, String text) {
	for (WatchPlayer client : connections) {
	    client.showMe(pid, text);
	}
    }
    
}
