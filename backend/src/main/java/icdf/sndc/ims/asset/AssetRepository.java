package icdf.sndc.ims.asset;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

import java.util.Optional;
import java.util.UUID;

public interface AssetRepository extends JpaRepository<Asset, UUID>, JpaSpecificationExecutor<Asset> {

    @EntityGraph(attributePaths = {"manager", "location"})
    Optional<Asset> findByIdAndDeletedAtIsNull(UUID id);

    /** 含 Soft Deleted 紀錄：asset_code 全域唯一。 */
    boolean existsByAssetCode(String assetCode);

    boolean existsByAssetCodeAndIdNot(String assetCode, UUID id);

    /** 名稱相同即視為同一品項；合計數量大於 1 才列入；排除 Soft Deleted（規格書第 5.2 節）。 */
    @Query("""
            select new icdf.sndc.ims.asset.DuplicateItem(a.itemName, sum(a.quantity), count(a))
            from Asset a
            where a.deletedAt is null
            group by a.itemName
            having sum(a.quantity) > 1
            order by sum(a.quantity) desc, a.itemName asc
            """)
    List<DuplicateItem> findDuplicates();
}
