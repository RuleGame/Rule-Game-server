<!-- this file is included into other files in the same directory -->
<p>

<form method="post" action="../LoginServlet">
<input type="hidden" name="sp" value="<%=sp%>">
<table>
        <tr>
                <td colspan="2">
In order for your games to be recorded properly, we invite you to provide
one or both of the following, to be associated with your record:

</td>
        </tr>
        <tr>
                <td>Email (optional):</td>
                <td><input type="text" size=64 name="email" /></td>
        </tr>
        <tr>
                <td>Display name (nickname). This will be used in publicly
			    visible displays, such as the leader boards.
			    If this is your first time at Rule Game, you can use an arbitrary string here; if you are coming back to Rule Game, please enter the same nickname you did the first time. If left blank, we will use your email as the display name.
		</td>
                <td><input type="text" size=64 name="nickname" /></td>
        </tr>
        <tr>
                <td colspan="2"><button type="submit">Continue</button></td>
        </tr>
</form>
</table>

<hr>

<p>Alternatively, you can proceed anonymously.

<table>
<tr>
<form method="post" action="../LoginServlet">
<input type="hidden" name="sp" value="<%=sp%>">
<input type="hidden" name="anon" value="true">
<td colspan="2"><button type="submit">Continue anonymously</button></td>
</form>
</tr>
</table>
