package org.gscheduler.service.executor;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Service;
import org.apache.commons.lang3.StringUtils;
import org.gscheduler.commons.SpringContextHolder;
import org.gscheduler.entity.JobInfo;
import org.gscheduler.service.task.JobInfoService;
import org.gscheduler.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * 作业管理类,任务启动,关闭,停止.
 */
@Component
public class JobManager {
    private static final Logger logger = LoggerFactory.getLogger(JobManager.class);
    /**
     * 任务状态:启用状态
     */
    public static final short AVAILABLE = 1;
    /**
     * 任务状态:禁用状态
     */
    public static final short DISABLE = 0;
    /**
     * 任务执行状态:未执行
     */
    public static final short UN_EXECUTE = -1;
    /**
     * 任务执行状态:执行失败
     */
    public static final short FAILED_EXECUTE = 0;
    /**
     * 任务执行状态:运行中
     */
    public static final short IN_EXECUTE = 1;
    /**
     * 任务执行状态:执行成功
     */
    public static final short SUCCESS_EXECUTE = 2;

    /**
     * 是否使用zk监听任务
     */
    public boolean isUsedZKListener = true;

    /**
     * STOP_OR_START:主机改变,一台主机关闭,一台主机启动
     * KILL:kill掉任务
     */
    public enum JobOperator {
        STOP, START, RESTART, STOP_OR_START, KILL, NONE
    }


    @Resource
    JobInfoService jobInfoService;

    @Resource
    JobWatcher jobWatcher;

    @Resource
    JobListener jobListener;

    @Value("${zookeeper.use.task.listener}")
    String isUseZookeeper;

    /**
     * 多个线程会同时操作该Map,只存放正在运行的JobScheduler,key-id,value-obj
     */
    private final Map<Long, JobScheduler> jobSchedulerMaps = new ConcurrentHashMap<>(50);

    //监听job线程执行状态的线程池
    private ExecutorService listenerService;

    /**
     * 初始化开始,启动定时任务,系统启动执行
     */
    public void init() {
        isUsedZKListener = Boolean.parseBoolean(isUseZookeeper);
        logger.info("do not use zookeeper keep consistence on startup,isUsedZKListener:{},properties value:{}",
                isUsedZKListener, isUseZookeeper);
        //执行任务监听
        listenerService = Executors.newSingleThreadExecutor(new NamedThreadFactory("job-listener"));

        List<JobInfo> allJobInfo = jobInfoService.getAllJobInfo();
        for (JobInfo jobInfo : allJobInfo) {
            // 如果任务是未启用的,不初始化
            if (jobInfo.getInitiateMode() != AVAILABLE) {
                continue;
            }
            if (!checkHostName(jobInfo.getExecuteHost())) {
                logger.error("任务不属于本机执行.执行任务主机:{}", jobInfo.getExecuteHost());
                continue;
            }
            //更新failExecuteHost
            if (StringUtils.equals(jobInfo.getFailExecuteHost(), Utils.getHostName())) {
                jobInfoService.modifyFailExecuteHost(jobInfo.getId(), "");
            }

            if (StringUtils.isNotBlank(jobInfo.getParentName())) {
                continue;
            }

            JobScheduler jobScheduler = initJobScheduler(jobInfo);

            //启动不是依赖调度的任务,依赖调度的任务不启动
            jobScheduler.startAsync().awaitRunning();
        }

        // 查看任务的健康状态,处理心跳
        logger.info("开启监控线程...");
        jobWatcher.init();
    }

    /**
     * 初始化一个定时任务
     */
    public JobScheduler initJobScheduler(final JobInfo jobInfo) {
        JobScheduler jobScheduler = new JobScheduler();
        // 设置调度器基本参数
        if (!jobScheduler.init(jobInfo)) {
            logger.info("初始化{}失败.", jobInfo.getJobName());
            return null;
        }

        // 如果初始化成功,放入容器管理
        jobSchedulerMaps.put(jobInfo.getId(), jobScheduler);
        // 添加监听器
        jobScheduler.addListener(new ScheduleListener(jobInfo), listenerService);
        return jobScheduler;
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
        if (jobSchedulerMaps.containsKey(id)) {
            jobSchedulerMaps.remove(id);
        }
        // 从数据库中获取并启动
        getAndStartNewJobScheduler(id);
    }

