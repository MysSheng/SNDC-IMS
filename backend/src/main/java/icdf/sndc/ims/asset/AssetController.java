package icdf.sndc.ims.asset;

import icdf.sndc.ims.asset.AssetDtos.AssetQuery;
import icdf.sndc.ims.asset.AssetDtos.AssetRequest;
import icdf.sndc.ims.asset.AssetDtos.AssetResponse;
import icdf.sndc.ims.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/assets")
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AssetResponse create(@Valid @RequestBody AssetRequest request) {
        return assetService.create(request);
    }

    @GetMapping
    public PageResponse<AssetResponse> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String assetCode,
            @RequestParam(required = false) String itemName,
            @RequestParam(required = false) String techMission,
            @RequestParam(required = false) String color,
            @RequestParam(required = false) UUID managerId,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) ExpiryStatus expiryStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate purchasedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate purchasedTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiresFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiresTo,
            @RequestParam(required = false) Integer minQuantity,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer quarter,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        AssetQuery query = new AssetQuery(keyword, assetCode, itemName, techMission, color, managerId, locationId,
                expiryStatus, purchasedFrom, purchasedTo, expiresFrom, expiresTo, minQuantity, year, quarter);
        return assetService.search(query, page, size, sort);
    }

    @GetMapping("/duplicates")
    public java.util.List<DuplicateItem> duplicates() {
        return assetService.duplicates();
    }

    @GetMapping("/{id}")
    public AssetResponse get(@PathVariable UUID id) {
        return assetService.get(id);
    }

    @PutMapping("/{id}")
    public AssetResponse update(@PathVariable UUID id, @Valid @RequestBody AssetRequest request) {
        return assetService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        assetService.delete(id);
    }
}
