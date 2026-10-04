package com.secondhand.mall.repository;

import com.secondhand.mall.entity.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {
    List<Announcement> findByEnabledTrueOrderBySortOrderAscCreatedAtDesc();
    List<Announcement> findAllByOrderBySortOrderAscCreatedAtDesc();
}
