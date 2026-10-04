package com.secondhand.mall.repository;

import com.secondhand.mall.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {
    List<Report> findAllByOrderByCreatedAtDesc();
    List<Report> findByStatusOrderByCreatedAtDesc(String status);
    long countByStatus(String status);
    boolean existsByReporterUsernameAndProductIdAndStatus(String reporterUsername, Long productId, String status);
}
