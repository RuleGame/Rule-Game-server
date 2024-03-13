<%@include file="include/imports.jsp" %>
<%
FrontEndForm2 main = new FrontEndForm2(request,response);
String cla=main.dev?"pink":"yellow";
%>
<html>
<head>
<title>Play using the GUI front end</title>
   <link rel="stylesheet" type="text/css" href="css/rule-game.css"/>

<script type="text/javascript">
function validateForm() {
  let x = document.forms["clientForm"]["workerId"].value;
  if (x.indexOf("XX") == 0) {
    alert("Invalid Player ID '" + x + "'. You must replace 'XX' with your initials.");
    return false;
  }
}
</script>

</head>
<body>
<h1>Play using the GUI front end</h1>

<%   if (main.getError()) {   %>  <%@include file="include/error.jsp" %>
<%   } else { %>


<h2>Playing experiment plan <%=main.exp%></h2>
<div class="<%=cla%>">
<h2>GUI play
<%= main.dev? "(with development client)": "(with production client)"  %> 
</h2>

<p>This is the launch page for GUI-client play using the Rule Game server ver. <%= main.getVersion() %> deployed at <%= main.serverUrl %>, and the 
<% if (main.dev) { %> 
development version of the client, which  gives you more debugging information on what goes on inside the system.
<% }else { %>
the production version of the client, which offers the same experience the M-Turkers have.
<% } %>


<p>Client URL: <tt><%=main.clientUrl%></tt>

<p>Game Server: <tt><%=main.serverUrl%></tt>


<div class="<%=cla%>">
	<form name="clientForm" method="get" action="<%=main.clientUrl%>" onsubmit="return validateForm()">

<input type=hidden name="server" value="<%=main.serverUrl%>">

<input type="hidden" name="exp"  value="<%=main.exp%>">

<br>Player Id: <input type="text" name="workerId" size="60" value="XX-<%=main.prefix%><%=main.stamp%>">
<br>(Replace XX above with your initials)

<!--
<br><input type="radio" name="intro" value="true" checked>Show intro
<br><input type="radio" name="intro" value="false">Skip intro
-->
<input type="hidden" name="intro" value="<%=main.intro%>">

<br>
 
    <strong><button type="submit">Play (<%=main.devProd()%>)</button></strong>
    </form>
</div>

<%}%>

</body>
</html>
