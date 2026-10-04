package com.secondhand.mall.repository;

import com.secondhand.mall.entity.Coupon;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {
    List<Coupon> findByEnabledTrueOrderByEndAtAsc();
    List<Coupon> findAllByOrderByCreatedAtDesc();
    Optional<Coupon> findByCode(String code);
    boolean existsByCode(String code);

    /** 领取前行锁，防并发超发 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT c FROM Coupon c WHERE c.id = :id")
    Optional<Coupon> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
}
