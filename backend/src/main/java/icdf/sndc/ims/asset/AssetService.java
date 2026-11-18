package icdf.sndc.ims.asset;

import icdf.sndc.ims.asset.AssetDtos.AssetQuery;
import icdf.sndc.ims.asset.AssetDtos.AssetRequest;
import icdf.sndc.ims.asset.AssetDtos.AssetResponse;
import icdf.sndc.ims.common.ApiException;
import icdf.sndc.ims.common.PageResponse;
import icdf.sndc.ims.common.TextNormalizer;
import icdf.sndc.ims.config.AppProperties;
import icdf.sndc.ims.location.LocationService;
import icdf.sndc.ims.manager.ManagerService;
import icdf.sndc.ims.period.PeriodService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AssetService {

    /** 允許排序的欄位白名單：API 參數名稱 → Entity 屬性。 */
    private static final Map<String, String> SORTABLE = Map.of(
            "itemName", "itemName",
            "assetCode", "assetCode",
            "quantity", "quantity",
            "purchaseDate", "purchaseDate",
            "expectedExpiryDate", "expectedExpiryDate",
            "createdAt", "createdAt",
            "updatedAt", "updatedAt");

    /** 購置日期格式 2xxx/xx/xx（專案負責人需求）。 */
    static final int MIN_PURCHASE_YEAR = 2000;
    static final int MAX_PURCHASE_YEAR = 2999;

    private final AssetRepository assetRepository;
    private final ManagerService managerService;
    private final LocationService locationService;
    private final PeriodService periodService;
    private final AppProperties props;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    /** 「即將到期」的天數範圍。 */
    public static final int EXPIRING_SOON_DAYS = 90;

    public AssetService(AssetRepository assetRepository, ManagerService managerService,
                        LocationService locationService, PeriodService periodService, AppProperties props,
                        Clock clock) {
        this.assetRepository = assetRepository;
        this.managerService = managerService;
        this.locationService = locationService;
        this.periodService = periodService;
        this.props = props;
        this.clock = clock;
    }

    /** 業務時區（APP_TIMEZONE）的今天（規格書第 7.7 節）。 */
    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private AssetResponse toResponse(Asset a) {
        return AssetResponse.from(a, today(), EXPIRING_SOON_DAYS);
    }

    @Transactional
    public AssetResponse create(AssetRequest req) {
        String code = requireText(req.assetCode(), "assetCode", "Asset code is required");
        if (assetRepository.existsByAssetCode(code)) {
            throw ApiException.conflict("ASSET_CODE_DUPLICATE", "Asset code already exists", "assetCode");
        }
        Asset asset = new Asset(code, requireText(req.itemName(), "itemName", "Item name is required"), 1);
        apply(asset, req);
        return toResponse(assetRepository.save(asset));
    }

    @Transactional(readOnly = true)
    public AssetResponse get(UUID id) {
        return toResponse(requireActive(id));
    }

    /** 取得未刪除的資產；不存在時回傳 empty（不丟例外，避免標記外層交易為 rollback-only）。 */
    @Transactional(readOnly = true)
    public java.util.Optional<AssetResponse> findActive(UUID id) {
        return assetRepository.findByIdAndDeletedAtIsNull(id).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<AssetResponse> search(AssetQuery q, Integer page, Integer size, String sort) {
        return PageResponse.from(assetRepository.findAll(spec(q), pageable(page, size, sort)), this::toResponse);
    }

    /** 符合條件的紀錄筆數與數量合計（COUNT_QUERY；規格書第 7.2 節）。 */
    @Transactional(readOnly = true)
    public CountSummary countSummary(AssetQuery q) {
        Specification<Asset> spec = spec(q);
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Asset> countRoot = countQuery.from(Asset.class);
        countQuery.select(cb.count(countRoot)).where(spec.toPredicate(countRoot, countQuery, cb));
        long records = entityManager.createQuery(countQuery).getSingleResult();

        CriteriaQuery<Long> sumQuery = cb.createQuery(Long.class);
        Root<Asset> sumRoot = sumQuery.from(Asset.class);
        sumQuery.select(cb.coalesce(cb.sumAsLong(sumRoot.get("quantity")), 0L))
                .where(spec.toPredicate(sumRoot, sumQuery, cb));
        long total = entityManager.createQuery(sumQuery).getSingleResult();
        return new CountSummary(records, total);
    }

    @Transactional(readOnly = true)
    public List<DuplicateItem> duplicates() {
        return assetRepository.findDuplicates();
    }

    /** 由現有資產建立整筆取代用的請求（供 AI 助理的修改功能只覆寫指定欄位）。 */
    public static AssetRequest toRequest(AssetResponse a) {
        return new AssetRequest(
                a.assetCode(), a.itemName(), a.techMission(),
                a.manager() == null ? null : a.manager().id(),
                a.purchaseDate(), a.usefulLifeYears(),
                a.location() == null ? null : a.location().id(),
                a.specification(), a.acquisitionType(), a.unit(), a.quantity(), a.valueTwd(), a.valueUsd(),
                a.color(), a.description(), null, null);
    }

    private Specification<Asset> spec(AssetQuery q) {
        if (q.purchasedFrom() != null && q.purchasedTo() != null && q.purchasedFrom().isAfter(q.purchasedTo())) {
            throw ApiException.invalidField("purchasedFrom", "purchasedFrom must not be after purchasedTo");
        }
        if (q.expiresFrom() != null && q.expiresTo() != null && q.expiresFrom().isAfter(q.expiresTo())) {
            throw ApiException.invalidField("expiresFrom", "expiresFrom must not be after expiresTo");
        }
        if (q.minQuantity() != null && q.minQuantity() < 1) {
            throw ApiException.invalidField("minQuantity", "minQuantity must be at least 1");
        }
        PeriodService.validate(q.year(), q.quarter());
        AssetQuery normalized = new AssetQuery(
                TextNormalizer.normalize(q.keyword()),
                TextNormalizer.normalize(q.assetCode()),
                TextNormalizer.normalize(q.itemName()),
                TextNormalizer.normalize(q.techMission()),
                TextNormalizer.normalize(q.color()),
                q.managerId(),
                q.locationId(),
                q.expiryStatus(),
                q.purchasedFrom(),
                q.purchasedTo(),
                q.expiresFrom(),
                q.expiresTo(),
                q.minQuantity(),
                q.year(),
                q.quarter());
        return AssetSpecifications.matching(normalized, today(), EXPIRING_SOON_DAYS);
    }

    @Transactional
    public AssetResponse update(UUID id, AssetRequest req) {
        Asset asset = requireActive(id);
        String code = requireText(req.assetCode(), "assetCode", "Asset code is required");
        if (!code.equals(asset.getAssetCode()) && assetRepository.existsByAssetCodeAndIdNot(code, id)) {
            throw ApiException.conflict("ASSET_CODE_DUPLICATE", "Asset code already exists", "assetCode");
        }
        asset.setAssetCode(code);
        asset.setItemName(requireText(req.itemName(), "itemName", "Item name is required"));
        apply(asset, req);
        return toResponse(assetRepository.saveAndFlush(asset));
    }

    /** Soft Delete：只設定 deleted_at，不移除資料列。 */
    @Transactional
    public void delete(UUID id) {
        Asset asset = requireActive(id);
        asset.softDelete(Instant.now());
    }

    // ------------------------------------------------------------------

    private Asset requireActive(UUID id) {
        return assetRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ApiException.notFound("Asset"));
    }

    private void apply(Asset asset, AssetRequest req) {
        if (req.purchaseDate().getYear() < MIN_PURCHASE_YEAR || req.purchaseDate().getYear() > MAX_PURCHASE_YEAR) {
            throw ApiException.invalidField("purchaseDate", "Purchase date year must be between 2000 and 2999");
        }
        asset.setTechMission(requireText(req.techMission(), "techMission", "Technical mission is required"));
        asset.setManager(managerService.requireForField(req.managerId(), "managerId"));
        asset.setLocation(locationService.requireForField(req.locationId(), "locationId"));
        asset.setPurchaseDate(req.purchaseDate());
        asset.setUsefulLifeYears(req.usefulLifeYears());
        asset.setExpectedExpiryDate(ExpiryCalculator.expectedExpiryDate(req.purchaseDate(), req.usefulLifeYears()));
        asset.setSpecification(TextNormalizer.normalize(req.specification()));
        asset.setAcquisitionType(TextNormalizer.normalize(req.acquisitionType()));
        asset.setUnit(TextNormalizer.normalize(req.unit()));
        asset.setQuantity(req.quantity() == null ? 1 : req.quantity());
        asset.setValueTwd(req.valueTwd());
        asset.setValueUsd(req.valueUsd());
        asset.setColor(TextNormalizer.normalize(req.color()));
        asset.setDescription(TextNormalizer.normalize(req.description()));
        if (req.periodYear() != null || req.periodQuarter() != null) {
            if (req.periodYear() == null || req.periodQuarter() == null) {
                throw ApiException.invalidField("periodQuarter", "Year and quarter must be provided together");
            }
            asset.addPeriod(periodService.getOrCreate(req.periodYear(), req.periodQuarter()));
        }
    }

    private static String requireText(String raw, String field, String message) {
        String v = TextNormalizer.normalize(raw);
        if (v == null) {
            throw ApiException.invalidField(field, message);
        }
        return v;
    }

    private Pageable pageable(Integer page, Integer size, String sort) {
        int p = page == null ? 0 : page;
        int s = size == null ? props.paging().defaultSize() : size;
        if (p < 0) {
            throw ApiException.invalidField("page", "Page must be zero or greater");
        }
        if (s < 1 || s > props.paging().maxSize()) {
            throw ApiException.invalidField("size", "Size must be between 1 and " + props.paging().maxSize());
        }
        return PageRequest.of(p, s, parseSort(sort));
    }

    /** sort 格式：「欄位,asc|desc」，例如 itemName,asc。預設依品項名稱排序。 */
    static Sort parseSort(String sort) {
        Sort tieBreaker = Sort.by(Sort.Direction.ASC, "assetCode");
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.ASC, "itemName").and(tieBreaker);
        }
        String[] parts = sort.split(",");
        String property = SORTABLE.get(parts[0].trim());
        if (property == null || parts.length > 2) {
            throw ApiException.invalidField("sort", "Unsupported sort field");
        }
        Sort.Direction dir = Sort.Direction.ASC;
        if (parts.length == 2) {
            dir = Sort.Direction.fromOptionalString(parts[1].trim())
                    .orElseThrow(() -> ApiException.invalidField("sort", "Sort direction must be asc or desc"));
        }
        Sort primary = Sort.by(dir, property);
        return "assetCode".equals(property) ? primary : primary.and(tieBreaker);
    }
}
