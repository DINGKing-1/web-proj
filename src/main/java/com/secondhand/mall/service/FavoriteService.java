package com.secondhand.mall.service;

import com.secondhand.mall.entity.Favorite;
import com.secondhand.mall.entity.Product;
import com.secondhand.mall.repository.FavoriteRepository;
import com.secondhand.mall.repository.ProductRepository;
import com.secondhand.mall.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public FavoriteService(FavoriteRepository favoriteRepository, ProductRepository productRepository,
                           UserRepository userRepository) {
        this.favoriteRepository = favoriteRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    public boolean toggleFavorite(String username, Long productId) {
        Optional<Favorite> existing = favoriteRepository.findByUsernameAndProductId(username, productId);
        if (existing.isPresent()) {
            favoriteRepository.delete(existing.get());
            return false;
        }

        productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("商品不存在"));

        Favorite fav = new Favorite(username, productId);
        favoriteRepository.save(fav);
        return true;
    }

    public boolean isFavorited(String username, Long productId) {
        return favoriteRepository.existsByUsernameAndProductId(username, productId);
    }

    public List<Map<String, Object>> getFavorites(String username) {
        List<Favorite> favorites = favoriteRepository.findByUsernameOrderByCreatedAtDesc(username);
        List<Map<String, Object>> result = new ArrayList<>();

        for (Favorite fav : favorites) {
            productRepository.findById(fav.getProductId()).ifPresent(product -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("favoriteId", fav.getId());
                item.put("createdAt", fav.getCreatedAt());
                item.put("id", product.getId());
                item.put("name", product.getName());
                item.put("category", product.getCategory());
                item.put("originalPrice", product.getOriginalPrice());
                item.put("secondHandPrice", product.getSecondHandPrice());
                item.put("status", product.getStatus());
                item.put("imageUrl", product.getImageUrl());
                item.put("description", product.getDescription());
                item.put("sellerName", product.getSellerName());

                String avatar = product.getSellerAvatar();
                if (product.getSellerUsername() != null && !product.getSellerUsername().isEmpty()) {
                    userRepository.findByUsername(product.getSellerUsername()).ifPresent(u -> {
                        item.put("sellerAvatar", u.getAvatar());
                        if (u.getNickname() != null && !u.getNickname().isEmpty()) {
                            item.put("sellerName", u.getNickname());
                        }
                    });
                }
                if (!item.containsKey("sellerAvatar")) {
                    item.put("sellerAvatar", avatar);
                }

                result.add(item);
            });
        }

        return result;
    }

    public void removeFavorite(String username, Long favoriteId) {
        Favorite fav = favoriteRepository.findById(favoriteId)
                .orElseThrow(() -> new RuntimeException("收藏不存在"));
        if (!fav.getUsername().equals(username)) {
            throw new RuntimeException("无权操作");
        }
        favoriteRepository.delete(fav);
    }
}
