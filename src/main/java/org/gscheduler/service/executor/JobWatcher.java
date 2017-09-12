package org.gscheduler.service.executor;

import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Service;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.gscheduler.entity.JobInfo;
import org.gscheduler.service.task.JobInfoService;
import org.gscheduler.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 负责监控任务的健康状态,维护自己的心跳,并监听其他主机的心跳
 */
@Component
public class JobWatcher {
    private static final Logger logger = LoggerFactory.getLogger(JobWatcher.class);
    @Resource
    JobManager jobManager;
    @Resource
    JobInfoService jobInfoService;

    @Resource
    JobListener jobListener;

    @Value("${job.heartbeat}")
    String jobHeartbeat;

    // 心跳频率
    private long heartbeat = 2;
    //时间单位
    private final TimeUnit TIME_UNIT = TimeUnit.MINUTES;
    //认为心跳已死的时间间隔
    private final long INTERVAL = TIME_UNIT.toMillis(heartbeat) * 2;

    private final ScheduledExecutorService watcher = Executors.newSingleThreadScheduledExecutor();

    public void init() {
        if (jobManager.getIsUsedZKListener()) {
            jobListener.init();
        }

        long heart = NumberUtils.toLong(jobHeartbeat, 0);
        if (heart > 0 && heart != heartbeat) {
            heartbeat = heart;
        }

        // 固定周期执行
        watcher.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    List<JobInfo> localJob = jobInfoService.getLocalJobInfo();
                    // 1.监控本地job
                    watchJob(localJob);
                    // 2.维持本机心跳
                    heartbeat(localJob);
                    // 3. 监控其他机器心跳
                    List<JobInfo> otherJob = jobInfoService.getJobInfoContainHostname();
                    watchOtherMachineHeartbeat(otherJob);
                } catch (Exception e) {
                    logger.error("Watcher:thread jobWatcher-thread-pool occur exception.", e);
                }
            }
        }, 1, heartbeat, TIME_UNIT);
    }

    /**
     * 监控属于本机执行的任务状态,本机任务运行状态检测.
     */
    public void watchJob(List<JobInfo> localJob) {
        if (null == localJob || localJob.isEmpty()) {
            return;
        }
        Map<Long, JobScheduler> jobSchedulerMaps = jobManager.getJobSchedulerMaps();

        for (JobInfo jobInfo : localJob) {
            JobScheduler jobScheduler = jobSchedulerMaps.get(jobInfo.getId());
            //不检查子任务
            if (StringUtils.isNotBlank(jobInfo.getParentName())) {
                continue;
            }
            //检查运行状态
            watchExecuteState(jobInfo, jobScheduler);
        }
        //检查是否存在nonlocal task
        watchExecuteHost(localJob);
        //监控本地sub_task
        watchLocalSubJob(localJob);
    }

    /**
     * 检查任务的运行状态
     *
     * @param jobInfo      obj
     * @param jobScheduler job
     */
    private void watchExecuteState(JobInfo jobInfo, JobScheduler jobScheduler) {
        // 如果该任务没有启用,则检测是否在运行,是-关闭
        if (jobInfo.getInitiateMode() == JobManager.DISABLE) {
            if (null != jobScheduler) {
                Service.State state = jobScheduler.state();
                if (state == Service.State.NEW || state == Service.State.STARTING
                        || state == Service.State.RUNNING) {
                    logger.info("Watcher:task:{},task id disabled but is running,terminate task right now.", jobInfo.getJobName());
                    jobInfoService.modifyInitiateMode(jobInfo.getId(), JobManager.DISABLE);
                    jobManager.stopSchedule(jobInfo.getId());
                }
            }
        } else if (jobInfo.getInitiateMode() == JobManager.AVAILABLE) {
            // 如果该任务是启用,检查是否运行中,不是-启动
            if (null != jobScheduler) {
                Service.State state = jobScheduler.state();
                if (state == Service.State.NEW || state == Service.State.STARTING
                        || state == Service.State.RUNNING) {
                    return;
                }
            }
            // 任务不存在或任务不在正常State,重新启动任务
            logger.info("Watcher:task:{},task is enabled but not running,start right now.", jobInfo.getJobName());
            jobManager.startSchedule(jobInfo.getId());
        }
    }

    /**
     * 检查执行主机是否合法
     *
     * @param localJob 本地执行列表
     */
    private void watchExecuteHost(List<JobInfo> localJob) {
        Map<Long, JobScheduler> jobSchedulerMaps = jobManager.getJobSchedulerMaps();
        if (jobSchedulerMaps.size() <= localJob.size()) {
            return;
        }
        //存在非本机运行的任务
        logger.info("Watcher:exist nonlocal task running on the local.");
        boolean isExisted;
        JobInfo task;
        for (JobScheduler scheduler : jobSchedulerMaps.values()) {
            isExisted = false;
            for (JobInfo jobInfo : localJob) {
                if (jobInfo.getId() == scheduler.getId()) {
                    isExisted = true;
                    break;
                }
            }
            if (isExisted) {
                continue;
            }

            //非本机任务
            task = jobInfoService.getJobInfoById(scheduler.getId());
            if (StringUtils.equals(task.getFailExecuteHost(), Utils.getHostName())) {
                //failover的执行host
                continue;
            }
            //既不是failover host,也不是ExecuteHost,关闭任务
            logger.info("Watcher:close nonlocal task,task:{}", task.toString());
            jobManager.stopSchedule(scheduler.getId());
        }
    }

    private Map<Long, Long> subTaskVersionMap = Maps.newHashMap();

    /**
     * 监控local sub job的生命周期.
     */
    public void watchLocalSubJob(List<JobInfo> localJob) {
        Map<Long, JobScheduler> jobSchedulerMaps = jobManager.getJobSchedulerMaps();
        for (JobInfo jobInfo : localJob) {
            if (StringUtils.isBlank(jobInfo.getParentName())) {
                continue;
            }

            Long id = jobInfo.getId();
            if (subTaskVersionMap.containsKey(id) && subTaskVersionMap.get(id) >= jobInfo.getJobVersion()) {
                //logger.info("Do not meet execute condition,ignore.");
                continue;
            }

            //如果是首次加入,监测,则第一次不执行
            if (!subTaskVersionMap.containsKey(id)) {
                subTaskVersionMap.put(id, jobInfo.getJobVersion());
                continue;
            }

            logger.info("execute local sub job:{}", jobInfo.toString());
            subTaskVersionMap.put(id, jobInfo.getJobVersion());
            if (jobSchedulerMaps.containsKey(id)) {
                jobManager.startSchedule(id);
            } else {
                jobManager.initJobScheduler(jobInfo).startAsync().awaitRunning();
            }
            jobManager.stopSchedule(id);
        }
    }


    /**
     * 更新属于本机执行的任务的心跳,检测包含本机host的任务心跳
     */
    public void heartbeat(List<JobInfo> localJob) {
        if (null == localJob || localJob.isEmpty()) {
            return;
        }
        Date date = new Date();
        for (JobInfo jobInfo : localJob) {
            jobInfoService.modifyVersion(jobInfo.getId(), date.getTime());
        }
    }

    /**
     * 查看不属于本机执行,但是本机在候选列表中的任务的心跳
     */
    public void watchOtherMachineHeartbeat(List<JobInfo> otherJob) {
        if (null == otherJob || otherJob.isEmpty()) {
            return;
        }
        // 心跳监测逻辑,任务是启用状态
        for (JobInfo jobInfo : otherJob) {
            if (jobInfo.getInitiateMode() != JobManager.AVAILABLE) {
                //任务是禁用状态
                continue;
            }

            long versionGap = new Date().getTime() - jobInfo.getVersion();
            if (versionGap > INTERVAL && StringUtils.isBlank(jobInfo.getFailExecuteHost())) {
                //当前时间减去version时间的间隔大于10分钟,认为心跳已死
                logger.warn("heartbeat dead,task has be shut down, task:{}", jobInfo.toString());
            }
        }
    }
}
