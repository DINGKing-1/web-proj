package com.secondhand.mall.controller;

import com.secondhand.mall.dto.LoginRequest;
import com.secondhand.mall.dto.RegisterRequest;
import com.secondhand.mall.entity.*;
import com.secondhand.mall.service.*;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final ProductService productService;
    private final CommentService commentService;
    private final UserService userService;
    private final CartService cartService;
    private final OrderService orderService;
    private final AddressService addressService;

    public ApiController(ProductService productService, CommentService commentService,
                         UserService userService, CartService cartService,
                         OrderService orderService, AddressService addressService) {
        this.productService = productService;
        this.commentService = commentService;
        this.userService = userService;
        this.cartService = cartService;
        this.orderService = orderService;
        this.addressService = addressService;
    }

    // ========== 辅助方法 ==========
    private String getLoginUsername(HttpSession session) {
        Object username = session.getAttribute("username");
        if (username == null) {
            throw new com.secondhand.mall.exception.UnauthorizedException("请先登录");
        }
        return username.toString();
    }

    private Map<String, Object> ok(String message) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("success", true);
        map.put("message", message);
        return map;
    }

    private Map<String, Object> fail(String message) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("success", false);
        map.put("message", message);
        return map;
    }

    // ========== 认证 ==========
    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest request, HttpSession session) {
        try {
            User user = userService.login(request, session.getId());
            session.setAttribute("username", user.getUsername());
            session.setAttribute("nickname", user.getNickname());
            session.setAttribute("role", user.getRole());

            Map<String, Object> result = ok("登录成功");
            result.put("username", user.getUsername());
            result.put("nickname", user.getNickname());
            result.put("balance", user.getBalance());
            result.put("avatar", user.getAvatar());
            result.put("role", user.getRole());
            return result;
        } catch (RuntimeException e) {
            return fail(e.getMessage());
        }
    }

    @PostMapping("/register")
    public Map<String, Object> register(@Valid @RequestBody RegisterRequest request, HttpSession session) {
        try {
            User user = userService.register(request);
            userService.setSessionId(user.getUsername(), session.getId());
            session.setAttribute("username", user.getUsername());
            session.setAttribute("nickname", user.getNickname());
            session.setAttribute("role", user.getRole());

            Map<String, Object> result = ok("注册成功");
            result.put("username", user.getUsername());
            result.put("nickname", user.getNickname());
            result.put("balance", user.getBalance());
            result.put("avatar", user.getAvatar());
            return result;
        } catch (RuntimeException e) {
            return fail(e.getMessage());
        }
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpSession session) {
        String username = (String) session.getAttribute("username");
        if (username != null) {
            userService.setSessionId(username, null);
        }
        session.invalidate();
        return ok("已退出登录");
    }

    @GetMapping("/session")
    public Map<String, Object> getSession(HttpSession session) {
        String username = (String) session.getAttribute("username");
        if (username == null) {
            return fail("未登录");
        }
        try {
            User user = userService.findByUsername(username);
            Map<String, Object> result = ok("已登录");
            result.put("username", username);
            result.put("nickname", user.getNickname());
            result.put("role", user.getRole());
            result.put("avatar", user.getAvatar());
            result.put("balance", user.getBalance());
            return result;
        } catch (RuntimeException e) {
            return fail("用户不存在");
        }
    }

    // ========== 图片上传（支持多张） ==========
    @PostMapping("/upload-image")
    public Map<String, Object> uploadImage(@RequestBody Map<String, Object> request) {
        try {
            // 支持单张 base64 或多张
            Object imageObj = request.get("image");
            @SuppressWarnings("unchecked")
            List<String> imageList = null;

            if (imageObj instanceof List) {
                imageList = (List<String>) imageObj;
            } else if (imageObj instanceof String) {
                imageList = List.of((String) imageObj);
            }

            if (imageList == null || imageList.isEmpty()) {
                return fail("图片数据为空");
            }

            String uploadDir = System.getProperty("user.dir") + File.separator + "product-images";
            File dir = new File(uploadDir);
            if (!dir.exists()) dir.mkdirs();

            List<String> urls = new ArrayList<>();
            for (String base64Data : imageList) {
                String[] parts = base64Data.split(",");
                String header = parts[0];
                String data = parts.length > 1 ? parts[1] : parts[0];

                String ext = "jpg";
                if (header.contains("png")) ext = "png";
                else if (header.contains("gif")) ext = "gif";
                else if (header.contains("webp")) ext = "webp";

                String filename = UUID.randomUUID().toString().substring(0, 8) + "_" + System.currentTimeMillis() + "." + ext;
                File file = new File(dir, filename);
                byte[] imageBytes = Base64.getDecoder().decode(data);
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(imageBytes);
                }
                urls.add("/product-images/" + filename);
            }

            Map<String, Object> result = ok("上传成功");
            result.put("imageUrls", urls);
            result.put("imageUrl", urls.get(0)); // 兼容旧代码
            return result;
        } catch (Exception e) {
            return fail("上传失败: " + e.getMessage());
        }
    }

    // ========== 商品 ==========
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
            product.setSellerName((String) request.get("sellerName"));
            product.setSellerPhone((String) request.get("sellerPhone"));
            product.setSellerUsername(username);

            // 从用户信息中获取头像
            try {
                User seller = userService.findByUsername(username);
                product.setSellerAvatar(seller.getAvatar());
            } catch (Exception ignored) {}

            // 多图片：接收逗号字符串或数组
            Object imageObj = request.get("imageUrl");
            if (imageObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> urls = (List<String>) imageObj;
                product.setImageUrls(urls);
            } else if (imageObj instanceof String && !((String) imageObj).isEmpty()) {
                product.setImageUrl((String) imageObj);
            }

            // 库存数量
            Object qtyObj = request.get("quantity");
            if (qtyObj != null) {
                product.setQuantity(Integer.parseInt(qtyObj.toString()));
            }

            Product saved = productService.createProduct(product);

            Map<String, Object> result = ok("商品发布成功");
            result.put("productId", saved.getId());
            return result;
        } catch (RuntimeException e) {
            return fail(e.getMessage());
        }
    }

    @GetMapping("/products")
    public Map<String, Object> getProducts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) String condition,
            @RequestParam(required = false) String sortBy) {

        List<Product> products = productService.getFilteredProducts(category, minPrice, maxPrice, condition, sortBy);
        Map<String, Object> result = ok("获取成功");
        result.put("products", products);
        result.put("total", products.size());
        return result;
    }

    @GetMapping("/products/{id}")
    public Map<String, Object> getProduct(@PathVariable Long id) {
        return productService.getProductById(id)
                .map(product -> {
                    Map<String, Object> result = ok("获取成功");
                    result.put("product", product);
                    return result;
                })
                .orElseGet(() -> fail("商品不存在"));
    }

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
                    @SuppressWarnings("unchecked")
                    List<String> urls = (List<String>) imageObj;
                    updates.setImageUrls(urls);
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
            return ok("更新成功");
        } catch (RuntimeException e) {
            return fail(e.getMessage());
        }
    }

    @DeleteMapping("/products/{id}")
    public Map<String, Object> deleteProduct(@PathVariable Long id, HttpSession session) {
        try {
            String username = getLoginUsername(session);
            productService.deleteProduct(id, username);
            return ok("删除成功");
        } catch (RuntimeException e) {
            return fail(e.getMessage());
        }
    }

    // ========== 评论 ==========
    @GetMapping("/products/{id}/comments")
    public Map<String, Object> getComments(@PathVariable Long id) {
        List<Map<String, Object>> comments = commentService.getCommentsByProduct(id);
        Map<String, Object> result = ok("获取成功");
        result.put("comments", comments);
        result.put("total", comments.size());
        return result;
    }

    @PostMapping("/products/{id}/comments")
    public Map<String, Object> addComment(@PathVariable Long id,
                                           @RequestBody Map<String, String> request,
                                           HttpSession session) {
        try {
            String username = getLoginUsername(session);
            String content = request.get("content");
            commentService.addComment(id, username, content);
            return ok("评论成功");
        } catch (RuntimeException e) {
            return fail(e.getMessage());
        }
    }

    @DeleteMapping("/products/{id}/comments/{commentId}")
    public Map<String, Object> deleteComment(@PathVariable Long id,
                                              @PathVariable Long commentId,
                                              HttpSession session) {
        try {
            String username = getLoginUsername(session);
            commentService.deleteComment(id, commentId, username);
            return ok("删除成功");
        } catch (RuntimeException e) {
            return fail(e.getMessage());
        }
    }

    // ========== 购物车 ==========
    @PostMapping("/cart")
    public Map<String, Object> addToCart(@RequestBody Map<String, Object> request, HttpSession session) {
        try {
            String username = getLoginUsername(session);
            Long productId = Long.parseLong(request.get("productId").toString());
            cartService.addToCart(username, productId);
            return ok("已加入购物车");
        } catch (RuntimeException e) {
            return fail(e.getMessage());
        }
    }

    @GetMapping("/cart")
    public Map<String, Object> getCart(HttpSession session) {
        String username = getLoginUsername(session);
        List<Map<String, Object>> items = cartService.getCart(username);
        Map<String, Object> result = ok("获取成功");
        result.put("items", items);
        result.put("total", items.size());
        return result;
    }

    @DeleteMapping("/cart/{id}")
    public Map<String, Object> removeFromCart(@PathVariable Long id, HttpSession session) {
        try {
            String username = getLoginUsername(session);
            cartService.removeFromCart(id, username);
            return ok("已移除");
        } catch (RuntimeException e) {
            return fail(e.getMessage());
        }
    }

    // ========== 用户信息 ==========
    @GetMapping("/user")
    public Map<String, Object> getUserInfo(HttpSession session) {
        String username = getLoginUsername(session);
        try {
            User user = userService.findByUsername(username);
            Map<String, Object> result = ok("获取成功");
            result.put("username", user.getUsername());
            result.put("nickname", user.getNickname());
            result.put("email", user.getEmail());
            result.put("phone", user.getPhone());
            result.put("balance", user.getBalance());
            result.put("avatar", user.getAvatar());
            result.put("role", user.getRole());
            result.put("createdAt", user.getCreatedAt());
            return result;
        } catch (RuntimeException e) {
            return fail(e.getMessage());
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
            Map<String, Object> result = ok("更新成功");
            result.put("nickname", updated.getNickname());
            result.put("balance", updated.getBalance());
            result.put("avatar", updated.getAvatar());
            return result;
        } catch (RuntimeException e) {
            return fail(e.getMessage());
        }
    }

    @GetMapping("/user/products")
    public Map<String, Object> getUserProducts(HttpSession session) {
        String username = getLoginUsername(session);
        List<Product> products = productService.getUserProducts(username);
        Map<String, Object> result = ok("获取成功");
        result.put("products", products);
        result.put("total", products.size());
        return result;
    }

    // ========== 订单 ==========
    @PostMapping("/orders")
    public Map<String, Object> createOrder(@RequestBody Map<String, Object> request, HttpSession session) {
        try {
            String username = getLoginUsername(session);
            Long productId = Long.parseLong(request.get("productId").toString());

            Order order = orderService.createOrder(
                username,
                productId,
                new BigDecimal(request.get("productPrice").toString()),
                (String) request.get("productName"),
                (String) request.get("category"),
                (String) request.get("imageUrl"),
                (String) request.get("receiverName"),
                (String) request.get("receiverPhone"),
                (String) request.get("receiverAddress"),
                (String) request.get("paymentMethod")
            );

            Map<String, Object> result = ok("订单创建成功");
            result.put("orderId", order.getId());
            return result;
        } catch (RuntimeException e) {
            return fail(e.getMessage());
        }
    }

    @GetMapping("/orders")
    public Map<String, Object> getOrders(@RequestParam(required = false) String status,
                                          HttpSession session) {
        String username = getLoginUsername(session);
        List<Order> orders = orderService.getBuyerOrders(username, status);
        Map<String, Object> result = ok("获取成功");
        result.put("orders", orders);
        result.put("total", orders.size());
        return result;
    }

    @GetMapping("/orders/{id}")
    public Map<String, Object> getOrderById(@PathVariable Long id, HttpSession session) {
        getLoginUsername(session); // 验证登录
        return orderService.getOrderById(id)
                .map(order -> {
                    Map<String, Object> result = ok("获取成功");
                    result.put("order", order);
                    return result;
                })
                .orElseGet(() -> fail("订单不存在"));
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
            return ok("状态更新成功");
        } catch (RuntimeException e) {
            return fail(e.getMessage());
        }
    }

    @DeleteMapping("/orders/{id}")
    public Map<String, Object> deleteOrder(@PathVariable Long id, HttpSession session) {
        try {
            String username = getLoginUsername(session);
            orderService.deleteOrder(id, username);
            return ok("删除成功");
        } catch (RuntimeException e) {
            return fail(e.getMessage());
        }
    }

    @PostMapping("/orders/{id}/cancel")
    public Map<String, Object> cancelOrder(@PathVariable Long id, HttpSession session) {
        try {
            String username = getLoginUsername(session);
            orderService.cancelOrder(id, username);
            return ok("订单已取消，余额已退还");
        } catch (RuntimeException e) {
            return fail(e.getMessage());
        }
    }

    @PostMapping("/orders/{id}/complete")
    public Map<String, Object> completeOrder(@PathVariable Long id, HttpSession session) {
        try {
            String username = getLoginUsername(session);
            orderService.completeOrder(id, username);
            return ok("订单已完成");
        } catch (RuntimeException e) {
            return fail(e.getMessage());
        }
    }

    @GetMapping("/seller/orders")
    public Map<String, Object> getSellerOrders(@RequestParam(required = false) String status,
                                                HttpSession session) {
        String username = getLoginUsername(session);
        List<Order> orders = orderService.getSellerOrders(username, status);
        Map<String, Object> result = ok("获取成功");
        result.put("orders", orders);
        result.put("total", orders.size());
        return result;
    }

    // ========== 地址 ==========
    @GetMapping("/addresses")
    public Map<String, Object> getAddresses(HttpSession session) {
        String username = getLoginUsername(session);
        List<Address> addresses = addressService.getAddresses(username);
        Map<String, Object> result = ok("获取成功");
        result.put("addresses", addresses);
        result.put("total", addresses.size());
        return result;
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
            Map<String, Object> result = ok("地址添加成功");
            result.put("addressId", saved.getId());
            return result;
        } catch (RuntimeException e) {
            return fail(e.getMessage());
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
            return ok("更新成功");
        } catch (RuntimeException e) {
            return fail(e.getMessage());
        }
    }

    @DeleteMapping("/addresses/{id}")
    public Map<String, Object> deleteAddress(@PathVariable Long id, HttpSession session) {
        try {
            String username = getLoginUsername(session);
            addressService.deleteAddress(id, username);
            return ok("删除成功");
        } catch (RuntimeException e) {
            return fail(e.getMessage());
        }
    }
}
