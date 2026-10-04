package com.secondhand.mall.repository;

import com.secondhand.mall.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByProductIdOrderByCreatedAtDesc(Long productId);
    long count();
    void deleteByUserName(String userName);

    /** 同一用户对同一商品的相同内容已存在（防重复刷评论） */
    boolean existsByProductIdAndUserNameAndContent(Long productId, String userName, String content);
}