package com.secondhand.mall.service;

import com.secondhand.mall.aspect.Audited;

import com.secondhand.mall.entity.Category;
import com.secondhand.mall.repository.CategoryRepository;
import com.secondhand.mall.repository.ProductRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 分类管理：前台导航/筛选读启用项，后台可增删改排序。
 * 分类改名时同步改写存量商品，删除前校验是否还有商品在用。
 */
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public CategoryService(CategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    /** 前台：仅启用分类 */
    @Cacheable(cacheNames = "categories", key = "'enabled'")
    public List<Category> listEnabled() {
        return categoryRepository.findByEnabledTrueOrderBySortAsc();
    }

    /** 后台：全部分类 */
    public List<Category> listAll() {
        return categoryRepository.findAllByOrderBySortAsc();
    }

    @Audited(action = "新建分类", targetType = "category")
    @CacheEvict(cacheNames = "categories", allEntries = true)
    @Transactional
    public Category create(Category body) {
        String name = normalize(body.getName());
        if (categoryRepository.existsByName(name)) {
            throw new RuntimeException("分类「" + name + "」已存在");
        }
        Category c = new Category();
        c.setName(name);
        c.setSort(body.getSort() != null ? body.getSort() : nextSort());
        c.setEnabled(body.getEnabled() == null || body.getEnabled());
        return categoryRepository.save(c);
    }

    @Audited(action = "修改分类", targetType = "category")
    @CacheEvict(cacheNames = "categories", allEntries = true)
    @Transactional
    public Category update(Long id, Category body) {
        Category c = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("分类不存在"));
        String name = normalize(body.getName());
        if (!c.getName().equals(name) && categoryRepository.existsByName(name)) {
            throw new RuntimeException("分类「" + name + "」已存在");
        }
        boolean renamed = !c.getName().equals(name);
        String oldName = c.getName();

        c.setName(name);
        if (body.getSort() != null) c.setSort(body.getSort());
        if (body.getEnabled() != null) c.setEnabled(body.getEnabled());
        Category saved = categoryRepository.save(c);

        // 改名要跟着改商品，否则分类下会凭空「没商品」
        if (renamed) {
            productRepository.renameCategory(oldName, name);
        }
        return saved;
    }

    /**
     * 删除分类。分类下还有商品时不允许删（否则这些商品会变成无分类），
     * 提示管理员先改到别的分类或用「停用」。
     */
    @Audited(action = "删除分类", targetType = "category")
    @CacheEvict(cacheNames = "categories", allEntries = true)
    @Transactional
    public void delete(Long id) {
        Category c = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("分类不存在"));
        long inUse = productRepository.countByCategory(c.getName());
        if (inUse > 0) {
            throw new RuntimeException("分类「" + c.getName() + "」下还有 " + inUse
                    + " 件商品，请先把商品改到其他分类；若只是不想展示，请改用「停用」");
        }
        categoryRepository.delete(c);
    }

    /** 分类下的商品数，后台列表展示用 */
    public long productCount(String categoryName) {
        return productRepository.countByCategory(categoryName);
    }

    private int nextSort() {
        return categoryRepository.findAllByOrderBySortAsc().stream()
                .mapToInt(c -> c.getSort() != null ? c.getSort() : 0)
                .max().orElse(0) + 1;
    }

    private static String normalize(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new RuntimeException("分类名称不能为空");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 100) {
            throw new RuntimeException("分类名称过长（最多 100 字）");
        }
        return trimmed;
    }
}
