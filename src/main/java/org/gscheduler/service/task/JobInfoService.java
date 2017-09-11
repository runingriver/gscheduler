package org.gscheduler.service.task;

import java.util.Date;
import java.util.List;

import org.gscheduler.entity.JobInfo;

/**
 * 任务调度 Created by zongzhehu on 16-12-26.
 */
public interface JobInfoService {

    /**
     * 获取所有定时任务
     */
    List<JobInfo> getAllJobInfo();

    /**
     * 搜索在本机执行的所有任务
     */
    List<JobInfo> getLocalJobInfo();

    /**
     * 搜索不在本机执行,但是主机列表中包含本机的任务
     */
    List<JobInfo> getJobInfoContainHostname();

    /**
     * 根据任务名获取job
     */
    JobInfo getJobInfoByTaskName(String taskName);

    /**
     * 根据id获取Job
     */
    JobInfo getJobInfoById(long id);

    /**
     * failover时,获取执行机器为executeHost,且包含本机的task
     *
     * @param executeHost executeHost
     * @return list
     */
    List<JobInfo> getJobInfoForFailover(String executeHost);

    /**
     * 更新job
     * @param jobInfo
     */
    void modifyJobInfo(JobInfo jobInfo);

    /**
     * 保存job
     */
    void saveJobInfo(JobInfo jobInfo);

    /**
     * 移除job
     */
    void removeJobInfo(long id);

    void stopJobInfo(long id);

    void startJobInfo(long id);

    void modifySubTaskVersion(long id);

    void modifyVersion(long id, long value);

    /**
     * 更新执行时间
     */
    void modifyExecuteTime(long id, String executeTime);

    void modifyFailExecuteHost(long id, String host);

    /**
     * 更新本次执行时间和下一次执行时间
     */
    void modifyLastAndNextExecuteTime(long id, Date lastExecuteTime, Date nextExecuteTime);

    /**
     * 更新执行状态
     */
    void modifyExecuteStatus(long id, short executeStatus);


    /**
     * 更新job的状态
     */
    void modifyInitiateMode(long id, short initiateMode);

    /**
     * 根据类名获取job
     */
    JobInfo getJobInfoByClassName(String className);
}
