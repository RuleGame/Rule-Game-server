<%@include file="include/imports.jsp" %>
<%
ContextInfo main = new ContextInfo(request,response);
String cla=main.dev?"pink":"yellow";
%>
<html>
<head>
<title>Play using the GUI front end</title>
   <link rel="stylesheet" type="text/css" href="css/rule-game.css"/>
</head>
<body>
<h1>Play using the GUI front end</h1>

<h2>Choose experiment plan from a list</h2>
<div class="<%=cla%>">
<h2>GUI play
<%= main.dev? "(development version)": "(production)"  %>
</h2>

<% if (main.dev) { %> 
<p>The development version of the client, which uses the development version of the Game Server, offers all the newest and coolest features currently under development. It also gives you more debugging information on what goes on inside the system.
<% }else { %>
<p>The production version of the client, which uses the production version of the Game Server, is the most stable version. It offers the same experience the M-Turkers have.
<% } %>

<p>Client URL: <tt><%=main.clientUrl%></tt>

<form method="get" action="<%=main.clientUrl%>">

Game Server: <input type=text name="server" size=80 value="<%=main.serverUrl%>">


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
	    
	    <strong><button type="submit">Play (<%=main.devProd()%>)</button></strong>
	    </tr>
	    </table>
</form>
</div>
<br>
<hr>

<h2>Form 2: ...or you can enter the plan name by hand:</h2>

<br>


<p>In this form, you can also enter the name of a <a href="concepts.html#sd">dynamic plan</a>, e.g. <tt>P:pilot06:max-feedback</tt>,
<tt>R:APP/OCT26/OCT26-1:APP/APP-no-feedback</tt>,
or <tt>R:bottomLeft_then_topRight:simple</tt>



<div class="<%=cla%>">
	<form method="get" action="<%=main.clientUrl%>">

<br>Game Server: <input type=text name="server" size=80 value="<%=main.serverUrl%>"><br>

<br>Experiment plan: (e.g. <tt>pilot06</tt>): <input type="text" name="exp" size="60" value="">

<br>Player Id (e.g. <tt>pk-2022-05-31-a</tt>): <input type="text" name="workerId" size="30" value="">

<br><input type="radio" name="intro" value="true" checked>Show intro
<br><input type="radio" name="intro" value="false">Skip intro
<br>

    <input type="radio" name="intro" value="true" checked>Show intro
    <input type="radio" name="intro" value="false">Skip intro

    <strong><button type="submit">Play (<%=main.devProd()%>)</button></strong>
    </form>
</div>


</body>
</html>
