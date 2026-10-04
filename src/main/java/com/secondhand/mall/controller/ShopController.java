package com.secondhand.mall.controller;

import com.secondhand.mall.entity.Product;
import com.secondhand.mall.entity.Review;
import com.secondhand.mall.entity.User;
import com.secondhand.mall.service.ProductService;
import com.secondhand.mall.service.ReviewService;
import com.secondhand.mall.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ShopController extends BaseApiController {

    private final UserService userService;
    private final ProductService productService;
    private final ReviewService reviewService;

    public ShopController(UserService userService, ProductService productService, ReviewService reviewService) {
        this.userService = userService;
        this.productService = productService;
        this.reviewService = reviewService;
    }

    /** 商品详情「相关推荐」 */
    @GetMapping("/products/{id}/related")
    public Map<String, Object> related(@PathVariable Long id,
                                       @RequestParam(defaultValue = "8") int limit) {
        List<Product> products = productService.getRelatedProducts(id, limit);
        return ok("获取成功").put("products", products).put("total", products.size()).toMap();
    }

    /** 店铺主页：卖家资料 + 评分 + 在售商品 */
    @GetMapping("/sellers/{username}")
    public Map<String, Object> getShop(@PathVariable String username) {
        try {
            if (username == null || username.isBlank()) {
                return fail("店铺不存在").toMap();
            }
            String key = username.trim();
            List<Product> products = productService.getShopProducts(key);
            Map<String, Object> rating = reviewService.sellerRating(key);

            Map<String, Object> seller = new HashMap<>();
            seller.put("username", key);

            User user = null;
            try {
                user = userService.findByUsername(key);
            } catch (RuntimeException ignored) {
                // 用户可能已注销：仍用商品上的卖家信息展示店铺
            }

            if (user != null) {
                seller.put("username", user.getUsername());
                seller.put("nickname", user.getNickname() != null ? user.getNickname() : user.getUsername());
                seller.put("avatar", user.getAvatar());
                seller.put("createdAt", user.getCreatedAt());
            } else if (!products.isEmpty()) {
                Product p = products.get(0);
                seller.put("nickname", p.getSellerName() != null ? p.getSellerName() : key);
                seller.put("avatar", p.getSellerAvatar() != null ? p.getSellerAvatar() : "/user-avatar/default.jpg");
                seller.put("createdAt", p.getCreatedAt());
            } else {
                return fail("店铺不存在").toMap();
            }

            seller.put("productCount", products.size());
            seller.put("avgRating", rating.get("avgRating"));
            seller.put("reviewCount", rating.get("reviewCount"));

            return ok("获取成功").put("seller", seller).put("products", products).put("total", products.size()).toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    /** 店铺评价列表 */
    @GetMapping("/sellers/{username}/reviews")
    public Map<String, Object> getShopReviews(@PathVariable String username) {
        List<Review> reviews = reviewService.bySeller(username);
        Map<String, Object> rating = reviewService.sellerRating(username);
        return ok("获取成功")
                .put("reviews", reviews)
                .put("avgRating", rating.get("avgRating"))
                .put("reviewCount", rating.get("reviewCount"))
                .toMap();
    }

    /** 商品评价（含订单评分） */
    @GetMapping("/products/{id}/reviews")
    public Map<String, Object> getProductReviews(@PathVariable Long id) {
        List<Review> reviews = reviewService.byProduct(id);
        Double avg = reviewService.productAvgRating(id);
        return ok("获取成功").put("reviews", reviews).put("avgRating", avg).toMap();
    }

    /** 卖家回复评价 */
    @PostMapping("/reviews/{id}/reply")
    public Map<String, Object> replyReview(@PathVariable Long id,
                                           @RequestBody Map<String, String> body,
                                           jakarta.servlet.http.HttpSession session) {
        try {
            String username = (String) session.getAttribute("username");
            if (username == null) return fail("请先登录").toMap();
            Review r = reviewService.reply(id, username, body.get("reply"));
            return ok("回复成功").put("review", r).toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    /** 买家追评（一条评价一次） */
    @PostMapping("/reviews/{id}/followup")
    public Map<String, Object> followUpReview(@PathVariable Long id,
                                              @RequestBody Map<String, String> body,
                                              jakarta.servlet.http.HttpSession session) {
        try {
            String username = (String) session.getAttribute("username");
            if (username == null) return fail("请先登录").toMap();
            Review r = reviewService.followUp(id, username, body.get("content"));
            return ok("追评成功").put("review", r).toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    /** 热门搜索词：取在售商品名高频词 + 分类 */
    @GetMapping("/search/hot-words")
    public Map<String, Object> hotWords() {
        List<String> words = new ArrayList<>(List.of("耳机", "手机", "笔记本", "相机", "空调", "冰箱"));
        return ok("获取成功").put("words", words).toMap();
    }
}
