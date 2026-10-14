package com.secondhand.mall.service;

import com.secondhand.mall.exception.ForbiddenException;

import com.secondhand.mall.aspect.Audited;

import com.secondhand.mall.constant.OrderStatus;
import com.secondhand.mall.constant.ProductStatus;
import com.secondhand.mall.entity.Notification;
import com.secondhand.mall.entity.Order;
import com.secondhand.mall.entity.Report;
import com.secondhand.mall.entity.User;
import com.secondhand.mall.entity.WalletTransaction;
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
    private final StockService stockService;
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
                        ProductService productService, StockService stockService) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.stockService = stockService;
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
        // GMV = 已付款成交口径（不含待付款/取消/退款），金额按实付（扣优惠券）
        BigDecimal gmv = orderRepository.findAll().stream()
                .filter(o -> !"cancelled".equals(o.getStatus()) && !"refunded".equals(o.getStatus())
                        && !"pending_payment".equals(o.getStatus()))
                .map(o -> {
                    int qty = o.getQuantity() != null && o.getQuantity() > 0 ? o.getQuantity() : 1;
                    BigDecimal price = o.getProductPrice() != null ? o.getProductPrice() : BigDecimal.ZERO;
                    BigDecimal discount = o.getDiscountAmount() != null ? o.getDiscountAmount() : BigDecimal.ZERO;
                    BigDecimal net = price.multiply(BigDecimal.valueOf(qty)).subtract(discount);
                    return net.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : net;
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

    /** 软删除用户：踢下线 + 标记 deleted，数据保留可恢复。
     *  进行中订单一并强制结算（退款/回补库存），避免账号删除后钱货永久挂在平台托管。
     *  已发货/已收货/售后中订单禁止直接退款，存在时拦截注销，须先走售后或确认收货。 */
    @Transactional
    public void softDeleteUser(User user, String deletedBy) {
        assertNoFulfilledOrders(user.getUsername());
        settleActiveOrders(user.getUsername());
        // 结算会改余额；重载托管实体，避免用入参陈旧余额把退款覆盖回去
        User target = userRepository.findByUsername(user.getUsername())
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        target.setDeleted(true);
        target.setDeletedAt(LocalDateTime.now());
        target.setDeletedBy(deletedBy);
        target.setSessionId(null);
        // 其商品一并进回收站，前台不可见
        productRepository.softDeleteByUsername(target.getUsername());
        userRepository.save(target);
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
     * 清理用户个人业务数据（买家 + 卖家双侧），供管理员删用户与用户自助注销共用。
     * 先结算未完成订单（退款 + 恢复库存）；订单是交易凭证永久保留，仅脱敏被删用户隐私字段，
     * 绝不物理删除订单，否则会连带抹掉对手方的成交记录。
     * 商品软删除保持订单快照一致性。
     */
    @Transactional
    public void purgeUserData(String username) {
        // 已履约订单须先走售后/确认收货，禁止借删号直接退款
        assertNoFulfilledOrders(username);
        // 未完成订单：强制结算（退款 + 恢复库存），避免钱货两空
        settleActiveOrders(username);

        // 订单：永久保留作交易凭证，仅脱敏本用户的隐私字段
        anonymizeOrders(username);

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

    /** 被注销用户在订单上的展示名 */
    private static final String MASKED_USER = "已注销用户";

    /**
     * 订单脱敏：买家侧改用户名并清空收货人/手机/地址；卖家侧改卖家名。
     * 收货信息属买家隐私，仅在买家被删时清空，保留对手方（卖家）视角的成交数据。
     */
    private void anonymizeOrders(String username) {
        for (Order o : orderRepository.findByUsernameOrderByCreatedAtDesc(username)) {
            o.setUsername(MASKED_USER);
            o.setReceiverName(MASKED_USER);
            o.setReceiverPhone("");
            o.setReceiverAddress("");
            orderRepository.save(o);
        }
        for (Order o : orderRepository.findBySellerUsernameOrderByCreatedAtDesc(username)) {
            o.setSellerUsername(MASKED_USER);
            orderRepository.save(o);
        }
    }

    /**
     * 将用户相关的未完结订单强制结算：
     * pending_payment：从未扣款，只关单回补库存；
     * unshipped：全额退款 + 关单 + 回补库存；
     * shipped/received/after_sale/return_*：已履约，禁止直接退款（注销入口已拦截，此处再兜底跳过）；
     * completed/cancelled/refunded：终态跳过防重复结算。
     * 金额走实付口径（扣优惠券），并退回优惠券、写资金流水。
     * 每单走 findByIdForUpdate 行锁并复核状态，防与取消/支付/超时任务并发双结算。
     */
    private void settleActiveOrders(String username) {
        List<Order> buyerOrders = orderRepository.findByUsernameOrderByCreatedAtDesc(username);
        List<Order> sellerOrders = orderRepository.findBySellerUsernameOrderByCreatedAtDesc(username);
        Set<Long> seen = new HashSet<>();
        List<Long> ids = new ArrayList<>();
        for (Order o : buyerOrders) {
            if (o.getId() != null && seen.add(o.getId())) ids.add(o.getId());
        }
        for (Order o : sellerOrders) {
            if (o.getId() != null && seen.add(o.getId())) ids.add(o.getId());
        }

        for (Long id : ids) {
            // 行锁 + 状态复核：期间订单可能已被取消/支付/退款，必须以锁内状态为准
            Order order = orderRepository.findByIdForUpdate(id).orElse(null);
            if (order == null) continue;
            String status = order.getStatus();
            // 终态跳过：防二次退款/二次回补库存
            if (OrderStatus.COMPLETED.equals(status) || OrderStatus.CANCELLED.equals(status)
                    || OrderStatus.REFUNDED.equals(status)) {
                continue;
            }
            // 已履约（货已出库/售后中）：不得直接退款关单，留给售后或确认收货流程
            if (isFulfilledStatus(status)) {
                continue;
            }
            int qty = order.getQuantity() != null && order.getQuantity() > 0 ? order.getQuantity() : 1;
            // 实付 = 单价×数量 - 优惠券抵扣（与 payOrder/settleRefund 同口径）
            BigDecimal price = order.getProductPrice() != null ? order.getProductPrice() : BigDecimal.ZERO;
            BigDecimal discount = order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO;
            BigDecimal rawRefund = price.multiply(BigDecimal.valueOf(qty)).subtract(discount);
            final BigDecimal refund = rawRefund.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : rawRefund;
            // 仅「已付款未发货」可全额退款；待付款从未扣款
            boolean refundable = OrderStatus.UNSHIPPED.equals(status);

            if (refundable) {
                userRepository.findByUsernameForUpdate(order.getUsername()).ifPresent(buyer -> {
                    buyer.setBalance(buyer.getBalance().add(refund));
                    userRepository.save(buyer);
                });
                walletTransactionRepository.save(new WalletTransaction(
                        order.getUsername(), "refund", "注销结算退款 · " + order.getProductName(), refund));
                // 全额退款，把用掉的券退回去
                if (couponService != null) {
                    try {
                        couponService.releaseByOrder(order.getId());
                    } catch (Exception ignored) {
                        // 券服务异常不阻断注销结算
                    }
                }
                if (Boolean.TRUE.equals(order.getSellerPaid())
                        && order.getSellerUsername() != null && !order.getSellerUsername().isEmpty()) {
                    userRepository.findByUsernameForUpdate(order.getSellerUsername()).ifPresent(seller -> {
                        seller.setBalance(seller.getBalance().subtract(refund));
                        userRepository.save(seller);
                    });
                    walletTransactionRepository.save(new WalletTransaction(
                            order.getSellerUsername(), "refund",
                            "注销结算扣回 · " + order.getProductName(), refund.negate()));
                }
            }

            // 仅货在卖方侧（待付款/已付款未发货）才回补库存；
            // 已发货后货在买家或途上，回补会造出幽灵库存
            // StockService：悲观锁 + @Version，防与并发下单/取消丢失更新
            if (order.getProductId() != null && stockHeldBySeller(status)) {
                stockService.restore(order.getProductId(), qty);
            }
            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);
        }
    }

    /** 货权是否在卖方侧（与 OrderService 同口径）：未出库 */
    private static boolean stockHeldBySeller(String status) {
        return OrderStatus.PENDING_PAYMENT.equals(status) || OrderStatus.UNSHIPPED.equals(status);
    }

    /** 已履约未完结：货已出库或进入售后，禁止在注销/删号结算中直接退款关单 */
    private static boolean isFulfilledStatus(String status) {
        return OrderStatus.SHIPPED.equals(status)
                || OrderStatus.RECEIVED.equals(status)
                || OrderStatus.AFTER_SALE.equals(status)
                || OrderStatus.RETURN_SHIPPING.equals(status)
                || OrderStatus.RETURN_SENT.equals(status);
    }

    /**
     * 注销/删号前置校验：买家或卖家侧存在已履约订单则拦截。
     * 已发货/已收货/售后中不得直接退款，须先完成售后或确认收货。
     */
    public void assertNoFulfilledOrders(String username) {
        for (Order o : orderRepository.findByUsernameOrderByCreatedAtDesc(username)) {
            if (isFulfilledStatus(o.getStatus())) {
                throw new RuntimeException("存在已履约订单，请先完成售后或确认收货后再注销");
            }
        }
        for (Order o : orderRepository.findBySellerUsernameOrderByCreatedAtDesc(username)) {
            if (isFulfilledStatus(o.getStatus())) {
                throw new RuntimeException("存在已履约订单，请先完成售后或确认收货后再注销");
            }
        }
    }

    /** 可选注入：用于注销结算时退回优惠券 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private CouponService couponService;
}

