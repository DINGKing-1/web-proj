package com.secondhand.mall.repository;

import com.secondhand.mall.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    @Query("SELECT r FROM ChatRoom r WHERE (r.user1Username = :u1 AND r.user2Username = :u2 AND r.productId = :pid) OR (r.user1Username = :u2 AND r.user2Username = :u1 AND r.productId = :pid)")
    Optional<ChatRoom> findByUsersAndProduct(@Param("u1") String user1, @Param("u2") String user2, @Param("pid") Long productId);

    /** 同一对用户 + 商品 + 会话类型（区分买卖会话与客服会话） */
    @Query("SELECT r FROM ChatRoom r WHERE ((r.user1Username = :u1 AND r.user2Username = :u2) OR (r.user1Username = :u2 AND r.user2Username = :u1)) AND r.productId = :pid AND r.roomType = :type")
    Optional<ChatRoom> findByUsersProductAndType(@Param("u1") String user1, @Param("u2") String user2,
                                                @Param("pid") Long productId, @Param("type") String roomType);

    @Query("SELECT r FROM ChatRoom r WHERE (r.user1Username = :u1 AND r.user2Username = :u2) OR (r.user1Username = :u2 AND r.user2Username = :u1)")
    Optional<ChatRoom> findByUsers(@Param("u1") String user1, @Param("u2") String user2);

    @Query("SELECT r FROM ChatRoom r WHERE (r.user1Username = :username OR r.user2Username = :username) AND r.deleted = false AND r.lastMessage IS NOT NULL AND r.lastMessage != '' ORDER BY r.lastMessageTime DESC")
    List<ChatRoom> findActiveByUsername(@Param("username") String username);

    @Query("SELECT r FROM ChatRoom r WHERE (r.user1Username = :username OR r.user2Username = :username) AND r.deleted = false")
    List<ChatRoom> findByUsername(@Param("username") String username);

    @Modifying
    @Query("DELETE FROM ChatRoom r WHERE r.user1Username = :username OR r.user2Username = :username")
    void deleteByUsername(@Param("username") String username);
}
