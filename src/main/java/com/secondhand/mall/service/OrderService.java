package com.secondhand.mall.service;

import com.secondhand.mall.aspect.Audited;

import com.secondhand.mall.constant.OrderStatus;
import com.secondhand.mall.constant.ProductStatus;
import com.secondhand.mall.entity.CartItem;
import com.secondhand.mall.entity.Order;
import com.secondhand.mall.entity.Product;
import com.secondhand.mall.entity.User;
import com.secondhand.mall.repository.CartItemRepository;
import com.secondhand.mall.repository.OrderRepository;
import com.secondhand.mall.repository.ProductRepository;
import com.secondhand.mall.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class OrderService {

    private static final Map<String, List<String>> VALID_TRANSITIONS = Map.of(
        OrderStatus.PENDING_PAYMENT, List.of(OrderStatus.UNSHIPPED, OrderStatus.CANCELLED),
        OrderStatus.UNSHIPPED, List.of(OrderStatus.SHIPPED),
        OrderStatus.SHIPPED,   List.of(OrderStatus.RECEIVED, OrderStatus.SHIPPED),
        OrderStatus.RECEIVED,  List.of(OrderStatus.COMPLETED),
        // 售后链路：申请 → 卖家同意退货/拒绝 → 买家寄回 → 卖家确认收货退款
        OrderStatus.AFTER_SALE, List.of(OrderStatus.CANCELLED, OrderStatus.SHIPPED,
                                        OrderStatus.RECEIVED, OrderStatus.RETURN_SHIPPING),
        OrderStatus.RETURN_SHIPPING, List.of(OrderStatus.RETURN_SENT, OrderStatus.CANCELLED, OrderStatus.RECEIVED),
        OrderStatus.RETURN_SENT, List.of(OrderStatus.REFUNDED),
        OrderStatus.REFUNDED, List.of()
    );

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CartItemRepository cartItemRepository;
    private final WalletService walletService;
    private final CouponService couponService;

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository,
                        UserRepository userRepository, CartItemRepository cartItemRepository,
                        WalletService walletService, CouponService couponService) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.cartItemRepository = cartItemRepository;
        this.walletService = walletService;
        this.couponService = couponService;
    }

    /** 实付金额 = 单价×数量 - 优惠券抵扣；支付、退款、结算统一走这里 */
    private BigDecimal paidTotal(Order order) {
        int qty = order.getQuantity() != null && order.getQuantity() > 0 ? order.getQuantity() : 1;
        BigDecimal gross = order.getProductPrice().multiply(BigDecimal.valueOf(qty));
        BigDecimal discount = order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO;
        BigDecimal net = gross.subtract(discount);
        return net.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : net;
    }

    /** 单条下单请求（批量结算的元素） */
    public record OrderItemRequest(Long productId, BigDecimal productPrice, String productName,
                                   String category, String imageUrl, int quantity) {}

    /**
     * 批量下单：同一事务内全部成功或全部失败。
     * cartItemIds 非空时，下单成功后一并清理购物车项。
     */
    @Transactional
    public List<Order> createOrders(String buyerUsername, List<OrderItemRequest> items,
                                    String receiverName, String receiverPhone, String receiverAddress,
                                    String paymentMethod, List<Long> cartItemIds) {
        if (items == null || items.isEmpty()) {
            throw new RuntimeException("没有可购买的商品");
        }

        List<Order> orders = new ArrayList<>(items.size());
        // 按 productId 排序加锁，避免并发批量下单反序加锁死锁
        List<OrderItemRequest> sorted = items.stream()
                .sorted(java.util.Comparator.comparing(OrderItemRequest::productId,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .toList();
        for (OrderItemRequest item : sorted) {
            orders.add(createOrder(
                buyerUsername, item.productId(), item.productPrice(), item.productName(),
                item.category(), item.imageUrl(), receiverName, receiverPhone, receiverAddress,
                paymentMethod, item.quantity() > 0 ? item.quantity() : 1
            ));
        }

        if (cartItemIds != null && !cartItemIds.isEmpty()) {
            for (Long cartItemId : cartItemIds) {
                if (cartItemId == null) continue;
                cartItemRepository.findById(cartItemId).ifPresent((CartItem cartItem) -> {
                    if (buyerUsername.equals(cartItem.getUsername())) {
                        cartItemRepository.delete(cartItem);
                    }
                });
            }
        }

        return orders;
    }

    @Transactional
    public Order createOrder(String buyerUsername, Long productId, BigDecimal orderPrice,
                             String productName, String category, String imageUrl,
                             String receiverName, String receiverPhone, String receiverAddress,
                             String paymentMethod) {
        return createOrder(buyerUsername, productId, orderPrice, productName, category, imageUrl,
                receiverName, receiverPhone, receiverAddress, paymentMethod, 1);
    }

    @Transactional
    public Order createOrder(String buyerUsername, Long productId, BigDecimal orderPrice,
                             String productName, String category, String imageUrl,
                             String receiverName, String receiverPhone, String receiverAddress,
                             String paymentMethod, int quantity) {
        return createOrder(buyerUsername, productId, orderPrice, productName, category, imageUrl,
                receiverName, receiverPhone, receiverAddress, paymentMethod, quantity, null);
    }

    /**
     * 议价下单：以协商价成交，订单金额、后续支付/退款/结算均按协商价，
     * 不再与挂牌价做一致性校验（否则接受出价必然失败）。
     */
    @Transactional
    public Order createNegotiatedOrder(String buyerUsername, Long productId, BigDecimal negotiatedPrice,
                                       String productName, String category, String imageUrl,
                                       String receiverName, String receiverPhone, String receiverAddress,
                                       String paymentMethod, int quantity) {
        return createOrder(buyerUsername, productId, null, productName, category, imageUrl,
                receiverName, receiverPhone, receiverAddress, paymentMethod, quantity, negotiatedPrice);
    }

    private Order createOrder(String buyerUsername, Long productId, BigDecimal orderPrice,
                              String productName, String category, String imageUrl,
                              String receiverName, String receiverPhone, String receiverAddress,
                              String paymentMethod, int quantity, BigDecimal negotiatedPrice) {
        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new RuntimeException("商品不存在"));

        // 只有在售商品可下单：待审/驳回/下架/售出/回收站一律拦下
        if (!ProductStatus.ACTIVE.equals(product.getStatus())) {
            throw new RuntimeException("该商品已被购买或已下架");
        }

        int qty = product.getQuantity() != null ? product.getQuantity() : 1;
        if (qty <= 0) {
            throw new RuntimeException("该商品库存不足");
        }

        int buyQty = Math.max(1, quantity);
        // 防超卖：购买数量不得超过库存
        if (buyQty > qty) {
            throw new RuntimeException("库存不足，当前仅剩 " + qty + " 件");
        }

        if (buyerUsername.equals(product.getSellerUsername())) {
            throw new RuntimeException("不能购买自己发布的商品");
        }

        // 收货信息必填
        if (isBlank(receiverName) || isBlank(receiverPhone) || isBlank(receiverAddress)) {
            throw new RuntimeException("请填写完整的收货信息");
        }

        BigDecimal unitPrice = product.getSecondHandPrice();
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("商品价格异常，请联系卖家修改后重试");
        }

        // 成交单价：议价单用协商价，普通单用挂牌价
        BigDecimal dealPrice;
        if (negotiatedPrice != null) {
            if (negotiatedPrice.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("议价金额无效");
            }
            if (negotiatedPrice.compareTo(unitPrice) > 0) {
                throw new RuntimeException("议价不得高于当前售价");
            }
            dealPrice = negotiatedPrice;
        } else {
            // 下单金额 = 单价 × 数量；客户端传了价格则比对（防篡改），未传则以服务端为准
            BigDecimal expected = unitPrice.multiply(BigDecimal.valueOf(buyQty));
            if (orderPrice != null
                    && orderPrice.subtract(expected).abs().compareTo(new BigDecimal("0.01")) > 0
                    && orderPrice.subtract(unitPrice).abs().compareTo(new BigDecimal("0.01")) > 0) {
                throw new RuntimeException("价格不一致，请刷新后重试");
            }
            dealPrice = unitPrice;
        }

        // 库存递减（下单即占库存，防止待付款期间被抢走）
        if (qty <= buyQty) {
            product.setQuantity(0);
            product.setStatus(ProductStatus.SOLD);
        } else {
            product.setQuantity(qty - buyQty);
        }
        productRepository.save(product);

        // 创建待付款订单：扣款发生在 payOrder，超时由定时任务关单并回补库存
        Order order = new Order();
        order.setOrderNumber(generateOrderNumber());
        order.setUsername(buyerUsername);
        order.setProductId(productId);
        order.setSellerUsername(product.getSellerUsername());
        // 订单快照一律以服务端商品为准，忽略客户端传入的名称/图片/分类（防钓鱼伪造）
        order.setProductName(product.getName());
        order.setProductPrice(dealPrice);
        order.setQuantity(buyQty);
        order.setCategory(product.getCategory());
        order.setImageUrl(firstImageOf(product.getImageUrl()));
        order.setReceiverName(receiverName.trim());
        order.setReceiverPhone(receiverPhone.trim());
        order.setReceiverAddress(receiverAddress.trim());
        order.setPaymentMethod(paymentMethod);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setSellerPaid(false);

        return orderRepository.save(order);
    }

    /**
     * 余额支付待付款订单：扣款后进入待发货。
     * 资金托管：卖家待订单完成后再打款（sellerPaid=false）。
     */
    @Transactional
    public Order payOrder(Long id, String currentUsername) {
        return payOrder(id, currentUsername, null);
    }

    /**
     * @param userCouponId 可选，支付时核销的优惠券（我的券 id）
     */
    @Transactional
    public Order payOrder(Long id, String currentUsername, Long userCouponId) {
        Order order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("订单不存在"));
        if (!order.getUsername().equals(currentUsername)) {
            throw new RuntimeException("只有买家可以支付此订单");
        }
        if (!OrderStatus.PENDING_PAYMENT.equals(order.getStatus())) {
            throw new RuntimeException("订单不在待付款状态");
        }

        int payQty = order.getQuantity() != null && order.getQuantity() > 0 ? order.getQuantity() : 1;
        BigDecimal gross = order.getProductPrice().multiply(BigDecimal.valueOf(payQty));

        // 先核销优惠券再算应付：券一旦核销就绑到本单，退款/取消时由 releaseByOrder 退回
        BigDecimal discount = couponService.consume(currentUsername, userCouponId, id, gross);
        BigDecimal total = gross.subtract(discount);
        if (total.compareTo(BigDecimal.ZERO) < 0) total = BigDecimal.ZERO;

        User buyer = userRepository.findByUsernameForUpdate(order.getUsername())
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        if (buyer.getBalance().compareTo(total) < 0) {
            throw new RuntimeException("余额不足，当前余额: ¥" + buyer.getBalance().toPlainString());
        }

        buyer.setBalance(buyer.getBalance().subtract(total));
        userRepository.save(buyer);
        // 账本与余额对齐：buy 记原价、coupon 记抵扣，净额 = 实付 total
        BigDecimal grossForLog = order.getProductPrice().multiply(BigDecimal.valueOf(payQty));
        walletService.add(order.getUsername(), "buy", "购买 · " + order.getProductName(), grossForLog.negate());
        if (discount.compareTo(BigDecimal.ZERO) > 0) {
            walletService.add(order.getUsername(), "coupon",
                    "优惠券抵扣 · " + order.getProductName(), discount);
        }

        order.setDiscountAmount(discount);
        order.setStatus(OrderStatus.UNSHIPPED);
        return orderRepository.save(order);
    }

    /**
     * 订单只需一张封面图：多图逗号串只取首图，
     * 避免历史 VARCHAR 列长度不足导致 Data truncation。
     */
    private static String firstImageOf(String imageUrl) {
        if (isBlank(imageUrl)) return "";
        String first = imageUrl.split(",")[0].trim();
        // 兜底截断，防止异常超长 URL
        return first.length() > 255 ? first.substring(0, 255) : first;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    @Transactional
    public Order cancelOrder(Long id, String currentUsername) {
        // 订单行锁：防并发双击取消导致双重退款
        Order order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("订单不存在"));

        if (!order.getUsername().equals(currentUsername)) {
            throw new RuntimeException("只有买家可以取消订单");
        }

        String st = order.getStatus();
        if (!OrderStatus.UNSHIPPED.equals(st) && !OrderStatus.PENDING_PAYMENT.equals(st)) {
            throw new RuntimeException("只能取消待付款或待发货的订单");
        }

        int refundQty = order.getQuantity() != null && order.getQuantity() > 0 ? order.getQuantity() : 1;
        BigDecimal refund = paidTotal(order);

        // 与下单一致的加锁顺序：商品 → 买家 → 卖家
        Product product = productRepository.findByIdForUpdate(order.getProductId()).orElse(null);

        // 仅已支付订单才退款；待付款订单未扣款，直接关单即可
        if (OrderStatus.UNSHIPPED.equals(st)) {
            User buyer = userRepository.findByUsernameForUpdate(order.getUsername())
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
            buyer.setBalance(buyer.getBalance().add(refund));
            userRepository.save(buyer);
            walletService.add(order.getUsername(), "refund", "取消退款 · " + order.getProductName(), refund);
            couponService.releaseByOrder(order.getId());

            // 资金托管下卖家尚未收款，无需扣回；兼容旧数据（已打款则扣回）
            if (Boolean.TRUE.equals(order.getSellerPaid())
                    && order.getSellerUsername() != null && !order.getSellerUsername().isEmpty()) {
                User seller = userRepository.findByUsernameForUpdate(order.getSellerUsername())
                        .orElseThrow(() -> new RuntimeException("卖家不存在"));
                seller.setBalance(seller.getBalance().subtract(refund));
                userRepository.save(seller);
                walletService.add(order.getSellerUsername(), "refund", "取消扣回 · " + order.getProductName(), refund.negate());
            }
        }

        // 按订单购买数量恢复商品库存
        if (product != null) {
            int restored = product.getQuantity() != null ? product.getQuantity() : 0;
            product.setQuantity(restored + refundQty);
            if (ProductStatus.SOLD.equals(product.getStatus())) {
                product.setStatus(ProductStatus.ACTIVE);
            }
            productRepository.save(product);
        }

        order.setStatus(OrderStatus.CANCELLED);
        return orderRepository.save(order);
    }

    /**
     * 买家申请售后（仅 shipped / received 可申请）。
     * 进入 after_sale 后由卖家同意退货或拒绝。
     */
    @Transactional
    public Order applyAfterSale(Long id, String currentUsername, String reason) {
        Order order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("订单不存在"));
        if (!order.getUsername().equals(currentUsername)) {
            throw new RuntimeException("只有买家可以申请售后");
        }
        String st = order.getStatus();
        if (!OrderStatus.SHIPPED.equals(st) && !OrderStatus.RECEIVED.equals(st)) {
            throw new RuntimeException("当前状态不可申请售后");
        }
        order.setPrevStatus(st);
        order.setAfterSaleReason(reason != null && !reason.isBlank() ? reason.trim() : "买家申请退款");
        order.setAfterSaleAt(LocalDateTime.now());
        order.setStatus(OrderStatus.AFTER_SALE);
        return orderRepository.save(order);
    }

    /**
     * 卖家同意退货：进入「待买家寄回」，此时不退款——
     * 退货签收后才结算，避免钱货两空。
     */
    @Transactional
    public Order approveAfterSale(Long id, String sellerUsername) {
        Order order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("订单不存在"));
        if (!order.getSellerUsername().equals(sellerUsername)) {
            throw new RuntimeException("无权操作此订单");
        }
        if (!OrderStatus.AFTER_SALE.equals(order.getStatus())) {
            throw new RuntimeException("订单不在售后中");
        }
        order.setStatus(OrderStatus.RETURN_SHIPPING);
        return orderRepository.save(order);
    }

    /**
     * 买家填写退货运单号并寄回。
     */
    @Transactional
    public Order submitReturnTracking(Long id, String currentUsername, String trackingNumber) {
        Order order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("订单不存在"));
        if (!order.getUsername().equals(currentUsername)) {
            throw new RuntimeException("只有买家可以填写退货物流");
        }
        if (!OrderStatus.RETURN_SHIPPING.equals(order.getStatus())) {
            throw new RuntimeException("当前状态无需寄回退货");
        }
        if (trackingNumber == null || trackingNumber.trim().isEmpty()) {
            throw new RuntimeException("请填写退货运单号");
        }
        String no = trackingNumber.trim();
        if (no.length() > 100) {
            throw new RuntimeException("运单号过长");
        }
        order.setReturnTrackingNumber(no);
        order.setReturnSentAt(LocalDateTime.now());
        order.setStatus(OrderStatus.RETURN_SENT);
        return orderRepository.save(order);
    }

    /**
     * 卖家确认收到退货 → 退款给买家、恢复库存，订单进入已退款。
     */
    @Transactional
    public Order confirmReturnReceived(Long id, String sellerUsername) {
        Order order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("订单不存在"));
        if (!order.getSellerUsername().equals(sellerUsername)) {
            throw new RuntimeException("无权操作此订单");
        }
        if (!OrderStatus.RETURN_SENT.equals(order.getStatus())) {
            throw new RuntimeException("买家尚未寄回退货");
        }
        settleRefund(order);
        order.setStatus(OrderStatus.REFUNDED);
        return orderRepository.save(order);
    }

    /** 超时任务用：卖家超时未确认收货 → 退款并标记已退款（调用方须已持有订单行锁） */
    @Transactional
    public void settleRefundAndMarkRefunded(Order order) {
        settleRefund(order);
        order.setStatus(OrderStatus.REFUNDED);
        orderRepository.save(order);
    }

    /**
     * 买家撤销售后申请（尚未寄回时）：回到申请前的状态。
     */
    @Transactional
    public Order cancelAfterSale(Long id, String currentUsername) {
        Order order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("订单不存在"));
        if (!order.getUsername().equals(currentUsername)) {
            throw new RuntimeException("只有买家可以撤销售后申请");
        }
        String st = order.getStatus();
        if (!OrderStatus.AFTER_SALE.equals(st) && !OrderStatus.RETURN_SHIPPING.equals(st)) {
            throw new RuntimeException("当前状态无法撤销售后");
        }
        if (order.getReturnTrackingNumber() != null && !order.getReturnTrackingNumber().isBlank()) {
            throw new RuntimeException("退货已寄出，无法撤销，请联系卖家或平台客服");
        }
        String back = order.getPrevStatus();
        if (back == null || back.isBlank() || OrderStatus.AFTER_SALE.equals(back)) {
            back = OrderStatus.SHIPPED;
        }
        order.setStatus(back);
        order.setPrevStatus(null);
        order.setAfterSaleAt(null);
        return orderRepository.save(order);
    }

    /**
     * 卖家拒绝售后：退回申请前状态。
     */
    @Transactional
    public Order rejectAfterSale(Long id, String sellerUsername) {
        Order order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("订单不存在"));
        if (!order.getSellerUsername().equals(sellerUsername)) {
            throw new RuntimeException("无权操作此订单");
        }
        if (!OrderStatus.AFTER_SALE.equals(order.getStatus())) {
            throw new RuntimeException("订单不在售后中");
        }
        String back = order.getPrevStatus();
        if (back == null || back.isBlank() || OrderStatus.AFTER_SALE.equals(back)) {
            back = OrderStatus.SHIPPED;
        }
        order.setStatus(back);
        order.setPrevStatus(null);
        order.setAfterSaleAt(null);
        return orderRepository.save(order);
    }

    /**
     * 管理员强制取消并退款（平台仲裁，任意未完结状态）。
     */
    @Transactional
    @Audited(action = "强制取消订单", targetType = "order")
    public Order adminForceCancel(Long id) {
        Order order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("订单不存在"));
        String st = order.getStatus();
        if (OrderStatus.COMPLETED.equals(st) || OrderStatus.CANCELLED.equals(st)
                || OrderStatus.REFUNDED.equals(st)) {
            throw new RuntimeException("订单已完结，无法强制取消");
        }
        settleRefund(order);
        order.setStatus(OrderStatus.CANCELLED);
        return orderRepository.save(order);
    }

    /**
     * 是否已实际扣款：扣款只发生在 payOrder，
     * pending_payment 尚未扣款，取消/强制取消时绝不能退款。
     */
    private static boolean isPaidStatus(String status) {
        return OrderStatus.UNSHIPPED.equals(status)
                || OrderStatus.SHIPPED.equals(status)
                || OrderStatus.RECEIVED.equals(status)
                || OrderStatus.AFTER_SALE.equals(status)
                || OrderStatus.RETURN_SHIPPING.equals(status)
                || OrderStatus.RETURN_SENT.equals(status);
    }

    /** 退款 + 恢复库存；仅已支付订单才退钱，待付款只回补库存 */
    private void settleRefund(Order order) {
        int refundQty = order.getQuantity() != null && order.getQuantity() > 0 ? order.getQuantity() : 1;
        boolean paid = isPaidStatus(order.getStatus());
        BigDecimal refund = paidTotal(order);

        if (paid) {
            User buyer = userRepository.findByUsernameForUpdate(order.getUsername())
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
            buyer.setBalance(buyer.getBalance().add(refund));
            userRepository.save(buyer);
            walletService.add(order.getUsername(), "refund",
                    "售后退款 · " + order.getProductName(), refund);

            // 仅当已打款给卖家时才扣回（兼容旧数据）
            if (Boolean.TRUE.equals(order.getSellerPaid())
                    && order.getSellerUsername() != null && !order.getSellerUsername().isEmpty()) {
                User seller = userRepository.findByUsernameForUpdate(order.getSellerUsername())
                        .orElseThrow(() -> new RuntimeException("卖家不存在"));
                seller.setBalance(seller.getBalance().subtract(refund));
                userRepository.save(seller);
                walletService.add(order.getSellerUsername(), "refund",
                        "售后扣回 · " + order.getProductName(), refund.negate());
            }
            // 全额退款，把用掉的券退回去
            couponService.releaseByOrder(order.getId());
        }

        Product product = productRepository.findByIdForUpdate(order.getProductId()).orElse(null);
        if (product != null) {
            int restored = product.getQuantity() != null ? product.getQuantity() : 0;
            product.setQuantity(restored + refundQty);
            if (ProductStatus.SOLD.equals(product.getStatus())) {
                product.setStatus(ProductStatus.ACTIVE);
            }
            productRepository.save(product);
        }
    }

    @Transactional
    public Order completeOrder(Long id, String currentUsername) {
        Order order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("订单不存在"));

        if (!order.getUsername().equals(currentUsername)) {
            throw new RuntimeException("只有买家可以确认完成");
        }

        if (!OrderStatus.RECEIVED.equals(order.getStatus())) {
            throw new RuntimeException("只能确认已完成收货的订单");
        }

        releaseToSeller(order);
        order.setStatus(OrderStatus.COMPLETED);
        return orderRepository.save(order);
    }

    /** 资金托管：订单完成时把货款打给卖家（幂等） */
    public void releaseToSeller(Order order) {
        if (Boolean.TRUE.equals(order.getSellerPaid())) {
            return;
        }
        if (order.getSellerUsername() == null || order.getSellerUsername().isEmpty()) {
            order.setSellerPaid(true);
            return;
        }
        int qty = order.getQuantity() != null && order.getQuantity() > 0 ? order.getQuantity() : 1;
        BigDecimal amount = paidTotal(order);
        userRepository.findByUsernameForUpdate(order.getSellerUsername()).ifPresent(seller -> {
            seller.setBalance(seller.getBalance().add(amount));
            userRepository.save(seller);
            walletService.add(order.getSellerUsername(), "sell",
                    "售出 · " + order.getProductName(), amount);
        });
        order.setSellerPaid(true);
    }

    public List<Order> getBuyerOrders(String username, String status) {
        if (status != null && !status.isEmpty() && !"all".equals(status)) {
            return orderRepository.findByUsernameAndStatusOrderByCreatedAtDesc(username, status);
        }
        return orderRepository.findByUsernameOrderByCreatedAtDesc(username);
    }

    public List<Order> getSellerOrders(String sellerUsername, String status) {
        if (status != null && !status.isEmpty() && !"all".equals(status)) {
            return orderRepository.findBySellerUsernameAndStatusOrderByCreatedAtDesc(sellerUsername, status);
        }
        return orderRepository.findBySellerUsernameOrderByCreatedAtDesc(sellerUsername);
    }

    /**
     * 卖家经营看板：在售/已售商品数、成交笔数、待结算与已到账收入、近 14 天成交趋势。
     * 收入口径与钱包流水一致（按实付金额，含优惠券抵扣后的净额）。
     */
    public Map<String, Object> sellerStats(String sellerUsername) {
        List<Order> orders = orderRepository.findBySellerUsernameOrderByCreatedAtDesc(sellerUsername);
        List<com.secondhand.mall.entity.Product> products = productRepository.findBySellerUsername(sellerUsername);

        java.math.BigDecimal pendingIncome = java.math.BigDecimal.ZERO;
        java.math.BigDecimal settledIncome = java.math.BigDecimal.ZERO;
        long salesCount = 0;
        for (Order o : orders) {
            if (OrderStatus.CANCELLED.equals(o.getStatus()) || OrderStatus.PENDING_PAYMENT.equals(o.getStatus())) {
                continue;
            }
            salesCount++;
            if (Boolean.TRUE.equals(o.getSellerPaid())) {
                settledIncome = settledIncome.add(paidTotal(o));
            } else if (OrderStatus.REFUNDED.equals(o.getStatus())) {
                // 已退款不计入收入
            } else if (!OrderStatus.AFTER_SALE.equals(o.getStatus())) {
                pendingIncome = pendingIncome.add(paidTotal(o));
            }
        }

        long activeProducts = products.stream()
                .filter(p -> ProductStatus.ACTIVE.equals(p.getStatus())).count();
        long soldProducts = products.stream()
                .filter(p -> ProductStatus.SOLD.equals(p.getStatus())).count();

        // 近 14 天成交趋势（按天聚合已支付订单）
        List<Map<String, Object>> trend = new ArrayList<>();
        java.time.LocalDate today = java.time.LocalDate.now();
        for (int i = 13; i >= 0; i--) {
            java.time.LocalDate day = today.minusDays(i);
            java.time.LocalDateTime start = day.atStartOfDay();
            java.time.LocalDateTime end = day.plusDays(1).atStartOfDay();
            long count = 0;
            java.math.BigDecimal amount = java.math.BigDecimal.ZERO;
            for (Order o : orders) {
                if (o.getCreatedAt() == null || o.getCreatedAt().isBefore(start) || !o.getCreatedAt().isBefore(end)) {
                    continue;
                }
                if (OrderStatus.CANCELLED.equals(o.getStatus()) || OrderStatus.PENDING_PAYMENT.equals(o.getStatus())) {
                    continue;
                }
                count++;
                amount = amount.add(paidTotal(o));
            }
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("date", day.toString());
            row.put("count", count);
            row.put("amount", amount);
            trend.add(row);
        }

        Map<String, Object> stats = new java.util.LinkedHashMap<>();
        stats.put("totalProducts", products.size());
        stats.put("activeProducts", activeProducts);
        stats.put("soldProducts", soldProducts);
        stats.put("salesCount", salesCount);
        stats.put("pendingIncome", pendingIncome);
        stats.put("settledIncome", settledIncome);
        stats.put("trend", trend);
        return stats;
    }

    /**
     * 获取订单详情，需校验当前用户是否是买家或卖家
     */
    public Optional<Order> getOrderById(Long id, String currentUsername) {
        Optional<Order> opt = orderRepository.findById(id);
        opt.ifPresent(order -> {
            if (!order.getUsername().equals(currentUsername) &&
                !order.getSellerUsername().equals(currentUsername)) {
                throw new RuntimeException("无权查看此订单");
            }
        });
        return opt;
    }

    /**
     * 获取订单详情（无权限校验，供 admin 使用）
     */
    public Optional<Order> getOrderById(Long id) {
        return orderRepository.findById(id);
    }

    @Transactional
    public Order updateOrderStatus(Long id, String status, String currentUsername) {
        return updateOrderStatus(id, status, null, currentUsername);
    }

    @Transactional
    public Order updateOrderStatus(Long id, String status, String trackingNumber, String currentUsername) {
        // 订单行锁：防与取消/收货并发双写
        Order order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("订单不存在"));

        // 验证状态流转合法性
        if (!isValidTransition(order.getStatus(), status)) {
            throw new RuntimeException("非法状态流转: " + order.getStatus() + " → " + status);
        }

        // 验证权限 + 资金副作用
        if (OrderStatus.SHIPPED.equals(status)) {
            if (!order.getSellerUsername().equals(currentUsername)) {
                throw new RuntimeException("无权操作此订单");
            }
            if (order.getShippedAt() == null) {
                order.setShippedAt(LocalDateTime.now());
            }
        } else if (OrderStatus.RECEIVED.equals(status)) {
            if (!order.getUsername().equals(currentUsername)) {
                throw new RuntimeException("无权操作此订单");
            }
            order.setReceivedAt(LocalDateTime.now());
        } else if (OrderStatus.COMPLETED.equals(status)) {
            if (!order.getUsername().equals(currentUsername)) {
                throw new RuntimeException("无权操作此订单");
            }
            // 经此路径完成同样必须打款，否则卖家拿不到货款
            releaseToSeller(order);
        } else if (OrderStatus.CANCELLED.equals(status)) {
            if (!order.getUsername().equals(currentUsername)) {
                throw new RuntimeException("无权操作此订单");
            }
            // 退款 + 恢复库存，与 cancelOrder / approveAfterSale 一致
            settleRefund(order);
        } else {
            throw new RuntimeException("该状态不可直接设置: " + status);
        }

        order.setStatus(status);
        // 运单号仅允许发货（卖家）写入，避免买家确认收货时覆盖物流证据
        if ("shipped".equals(status) && trackingNumber != null && !trackingNumber.isEmpty()) {
            order.setTrackingNumber(trackingNumber);
        }
        return orderRepository.save(order);
    }

    @Transactional
    public void deleteOrder(Long id, String currentUsername) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("订单不存在"));

        if (!order.getUsername().equals(currentUsername) &&
            !order.getSellerUsername().equals(currentUsername)) {
            throw new RuntimeException("无权删除此订单");
        }

        if (!OrderStatus.COMPLETED.equals(order.getStatus()) &&
            !OrderStatus.CANCELLED.equals(order.getStatus())) {
            throw new RuntimeException("只能删除已完成或已取消的订单");
        }

        orderRepository.delete(order);
    }

    public long getOrderCount() {
        return orderRepository.count();
    }

    /** 管理端：全部订单 */
    public List<Order> getAllOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc();
    }

    public long getBuyerActiveOrderCount(String username) {
        return orderRepository.countBuyerActiveOrders(username);
    }

    public long getSellerPendingOrderCount(String username) {
        return orderRepository.countSellerPendingOrders(username);
    }

    /** 卖家待结算金额（资金托管：完成前货款在平台）；与实付口径一致，扣掉优惠券 */
    public BigDecimal getSellerPendingAmount(String username) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Order o : orderRepository.findSellerUnpaidOrders(username)) {
            sum = sum.add(paidTotal(o));
        }
        return sum;
    }

    private boolean isValidTransition(String currentStatus, String newStatus) {
        return VALID_TRANSITIONS
            .getOrDefault(currentStatus, List.of())
            .contains(newStatus);
    }

    private String generateOrderNumber() {
        // 时间戳 + 4 位随机：不可预测且避免并发撞号
        String ts = String.valueOf(System.currentTimeMillis());
        for (int i = 0; i < 10; i++) {
            String num = ts + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
            if (!orderRepository.existsByOrderNumber(num)) {
                return num;
            }
        }
        return ts + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }
}
