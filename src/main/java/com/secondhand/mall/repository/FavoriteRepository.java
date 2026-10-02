package com.secondhand.mall.repository;

import com.secondhand.mall.entity.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    List<Favorite> findByUsernameOrderByCreatedAtDesc(String username);
    Optional<Favorite> findByUsernameAndProductId(String username, Long productId);
    boolean existsByUsernameAndProductId(String username, Long productId);
    long countByUsername(String username);
    void deleteByUsername(String username);
}
