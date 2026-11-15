package icdf.sndc.ims.asset;

import icdf.sndc.ims.asset.AssetDtos.AssetQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 以 JPA Criteria 組合查詢條件；所有值皆為參數，不拼接 SQL。一律排除 Soft Deleted 紀錄。 */
final class AssetSpecifications {

    private AssetSpecifications() {}

    /** query 內的字串須已正規化。 */
    static Specification<Asset> matching(AssetQuery q, LocalDate today, int soonDays) {
        return (root, query, cb) -> {
            // 清單查詢時一併載入保管人與位置，避免 N+1；分頁的 count 查詢不 fetch
            if (query != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("manager", JoinType.LEFT);
                root.fetch("location", JoinType.LEFT);
            }
            List<Predicate> p = new ArrayList<>();
            p.add(cb.isNull(root.get("deletedAt")));
            if (q.keyword() != null) {
                String like = "%" + escapeLike(q.keyword()) + "%";
                p.add(cb.or(cb.like(root.get("itemName"), like, '\\'), cb.like(root.get("assetCode"), like, '\\')));
            }
            if (q.assetCode() != null) {
                p.add(cb.equal(root.get("assetCode"), q.assetCode()));
            }
            if (q.itemName() != null) {
                p.add(cb.like(root.get("itemName"), "%" + escapeLike(q.itemName()) + "%", '\\'));
            }
            if (q.techMission() != null) {
                p.add(cb.equal(root.get("techMission"), q.techMission()));
            }
            if (q.color() != null) {
                p.add(cb.equal(root.get("color"), q.color()));
            }
            if (q.managerId() != null) {
                p.add(cb.equal(root.get("manager").get("id"), q.managerId()));
            }
            if (q.locationId() != null) {
                p.add(cb.equal(root.get("location").get("id"), q.locationId()));
            }
            if (q.expiryStatus() != null) {
                Path<LocalDate> expiry = root.get("expectedExpiryDate");
                switch (q.expiryStatus()) {
                    case EXPIRED -> p.add(cb.lessThanOrEqualTo(expiry, today));
                    case EXPIRING_SOON -> p.add(cb.and(cb.greaterThan(expiry, today),
                            cb.lessThanOrEqualTo(expiry, today.plusDays(soonDays))));
                    case VALID -> p.add(cb.greaterThan(expiry, today.plusDays(soonDays)));
                }
            }
            if (q.purchasedFrom() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("purchaseDate"), q.purchasedFrom()));
            }
            if (q.purchasedTo() != null) {
                p.add(cb.lessThanOrEqualTo(root.get("purchaseDate"), q.purchasedTo()));
            }
            if (q.minQuantity() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("quantity"), q.minQuantity()));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
    }

    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
