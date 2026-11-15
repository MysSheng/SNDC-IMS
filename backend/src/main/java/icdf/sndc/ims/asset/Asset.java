package icdf.sndc.ims.asset;

import icdf.sndc.ims.common.BaseEntity;
import icdf.sndc.ims.location.Location;
import icdf.sndc.ims.manager.Manager;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

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
    void setQuantity(int quantity) { this.quantity = quantity; }
    void setDescription(String description) { this.description = description; }
}
