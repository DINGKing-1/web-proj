package com.secondhand.mall.service;

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
        "unshipped", List.of("shipped"),
        "shipped",   List.of("received"),
        "received",  List.of("completed")
    );

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CartItemRepository cartItemRepository;

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository,
                        UserRepository userRepository, CartItemRepository cartItemRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.cartItemRepository = cartItemRepository;
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
        for (OrderItemRequest item : items) {
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
        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new RuntimeException("商品不存在"));

        if ("sold".equals(product.getStatus())) {
            throw new RuntimeException("该商品已被购买");
        }

        int qty = product.getQuantity() != null ? product.getQuantity() : 1;
        if (qty <= 0) {
            throw new RuntimeException("该商品库存不足");
        }

        if (buyerUsername.equals(product.getSellerUsername())) {
            throw new RuntimeException("不能购买自己发布的商品");
        }

        User buyer = userRepository.findByUsernameForUpdate(buyerUsername)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        if (buyer.getBalance().compareTo(orderPrice) < 0) {
            throw new RuntimeException("余额不足，当前余额: ¥" + buyer.getBalance().toPlainString());
        }

        BigDecimal price = product.getSecondHandPrice();
        if (orderPrice.subtract(price).abs().compareTo(new BigDecimal("0.01")) > 0) {
            throw new RuntimeException("价格不一致，请刷新后重试");
        }

        // 扣减买家余额
        buyer.setBalance(buyer.getBalance().subtract(price));
        userRepository.save(buyer);

        // 库存递减
        if (qty <= 1) {
            product.setQuantity(0);
            product.setStatus("sold");
        } else {
            product.setQuantity(qty - 1);
        }
        productRepository.save(product);

        // 增加卖家余额
        if (product.getSellerUsername() != null && !product.getSellerUsername().isEmpty()) {
            userRepository.findByUsernameForUpdate(product.getSellerUsername()).ifPresent(seller -> {
                seller.setBalance(seller.getBalance().add(price));
                userRepository.save(seller);
            });
        }

        // 创建订单
        Order order = new Order();
        order.setOrderNumber(generateOrderNumber());
        order.setUsername(buyerUsername);
        order.setProductId(productId);
        order.setSellerUsername(product.getSellerUsername());
        order.setProductName(productName);
        order.setProductPrice(price);
        order.setQuantity(quantity > 0 ? quantity : 1);
        order.setCategory(category);
        order.setImageUrl(imageUrl);
        order.setReceiverName(receiverName);
        order.setReceiverPhone(receiverPhone);
        order.setReceiverAddress(receiverAddress);
        order.setPaymentMethod(paymentMethod);
        order.setStatus("unshipped");

        return orderRepository.save(order);
    }

    @Transactional
    public Order cancelOrder(Long id, String currentUsername) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("订单不存在"));

        if (!order.getUsername().equals(currentUsername)) {
            throw new RuntimeException("只有买家可以取消订单");
        }

        if (!"unshipped".equals(order.getStatus())) {
            throw new RuntimeException("只能取消待发货的订单");
        }

        // 退还买家余额
        User buyer = userRepository.findByUsernameForUpdate(order.getUsername())
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        buyer.setBalance(buyer.getBalance().add(order.getProductPrice()));
        userRepository.save(buyer);

        // 扣回卖家余额
        if (order.getSellerUsername() != null && !order.getSellerUsername().isEmpty()) {
            User seller = userRepository.findByUsernameForUpdate(order.getSellerUsername())
                    .orElseThrow(() -> new RuntimeException("卖家不存在"));

            if (seller.getBalance().compareTo(order.getProductPrice()) < 0) {
                throw new RuntimeException("卖家余额不足，无法退款。请联系管理员处理。");
            }

            seller.setBalance(seller.getBalance().subtract(order.getProductPrice()));
            userRepository.save(seller);
        }

        // 恢复商品库存
        productRepository.findById(order.getProductId()).ifPresent(product -> {
            if ("sold".equals(product.getStatus())) {
                product.setStatus("active");
                product.setQuantity(1);
            } else {
                product.setQuantity((product.getQuantity() != null ? product.getQuantity() : 0) + 1);
            }
            productRepository.save(product);
        });

        order.setStatus("cancelled");
        return orderRepository.save(order);
    }

    @Transactional
    public Order completeOrder(Long id, String currentUsername) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("订单不存在"));

        if (!order.getUsername().equals(currentUsername)) {
            throw new RuntimeException("只有买家可以确认完成");
        }

        if (!"received".equals(order.getStatus())) {
            throw new RuntimeException("只能确认已完成收货的订单");
        }

        order.setStatus("completed");
        return orderRepository.save(order);
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

    public Order updateOrderStatus(Long id, String status, String currentUsername) {
        return updateOrderStatus(id, status, null, currentUsername);
    }

    @Transactional
    public Order updateOrderStatus(Long id, String status, String trackingNumber, String currentUsername) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("订单不存在"));

        // 验证状态流转合法性
        if (!isValidTransition(order.getStatus(), status)) {
            throw new RuntimeException("非法状态流转: " + order.getStatus() + " → " + status);
        }

        // 验证权限
        if ("shipped".equals(status)) {
            if (!order.getSellerUsername().equals(currentUsername)) {
                throw new RuntimeException("无权操作此订单");
            }
        } else if ("received".equals(status) || "completed".equals(status)) {
            if (!order.getUsername().equals(currentUsername)) {
                throw new RuntimeException("无权操作此订单");
            }
        }

        order.setStatus(status);
        if (trackingNumber != null && !trackingNumber.isEmpty()) {
            order.setTrackingNumber(trackingNumber);
        }
        return orderRepository.save(order);
    }

    public void deleteOrder(Long id, String currentUsername) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("订单不存在"));

        if (!order.getUsername().equals(currentUsername) &&
            !order.getSellerUsername().equals(currentUsername)) {
            throw new RuntimeException("无权删除此订单");
        }

        if (!"completed".equals(order.getStatus()) &&
            !"cancelled".equals(order.getStatus())) {
            throw new RuntimeException("只能删除已完成或已取消的订单");
        }

        orderRepository.delete(order);
    }

    public long getOrderCount() {
        return orderRepository.count();
    }

    public long getBuyerActiveOrderCount(String username) {
        return orderRepository.countBuyerActiveOrders(username);
    }

    public long getSellerPendingOrderCount(String username) {
        return orderRepository.countSellerPendingOrders(username);
    }

    private boolean isValidTransition(String currentStatus, String newStatus) {
        return VALID_TRANSITIONS
            .getOrDefault(currentStatus, List.of())
            .contains(newStatus);
    }

    private String generateOrderNumber() {
        for (int i = 0; i < 10; i++) {
            String num = String.valueOf(100000 + ThreadLocalRandom.current().nextInt(900000));
            if (!orderRepository.existsByOrderNumber(num)) {
                return num;
            }
        }
        return String.valueOf(System.currentTimeMillis() % 1000000);
    }
}
