package org.gscheduler.service.task.impl;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.apache.zookeeper.CreateMode;
import org.gscheduler.commons.ZkHelper;
import org.gscheduler.dao.task.JobInfoDao;
import org.gscheduler.entity.JobInfo;
import org.gscheduler.exception.SqlOperationException;
import org.gscheduler.service.jober.JobListener;
import org.gscheduler.service.jober.JobManager;
import org.gscheduler.service.task.JobInfoService;
import org.gscheduler.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 任务调度数据库操作实体类
 */
@Service
public class JobInfoServiceImpl implements JobInfoService {
    private static final Logger logger = LoggerFactory.getLogger(JobInfoServiceImpl.class);

    @Resource
    JobInfoDao jobInfoDao;

    @Resource
    ZkHelper zkHelper;

    @Resource
    JobManager jobManager;

    public List<JobInfo> getAllJobInfo() {
        List<JobInfo> jobInfos = jobInfoDao.selectAllJobInfo();

        // 如果集合为空,返回一个空集合
        return CollectionUtils.isEmpty(jobInfos) ? Collections.<JobInfo>emptyList() : jobInfos;
    }

    public List<JobInfo> getLocalJobInfo() {
        String hostname = Utils.getHostName();
        List<JobInfo> jobInfos = jobInfoDao.selectJobInfoByHostname(hostname);

        return CollectionUtils.isEmpty(jobInfos) ? Collections.<JobInfo>emptyList() : jobInfos;
    }

    public List<JobInfo> getJobInfoContainHostname() {
        String hostname = Utils.getHostName();
        List<JobInfo> jobInfos = jobInfoDao.selectJobInfoContainHostname(hostname);

        return CollectionUtils.isEmpty(jobInfos) ? Collections.<JobInfo>emptyList() : jobInfos;
    }

    public List<JobInfo> getJobInfoForFailover(String executeHost) {
        String hostname = Utils.getHostName();
        List<JobInfo> jobInfos = jobInfoDao.selectJobInfoForFailover(hostname, executeHost);

        return CollectionUtils.isEmpty(jobInfos) ? Collections.<JobInfo>emptyList() : jobInfos;
    }

    public JobInfo getJobInfoById(long id) {
        Preconditions.checkArgument(id > 0, "argument id illegal");
        logger.info("select Task by id:{}", id);
        JobInfo jobInfo = jobInfoDao.selectJobInfoById(id);

        if (null == jobInfo) {
            logger.error("cannot select the JobInfo of id:{}", id);
        }
        return jobInfo;
    }

    public JobInfo getJobInfoByTaskName(String taskName) {
        Preconditions.checkArgument(StringUtils.isNotBlank(taskName), "argument taskName illegal");
        JobInfo jobInfo = null;
        try {
            jobInfo = jobInfoDao.selectJobInfoByTaskName(taskName.trim());
        } catch (Exception e) {
            logger.error("select jobInfo by taskName exception.taskName:{}", taskName);
        }
        return jobInfo;
    }

    public void modifyJobInfo(JobInfo jobInfo) {
        Preconditions.checkNotNull(jobInfo, "argument jobInfo illegal.");

        long id = jobInfo.getId();
        JobInfo oldSchedule = jobInfoDao.selectJobInfoById(id);

        boolean isSubTask = false;
        String parentName = jobInfo.getParentName();
        //更新父task的sub_task字段
        if (StringUtils.isNotBlank(parentName)) {
            JobInfo parentSchedule = jobInfoDao.selectJobInfoByTaskName(parentName);
            //存在父任务,则更新父任务的sub_task,否则置本task的parentTask为空
            if (parentSchedule != null) {
                String idString = String.valueOf(id);
                isSubTask = true;
                JobInfo ts = new JobInfo();
                ts.setId(parentSchedule.getId());

                try {
                    if (StringUtils.isBlank(parentSchedule.getSubTask())) {
                        ts.setSubTask(idString);
                        ts.setInitiateMode(parentSchedule.getInitiateMode());
                        jobInfoDao.updateJobInfoById(ts);
                    } else if (!parentSchedule.getSubTask().contains(idString)) {
                        idString = parentSchedule.getSubTask() + ',' + idString;
                        ts.setSubTask(idString);
                        jobInfoDao.updateJobInfoById(ts);
                    }
                } catch (Exception e) {
                    throw new SqlOperationException("更父任务的sub_task失败.", e);
                }
                logger.info("更新父任务:{}", parentName, idString);
            } else {
                //输入错误,不更新
                jobInfo.setParentName("");
            }
        }

        //check该子任务原来ParentName不为空的情况
        if (StringUtils.isNotBlank(oldSchedule.getParentName()) && StringUtils.isBlank(parentName)) {
            jobInfo.setParentName("");
        }

        try {
            jobInfoDao.updateJobInfoById(jobInfo);
        } catch (RuntimeException e) {
            throw new SqlOperationException("更新定时任务JobInfo失败.", jobInfo, e);
        }

        //如果是依赖任务,则直接返回.
        if (isSubTask) {
            return;
        }

        boolean isParamChanged = !StringUtils.equals(oldSchedule.getConfigParameter(), jobInfo.getConfigParameter());
        boolean isCronChanged = !StringUtils.equals(oldSchedule.getCrontab(), jobInfo.getCrontab());
        boolean isExeHostChanged = !StringUtils.equals(oldSchedule.getExecuteHost(), jobInfo.getExecuteHost());
        boolean isInitModeChanged = oldSchedule.getInitiateMode() != jobInfo.getInitiateMode();

        logger.info("JobInfo update,Param:{},Cron:{},ExeHost:{},InitMode:{}"
                , isParamChanged, isCronChanged, isExeHostChanged, isInitModeChanged);

        if (isParamChanged || isCronChanged || isExeHostChanged || isInitModeChanged) {
            if (jobManager.getIsUsedZKListener()) {
                logger.info("task changed update and notify zookeeper.name:{}", jobInfo.getTaskName());
                //FailExecuteHost参数不会被修改
                jobInfo.setFailExecuteHost(oldSchedule.getFailExecuteHost());
                notifyZooKeeper(jobInfo, JobManager.JobOperator.STOP_OR_START);
            } else {
                if (StringUtils.equals(jobInfo.getExecuteHost(), Utils.getHostName())
                        && jobInfo.getInitiateMode() == JobManager.AVAILABLE) {
                    logger.info("local job restart job,name:{}", jobInfo.getTaskName());
                    jobManager.restartSchedule(id);
                }
            }
        }
    }

