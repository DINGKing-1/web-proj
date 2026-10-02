package com.secondhand.mall.controller;

import com.secondhand.mall.dto.LoginRequest;
import com.secondhand.mall.dto.RegisterRequest;
import com.secondhand.mall.entity.*;
import com.secondhand.mall.service.*;
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

    public ApiController(ProductService productService, CommentService commentService,
                         UserService userService, CartService cartService,
                         OrderService orderService, AddressService addressService,
                         FileUploadService fileUploadService) {
        this.productService = productService;
        this.commentService = commentService;
        this.userService = userService;
        this.cartService = cartService;
        this.orderService = orderService;
        this.addressService = addressService;
        this.fileUploadService = fileUploadService;
    }

    // ========== 认证 ==========
    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest request, HttpSession session) {
        try {
            User user = userService.login(request, session.getId());
            session.setAttribute("username", user.getUsername());
            session.setAttribute("nickname", user.getNickname());
            session.setAttribute("role", user.getRole());

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
    public Map<String, Object> register(@Valid @RequestBody RegisterRequest request, HttpSession session) {
        try {
            User user = userService.register(request, session.getId());
            session.setAttribute("username", user.getUsername());
            session.setAttribute("nickname", user.getNickname());
            session.setAttribute("role", user.getRole());

            return ok("注册成功")
                    .put("username", user.getUsername())
                    .put("nickname", user.getNickname())
                    .put("balance", user.getBalance())
                    .put("avatar", user.getAvatar())
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
    public Map<String, Object> uploadImage(@RequestBody Map<String, Object> request) {
        try {
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
                product.setImageUrls((List<String>) imageObj);
            } else if (imageObj instanceof String && !((String) imageObj).isEmpty()) {
                product.setImageUrl((String) imageObj);
            }

            Object qtyObj = request.get("quantity");
            if (qtyObj != null) {
                product.setQuantity(Integer.parseInt(qtyObj.toString()));
            }

            Product saved = productService.createProduct(product);
            return ok("商品发布成功").put("productId", saved.getId()).toMap();
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

        // 获取筛选后的商品
        List<Product> allProducts;
        boolean hasSeller = seller != null && !seller.isEmpty();
        boolean hasFilters = (category != null && !category.isEmpty()) || minPrice != null || maxPrice != null || (condition != null && !condition.isEmpty()) || hasSeller;
        boolean hasSort = sortBy != null && !sortBy.isEmpty();

        if (hasSeller && !hasFilters) {
            allProducts = productService.getSellerActiveProducts(seller);
        } else if (hasSeller) {
            allProducts = productService.getSellerActiveProducts(seller);
            // 卖家筛选后再做价格/成色/排序
            if (minPrice != null) {
                allProducts = allProducts.stream().filter(p -> p.getSecondHandPrice() != null && p.getSecondHandPrice().compareTo(minPrice) >= 0).toList();
            }
            if (maxPrice != null) {
                allProducts = allProducts.stream().filter(p -> p.getSecondHandPrice() != null && p.getSecondHandPrice().compareTo(maxPrice) <= 0).toList();
            }
            if (condition != null && !condition.isEmpty()) {
                String cond = condition;
                allProducts = allProducts.stream().filter(p -> cond.equals(p.getItemCondition())).toList();
            }
            if (category != null && !category.isEmpty()) {
                String cat = category;
                allProducts = allProducts.stream().filter(p -> cat.equals(p.getCategory())).toList();
            }
        } else if (!hasFilters && !hasSort) {
            allProducts = productService.getActiveProducts();
        } else {
            allProducts = productService.getFilteredProducts(category, minPrice, maxPrice, condition, sortBy);
        }

        if (hasSeller && hasSort) {
            final String sort = sortBy;
            allProducts = new java.util.ArrayList<>(allProducts);
            if ("price-asc".equals(sort)) {
                allProducts.sort(java.util.Comparator.comparing(Product::getSecondHandPrice));
            } else if ("price-desc".equals(sort)) {
                allProducts.sort(java.util.Comparator.comparing(Product::getSecondHandPrice).reversed());
            } else if ("sales".equals(sort) || "sales-desc".equals(sort)) {
                // 销量排序由前端列表场景使用；卖家店内按上架时间即可
                allProducts.sort(java.util.Comparator.comparing(Product::getCreatedAt).reversed());
            } else {
                allProducts.sort(java.util.Comparator.comparing(Product::getCreatedAt).reversed());
            }
        }

        // 手动分页
        int totalItems = allProducts.size();
        int totalPages = (int) Math.ceil((double) totalItems / size);
        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, totalItems);

        List<Product> pageProducts = (fromIndex < totalItems) ? allProducts.subList(fromIndex, toIndex) : List.of();

        return ok("获取成功")
                .put("products", pageProducts)
                .put("total", totalItems)
                .put("totalPages", totalPages)
                .put("currentPage", page)
                .toMap();
    }

    @GetMapping("/products/{id}")
    public Map<String, Object> getProduct(@PathVariable Long id) {
        return productService.getProductById(id)
                .map(product -> ok("获取成功").put("product", product).toMap())
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

        List<Product> allProducts = productService.searchProducts(q.trim(), category, minPrice, maxPrice, condition, sortBy);

        int totalItems = allProducts.size();
        int totalPages = (int) Math.ceil((double) totalItems / size);
        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, totalItems);

        List<Product> pageProducts = (fromIndex < totalItems) ? allProducts.subList(fromIndex, toIndex) : List.of();

        return ok("搜索成功")
                .put("products", pageProducts)
                .put("total", totalItems)
                .put("totalPages", totalPages)
                .put("currentPage", page)
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
                    updates.setImageUrls((List<String>) imageObj);
                } else if (imageObj instanceof String) {
                    updates.setImageUrl((String) imageObj);
                }
            }
            if (request.containsKey("name")) updates.setName((String) request.get("name"));
            if (request.containsKey("description")) updates.setDescription((String) request.get("description"));
            if (request.containsKey("category")) updates.setCategory((String) request.get("category"));
            if (request.containsKey("originalPrice")) updates.setOriginalPrice(new BigDecimal(request.get("originalPrice").toString()));
            if (request.containsKey("secondHandPrice")) updates.setSecondHandPrice(new BigDecimal(request.get("secondHandPrice").toString()));
            if (request.containsKey("condition")) updates.setItemCondition((String) request.get("condition"));
            if (request.containsKey("status")) updates.setStatus((String) request.get("status"));
            if (request.containsKey("quantity")) updates.setQuantity(Integer.parseInt(request.get("quantity").toString()));

            productService.updateProduct(id, updates, username);
            return ok("更新成功").toMap();
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
            cartService.addToCart(username, productId);
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
            User updated = userService.updateUser(
                username,
                (String) request.get("nickname"),
                (String) request.get("email"),
                (String) request.get("phone"),
                (String) request.get("currentPassword"),
                (String) request.get("password"),
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
                new BigDecimal(request.get("productPrice").toString()),
                (String) request.get("productName"),
                (String) request.get("category"),
                (String) request.get("imageUrl"),
                (String) request.get("receiverName"),
                (String) request.get("receiverPhone"),
                (String) request.get("receiverAddress"),
                (String) request.get("paymentMethod"),
                quantity
            );

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
                    new BigDecimal(m.get("productPrice").toString()),
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
            orderService.updateOrderStatus(id, status, trackingNumber, username);
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

    @PostMapping("/orders/{id}/cancel")
    public Map<String, Object> cancelOrder(@PathVariable Long id, HttpSession session) {
        try {
            String username = getLoginUsername(session);
            orderService.cancelOrder(id, username);
            return ok("订单已取消，余额已退还").toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    @PostMapping("/orders/{id}/complete")
    public Map<String, Object> completeOrder(@PathVariable Long id, HttpSession session) {
        try {
            String username = getLoginUsername(session);
            orderService.completeOrder(id, username);
            return ok("订单已完成").toMap();
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

    @PostMapping("/internal/shutdown")
    public Map<String, Object> internalShutdown() {
        new Thread(() -> {
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            System.exit(0);
        }).start();
        return ok("应用即将关闭").toMap();
    }
}
