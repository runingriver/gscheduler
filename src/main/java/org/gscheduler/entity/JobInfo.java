package org.gscheduler.entity;

import java.util.Date;

import com.google.common.base.MoreObjects;

/**
 * 对应表job_info的实体类
 */
public class JobInfo {
    private long id;
    // 任务名称
    private String jobName;
    // 任务调用的类名
    private String jobClass;
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

    public short getExecuteStatus() {
        return executeStatus;
    }

    public void setExecuteStatus(short executeStatus) {
        this.executeStatus = executeStatus;
    }

    public String getConfigParameter() {
        return configParameter;
    }

    public void setConfigParameter(String configParameter) {
        this.configParameter = configParameter;
    }

    public String getCrontab() {
        return crontab;
    }

    public void setCrontab(String crontab) {
        this.crontab = crontab;
    }

    public String getExecuteTime() {
        return executeTime;
    }

    public void setExecuteTime(String executeTime) {
        this.executeTime = executeTime;
    }

    public String getExecuteHost() {
        return executeHost;
    }

    public void setExecuteHost(String executeHost) {
        this.executeHost = executeHost;
    }

    public String getHostList() {
        return hostList;
    }

    public void setHostList(String hostList) {
        this.hostList = hostList;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public short getInitiateMode() {
        return initiateMode;
    }

    public void setInitiateMode(short initiateMode) {
        this.initiateMode = initiateMode;
    }

    public Date getLastExecuteTime() {
        return lastExecuteTime;
    }

    public void setLastExecuteTime(Date lastExecuteTime) {
        this.lastExecuteTime = lastExecuteTime;
    }

    public Date getNextExecuteTime() {
        return nextExecuteTime;
    }

    public void setNextExecuteTime(Date nextExecuteTime) {
        this.nextExecuteTime = nextExecuteTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getJobClass() {
        return jobClass;
    }

    public void setJobClass(String jobClass) {
        this.jobClass = jobClass;
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    @Override public String toString() {
        return "JobInfo{" +
                "configParameter='" + configParameter + '\'' +
                ", id=" + id +
                ", jobName='" + jobName + '\'' +
                ", jobClass='" + jobClass + '\'' +
                ", crontab='" + crontab + '\'' +
                ", initiateMode=" + initiateMode +
                ", hostList='" + hostList + '\'' +
                ", executeHost='" + executeHost + '\'' +
                ", version=" + version +
                ", executeStatus=" + executeStatus +
                ", executeTime='" + executeTime + '\'' +
                ", lastExecuteTime=" + lastExecuteTime +
                ", nextExecuteTime=" + nextExecuteTime +
                ", updateTime=" + updateTime +
                '}';
    }
}
