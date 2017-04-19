<%@ page import="java.util.Map" %>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html>
<head>
    <title>view the process's thread stack</title>
</head>
<body>
<%
    StringBuilder threadInfo = new StringBuilder(500);
    //在html中显示所以格式要用html定义的
    threadInfo.append("<pre>");
    int size = Thread.getAllStackTraces().size();
    threadInfo.append("线程数:").append(size);
    threadInfo.append("<br>").append("<hr/>");
    //逐个打印线程栈
    for (Map.Entry<Thread, StackTraceElement[]> threadEntry : Thread.getAllStackTraces().entrySet()) {
        Thread key = threadEntry.getKey();
        StackTraceElement[] value = threadEntry.getValue();
        threadInfo.append(key.toString()).append("<br>");
        for (StackTraceElement stackTraceElement : value) {
            threadInfo.append("&nbsp;&nbsp;&nbsp;&nbsp;").append(stackTraceElement.toString()).append("<br>");
        }
        threadInfo.append("<hr/>");
    }
    threadInfo.append("</pre>");

%>
<div>
    <%=threadInfo.toString() %>
</div>

</body>
</html>
