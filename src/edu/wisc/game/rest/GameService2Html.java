package edu.wisc.game.rest;

import java.io.*;
import java.util.*;
import java.net.*;
import java.text.*;
import jakarta.json.*;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

// For REST method descriptions
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
// for URLs
import jakarta.ws.rs.core.Context;

import edu.wisc.game.util.*;
import edu.wisc.game.reflect.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.formatter.*;
import edu.wisc.game.rest.ParaSet.Incentive;


/** The page generator for the HTML Play interface.

    <p> Each REST API method (e.g. /moveHtml) here returns an HTML
    wrapper for the corresponding Second Batch Rule Game API call
    (e.g. /move), to enable "HTML Play".

    <p>The designer of the GUI Client can look at various methods of
    this class to see how the Java structure returned by the
    underlying API calls (such as /player, /newEpisode, /move, /pick,
    or /display) is analyzed to extract the information needed to
    correctly generate all the data-presenting elements of the GUI.
    In the GUI Client, the same exactly data are returned by the Game
    Server API calls in the form of JSON structures.

    <P>There is a minor difference between the data available to this
    class's methods and to the GUI Client: this method sometimes
    directly access EpisodeInfo or PlayerInfo objects, which of course
    are not available in the GUI client. However, here we access these
    structures in a very limited way, and for each of these accesses
    there is an alternative way to get ahold of the needed piece of
    data, usually by saving some value (such as "adveGame" or
    "needChat") from the result of a /player or /newEpisode call, and
    using it later on (e.g. during a /display call).
 */

@Path("/GameService2Html") 
public class GameService2Html extends GameService2 {

    /** JavaScript elements to put inside HEAD */
    private static String makeJS(UriInfo uriInfo, String myPlayerId) {
	String watchUrl = makeWatchServerUrl( uriInfo, myPlayerId);

	String s= "";
	s += fm.style("../../css/board.css") + "\n";
	s +="<script type=\"application/javascript\">\n";
	s += "var myPid='"  + myPlayerId + "';\n";
	s +=  "var watchUrl='"  + watchUrl + "';\n";
	s += "</script>\n";
	s += "<script type=\"application/javascript\" src=\"../../js/socket1.js\"></script>\n";
	s += "<script type=\"application/javascript\" src=\"../../js/boardDisplay.js\"></script>\n";
	return s;	
    }

    /** Some extra HTML code to put at the end of BODY */
    /*    private static String makeEnding() {
	String s= 
	    "<div id=\"console-container\">\n" +
	    "<div id=\"console\"/>\n" +
	    "</div>";
	return s;
    }
    */
    
    private static String makeWatchClientUrl( UriInfo uriInfo, String myPlayerId) {
	//UriBuilder ub = uriIndo.getAbsolutePathBuilder();
	URI base = uriInfo.getBaseUri();
	// From: http://localhost:8080/w2020/game-data/GameService2Html/playerHtml
	// To: http://localhost:8080/w2020/websocket/watchPlayer.xhtml?playerId=....
        return base.toString().replaceAll("/game-data.*", "/websocket/watchPlayer.xhtml?iam=" + myPlayerId);	
    }

    /** Produces a "ws:" or "wss:" URL.

[scheme:][//authority][path][?query][#fragment]


authority = [user-info@]host[:port] = localhost:8080
path = /w2020/game-data/GameService2Html/playerHtml

 new URI(u.getScheme(),
             u.getUserInfo(), u.getAuthority(),
             u.getPath(), u.getQuery(),
             u.getFragment())
     .equals(u)
     */
    private static String makeWatchServerUrl( UriInfo uriInfo, String myPlayerId) {
	//UriBuilder ub = uriIndo.getAbsolutePathBuilder();
	URI u = uriInfo.getBaseUri();
	
	// From: http://localhost:8080/w2020/game-data/GameService2Html/playerHtml
	// To: http://localhost:8080/w2020/websocket/watchPlayer

	String scheme = u.getScheme();
	scheme = scheme.equals("https")? "wss": "ws";
	String path = u.getPath();  // E.g. "/w2020/game-data/GameService2Html/playerHtml"
	path = path.replaceAll("/game-data.*", "/websocket/watchPlayer");
	String query = "iam=" + myPlayerId;

	
	URI serverURI = null;
	try {
	    serverURI= new URI(scheme, u.getAuthority(), path, query, null);
	} catch (URISyntaxException ex) {
	    Logging.error("URISyntaxException: " + ex);
	}
	Logging.debug("GS2HTML: watchServerUrl=" + serverURI);
        return serverURI.toString();
    }

