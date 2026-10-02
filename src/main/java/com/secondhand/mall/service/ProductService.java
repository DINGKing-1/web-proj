package com.secondhand.mall.service;

import com.secondhand.mall.entity.Product;
import com.secondhand.mall.entity.User;
import com.secondhand.mall.repository.OrderRepository;
import com.secondhand.mall.repository.ProductRepository;
import com.secondhand.mall.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    public ProductService(ProductRepository productRepository, UserRepository userRepository,
                          OrderRepository orderRepository) {
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
    }

    private Map<Long, Long> loadSalesMap() {
        Map<Long, Long> sales = new HashMap<>();
        for (Object[] row : orderRepository.countSalesByProductId()) {
            if (row[0] == null || row[1] == null) continue;
            sales.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return sales;
    }

    /** 批量填充销量到商品列表 */
    private void fillSalesCounts(List<Product> products) {
        if (products == null || products.isEmpty()) return;
        Map<Long, Long> sales = loadSalesMap();
        for (Product p : products) {
            p.setSalesCount(sales.getOrDefault(p.getId(), 0L));
        }
    }

    /** 按销量降序（同销量再按上架时间新→旧） */
    private void sortBySales(List<Product> products) {
        Map<Long, Long> sales = loadSalesMap();
        for (Product p : products) {
            p.setSalesCount(sales.getOrDefault(p.getId(), 0L));
        }
        products.sort(
            Comparator.comparing((Product p) -> sales.getOrDefault(p.getId(), 0L)).reversed()
                .thenComparing(Product::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
        );
    }

    private void fillSellerAvatar(Product product) {
        // 每次都从用户表获取最新头像，确保头像跟随用户变化
        if (product.getSellerUsername() != null && !product.getSellerUsername().isEmpty()) {
            userRepository.findByUsername(product.getSellerUsername()).ifPresent(user -> {
                product.setSellerAvatar(user.getAvatar());
                // 同时更新卖家昵称，确保跟随用户变化
                if (user.getNickname() != null && !user.getNickname().isEmpty()) {
                    product.setSellerName(user.getNickname());
                }
            });
        }
        if (product.getId() != null) {
            product.setSalesCount(loadSalesMap().getOrDefault(product.getId(), 0L));
        }
    }

    private void fillSellerAvatars(List<Product> products) {
        for (Product p : products) {
            fillSellerAvatar(p);
        }
        fillSalesCounts(products);
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

    public List<Product> getRandomActiveProducts(int count) {
        List<Product> allProducts = productRepository.findByStatus("active");
        if (allProducts.size() <= count) {
            fillSellerAvatars(allProducts);
            return allProducts;
        }
        // 随机打乱后取前count个
        Collections.shuffle(allProducts);
        List<Product> randomProducts = allProducts.subList(0, count);
        fillSellerAvatars(randomProducts);
        return randomProducts;
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

        if (product.getOriginalPrice().compareTo(BigDecimal.ZERO) <= 0 || product.getSecondHandPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("价格必须大于0");
        }
        if (product.getSecondHandPrice().compareTo(product.getOriginalPrice()) > 0) {
            throw new RuntimeException("二手价不能高于原价");
        }
        if (product.getQuantity() != null && product.getQuantity() < 0) {
            throw new RuntimeException("库存不能为负数");
        }

        return productRepository.save(product);
    }

    public void deleteProduct(Long id, String currentUsername) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("商品不存在"));

        if (!product.getSellerUsername().equals(currentUsername)) {
            throw new RuntimeException("无权删除此商品");
        }

        product.setStatus("deleted");
        productRepository.save(product);
    }

    /**
     * 获取所有商品（含已售出），供 admin 使用
     */
    public List<Product> getAllProducts() {
        List<Product> products = productRepository.findAll();
        fillSellerAvatars(products);
        return products;
    }

    /**
     * 管理员直接删除商品，无需校验卖家
     */
    public void adminDeleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("商品不存在"));
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
        List<Product> products = productRepository.searchActiveByKeyword(keyword);
        fillSellerAvatars(products);
        return products;
    }

    public List<Product> searchProducts(String keyword, String category, BigDecimal minPrice, BigDecimal maxPrice, String condition, String sortBy) {
        boolean hasCategory = category != null && !category.isEmpty();
        boolean hasPriceRange = minPrice != null || maxPrice != null;
        boolean hasCondition = condition != null && !condition.isEmpty();

        BigDecimal min = minPrice != null ? minPrice : BigDecimal.ZERO;
        BigDecimal max = maxPrice != null ? maxPrice : new BigDecimal("99999999.99");

        List<Product> products;

        if (hasCategory && hasPriceRange && hasCondition) {
            products = productRepository.searchByKeywordCategoryPriceRangeAndCondition(keyword, category, min, max, condition);
        } else if (hasCategory && hasPriceRange) {
            products = productRepository.searchByKeywordCategoryAndPriceRange(keyword, category, min, max);
        } else if (hasCategory && hasCondition) {
            products = productRepository.searchByKeywordCategoryAndCondition(keyword, category, condition);
        } else if (hasPriceRange && hasCondition) {
            products = productRepository.searchByKeywordPriceRangeAndCondition(keyword, min, max, condition);
        } else if (hasCategory) {
            products = productRepository.searchByKeywordAndCategory(keyword, category);
        } else if (hasPriceRange) {
            products = productRepository.searchByKeywordAndPriceRange(keyword, min, max);
        } else if (hasCondition) {
            products = productRepository.searchByKeywordAndCondition(keyword, condition);
        } else {
            products = productRepository.searchActiveByKeyword(keyword);
        }

        if (sortBy != null) {
            switch (sortBy) {
                case "price-asc":
                    products.sort(Comparator.comparing(Product::getSecondHandPrice));
                    break;
                case "price-desc":
                    products.sort(Comparator.comparing(Product::getSecondHandPrice).reversed());
                    break;
                case "sales":
                case "sales-desc":
                    sortBySales(products);
                    break;
                case "newest":
                default:
                    products.sort(Comparator.comparing(Product::getCreatedAt).reversed());
                    break;
            }
        } else {
            products.sort(Comparator.comparing(Product::getCreatedAt).reversed());
        }

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

    public List<Product> getFilteredProducts(String category, BigDecimal minPrice, BigDecimal maxPrice, String condition, String sortBy) {
        boolean hasCategory = category != null && !category.isEmpty();
        boolean hasPriceRange = minPrice != null || maxPrice != null;
        boolean hasCondition = condition != null && !condition.isEmpty();
        boolean hasFilters = hasCategory || hasPriceRange || hasCondition;

        // 如果没有筛选条件，使用数据库排序
        if (!hasFilters) {
            List<Product> products;
            if (sortBy != null) {
                switch (sortBy) {
                    case "price-asc":
                        products = productRepository.findAllActiveOrderByPriceAsc();
                        break;
                    case "price-desc":
                        products = productRepository.findAllActiveOrderByPriceDesc();
                        break;
                    case "sales":
                    case "sales-desc":
                        products = productRepository.findByStatus("active");
                        sortBySales(products);
                        break;
                    case "newest":
                    default:
                        products = productRepository.findAllActiveOrderByNewest();
                        break;
                }
            } else {
                products = productRepository.findAllActiveOrderByNewest();
            }
            fillSellerAvatars(products);
            return products;
        }

        // 有筛选条件时，使用内存排序
        BigDecimal min = minPrice != null ? minPrice : BigDecimal.ZERO;
        BigDecimal max = maxPrice != null ? maxPrice : new BigDecimal("99999999.99");

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
        } else {
            products = productRepository.findByCondition("active", condition);
        }

        if (sortBy != null) {
            switch (sortBy) {
                case "price-asc":
                    products.sort(Comparator.comparing(Product::getSecondHandPrice));
                    break;
                case "price-desc":
                    products.sort(Comparator.comparing(Product::getSecondHandPrice).reversed());
                    break;
                case "sales":
                case "sales-desc":
                    sortBySales(products);
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

    public Page<Product> getProducts(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return productRepository.findAllActive(pageable);
    }

    public List<Product> getLatestActiveProducts(int count) {
        Pageable pageable = PageRequest.of(0, count, Sort.by("createdAt").descending());
        List<Product> products = productRepository.findAllActive(pageable).getContent();
        fillSellerAvatars(products);
        return products;
    }

    public Page<Product> searchProducts(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return productRepository.searchActiveByName(keyword, pageable);
    }
}
