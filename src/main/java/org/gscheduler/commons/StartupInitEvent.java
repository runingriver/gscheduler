package org.gscheduler.commons;

import org.gscheduler.service.jober.JobManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 应用(tomcat)启动,执行的一些操作.
 * 开启定时任务.
 */
@Component
public class StartupInitEvent implements ApplicationListener<ContextRefreshedEvent> {
    private static final Logger logger = LoggerFactory.getLogger(StartupInitEvent.class);

    private volatile boolean isInitialed = false;

    @Resource
    JobManager jobManager;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        //有可能多次被调用
        if (isInitialed) {
            return;
        }
        logger.info("服务开始启动...");
        jobManager.init();
        isInitialed = true;
    }
}
