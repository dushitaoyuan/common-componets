package com.taoyuanx.common.audit.log.common;

/**
 * 日志异常
 *
 *
 * @author dushitaoyuan
 * @date 2026/4/1 09:45
 */
public class LogException  extends RuntimeException{
    public LogException() {
    }

    public LogException(String message) {
        super(message);
    }

    public LogException(Throwable cause) {
        super(cause);
    }

    public LogException(String message, Throwable cause) {
        super(message, cause);
    }
}
