package icdf.sndc.ims.manager;

import icdf.sndc.ims.common.BaseEntity;
import icdf.sndc.ims.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** 資產管理人；不一定是 App 使用者，user 為選填（規格書第 4.3 節）。 */
@Entity
@Table(name = "managers")
public class Manager extends BaseEntity {

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    protected Manager() {}

    public Manager(String displayName, User user) {
        this.displayName = displayName;
        this.user = user;
    }

    public String getDisplayName() { return displayName; }
    public User getUser() { return user; }
}
