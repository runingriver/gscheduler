package org.gscheduler.commons;

import org.gscheduler.service.executor.JobManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * spring关闭时等待job执行完毕在关闭
 */
@Component
public class ShutdownEvent implements ApplicationListener<ContextClosedEvent> {
    private static final Logger logger = LoggerFactory.getLogger(ShutdownEvent.class);

    private volatile boolean isShutDown = false;

    @Resource
    JobManager jobManager;

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        if (isShutDown) {
            return;
        }
        logger.info("任务调度,服务开始关闭.");
        jobManager.shutdown();
        logger.info("任务调度,服务关闭完成.");
        isShutDown = true;
    }
}
