package edu.wisc.game.web;

import javax.servlet.http.HttpServletResponse;

/** This is used when a lower-level function can tell a higher-level function
    what kind of error it shoudl report to the end user.
 */
public class WebException extends Exception {
    private int code =HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
    public int getCode() { return code; }
    public WebException(String msg) {
	super(msg);
    }
    WebException(int _code, String msg) {
	super(msg);
	code = _code;
    }
}
