package org.gscheduler.service.task.impl;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.gscheduler.dao.task.JobInfoDao;
import org.gscheduler.entity.JobInfo;
import org.gscheduler.exception.SqlOperationException;
import org.gscheduler.service.task.JobInfoService;
import org.gscheduler.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.google.common.base.Preconditions;

/**
 * 任务调度数据库操作实体类
 */
@Service
public class JobInfoServiceImpl implements JobInfoService {
    private static final Logger logger = LoggerFactory.getLogger(JobInfoServiceImpl.class);

    @Resource
    JobInfoDao jobInfoDao;

    public List<JobInfo> getAllJobInfo() {
        List<JobInfo> jobInfos = jobInfoDao.selectAllJobInfo();

        // 如果集合为空,返回一个空集合
        return CollectionUtils.isEmpty(jobInfos) ? Collections.<JobInfo> emptyList() : jobInfos;
    }

    public List<JobInfo> getJobInfoByHostname() {
        String hostname = Utils.getHostName();
        List<JobInfo> jobInfos = jobInfoDao.selectJobInfoByHostname(hostname);

        // 如果集合为空,返回一个空集合
        return CollectionUtils.isEmpty(jobInfos) ? Collections.<JobInfo> emptyList() : jobInfos;
    }

    public List<JobInfo> getJobInfoContainHostname() {
        String hostname = Utils.getHostName();
        List<JobInfo> jobInfos = jobInfoDao.selectJobInfoContainHostname(hostname);

        // 如果集合为空,返回一个空集合
        return CollectionUtils.isEmpty(jobInfos) ? Collections.<JobInfo> emptyList() : jobInfos;
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

    public void modifyJobInfo(JobInfo jobInfo) {
        Preconditions.checkNotNull(jobInfo, "argument jobInfo illegal.");
        // 不更新任务名
        if (StringUtils.isNotBlank(jobInfo.getJobName())) {
            jobInfo.setJobName(null);
        }

        Integer result;
        try {
            result = jobInfoDao.updateJobInfoById(jobInfo);
        } catch (RuntimeException e) {
            throw new SqlOperationException("更新定时任务JobInfo失败.", jobInfo, e);
        }
        if (!Utils.isPositiveNumber(result)) {
            logger.error("更新定时任务失败:{}", jobInfo);
        }
    }

    public void saveJobInfo(JobInfo taskSchedule) {
        Preconditions.checkNotNull(taskSchedule, "argument jobInfo illegal.");
        Integer result;
        try {
            result = jobInfoDao.insertJobInfo(taskSchedule);
        } catch (RuntimeException e) {
            throw new SqlOperationException("添加定时任务JobInfo失败.", taskSchedule, e);
        }
        if (!Utils.isPositiveNumber(result)) {
            logger.error("添加定时任务失败:{}", taskSchedule);
        }
    }

    public void removeJobInfo(long id) {
        Preconditions.checkArgument(id > 0, "argument id illegal");
        logger.info("select Task by id:{}", id);
        Integer result;
        try {
            result = jobInfoDao.deleteJobInfoById(id);
        } catch (Exception e) {
            throw new SqlOperationException("删除定时任务数据失败.", id, e);
        }

        if (!Utils.isPositiveNumber(result)) {
            logger.error("删除定时任务数据失败.id:{}", id);
        }
    }

    public void modifyVersionAutoAdd(long id) {
        Preconditions.checkArgument(id > 0, "argument id illegal");
        try {
            jobInfoDao.updateVersionAutoAdd(id);
        } catch (RuntimeException e) {
            throw new SqlOperationException("version字段自增", id);
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
        JobInfo jobInfo = jobInfoDao.selectJobInfoByClassName(className);
        return jobInfo;
    }

}
