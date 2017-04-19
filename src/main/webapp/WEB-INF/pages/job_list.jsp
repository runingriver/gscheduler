<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ include file="taglibs.jsp" %>
<html lang="zh-CN">
<head>
    <meta charset="utf-8">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <!-- 上述3个meta标签*必须*放在最前面，任何其他内容都*必须*跟随其后！ -->
    <title>任务详情</title>

    <!-- 新 Bootstrap 核心 CSS 文件 -->
    <link rel="stylesheet" href="${ctx}/static/css/bootstrap.min.css">
    <!-- 自定义css -->
    <link rel="stylesheet" href="${ctx}/static/css/style.css"/>
</head>
<body>
<div class="blog-masthead">
    <div class="container">
        <nav class="blog-nav">
            <a class="blog-nav-item active" href="${ctx}/job/list">任务列表</a>
            <a class="blog-nav-item" href="${ctx}/job/add">添加任务</a>
            <a class="blog-nav-item" href="${ctx}/job/log">任务日志</a>
            <a class="blog-nav-item" href="${ctx}/job/about">关于</a>
        </nav>
    </div>
</div>

<div class="container-fluid main-content">
    <div class="page">
        <div class="row">
            <div class="col-md-1"></div>
            <div class="col-md-10">
                <h2>任务列表&nbsp;&nbsp;<a href="${ctx}/job/close/all" type="button"
                                       class="btn btn-primary btn-xs">关闭所有任务</a></h2>
                <table class="table table-bordered table-hover custom-fz">
                    <tr>
                        <th>名称</th>
                        <th>类名</th>
                        <th>配置参数</th>
                        <th>时间表达式</th>
                        <th>执行机器</th>
                        <th>启用</th>
                        <th>状态</th>
                        <th>时长</th>
                        <th>上次执行时间</th>
                        <th>下次执行时间</th>
                        <th>操作</th>
                    </tr>
                    <c:forEach items="${jobList}" var="job">
                        <tr>
                            <th>${job.jobName}</th>
                            <th>${job.jobClass}</th>
                            <th>${job.configParameter}</th>
                            <th>${job.crontab}</th>
                            <th>${job.executeHost}</th>
                            <th>
                                <c:choose>
                                    <c:when test="${job.initiateMode == 1}">已启用</c:when>
                                    <c:when test="${job.initiateMode == 0}">禁用</c:when>
                                    <c:otherwise>未知</c:otherwise>
                                </c:choose>
                            </th>
                            <th>
                                <c:choose>
                                    <c:when test="${job.executeStatus == -1}">未执行</c:when>
                                    <c:when test="${job.executeStatus == 0}">执行失败</c:when>
                                    <c:when test="${job.executeStatus == 1}">运行中</c:when>
                                    <c:when test="${job.executeStatus == 2}">成功</c:when>
                                    <c:otherwise>未知</c:otherwise>
                                </c:choose>
                            </th>
                            <th>${job.executeTime}</th>
                            <th><fmt:formatDate value="${job.lastExecuteTime}" pattern="yyyy-MM-dd HH:mm:ss"/></th>
                            <th><fmt:formatDate value="${job.nextExecuteTime}" pattern="yyyy-MM-dd HH:mm:ss"/></th>
                            <th>
                                <a href="${ctx}/job/execute/${job.id}" role="button"
                                   class="btn btn-sm btn-info">执行</a>
                                <a href="${ctx}/job/modify/${job.id}" role="button"
                                   class="btn btn-sm btn-success">修改</a>
                                <a href="${ctx}/job/stop/${job.id}" role="button"
                                   class="btn btn-sm btn-warning">停止</a>
                                <a href="${ctx}/job/delete/${job.id}" role="button"
                                   class="btn btn-sm btn-danger">删除</a>
                            </th>
                        </tr>
                    </c:forEach>
                </table>
            </div>
            <div class="col-md-1"></div>
        </div>
    </div>
</div>

<div class="blog-footer">
    <p>Copyright © 2016 running-river.com</p>
</div>

<!-- jQuery文件。务必在bootstrap.min.js 之前引入 -->
<script src="${ctx}/static/js/jquery.min.js"></script>
<!-- 最新的 Bootstrap 核心 JavaScript 文件 -->
<script src="${ctx}/static/js/bootstrap.min.js"></script>
</body>
</html>
