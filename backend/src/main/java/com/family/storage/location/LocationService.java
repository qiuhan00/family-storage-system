package com.family.storage.location;

import com.family.storage.common.ApiException;
import com.family.storage.item.ItemRepository;
import com.family.storage.location.dto.LocationRequest;
import com.family.storage.location.dto.LocationResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 位置业务：树形列表、增改查删。
 * - create/update 校验父位置存在（404 LOCATION_NOT_FOUND）且不构成环（400 LOCATION_CYCLE）。
 * - delete 校验位置下仍有物品则拒绝（400 LOCATION_IN_USE）；
 *   另校验仍存在子位置则拒绝，避免违反自引用外键导致 500。
 */
@Service
@RequiredArgsConstructor
public class LocationService {

    private static final String NOT_FOUND_CODE = "LOCATION_NOT_FOUND";

    private final LocationRepository locationRepository;
    private final ItemRepository itemRepository;

    public List<LocationResponse> listTree() {
        List<Location> all = locationRepository.findAllByOrderBySortOrderAsc();
        Map<Long, List<Location>> byParent = new HashMap<>();
        for (Location l : all) {
            byParent.computeIfAbsent(l.getParentId(), k -> new ArrayList<>()).add(l);
        }
        List<LocationResponse> roots = new ArrayList<>();
        for (Location l : byParent.getOrDefault(null, List.of())) {
            roots.add(toResponse(l, byParent));
        }
        return roots;
    }

    public LocationResponse get(Long id) {
        find(id);
        return listTree().stream()
                .flatMap(this::flatten)
                .filter(n -> n.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND_CODE, "位置不存在"));
    }

    public LocationResponse create(LocationRequest req) {
        validateParent(req.parentId());
        Location l = new Location();
        l.setName(req.name());
        l.setParentId(req.parentId());
        l.setSortOrder(req.sortOrder());
        return toFlatResponse(locationRepository.save(l));
    }

    public LocationResponse update(Long id, LocationRequest req) {
        Location l = find(id);
        validateParentForUpdate(id, req.parentId());
        l.setName(req.name());
        l.setParentId(req.parentId());
        l.setSortOrder(req.sortOrder());
        return toFlatResponse(locationRepository.save(l));
    }

    public void delete(Long id) {
        if (itemRepository.countByLocationId(id) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LOCATION_IN_USE",
                    "该位置下仍有物品，请先迁移或删除物品");
        }
        if (locationRepository.existsByParentId(id)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LOCATION_HAS_CHILDREN",
                    "该位置下仍有子位置，请先删除子位置");
        }
        locationRepository.delete(find(id));
    }

    private void validateParent(Long parentId) {
        if (parentId != null && locationRepository.findById(parentId).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND_CODE, "父位置不存在");
        }
    }

    private void validateParentForUpdate(Long id, Long parentId) {
        if (parentId == null) {
            return;
        }
        if (parentId.equals(id)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LOCATION_CYCLE", "位置不能挂到自身下");
        }
        if (locationRepository.findById(parentId).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND_CODE, "父位置不存在");
        }
        // 环校验：沿新父链向上，若出现当前节点自身则构成环
        Set<Long> visited = new HashSet<>();
        Long cursor = parentId;
        while (cursor != null) {
            if (cursor.equals(id)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "LOCATION_CYCLE",
                        "不能将位置移动到其子孙节点下");
            }
            if (!visited.add(cursor)) {
                return; // 数据异常冗余保护：避免死循环
            }
            cursor = locationRepository.findById(cursor)
                    .map(Location::getParentId).orElse(null);
        }
    }

    private LocationResponse toResponse(Location l, Map<Long, List<Location>> byParent) {
        List<LocationResponse> children = new ArrayList<>();
        for (Location c : byParent.getOrDefault(l.getId(), List.of())) {
            children.add(toResponse(c, byParent));
        }
        return new LocationResponse(l.getId(), l.getName(), l.getParentId(), l.getSortOrder(), children);
    }

    private LocationResponse toFlatResponse(Location l) {
        return new LocationResponse(l.getId(), l.getName(), l.getParentId(), l.getSortOrder(), List.of());
    }

    private Stream<LocationResponse> flatten(LocationResponse node) {
        return Stream.concat(Stream.of(node), node.children().stream().flatMap(this::flatten));
    }

    private Location find(Long id) {
        return locationRepository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND_CODE, "位置不存在"));
    }
}