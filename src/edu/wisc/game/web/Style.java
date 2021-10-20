package edu.wisc.game.web;


/** Contains class names used in CSS style for rendering various
 * types, and methods that generate HTML elements with appropriate attributes.
 */
class Style {
    /** Class names */
    final static String INST = "inst", READ="read", 
	CHOICES="choices";

    static String element(String element, String style) {
	if (style==null || style.equals("")) return "<"+element+">"; 
	else return "<"+element+" class=\""+style+"\">"; 
    }

    static String P( String style) {
	return element("P", style);
    }

    static String SPAN( String style) {
	return element("SPAN", style);
    }

   static  String TD( String style) {
	return element("TD", style);
    }
}
