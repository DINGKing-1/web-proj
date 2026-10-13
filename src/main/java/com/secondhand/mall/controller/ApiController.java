package com.secondhand.mall.controller;

import com.secondhand.mall.dto.LoginRequest;
import com.secondhand.mall.dto.RegisterRequest;
import com.secondhand.mall.entity.*;
import com.secondhand.mall.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ApiController extends BaseApiController {

    private final ProductService productService;
    private final CommentService commentService;
    private final UserService userService;
    private final CartService cartService;
    private final OrderService orderService;
    private final AddressService addressService;
    private final FileUploadService fileUploadService;
    private final ReviewService reviewService;
    private final NotificationService notificationService;
    private final SensitiveWordService sensitiveWordService;
    private final WalletService walletService;
    private final LogisticsService logisticsService;

    public ApiController(ProductService productService, CommentService commentService,
                         UserService userService, CartService cartService,
                         OrderService orderService, AddressService addressService,
                         FileUploadService fileUploadService, ReviewService reviewService,
                         NotificationService notificationService,
                         SensitiveWordService sensitiveWordService, WalletService walletService,
                         LogisticsService logisticsService) {
        this.productService = productService;
        this.commentService = commentService;
        this.userService = userService;
        this.cartService = cartService;
        this.orderService = orderService;
        this.addressService = addressService;
        this.fileUploadService = fileUploadService;
        this.reviewService = reviewService;
        this.notificationService = notificationService;
        this.sensitiveWordService = sensitiveWordService;
        this.walletService = walletService;
        this.logisticsService = logisticsService;
    }

    // ========== 认证 ==========
    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest request,
                                     HttpServletRequest httpRequest, HttpSession session) {
        try {
            User user = userService.login(request, session.getId());
            // 防会话固定：登录成功后更换会话 ID
            String newSessionId = httpRequest.changeSessionId();
            session.setAttribute("username", user.getUsername());
            session.setAttribute("nickname", user.getNickname());
            session.setAttribute("role", user.getRole());
            userService.setSessionId(user.getUsername(), newSessionId);

            return ok("登录成功")
                    .put("username", user.getUsername())
                    .put("nickname", user.getNickname())
                    .put("balance", user.getBalance())
                    .put("avatar", user.getAvatar())
                    .put("role", user.getRole())
                    .toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    @PostMapping("/register")
    public Map<String, Object> register(@Valid @RequestBody RegisterRequest request,
                                        HttpServletRequest httpRequest, HttpSession session) {
        try {
            User user = userService.register(request, session.getId());
            // 防会话固定：注册成功后更换会话 ID
            String newSessionId = httpRequest.changeSessionId();
            session.setAttribute("username", user.getUsername());
            session.setAttribute("nickname", user.getNickname());
            session.setAttribute("role", user.getRole());
            userService.setSessionId(user.getUsername(), newSessionId);

            return ok("注册成功")
                    .put("username", user.getUsername())
                    .put("nickname", user.getNickname())
                    .put("balance", user.getBalance())
                    .put("avatar", user.getAvatar())
                    .put("role", user.getRole())
                    .toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpSession session) {
        String username = (String) session.getAttribute("username");
        if (username != null) {
            userService.setSessionId(username, null);
        }
        session.invalidate();
        return ok("已退出登录").toMap();
    }

    @GetMapping("/session")
    public Map<String, Object> getSession(HttpSession session) {
        String username = (String) session.getAttribute("username");
        if (username == null) {
            return fail("未登录").toMap();
        }
        try {
            User user = userService.findByUsername(username);
            long buyerCount = orderService.getBuyerActiveOrderCount(username);
            long sellerCount = orderService.getSellerPendingOrderCount(username);
            return ok("已登录")
                    .put("username", username)
                    .put("nickname", user.getNickname())
                    .put("role", user.getRole())
                    .put("avatar", user.getAvatar())
                    .put("balance", user.getBalance())
                    .put("pendingAmount", orderService.getSellerPendingAmount(username))
                    .put("buyerOrderCount", buyerCount)
                    .put("sellerOrderCount", sellerCount)
                    .toMap();
        } catch (RuntimeException e) {
            return fail("用户不存在").toMap();
        }
    }

    // ========== 图片上传（支持多张） ==========
    @SuppressWarnings("unchecked")
    @PostMapping("/upload-image")
    public Map<String, Object> uploadImage(@RequestBody Map<String, Object> request, HttpSession session) {
        try {
            getLoginUsername(session); // 需登录，防匿名刷磁盘
            Object imageObj = request.get("image");
            List<String> imageList;
            if (imageObj instanceof List) {
                imageList = (List<String>) imageObj;
            } else if (imageObj instanceof String) {
                imageList = List.of((String) imageObj);
            } else {
                return fail("图片数据为空").toMap();
            }

            List<String> urls = fileUploadService.uploadProductImages(imageList);
            return ok("上传成功")
                    .put("imageUrls", urls)
                    .put("imageUrl", urls.get(0))
                    .toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    // ========== 商品 ==========
    @SuppressWarnings("unchecked")
    @PostMapping("/products")
    public Map<String, Object> createProduct(@RequestBody Map<String, Object> request, HttpSession session) {
        try {
            String username = getLoginUsername(session);

            Product product = new Product();
            product.setName((String) request.get("name"));
            product.setCategory((String) request.get("category"));
            product.setDescription((String) request.get("description"));
            product.setOriginalPrice(new BigDecimal(request.get("originalPrice").toString()));
            product.setSecondHandPrice(new BigDecimal(request.get("secondHandPrice").toString()));
            product.setItemCondition((String) request.get("condition"));
            product.setSellerUsername(username);

            // 优先使用登录用户信息
            try {
                User seller = userService.findByUsername(username);
                product.setSellerName(seller.getNickname() != null ? seller.getNickname() : seller.getUsername());
                product.setSellerPhone(seller.getPhone() != null ? seller.getPhone() : "");
                product.setSellerAvatar(seller.getAvatar());
            } catch (Exception e) {
                product.setSellerName(username);
                product.setSellerPhone("");
            }

            Object imageObj = request.get("imageUrl");
            if (imageObj instanceof List) {
                product.setImageUrls(sanitizeProductImages((List<String>) imageObj));
            } else if (imageObj instanceof String && !((String) imageObj).isEmpty()) {
                product.setImageUrl(sanitizeProductImages(List.of((String) imageObj)).get(0));
            }

            Object qtyObj = request.get("quantity");
            if (qtyObj != null) {
                product.setQuantity(Integer.parseInt(qtyObj.toString()));
            }

            Product saved = productService.createProduct(product);
            return ok("商品发布成功")
                    .put("productId", saved.getId())
                    .put("status", saved.getStatus())
                    .toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    @GetMapping("/products")
    public Map<String, Object> getProducts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String condition,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String seller,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {

        // 真分页：筛选/排序下推数据库，只取当前页
        var result = productService.searchProductsPaged(
                null, category, seller, minPrice, maxPrice, condition, sortBy, page, size);

        return ok("获取成功")
                .put("products", result.getContent())
                .put("total", result.getTotalElements())
                .put("totalPages", result.getTotalPages())
                .put("currentPage", result.getNumber())
                .toMap();
    }

    @GetMapping("/products/{id}")
    public Map<String, Object> getProduct(@PathVariable Long id, HttpSession session) {
        String viewer = (String) session.getAttribute("username");
        // 路人仅见在售；卖家可看自己的待审/驳回，防审核内容外泄
        return productService.getProductById(id, viewer)
                .map(product -> {
                    productService.incrementViewCount(id);
                    return ok("获取成功").put("product", product).toMap();
                })
                .orElseGet(() -> fail("商品不存在").toMap());
    }

    @GetMapping("/products/search")
    public Map<String, Object> searchProducts(
            @RequestParam String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String condition,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {

        if (q == null || q.trim().isEmpty()) {
            return fail("搜索关键词不能为空").toMap();
        }

        var result = productService.searchProductsPaged(
                q.trim(), category, null, minPrice, maxPrice, condition, sortBy, page, size);

        return ok("搜索成功")
                .put("products", result.getContent())
                .put("total", result.getTotalElements())
                .put("totalPages", result.getTotalPages())
                .put("currentPage", result.getNumber())
                .toMap();
    }

    @SuppressWarnings("unchecked")
    @PutMapping("/products/{id}")
    public Map<String, Object> updateProduct(@PathVariable Long id,
                                              @RequestBody Map<String, Object> request,
                                              HttpSession session) {
        try {
            String username = getLoginUsername(session);

            Product updates = new Product();
            if (request.containsKey("imageUrl")) {
                Object imageObj = request.get("imageUrl");
                if (imageObj instanceof List) {
                    updates.setImageUrls(sanitizeProductImages((List<String>) imageObj));
                } else if (imageObj instanceof String) {
                    updates.setImageUrl(sanitizeProductImages(List.of((String) imageObj)).get(0));
                }
            }
            if (request.containsKey("name")) updates.setName((String) request.get("name"));
            if (request.containsKey("description")) updates.setDescription((String) request.get("description"));
            if (request.containsKey("category")) updates.setCategory((String) request.get("category"));
            if (request.containsKey("originalPrice")) updates.setOriginalPrice(new BigDecimal(request.get("originalPrice").toString()));
            if (request.containsKey("secondHandPrice")) updates.setSecondHandPrice(new BigDecimal(request.get("secondHandPrice").toString()));
            if (request.containsKey("condition")) updates.setItemCondition((String) request.get("condition"));
            if (request.containsKey("status")) {
                String status = (String) request.get("status");
                // 状态白名单：禁止写入 sold/deleted 等任意值
                if (!Set.of("active", "inactive").contains(status)) {
                    return fail("非法的商品状态").toMap();
                }
                updates.setStatus(status);
            }
            if (request.containsKey("quantity")) updates.setQuantity(Integer.parseInt(request.get("quantity").toString()));

            Product saved = productService.updateProduct(id, updates, username);
            return ok("更新成功").put("status", saved.getStatus()).toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    @DeleteMapping("/products/{id}")
    public Map<String, Object> deleteProduct(@PathVariable Long id, HttpSession session) {
        try {
            String username = getLoginUsername(session);
            productService.deleteProduct(id, username);
            return ok("删除成功").toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    // ========== 评论 ==========
    @GetMapping("/products/{id}/comments")
    public Map<String, Object> getComments(@PathVariable Long id) {
        List<Map<String, Object>> comments = commentService.getCommentsByProduct(id);
        return ok("获取成功").put("comments", comments).put("total", comments.size()).toMap();
    }

    @PostMapping("/products/{id}/comments")
    public Map<String, Object> addComment(@PathVariable Long id,
                                           @RequestBody Map<String, String> request,
                                           HttpSession session) {
        try {
            String username = getLoginUsername(session);
            String content = request.get("content");
            sensitiveWordService.check(content);
            commentService.addComment(id, username, content);
            return ok("评论成功").toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    @DeleteMapping("/products/{id}/comments/{commentId}")
    public Map<String, Object> deleteComment(@PathVariable Long id,
                                              @PathVariable Long commentId,
                                              HttpSession session) {
        try {
            String username = getLoginUsername(session);
            commentService.deleteComment(id, commentId, username);
            return ok("删除成功").toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    // ========== 购物车 ==========
    @PostMapping("/cart")
    public Map<String, Object> addToCart(@RequestBody Map<String, Object> request, HttpSession session) {
        try {
            String username = getLoginUsername(session);
            Long productId = Long.parseLong(request.get("productId").toString());
            int quantity = 1;
            if (request.get("quantity") != null) {
                quantity = Integer.parseInt(request.get("quantity").toString());
            }
            cartService.addToCart(username, productId, quantity);
            return ok("已加入购物车").toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    @GetMapping("/cart")
    public Map<String, Object> getCart(HttpSession session) {
        String username = getLoginUsername(session);
        List<?> items = cartService.getCart(username);
        return ok("获取成功").put("items", items).put("total", items.size()).toMap();
    }

    @PutMapping("/cart/{id}/quantity")
    public Map<String, Object> updateCartQuantity(@PathVariable Long id,
                                                  @RequestBody Map<String, Object> request,
                                                  HttpSession session) {
        try {
            String username = getLoginUsername(session);
            int quantity = Integer.parseInt(String.valueOf(request.get("quantity")));
            cartService.updateQuantity(id, username, quantity);
            return ok("数量已更新").toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    @DeleteMapping("/cart/{id}")
    public Map<String, Object> removeFromCart(@PathVariable Long id, HttpSession session) {
        try {
            String username = getLoginUsername(session);
            cartService.removeFromCart(id, username);
            return ok("已移除").toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    @DeleteMapping("/cart")
    public Map<String, Object> clearCart(HttpSession session) {
        try {
            String username = getLoginUsername(session);
            cartService.clearCart(username);
            return ok("购物车已清空").toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    @GetMapping("/cart/count")
    public Map<String, Object> getCartCount(HttpSession session) {
        String username = getLoginUsername(session);
        int count = cartService.getCartCount(username);
        return ok("获取成功").put("count", count).toMap();
    }

    // ========== 用户信息 ==========
    @GetMapping("/user")
    public Map<String, Object> getUserInfo(HttpSession session) {
        String username = getLoginUsername(session);
        try {
            User user = userService.findByUsername(username);
            return ok("获取成功")
                    .put("username", user.getUsername())
                    .put("nickname", user.getNickname())
                    .put("email", user.getEmail())
                    .put("phone", user.getPhone())
                    .put("balance", user.getBalance())
                    .put("pendingAmount", orderService.getSellerPendingAmount(username))
                    .put("avatar", user.getAvatar())
                    .put("role", user.getRole())
                    .put("createdAt", user.getCreatedAt())
                    .put("lastActiveTime", user.getLastActiveTime())
                    .toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    @PutMapping("/user")
    public Map<String, Object> updateUser(@RequestBody Map<String, Object> request, HttpSession session) {
        try {
            String username = getLoginUsername(session);
            // 仅更新资料；改密走 /api/user/change-password（需绑定邮箱 + 邮箱验证码）
            User updated = userService.updateUser(
                username,
                (String) request.get("nickname"),
                (String) request.get("email"),
                (String) request.get("phone"),
                (String) request.get("avatar")
            );
            return ok("更新成功")
                    .put("nickname", updated.getNickname())
                    .put("balance", updated.getBalance())
                    .put("avatar", updated.getAvatar())
                    .toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    @GetMapping("/user/products")
    public Map<String, Object> getUserProducts(HttpSession session) {
        String username = getLoginUsername(session);
        List<Product> products = productService.getUserProducts(username);
        return ok("获取成功").put("products", products).put("total", products.size()).toMap();
    }

    // ========== 订单 ==========
    @PostMapping("/orders")
    public Map<String, Object> createOrder(@RequestBody Map<String, Object> request, HttpSession session) {
        try {
            String username = getLoginUsername(session);
            Long productId = Long.parseLong(request.get("productId").toString());

            int quantity = 1;
            if (request.containsKey("quantity") && request.get("quantity") != null) {
                quantity = Integer.parseInt(request.get("quantity").toString());
            }

            Order order = orderService.createOrder(
                username, productId,
                request.get("productPrice") != null ? new BigDecimal(request.get("productPrice").toString()) : null,
                (String) request.get("productName"),
                (String) request.get("category"),
                (String) request.get("imageUrl"),
                (String) request.get("receiverName"),
                (String) request.get("receiverPhone"),
                (String) request.get("receiverAddress"),
                (String) request.get("paymentMethod"),
                quantity
            );

            notificationService.push(order.getSellerUsername(), "order", "新订单",
                "买家购买了「" + order.getProductName() + "」，请及时发货", "order", String.valueOf(order.getId()));
            notificationService.push(username, "order", "下单成功",
                "「" + order.getProductName() + "」下单成功，等待卖家发货", "order", String.valueOf(order.getId()));

            return ok("订单创建成功").put("orderId", order.getId()).toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/orders/batch")
    public Map<String, Object> createOrdersBatch(@RequestBody Map<String, Object> request, HttpSession session) {
        try {
            String username = getLoginUsername(session);
            Object itemsObj = request.get("items");
            if (!(itemsObj instanceof List) || ((List<?>) itemsObj).isEmpty()) {
                return fail("没有可购买的商品").toMap();
            }

            List<OrderService.OrderItemRequest> items = new java.util.ArrayList<>();
            for (Object o : (List<Object>) itemsObj) {
                Map<String, Object> m = (Map<String, Object>) o;
                int quantity = 1;
                if (m.get("quantity") != null) {
                    quantity = Integer.parseInt(m.get("quantity").toString());
                }
                items.add(new OrderService.OrderItemRequest(
                    Long.parseLong(m.get("productId").toString()),
                    m.get("productPrice") != null ? new BigDecimal(m.get("productPrice").toString()) : null,
                    (String) m.get("productName"),
                    (String) m.get("category"),
                    (String) m.get("imageUrl"),
                    quantity
                ));
            }

            List<Long> cartItemIds = new java.util.ArrayList<>();
            Object cartIdsObj = request.get("cartItemIds");
            if (cartIdsObj instanceof List) {
                for (Object id : (List<Object>) cartIdsObj) {
                    if (id != null) cartItemIds.add(Long.parseLong(id.toString()));
                }
            }

            List<Order> orders = orderService.createOrders(
                username, items,
                (String) request.get("receiverName"),
                (String) request.get("receiverPhone"),
                (String) request.get("receiverAddress"),
                (String) request.get("paymentMethod"),
                cartItemIds
            );

            for (Order order : orders) {
                notificationService.push(order.getSellerUsername(), "order", "新订单",
                    "买家购买了「" + order.getProductName() + "」，请及时发货", "order", String.valueOf(order.getId()));
            }
            notificationService.push(username, "order", "下单成功",
                "成功下单 " + orders.size() + " 件商品，等待卖家发货", "order", "");

            List<Long> orderIds = orders.stream().map(Order::getId).toList();
            return ok("订单创建成功").put("orderIds", orderIds).put("count", orders.size()).toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    @GetMapping("/orders")
    public Map<String, Object> getOrders(@RequestParam(required = false) String status,
                                          HttpSession session) {
        String username = getLoginUsername(session);
        List<Order> orders = orderService.getBuyerOrders(username, status);
        return ok("获取成功").put("orders", orders).put("total", orders.size()).toMap();
    }

    @GetMapping("/orders/{id}")
    public Map<String, Object> getOrderById(@PathVariable Long id, HttpSession session) {
        try {
            String username = getLoginUsername(session);
            return orderService.getOrderById(id, username)
                    .map(order -> ok("获取成功").put("order", order).toMap())
                    .orElseGet(() -> fail("订单不存在").toMap());
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    @PutMapping("/orders/{id}")
    public Map<String, Object> updateOrderStatus(@PathVariable Long id,
                                                  @RequestBody Map<String, String> request,
                                                  HttpSession session) {
        try {
            String username = getLoginUsername(session);
            String status = request.get("status");
            String trackingNumber = request.get("trackingNumber");
            Order order = orderService.updateOrderStatus(id, status, trackingNumber, username);
            if ("shipped".equals(status)) {
                notificationService.push(order.getUsername(), "order", "卖家已发货",
                    "「" + order.getProductName() + "」已发货" +
                        (trackingNumber != null && !trackingNumber.isEmpty() ? "，运单号 " + trackingNumber : ""),
                    "order", String.valueOf(order.getId()));
            } else if ("received".equals(status)) {
                notificationService.push(order.getSellerUsername(), "order", "买家已收货",
                    "「" + order.getProductName() + "」买家已确认收货", "order", String.valueOf(order.getId()));
            }
            return ok("状态更新成功").toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    @DeleteMapping("/orders/{id}")
    public Map<String, Object> deleteOrder(@PathVariable Long id, HttpSession session) {
        try {
            String username = getLoginUsername(session);
            orderService.deleteOrder(id, username);
            return ok("删除成功").toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    /** 余额支付待付款订单 */
    @PostMapping("/orders/{id}/pay")
    public Map<String, Object> payOrder(@PathVariable Long id,
                                        @RequestBody(required = false) Map<String, Object> body,
                                        HttpSession session) {
        try {
            String username = getLoginUsername(session);
            Long userCouponId = null;
            if (body != null && body.get("userCouponId") != null) {
                String v = String.valueOf(body.get("userCouponId"));
                if (!v.isBlank() && !"null".equals(v)) userCouponId = Long.parseLong(v);
            }
            Order order = orderService.payOrder(id, username, userCouponId);
            notificationService.push(order.getSellerUsername(), "order", "新订单",
                "「" + order.getProductName() + "」有新订单，请及时发货", "order", String.valueOf(order.getId()));
            return ok("支付成功，等待卖家发货").put("order", order).toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    /** 物流轨迹（演示版） */
    @GetMapping("/orders/{id}/tracking")
    public Map<String, Object> orderTracking(@PathVariable Long id, HttpSession session) {
        try {
            String username = getLoginUsername(session);
            return orderService.getOrderById(id, username)
                    .map(order -> logisticsService.track(order))
                    .orElseGet(() -> fail("订单不存在").toMap());
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    @PostMapping("/orders/{id}/cancel")
    public Map<String, Object> cancelOrder(@PathVariable Long id, HttpSession session) {
        try {
            String username = getLoginUsername(session);
            Order order = orderService.cancelOrder(id, username);
            notificationService.push(order.getSellerUsername(), "order", "订单已取消",
                "「" + order.getProductName() + "」的订单已被买家取消", "order", String.valueOf(order.getId()));
            return ok("订单已取消").toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    /** 买家申请售后（发货后/收货后） */
    @PostMapping("/orders/{id}/after-sale")
    public Map<String, Object> applyAfterSale(@PathVariable Long id,
                                              @RequestBody(required = false) Map<String, String> body,
                                              HttpSession session) {
        try {
            String username = getLoginUsername(session);
            String reason = body != null ? body.get("reason") : null;
            Order order = orderService.applyAfterSale(id, username, reason);
            String saleReason = order.getAfterSaleReason() != null ? order.getAfterSaleReason() : "";
            notificationService.push(order.getSellerUsername(), "order", "售后申请",
                "「" + order.getProductName() + "」买家申请退款：" + saleReason,
                "order", String.valueOf(order.getId()));
            return ok("售后申请已提交，等待卖家处理").toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    /** 卖家同意售后：进入待买家寄回（不立即退款） */
    @PostMapping("/orders/{id}/after-sale/approve")
    public Map<String, Object> approveAfterSale(@PathVariable Long id, HttpSession session) {
        try {
            String username = getLoginUsername(session);
            Order order = orderService.approveAfterSale(id, username);
            notificationService.push(order.getUsername(), "order", "卖家已同意退货",
                "「" + order.getProductName() + "」卖家同意退货，请尽快寄回并填写退货运单号",
                "order", String.valueOf(order.getId()));
            return ok("已同意退货，请等待买家寄回").toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    /** 买家填写退货运单号并寄回 */
    @PostMapping("/orders/{id}/return-tracking")
    public Map<String, Object> submitReturnTracking(@PathVariable Long id,
                                                    @RequestBody Map<String, Object> body,
                                                    HttpSession session) {
        try {
            String username = getLoginUsername(session);
            Object no = body.get("trackingNumber");
            Order order = orderService.submitReturnTracking(id, username, no == null ? null : String.valueOf(no));
            notificationService.push(order.getSellerUsername(), "order", "买家已寄回退货",
                "「" + order.getProductName() + "」退货运单号：" + order.getReturnTrackingNumber()
                    + "，确认收到退货后将立即退款", "order", String.valueOf(order.getId()));
            return ok("退货已寄出，等待卖家确认收货").put("order", order).toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    /** 卖家确认收到退货 → 退款 */
    @PostMapping("/orders/{id}/return-received")
    public Map<String, Object> confirmReturnReceived(@PathVariable Long id, HttpSession session) {
        try {
            String username = getLoginUsername(session);
            Order order = orderService.confirmReturnReceived(id, username);
            notificationService.push(order.getUsername(), "order", "退款已完成",
                "「" + order.getProductName() + "」卖家已确认收到退货，货款已退回你的余额",
                "order", String.valueOf(order.getId()));
            return ok("已确认收货，货款已退回买家").toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    /** 买家撤销售后申请（尚未寄回时） */
    @PostMapping("/orders/{id}/after-sale/cancel")
    public Map<String, Object> cancelAfterSale(@PathVariable Long id, HttpSession session) {
        try {
            String username = getLoginUsername(session);
            Order order = orderService.cancelAfterSale(id, username);
            notificationService.push(order.getSellerUsername(), "order", "售后申请已撤回",
                "「" + order.getProductName() + "」买家撤回了售后申请", "order", String.valueOf(order.getId()));
            return ok("售后申请已撤回").toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    /** 卖家拒绝售后 */
    @PostMapping("/orders/{id}/after-sale/reject")
    public Map<String, Object> rejectAfterSale(@PathVariable Long id, HttpSession session) {
        try {
            String username = getLoginUsername(session);
            Order order = orderService.rejectAfterSale(id, username);
            notificationService.push(order.getUsername(), "order", "售后被拒绝",
                "「" + order.getProductName() + "」卖家拒绝了售后申请，如有疑问请联系客服",
                "order", String.valueOf(order.getId()));
            return ok("已拒绝售后申请").toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    @PostMapping("/orders/{id}/complete")
    public Map<String, Object> completeOrder(@PathVariable Long id, HttpSession session) {
        try {
            String username = getLoginUsername(session);
            Order order = orderService.completeOrder(id, username);
            notificationService.push(order.getSellerUsername(), "order", "订单已完成",
                "「" + order.getProductName() + "」订单已完成", "order", String.valueOf(order.getId()));
            return ok("订单已完成").toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    /** 订单评价（确认收货后：三项星级 + 图/视频） */
    @PostMapping("/orders/{id}/review")
    public Map<String, Object> reviewOrder(@PathVariable Long id,
                                           @RequestBody Map<String, Object> request,
                                           HttpSession session) {
        try {
            String username = getLoginUsername(session);
            int ratingGoods = parseStars(request.get("ratingGoods"));
            int ratingService = parseStars(request.get("ratingService"));
            int ratingDelivery = parseStars(request.get("ratingDelivery"));
            // 兼容旧字段：只传 rating 时三项同分
            if (request.get("ratingGoods") == null && request.get("rating") != null) {
                int legacy = parseStars(request.get("rating"));
                ratingGoods = legacy;
                ratingService = legacy;
                ratingDelivery = legacy;
            }
            String content = request.get("content") == null ? "" : String.valueOf(request.get("content"));
            if (content.length() > 500) {
                return fail("评价内容不能超过 500 字").toMap();
            }
            sensitiveWordService.check(content);
            String images = joinList(request.get("images"));
            String videos = joinList(request.get("videos"));

            Review review = reviewService.reviewOrder(
                    id, username, ratingGoods, ratingService, ratingDelivery, content, images, videos);
            int avg = review.getRating() == null ? 5 : review.getRating();
            notificationService.push(review.getSellerUsername(), "review", "收到新评价",
                "买家给出 " + avg + " 星评价" + (content.isEmpty() ? "" : "：" + content),
                "order", String.valueOf(id));
            return ok("评价成功").put("review", review).toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    private int parseStars(Object raw) {
        int v = Integer.parseInt(String.valueOf(raw == null ? 5 : raw));
        if (v < 1 || v > 5) {
            throw new RuntimeException("评分需在 1-5 星之间");
        }
        return v;
    }

    private String joinList(Object raw) {
        if (raw == null) return "";
        if (raw instanceof List<?> list) {
            return list.stream()
                    .filter(x -> x != null && !String.valueOf(x).isBlank())
                    .map(String::valueOf)
                    .reduce((a, b) -> a + "," + b)
                    .orElse("");
        }
        return String.valueOf(raw);
    }

    /**
     * 商品图白名单：只接受服务端上传产物 /product-images/，
     * 拒绝外链/协议头/路径穿越，防钓鱼图与存储型 XSS。
     */
    private static List<String> sanitizeProductImages(List<String> urls) {
        if (urls == null) return List.of();
        List<String> cleaned = new ArrayList<>();
        for (String raw : urls) {
            if (raw == null) continue;
            String u = raw.trim();
            if (u.isEmpty()) continue;
            if (!u.startsWith("/product-images/")
                    || u.contains("..") || u.contains("\\")
                    || u.contains("://") || u.contains("\n") || u.contains("\r")) {
                throw new RuntimeException("非法的图片地址");
            }
            cleaned.add(u);
        }
        return cleaned;
    }

    /** 通用媒体上传（聊天图片/视频），与评价媒体同一套校验与落盘 */
    @PostMapping("/upload-media")
    public Map<String, Object> uploadMedia(@RequestBody Map<String, Object> request, HttpSession session) {
        return uploadReviewMedia(request, session);
    }

    /** 评价媒体上传（图片 / 视频） */
    @SuppressWarnings("unchecked")
    @PostMapping("/upload-review-media")
    public Map<String, Object> uploadReviewMedia(@RequestBody Map<String, Object> request, HttpSession session) {
        try {
            getLoginUsername(session);
            Object mediaObj = request.get("media");
            List<String> mediaList;
            if (mediaObj instanceof List) {
                mediaList = (List<String>) mediaObj;
            } else if (mediaObj instanceof String) {
                mediaList = List.of((String) mediaObj);
            } else {
                return fail("媒体数据为空").toMap();
            }
            List<String> urls = fileUploadService.uploadReviewMedia(mediaList);
            return ok("上传成功").put("urls", urls).toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    /** 卖家经营看板：商品数、成交、待结算/已到账收入、近 14 天趋势 */
    @GetMapping("/seller/stats")
    public Map<String, Object> sellerStats(HttpSession session) {
        try {
            String username = getLoginUsername(session);
            return ok("获取成功").put("stats", orderService.sellerStats(username)).toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    @GetMapping("/seller/orders")
    public Map<String, Object> getSellerOrders(@RequestParam(required = false) String status,
                                                HttpSession session) {
        String username = getLoginUsername(session);
        List<Order> orders = orderService.getSellerOrders(username, status);
        return ok("获取成功").put("orders", orders).put("total", orders.size()).toMap();
    }

    // ========== 地址 ==========
    @GetMapping("/addresses")
    public Map<String, Object> getAddresses(HttpSession session) {
        String username = getLoginUsername(session);
        List<Address> addresses = addressService.getAddresses(username);
        return ok("获取成功").put("addresses", addresses).put("total", addresses.size()).toMap();
    }

    @PostMapping("/addresses")
    public Map<String, Object> addAddress(@RequestBody Map<String, Object> request, HttpSession session) {
        try {
            String username = getLoginUsername(session);

            Address address = new Address();
            address.setUsername(username);
            address.setReceiverName((String) request.get("receiverName"));
            address.setPhone((String) request.get("phone"));
            address.setProvince((String) request.get("province"));
            address.setCity((String) request.get("city"));
            address.setDistrict((String) request.get("district"));
            address.setDetail((String) request.get("detail"));
            address.setIsDefault(Boolean.TRUE.equals(request.get("isDefault")));

            Address saved = addressService.addAddress(address);
            return ok("地址添加成功").put("addressId", saved.getId()).toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    @PutMapping("/addresses/{id}")
    public Map<String, Object> updateAddress(@PathVariable Long id,
                                              @RequestBody Map<String, Object> request,
                                              HttpSession session) {
        try {
            String username = getLoginUsername(session);

            Address updates = new Address();
            if (request.containsKey("receiverName")) updates.setReceiverName((String) request.get("receiverName"));
            if (request.containsKey("phone")) updates.setPhone((String) request.get("phone"));
            if (request.containsKey("province")) updates.setProvince((String) request.get("province"));
            if (request.containsKey("city")) updates.setCity((String) request.get("city"));
            if (request.containsKey("district")) updates.setDistrict((String) request.get("district"));
            if (request.containsKey("detail")) updates.setDetail((String) request.get("detail"));
            if (request.containsKey("isDefault")) updates.setIsDefault(Boolean.TRUE.equals(request.get("isDefault")));

            addressService.updateAddress(id, updates, username);
            return ok("更新成功").toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    @DeleteMapping("/addresses/{id}")
    public Map<String, Object> deleteAddress(@PathVariable Long id, HttpSession session) {
        try {
            String username = getLoginUsername(session);
            addressService.deleteAddress(id, username);
            return ok("删除成功").toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }
}
