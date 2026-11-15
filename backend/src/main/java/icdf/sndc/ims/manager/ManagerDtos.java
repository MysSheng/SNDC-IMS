package icdf.sndc.ims.manager;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class ManagerDtos {

    private ManagerDtos() {}

    public record CreateManagerRequest(
            @NotBlank(message = "Display name is required")
            @Size(max = 100, message = "Display name must be at most 100 characters")
            String displayName,
            UUID userId
    ) {}

    public record ManagerResponse(UUID id, String displayName, UUID userId, Instant createdAt) {
        static ManagerResponse from(Manager m) {
            return new ManagerResponse(m.getId(), m.getDisplayName(),
                    m.getUser() == null ? null : m.getUser().getId(), m.getCreatedAt());
        }
    }
}
