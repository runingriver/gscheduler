package org.gscheduler.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 非受检异常,明确异常类型,将异常信息包装,提示用户
 */
public class CustomRuntimeException extends RuntimeException {
    /* 统一定义异常日志 */
    protected Logger getLogger() {
        return LoggerFactory.getLogger(getClass());
    }

    public CustomRuntimeException() {
        super();
        getLogger().error("Exception", this);
    }

    public CustomRuntimeException(Throwable cause) {
        super(cause);
        getLogger().error("Exception:", cause);
    }

    public CustomRuntimeException(String message) {
        super(message);
        getLogger().error("Exception:{}", message);
    }

    /**
     * 添加一个argument参数,用于传递,参数相关信息
     */
    public CustomRuntimeException(String message, Object argument) {
        super(message);
        getLogger().error("Exception:{},argument:{}", message, argument);
    }

    public CustomRuntimeException(String message, Object argument1, Object argument2) {
        super(message);
        getLogger().error("Exception:{},argument1:{},argument2:{}", message, argument1, argument2);
    }

    public CustomRuntimeException(String message, Object argument1, Object argument2, Throwable cause) {
        super(message);
        getLogger().error("Exception:{},argument1:{},argument2:{}", message, argument1, argument2,cause);
    }

    public CustomRuntimeException(String message, Throwable cause) {
        super(message, cause);
        getLogger().error("Exception:{}", message, cause);
    }

    public CustomRuntimeException(String message, Object argument, Throwable cause) {
        super(message, cause);
        getLogger().error("Exception:{},argument:{}", message, argument, cause);
    }

    // 需升级到1.7,enableSuppression/writableStackTrace都设为false不追溯异常栈和打印异常信息,提高性能.
    public CustomRuntimeException(String message, Throwable cause, boolean enableSuppression,
            boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
