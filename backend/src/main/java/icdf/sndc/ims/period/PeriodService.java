package icdf.sndc.ims.period;

import icdf.sndc.ims.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Sort;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PeriodService {

    private final InventoryPeriodRepository repository;

    public PeriodService(InventoryPeriodRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<PeriodDtos.PeriodResponse> list() {
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : repository.countActiveAssetsByPeriod()) {
            counts.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return repository.findAll(Sort.by(Sort.Direction.DESC, "year", "quarter")).stream()
                .map(p -> new PeriodDtos.PeriodResponse(p.getYear(), p.getQuarter(), p.label(),
                        counts.getOrDefault(p.getId(), 0L)))
                .toList();
    }

    /** 取得期別，不存在時建立。 */
    @Transactional
    public InventoryPeriod getOrCreate(int year, int quarter) {
        validate(year, quarter);
        return repository.findByYearAndQuarter(year, quarter)
                .orElseGet(() -> repository.saveAndFlush(new InventoryPeriod(year, quarter)));
    }

    public static void validate(Integer year, Integer quarter) {
        if (year == null && quarter == null) {
            return;
        }
        if (year == null) {
            throw ApiException.invalidField("year", "Select a year before selecting a quarter");
        }
        if (year < 2000 || year > 2999) {
            throw ApiException.invalidField("year", "Year must be between 2000 and 2999");
        }
        if (quarter != null && (quarter < 1 || quarter > 4)) {
            throw ApiException.invalidField("quarter", "Quarter must be between 1 and 4");
        }
    }
}
