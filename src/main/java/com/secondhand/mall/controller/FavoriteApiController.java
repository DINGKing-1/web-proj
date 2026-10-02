package com.secondhand.mall.controller;

import com.secondhand.mall.service.FavoriteService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/favorites")
public class FavoriteApiController extends BaseApiController {

    private final FavoriteService favoriteService;

    public FavoriteApiController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @PostMapping("/toggle")
    public Map<String, Object> toggle(@RequestBody Map<String, Object> request, HttpSession session) {
        try {
            String username = getLoginUsername(session);
            Long productId = Long.parseLong(request.get("productId").toString());
            boolean added = favoriteService.toggleFavorite(username, productId);
            return ok(added ? "已收藏" : "已取消收藏").put("favorited", added).toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    @GetMapping("/check/{productId}")
    public Map<String, Object> check(@PathVariable Long productId, HttpSession session) {
        try {
            String username = getLoginUsername(session);
            boolean favorited = favoriteService.isFavorited(username, productId);
            return ok("获取成功").put("favorited", favorited).toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    @GetMapping
    public Map<String, Object> list(HttpSession session) {
        try {
            String username = getLoginUsername(session);
            List<Map<String, Object>> favorites = favoriteService.getFavorites(username);
            return ok("获取成功").put("favorites", favorites).put("total", favorites.size()).toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    @DeleteMapping("/{favoriteId}")
    public Map<String, Object> remove(@PathVariable Long favoriteId, HttpSession session) {
        try {
            String username = getLoginUsername(session);
            favoriteService.removeFavorite(username, favoriteId);
            return ok("已取消收藏").toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }
}
