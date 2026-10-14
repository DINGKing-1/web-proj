package com.secondhand.mall.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @JsonIgnore
    @Column(nullable = false, length = 255)
    private String password;

    @Column(length = 100)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(length = 100)
    private String nickname;

    @Column(nullable = false, length = 20)
    private String role = "user";

    /** 账号封禁：禁止登录与交易 */
    @Column(nullable = false)
    private Boolean banned = false;

    /** 首次登录强制改密（管理员初始口令等） */
    @Column(nullable = false)
    private Boolean mustChangePassword = false;

    /** 软删除标记：注销/管理员删除后进回收站，可恢复 */
    @Column(nullable = false)
    private Boolean deleted = Boolean.FALSE;

    private LocalDateTime deletedAt;

    private String deletedBy;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal balance = new BigDecimal("10000.00");

    @Column(nullable = false)
    private String avatar = "/user-avatar/default.jpg";

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @JsonIgnore
    @Column(length = 100)
    private String sessionId;

    private LocalDateTime lastActiveTime;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (role == null) role = "user";
        if (banned == null) banned = false;
        if (mustChangePassword == null) mustChangePassword = false;
        if (deleted == null) deleted = false;
        if (balance == null) balance = new BigDecimal("10000.00");
        if (avatar == null) avatar = "/user-avatar/default.jpg";
        if (lastActiveTime == null) lastActiveTime = LocalDateTime.now();
    }

    public User() {}

    public User(String username, String password, String email, String phone, String nickname) {
        this.username = username;
        this.password = password;
        this.email = email;
        this.phone = phone;
        this.nickname = nickname;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public Boolean getBanned() { return banned; }
    public void setBanned(Boolean banned) { this.banned = banned; }

        public Boolean getMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(Boolean mustChangePassword) { this.mustChangePassword = mustChangePassword; }

    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }

    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }

    public String getDeletedBy() { return deletedBy; }
    public void setDeletedBy(String deletedBy) { this.deletedBy = deletedBy; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public LocalDateTime getLastActiveTime() { return lastActiveTime; }
    public void setLastActiveTime(LocalDateTime lastActiveTime) { this.lastActiveTime = lastActiveTime; }
}
