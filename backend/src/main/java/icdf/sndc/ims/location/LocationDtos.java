package icdf.sndc.ims.location;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class LocationDtos {

    private LocationDtos() {}

    public record CreateLocationRequest(
            @NotBlank(message = "Location name is required")
            @Size(max = 100, message = "Location name must be at most 100 characters")
            String name,
            @Size(max = 500, message = "Description must be at most 500 characters")
            String description
    ) {}

    public record LocationResponse(UUID id, String name, String description, Instant createdAt) {
        static LocationResponse from(Location l) {
            return new LocationResponse(l.getId(), l.getName(), l.getDescription(), l.getCreatedAt());
        }
    }
}
