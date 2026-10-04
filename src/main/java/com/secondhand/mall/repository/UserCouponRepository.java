package com.secondhand.mall.repository;

import com.secondhand.mall.entity.UserCoupon;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserCouponRepository extends JpaRepository<UserCoupon, Long> {
    List<UserCoupon> findByUsernameOrderByClaimedAtDesc(String username);
    List<UserCoupon> findByUsernameAndStatusOrderByClaimedAtDesc(String username, String status);
    Optional<UserCoupon> findByIdAndUsername(Long id, String username);
    Optional<UserCoupon> findByUsedOrderId(Long orderId);
    long countByCouponIdAndUsername(Long couponId, String username);

    /** 核销前行锁，防并发双花 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT uc FROM UserCoupon uc WHERE uc.id = :id AND uc.username = :username")
    Optional<UserCoupon> findByIdAndUsernameForUpdate(@Param("id") Long id, @Param("username") String username);

    /** 乐观核销：仅 unused → used，返回受影响行数 */
    @Modifying
    @Query("UPDATE UserCoupon uc SET uc.status = 'used', uc.usedOrderId = :orderId, uc.usedAt = :now "
            + "WHERE uc.id = :id AND uc.username = :username AND uc.status = 'unused'")
    int markUsed(@Param("id") Long id, @Param("username") String username,
                 @Param("orderId") Long orderId, @Param("now") java.time.LocalDateTime now);
}
