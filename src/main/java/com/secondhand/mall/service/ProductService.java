package com.secondhand.mall.service;

import com.secondhand.mall.entity.Product;
import com.secondhand.mall.entity.User;
import com.secondhand.mall.repository.ProductRepository;
import com.secondhand.mall.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public ProductService(ProductRepository productRepository, UserRepository userRepository) {
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    private void fillSellerAvatar(Product product) {
        if (product.getSellerAvatar() == null || product.getSellerAvatar().isEmpty()) {
            if (product.getSellerUsername() != null && !product.getSellerUsername().isEmpty()) {
                userRepository.findByUsername(product.getSellerUsername()).ifPresent(user -> {
                    product.setSellerAvatar(user.getAvatar());
                });
            }
        }
    }

    private void fillSellerAvatars(List<Product> products) {
        for (Product p : products) {
            fillSellerAvatar(p);
        }
    }

    public Product createProduct(Product product) {
        if (product.getOriginalPrice().compareTo(BigDecimal.ZERO) <= 0 || product.getSecondHandPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("价格必须大于0");
        }
        if (product.getSecondHandPrice().compareTo(product.getOriginalPrice()) > 0) {
            throw new RuntimeException("二手价不能高于原价");
        }
        if (product.getQuantity() == null || product.getQuantity() < 1) {
            product.setQuantity(1);
        }
        product.setStatus("active");
        return productRepository.save(product);
    }

    public List<Product> getActiveProducts() {
        List<Product> products = productRepository.findByStatus("active");
        fillSellerAvatars(products);
        return products;
    }

    public List<Product> getProductsByCategory(String category) {
        List<Product> products = productRepository.findByStatusAndCategoryOrderByCreatedAtDesc("active", category);
        fillSellerAvatars(products);
        return products;
    }

    public Optional<Product> getProductById(Long id) {
        Optional<Product> opt = productRepository.findById(id);
        opt.ifPresent(this::fillSellerAvatar);
        return opt;
    }

    public Product updateProduct(Long id, Product updates, String currentUsername) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("商品不存在"));

        if (!product.getSellerUsername().equals(currentUsername)) {
            throw new RuntimeException("无权修改此商品");
        }

        if (updates.getName() != null) product.setName(updates.getName());
        if (updates.getDescription() != null) product.setDescription(updates.getDescription());
        if (updates.getCategory() != null) product.setCategory(updates.getCategory());
        if (updates.getOriginalPrice() != null) product.setOriginalPrice(updates.getOriginalPrice());
        if (updates.getSecondHandPrice() != null) product.setSecondHandPrice(updates.getSecondHandPrice());
        if (updates.getItemCondition() != null) product.setItemCondition(updates.getItemCondition());
        if (updates.getStatus() != null) product.setStatus(updates.getStatus());
        if (updates.getImageUrl() != null) product.setImageUrl(updates.getImageUrl());
        if (updates.getQuantity() != null) product.setQuantity(updates.getQuantity());

        return productRepository.save(product);
    }

    public void deleteProduct(Long id, String currentUsername) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("商品不存在"));

        if (!product.getSellerUsername().equals(currentUsername)) {
            throw new RuntimeException("无权删除此商品");
        }

        productRepository.delete(product);
    }

    public void markAsSold(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("商品不存在"));
        int qty = product.getQuantity() != null ? product.getQuantity() : 1;
        if (qty <= 1) {
            product.setQuantity(0);
            product.setStatus("sold");
        } else {
            product.setQuantity(qty - 1);
        }
        productRepository.save(product);
    }

    public List<Product> searchProducts(String keyword) {
        List<Product> products = productRepository.findByNameContaining(keyword);
        fillSellerAvatars(products);
        return products;
    }

    public List<Product> getUserProducts(String username) {
        List<Product> products = productRepository.findBySellerUsername(username);
        fillSellerAvatars(products);
        return products;
    }

    public List<Product> getAllActiveProducts() {
        List<Product> products = productRepository.findByStatus("active");
        fillSellerAvatars(products);
        return products;
    }

    public List<Product> getFilteredProducts(String category, Double minPrice, Double maxPrice, String condition, String sortBy) {
        boolean hasCategory = category != null && !category.isEmpty();
        boolean hasPriceRange = minPrice != null || maxPrice != null;
        boolean hasCondition = condition != null && !condition.isEmpty();

        double min = minPrice != null ? minPrice : 0;
        double max = maxPrice != null ? maxPrice : Double.MAX_VALUE;

        List<Product> products;

        if (hasCategory && hasPriceRange && hasCondition) {
            products = productRepository.findByCategoryAndPriceRangeAndCondition("active", category, min, max, condition);
        } else if (hasCategory && hasPriceRange) {
            products = productRepository.findByCategoryAndPriceRange("active", category, min, max);
        } else if (hasCategory && hasCondition) {
            products = productRepository.findByCategoryAndCondition("active", category, condition);
        } else if (hasPriceRange && hasCondition) {
            products = productRepository.findByPriceRangeAndCondition("active", min, max, condition);
        } else if (hasCategory) {
            products = productRepository.findByStatusAndCategoryOrderByCreatedAtDesc("active", category);
        } else if (hasPriceRange) {
            products = productRepository.findByPriceRange("active", min, max);
        } else if (hasCondition) {
            products = productRepository.findByCondition("active", condition);
        } else {
            products = productRepository.findByStatus("active");
        }

        if (sortBy != null) {
            switch (sortBy) {
                case "price-asc":
                    products.sort(Comparator.comparing(Product::getSecondHandPrice));
                    break;
                case "price-desc":
                    products.sort(Comparator.comparing(Product::getSecondHandPrice).reversed());
                    break;
                case "newest":
                    products.sort(Comparator.comparing(Product::getCreatedAt).reversed());
                    break;
                default:
                    products.sort(Comparator.comparing(Product::getCreatedAt).reversed());
            }
        } else {
            products.sort(Comparator.comparing(Product::getCreatedAt).reversed());
        }

        fillSellerAvatars(products);
        return products;
    }
}
