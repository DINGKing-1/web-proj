package com.secondhand.mall.repository;

import com.secondhand.mall.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findTop50ByUsernameOrderByCreatedAtDesc(String username);
    long countByUsernameAndReadFlagFalse(String username);

    @Modifying
    @Query("UPDATE Notification n SET n.readFlag = true WHERE n.username = :username AND n.readFlag = false")
    int markAllRead(@Param("username") String username);

    /** 用户清理：删除其全部站内通知 */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.username = :username")
    void deleteByUsername(@Param("username") String username);
}
