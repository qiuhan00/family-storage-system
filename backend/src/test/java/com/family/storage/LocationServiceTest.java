package com.family.storage;

import com.family.storage.common.ApiException;
import com.family.storage.item.ItemRepository;
import com.family.storage.location.Location;
import com.family.storage.location.LocationRepository;
import com.family.storage.location.LocationService;
import com.family.storage.location.dto.LocationRequest;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
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
        assertThrows(ApiException.class, () -> svc.update(5L, new LocationRequest("客厅", 5L, 0)));
    }
}