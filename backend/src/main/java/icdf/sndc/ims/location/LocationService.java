package icdf.sndc.ims.location;

import icdf.sndc.ims.common.ApiException;
import icdf.sndc.ims.common.TextNormalizer;
import icdf.sndc.ims.location.LocationDtos.CreateLocationRequest;
import icdf.sndc.ims.location.LocationDtos.LocationResponse;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class LocationService {

    private final LocationRepository locationRepository;

    public LocationService(LocationRepository locationRepository) {
        this.locationRepository = locationRepository;
    }

    @Transactional(readOnly = true)
    public List<LocationResponse> list() {
        return locationRepository.findAll(Sort.by("name")).stream()
                .map(LocationResponse::from)
                .toList();
    }

    @Transactional
    public LocationResponse create(CreateLocationRequest req) {
        String name = TextNormalizer.normalize(req.name());
        if (name == null) {
            throw ApiException.invalidField("name", "Location name is required");
        }
        if (locationRepository.existsByName(name)) {
            throw ApiException.conflict("LOCATION_NAME_DUPLICATE", "Location name already exists", "name");
        }
        Location saved = locationRepository.save(new Location(name, TextNormalizer.normalize(req.description())));
        return LocationResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Location requireForField(UUID id, String field) {
        return locationRepository.findById(id)
                .orElseThrow(() -> ApiException.invalidField(field, "Location does not exist"));
    }
}
