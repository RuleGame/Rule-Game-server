package edu.wisc.game.engine;

import java.io.*;
import java.util.*;
import java.text.*;
import java.net.*;

import edu.wisc.game.util.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.parser.*;

/** The socket implementation of a captive game server */
public class GameSocketServer {

    public static void main(String argv[]) {

	if (argv.length != 1) {
	    System.err.println("Usage: java GameSocketServer <port_number>");
	    System.exit(1);
	}
 
        int portNumber = Integer.parseInt(argv[0]);
        boolean listening = true;
         
        try (ServerSocket serverSocket = new ServerSocket(portNumber)) { 
            while (listening) {
		Socket socket=serverSocket.accept();
		new GameSocketServerThread(socket).start();
            }
        } catch (IOException e) {
            System.err.println("Could not listen on port " + portNumber);
            System.exit(-1);
        }
    }

}
