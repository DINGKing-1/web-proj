package com.secondhand.mall.repository;

import com.secondhand.mall.entity.QuickReply;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuickReplyRepository extends JpaRepository<QuickReply, Long> {
    List<QuickReply> findAllByOrderBySortOrderAscIdAsc();
}
