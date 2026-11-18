package icdf.sndc.ims.asset;

import icdf.sndc.ims.common.BaseEntity;
import icdf.sndc.ims.location.Location;
import icdf.sndc.ims.manager.Manager;
import icdf.sndc.ims.period.InventoryPeriod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 一筆 Asset 代表一組具有相同管理屬性的物品；quantity 為該筆紀錄的數量（規格書第 4.1、4.2 節）。
 * manager 在介面上稱為「保管人」。
 */
@Entity
@Table(name = "assets")
public class Asset extends BaseEntity {

    @Column(name = "asset_code", nullable = false, unique = true, length = 64)
    private String assetCode;

    @Column(name = "item_name", nullable = false, length = 200)
    private String itemName;

    @Column(name = "tech_mission", length = 100)
    private String techMission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private Manager manager;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "useful_life_years", precision = 4, scale = 1)
    private BigDecimal usefulLifeYears;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private Location location;

    /** 預計到期日：由 Service 在每次儲存時依購置日期與使用年限重新計算，不由 API 直接寫入。 */
    @Column(name = "expected_expiry_date")
    private LocalDate expectedExpiryDate;

    @Column(length = 50)
    private String color;

    /** 規格及摘要（Excel「規格及摘要」）。 */
    @Column(length = 2000)
    private String specification;

    /** 購置類型／經費來源，例如「當地採購」。 */
    @Column(name = "acquisition_type", length = 100)
    private String acquisitionType;

    /** 單位，例如「臺」「具」「套」。 */
    @Column(length = 20)
    private String unit;

    @Column(name = "value_twd", precision = 14, scale = 2)
    private BigDecimal valueTwd;

    @Column(name = "value_usd", precision = 14, scale = 2)
    private BigDecimal valueUsd;

    /** 這筆資產出現在哪些盤點期別的清單中。 */
    @ManyToMany
    @JoinTable(name = "asset_periods",
            joinColumns = @JoinColumn(name = "asset_id"),
            inverseJoinColumns = @JoinColumn(name = "period_id"))
    @BatchSize(size = 100)
    private Set<InventoryPeriod> periods = new HashSet<>();

    @Column(nullable = false)
    private int quantity = 1;

    @Column(length = 2000)
    private String description;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Asset() {}

    public Asset(String assetCode, String itemName, int quantity) {
        this.assetCode = assetCode;
        this.itemName = itemName;
        this.quantity = quantity;
    }

    public void softDelete(Instant when) {
        this.deletedAt = when;
    }

    /** 匯入時遇到已刪除的同編號資產：恢復並以 Excel 內容更新。 */
    void restore() {
        this.deletedAt = null;
    }

    void addPeriod(InventoryPeriod period) {
        periods.add(period);
    }

    /** 依年度、季度排序（舊到新）。 */
    public List<InventoryPeriod> getPeriodsSorted() {
        return periods.stream()
                .sorted(Comparator.comparingInt(InventoryPeriod::getYear).thenComparingInt(InventoryPeriod::getQuarter))
                .toList();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public String getAssetCode() { return assetCode; }
    public String getItemName() { return itemName; }
    public String getTechMission() { return techMission; }
    public Manager getManager() { return manager; }
    public LocalDate getPurchaseDate() { return purchaseDate; }
    public BigDecimal getUsefulLifeYears() { return usefulLifeYears; }
    public Location getLocation() { return location; }
    public LocalDate getExpectedExpiryDate() { return expectedExpiryDate; }
    public String getColor() { return color; }
    public String getSpecification() { return specification; }
    public String getAcquisitionType() { return acquisitionType; }
    public String getUnit() { return unit; }
    public BigDecimal getValueTwd() { return valueTwd; }
    public BigDecimal getValueUsd() { return valueUsd; }
    public int getQuantity() { return quantity; }
    public String getDescription() { return description; }
    public Instant getDeletedAt() { return deletedAt; }

    void setAssetCode(String assetCode) { this.assetCode = assetCode; }
    void setItemName(String itemName) { this.itemName = itemName; }
    void setTechMission(String techMission) { this.techMission = techMission; }
    void setManager(Manager manager) { this.manager = manager; }
    void setPurchaseDate(LocalDate purchaseDate) { this.purchaseDate = purchaseDate; }
    void setUsefulLifeYears(BigDecimal usefulLifeYears) { this.usefulLifeYears = usefulLifeYears; }
    void setLocation(Location location) { this.location = location; }
    void setExpectedExpiryDate(LocalDate expectedExpiryDate) { this.expectedExpiryDate = expectedExpiryDate; }
    void setColor(String color) { this.color = color; }
    void setSpecification(String specification) { this.specification = specification; }
    void setAcquisitionType(String acquisitionType) { this.acquisitionType = acquisitionType; }
    void setUnit(String unit) { this.unit = unit; }
    void setValueTwd(BigDecimal valueTwd) { this.valueTwd = valueTwd; }
    void setValueUsd(BigDecimal valueUsd) { this.valueUsd = valueUsd; }
    void setQuantity(int quantity) { this.quantity = quantity; }
    void setDescription(String description) { this.description = description; }
}
