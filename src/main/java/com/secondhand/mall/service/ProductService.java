package com.secondhand.mall.service;

import com.secondhand.mall.aspect.Audited;
import com.secondhand.mall.constant.ProductStatus;
import com.secondhand.mall.entity.Product;
import com.secondhand.mall.entity.User;
import com.secondhand.mall.repository.OrderRepository;
import com.secondhand.mall.repository.ProductRepository;
import com.secondhand.mall.repository.UserRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
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
    private final SensitiveWordService sensitiveWordService;

    /** 是否要求人工审核后才能上架（mall.product.review-required，默认开） */
    @org.springframework.beans.factory.annotation.Value("${mall.product.review-required:true}")
    private boolean reviewRequired = true;

    public ProductService(ProductRepository productRepository, UserRepository userRepository,
                          OrderRepository orderRepository, SensitiveWordService sensitiveWordService) {
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.sensitiveWordService = sensitiveWordService;
    }

    private volatile Map<Long, Long> salesCache;
    private volatile long salesCacheAt;

    private Map<Long, Long> loadSalesMap() {
        // 60s 内存缓存，避免商品详情/列表每次全表聚合订单
        long now = System.currentTimeMillis();
        Map<Long, Long> cached = salesCache;
        if (cached != null && now - salesCacheAt < 60_000L) {
            return cached;
        }
        Map<Long, Long> sales = new HashMap<>();
        for (Object[] row : orderRepository.countSalesByProductId()) {
            if (row[0] == null || row[1] == null) continue;
            sales.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        salesCache = sales;
        salesCacheAt = now;
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

    /** 批量填充（带缓存）：销量一次聚合、每个卖家只查一次用户表，消除 N+1 */
    private void fillSellerAvatar(Product product, Map<Long, Long> sales, Map<String, User> userCache) {
        if (product.getSellerUsername() != null && !product.getSellerUsername().isEmpty()) {
            User user = userCache.get(product.getSellerUsername());
            if (user != null) {
                product.setSellerAvatar(user.getAvatar());
                if (user.getNickname() != null && !user.getNickname().isEmpty()) {
                    product.setSellerName(user.getNickname());
                }
            }
        }
        if (product.getId() != null) {
            product.setSalesCount(sales.getOrDefault(product.getId(), 0L));
        }
    }

    private void fillSellerAvatars(List<Product> products) {
        if (products == null || products.isEmpty()) return;
        Map<Long, Long> sales = loadSalesMap();
        Map<String, User> userCache = new HashMap<>();
        for (Product p : products) {
            String seller = p.getSellerUsername();
            if (seller != null && !seller.isEmpty() && !userCache.containsKey(seller)) {
                userCache.put(seller, userRepository.findByUsername(seller).orElse(null));
            }
        }
        for (Product p : products) {
            fillSellerAvatar(p, sales, userCache);
        }
    }

    @CacheEvict(cacheNames = {"productList", "productDetail"}, allEntries = true)
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
        sensitiveWordService.check(product.getName(), product.getDescription());
        // 发布审核：开启时先挂 pending，管理员通过才上前台
        product.setStatus(reviewRequired ? ProductStatus.PENDING : ProductStatus.ACTIVE);
        product.setReviewNote(null);
        return productRepository.save(product);
    }

    @Cacheable(cacheNames = "productList", key = "'active'")
    public List<Product> getActiveProducts() {
        List<Product> products = productRepository.findByStatus(ProductStatus.ACTIVE);
        fillSellerAvatars(products);
        return products;
    }

    public List<Product> getRandomActiveProducts(int count) {
        List<Product> allProducts = productRepository.findByStatus(ProductStatus.ACTIVE);
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

    @Cacheable(cacheNames = "productList", key = "'cat:' + #category")
    public List<Product> getProductsByCategory(String category) {
        List<Product> products = productRepository.findByStatusAndCategoryOrderByCreatedAtDesc(ProductStatus.ACTIVE, category);
        fillSellerAvatars(products);
        return products;
    }

    public Optional<Product> getProductById(Long id) {
        Optional<Product> opt = productRepository.findById(id);
        opt.ifPresent(this::fillSellerAvatar);
        return opt;
    }

    /** 详情页浏览量 +1 */
    public void incrementViewCount(Long id) {
        productRepository.findById(id).ifPresent(p -> {
            p.setViewCount((p.getViewCount() == null ? 0 : p.getViewCount()) + 1);
            productRepository.save(p);
        });
    }

    /** 店铺：某卖家在售商品 */
    public List<Product> getShopProducts(String sellerUsername) {
        List<Product> products = productRepository.findByStatusAndSellerUsernameOrderByCreatedAtDesc(ProductStatus.ACTIVE, sellerUsername);
        fillSellerAvatars(products);
        return products;
    }

    /** 详情页「相关推荐」：同分类在售商品，排除自身，最多 8 条 */
    public List<Product> getRelatedProducts(Long productId, int limit) {
        Optional<Product> opt = productRepository.findById(productId);
        if (opt.isEmpty()) return List.of();
        Product current = opt.get();
        List<Product> pool;
        if (current.getCategory() != null && !current.getCategory().isBlank()) {
            pool = productRepository.findByStatusAndCategoryOrderByCreatedAtDesc(ProductStatus.ACTIVE, current.getCategory());
        } else {
            pool = productRepository.findAllActiveOrderByNewest();
        }
        List<Product> related = new java.util.ArrayList<>();
        for (Product p : pool) {
            if (p.getId() != null && p.getId().equals(productId)) continue;
            related.add(p);
            if (related.size() >= Math.max(1, limit)) break;
        }
        fillSalesCounts(related);
        fillSellerAvatars(related);
        return related;
    }

    @CacheEvict(cacheNames = {"productList", "productDetail"}, allEntries = true)
    public Product updateProduct(Long id, Product updates, String currentUsername) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("商品不存在"));

        if (!product.getSellerUsername().equals(currentUsername)) {
            throw new RuntimeException("无权修改此商品");
        }

        String beforeStatus = product.getStatus();
        boolean underReview = ProductStatus.PENDING.equals(beforeStatus)
                || ProductStatus.REJECTED.equals(beforeStatus);

        if (updates.getName() != null) product.setName(updates.getName());
        if (updates.getDescription() != null) product.setDescription(updates.getDescription());
        if (updates.getCategory() != null) product.setCategory(updates.getCategory());
        if (updates.getOriginalPrice() != null) product.setOriginalPrice(updates.getOriginalPrice());
        if (updates.getSecondHandPrice() != null) product.setSecondHandPrice(updates.getSecondHandPrice());
        if (updates.getItemCondition() != null) product.setItemCondition(updates.getItemCondition());
        if (updates.getImageUrl() != null) product.setImageUrl(updates.getImageUrl());
        if (updates.getQuantity() != null) product.setQuantity(updates.getQuantity());

        if (underReview) {
            // 待审/驳回由审核流程接管：卖家改内容一律重新排队，不能自己改成在售
            if (ProductStatus.ACTIVE.equals(updates.getStatus())) {
                throw new RuntimeException("该商品尚未通过审核，无法自行上架");
            }
            product.setStatus(ProductStatus.PENDING);
            product.setReviewNote(null);
        } else if (updates.getStatus() != null) {
            product.setStatus(updates.getStatus());
        }

        if (product.getOriginalPrice().compareTo(BigDecimal.ZERO) <= 0 || product.getSecondHandPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("价格必须大于0");
        }
        if (product.getSecondHandPrice().compareTo(product.getOriginalPrice()) > 0) {
            throw new RuntimeException("二手价不能高于原价");
        }
        if (product.getQuantity() != null && product.getQuantity() < 0) {
            throw new RuntimeException("库存不能为负数");
        }
        // 合并后再校验：客户端没传的字段以原值参与检查，避免漏检
        sensitiveWordService.check(product.getName(), product.getDescription());

        return productRepository.save(product);
    }

    @CacheEvict(cacheNames = {"productList", "productDetail"}, allEntries = true)
    public void deleteProduct(Long id, String currentUsername) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("商品不存在"));

        if (!product.getSellerUsername().equals(currentUsername)) {
            throw new RuntimeException("无权删除此商品");
        }

        product.setStatus(ProductStatus.DELETED);
        productRepository.save(product);
    }

    /**
     * 获取所有商品（含已售出/下架，不含回收站），供 admin 主列表使用
     */
    public List<Product> getAllProducts() {
        List<Product> products = productRepository.findAll().stream()
                .filter(p -> !ProductStatus.DELETED.equals(p.getStatus()))
                .toList();
        fillSellerAvatars(products);
        return products;
    }

    /** 回收站：已删除商品 */
    public List<Product> getDeletedProducts() {
        List<Product> products = productRepository.findByStatus(ProductStatus.DELETED);
        fillSellerAvatars(products);
        return products;
    }

    /**
     * 管理员删除商品（软删除进回收站），保留订单/购物车引用一致性
     */
    @Audited(action = "删除商品到回收站", targetType = "product")
    @CacheEvict(cacheNames = {"productList", "productDetail"}, allEntries = true)
    public void adminDeleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("商品不存在"));
        product.setStatus(ProductStatus.DELETED);
        productRepository.save(product);
    }

    /** 审核通过：待审/驳回商品转为在售 */
    @Audited(action = "商品审核通过", targetType = "product")
    @CacheEvict(cacheNames = {"productList", "productDetail"}, allEntries = true)
    public Product approveProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("商品不存在"));
        if (ProductStatus.DELETED.equals(product.getStatus())) {
            throw new RuntimeException("商品已删除，无法审核");
        }
        if (ProductStatus.SOLD.equals(product.getStatus())) {
            throw new RuntimeException("商品已售出，无法审核");
        }
        product.setStatus(ProductStatus.ACTIVE);
        product.setReviewNote(null);
        return productRepository.save(product);
    }

    /** 审核驳回：写明理由，卖家可在「我的商品」看到并修改重提 */
    @Audited(action = "商品审核驳回", targetType = "product")
    @CacheEvict(cacheNames = {"productList", "productDetail"}, allEntries = true)
    public Product rejectProduct(Long id, String reason) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("商品不存在"));
        if (ProductStatus.DELETED.equals(product.getStatus())) {
            throw new RuntimeException("商品已删除，无法审核");
        }
        String note = reason != null && !reason.isBlank() ? reason.trim() : "商品信息不符合发布规范";
        product.setStatus(ProductStatus.REJECTED);
        product.setReviewNote(note.length() > 255 ? note.substring(0, 255) : note);
        return productRepository.save(product);
    }

    /** 管理员下架商品（不进回收站，前台不可见） */
    @Audited(action = "下架商品", targetType = "product")
    @CacheEvict(cacheNames = {"productList", "productDetail"}, allEntries = true)
    public void adminOfflineProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("商品不存在"));
        product.setStatus(ProductStatus.INACTIVE);
        productRepository.save(product);
    }

    /** 管理员上架商品 */
    @Audited(action = "重新上架商品", targetType = "product")
    @CacheEvict(cacheNames = {"productList", "productDetail"}, allEntries = true)
    public void adminOnlineProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("商品不存在"));
        if (ProductStatus.DELETED.equals(product.getStatus())) {
            throw new RuntimeException("请先从回收站恢复该商品");
        }
        product.setStatus(ProductStatus.ACTIVE);
        productRepository.save(product);
    }

    /** 从回收站恢复商品（恢复为在售） */
    @Audited(action = "恢复商品", targetType = "product")
    @CacheEvict(cacheNames = {"productList", "productDetail"}, allEntries = true)
    public void restoreProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("商品不存在"));
        product.setStatus(ProductStatus.ACTIVE);
        productRepository.save(product);
    }

    /** 彻底删除商品（回收站清空，不可恢复） */
    @Audited(action = "彻底删除商品", targetType = "product")
    @CacheEvict(cacheNames = {"productList", "productDetail"}, allEntries = true)
    public void purgeProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("商品不存在"));
        productRepository.delete(product);
    }

    @CacheEvict(cacheNames = {"productList", "productDetail"}, allEntries = true)
    public void markAsSold(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("商品不存在"));
        int qty = product.getQuantity() != null ? product.getQuantity() : 1;
        if (qty <= 1) {
            product.setQuantity(0);
            product.setStatus(ProductStatus.SOLD);
        } else {
            product.setQuantity(qty - 1);
        }
        productRepository.save(product);
    }

    /**
     * 真分页商品检索：筛选 + 排序全部下推到数据库，只对当前页填充卖家头像。
     * sortBy ∈ newest / price-asc / price-desc / sales
     */
    public Page<Product> searchProductsPaged(
            String keyword, String category, String seller,
            BigDecimal minPrice, BigDecimal maxPrice, String condition,
            String sortBy, int page, int size) {
        String kw = blankToNull(keyword);
        String cat = blankToNull(category);
        String sel = blankToNull(seller);
        String cond = blankToNull(condition);
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));

        Pageable pageable;
        boolean bySales = sortBy != null && (sortBy.equals("sales") || sortBy.equals("sales-desc"));
        if (bySales) {
            pageable = PageRequest.of(safePage, safeSize);
        } else {
            Sort sort = switch (sortBy == null ? "newest" : sortBy) {
                case "price-asc" -> Sort.by(Sort.Direction.ASC, "secondHandPrice");
                case "price-desc" -> Sort.by(Sort.Direction.DESC, "secondHandPrice");
                default -> Sort.by(Sort.Direction.DESC, "createdAt");
            };
            pageable = PageRequest.of(safePage, safeSize, sort);
        }

        Page<Product> result = bySales
                ? productRepository.searchActivePagedBySales(kw, cat, sel, minPrice, maxPrice, cond, pageable)
                : productRepository.searchActivePaged(kw, cat, sel, minPrice, maxPrice, cond, pageable);

        List<Product> content = new ArrayList<>(result.getContent());
        fillSellerAvatars(content);
        if (bySales) {
            Map<Long, Long> sales = loadSalesMap();
            for (Product p : content) {
                if (p.getId() != null) p.setSalesCount(sales.getOrDefault(p.getId(), 0L));
            }
        }
        return new PageImpl<>(content, result.getPageable(), result.getTotalElements());
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
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

    /** 某卖家的在售商品（进店逛逛） */
    public List<Product> getSellerActiveProducts(String sellerUsername) {
        List<Product> products = productRepository.findByStatusAndSellerUsername(ProductStatus.ACTIVE, sellerUsername);
        products.sort(Comparator.comparing(Product::getCreatedAt).reversed());
        fillSellerAvatars(products);
        return products;
    }

    public List<Product> getAllActiveProducts() {
        List<Product> products = productRepository.findByStatus(ProductStatus.ACTIVE);
        fillSellerAvatars(products);
        return products;
    }

    @Cacheable(cacheNames = "productList",
            key = "'f:' + #category + '|' + #minPrice + '|' + #maxPrice + '|' + #condition + '|' + #sortBy")
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
                        products = productRepository.findByStatus(ProductStatus.ACTIVE);
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
            products = productRepository.findByCategoryAndPriceRangeAndCondition(ProductStatus.ACTIVE, category, min, max, condition);
        } else if (hasCategory && hasPriceRange) {
            products = productRepository.findByCategoryAndPriceRange(ProductStatus.ACTIVE, category, min, max);
        } else if (hasCategory && hasCondition) {
            products = productRepository.findByCategoryAndCondition(ProductStatus.ACTIVE, category, condition);
        } else if (hasPriceRange && hasCondition) {
            products = productRepository.findByPriceRangeAndCondition(ProductStatus.ACTIVE, min, max, condition);
        } else if (hasCategory) {
            products = productRepository.findByStatusAndCategoryOrderByCreatedAtDesc(ProductStatus.ACTIVE, category);
        } else if (hasPriceRange) {
            products = productRepository.findByPriceRange(ProductStatus.ACTIVE, min, max);
        } else {
            products = productRepository.findByCondition(ProductStatus.ACTIVE, condition);
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

    @Cacheable(cacheNames = "productList", key = "'page:' + #page + ':' + #size")
    public Page<Product> getProducts(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return productRepository.findAllActive(pageable);
    }

    @Cacheable(cacheNames = "productList", key = "'latest:' + #count")
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
