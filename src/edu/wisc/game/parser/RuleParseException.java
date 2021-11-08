package edu.wisc.game.parser;

import java.io.*;
import java.util.*;
import java.text.*;

public class RuleParseException extends Exception {
    public RuleParseException(String msg) {
        super(msg);
    }
    public RuleParseException( String msg, Exception ex) {
        super(msg + "\nCaused by: " + ex.getMessage(), ex);
    }

    
}

