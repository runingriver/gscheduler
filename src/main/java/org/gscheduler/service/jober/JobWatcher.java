package org.gscheduler.service.jober;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.Resource;

import org.gscheduler.entity.JobInfo;
import org.gscheduler.service.task.JobInfoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Service;

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

    // 心跳频率
    private static final long HEARTBEAT = 5;
    // key:对应jobScheduler和数据任务id,value:对应version
    private final HashMap<Long, Long> heartbeatMap = Maps.newHashMap();

    private final ScheduledExecutorService watcher = Executors.newSingleThreadScheduledExecutor();

    public void init() {
        // 固定周期执行
        watcher.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    List<JobInfo> localJob = jobInfoService.getJobInfoByHostname();
                    // 1.监控本地job
                    watchJob(localJob);
                    // 2. 维持本机心跳
                    heartbeat(localJob);
                    // 3. 监控其他机器心跳
                    // List<JobInfo> otherJob = jobInfoService.getJobInfoContainHostname();
                    // watchOtherMachineHeartbeat(otherJob);
                } catch (Exception e) {
                    logger.error("监控线程jobWatcher-thread-pool出现异常.", e);
                }
            }
        }, 1, HEARTBEAT, TimeUnit.MINUTES);
    }

    /**
     * 监控属于本机执行的任务,如果停掉了,则重启. // TODO: 17-1-23 如果修改了时间表达式,也应该监控?
     */
    public void watchJob(List<JobInfo> localJob) {
        logger.info("JobWatcher开始检查本机任务...");
        if (null == localJob || localJob.isEmpty()) {
            return;
        }
        Map<Long, JobScheduler> jobSchedulerMaps = jobManager.getJobSchedulerMaps();

        for (JobInfo taskSchedule : localJob) {
            JobScheduler jobScheduler = jobSchedulerMaps.get(taskSchedule.getId());
            // 如果该任务没有启用,则检测是否在运行,是-关闭
            if (taskSchedule.getInitiateMode() == JobManager.DISABLE) {
                if (null != jobScheduler) {
                    Service.State state = jobScheduler.state();
                    if (state == Service.State.NEW || state == Service.State.STARTING
                            || state == Service.State.RUNNING) {
                        logger.info("Watcher发现任务:{},禁用但是在运行的任务,立刻终止任务执行.", taskSchedule.getJobName());
                        jobManager.stopSchedule(taskSchedule.getId());
                    }
                }
            } else if (taskSchedule.getInitiateMode() == JobManager.AVAILABLE) {
                // 如果该任务是启用,检查是否运行中,不是-启动
                if (null != jobScheduler) {
                    Service.State state = jobScheduler.state();
                    if (state == Service.State.NEW || state == Service.State.STARTING
                            || state == Service.State.RUNNING) {
                        continue;// 状态正常.
                    }
                }
                // 任务不存在或任务不在正常State,重新启动任务
                logger.info("Watcher发现任务:{},启用但是没有执行的任务,立刻启动任务执行.", taskSchedule.getJobName());
                jobManager.updateSchedule(taskSchedule.getId());
            }
        }
        logger.info("JobWatcher检查本机任务结束...");
    }

    /**
     * 更新属于本机执行的任务的心跳,检测包含本机host的任务心跳
     */
    public void heartbeat(List<JobInfo> localJob) {
        if (null == localJob || localJob.isEmpty()) {
            return;
        }
        for (JobInfo taskSchedule : localJob) {
            jobInfoService.modifyVersionAutoAdd(taskSchedule.getId());
        }
    }

    /**
     * 查看不属于本机执行,但是本机在候选列表中的任务的心跳
     */
    public void watchOtherMachineHeartbeat(List<JobInfo> otherJob) {
        if (null == otherJob || otherJob.isEmpty()) {
            return;
        }
        // 心跳监测逻辑
        for (JobInfo taskSchedule : otherJob) {
            if (!heartbeatMap.containsKey(taskSchedule.getId())) {
                // 没有该心跳,加入
                heartbeatMap.put(taskSchedule.getId(), taskSchedule.getVersion());
            } else {
                // 有心跳,计算心跳是否在跳动
            }
        }
    }

    private static class Heart {
        // 心跳
        private long version;
        // 是否在跳动
        private boolean beating;
    }
}
