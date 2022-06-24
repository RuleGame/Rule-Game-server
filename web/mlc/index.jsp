<%@include file="../include/imports.jsp" %>
<%
   String sp="mlc/index.jsp";
   MlcMain main=new MlcMain(request, response);
%>
<html>
<head>
<title>MLC Results Upload Form</title>
</head>
<body>
<h1>MLC Results Upload Form</h1>

<%   if (main.getError()) {   %>  <%@include file="../include/error.jsp" %>
<%   } else if (!main.loggedIn()) { %>

<%@include file="login_form.jsp" %>

<%
} else {
%>

<h2>
Welcome <tt><%= main.getDisplayText() %></tt>
</h2>

<%= main.report%>

<hr>
<h2>Upload a file</h2>

<p>If you want to upload a results file, use the form below. A new upload may erase your previously uploaded files.


<p>The results file should contain your nickname, <tt><%=main.nickname%></tt>, in the first column in every line (other than the header line). See <a href="mlc-guide.html">MLC Participants Guide</a> for details on how to prepare a results file.


 <form  enctype="multipart/form-data"
  	 action="../game-data/MlcUploadService/uploadFile"	
	 method="post">						
<input type="hidden" name="nickname" value="<%=main.nickname%>">
<input type="hidden" name="key" value="<%=main.key%>">
Click here to choose a file to upload:
<input type="file" size="80" name="file"> <!-- multiple -->
<br><br>
<button type="submit">Upload file</button>
</form>



<hr>
<h2>Instructions for the experiment manager</h2>

<p>The experiment manager can find the setup instructions for this page <a href="mlc-manage.html">here</a>.

<% 
} %>

<hr>
<a href="../LogoutServlet?redirect=/mlc">Log out</a>


<hr>
<p><small>Info Message: <%= main.infomsg%></small>


</body>
</html>
