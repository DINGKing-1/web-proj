package com.secondhand.mall.repository;

import com.secondhand.mall.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    List<User> findByUsernameIn(Collection<String> usernames);

    @Query("SELECT u FROM User u WHERE u.deleted = false OR u.deleted IS NULL")
    List<User> findActiveUsers();

    @Query("SELECT u FROM User u WHERE u.deleted = true")
    List<User> findDeletedUsers();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.username = :username")
    Optional<User> findByUsernameForUpdate(@Param("username") String username);

    /** 轻量更新最后活跃时间，避免每请求整实体 save */
    @Modifying
    @Query("UPDATE User u SET u.lastActiveTime = :time WHERE u.username = :username")
    int touchLastActive(@Param("username") String username, @Param("time") java.time.LocalDateTime time);
}