    /**
     * 重启任务,将任务关闭,然后重新加载
     */
    public void restartSchedule(long id) {
        Preconditions.checkArgument(id > 0, "id illegal.");
        logger.info("线程:{},重启任务,id:{}", Thread.currentThread().getName(), id);
        JobScheduler jobScheduler = jobSchedulerMaps.get(id);
        if (null == jobScheduler) {
            logger.warn("该任务未启动,启动任务,id:{}", id);
            getAndStartNewJobScheduler(id);
            return;
        }

        logger.info("任务已启动,关闭并重启任务,当前执行状态:{}", jobScheduler.state());
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
     * 关闭任务 更新initiateMode=0,未启用
     */
    public boolean stopSchedule(long id) {
        Preconditions.checkArgument(id > 0, "id illegal.");
        JobScheduler jobScheduler = jobSchedulerMaps.get(id);
        // 从map中没有获取到任务,说明该任务没有执行
        if (null == jobScheduler) {
            logger.info("任务不处于执行状态,id:{}", id);
            return false;
        }

        try {
            jobScheduler.stopAsync().awaitTerminated();
        } catch (IllegalStateException e) {
            logger.error("停止任务失败,id:{},JobScheduler:{}", id, jobScheduler.toString());
            return false;
        }
        logger.info("任务关闭成功,State:{}", jobScheduler.state());
        // 更新任务状态为未执行
        jobInfoService.modifyExecuteStatus(id, UN_EXECUTE);
        jobSchedulerMaps.remove(id);
        // tip:更新数据库状态在JobScheduler中的shutDown中执行
        return true;
    }

    public void killSchedule(long id) {
        Preconditions.checkArgument(id > 0, "id illegal.");
        JobScheduler jobScheduler = jobSchedulerMaps.get(id);
        // 从map中没有获取到任务,说明该任务没有执行
        if (null == jobScheduler) {
            logger.info("任务不处于执行状态,id:{}", id);
            return;
        }

        jobSchedulerMaps.remove(id);
        try {
            jobScheduler.stopAsync().awaitTerminated();
        } catch (IllegalStateException e) {
            logger.error("停止任务失败,id:{},JobScheduler:{}", id, jobScheduler.toString());
        }
        logger.info("任务kill成功,State:{}", jobScheduler.state());
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
            jobScheduler.stopAsync().awaitTerminated();
            logger.info("{} stop,Job:{}", jobScheduler.serviceName(), jobScheduler.toString());
        }
    }

    public Map<Long, JobScheduler> getJobSchedulerMaps() {
        ImmutableMap<Long, JobScheduler> immutableJobMap = ImmutableMap.copyOf(jobSchedulerMaps);
        return immutableJobMap;
    }

    public boolean getIsUsedZKListener() {
        return isUsedZKListener;
    }

    public void setIsUsedZKListener(boolean isUsed) {
        isUsedZKListener = isUsed;
        //关闭,启动
        if (isUsedZKListener && !jobListener.isInListening()) {
            jobListener.init();
        }
        if (!isUsedZKListener && jobListener.isInListening()) {
            jobListener.close();
        }
    }

    /**
     * 监听定时任务执行状态,并实时更新数据库
     */
    private static class ScheduleListener extends Service.Listener {
        private JobInfo jobInfo;
        JobInfoService jobInfoService;

        ScheduleListener(JobInfo jobInfo) {
            super();
            this.jobInfo = jobInfo;
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
            logger.info("Listener: {}任务执行失败 State:{},exception:{}", jobInfo.getJobName(), from.toString(),
                    failure);
            jobInfoService.modifyExecuteStatus(jobInfo.getId(), FAILED_EXECUTE);
        }

        /**
         * 更新executeStatus=1,运行中
         */
        @Override
        public void running() {
            logger.info("Listener:{} 开始运行,监听线程:{}", jobInfo.getJobName(), Thread.currentThread().getName());
            jobInfoService.modifyExecuteStatus(jobInfo.getId(), IN_EXECUTE);
        }

        @Override
        public void starting() {
            logger.info("Listener:{}任务启动,当前监听定时任务线程:{}", jobInfo.getJobName(), Thread.currentThread().getName());
        }

        /**
         * 执行状态executeStatus=-1(未执行)
         *
         * @param from 只可能有两种状态:RUNNING,STARTING
         */
        @Override
        public void stopping(Service.State from) {
            logger.info("Listener: {}开始停止,线程:{}", jobInfo.getJobName(), Thread.currentThread().getName());
            jobInfoService.modifyExecuteStatus(jobInfo.getId(), UN_EXECUTE);
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
            this.threadName = threadName + "-pool-thread-" + JobScheduler.threadCount.incrementAndGet();
        }

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, threadName);
        }
    }
}