    /** Produces the final document, attaching JS snippets to the head and body */
    private static String withJS(  UriInfo uriInfo, String playerId, String title, String body) {
	String head = fm.title(title) + makeJS(uriInfo,  playerId);
        //body += makeEnding();
	return fm.html2(head, body);	
    }

    
    private static HTMLFmter  fm = new HTMLFmter();

    @POST
    @Path("/playerHtml") 
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    /** @param exp The experiment plan. If not supplied (null), the experiment
	plan will be guessed from the playerId.
     */
    public String playerHtml(@DefaultValue("null") @FormParam("playerId") String playerId,
			     @DefaultValue("null") @FormParam("exp") String exp,
			     @DefaultValue("-1") @FormParam("uid") int uid,
			     final @Context UriInfo uriInfo){

	PlayerResponse pr = new PlayerResponse( playerId, exp, uid);

	Vector<String> v = new Vector<>();
	boolean canPlay = false;

	v.add("Response: " + fm.para(  ""+JsonReflect.reflectToJSONObject(pr, true)));

	String title = "?";
	String body = "";

	try {
	    
	    //v.add("Base=" + base);
	    
	if (pr.getError()) {
	    v.add(fm.para("Error happened: " + pr.getErrmsg()));
	    title="Error";
	} else if (pr.getNewlyRegistered()) {
	    v.add(fm.para("Successfully registered new player"));
	    canPlay = true;
	    title="Registered player " + playerId;
	} else if (pr.getExperimentPlan().equals(exp)  && !pr.getAlreadyFinished()) {
	    v.add(fm.para("This player already exists, but it is associated with the same experiment plan, and you can play more episodes"));
	    canPlay = true;
	    title="Reusing player " + playerId;
	} else {
	    v.add(fm.para("A player with this name already exists; experimentPlan=" +
			  pr.getExperimentPlan() +"; alreadyFinished=" + pr.getAlreadyFinished()));
	    title="Player " + playerId + " already exists";
	}

	if (pr.getIsTwoPlayerGame()) {
	    String s = "This is a two-player-game";
	    if (pr.getIsCoopGame()) s += " (cooperative)";
	    else if (pr.getIsAdveGame()) s += " (adversarial)";	    
	    s += ". If you want to see server messages (READY DIS, READY EPI), you can open this URL in another browser tab or window: ";
	    String url = makeWatchClientUrl( uriInfo, playerId);
	    s += fm.a(url, url, null);
	    v.add(fm.para(s));		
	}
					 

	
	if (canPlay) {
	    String form = "Now, you can start (or resume) playing!<br>";
	    form += fm.hidden("playerId",playerId);
	    form += "<button type='submit'>Play an episode!</button>";
	    form = fm.wrap("form", "method='post' action='newEpisodeHtml'", form);

	    v.add(fm.para(form));
	}

	body = String.join("\n", v);
	    
	} catch(Exception ex) {
	    title = "Error";
	    body =
		fm.para(ex.toString()) + 
		fm.pre(Util.stackToString(ex));
	}
	return fm.html(title, body);	
	
    }
   
    
    @POST
    @Path("/mostRecentEpisodeHtml") 
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public String mostRecentEpisodeHtml(@FormParam("playerId") String playerId,
					final @Context UriInfo uriInfo
					) {
	return newOrRecentEpisodeHtml(playerId,true, false, false,  uriInfo);
    }

