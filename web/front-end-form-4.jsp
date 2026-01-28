<%@include file="include/imports.jsp" %>
<%
FrontEndForm4 main = new FrontEndForm4(request,response);
String cla=main.dev?"pink":"yellow";
%>
<html>
<head>
<title>Play using the GUI front end</title>
   <link rel="stylesheet" type="text/css" href="css/rule-game.css"/>


<script type="text/javascript">
function validateForm() {
  let pref="<%= main.prefix %>";

  let suff = document.forms["clientForm"]["workerIdSuffix"].value;
  document.forms["clientForm"]["workerId"].value = pref + suff;
  return true;
}
</script>

</head>
<body>
<h1>Play using the GUI front end (players from the "<%= main.prefix %>" family)</h1>


<h2> GUI play
<%= main.dev? "(with development client)": "(with production client)"  %> 
</h2>


<div class="<%=cla%>">
<h2>Choose experiment plan from a list</h2>

<!-- <p>ServerPort=<%= main.serverPort %> -->

<p>This is the launch page for GUI-client play using the Rule Game server ver. <%= main.getVersion() %> deployed at <%= main.serverUrl %>, and the 
<% if (main.dev) { %> 
development version of the client, which  gives you more debugging information on what goes on inside the system.
<% }else { %>
the production version of the client, which offers the same experience the M-Turkers have.
<% } %>


<p>Client URL: <tt><%=main.clientUrl%></tt>

<form name="clientForm" method="get" action="<%=main.clientUrl%>" onsubmit="return validateForm()">

Game Server: <input type=text name="server" size=80 value="<%=main.serverUrl%>">


<table border="1">
<tr><td valign="top">
Choose one of the experiment plans listed below:<br>
	 <tt>
	 	 <%= (main.exp==null) ?
	  Files.listSAllExperimentPlansHtml(true) :
	  Tools.radio("exp", main.exp, main.exp, true)
	  %>

	 </tt>    
<td valign="top">   
Pick a unique workerId for your session (e.g. <tt>john-doe-2021-06-18</tt>): &nbsp;
<input type="hidden" name="workerId" value=""><tt>
<%=main.prefix%><input type="text" name="workerIdSuffix" size="30" value="auto-<%= main.stamp %>"></tt>
<br><input type="radio" name="intro" value="true" checked>Show intro
<br><input type="radio" name="intro" value="false">Skip intro
<br>
	    
	    <strong><button type="submit">Play (<%=main.devProd()%>)</button></strong>
	    </tr>
	    </table>
</form>
</div>
<br>
<hr>


</body>
</html>
