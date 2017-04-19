package org.gscheduler.dao.task;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.gscheduler.entity.JobInfo;
import org.springframework.stereotype.Repository;


@Repository
public interface JobInfoDao {

    /**
     * 搜索所有任务调度信息
     */
    List<JobInfo> selectAllJobInfo();

    JobInfo selectJobInfoByClassName(@Param("name") String name);

    /**
     * 搜索所有任务调度信息
     */
    List<JobInfo> selectJobInfoByHostname(String hostname);

    /**
     * 搜索包含本机的任务
     */
    List<JobInfo> selectJobInfoContainHostname(String hostname);

    /**
     * 根据id查找任务
     */
    JobInfo selectJobInfoById(long id);

    String selectCrontabByTaskClass(String jobClass);

    /**
     * 根据id更新TaskShecdule 不更新executeTime,lastExecuteTime,nextExecuteTime
     *
     */
    Integer updateJobInfoById(JobInfo jobInfo);

    /**
     * version字段自增1
     */
    Integer updateVersionAutoAdd(long id);

    Integer updateExecuteTime(@Param("id") long id, @Param("executeTime") String executeTime);

    Integer updateLastAndNextExecuteTime(@Param("id") long id, @Param("lastExecuteTime") String lastExecuteTime,
            @Param("nextExecuteTime") String nextExecuteTime);

    Integer updateExecuteStatus(@Param("id") long id, @Param("executeStatus") short executeStatus);

    Integer updateInitiateMode(@Param("id") long id, @Param("initiateMode") short initiateMode);

    /**
     * 插入JobInfo对象到task_schedule表 不插入executeTime,lastExecuteTime,nextExecuteTime
     *
     */
    Integer insertJobInfo(JobInfo taskSchedule);

    Integer deleteJobInfoById(long id);

}
