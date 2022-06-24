<!-- this file is included into other files in the same directory -->
<p>

<form method="post" action="../MlcLoginServlet">
<input type="hidden" name="sp" value="<%=sp%>">
<table>
       <tr>
                <td>Nickname:</td>
                <td><input type="text" size=64 name="nickname" /></td>
        </tr>
        <tr>
                <td>Password:</td>
                <td><input type="text" size=64 name="password" /></td>
        </tr>
<tr>
<td colspan="2"><button type="submit">Log in</button></td>
</tr>

</table>
</form>





