<%@include file="include/imports.jsp" %>
<html>
<head>
<title>Play using the GUI front end</title>
   <link rel="stylesheet" type="text/css" href="css/rule-game.css"/>
</head>
<body>
<h1>Play using the GUI front end</h1>

<div class="pink">
<h2>GUI play (development version)</h2>

<p>The development version of the client, which uses the development version of the Game Server, offers all the newest and coolest features currently under development. It also gives you more debugging information on what goes on inside the system.

<form method="get" action="http://sapir.psych.wisc.edu/rule-game/dev/">


<table border="1">
<tr><td valign="top">
Choose one of the experiment plans listed below:<br>
	 <tt>
	 <%= Files.listSAllExperimentPlansHtml() %>
	 </tt>    
<td valign="top">   
Pick a unique workerId for your session (e.g. <tt>john-doe-2021-06-18</tt>): <input type="text" name="workerId" size="30" value="">
<br><input type="radio" name="intro" value="true" checked>Show intro
<br><input type="radio" name="intro" value="false">Skip intro
<br>
	    
	    <strong><button type="submit">Play (dev)!</button></strong>
	    </tr>
	    </table>
</form>
</div>	      
<hr>

<h2>GUI play (production)</h2>

<p>The production version of the client, which uses the production version of the Game Server, is the most stable version. It offers the same experience the M-Turkers have.

<p>
	  <form method="get"
		action="http://sapir.psych.wisc.edu/rule-game/prod/">


<table border="1">
<tr><td valign="top">
Choose one of the experiment plans listed below:<br>
	 <tt>
	 <%= Files.listSAllExperimentPlansHtml() %>
	 </tt>    
<td valign="top">   
Pick a unique workerId for your session (e.g. <tt>john-doe-2021-06-18</tt>): <input type="text" name="workerId" size="30" value="">
<br><input type="radio" name="intro" value="true" checked>Show intro
<br><input type="radio" name="intro" value="false">Skip intro
<br>
	    
	    <button type="submit">Play (prod)!</button>
	    </tr>
	    </table>
	      </form>


</body>
</html>
