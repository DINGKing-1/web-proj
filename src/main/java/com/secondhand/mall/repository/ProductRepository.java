package com.secondhand.mall.repository;

import com.secondhand.mall.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    List<Product> findByCategory(String category);
    List<Product> findByStatus(String status);
    List<Product> findByNameContaining(String name);
    List<Product> findBySellerUsername(String sellerUsername);

    List<Product> findByStatusAndCategoryOrderByCreatedAtDesc(String status, String category);

    @Query("SELECT p FROM Product p WHERE p.status = :status AND p.category = :category AND p.secondHandPrice >= :minPrice AND p.secondHandPrice <= :maxPrice")
    List<Product> findByCategoryAndPriceRange(@Param("status") String status, @Param("category") String category, @Param("minPrice") double minPrice, @Param("maxPrice") double maxPrice);

    @Query("SELECT p FROM Product p WHERE p.status = :status AND p.secondHandPrice >= :minPrice AND p.secondHandPrice <= :maxPrice")
    List<Product> findByPriceRange(@Param("status") String status, @Param("minPrice") double minPrice, @Param("maxPrice") double maxPrice);

    @Query("SELECT p FROM Product p WHERE p.status = :status AND p.category = :category AND p.itemCondition = :condition")
    List<Product> findByCategoryAndCondition(@Param("status") String status, @Param("category") String category, @Param("condition") String condition);

    @Query("SELECT p FROM Product p WHERE p.status = :status AND p.itemCondition = :condition")
    List<Product> findByCondition(@Param("status") String status, @Param("condition") String condition);

    @Query("SELECT p FROM Product p WHERE p.status = :status AND p.category = :category AND p.secondHandPrice >= :minPrice AND p.secondHandPrice <= :maxPrice AND p.itemCondition = :condition")
    List<Product> findByCategoryAndPriceRangeAndCondition(@Param("status") String status, @Param("category") String category, @Param("minPrice") double minPrice, @Param("maxPrice") double maxPrice, @Param("condition") String condition);

    @Query("SELECT p FROM Product p WHERE p.status = :status AND p.secondHandPrice >= :minPrice AND p.secondHandPrice <= :maxPrice AND p.itemCondition = :condition")
    List<Product> findByPriceRangeAndCondition(@Param("status") String status, @Param("minPrice") double minPrice, @Param("maxPrice") double maxPrice, @Param("condition") String condition);
}