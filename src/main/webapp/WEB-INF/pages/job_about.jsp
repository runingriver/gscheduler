<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ include file="taglibs.jsp" %>
<html lang="zh-CN">
<head>
    <meta charset="utf-8">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <!-- 上述3个meta标签*必须*放在最前面，任何其他内容都*必须*跟随其后！ -->
    <title>关于</title>

    <!-- 新 Bootstrap 核心 CSS 文件 -->
    <link rel="stylesheet" href="${ctx}/static/css/bootstrap.min.css">
    <!-- 自定义css -->
    <link rel="stylesheet" href="${ctx}/static/css/style.css"/>
</head>
<body>
<div class="blog-masthead">
    <div class="container">
        <nav class="blog-nav">
            <a class="blog-nav-item " href="${ctx}/job/list">任务列表</a>
            <a class="blog-nav-item" href="${ctx}/job/add">添加任务</a>
            <a class="blog-nav-item" href="${ctx}/job/log">任务日志</a>
            <a class="blog-nav-item active" href="${ctx}/job/about">关于</a>
        </nav>
    </div>
</div>

<div class="container-fluid main-content">
    <div class="page">
        <div class="row">
            <div class="col-md-3"></div>
            <div class="col-md-9"><h2>关于...</h2></div>
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
