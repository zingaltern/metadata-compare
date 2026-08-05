package com.dqc.compare.api.rest;

/**
 * 任务正在运行冲突异常：同一任务在前一次比对尚未结束时再次被触发时抛出。
 * 由 {@link RestExceptionHandler} 映射为 409 Conflict。
 */
public class TaskRunningException extends RuntimeException {

    public TaskRunningException(String message) {
        super(message);
    }
}
