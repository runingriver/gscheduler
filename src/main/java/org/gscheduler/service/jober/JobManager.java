package org.gscheduler.service.jober;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.gscheduler.entity.JobInfo;
import org.gscheduler.service.task.JobInfoService;
import org.gscheduler.utils.SpringContextHolder;
import org.gscheduler.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Service;

/**
 * 作业管理类,任务启动,关闭,停止.
 */
@Component
public class JobManager {
    private static final Logger logger = LoggerFactory.getLogger(JobManager.class);
    /** 启用状态 */
    public static final short AVAILABLE = 1;
    /** 禁用状态 */
    public static final short DISABLE = 0;
    /** 未执行 */
    private static final short UN_EXECUTE = -1;
    /** 执行失败 */
    private static final short FAILED_EXECUTE = 0;
    /** 运行中 */
    private static final short IN_EXECUTE = 1;
    /** 执行成功 */
    public static final short SUCCESS_EXECUTE = 2;

    @Resource
    JobInfoService jobInfoService;

    @Resource
    JobWatcher jobWatcher;

    /**
     * 多个线程会同事操作该Map,只存放正在运行的JobScheduler
     */
    private final Map<Long, JobScheduler> jobSchedulerMaps = Maps.newConcurrentMap();

    private final ExecutorService jobLisenter = Executors.newFixedThreadPool(5,
            new NamedThreadFactory("job-listener"));

    /**
     * 初始化开始,启动定时任务
     */
    public void init() {
        List<JobInfo> allJobInfo = jobInfoService.getAllJobInfo();
        for (JobInfo taskSchedule : allJobInfo) {
            // 如果任务是未启用的,不初始化
            if (taskSchedule.getInitiateMode() != AVAILABLE) {
                continue;
            }
            initJobScheduler(taskSchedule);
        }
        // 初始化任务管理器,并开始执行任务
        initStartAllJobScheduler();

        // 查看任务的健康状态,处理心跳
        logger.info("开启监控线程...");
        jobWatcher.init();
    }

    /**
     * 初始化一个定时任务
     */
    private JobScheduler initJobScheduler(final JobInfo taskSchedule) {
        JobScheduler jobScheduler = new JobScheduler();
        // 设置调度器基本参数
        boolean isIllegal = setJobSchedulerParameter(jobScheduler, taskSchedule);
        if (!isIllegal) {
            return null;
        }

        // 如果参数合法,放人容器管理
        jobSchedulerMaps.put(taskSchedule.getId(), jobScheduler);
        // 添加监听器,设置线程名,如果线程紧张可以改用线程池执行
        jobScheduler.addListener(new ScheduleListener(taskSchedule), jobLisenter);

        return jobScheduler;
    }

    /**
     * 设置调度器的执行参数,并检查参数合法性,检查是否指定改主机执行.
     *
     * @return true-合法,false-不合法
     */
    private boolean setJobSchedulerParameter(JobScheduler jobScheduler, JobInfo taskSchedule) {
        // 检查是否指定在本机上执行
        if (!checkHostName(taskSchedule.getExecuteHost())) {
            logger.error("任务不属于本机执行.执行任务主机:{}", taskSchedule.getExecuteHost());
            return false;
        }

        // 初始化jobScheduler必要参数
        return jobScheduler.init(taskSchedule);
    }

    /**
     * 检查本地hostname是否和数据库中保存的hostname一致
     */
    private boolean checkHostName(String dbHostName) {
        if (StringUtils.isBlank(dbHostName)) {
            logger.error("没有设置执行主机,任务无法执行.");
            return false;
        }

        String hostName = Utils.getHostName();
        logger.info("本机名:{},DB中主机名:{}", hostName, dbHostName);
        return StringUtils.equals(dbHostName.trim(), hostName);
    }

    /**
     * 初始化启动所有定时任务
     */
    private void initStartAllJobScheduler() {
        if (jobSchedulerMaps.isEmpty()) {
            logger.info("没有定时任务可开启");
        }

        for (JobScheduler jobScheduler : jobSchedulerMaps.values()) {
            jobScheduler.startAsync().awaitRunning();
        }
    }

