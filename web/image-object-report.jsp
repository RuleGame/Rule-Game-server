<%@include file="include/imports.jsp" %>
<%
ImageObjectReport main	= new ImageObjectReport(request, response);
%>
<html>
<head>
<title>Image Object Report</title>
</head>
<body>
<h1>Image Object Report</h1>

<%   if (main.getError()) {   %>  <%@include file="include/error.jsp" %>
<%
} else {
%>
<% if (main.wild) { %>
<p>Wildcard expression: <tt><%= main.name0 %></tt>
<p>Sample matching name: <tt><%= main.name %></tt> (If you reload this page, you'll get another sample!)
<% } else {
%>
<p>Name: <tt><%= main.name0 %>
<%
} %>
<p><img src="GetImageServlet?image=<%= main.nameEncoded %>">
<p>Properties:
<ul>
<li>
<%=
main.io.listProperties("\n<li>") 
%>
</ul>

<% 
} %>

<hr>

<form method="get" action="image-object-report.jsp">
	<input type="text" name="image" size="120" value="<%= main.name0 %>"><br>
	<button type="submit"> Show this object! </button>
</form>	       




<hr>
<p><small>Info Message: <%= main.infomsg%></small>

</body>
</html>
