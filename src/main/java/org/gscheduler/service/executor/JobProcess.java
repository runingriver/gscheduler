package org.gscheduler.service.executor;

/**
 * 定时任务实现接口,任何定时任务必须实现此接口
 */
public interface JobProcess {
    /**
     * 执行任务
     */
    void execute();
}
