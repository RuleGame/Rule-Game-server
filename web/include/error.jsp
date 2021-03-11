<!-- This HTML snippet is used when an error was reported in the underlyng
     Java method. The object main is of a class derived from  ResponseBase -->

<h3>Error</h3>
<p> <em class="errMsg"><%= main.getErrmsg() %></em></p>
<%  if (main.getEx() != null) { %>
<p><%= main.getEx() %></p>
<hr>
<p>Details of the exception, if you care for them:</p>

<p><small>
<pre><%= main.exceptionTrace() %></pre></small></p>
<%      } %>
