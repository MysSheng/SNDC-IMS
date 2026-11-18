package icdf.sndc.ims.period;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryPeriodRepository extends JpaRepository<InventoryPeriod, UUID> {

    Optional<InventoryPeriod> findByYearAndQuarter(int year, int quarter);

    /** 每個期別中未刪除的資產筆數：[period id, count]。沒有資產的期別不會出現在結果中。 */
    @Query("""
            select p.id, count(a.id)
            from Asset a join a.periods p
            where a.deletedAt is null
            group by p.id
            """)
    List<Object[]> countActiveAssetsByPeriod();
}
