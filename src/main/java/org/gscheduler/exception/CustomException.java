package org.gscheduler.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 受检异常,程序内部能处理,明确异常信息(少用)
 */
public class CustomException extends Exception {
    /* 统一定义异常日志 */
    protected Logger getLogger() {
        return LoggerFactory.getLogger(getClass());
    }

    public CustomException() {
        super();
        getLogger().error("Exception", this);
    }

    public CustomException(Throwable cause) {
        super(cause);
        getLogger().error("Exception:", cause);
    }

    public CustomException(String message) {
        super(message);
        getLogger().error("Exception:{}", message);
    }

    public CustomException(String message, Throwable cause) {
        super(message, cause);
        getLogger().error("Exception:{}", message, cause);
    }

    public CustomException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
