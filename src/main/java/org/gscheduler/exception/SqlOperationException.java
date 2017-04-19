package org.gscheduler.exception;

/**
 * 数据库操作异常,必须带参数
 */
public class SqlOperationException extends CustomRuntimeException {

    public SqlOperationException(String message) {
        super(message);
    }

    public SqlOperationException(String message, Object argument) {
        super(message,argument);
    }

    public SqlOperationException(String message, Object argument1, Object argument2) {
        super(message,argument1,argument2);
    }

    public SqlOperationException(String message, Object argument1, Object argument2, Throwable cause) {
        super(message,argument1,argument2,cause);
    }

    public SqlOperationException(String message, Object argument, Throwable cause) {
        super(message,argument,cause);
    }

    public SqlOperationException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
