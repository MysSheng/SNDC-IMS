package icdf.sndc.ims.user;

import icdf.sndc.ims.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** App 登入帳號。第一版不設角色（規格書第 3.5 節）。註冊／登入於 Phase 2 實作。 */
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /** PIN 的安全雜湊值；絕不儲存明文、絕不回傳給 Client。 */
    @Column(name = "pin_hash", nullable = false, length = 100)
    private String pinHash;

    protected User() {}

    public User(String username, String pinHash) {
        this.username = username;
        this.pinHash = pinHash;
    }

    public String getUsername() { return username; }
    public String getPinHash() { return pinHash; }
}
