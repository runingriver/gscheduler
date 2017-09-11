package org.gscheduler.service.jober;

import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.StringUtils;
import org.gscheduler.commons.SpringContextHolder;
import org.gscheduler.entity.JobInfo;
import org.gscheduler.exception.SqlOperationException;
import org.gscheduler.service.task.JobInfoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.AbstractScheduledService;

/**
 * 作业调度器,定时任务的执行类,真正执行任务是一个 Executors.newSingleThreadScheduledExecutor线程.
 */
class JobScheduler extends AbstractScheduledService {
    private static final Logger logger = LoggerFactory.getLogger(JobScheduler.class);
    public static AtomicInteger threadCount = new AtomicInteger(0);
    private long id;

    // 定时任务的信息类
    private JobInfo jobInfo;

    private JobTrigger jobTrigger;

    /** 定时任务实现接口,任何定时任务必须实现此接口 */
    private JobProcess jobProcess;

    // 操作定时任务信息的类
    private JobInfoService jobInfoService;

    /**
     * 用于初始化与任务调度相关的事情 注入类名,使用 spring获取类的实例,类名应该格式为变量命名格式(首字母小写,eg:arrivedMonitor)
     *
     */
    public boolean init(JobInfo jobInfo) {
        if (null == jobInfo || !checkJobParameter(jobInfo)) {
            return false;
        }

        this.jobInfo = jobInfo;
        this.id = jobInfo.getId();
        logger.info("任务类:{},线程:{}", jobInfo.getTaskClass(), Thread.currentThread().getName());
        try {
            jobTrigger = new JobTrigger(jobInfo.getCrontab());
            jobProcess = SpringContextHolder.getBean(jobInfo.getTaskClass().trim());
            jobInfoService = SpringContextHolder.getBean(JobInfoService.class);
        } catch (Exception e) {
            logger.error("spring获取bean类实例失败", e);
            logger.error("丢弃该任务,类名:{}", jobInfo.getTaskClass());
            return false;
        }
        return true;
    }

    // 检查传进来的TaskSchedule对象是否合法
    private boolean checkJobParameter(JobInfo jobInfo) {
        if (jobInfo.getId() < 1) {
            logger.error("任务初始化失败,未获取到TaskSchedule id.");
            return false;
        }
        if (StringUtils.isBlank(jobInfo.getTaskClass())) {
            logger.error("任务初始化失败,未获取到TaskClass.");
            return false;
        }
        if (StringUtils.isBlank(jobInfo.getCrontab())) {
            logger.error("任务初始化失败,未获取到Crontab.");
            return false;
        }

        if (JobTrigger.checkExpression(jobInfo.getCrontab())) {
            logger.error("任务初始化失败,时间表达式错误:{}", jobInfo.getCrontab());
            return false;
        }

        return true;
    }

    @Override
    protected String serviceName() {
        return jobInfo.getTaskClass() + "thread-" + threadCount.incrementAndGet();
    }

    /**
     * 定时任务停止时,调用该方法,更新数据库initial_mode=0.
     *
     * 该方法只在stopAsync方法被调用的时候执行
     *
     * @throws Exception
     */
    @Override
    protected void shutDown() throws Exception {
        logger.info("任务关闭,更新数据库");
    }

    /**
     * 定时任务启动时,调用该方法,
     *
     * @throws Exception
     */
    @Override
    protected void startUp() throws Exception {
        logger.info("调度器线程:{},开始执行", Thread.currentThread().getName());
        jobInfoService.modifyInitiateMode(id, JobManager.AVAILABLE);
    }

    /**
     * 如果该方法抛出异常,则定时任务会停止. 该方法会一直循环调度 更新每一次执行时长,上一次执行时间,下一次执行时间
     *
     * @throws Exception
     */
    @Override
    protected void runOneIteration() throws Exception {
        logger.info("开始执行任务,执行任务线程:{}", Thread.currentThread().getName());
        boolean succeed = false;
        try {
            jobInfoService.modifyExecuteTime(id, "running");
            // 下一次执行时间
            jobInfoService.modifyLastAndNextExecuteTime(id, new Date(), jobTrigger.getNextExecutionDate());
            Stopwatch stopwatch = Stopwatch.createStarted();

            jobProcess.execute();

            stopwatch.stop();
            String executeTime = stopwatch.toString();
            logger.info("任务执行时长:{}", executeTime);
            jobInfoService.modifyExecuteTime(id, executeTime);
            // 执行到此处代表上面执行正常
            succeed = true;
        } catch (Throwable e) {
            logger.error("任务执行中发生异常.", e);
        }

        if (!succeed) {
            try {
                jobInfoService.modifyExecuteTime(id, "failed");
            } catch (SqlOperationException e) {
                logger.error("update execute time exception", e);
            }
        }
    }

    /**
     * 定时任务调度策略设置类 固定延迟
     */
    @Override
    protected Scheduler scheduler() {
        logger.info("Scheduler 线程:{},cron:{}", Thread.currentThread().getName(), jobInfo.getCrontab());
        return jobTrigger.getScheduler();
        // return Scheduler.newFixedRateSchedule(delay, period, TimeUnit.MINUTES);
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this.getClass()).addValue(jobInfo).add("State:", this.state())
                .toString();
    }
}
