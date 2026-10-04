package com.secondhand.mall.service;

import com.secondhand.mall.exception.ForbiddenException;

import com.secondhand.mall.aspect.Audited;

import com.secondhand.mall.constant.ProductStatus;
import com.secondhand.mall.entity.Notification;
import com.secondhand.mall.entity.Order;
import com.secondhand.mall.entity.Report;
import com.secondhand.mall.entity.User;
import com.secondhand.mall.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    private final ReportRepository reportRepository;
    private final ReviewRepository reviewRepository;
    private final NotificationRepository notificationRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final ProductService productService;

    public AdminService(UserRepository userRepository, ProductRepository productRepository,
                        CommentRepository commentRepository, OrderRepository orderRepository,
                        CartItemRepository cartItemRepository, FavoriteRepository favoriteRepository,
                        AddressRepository addressRepository, ChatMessageRepository chatMessageRepository,
                        ChatRoomRepository chatRoomRepository, ReportRepository reportRepository,
                        ReviewRepository reviewRepository, NotificationRepository notificationRepository,
                        WalletTransactionRepository walletTransactionRepository,
                        ProductService productService) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.commentRepository = commentRepository;
        this.orderRepository = orderRepository;
        this.cartItemRepository = cartItemRepository;
        this.favoriteRepository = favoriteRepository;
        this.addressRepository = addressRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.chatRoomRepository = chatRoomRepository;
        this.reportRepository = reportRepository;
        this.reviewRepository = reviewRepository;
        this.notificationRepository = notificationRepository;
        this.walletTransactionRepository = walletTransactionRepository;
        this.productService = productService;
    }

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalProducts", productRepository.findByStatus(ProductStatus.ACTIVE).size());
        stats.put("totalUsers", userRepository.findAll().size());
        stats.put("totalComments", commentRepository.count());
        stats.put("totalOrders", orderRepository.count());
        stats.put("totalSales", orderRepository.countSalesByProductId().stream()
                .mapToLong(row -> ((Number) row[1]).longValue()).sum());
        BigDecimal gmv = orderRepository.findAll().stream()
                .filter(o -> !"cancelled".equals(o.getStatus()) && !"refunded".equals(o.getStatus()))
                .map(o -> {
                    int qty = o.getQuantity() != null && o.getQuantity() > 0 ? o.getQuantity() : 1;
                    BigDecimal price = o.getProductPrice() != null ? o.getProductPrice() : BigDecimal.ZERO;
                    return price.multiply(BigDecimal.valueOf(qty));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        stats.put("gmv", gmv);
        stats.put("pendingReports", reportRepository.countByStatus("pending"));
        stats.put("pendingProducts", productRepository.findByStatus(ProductStatus.PENDING).size());
        return stats;
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<Report> getAllReports() {
        return reportRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * 处理举报。status: resolved 采纳 / rejected 不采纳；
     * action 仅在 resolved 下生效：offline 下架商品、delete 商品进回收站、none 只改状态。
     * 处理人、时间、动作落库，审计与页面都能追溯。
     */
    @Transactional
    @Audited(action = "处理举报", targetType = "report")
    public Report resolveReport(Long id, String status, String action, String handler) {
        if (!Set.of("pending", "resolved", "rejected").contains(status)) {
            throw new RuntimeException("非法的处理状");
        }
        String act = action == null || action.isBlank() ? "none" : action;
        if (!Set.of("none", "offline", "delete").contains(act)) {
            throw new RuntimeException("非法的处置动作");
        }
        Report r = reportRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("举报不存在"));
        r.setStatus(status);
        r.setAction(act);
        r.setHandledBy(handler);
        r.setHandledAt(java.time.LocalDateTime.now());

        // 采纳举报时联动处置商品，避免「处理了举报但商品还在挂」的假闭环
        String actionText = "";
        if ("resolved".equals(status) && !"none".equals(act)) {
            if ("offline".equals(act)) {
                productService.adminOfflineProduct(r.getProductId());
                actionText = "，该商品已下架";
            } else if ("delete".equals(act)) {
                productService.adminDeleteProduct(r.getProductId());
                actionText = "，该商品已移入回收站";
            }
        }
        Report saved = reportRepository.save(r);

        if (!"pending".equals(status) && r.getReporterUsername() != null && !r.getReporterUsername().isBlank()) {
            String title = "resolved".equals(status) ? "举报已处理" : "举报未采纳";
            String content = "resolved".equals(status)
                ? "您举报的商品「" + r.getProductName() + "」已核实处理" + actionText + "，感谢反馈"
                : "您举报的商品「" + r.getProductName() + "」经核实暂不违规，如有补充证据可再次反馈";
            Notification n = new Notification();
            n.setUsername(r.getReporterUsername());
            n.setType("report");
            n.setTitle(title);
            n.setContent(content);
            n.setRefType("report");
            n.setRefId(String.valueOf(r.getId()));
            notificationRepository.save(n);
        }
        return saved;
    }

    public boolean isAdmin(String username) {
        return userRepository.findByUsername(username)
                .map(user -> "admin".equals(user.getRole()))
                .orElse(false);
    }

    /** 封禁 / 解封用户 */
    @Transactional
    @Audited(action = "封禁/解封用户", targetType = "user")
    public User setBanned(Long userId, boolean banned) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存"));
        if ("admin".equals(user.getRole())) {
            throw new RuntimeException("不能封禁管理");
        }
        user.setBanned(banned);
        if (banned) {
            // 封禁后踢下线
            user.setSessionId(null);
        }
        return userRepository.save(user);
    }

    /**
     * 获取所有有效用户列表，供 admin 使用（不含回收站。
     */
    public List<User> getAllUsers() {
        return userRepository.findActiveUsers();
    }

    /** 回收站：已注销/已删除用户。
     */
    public List<User> getDeletedUsers() {
        return userRepository.findDeletedUsers();
    }

    public User loginAsAdmin(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("管理员账号不存在"));

        if (!"admin".equals(user.getRole())) {
            throw new com.secondhand.mall.exception.ForbiddenException("无管理员权限");
        }

        // 密码验证由 UserService 的 BCrypt 处理，这里只检查角色
        return user;
    }

    /**
     * 删除用户（软删除进回收站，保留业务数据以便追回）。
     * 彻底清理请用 purgeUser。
     */
    @Transactional
    @Audited(action = "删除用户到回收站", targetType = "user")
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        if ("admin".equals(user.getRole())) {
            throw new RuntimeException("不能删除管理员");
        }
        softDeleteUser(user, "admin");
    }

    /** 软删除用户：踢下线 + 标记 deleted，数据保留可恢复 */
    @Transactional
    public void softDeleteUser(User user, String deletedBy) {
        user.setDeleted(true);
        user.setDeletedAt(LocalDateTime.now());
        user.setDeletedBy(deletedBy);
        user.setSessionId(null);
        // 其商品一并进回收站，前台不可见
        productRepository.softDeleteByUsername(user.getUsername());
        userRepository.save(user);
    }

    /** 从回收站恢复用户（一并恢复其名下已删除商品） */
    @Transactional
    @Audited(action = "恢复用户", targetType = "user")
    public User restoreUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存"));
        user.setDeleted(false);
        user.setDeletedAt(null);
        user.setDeletedBy(null);
        User saved = userRepository.save(user);
        productRepository.findByStatusAndSellerUsername(ProductStatus.DELETED, user.getUsername())
                .forEach(p -> {
                    p.setStatus(ProductStatus.ACTIVE);
                    productRepository.save(p);
                });
        return saved;
    }

    /** 彻底删除用户（清理全部业务数据，不可恢复）。
     */
    @Transactional
    @Audited(action = "彻底删除用户", targetType = "user")
    public void purgeUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存"));
        if ("admin".equals(user.getRole())) {
            throw new RuntimeException("不能删除管理");
        }
        purgeUserData(user.getUsername());
        userRepository.delete(user);
    }

    /**
     * 清理用户全部业务数据（买家 + 卖家双侧），供管理员删用户与用户自助注销共用。
     * 先结算未完成订单（退款 + 恢复库存），再删历史订单；商品软删除保持订单快照一致性。
     */
    @Transactional
    public void purgeUserData(String username) {
        // 未完成订单：强制结算（退款 + 恢复库存），避免钱货两空
        settleActiveOrders(username);

        // 订单：买家侧 + 卖家侧（已完成/已取消的历史订单直接删）
        orderRepository.deleteByUsername(username);
        orderRepository.deleteBySellerUsername(username);

        // 商品：软删除
        productRepository.softDeleteByUsername(username);

        // 购物车 / 收藏 / 地址 / 评论
        cartItemRepository.deleteByUsername(username);
        favoriteRepository.deleteByUsername(username);
        addressRepository.deleteByUsername(username);
        commentRepository.deleteByUserName(username);

        // 聊天记录 + 聊天室
        chatMessageRepository.deleteBySenderUsername(username);
        chatRoomRepository.deleteByUsername(username);

        // 评价 + 站内通知 + 钱包流水
        reviewRepository.deleteByUsername(username);
        notificationRepository.deleteByUsername(username);
        walletTransactionRepository.deleteByUsername(username);
    }

    /**
     * 将用户相关的未完成订单（unshipped/shipped/received）强制结算：
     * 买家退款、卖家扣回、商品库存恢复，然后订单标记 cancelled（随后会被删除）。
     */
    private void settleActiveOrders(String username) {
        List<Order> buyerOrders = orderRepository.findByUsernameOrderByCreatedAtDesc(username);
        List<Order> sellerOrders = orderRepository.findBySellerUsernameOrderByCreatedAtDesc(username);
        Set<Long> seen = new HashSet<>();
        List<Order> all = new ArrayList<>();
        for (Order o : buyerOrders) {
            if (o.getId() != null && seen.add(o.getId())) all.add(o);
        }
        for (Order o : sellerOrders) {
            if (o.getId() != null && seen.add(o.getId())) all.add(o);
        }

        for (Order order : all) {
            String status = order.getStatus();
            if ("completed".equals(status) || "cancelled".equals(status)) {
                continue;
            }
            // 资金托管：买家退款；仅当已打款给卖家时才扣回
            int qty = order.getQuantity() != null && order.getQuantity() > 0 ? order.getQuantity() : 1;
            BigDecimal price = order.getProductPrice() != null ? order.getProductPrice() : BigDecimal.ZERO;
            BigDecimal refund = price.multiply(BigDecimal.valueOf(qty));

            userRepository.findByUsername(order.getUsername()).ifPresent(buyer -> {
                buyer.setBalance(buyer.getBalance().add(refund));
                userRepository.save(buyer);
            });
            if (Boolean.TRUE.equals(order.getSellerPaid())
                    && order.getSellerUsername() != null && !order.getSellerUsername().isEmpty()) {
                userRepository.findByUsername(order.getSellerUsername()).ifPresent(seller -> {
                    seller.setBalance(seller.getBalance().subtract(refund));
                    userRepository.save(seller);
                });
            }
            if (order.getProductId() != null) {
                productRepository.findById(order.getProductId()).ifPresent(product -> {
                    int restored = product.getQuantity() != null ? product.getQuantity() : 0;
                    product.setQuantity(restored + qty);
                    if (ProductStatus.SOLD.equals(product.getStatus())) {
                        product.setStatus(ProductStatus.ACTIVE);
                    }
                    productRepository.save(product);
                });
            }
            order.setStatus("cancelled");
            orderRepository.save(order);
        }
    }
}