    @POST
    @Path("/newEpisodeHtml") 
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public String newEpisodeHtml(@FormParam("playerId") String playerId,
				 @DefaultValue("false") @FormParam("activateBonus") boolean activateBonus,
				 @DefaultValue("false") @FormParam("giveUp") boolean giveUp,
				 final @Context UriInfo uriInfo
				 ) {
	return newOrRecentEpisodeHtml(playerId,false, activateBonus, giveUp,  uriInfo);
    }

    private String newOrRecentEpisodeHtml( String playerId, boolean recent,
					   boolean activateBonus, boolean giveUp, UriInfo uriInfo) {
	String body = "";

	//String msg = "";
	if (activateBonus) {
	    ActivateBonusWrapper w=new  ActivateBonusWrapper(playerId);
	    body += fm.h4("Activate") + fm.para(""+JsonReflect.reflectToJSONObject(w, true)) + fm.hr();
	    //msg += " (Activate: " + w.getErrmsg();
	}
	if (giveUp) {
	    GiveUpWrapper w =new  GiveUpWrapper(playerId);
	    body += fm.h4("Give up") + fm.para(""+JsonReflect.reflectToJSONObject(w, true)) + fm.hr();
	    //msg += " (Activate: " + w.getErrmsg();
	}

	NewEpisodeWrapper2 w = new NewEpisodeWrapper2(playerId, recent, false, false);
	String episodeId = w.getEpisodeId();

	String title= episodeId +" : "+ (recent? "mostRecentEpisode":"newEpisode");

	EpisodeInfo epi = (EpisodeInfo)EpisodeInfo.locateEpisode(episodeId);


	
	if (w.getError()) {
	    if (w.getAlreadyFinished()) {
		body += fm.para("The player has finished all episodes. Completion code = " +w.getCompletionCode());
	    } else {
		body += fm.para("Error: " + w.getErrmsg());
	    }
	} else if (w.getMustWait()) {
	    body += fm.h3("Please wait for your partner in order for the episode to start");
	    String s = "If you want to monitor server messages, you can open this URL in another tab: ";
	    String url = makeWatchClientUrl( uriInfo, playerId);
	    s += fm.a(url, url, null);
	    body += fm.para(s);


	    String form = "When the  'READY EPI' message comes from the server, this page should automatically reload with the new episode. If it does not, you can click the button below:<br>";
	    form += fm.hidden("playerId",playerId) + fm.br();
	    form += "<button type='submit'>See the new episode!</button>";
	    form = fm.wrap("form", "id='readyEpiForm' method='post' action='newEpisodeHtml'", form);
	    body  += fm.para(form);

	    PlayerInfo x = PlayerResponse.findPlayerInfoAlreadyCached(playerId);
	    if (x.getNeedChat()) {
		body += chatSection();
	    }	    
	} else {
	    //-- The moveForm also includes the chatSection at the end
	    body += moveForm(playerId, w.getDisplay(),  episodeId);
	}

	body += fm.hr();
	
	body +=  fm.h4( "Response")+fm.para(  ""+JsonReflect.reflectToJSONObject(w, true));

	return withJS( uriInfo, playerId, title, body);
    }

    
    @GET
    @Path("/displayHtml")
    @Produces(MediaType.TEXT_HTML)
    public String displayHtml(@DefaultValue("null") @QueryParam("playerId") String playerId,
			      @QueryParam("episode") String episodeId,
			      final @Context UriInfo uriInfo){			      
	EpisodeInfo.ExtendedDisplay d = display(playerId, episodeId);
	String title= episodeId +" : DISPLAY";

	String body = "";
	//body += fm.h1(title);

	body += moveForm(playerId, d,  episodeId);

	body += fm.hr();
	body += fm.h4("Server response") + fm.para(  ""+JsonReflect.reflectToJSONObject(d, true));

	return withJS( uriInfo, playerId, title, body);
    }


