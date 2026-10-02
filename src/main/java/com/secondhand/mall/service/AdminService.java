package com.secondhand.mall.service;

import com.secondhand.mall.entity.User;
import com.secondhand.mall.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final CommentRepository commentRepository;
    private final OrderRepository orderRepository;
    private final CartItemRepository cartItemRepository;
    private final FavoriteRepository favoriteRepository;
    private final AddressRepository addressRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomRepository chatRoomRepository;

    public AdminService(UserRepository userRepository, ProductRepository productRepository,
                        CommentRepository commentRepository, OrderRepository orderRepository,
                        CartItemRepository cartItemRepository, FavoriteRepository favoriteRepository,
                        AddressRepository addressRepository, ChatMessageRepository chatMessageRepository,
                        ChatRoomRepository chatRoomRepository) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.commentRepository = commentRepository;
        this.orderRepository = orderRepository;
        this.cartItemRepository = cartItemRepository;
        this.favoriteRepository = favoriteRepository;
        this.addressRepository = addressRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.chatRoomRepository = chatRoomRepository;
    }

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalProducts", productRepository.findByStatus("active").size());
        stats.put("totalUsers", userRepository.findAll().size());
        stats.put("totalComments", commentRepository.count());
        return stats;
    }

    public boolean isAdmin(String username) {
        return userRepository.findByUsername(username)
                .map(user -> "admin".equals(user.getRole()))
                .orElse(false);
    }

    /**
     * 获取所有用户列表，供 admin 使用
     */
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User loginAsAdmin(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("管理员账号不存在"));

        if (!"admin".equals(user.getRole())) {
            throw new RuntimeException("无管理员权限");
        }

        // 密码验证由 UserService 的 BCrypt 处理，这里只检查角色
        return user;
    }

    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        String username = user.getUsername();

        // 删除用户的订单
        orderRepository.deleteByUsername(username);

        // 删除用户的购物车
        cartItemRepository.deleteByUsername(username);

        // 删除用户的收藏
        favoriteRepository.deleteByUsername(username);

        // 删除用户的地址
        addressRepository.deleteByUsername(username);

        // 删除用户的评论
        commentRepository.deleteByUserName(username);

        // 删除用户的聊天记录
        chatMessageRepository.deleteBySenderUsername(username);

        // 删除用户的聊天室
        chatRoomRepository.deleteByUsername(username);

        // 删除用户
        userRepository.delete(user);
    }
}
