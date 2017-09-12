package org.gscheduler.service.job;

import org.gscheduler.service.executor.JobProcess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 测试任务
 */
@Component
public class TestJob implements JobProcess {
    private static final Logger logger = LoggerFactory.getLogger(TestJob.class);

    @Override
    public void execute() {
        logger.info("enter jobTester.");
        for (int i = 0; i < 10; i++) {
            logger.info("JobTester do work {} times.", i);
            sleep(2);
        }
        logger.info("leave jobTester.");
    }

    private void sleep(long seconds) {
        try {
            TimeUnit.SECONDS.sleep(seconds);
        } catch (InterruptedException e) {
            logger.error("{} Thread sleep Exception.", Thread.currentThread().getName(), e);
        }
    }
}
