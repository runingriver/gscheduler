package org.gscheduler.dao.task;

import org.apache.ibatis.annotations.Param;
import org.gscheduler.entity.JobInfo;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
public interface JobInfoDao {

    /**
     * 搜索所有任务调度信息
     */
    List<JobInfo> selectAllJobInfo();

    List<JobInfo> selectJobInfoForFailover(@Param("localHost") String localHost, @Param("executeHost") String executeHost);

    /**
     * 根据类名查询jobInfo
     *
     * @param name 类名
     * @return JobInfo
     */
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

    /**
     * 查询任务的cron表达式
     *
     * @param jobClass TaskClass
     * @return String
     */
    String selectCrontabByTaskClass(String jobClass);

    JobInfo selectJobInfoByTaskName(String taskName);

    /**
     * 根据id更新TaskShecdule 不更新executeTime,lastExecuteTime,nextExecuteTime
     */
    Integer updateJobInfoById(JobInfo jobInfo);

    Integer updateTaskVersion(@Param("id") long id);

    /**
     * 更新version字段
     */
    Integer updateVersion(@Param("id") long id, @Param("version") long version);

    Integer updateFailExecuteHost(@Param("id") long id, @Param("failExecuteHost") String failExecuteHost);

    /**
     * 更新执行时间
     */
    Integer updateExecuteTime(@Param("id") long id, @Param("executeTime") String executeTime);

    /**
     * 更新上一次和下一次执行时间
     */
    Integer updateLastAndNextExecuteTime(@Param("id") long id, @Param("lastExecuteTime") String lastExecuteTime,
                                         @Param("nextExecuteTime") String nextExecuteTime);

    /**
     * 更新执行状态
     */
    Integer updateExecuteStatus(@Param("id") long id, @Param("executeStatus") short executeStatus);

    /**
     * 更新初始化状态
     */
    Integer updateInitiateMode(@Param("id") long id, @Param("initiateMode") short initiateMode);

    /**
     * 插入JobInfo对象到task_schedule表 不插入executeTime,lastExecuteTime,nextExecuteTime
     */
    Integer insertJobInfo(JobInfo jobInfo);

    /**
     * 删除JobInfo
     */
    Integer deleteJobInfoById(long id);

}
