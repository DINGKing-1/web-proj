package com.secondhand.mall.repository;

import com.secondhand.mall.entity.Offer;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OfferRepository extends JpaRepository<Offer, Long> {
    List<Offer> findByBuyerUsernameOrderByCreatedAtDesc(String buyerUsername);
    List<Offer> findBySellerUsernameOrderByCreatedAtDesc(String sellerUsername);
    List<Offer> findByProductIdAndBuyerUsernameAndStatus(Long productId, String buyerUsername, String status);
    List<Offer> findByProductIdAndStatus(Long productId, String status);

    /** 接受/拒绝出价前行锁，防一价生成两单 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Offer o WHERE o.id = :id")
    Optional<Offer> findByIdForUpdate(@Param("id") Long id);
}
