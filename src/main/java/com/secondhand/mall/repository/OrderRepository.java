package com.secondhand.mall.repository;

import com.secondhand.mall.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUsernameOrderByCreatedAtDesc(String username);
    List<Order> findByUsernameAndStatusOrderByCreatedAtDesc(String username, String status);
    List<Order> findByProductIdInOrderByCreatedAtDesc(List<Long> productIds);
    List<Order> findByProductIdInAndStatusOrderByCreatedAtDesc(List<Long> productIds, String status);
    void deleteByUsername(String username);
    boolean existsByOrderNumber(String orderNumber);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.username = :username OR o.sellerUsername = :username")
    long countByUsernameOrSellerUsername(@Param("username") String username);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.username = :username AND o.status IN ('unshipped','shipped','received')")
    long countBuyerActiveOrders(@Param("username") String username);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.sellerUsername = :username AND o.status = 'unshipped'")
    long countSellerPendingOrders(@Param("username") String username);

    @Query("SELECT o FROM Order o WHERE o.sellerUsername = :username ORDER BY o.createdAt DESC")
    List<Order> findBySellerUsernameOrderByCreatedAtDesc(@Param("username") String username);

    @Query("SELECT o FROM Order o WHERE o.sellerUsername = :username AND o.status = :status ORDER BY o.createdAt DESC")
    List<Order> findBySellerUsernameAndStatusOrderByCreatedAtDesc(@Param("username") String username, @Param("status") String status);

    /** 销量统计：按商品聚合未取消订单数 */
    @Query("SELECT o.productId, COUNT(o) FROM Order o WHERE o.status <> 'cancelled' GROUP BY o.productId")
    List<Object[]> countSalesByProductId();
}
