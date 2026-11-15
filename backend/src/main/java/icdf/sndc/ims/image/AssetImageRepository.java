package icdf.sndc.ims.image;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AssetImageRepository extends JpaRepository<AssetImage, UUID> {
    List<AssetImage> findByAssetIdOrderByCreatedAtAsc(UUID assetId);
}