    /**
     * 启动一个已经停止的任务 更新initiateMode=1启用
     *
     * @param id 存储于数据库的任务id
     */
    public void startSchedule(long id) {
        Preconditions.checkArgument(id > 0, "id illegal.");
        JobScheduler jobScheduler = jobSchedulerMaps.get(id);
        if (jobScheduler != null && jobScheduler.isRunning()) {
            logger.info("任务已经在执行,id:{}", id);
            return;
        }

        // 移除JobScheduler
        jobSchedulerMaps.remove(id);
        jobInfoService.modifyInitiateMode(id, AVAILABLE);
        // 从数据库中获取并启动
        getAndStartNewJobScheduler(id);
    }

    /**
     * 重启任务,将任务关闭,然后重新加载
     *
     */
    public void restartSchedule(long id) {
        Preconditions.checkArgument(id > 0, "id illegal.");
        logger.info("线程:{},重启任务{}定时任务 ", Thread.currentThread().getName(), id);
        JobScheduler jobScheduler = jobSchedulerMaps.get(id);
        if (null == jobScheduler) {
            logger.error("未获取到该任务,id:{}", id);
            return;
        }
        logger.info("重启的定时任务,当前执行状态:{}", jobScheduler.state());

        // 关闭任务
        try {
            jobScheduler.stopAsync().awaitTerminated();
            jobSchedulerMaps.remove(id);
        } catch (IllegalStateException e) {
            logger.error("重启定时任务失败,id:{},JobScheduler:{}", id, jobScheduler, e);
        }

        getAndStartNewJobScheduler(id);
    }

    /**
     * 冲数据库中获取,并启动一个新的定时任务
     *
     */
    private void getAndStartNewJobScheduler(long id) {
        // 获取定时任务信息
        JobInfo taskScheduleById;
        try {
            taskScheduleById = jobInfoService.getJobInfoById(id);
        } catch (RuntimeException e) {
            logger.error("从数据库获取定时任务失败,id:{}", id, e);
            return;
        }
        // 如果,设置为不启用,则不启动任务
        if (taskScheduleById.getInitiateMode() == DISABLE) {
            logger.info("该任务为未启用状态,不启动改任务:{}", taskScheduleById.toString());
            return;
        }
        // 初始化任务,并加入到Map容器中
        JobScheduler restartJobScheduler = initJobScheduler(taskScheduleById);
        if (null == restartJobScheduler) {
            logger.error("初始化任务({})调度器失败.", taskScheduleById.getJobName());
            return;
        }

        // 开启线程执行
        try {
            restartJobScheduler.startAsync().awaitRunning();
        } catch (IllegalStateException e) {
            logger.error("启动新建任务失败,JobScheduler:{}", restartJobScheduler.toString(), e);
            return;
        }
        logger.info("任务启动成功,State:{}.", restartJobScheduler.state());
    }

    /**
     * 关闭任务 更新initiateMode=1,未启用
     */
    public void stopSchedule(long id) {
        Preconditions.checkArgument(id > 0, "id illegal.");
        JobScheduler jobScheduler = jobSchedulerMaps.get(id);
        // 将可用状态置为,不可用
        jobInfoService.modifyInitiateMode(id, DISABLE);
        // 从map中没有获取到任务,说明该任务没有执行
        if (null == jobScheduler) {
            logger.info("任务不处于执行状态,id:{}", id);
            return;
        }

        try {
            jobScheduler.stopAsync().awaitTerminated();
        } catch (IllegalStateException e) {
            logger.error("停止任务失败,id:{},JobScheduler:{}", id, jobScheduler.toString());
            return;
        }
        logger.info("任务关闭成功,State:{}", jobScheduler.state());
        // 更新任务状态为未执行
        jobInfoService.modifyExecuteStatus(id, UN_EXECUTE);
        jobSchedulerMaps.remove(id);
        // tip:更新数据库状态在JobScheduler中的shutDown中执行
    }

