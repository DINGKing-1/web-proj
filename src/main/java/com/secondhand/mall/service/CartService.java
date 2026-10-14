package com.secondhand.mall.service;

import com.secondhand.mall.constant.ProductStatus;
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
        return addToCart(username, productId, 1);
    }

    /** 加购（支持数量）：数量必须在库存范围内 */
    public CartItem addToCart(String username, Long productId, int quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("商品不存在"));

        if (ProductStatus.SOLD.equals(product.getStatus()) || !ProductStatus.ACTIVE.equals(product.getStatus())) {
            throw new RuntimeException("该商品已售出或已下架");
        }

        if (username.equals(product.getSellerUsername())) {
            throw new RuntimeException("不能添加自己发布的商品");
        }

        if (cartItemRepository.existsByUsernameAndProductId(username, productId)) {
            throw new RuntimeException("该商品已在购物车中");
        }

        int qty = Math.max(1, quantity);
        int stock = product.getQuantity() != null ? product.getQuantity() : 1;
        if (stock <= 0) {
            throw new RuntimeException("该商品库存不足");
        }
        if (qty > stock) {
            throw new RuntimeException("库存不足，最多 " + stock + " 件");
        }

        CartItem item = new CartItem(username, productId);
        item.setQuantity(qty);
        return cartItemRepository.save(item);
    }

    public List<CartItemDTO> getCart(String username) {
        List<CartItem> items = cartItemRepository.findByUsernameOrderByCreatedAtDesc(username);

        return items.stream().map(item -> {
            Product product = productRepository.findById(item.getProductId()).orElse(null);

            CartItemDTO dto = new CartItemDTO();
            dto.setId(item.getId());
            dto.setProductId(item.getProductId());

            if (product != null && ProductStatus.ACTIVE.equals(product.getStatus())) {
                dto.setProductName(product.getName());
                dto.setPrice(product.getSecondHandPrice());
                // 多图只回第一张，避免前端 img src 含逗号
                String img = product.getImageUrl();
                if (img != null && img.contains(",")) {
                    img = img.split(",")[0].trim();
                }
                dto.setImage(img);
                dto.setCategory(product.getCategory());
                dto.setAvailable(true);
                int qty = item.getQuantity() != null && item.getQuantity() > 0 ? item.getQuantity() : 1;
                int stock = product.getQuantity() != null ? product.getQuantity() : 1;
                dto.setQuantity(Math.min(qty, stock));
                dto.setStock(stock);
            } else {
                dto.setProductName("商品已下架");
                dto.setAvailable(false);
                dto.setQuantity(item.getQuantity() != null ? item.getQuantity() : 1);
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

    /** 更新购物车数量 */
    public CartItem updateQuantity(Long cartItemId, String currentUsername, int quantity) {
        if (quantity < 1) {
            throw new RuntimeException("数量至少为 1");
        }
        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new RuntimeException("购物车项不存在"));
        if (!item.getUsername().equals(currentUsername)) {
            throw new RuntimeException("无权操作");
        }
        Product product = productRepository.findById(item.getProductId()).orElse(null);
        if (product == null || !ProductStatus.ACTIVE.equals(product.getStatus())) {
            throw new RuntimeException("商品已下架");
        }
        int stock = product.getQuantity() != null ? product.getQuantity() : 1;
        if (quantity > stock) {
            throw new RuntimeException("库存不足，最多 " + stock + " 件");
        }
        item.setQuantity(quantity);
        return cartItemRepository.save(item);
    }

    public void clearCart(String username) {
        cartItemRepository.deleteByUsername(username);
    }

    public int getCartCount(String username) {
        return cartItemRepository.countByUsername(username);
    }
}
