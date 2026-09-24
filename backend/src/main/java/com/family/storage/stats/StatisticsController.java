package com.family.storage.stats;

import com.family.storage.stats.dto.ItemCountByDimension;
import com.family.storage.stats.dto.SummaryResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统计端点（spec §5.8，/api/statistics）：summary 总览、by-category /
 * by-location 维度聚合。需登录（SecurityConfig 默认拦截）。
 */
@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
public class StatisticsController {
    private final StatisticsService statisticsService;

    @GetMapping("/summary")
    public SummaryResponse summary() {
        return statisticsService.summary();
    }

    @GetMapping("/by-category")
    public List<ItemCountByDimension> byCategory() {
        return statisticsService.byCategory();
    }

    @GetMapping("/by-location")
    public List<ItemCountByDimension> byLocation() {
        return statisticsService.byLocation();
    }
}