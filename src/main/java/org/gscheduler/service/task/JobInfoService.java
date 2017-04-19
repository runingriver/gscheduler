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
    List<JobInfo> getJobInfoByHostname();

    /**
     * 搜索不在本机执行,但是主机列表中包含本机的任务
     */
    List<JobInfo> getJobInfoContainHostname();

    JobInfo getJobInfoById(long id);

    void modifyJobInfo(JobInfo taskSchedule);

    void saveJobInfo(JobInfo taskSchedule);

    void removeJobInfo(long id);

    void modifyVersionAutoAdd(long id);

    void modifyExecuteTime(long id, String executeTime);

    void modifyLastAndNextExecuteTime(long id, Date lastExecuteTime, Date nextExecuteTime);

    void modifyExecuteStatus(long id, short executeStatus);

    void modifyInitiateMode(long id, short initiateMode);

    JobInfo getJobInfoByClassName(String className);
}
