package com.secondhand.mall.service;

import com.secondhand.mall.entity.Comment;
import com.secondhand.mall.entity.Product;
import com.secondhand.mall.entity.User;
import com.secondhand.mall.repository.CommentRepository;
import com.secondhand.mall.repository.ProductRepository;
import com.secondhand.mall.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public CommentService(CommentRepository commentRepository, ProductRepository productRepository,
                          UserRepository userRepository) {
        this.commentRepository = commentRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    public List<Map<String, Object>> getCommentsByProduct(Long productId) {
        List<Comment> comments = commentRepository.findByProductIdOrderByCreatedAtDesc(productId);
        List<Map<String, Object>> result = new ArrayList<>();

        for (Comment c : comments) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", c.getId());
            item.put("userName", c.getUserName());

            String avatarUrl = "/user-avatar/default.jpg";
            String displayName = c.getUserName();
            Optional<User> commentUser = userRepository.findByUsername(c.getUserName());
            if (commentUser.isPresent()) {
                User u = commentUser.get();
                if (u.getAvatar() != null && !u.getAvatar().isEmpty()) {
                    avatarUrl = u.getAvatar();
                }
                if (u.getNickname() != null && !u.getNickname().isEmpty()) {
                    displayName = u.getNickname();
                }
            }
            item.put("displayName", displayName);
            item.put("content", c.getContent());
            item.put("createdAt", c.getCreatedAt());
            item.put("avatar", avatarUrl);
            result.add(item);
        }

        return result;
    }

    public Comment addComment(Long productId, String userName, String content) {
        if (userName == null || userName.isEmpty() || content == null || content.isEmpty()) {
            throw new RuntimeException("请填写完整信息");
        }

        if (content.length() > 500) {
            throw new RuntimeException("评论内容不能超过 500 字");
        }

        productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("商品不存在"));

        Comment comment = new Comment(productId, userName, content.trim());
        return commentRepository.save(comment);
    }

    public void deleteComment(Long productId, Long commentId, String currentUsername) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("评论不存在"));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("商品不存在"));

        // 评论者本人 或 商品拥有者可以删除
        if (!comment.getUserName().equals(currentUsername) &&
            !product.getSellerUsername().equals(currentUsername)) {
            throw new RuntimeException("无权删除此评论");
        }

        commentRepository.delete(comment);
    }

    public long getCommentCount() {
        return commentRepository.count();
    }
}