    @POST
    @Path("/moveHtml") 
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public String moveHtml(@FormParam("playerId") String playerId,
			   @FormParam("episode") String episodeId,
			   @FormParam("x") int x,
			   @FormParam("y") int y,
			   @FormParam("bx") int bx,
			   @FormParam("by") int by,
			   @FormParam("cnt") int cnt,
			   final @Context UriInfo uriInfo){
			   
	Episode.Display d=move(playerId, episodeId,x,y,bx,by,cnt);
	
	String title= episodeId +" : MOVE " + x + " " +y + " " + bx + " " + by;

	String body = "";
	body += fm.para(title);

	body += moveForm(playerId, d,  episodeId);

	body += fm.hr();
	body += fm.h4("Server response") + fm.para(  ""+JsonReflect.reflectToJSONObject(d, true));

	return withJS( uriInfo, playerId, title, body);
    }

    @POST
    @Path("/pickHtml") 
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public String pickHtml(@FormParam("playerId") String playerId,
			   @FormParam("episode") String episodeId,
			   @FormParam("x") int x,
			   @FormParam("y") int y,
			   @FormParam("cnt") int cnt,
			   final @Context UriInfo uriInfo){
			   
	Episode.Display d=move(playerId, episodeId,x,y,cnt);
	
	String title= episodeId +" : PICK " + x + " " +y;

	String body = "";
	body += fm.para(title);

	body += moveForm(playerId, d,  episodeId);
	body += fm.hr();
	body += fm.h4("Server response") + fm.para(  ""+JsonReflect.reflectToJSONObject(d, true));

	return withJS( uriInfo, playerId, title, body);
    }

    /** Shows the player's history.
     */
    static private String showHistory(EpisodeInfo epi,
				      EpisodeInfo.ExtendedDisplay d) {
	String body;
	if (epi==null) {
	    body = fm.para("No episode in memory");
	} else {
	    body =	    
		fm.h4("Player's history") +
		fm.para("All episodes, completed and incomplete, are listed below, one series per line. The format for each episode is:<br>" +  epi.reportKey() ) +
		fm.wrap("pre",epi.getPlayer().report());
	    if (epi.isBonus()) {
		final NumberFormat df = new DecimalFormat("#.##");
		body += fm.para("Moves left: " + df.format(d.getMovesLeftToStayInBonus()));
	    }
	}	
	return body;// + fm.hr();	
   }

    /** Shows the "faces" illustrating the current transcript.
     */
    static private String showFaces(EpisodeInfo epi,
				    EpisodeInfo.ExtendedDisplay d) {
	String body ="";
	if (epi==null) {
	    body = fm.para("No episode in memory");
	} else {
	    Vector<Boolean> faces = d.getFaces();
	    Vector<Boolean> facesMine = d.getFacesMine();


	    // Here I pull them from EpisodeInfo; in the GUI client, this info can be stored in memory from the
	    // initial /player response
	    boolean isAdve = epi.getPlayer().isAdveGame();
	    boolean isCoop = epi.getPlayer().isCoopGame();

	    if (isAdve) {
		String s1 = "My moves: ",   s2 = "Opponent's moves: ";
		for(int i=0; i<faces.size(); i++) {
		    String q = faceImg(faces.get(i), facesMine.get(i));
		    if (facesMine.get(i)) {
			s1 += q;
		    } else {
			s2 += q;
		    }
		}
		body += fm.para(s1);
		body += fm.para(s2);
	    } else {
		//Vector<String> v= new Vector<>();
		String s = "";
		for(int i=0; i<faces.size(); i++) {
		    String q = faceImg(faces.get(i), facesMine.get(i));
		    s += q;
		}
		body += fm.para(s);
	    }
	}
	return body;// + fm.hr();	
   }

    // From: http://localhost:8080/w2020/game-data/GameService2Html/playerHtml
    //  /w2020/admin/getSvg.jsp?shape=unhappy
    static final String base = "../../admin/getSvg.jsp?shape=";
    static final String happy = base + "happy"; 
    static final String unhappy = base + "unhappy";

    private static String faceImg(boolean isHappy, boolean big) {
	String src = isHappy? happy: unhappy;
	String w =  big? "20" : "10";
	String s= "<img src='"+src+"' height='"+w+"'>";
	if (!isHappy) s = "<span style='background:red'>"+s+"</span>";
	return s;
    }
   