    private void notifyZooKeeper(JobInfo jobInfo, JobManager.JobOperator operator) {
        if (jobInfo.getFailExecuteHost() == null) {
            jobInfo.setFailExecuteHost("");
        }
        String jobJson = JobListener.createJobJson(jobInfo, operator);
        String nodePath = JobListener.SERVICE_UPDATE_PATH + "/" + jobInfo.getTaskName();

        zkHelper.getDefaultZKClient().setData(nodePath, jobJson.getBytes());
    }

    public void stopJobInfo(long id) {
        Preconditions.checkArgument(id > 0, "argument id illegal");
        this.modifyInitiateMode(id, JobManager.DISABLE);

        JobInfo jobInfo = jobInfoDao.selectJobInfoById(id);
        if (jobManager.getIsUsedZKListener()) {
            logger.info("stop job task,task:{}", jobInfo.toString());
            notifyZooKeeper(jobInfo, JobManager.JobOperator.STOP);
        } else {
            if (StringUtils.equals(jobInfo.getExecuteHost(), Utils.getHostName())) {
                logger.info("stop local job,name:{}", jobInfo.getTaskName());
                jobManager.stopSchedule(id);
            }
        }
    }

    public void startJobInfo(long id) {
        Preconditions.checkArgument(id > 0, "argument id illegal");
        this.modifyInitiateMode(id, JobManager.AVAILABLE);

        JobInfo jobInfo = jobInfoDao.selectJobInfoById(id);
        if (jobManager.getIsUsedZKListener()) {
            logger.info("start job task,task:{}", jobInfo.toString());
            notifyZooKeeper(jobInfo, JobManager.JobOperator.START);
        } else {
            if (StringUtils.equals(jobInfo.getExecuteHost(), Utils.getHostName())) {
                logger.info("start local job,name:{}", jobInfo.getTaskName());
                jobManager.startSchedule(id);
            }
        }
    }

    public void modifySubTaskVersion(long id) {
        Preconditions.checkNotNull(id > 0, "argument jobInfo illegal.");
        logger.info("notify sub task:{}", id);
        if (jobManager.getIsUsedZKListener()) {
            //zk push通知
        } else {
            //db pull通知
            try {
                jobInfoDao.updateTaskVersion(id);
            } catch (RuntimeException e) {
                throw new SqlOperationException("添加定时任务JobInfo失败.", id, e);
            }
        }
    }

    public void saveJobInfo(JobInfo jobInfo) {
        Preconditions.checkNotNull(jobInfo, "argument jobInfo illegal.");
        try {
            jobInfoDao.insertJobInfo(jobInfo);
        } catch (RuntimeException e) {
            throw new SqlOperationException("添加定时任务JobInfo失败.", jobInfo, e);
        }

        //如果是子任务,直接返回
        if (StringUtils.isNotBlank(jobInfo.getParentName())) {
            logger.info("保存JobInfo为子任务,不做任何工作,task:{}", jobInfo.toString());
            return;
        }

        if (jobManager.getIsUsedZKListener()) {
            //新增一个job,注册到zk上,如果是本地任务执行任务,添加监听
            String nodePath = JobListener.SERVICE_UPDATE_PATH + "/" + jobInfo.getTaskName();
            logger.info("add job,update zk,jobSchedule:{},path:{}", jobInfo.toString(), nodePath);

            String jobJson = JobListener.createJobJson(jobInfo, JobManager.JobOperator.START);
            zkHelper.getDefaultZKClient().createNode(nodePath, CreateMode.PERSISTENT, jobJson.getBytes());
        } else {
            if (StringUtils.equals(jobInfo.getExecuteHost(), Utils.getHostName())
                    && jobInfo.getInitiateMode() == JobManager.AVAILABLE) {
                logger.info("start local job,name:{}", jobInfo.getTaskName());
                jobManager.startSchedule(jobInfo.getId());
            }
        }
    }

