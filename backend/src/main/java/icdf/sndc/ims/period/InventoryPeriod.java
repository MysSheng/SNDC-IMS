package icdf.sndc.ims.period;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 盤點期別（年度＋季度），例如 2026Q3。主管每季提供一份財產清單，匯入時指定期別；
 * 同一筆資產可同時屬於多個期別（規格書第 4.6 節）。
 */
@Entity
@Table(name = "inventory_periods")
public class InventoryPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "period_year", nullable = false)
    private int year;

    @Column(name = "period_quarter", nullable = false)
    private int quarter;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected InventoryPeriod() {}

    public InventoryPeriod(int year, int quarter) {
        this.year = year;
        this.quarter = quarter;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public int getYear() { return year; }
    public int getQuarter() { return quarter; }
    public Instant getCreatedAt() { return createdAt; }

    /** 例如「2026Q3」。 */
    public String label() {
        return year + "Q" + quarter;
    }
}
