package com.family.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.family.storage.common.GlobalExceptionHandler;
import com.family.storage.item.Item;
import com.family.storage.item.dto.ItemRequest;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

/**
 * 全局异常映射测试（code-review M4 / M2 / L4）：
 * 输入类错误 → 400；数据冲突 → 409；乐观锁冲突 → 409 STOCK_CONFLICT；
 * price 校验失败 → 400 INVALID_PRICE。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void optimisticLockConflictMapsTo409Conflict() {
        ResponseEntity<Map<String, String>> resp =
                handler.handleOptimisticLock(new ObjectOptimisticLockingFailureException(Item.class, 1L));
        assertEquals(HttpStatus.CONFLICT.value(), resp.getStatusCode().value());
        assertEquals("STOCK_CONFLICT", resp.getBody().get("code"));
    }

    @Test
    void deadlockLockFailureMapsTo409ConflictNot500() {
        // NEW-D1：并发出入库 MySQL 死锁 → CannotAcquireLockException（PessimisticLockingFailureException 子类）
        // 必须映射 409 STOCK_CONFLICT 可重试，而不是落入 500 INTERNAL_ERROR。
        com.family.storage.common.GlobalExceptionHandler h = new com.family.storage.common.GlobalExceptionHandler();
        var resp = h.handleLockFailure(new org.springframework.dao.CannotAcquireLockException("deadlock"));
        assertEquals(HttpStatus.CONFLICT.value(), resp.getStatusCode().value());
        assertEquals("STOCK_CONFLICT", resp.getBody().get("code"));
    }

    @Test
    void malformedJsonMapsTo400BadRequest() {
        ResponseEntity<Map<String, String>> resp =
                handler.handleBadRequest(new HttpMessageNotReadableException("malformed body"));
        assertEquals(HttpStatus.BAD_REQUEST.value(), resp.getStatusCode().value());
        assertEquals("BAD_REQUEST", resp.getBody().get("code"));
    }

    @Test
    void dataIntegrityViolationMapsTo409Conflict() {
        ResponseEntity<Map<String, String>> resp =
                handler.handleDataIntegrity(new DataIntegrityViolationException("duplicate key"));
        assertEquals(HttpStatus.CONFLICT.value(), resp.getStatusCode().value());
        assertEquals("DATA_CONFLICT", resp.getBody().get("code"));
    }

    @Test
    void priceValidationMapsToInvalidPrice() throws Exception {
        MethodParameter parameter = new MethodParameter(
                ItemServiceUpdateMethodHolder.class.getMethod("update", ItemRequest.class), 0);
        BeanPropertyBindingResult binding =
                new BeanPropertyBindingResult(new ItemRequest("牛奶", 1L, 1L, 1, "盒",
                        new BigDecimal("-1"), null, null, null, null, null, null), "itemRequest");
        binding.addError(new FieldError("itemRequest", "purchasePrice",
                new BigDecimal("-1"), false, null, null, "价格不能为负数"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, binding);

        ResponseEntity<Map<String, String>> resp = handler.handleValidation(ex);
        assertEquals(HttpStatus.BAD_REQUEST.value(), resp.getStatusCode().value());
        assertEquals("INVALID_PRICE", resp.getBody().get("code"));
    }

    /** 仅用于为 MethodParameter 提供参数位（与业务无关的桩方法）。 */
    static final class ItemServiceUpdateMethodHolder {
        @SuppressWarnings("unused")
        public void update(ItemRequest req) {
        }
    }
}