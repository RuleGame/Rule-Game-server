<%@include file="include/imports.jsp" %>
<html>
<head>
<title>Arithmetic test</title>
</head>
<body>
<h1>Arithmetic test</h1>

<p>You can use this form to see how the Rule Game Engine evaluates the expressions that are used in the "destination buckets" field of the rules' atoms.

<p>All computaions are performed in set-valued arithmetic, with the final results mapped to the range [0..<%=Episode.NBU - 1 %>] by modulo-<%=Episode.NBU%> arithmetic.</p>

<p>1. If you want any variables to have values other than empty set, please enter the values. (For a single value, just enter a number; for a set with more than member, just  enter the numbers separated by spaces. Note though that on our board, none of our variables ever has more than value, but you can try multi-valuied vars anyway, just for fun). If a variable has no value assigned to it (as p, pc, and ps are early in each episode), just leave the input box blank.

<form method="post" action="arithmetic-result.jsp">
<table>
<% for(RuleSet.BucketSelector varName: RuleSet.BucketSelector.values()) {%>
<tr>
<td><%= varName %> =
<td><%= HTMLFmter.htmlFmter.input( ArithmeticResult.prefix + varName, "", 20) %>
</tr>
<% } %>
</table>

2. Enter the expression to be evaluated in the box below
<%= HTMLFmter.htmlFmter.input( "expression", "", 80) %>

<button type="submit">Evaluate!</button>
</form>

</body>
</html>