    /** Looks at the ExtendedDisplay (returned by a /pick or /move or /display call) to see
	if the most recent move attempt was made by this player, and was a success. */  
    private static boolean wasLastMoveMySuccess(EpisodeInfo.ExtendedDisplay d)  {
	int myRole = d.getMover();
	if (d.getTranscript().size()==0) return false;
	Episode.Pick lastMove = d.getTranscript().lastElement();
	return lastMove.getMover() == myRole && lastMove.getCode() == Episode.CODE.ACCEPT;
    }
    
    /** Generates a /moveHtml form, if the episode is not completed,
     or a /guess form if it's time for a guess */
    static private String moveForm(String playerId, Episode.Display _d, String  episodeId) {

	if (!(_d instanceof EpisodeInfo.ExtendedDisplay)) {
	    return fm.para("Cannot cast to  EpisodeInfo.ExtendedDisplay; _d=" + _d);
	}
    
	EpisodeInfo.ExtendedDisplay d = (EpisodeInfo.ExtendedDisplay)_d;
	
	String body = "";
	EpisodeInfo epi = (EpisodeInfo)EpisodeInfo.locateEpisode(episodeId);
	boolean isAdve = epi.getPlayer().isAdveGame();
	
	String s = "Rule " + (d.getDisplaySeriesNo()+1) + ". ";
	if (d.getDisplaySeriesNo() != d.getSeriesNo()) s += " (internally "+(d.getSeriesNo()+1)+")";
	s += "Episode " + (d.getDisplayEpisodeNo()+1) + " of "+ d.getTotalBoardsPredicted() + ". ";
	if (isAdve) {
	    s += "Score: me " + d.getTotalRewardEarned() + " : opponent " + d.getTotalRewardEarnedPartner();
	} else {
	    s += "" + d.xgetRewardsAndFactorsPerSeriesString();
	}
	body += fm.para(s);
	
	body+= fm.h4("Current position");

	//body +=	fm.para(epi.graphicDisplay(true));
	//-----------

	boolean canMove = (d.getFinishCode()==Episode.FINISH_CODE.NO) && !d.getMustWait();
	boolean[] isMoveable = epi.positionsOfMoveablePieces();
	
	String notation = HtmlDisplay.notation(epi.weShowAllMovables());	
	String leftSide  =HtmlDisplay.htmlDisplay(epi.getPieces(), epi.getLastMovePos(),  epi.weShowAllMovables(), isMoveable, 40, canMove);

	String rightSide = notation;
	//------------
	if (d.getFinishCode()==Episode.FINISH_CODE.NO) {

	    Vector<String> cells = new Vector<>();

	    if (d.getMustWait()) {		
		String form="";
		form += fm.hidden("playerId", playerId);
		form += fm.hidden("episode", episodeId);
		form += "<input type='submit' value='Redisplay'>";
		form = fm.wrap("form", "id='readyDisForm' method='get' action='displayHtml'", form);
		form = fm.h3( "Wait for your turn") +
		    fm.para("This page should automatically reload when READY DIS signal comes; but if it doesn't, you can click on the button below to redisplay") + fm.para(form);
		cells.add(form);
	    } else {	    
		String formA="";
		formA += fm.hidden("playerId", playerId);
		formA += fm.hidden("episode", episodeId);
		formA += fm.hidden("cnt", ""+d.getNumMovesMade());
		formA += "Piece (x=" + fm.input2("x",  "id='moveFormX' size='2'") + 
		    ", y=" + fm.input2("y",  "id='moveFormY' size='2'" ) +  ")" + fm.br();
		
		String form = formA;
		// 
		form += "Bucket (x=" + fm.input2("bx", "id='moveFormBX' size='2'") +
		    ", y=" + fm.input2("by",  "id='moveFormBY' size='2'")	+")"+   fm.br();
		form += "<input type='submit'>";
		form = fm.wrap("form", "id=\"moveForm\" method='post' action='moveHtml'", form);
		form = fm.h3( "Make a move!") +
		    fm.para("To make a move, first click on a game piece, and then on a bucket. Alternatively, you can use the form below.") +
		    fm.para(form);
		cells.add(form);
		
		if (epi.xgetPara().isFeedbackSwitchesFree()) {
		    form = formA;
		    form += "<input type='submit'>";
		    form = fm.wrap("form", "id=\"pickForm\" method='post' action='pickHtml'", form);
		    form = fm.h4( "or, Try to pick a piece to see if it's moveable") + fm.para(form);
		    cells.add(form);
		}
	    }

	    if (cells.size()==0) cells.add(fm.para("Nothing -- the experiment finished"));
	    	    
	    rightSide += fm.table("border='1'", fm.rowExtra("valign='top'",cells));
	    
	} else {
	    rightSide += fm.para("Game over - no move possible. Finish code=" + d.getFinishCode());

	    
	    boolean mayNeedGuess =
		d.getIncentive().mastery() ?
		d.getFinishCode()==Episode.FINISH_CODE.EARLY_WIN:
		true;
	
	    if (mayNeedGuess && !d.getGuessSaved()) {
		rightSide += fm.h4("Your guess");


		if (d.getFinishCode()==Episode.FINISH_CODE.EARLY_WIN) {
		    s= "";
		    if (epi.getPlayer().isCoopGame()) {
			s="You and your partner seem to have learned this rule! What do you think the rule is?";
		    } else if (epi.getPlayer().isAdveGame() && !wasLastMoveMySuccess(d)) {
			s="As you watched your opponent's play, what is the rule that he seems to have found?";
		    } else {
			s="You seem to have learned this rule! What do you think the rule is?";
		    }
		    rightSide += fm.para(s);
		}


		String form = "";
		form += fm.hidden("playerId",playerId);
		form += fm.hidden("episode", episodeId);
		form += "Enter your guess below:" + fm.br() +
		    fm.input("data", null, 80) + fm.br() +
		    "Confidence=" + fm.input("confidence", "5", 2) + fm.br();
		form += "<input type='submit'>";	    
		form =  fm.wrap("form", "method='post' action='guessHtml'", form);
		form = fm.para(form);
		rightSide += form;
	    } else {
		rightSide += fm.h3("What will you do next?");
		PlayerInfo.TransitionMap map = d.getTransitionMap();	    
		rightSide += transitionTable(epi, playerId, map);
	    }

	}

	Vector<String> cc = new Vector();
	cc.add(leftSide);
	cc.add(rightSide);
	body  += fm.table("border='0'", fm.rowExtra("valign='top'",cc));
	body += fm.hr();

	s = "Incentive scheme=" + d.getIncentive();
	if (d.getIncentive()==Incentive.DOUBLING) {
	    s += fm.br() + "Stretch="+d.getLastStretch()+". Factor achieved="+ d.getFactorAchieved()+"; promised in this episode " +  d.getFactorPromised() + ".";
	    s += d.getJustReachedX2()? " Just reached x2." : "";
	    s += d.getJustReachedX4()? " Just reached x4.": "";
	}
	if (d.getIncentive()==Incentive.LIKELIHOOD) {
	    s += fm.br() + "R="+d.getLastR()+". Factor achieved="+ d.getFactorAchieved()+"; promised in this episode " +  d.getFactorPromised();
	    s += d.getJustReachedX2()? " Just reached x2." : "";
	    s += d.getJustReachedX4()? " Just reached x4." : "";
	}
	body += fm.para(s) + fm.hr();

	body += showFaces(epi, d);

	if (isAdve) { // print it here, because in Adve mode it's not shown on top
	    body += fm.para("My Score breakdown: " + d.xgetRewardsAndFactorsPerSeriesString());
	}
	
	body += showHistory(epi,  d);
	
	if (epi.getPlayer().getNeedChat()) {
	    body += chatSection();
	}
	
	return body;
    }


