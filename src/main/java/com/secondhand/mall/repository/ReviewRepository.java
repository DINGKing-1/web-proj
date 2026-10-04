package com.secondhand.mall.repository;

import com.secondhand.mall.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByProductIdOrderByCreatedAtDesc(Long productId);
    List<Review> findBySellerUsernameOrderByCreatedAtDesc(String sellerUsername);
    Optional<Review> findByOrderId(Long orderId);
    boolean existsByOrderId(Long orderId);
    long countBySellerUsername(String sellerUsername);

    /** 用户清理：删除其作为买家/卖家的全部评价 */
    @Modifying
    @Query("DELETE FROM Review r WHERE r.buyerUsername = :username OR r.sellerUsername = :username")
    void deleteByUsername(@Param("username") String username);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.sellerUsername = :seller")
    Double avgRatingBySeller(@Param("seller") String seller);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.productId = :productId")
    Double avgRatingByProduct(@Param("productId") Long productId);
}
