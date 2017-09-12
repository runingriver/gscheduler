package org.gscheduler.service.job;

import java.util.concurrent.TimeUnit;

import org.gscheduler.service.executor.JobProcess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;
import org.springframework.stereotype.Component;

/**
 * 关于使用任务调度的一个示例
 */
@Component
public class DemoJob implements JobProcess {
    private static final Logger logger = LoggerFactory.getLogger(DemoJob.class);

    public void execute() {
        logger.info("---enter DemoJob---");
        Stopwatch stopWatch = Stopwatch.createStarted();

        try {
            TimeUnit.SECONDS.sleep(10);
        } catch (InterruptedException e) {
            logger.error("exception occur.", e);
        }

        logger.info("do something you like!");

        stopWatch.stop();
        logger.info("demo job cost time:{}", stopWatch);
        logger.info("---leave DemoJob---");

    }
}
