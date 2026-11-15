package icdf.sndc.ims.asset;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface AssetRepository extends JpaRepository<Asset, UUID>, JpaSpecificationExecutor<Asset> {

    @EntityGraph(attributePaths = {"manager", "location"})
    Optional<Asset> findByIdAndDeletedAtIsNull(UUID id);

    /** 含 Soft Deleted 紀錄：asset_code 全域唯一。 */
    boolean existsByAssetCode(String assetCode);

    boolean existsByAssetCodeAndIdNot(String assetCode, UUID id);
}
