package com.family.storage.location;

import com.family.storage.location.dto.LocationRequest;
import com.family.storage.location.dto.LocationResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 位置端点（spec §5.3，/api/locations）。GET 返回树形结构。
 * 需登录（SecurityConfig 默认拦截）。
 */
@RestController
@RequestMapping("/api/locations")
@RequiredArgsConstructor
public class LocationController {
    private final LocationService locationService;

    @GetMapping
    public List<LocationResponse> listTree() {
        return locationService.listTree();
    }

    @GetMapping("/{id}")
    public LocationResponse get(@PathVariable Long id) {
        return locationService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LocationResponse create(@RequestBody @Valid LocationRequest req) {
        return locationService.create(req);
    }

    @PutMapping("/{id}")
    public LocationResponse update(@PathVariable Long id, @RequestBody @Valid LocationRequest req) {
        return locationService.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        locationService.delete(id);
    }
}