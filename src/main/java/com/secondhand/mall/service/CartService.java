package com.secondhand.mall.service;

import com.secondhand.mall.dto.CartItemDTO;
import com.secondhand.mall.entity.CartItem;
import com.secondhand.mall.entity.Product;
import com.secondhand.mall.repository.CartItemRepository;
import com.secondhand.mall.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

    public CartService(CartItemRepository cartItemRepository, ProductRepository productRepository) {
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
    }

    public CartItem addToCart(String username, Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("商品不存在"));

        if ("sold".equals(product.getStatus())) {
            throw new RuntimeException("该商品已售出");
        }

        if (username.equals(product.getSellerUsername())) {
            throw new RuntimeException("不能添加自己发布的商品");
        }

        if (cartItemRepository.existsByUsernameAndProductId(username, productId)) {
            throw new RuntimeException("该商品已在购物车中");
        }

        CartItem item = new CartItem(username, productId);
        return cartItemRepository.save(item);
    }

    public List<CartItemDTO> getCart(String username) {
        List<CartItem> items = cartItemRepository.findByUsernameOrderByCreatedAtDesc(username);

        return items.stream().map(item -> {
            Product product = productRepository.findById(item.getProductId()).orElse(null);

            CartItemDTO dto = new CartItemDTO();
            dto.setId(item.getId());
            dto.setProductId(item.getProductId());

            if (product != null && "active".equals(product.getStatus())) {
                dto.setProductName(product.getName());
                dto.setPrice(product.getSecondHandPrice());
                dto.setImage(product.getImageUrl());
                dto.setAvailable(true);
            } else {
                dto.setProductName("商品已下架");
                dto.setAvailable(false);
            }

            return dto;
        }).collect(Collectors.toList());
    }

    public void removeFromCart(Long cartItemId, String currentUsername) {
        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new RuntimeException("购物车项不存在"));

        if (!item.getUsername().equals(currentUsername)) {
            throw new RuntimeException("无权操作");
        }

        cartItemRepository.delete(item);
    }

    public void clearCart(String username) {
        cartItemRepository.deleteByUsername(username);
    }

    public int getCartCount(String username) {
        return cartItemRepository.countByUsername(username);
    }
}
