package com.family.storage;

import com.family.storage.common.ApiException;
import com.family.storage.item.ItemRepository;
import com.family.storage.location.Location;
import com.family.storage.location.LocationRepository;
import com.family.storage.location.LocationService;
import com.family.storage.location.dto.LocationRequest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationServiceTest {
    @Mock LocationRepository locationRepository;
    @Mock ItemRepository itemRepository;

    @Test
    void movingLocationUnderItselfIsRejected() {
        Location location = new Location();
        location.setId(5L);
        when(locationRepository.findById(5L)).thenReturn(Optional.of(location));
        var svc = new LocationService(locationRepository, itemRepository);
        assertThrows(ApiException.class, () -> svc.update(5L, new LocationRequest("客厅", 5L, 0, null, null)));
    }

    @Test
    void createStoresDescriptionAndImage() {
        when(locationRepository.save(any())).thenAnswer(inv -> {
            Location l = inv.getArgument(0);
            l.setId(9L);
            return l;
        });
        var svc = new LocationService(locationRepository, itemRepository);
        var res = svc.create(new LocationRequest("厨房吊柜", null, 0, "顶层放干货", "/uploads/202609/a.png"));
        assertEquals("顶层放干货", res.description());
        assertEquals("/uploads/202609/a.png", res.imageUrl());
        verify(locationRepository).save(any());
    }

    @Test
    void updateOverridesDescriptionAndImage() {
        Location location = new Location();
        location.setId(5L);
        location.setDescription("旧说明");
        location.setImageUrl("/uploads/old.png");
        when(locationRepository.findById(5L)).thenReturn(Optional.of(location));
        when(locationRepository.save(any())).thenReturn(location);
        var svc = new LocationService(locationRepository, itemRepository);
        var res = svc.update(5L, new LocationRequest("鞋柜", null, 1, "下层放常穿鞋", null));
        assertEquals("下层放常穿鞋", res.description());
        assertNull(res.imageUrl());
    }

    @Test
    void listTreeIncludesDescriptionAndImage() {
        Location root = new Location();
        root.setId(1L);
        root.setName("厨房");
        root.setDescription("吊柜放干货");
        root.setImageUrl("/uploads/202609/kitchen.png");
        when(locationRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(root));
        var svc = new LocationService(locationRepository, itemRepository);
        var tree = svc.listTree();
        assertEquals(1, tree.size());
        assertEquals("吊柜放干货", tree.get(0).description());
        assertEquals("/uploads/202609/kitchen.png", tree.get(0).imageUrl());
    }
}
