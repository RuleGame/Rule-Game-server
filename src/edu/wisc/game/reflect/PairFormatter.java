package edu.wisc.game.reflect;


interface PairFormatter {
    String row(String name, String value);

    static class TablePairFormatter implements  PairFormatter{
	public String row(String name, String value) {
	    return "<tr><td class=\"inst\">" + name + "</td><td>" + value + 
		"</td></tr>\n";
	}
    }

    static class CompactPairFormatter implements  PairFormatter {
	public String row(String name, String value) {
	    return "<strong>"+name + "</strong>: " + value + "; ";
	}
    }
}



