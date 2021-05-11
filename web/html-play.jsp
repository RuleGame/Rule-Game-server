<%@include file="include/imports.jsp" %>
<html>
<head>
<title>HTML Play</title>
</head>
<body>
<h1>HTML Play</h1>


<p>The "HTML play" can be used to play some episodes in the web browser, without the use of the client GUI app. This allows to test the Game Server functionality separately from that of the client GUI app.

<p>Note: for technical reasons, in the HTML play the shape-and-color game pieces are drawn in black, and their assigned color (slightly "watered down", for better visibility) is indicated by the background of the cell. This is different from how the pieces appear in the GUI client. The image-and-properties-based game pieces are shown using the same images that the GUI client will show.

<P>To start playing, use the <tt>/playerHtml</tt> form below.


<p>
  <ul>
<li>
    POST <strong><tt>/playerHtml</tt>: Register a new player in an experiment plan of your choice:</strong>
        <form method="post" action="game-data/GameService2Html/playerHtml"
	      enctype="application/x-www-form-urlencoded">

Pick a new unique player ID (e.g. <tt>johndoe-2021-06-18-a</tt>): <input name="playerId" type="text" size="25" value="AnyText01"><br>
Choose an experiment plan:<br>
	 <tt>
	 <%= Files.listSAllExperimentPlansHtml() %><br>
	     </tt>    
</br>
   <button type="submit">Register player</button>
	</form>

    
<li>POST <strong><tt>/mostRecentEpisodeHtml</tt>: Look up the most recent episode for a specified user:</strong>
        <form method="post" action="game-data/GameService2Html/mostRecentEpisodeHtml"
	 enctype="application/x-www-form-urlencoded">
	  Player ID: <input name="playerId" type="text" value="" size="20"><br>
	  <input type="submit">
	</form>

<li>POST <strong><tt>/newEpisodeHtml</tt>: Create a new episode for a specified user:</strong>
        <form method="post" action="game-data/GameService2Html/newEpisodeHtml"
	 enctype="application/x-www-form-urlencoded">
	  Player ID: <input name="playerId" type="text" value="" size="20"><br>
	  <input type="submit">
	</form>

	
<li>/moveHtml

      <form method="post" action="game-data/GameService2Html/moveHtml"
	 enctype="application/x-www-form-urlencoded">
	  Episode ID (returned by an earlier newEpisode call): <input name="episode" type="text" size="25"><br>
	  Piece to move: column=x=<input name="x" type="text" value="1" size="2">,
	  row=y=<input name="y" type="text" value="1" size="2"> (both ranging 1 thru 6)<br>
	  Destination bucket (identified by  bucket_column=bx=<input name="bx" type="text" value="7" size="2">,
	  bucket_row=by=<input name="by" type="text" value="7" size="2"> (possible values are 0 and 7)<br>
	  Attempted move count so far (the number of previous calls to <strong><tt>move</tt></strong> in this episode. This can also be interpreted as 0-based number of this move attempt) <input name="cnt" type="text" value="0" size="5"><br>
	  <input type="submit">
      </form>

      
  </ul>

<hr>

[<a href=".">HOME</a>]

</body>
</html>