    /**
     * 更新定时任务
     *
     * @param id 与数据库对应的调度任务的主键id
     */
    public void updateSchedule(long id) {
        Preconditions.checkArgument(id > 0, "id illegal.");
        // 首先判断是否是正在执行,是:关闭,剔除.
        JobScheduler jobScheduler = jobSchedulerMaps.get(id);
        if (null != jobScheduler) {
            try {
                jobScheduler.stopAsync().awaitTerminated();
            } catch (IllegalStateException e) {
                logger.error("停止任务失败,id:{},JobScheduler:{}", id, jobScheduler.toString());
                return;
            }
            // map容器移除,并重置为启用状态
            jobSchedulerMaps.remove(id);
        }

        getAndStartNewJobScheduler(id);
    }

    public void stopAllScheduler() {
        for (JobScheduler jobScheduler : jobSchedulerMaps.values()) {
            try {
                jobScheduler.stopAsync().awaitTerminated();
                jobSchedulerMaps.remove(jobScheduler.getId());
                jobInfoService.modifyExecuteStatus(jobScheduler.getId(), UN_EXECUTE);
            } catch (IllegalStateException e) {
                logger.error("定时任务关闭失败,JobScheduler:{}", jobScheduler.toString());
            }
        }
    }

    /**
     * 当容器关闭时,将状态改为"未执行"
     */
    public void shutdown() {
        for (JobScheduler jobScheduler : jobSchedulerMaps.values()) {
            try {
                jobInfoService.modifyExecuteStatus(jobScheduler.getId(), UN_EXECUTE);
            } catch (RuntimeException e) {
                logger.error("更新任务状态失败,JobScheduler:{}", jobScheduler.toString());
            }
        }
    }

    public Map<Long, JobScheduler> getJobSchedulerMaps() {
        ImmutableMap<Long, JobScheduler> immutableJobMap = ImmutableMap.copyOf(jobSchedulerMaps);
        return immutableJobMap;
    }

    /**
     * 监听定时任务执行状态,并实时更新数据库
     */
    private static class ScheduleListener extends Service.Listener {
        private JobInfo taskSchedule;
        JobInfoService jobInfoService;

        ScheduleListener(JobInfo taskSchedule) {
            super();
            this.taskSchedule = taskSchedule;
            init();
        }

        private void init() {
            jobInfoService = SpringContextHolder.getBean(JobInfoService.class);
        }

        /**
         * 更新executeStatus=0,执行失败
         */
        @Override
        public void failed(Service.State from, Throwable failure) {
            logger.info("Listener: {}任务执行失败 State:{},exception:{}", taskSchedule.getJobName(), from.toString(),
                    failure);
            jobInfoService.modifyExecuteStatus(taskSchedule.getId(), FAILED_EXECUTE);
        }

        /**
         * 更新executeStatus=1,运行中
         */
        @Override
        public void running() {
            logger.info("Listener:任务开始运行,线程:{}", Thread.currentThread().getName());
            jobInfoService.modifyExecuteStatus(taskSchedule.getId(), IN_EXECUTE);
        }

        @Override
        public void starting() {
            logger.info("Listener:{}任务启动,当前监听定时任务线程:{}", taskSchedule.getJobName(), Thread.currentThread().getName());
        }

        /**
         * 执行状态executeStatus=-1(未执行)
         * 
         * @param from 只可能有两种状态:RUNNING,STARTING
         */
        @Override
        public void stopping(Service.State from) {
            logger.info("Listener: 任务开始停止,线程:{}", Thread.currentThread().getName());
            jobInfoService.modifyExecuteStatus(taskSchedule.getId(), UN_EXECUTE);
        }

        /**
         * 执行状态executeStatus=2(成功)
         *
         * @param from 转换成TERMINATED状态,之前的状态
         */
        @Override
        public void terminated(Service.State from) {
            logger.info("Listener:任务终止. State:{}", from.toString());
        }
    }

    /**
     * 提供命名的线程工厂
     */
    public static class NamedThreadFactory implements ThreadFactory {
        private final String threadName;

        public NamedThreadFactory(String threadName) {
            this.threadName = threadName + "-thread-" + JobScheduler.threadCount.incrementAndGet();
        }

        public Thread newThread(Runnable r) {
            return new Thread(r, threadName);
        }
    }
}
