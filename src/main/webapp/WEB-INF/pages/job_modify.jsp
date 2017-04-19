<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
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
            <a class="blog-nav-item " href="${ctx}/job/list">任务列表</a>
            <a class="blog-nav-item" href="${ctx}/job/add">添加任务</a>
            <a class="blog-nav-item" href="${ctx}/job/log">任务日志</a>
            <a class="blog-nav-item" href="${ctx}/job/about">关于</a>
        </nav>
    </div>
</div>

<div class="container-fluid main-content">
    <div class="page">
        <div class="row">
            <div class="col-md-3"></div>
            <div class="col-md-9"><h2>修改任务</h2></div>
        </div>
        <div class="row">
            <div class="col-md-3"></div>
            <div class="col-md-4">
                <form:form class="" action="${ctx}/job/update" role="form" method="post" commandName="jobInfo">
                    <input type="hidden" name="id" value="${job.id}">
                    <div class="form-group">
                        <label for="jobName">任务名:</label>
                        <input type="text" class="form-control" name="jobName" id="jobName" value="${job.jobName}"
                               readonly>
                    </div>
                    <div class="form-group">
                        <label for="jobClass">任务类名:<code>eg:arrivedMonitor 首字母小写</code></label>
                        <input type="text" class="form-control" name="jobClass" id="jobClass"
                               value="${job.jobClass}">
                    </div>
                    <div class="form-group">
                        <label for="configParameter">配置参数:</label>
                        <input type="text" class="form-control" name="configParameter" id="configParameter"
                               value="${job.configParameter}">
                    </div>
                    <div class="form-group">
                        <label for="crontab">时间正则crontab:<code>eg:0/min,5/min 延迟0分钟,每5分钟执行一次</code></label>
                        <input type="text" class="form-control" name="crontab" id="crontab" value="${job.crontab}"
                               required>
                    </div>
                    <div class="form-group">
                        <label for="initiateMode">启用状态:<code>1-启用任务,0-不启用任务</code></label>
                        <input type="text" class="form-control" name="initiateMode" id="initiateMode"
                               value="${job.initiateMode}" required>
                    </div>
                    <div class="form-group">
                        <label for="hostList">执行任务主机列表:<code>主机之间逗号(,)分隔</code></label>
                        <input type="text" class="form-control" name="hostList" id="hostList" value="${job.hostList}"
                               required>
                    </div>
                    <div class="form-group">
                        <label for="executeHost">当前执行主机:<code>eg:l-sms.monitor1.wap.cn1</code></label>
                        <input type="text" class="form-control" name="executeHost" id="executeHost"
                               value="${job.executeHost}" required>
                    </div>
                    <div class="form-group">
                        &nbsp;&nbsp;&nbsp;&nbsp;
                        <button type="submit" class="btn btn-success">提 交</button>
                        &nbsp;&nbsp;&nbsp;&nbsp;
                        <a href="${ctx}/job/list" class="btn btn-info active" role="button">返 回</a>
                    </div>
                </form:form>
            </div>
            <div class="col-md-5"></div>
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