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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
    private final AppProperties props;
    private final Clock clock;

    /** 「即將到期」的天數範圍。 */
    static final int EXPIRING_SOON_DAYS = 90;

    public AssetService(AssetRepository assetRepository, ManagerService managerService,
                        LocationService locationService, AppProperties props, Clock clock) {
        this.assetRepository = assetRepository;
        this.managerService = managerService;
        this.locationService = locationService;
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

    @Transactional(readOnly = true)
    public PageResponse<AssetResponse> search(AssetQuery q, Integer page, Integer size, String sort) {
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
                q.minQuantity());
        if (q.purchasedFrom() != null && q.purchasedTo() != null && q.purchasedFrom().isAfter(q.purchasedTo())) {
            throw ApiException.invalidField("purchasedFrom", "purchasedFrom must not be after purchasedTo");
        }
        if (q.minQuantity() != null && q.minQuantity() < 1) {
            throw ApiException.invalidField("minQuantity", "minQuantity must be at least 1");
        }
        return PageResponse.from(
                assetRepository.findAll(AssetSpecifications.matching(normalized, today(), EXPIRING_SOON_DAYS), pageable(page, size, sort)),
                this::toResponse);
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
        asset.setColor(TextNormalizer.normalize(req.color()));
        asset.setQuantity(req.quantity() == null ? 1 : req.quantity());
        asset.setDescription(TextNormalizer.normalize(req.description()));
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
