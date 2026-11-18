package icdf.sndc.ims.manager;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ManagerRepository extends JpaRepository<Manager, UUID> {
    boolean existsByDisplayName(String displayName);

    Optional<Manager> findByDisplayName(String displayName);
}
