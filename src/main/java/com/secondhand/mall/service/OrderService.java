package com.secondhand.mall.service;

import com.secondhand.mall.entity.Order;
import com.secondhand.mall.entity.Product;
import com.secondhand.mall.entity.User;
import com.secondhand.mall.repository.OrderRepository;
import com.secondhand.mall.repository.ProductRepository;
import com.secondhand.mall.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository,
                        UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Order createOrder(String buyerUsername, Long productId, double orderPrice,
                             String productName, String category, String imageUrl,
                             String receiverName, String receiverPhone, String receiverAddress,
                             String paymentMethod) {
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

        User buyer = userRepository.findByUsername(buyerUsername)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        if (buyer.getBalance() < orderPrice) {
            throw new RuntimeException("余额不足，当前余额: ¥" + String.format("%.2f", buyer.getBalance()));
        }

        double price = product.getSecondHandPrice();
        if (Math.abs(orderPrice - price) > 0.01) {
            throw new RuntimeException("价格不一致，请刷新后重试");
        }

        // 扣减买家余额
        buyer.setBalance(buyer.getBalance() - price);
        userRepository.save(buyer);

        // 库存递减（复用 markAsSold 逻辑）
        if (qty <= 1) {
            product.setQuantity(0);
            product.setStatus("sold");
        } else {
            product.setQuantity(qty - 1);
        }
        productRepository.save(product);

        // 增加卖家余额
        if (product.getSellerUsername() != null && !product.getSellerUsername().isEmpty()) {
            userRepository.findByUsername(product.getSellerUsername()).ifPresent(seller -> {
                seller.setBalance(seller.getBalance() + price);
                userRepository.save(seller);
            });
        }

        // 创建订单
        Order order = new Order();
        order.setUsername(buyerUsername);
        order.setProductId(productId);
        order.setSellerUsername(product.getSellerUsername());
        order.setProductName(productName);
        order.setProductPrice(price);
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
        User buyer = userRepository.findByUsername(order.getUsername())
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        buyer.setBalance(buyer.getBalance() + order.getProductPrice());
        userRepository.save(buyer);

        // 扣回卖家余额
        if (order.getSellerUsername() != null && !order.getSellerUsername().isEmpty()) {
            userRepository.findByUsername(order.getSellerUsername()).ifPresent(seller -> {
                seller.setBalance(seller.getBalance() - order.getProductPrice());
                userRepository.save(seller);
            });
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
        List<Product> sellerProducts = productRepository.findBySellerUsername(sellerUsername);
        List<Long> productIds = sellerProducts.stream().map(Product::getId).toList();

        if (productIds.isEmpty()) {
            return List.of();
        }

        if (status != null && !status.isEmpty() && !"all".equals(status)) {
            return orderRepository.findByProductIdInAndStatusOrderByCreatedAtDesc(productIds, status);
        }
        return orderRepository.findByProductIdInOrderByCreatedAtDesc(productIds);
    }

    public Optional<Order> getOrderById(Long id) {
        return orderRepository.findById(id);
    }

    public Order updateOrderStatus(Long id, String status, String currentUsername) {
        return updateOrderStatus(id, status, null, currentUsername);
    }

    public Order updateOrderStatus(Long id, String status, String trackingNumber, String currentUsername) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("订单不存在"));

        // 验证权限
        if ("shipped".equals(status) || "unshipped".equals(status)) {
            // 卖家操作
            if (!order.getSellerUsername().equals(currentUsername)) {
                throw new RuntimeException("无权操作此订单");
            }
        } else if ("received".equals(status) || "completed".equals(status)) {
            // 买家操作
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

        orderRepository.delete(order);
    }

    public long getOrderCount() {
        return orderRepository.count();
    }
}
