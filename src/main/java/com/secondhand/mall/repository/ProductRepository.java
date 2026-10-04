package com.secondhand.mall.repository;

import com.secondhand.mall.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    /** 用户清理：软删除其全部商品（保留订单快照一致性） */
    @Modifying
    @Query("UPDATE Product p SET p.status = 'deleted' WHERE p.sellerUsername = :username AND p.status <> 'deleted'")
    int softDeleteByUsername(@Param("username") String username);

    List<Product> findByCategory(String category);
    long countByCategory(String category);

    /** 分类改名时批量改写存量商品 */
    @Modifying
    @Transactional
    @Query("UPDATE Product p SET p.category = :to WHERE p.category = :from")
    int renameCategory(@Param("from") String from, @Param("to") String to);

    List<Product> findByStatus(String status);
    List<Product> findByNameContaining(String name);
    List<Product> findBySellerUsername(String sellerUsername);
    List<Product> findByStatusAndSellerUsername(String status, String sellerUsername);
    List<Product> findByStatusAndSellerUsernameOrderByCreatedAtDesc(String status, String sellerUsername);

    List<Product> findByStatusAndCategoryOrderByCreatedAtDesc(String status, String category);

    @Query("SELECT p FROM Product p WHERE p.status = :status AND p.category = :category AND p.secondHandPrice >= :minPrice AND p.secondHandPrice <= :maxPrice")
    List<Product> findByCategoryAndPriceRange(@Param("status") String status, @Param("category") String category, @Param("minPrice") java.math.BigDecimal minPrice, @Param("maxPrice") java.math.BigDecimal maxPrice);

    @Query("SELECT p FROM Product p WHERE p.status = :status AND p.secondHandPrice >= :minPrice AND p.secondHandPrice <= :maxPrice")
    List<Product> findByPriceRange(@Param("status") String status, @Param("minPrice") java.math.BigDecimal minPrice, @Param("maxPrice") java.math.BigDecimal maxPrice);

    @Query("SELECT p FROM Product p WHERE p.status = :status AND p.category = :category AND p.itemCondition = :condition")
    List<Product> findByCategoryAndCondition(@Param("status") String status, @Param("category") String category, @Param("condition") String condition);

    @Query("SELECT p FROM Product p WHERE p.status = :status AND p.itemCondition = :condition")
    List<Product> findByCondition(@Param("status") String status, @Param("condition") String condition);

    @Query("SELECT p FROM Product p WHERE p.status = :status AND p.category = :category AND p.secondHandPrice >= :minPrice AND p.secondHandPrice <= :maxPrice AND p.itemCondition = :condition")
    List<Product> findByCategoryAndPriceRangeAndCondition(@Param("status") String status, @Param("category") String category, @Param("minPrice") java.math.BigDecimal minPrice, @Param("maxPrice") java.math.BigDecimal maxPrice, @Param("condition") String condition);

    @Query("SELECT p FROM Product p WHERE p.status = :status AND p.secondHandPrice >= :minPrice AND p.secondHandPrice <= :maxPrice AND p.itemCondition = :condition")
    List<Product> findByPriceRangeAndCondition(@Param("status") String status, @Param("minPrice") java.math.BigDecimal minPrice, @Param("maxPrice") java.math.BigDecimal maxPrice, @Param("condition") String condition);

    @Query("SELECT p FROM Product p WHERE p.status = 'active' AND p.name LIKE %:name%")
    List<Product> searchActiveByName(@Param("name") String name);

    @Query("SELECT p FROM Product p WHERE p.status = 'active' AND (p.name LIKE %:keyword% OR p.description LIKE %:keyword%)")
    List<Product> searchActiveByKeyword(@Param("keyword") String keyword);

    @Query("SELECT p FROM Product p WHERE p.status = 'active' AND (p.name LIKE %:keyword% OR p.description LIKE %:keyword%) AND p.category = :category")
    List<Product> searchByKeywordAndCategory(@Param("keyword") String keyword, @Param("category") String category);

    @Query("SELECT p FROM Product p WHERE p.status = 'active' AND (p.name LIKE %:keyword% OR p.description LIKE %:keyword%) AND p.secondHandPrice >= :minPrice AND p.secondHandPrice <= :maxPrice")
    List<Product> searchByKeywordAndPriceRange(@Param("keyword") String keyword, @Param("minPrice") java.math.BigDecimal minPrice, @Param("maxPrice") java.math.BigDecimal maxPrice);

    @Query("SELECT p FROM Product p WHERE p.status = 'active' AND (p.name LIKE %:keyword% OR p.description LIKE %:keyword%) AND p.itemCondition = :condition")
    List<Product> searchByKeywordAndCondition(@Param("keyword") String keyword, @Param("condition") String condition);

    @Query("SELECT p FROM Product p WHERE p.status = 'active' AND (p.name LIKE %:keyword% OR p.description LIKE %:keyword%) AND p.category = :category AND p.secondHandPrice >= :minPrice AND p.secondHandPrice <= :maxPrice")
    List<Product> searchByKeywordCategoryAndPriceRange(@Param("keyword") String keyword, @Param("category") String category, @Param("minPrice") java.math.BigDecimal minPrice, @Param("maxPrice") java.math.BigDecimal maxPrice);

    @Query("SELECT p FROM Product p WHERE p.status = 'active' AND (p.name LIKE %:keyword% OR p.description LIKE %:keyword%) AND p.category = :category AND p.itemCondition = :condition")
    List<Product> searchByKeywordCategoryAndCondition(@Param("keyword") String keyword, @Param("category") String category, @Param("condition") String condition);

    @Query("SELECT p FROM Product p WHERE p.status = 'active' AND (p.name LIKE %:keyword% OR p.description LIKE %:keyword%) AND p.secondHandPrice >= :minPrice AND p.secondHandPrice <= :maxPrice AND p.itemCondition = :condition")
    List<Product> searchByKeywordPriceRangeAndCondition(@Param("keyword") String keyword, @Param("minPrice") java.math.BigDecimal minPrice, @Param("maxPrice") java.math.BigDecimal maxPrice, @Param("condition") String condition);

    @Query("SELECT p FROM Product p WHERE p.status = 'active' AND (p.name LIKE %:keyword% OR p.description LIKE %:keyword%) AND p.category = :category AND p.secondHandPrice >= :minPrice AND p.secondHandPrice <= :maxPrice AND p.itemCondition = :condition")
    List<Product> searchByKeywordCategoryPriceRangeAndCondition(@Param("keyword") String keyword, @Param("category") String category, @Param("minPrice") java.math.BigDecimal minPrice, @Param("maxPrice") java.math.BigDecimal maxPrice, @Param("condition") String condition);

    @Query("SELECT p FROM Product p WHERE p.status = 'active' ORDER BY p.createdAt DESC")
    List<Product> findAllActiveOrderByNewest();

    @Query("SELECT p FROM Product p WHERE p.status = 'active' ORDER BY p.secondHandPrice ASC")
    List<Product> findAllActiveOrderByPriceAsc();

    @Query("SELECT p FROM Product p WHERE p.status = 'active' ORDER BY p.secondHandPrice DESC")
    List<Product> findAllActiveOrderByPriceDesc();

    @Query("SELECT p FROM Product p WHERE p.status = 'active'")
    Page<Product> findAllActive(Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.status = 'active' AND p.name LIKE %:name%")
    Page<Product> searchActiveByName(@Param("name") String name, Pageable pageable);

    /**
     * 统一真分页检索：keyword / category / seller / 价格区间 / 成色 均为可选。
     * 空字符串按「未筛选」处理；排序交给 Pageable（newest / price-asc / price-desc）。
     */
    @Query("""
            SELECT p FROM Product p
            WHERE p.status = 'active'
              AND (:keyword IS NULL OR :keyword = ''
                   OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:category IS NULL OR :category = '' OR p.category = :category)
              AND (:seller IS NULL OR :seller = '' OR p.sellerUsername = :seller)
              AND (:minPrice IS NULL OR p.secondHandPrice >= :minPrice)
              AND (:maxPrice IS NULL OR p.secondHandPrice <= :maxPrice)
              AND (:condition IS NULL OR :condition = '' OR p.itemCondition = :condition)
            """)
    Page<Product> searchActivePaged(
            @Param("keyword") String keyword,
            @Param("category") String category,
            @Param("seller") String seller,
            @Param("minPrice") java.math.BigDecimal minPrice,
            @Param("maxPrice") java.math.BigDecimal maxPrice,
            @Param("condition") String condition,
            Pageable pageable);

    /** 销量降序真分页（销量=未取消/未退款订单件数） */
    @Query("""
            SELECT p FROM Product p
            WHERE p.status = 'active'
              AND (:keyword IS NULL OR :keyword = ''
                   OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:category IS NULL OR :category = '' OR p.category = :category)
              AND (:seller IS NULL OR :seller = '' OR p.sellerUsername = :seller)
              AND (:minPrice IS NULL OR p.secondHandPrice >= :minPrice)
              AND (:maxPrice IS NULL OR p.secondHandPrice <= :maxPrice)
              AND (:condition IS NULL OR :condition = '' OR p.itemCondition = :condition)
            ORDER BY (SELECT COALESCE(SUM(CASE WHEN o.quantity IS NULL THEN 1 ELSE o.quantity END), 0)
                      FROM Order o
                      WHERE o.productId = p.id AND o.status NOT IN ('cancelled', 'refunded')) DESC,
                     p.createdAt DESC
            """)
    Page<Product> searchActivePagedBySales(
            @Param("keyword") String keyword,
            @Param("category") String category,
            @Param("seller") String seller,
            @Param("minPrice") java.math.BigDecimal minPrice,
            @Param("maxPrice") java.math.BigDecimal maxPrice,
            @Param("condition") String condition,
            Pageable pageable);
}