    @POST 
    @Path("/guessHtml")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public String guessHtml(@DefaultValue("null") @FormParam("playerId") String playerId,
			    @FormParam("episode") String episodeId,
			    @FormParam("data") String guessText,
			    @DefaultValue("-1") @FormParam("confidence") int confidence			    ) {
	if (playerId!=null && playerId.equals("null")) playerId=null;
	FileWriteReport _r = GuessWriteReport.writeGuess(playerId, episodeId, guessText, confidence);

	EpisodeInfo epi = (EpisodeInfo)EpisodeInfo.locateEpisode(episodeId);
	if (epi==null) {
	    String msg = "Episode not loaded: " + episodeId;
	    return fm.html(msg, msg);	
	}
	
	String head= episodeId +" : Guess";

	String body = "";
	body += fm.h4("Response") + fm.para(  ""+JsonReflect.reflectToJSONObject(_r, true));
	body += fm.hr();
	body += fm.h4("What will you do next?");
	
	if (_r instanceof GuessWriteReport && epi!=null) {
	    GuessWriteReport r = (GuessWriteReport)_r;
	    PlayerInfo.TransitionMap map = r.getTransitionMap();	    
	    body += transitionTable(epi, playerId, map);

	}

	if (epi.getPlayer().getNeedChat()) {
	    body += chatSection();
	}

	
	return fm.html(head, body);	
    }