    public void removeJobInfo(long id) {
        Preconditions.checkArgument(id > 0, "argument id illegal");
        logger.info("select Task by id:{}", id);
        JobInfo jobInfo = jobInfoDao.selectJobInfoById(id);
        try {
            jobInfoDao.deleteJobInfoById(id);
        } catch (Exception e) {
            throw new SqlOperationException("删除定时任务数据失败.", id, e);
        }

        if (jobManager.getIsUsedZKListener()) {
            String nodePath = JobListener.SERVICE_UPDATE_PATH + "/" + jobInfo.getTaskName();
            logger.info("remove node,update zk,task:{},path:{}", jobInfo.toString(), nodePath);
            //移除节点
            zkHelper.getDefaultZKClient().deleteNode(nodePath);
        } else {
            if (StringUtils.equals(jobInfo.getExecuteHost(), Utils.getHostName())) {
                logger.info("remove local job,name:{}", jobInfo.getTaskName());
                jobManager.killSchedule(jobInfo.getId());
            }
        }
    }

    public void modifyVersion(long id, long value) {
        Preconditions.checkArgument(id > 0 && value > 0, "argument id illegal");
        try {
            jobInfoDao.updateVersion(id, value);
        } catch (RuntimeException e) {
            throw new SqlOperationException("version字段自增", id);
        }
    }

    public void modifyFailExecuteHost(long id, String host) {
        Preconditions.checkArgument(id > 0, "argument id illegal");
        logger.info("id:{},host:{}", id, host);
        try {
            jobInfoDao.updateFailExecuteHost(id, host);
        } catch (RuntimeException e) {
            throw new SqlOperationException("更新fail_execute_host失败.", id);
        }
    }

    public void modifyExecuteStatus(long id, short executeStatus) {
        Preconditions.checkArgument(id > 0, "argument id illegal");
        Preconditions.checkArgument(executeStatus >= -1 && executeStatus <= 2, "executeStatus illegal.");
        try {
            jobInfoDao.updateExecuteStatus(id, executeStatus);
        } catch (RuntimeException e) {
            throw new SqlOperationException("executeStatus(任务执行情况字段)失败", id, executeStatus);
        }
    }

    public void modifyExecuteTime(long id, String executeTime) {
        Preconditions.checkArgument(id > 0, "argument id illegal");
        Preconditions.checkArgument(StringUtils.isNotBlank(executeTime), "executeTime illegal.");

        try {
            jobInfoDao.updateExecuteTime(id, executeTime);
        } catch (RuntimeException e) {
            logger.error("更新任务时间信息失败,executeTime:{}", executeTime);
            throw new SqlOperationException("更新任务时间信息失败", id, e);
        }
    }

    @Override
    public void modifyLastAndNextExecuteTime(long id, Date lastExecuteTime, Date nextExecuteTime) {
        Preconditions.checkArgument(id > 0, "argument id illegal");
        Preconditions.checkArgument(lastExecuteTime != null, "lastExecuteTime illegal.");
        Preconditions.checkArgument(nextExecuteTime != null, "nextExecuteTime illegal.");

        try {
            jobInfoDao.updateLastAndNextExecuteTime(id, Utils.dateToString(lastExecuteTime),
                    Utils.dateToString(nextExecuteTime));
        } catch (RuntimeException e) {
            logger.error("更新任务时间信息失败,lastExecuteTime:{},nextExecuteTime:{}", lastExecuteTime, nextExecuteTime);
            throw new SqlOperationException("更新任务时间信息失败", id, e);
        }
    }

    public void modifyInitiateMode(long id, short initiateMode) {
        Preconditions.checkArgument(id > 0, "argument id illegal");
        Preconditions.checkArgument(initiateMode >= 0 && initiateMode <= 1, "initiateMode illegal.");
        try {
            jobInfoDao.updateInitiateMode(id, initiateMode);
        } catch (RuntimeException e) {
            throw new SqlOperationException("更新initiateMode(任务启用情况字段)失败", id, initiateMode);
        }
    }

    public JobInfo getJobInfoByClassName(String className) {
        Preconditions.checkArgument(StringUtils.isNotBlank(className), "className illegal.");
        return jobInfoDao.selectJobInfoByClassName(className);
    }

}
