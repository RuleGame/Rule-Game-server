<%@include file="../include/imports.jsp" %>
<%
ContextInfo main = new ContextInfo(request,response);
%>
<html>
<head>
<title>Compare rule sets difficulty for human players</title>
   <link rel="stylesheet" type="text/css" href="css/rule-game.css"/>
</head>
<body>
<h1>Compare rule sets difficulty for human players</h1>

<h2>Choose experiment plans</h2>
<div>

<p>Note: there is also a command-line inteface for this tool, scripts/analyze-transcripts-mwh.sh. Documentation is coming...

<hr>

<p>Choose one or several experiment plans from the list below. All rule sets included in those plans will be compared.<p>

<form method="get" action="../game-data/ManagerDashboardService/compareRulesForHumans">


<table border="1">
<tr><td valign="top">
	 <tt>
	 <%= PlanStats.listSAllExperimentPlansHtml() %>
	 </tt>    
<td valign="top">   
<h3>Stage 1: Transcript processing parameters</h3>
<br>
Learning attainment criterion:
<input type="text" name="targetStreak" size="4" value="10"> consecutive error-free moves

<br>Default mStar =
<input type="text" name="defaultMStar" size="6" value="300">  (You can also enter the value <tt>Infinity</tt>)

<hr>

<h3>Stage 2: data interpretation parameters for M-W</h3>

<br><input type="radio" name="prec" value="Naive" checked>Naive: For each rule sets, only include "naive" players (those who played this rule set as their first rule set)
<br><input type="radio" name="prec" value="Every">Every: Consider each (rule set + preceding set) combination as a separate experience to be ranked. (That is,
the R1 data from <strong>R1</strong>:R2:R3,  
R2:<strong>R1</strong>:R3, and
R2:R3:<strong>R1</strong> are viewed as belonging to three distinct experiences, "R1", "R2:R1", and "R2:R3:R1")
<br><input type="radio" name="prec" value="Ignore">Ignore: When viewing a rule set's series, ignore preceding rule sets. (In other words, the R1 data from
<strong>R1</strong>:R2:R3,  
R2:<strong>R1</strong>:R3, and
R2:R3:<strong>R1</strong> are merged, viewed as the same "R1 experience").

<br><checkbox name="mDagger">Check to use mDagger instead of mStar

<hr>
	    <strong><button type="submit">Compare rule sets</button></strong>
	    </tr>
	    </table>
</form>
</div>
<br>
<hr>


</body>
</html>
