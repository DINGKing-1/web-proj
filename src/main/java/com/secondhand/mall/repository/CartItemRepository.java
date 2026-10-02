package com.secondhand.mall.repository;

import com.secondhand.mall.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findByUsernameOrderByCreatedAtDesc(String username);
    int countByUsername(String username);
    boolean existsByUsernameAndProductId(String username, Long productId);

    @Modifying
    @Transactional
    @Query("DELETE FROM CartItem c WHERE c.username = :username")
    void deleteByUsername(@Param("username") String username);
}
