package com.dqc.compare.api.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 全局异常处理：
 * <ul>
 *   <li>TaskRunningException（任务运行中冲突）→ 409 Conflict，返回友好文案</li>
 *   <li>IllegalArgumentException（业务参数错误）→ 400</li>
 *   <li>其余异常 → 500，但<b>不向客户端泄露内部异常信息</b>，仅返回通用提示，完整堆栈服务端记录</li>
 * </ul>
 */
@RestControllerAdvice
public class RestExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RestExceptionHandler.class);

    @ExceptionHandler(TaskRunningException.class)
    public ResponseEntity<Map<String, Object>> handleRunning(TaskRunningException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegal(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception e) {
        // 不把内部异常信息（可能含路径/SQL）暴露给客户端
        log.error("未预期异常：", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "系统内部错误，请稍后重试或联系管理员"));
    }
}
