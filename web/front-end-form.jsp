<%@include file="include/imports.jsp" %>
<html>
<head>
<title>Play using the GUI front end</title>
</head>
<body>
<h1>Play using the GUI front end</h1>

<li>GUI play (dev), with 
	  <form method="get"
		action="http://sapir.psych.wisc.edu/rule-game/dev/">


Choose one of the experiment plans listed below:
	 <tt>
	 <%= Files.listSAllExperimentPlansHtml() %><br>
	 </tt>    
   
Pick a unique workerId for your session (e.g. <tt>john-doe-2021-06-18</tt>: <input type="text" name="workerId" size="30" value="">
	    
	    <input type="radio" name="intro" value="true" checked>Show intro
	    <input type="radio" name="intro" value="false">Skip intro

	    
	    <button type="submit">Play (dev)!</button>
	      </form>
<hr>


</body>
</html>
