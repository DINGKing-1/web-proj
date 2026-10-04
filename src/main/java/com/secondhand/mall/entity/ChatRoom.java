package com.secondhand.mall.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_rooms")
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String user1Username;

    @Column(nullable = false, length = 100)
    private String user2Username;

    /** 0 表示客服会话（不关联商品） */
    @Column(nullable = false)
    private Long productId;

    /** seller=买卖会话，support=客服会话 */
    @Column(nullable = false, length = 20)
    private String roomType = "seller";

    @Column(length = 500)
    private String lastMessage;

    @Column(nullable = false)
    private LocalDateTime lastMessageTime;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private Integer user1Unread = 0;

    @Column(nullable = false)
    private Integer user2Unread = 0;

    @Column(nullable = false)
    private Boolean deleted = false;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (lastMessageTime == null) lastMessageTime = LocalDateTime.now();
        if (user1Unread == null) user1Unread = 0;
        if (user2Unread == null) user2Unread = 0;
        if (deleted == null) deleted = false;
        if (roomType == null) roomType = "seller";
        if (productId == null) productId = 0L;
    }

    public ChatRoom() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUser1Username() { return user1Username; }
    public void setUser1Username(String user1Username) { this.user1Username = user1Username; }

    public String getUser2Username() { return user2Username; }
    public void setUser2Username(String user2Username) { this.user2Username = user2Username; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public String getRoomType() { return roomType; }
    public void setRoomType(String roomType) { this.roomType = roomType; }

    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }

    public LocalDateTime getLastMessageTime() { return lastMessageTime; }
    public void setLastMessageTime(LocalDateTime lastMessageTime) { this.lastMessageTime = lastMessageTime; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Integer getUser1Unread() { return user1Unread; }
    public void setUser1Unread(Integer user1Unread) { this.user1Unread = user1Unread; }

    public Integer getUser2Unread() { return user2Unread; }
    public void setUser2Unread(Integer user2Unread) { this.user2Unread = user2Unread; }

    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }
}
