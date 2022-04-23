<%@include file="include/imports.jsp" %>
<html>
<head>
<title>Validate your experiment plan</title>
    <link rel="stylesheet" type="text/css" href="css/rule-game.css"/>
</head>
<body>
<h1>Validate your experiment plan</h1>

<h2>Static experiment plans</h2>
<P>You can use this form to check your experiment plan's input files for errors (syntax errors, references to non-existent files, attempts to generate boards with non-existence colors or shapes, etc). Just choose the name of your experiment plan in the form below, and click on the "Check" button.
  
   <form method="post" action="game-data/CheckPlanService/checkPlan"
	 enctype="application/x-www-form-urlencoded">
	 <tt>
	 <%= Files.listSAllExperimentPlansHtml() %><br>
	     </tt>    
     <button type="submit">Check</button>
   </form>


<h2>Dynamic experiment plans</h2>

<p>Type  the name of a plan, e.g. <tt>P:pilot06:max-feedback</tt>,
<tt>R:APP/OCT26/OCT26-1:APP/APP-no-feedback</tt>,
or <tt>R:bottomLeft_then_topRight:simple</tt>

   <form method="post" action="game-data/CheckPlanService/checkPlan"
	 enctype="application/x-www-form-urlencoded">
	 <input type="text" name="exp" size="80">
	 <br>
     <button type="submit">Check</button>
   </form>

<h2>Individual rule sets</h2>

<p>To test an individual rule set without creating an experiment plan, 
you can type the text of the rule set in the box below. For example,
<pre class="yellow">
1 (*, square, *, *, 0) (*, triangle, *, *,1) (*, circle, *, *,2) (*, square, *, *,3)
(*,*,*,*,[p-1,p+1])
</pre>

or (for a stalemate example):

<pre class="yellow">
(*,*,RED,T,[0,1,2,3])
(*,*,BLUE,R,[0,1,2,3])
(*,*,YELLOW,*,[0,1,2,3])
(*,*,BLACK,*,[0,1,2,3])
</pre>

or (for another stalemate example):
<pre class="yellow">
(color:[red,yellow], pos:T, bucket:[0,1]) (color:[blue,black], pos:B, bucket:[2,3])
</pre>

   <form method="post" action="game-data/CheckPlanService/checkRules"
	 enctype="application/x-www-form-urlencoded">

<textarea  name="rulesText" rows="10" cols="120"></textarea><br>

	 <br>
     <button type="submit">Check</button>
   </form>

<p>(Note: when testing individual rule sets, stalemate testing will only work correctly if your rule set uses the legacy shapes and legacy colors. To test a rule set with custom shapes or custom colors, and for rule sets with image-and-property-based objects, you need to create an experiment plan, so that the tester will know what the set of possible objects is!)

<hr>

<p>See also:

<ul>
<li><a href="stalemate.html">About stalemates and the stalemate tester</a>
</ul>


</body>
</html>
