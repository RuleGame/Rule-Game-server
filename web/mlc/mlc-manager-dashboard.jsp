<%@include file="../include/imports.jsp" %>
<%
   String sp="mlc/mlc-manager-dashboard.jsp";
   MlcManagerDashboard main=new MlcManagerDashboard(request, response);
%>
<html>
<head>
<title>MLC Manager Dashboard</title>
</head>
<body>
<h1>MLC Manager Dashboard</h1>

<%   if (main.getError()) {   %>  <%@include file="../include/error.jsp" %>
<%   } else if (!main.loggedIn()) { %>

<%@include file="login_form.jsp" %>

<%
} else {
%>

<h2>
Welcome <tt><!-- %= main.getDisplayText() % --></tt>
</h2>

<%= main.report%>

<hr>

<hr>
<h2>Command-line interface</h2>

<p>This tool also has command-line script interface (on sapir). It can be used if you wanted to save the M-W matrix and ranking results into CSV files (in the same way as it's done for the <a href="../tools/analyze-transcripts-mwh.html">Human Data M-W analysis tool</a>.

<p>The usage is as follows, as shown in 2 examples.

<p>To compare different ML algorithms' performance on a particular rule set:
<pre>scripts/mann-whitney.sh -mode CMP_ALGOS -rule alternateShape2Bucket_color2Bucket -csvOut tmp</pre>
To compare the difficulty of different rule sets for a particular ML algo:
<pre>scripts/mann-whitney.sh -mode CMP_RULES -algo someAlgoName  -csvOut tmp</pre>

<p>In the above examples, <tt>tmp</tt> is the name of the directory to which M-W CSV files are written by this tool.

<hr>
<h2>Instructions for the experiment manager</h2>

<p>The experiment manager can find the setup instructions for the player's pages <a href="mlc-manage.html">here</a>.

<% 
} %>

<hr>
<a href="../LogoutServlet?redirect=/mlc/mlc-manager-dashboard.jsp">Log out</a>


<hr>
<p><small>Info Message: <%= main.infomsg%></small>


</body>
</html>
