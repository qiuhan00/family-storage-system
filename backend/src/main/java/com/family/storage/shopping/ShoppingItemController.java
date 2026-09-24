package com.family.storage.shopping;

import com.family.storage.common.ApiException;
import com.family.storage.shopping.dto.ShoppingItemResponse;
import com.family.storage.shopping.dto.ShoppingRequest;
import com.family.storage.shopping.dto.ShoppingStatusRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 购物清单端点（spec §5.5，/api/shopping）。需登录（SecurityConfig 默认拦截）；
 * create 取当前登录用户身份写入 created_by；PATCH /{id}/status 流转状态。
 */
@RestController
@RequestMapping("/api/shopping")
@RequiredArgsConstructor
public class ShoppingItemController {
    private final ShoppingItemService shoppingItemService;

    @GetMapping
    public List<ShoppingItemResponse> list(@RequestParam(required = false) String status) {
        return shoppingItemService.list(parseStatus(status));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShoppingItemResponse create(@RequestBody @Valid ShoppingRequest req,
                                       @AuthenticationPrincipal Long userId) {
        return shoppingItemService.create(req, userId);
    }

    @PutMapping("/{id}")
    public ShoppingItemResponse update(@PathVariable Long id, @RequestBody @Valid ShoppingRequest req) {
        return shoppingItemService.update(id, req);
    }

    @PatchMapping("/{id}/status")
    public ShoppingItemResponse setStatus(@PathVariable Long id,
                                          @RequestBody @Valid ShoppingStatusRequest req) {
        return shoppingItemService.setStatus(id, parseStatus(req.status()));
    }

    private ShoppingStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ShoppingStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "无效的购物清单状态: " + status);
        }
    }
}