package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;

/**
 * V3：新增 expected_expiry_date（預計到期日）欄位，供「已過使用年限」等查詢使用，並回填既有資料。
 * 以 Java 撰寫是為了讓日期計算在 PostgreSQL 與 H2（測試）上結果一致。
 * 計算方式與 icdf.sndc.ims.asset.ExpiryCalculator 相同：購置日期 + 使用年限（換算為月數，四捨五入）。
 * 此處刻意複製公式，不引用應用程式類別，確保日後程式修改不會改變已執行過的 migration。
 */
public class V3__Store_expected_expiry_date extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection c = context.getConnection();
        try (Statement st = c.createStatement()) {
            st.execute("ALTER TABLE assets ADD COLUMN expected_expiry_date DATE NULL");
            st.execute("CREATE INDEX idx_assets_expected_expiry_date ON assets (expected_expiry_date)");
        }
        try (PreparedStatement select = c.prepareStatement(
                "SELECT id, purchase_date, useful_life_years FROM assets "
                        + "WHERE purchase_date IS NOT NULL AND useful_life_years IS NOT NULL");
             PreparedStatement update = c.prepareStatement(
                     "UPDATE assets SET expected_expiry_date = ? WHERE id = ?");
             ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                LocalDate purchase = rs.getObject(2, LocalDate.class);
                BigDecimal years = rs.getBigDecimal(3);
                if (years.signum() <= 0) {
                    continue;
                }
                long months = years.multiply(BigDecimal.valueOf(12)).setScale(0, RoundingMode.HALF_UP).longValueExact();
                update.setObject(1, purchase.plusMonths(months));
                update.setObject(2, rs.getObject(1));
                update.addBatch();
            }
            update.executeBatch();
        }
    }
}
