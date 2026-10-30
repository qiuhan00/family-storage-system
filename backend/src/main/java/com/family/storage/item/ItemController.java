package com.family.storage.item;

import com.family.storage.common.PageResponse;
import com.family.storage.item.dto.ItemRequest;
import com.family.storage.item.dto.ItemResponse;
import com.family.storage.item.dto.StockChangeResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 物品端点（spec §5.4，/api/items）。需登录（SecurityConfig 默认拦截）；
 * create 取当前登录用户身份写入 created_by；出入库与变更历史同域。
 */
@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
public class ItemController {
    private final ItemService itemService;

    @GetMapping
    public PageResponse<ItemResponse> search(@RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) Long categoryId,
                                             @RequestParam(required = false) Long locationId,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(required = false, defaultValue = "false") boolean expiring,
                                             Pageable pageable) {
        return itemService.search(keyword, categoryId, locationId, status, expiring, pageable);
    }

    @GetMapping("/expiring")
    public List<ItemResponse> expiringList(@RequestParam(required = false) Integer days) {
        return itemService.expiringList(days);
    }

    @GetMapping("/low-stock")
    public List<ItemResponse> lowStock() {
        return itemService.lowStockList();
    }

    @GetMapping("/{id}")
    public ItemResponse get(@PathVariable Long id) {
        return itemService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ItemResponse create(@RequestBody @Valid ItemRequest req,
                               @AuthenticationPrincipal Long userId) {
        return itemService.create(req, userId);
    }

    @PutMapping("/{id}")
    public ItemResponse update(@PathVariable Long id, @RequestBody @Valid ItemRequest req) {
        return itemService.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        itemService.delete(id);
    }

    @PostMapping("/{id}/stock")
    public StockChangeResponse adjustStock(@PathVariable Long id,
                                           @RequestBody @Valid StockRequest req,
                                           @AuthenticationPrincipal Long userId) {
        return itemService.adjustStock(id, req, userId);
    }

    @GetMapping("/{id}/stock-changes")
    public List<StockChangeResponse> stockHistory(@PathVariable Long id) {
        return itemService.stockHistory(id);
    }
}