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

<p>Choose one or several experiment plans from the list below. All rule sets included in those plans will be compared.<p>

<form method="get" action="../game-data/ManagerDashboardService/compareRulesForHumans">


<table border="1">
<tr><td valign="top">
	 <tt>
	 <%= PlanStats.listSAllExperimentPlansHtml() %>
	 </tt>    
<td valign="top">   
Learning attainment criterion: <input type="text" name="targetStreak" size="4" value="10"> consecutive error-free moves

<br><input type="radio" name="prec" value="naive" checked>For each rule sets, only include "naive" players (those who played this rule set as their first rule set)
<br><input type="radio" name="prec" value="every">Consider each (rule set + preceding set) combination as a separate experience to be ranked
<br><input type="radio" name="prec" value="ignire">When analyzing a set, ignore preceding rule sets
<br>
	    
	    <strong><button type="submit">Compare rule sets</button></strong>
	    </tr>
	    </table>
</form>
</div>
<br>
<hr>


</body>
</html>
