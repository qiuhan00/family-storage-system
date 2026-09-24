package com.family.storage.common;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;

/**
 * 全局异常处理：统一输出 {@code {code, message}} 错误体。
 * code-review M4：输入类错误（畸形 JSON、参数类型不匹配、缺参、数据冲突）不再吞成 500；
 * 500 兜底必须记 error 日志否则生产排障无痕。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, String>> handleApi(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getCode(), "message", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException e) {
        // 物品价格字段边界（L4 顺手项）统一映射为业务码 INVALID_PRICE，其余保持 VALIDATION_ERROR
        String priceMessage = e.getBindingResult().getFieldErrors().stream()
                .filter(f -> "purchasePrice".equals(f.getField()))
                .map(f -> "purchasePrice: " + f.getDefaultMessage())
                .findFirst().orElse(null);
        if (priceMessage != null) {
            return ResponseEntity.badRequest().body(Map.of("code", "INVALID_PRICE", "message", priceMessage));
        }
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage()).findFirst().orElse("参数校验失败");
        return ResponseEntity.badRequest().body(Map.of("code", "VALIDATION_ERROR", "message", msg));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<Map<String, String>> handleBadRequest(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("code", "BAD_REQUEST", "message", "请求参数格式错误"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrity(DataIntegrityViolationException e) {
        // 唯一约束/外键/非空冲突（并发重名、越界写入等）→ 409，由业务层优先抛业务码；
        // 若业务层已先抛 ApiException，走 handleApi，不会到达这里。
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("code", "DATA_CONFLICT", "message", "数据冲突，请检查唯一性或关联数据"));
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class,
            jakarta.persistence.OptimisticLockException.class})
    public ResponseEntity<Map<String, String>> handleOptimisticLock(Exception e) {
        // M2：出入库/更新并发冲突 → 409，提示客户端重试
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("code", "STOCK_CONFLICT", "message", "物品数据已被其他操作修改，请重试"));
    }

    @ExceptionHandler(org.springframework.dao.PessimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> handleLockFailure(org.springframework.dao.PessimisticLockingFailureException e) {
        // NEW-D1：并发下 MySQL 死锁/拿锁失败（CannotAcquireLockException 为其子类）同样映射 409 可重试，
        // 避免落 500；死锁是并发下正常可重试场景，不需记 error（data 未损坏）。
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("code", "STOCK_CONFLICT", "message", "系统繁忙，请稍后重试"));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Map<String, String>> handleMultipart(MultipartException e) {
        // 包含 MaxUploadSizeExceededException 及 Tomcat 容器层抛出的其他 multipart 异常
        return ResponseEntity.badRequest().body(Map.of("code", "FILE_TOO_LARGE", "message", "文件大小不能超过 5MB"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleOther(Exception e) {
        // M4：500 类异常必须留痕，否则生产排障无痕
        log.error("Unhandled exception while processing request", e);
        return ResponseEntity.status(500).body(Map.of("code", "INTERNAL_ERROR", "message", "服务器内部错误"));
    }
}