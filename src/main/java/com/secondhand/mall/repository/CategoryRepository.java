package com.secondhand.mall.repository;

import com.secondhand.mall.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findAllByOrderBySortAsc();
    List<Category> findByEnabledTrueOrderBySortAsc();
    Optional<Category> findByName(String name);
    boolean existsByName(String name);
}