    /** Prepares a table with transition buttons, to appear at the end of each episode. In 2PG, if
	there is only one button (next episode), it can be automatically pushed by the READY EPI message.
     */
    private static String transitionTable(EpisodeInfo epi, String playerId, PlayerInfo.TransitionMap map) {
	Vector<String> rows= new Vector<>();

	boolean canAutoTransition = epi.getPlayer().is2PG() && map.size()==1 &&
	    (map.get(PlayerInfo.Transition.MAIN)!=null ||
	     map.get(PlayerInfo.Transition.NEXT)==PlayerInfo.Action.DEFAULT);
	
	    
	    
	for(PlayerInfo.Transition key: map.keySet()) {
	    PlayerInfo.Action val =map.get(key);
	    Vector<String> v = new Vector<>();
	    v.add(""+key);
	    v.add(""+val);
	    
	    
	    String action="newEpisodeHtml";
	    String form =  "<form method='post' action='"+action+"'";
		if (canAutoTransition) form += " id='readyEpiForm' "; 
		form += ">\n";
	    form +=  fm.hidden("playerId", playerId);
	    
	    if (val==PlayerInfo.Action.ACTIVATE) {
		form += fm.hidden("activateBonus", "true");
	    } else if (val==PlayerInfo.Action.GIVE_UP) {
		form += fm.hidden("giveUp", "true");
	    } 

	    String text =
		(key==PlayerInfo.Transition.MAIN) ? "Next episode":
		(key==PlayerInfo.Transition.BONUS)?
		(val==PlayerInfo.Action.ACTIVATE? "Activate Bonus":
		 "Next bonus episode"):
		(key==PlayerInfo.Transition.NEXT)?
		(val==PlayerInfo.Action.DEFAULT? "Start the next series":
		 "Give up on this series and start the next"):
		(key==PlayerInfo.Transition.END)?
		(val==PlayerInfo.Action.DEFAULT? "Finish":
		 "Give up on this (last) series and end the expriment"):
		"Error - unknown action";
	    
	    form += "<input type='submit' value='Go!'></form>\n";
	    v.add(text);
	    v.add(form);
	    
	    rows.add(fm.row(v));
	}
	return fm.wrap("table", "border=1", String.join("\n",rows)) +fm.br();
    }

    private static String transitionButton(String action, String episodeId, String text) {
	return "<form method='post' action='"+action+"'><strong>"+text+"</strong><input type='submit'></form>";
    }

    
    /** Generates the HTML snippet for between-player chat: the text
	entry box, and the conversation display box. In the HTML Play
	page generator, this method is only called when
	PlayerInfo.needChat for the current player is true.  In the
	GUI client, the this flag should be obtained from the needChat
	field of the return structure of the /player call that starts
	the session.
     */
    static private String chatSection() {
	String body = fm.hr();
	body += fm.para("In the box below, you can type a message to be sent to your partner; then press ENTER to send it.");
	// The text entry box
	body += fm.para("<input type='text' placeholder='Type a message and press ENTER' id='chat'/>");
	// The conversation display box
	body += "<div id='console-container'>  <div id='console'/> </div>\n";
	return body;
    }


    
}
