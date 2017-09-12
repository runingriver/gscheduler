package org.gscheduler.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 对应表job_info的实体类
 */
@Data
@NoArgsConstructor
public class JobInfo {
    private long id;
    // 任务名称
    private String jobName;
    // 任务调用的类名
    private String jobClass;
    //依赖调度,父调度任务名taskName,仅做标识
    private String parentName;
    //依赖调度,子任务的id集合
    private String subJob;
    //任务执行version,父task通知子task
    private long jobVersion;
    // 任务配置参数
    private String configParameter;
    // 任务执行时间正则,参考cron
    private String crontab;
    // 任务启用情况:1-启用,0-禁用
    private short initiateMode;
    // 主机列表,逗号分隔
    private String hostList;
    // 指定执行的机器
    private String executeHost;
    //failover执行机器
    private String failExecuteHost;
    // 用作保证任务运行
    private long version;
    // 任务执行情况,-1:未执行,0:执行失败,1:运行中,2:成功
    private short executeStatus;
    // 最近任务执行时长,单位秒
    private String executeTime;
    // 上一次执行时间
    private Date lastExecuteTime;
    // 下一次执行时间
    private Date nextExecuteTime;
    // 任务操作时间
    private Date updateTime;
}
