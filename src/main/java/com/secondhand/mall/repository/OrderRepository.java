package com.secondhand.mall.repository;

import com.secondhand.mall.entity.Order;
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
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUsernameOrderByCreatedAtDesc(String username);
    List<Order> findByUsernameAndStatusOrderByCreatedAtDesc(String username, String status);
    List<Order> findByProductIdInOrderByCreatedAtDesc(List<Long> productIds);
    List<Order> findByProductIdInAndStatusOrderByCreatedAtDesc(List<Long> productIds, String status);

    @Modifying
    @Query("DELETE FROM Order o WHERE o.username = :username")
    void deleteByUsername(@Param("username") String username);

    /** 删除用户作为卖家的订单（用户清理时买家侧之外的补充） */
    @Modifying
    @Query("DELETE FROM Order o WHERE o.sellerUsername = :username")
    void deleteBySellerUsername(@Param("username") String username);

    /** 订单行悲观锁：状态流转/取消/退款用，防并发双写 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

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

    /** 卖家待结算订单（已下单未完结且未打款） */
    @Query("SELECT o FROM Order o WHERE o.sellerUsername = :username AND o.sellerPaid = false AND o.status NOT IN ('cancelled','completed','refunded')")
    List<Order> findSellerUnpaidOrders(@Param("username") String username);

    /** 自动确认：某状态且创建时间早于指定时刻 */
    List<Order> findByStatusAndCreatedAtBefore(String status, java.time.LocalDateTime before);

    /** 售后超时：卖家超过期限未处理的售后申请 */
    @Query("SELECT o FROM Order o WHERE o.status = 'after_sale' AND o.afterSaleAt IS NOT NULL AND o.afterSaleAt < :deadline")
    List<Order> findOverdueAfterSale(@Param("deadline") java.time.LocalDateTime deadline);

    /** 销量统计：按商品聚合未取消/未退款订单的件数（quantity 为空按 1 计） */
    @Query("SELECT o.productId, COALESCE(SUM(CASE WHEN o.quantity IS NULL THEN 1 ELSE o.quantity END), 0) FROM Order o WHERE o.status NOT IN ('cancelled','refunded') GROUP BY o.productId")
    List<Object[]> countSalesByProductId();

    List<Order> findAllByOrderByCreatedAtDesc();
}
