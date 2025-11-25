package edu.wisc.game.pseudo;
import edu.wisc.game.rest.ParaSet;

public class BotParams {
    /** Reads the pseudo learning params, for bot assist or bot player, from
	the specified para set.

       @param whatFor "bot" for partner in HvB 2PG, "bot_assist" for
       a bot assist
       @param j  0 for  for partner in HvB 2PG, or for bot assist in 1PG;
       0 or 1  for bot assist in 2PG
       @return a BotParams object describing the type and params of
       the relevant bot, or null if this ParaSet does not call for a bot
       in this role
    */
    public static BotParams init(ParaSet para, String whatFor, int j) {
	String suff = (j==0)? "": "" + j;
	String botAssistName = para.getString(whatFor + suff, null);
	if (botAssistName == null) {
	    return null;
	} else if (botAssistName.equals("pseudo")) {
	    return new Pseudo.Params(para, j);
	} else {
	    throw new IllegalArgumentException("Illegal bot assist name ("+botAssistName+")"); // for player " + playerId + ", mover=" +j);
	}
	//	msg += ";(j="+j+": botAssistName=" + botAssistName +")";
    }

    public String toString() {
	return "(A bot: "+getClass() +")";
    }

    
	 	
}
