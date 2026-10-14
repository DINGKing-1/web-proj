package com.secondhand.mall.service;

import com.secondhand.mall.constant.ProductStatus;
import com.secondhand.mall.entity.Product;
import com.secondhand.mall.repository.ProductRepository;
import jakarta.persistence.OptimisticLockException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 商品库存变更统一入口。
 * 1) 悲观锁 findByIdForUpdate 串行化并发更新（主防线，防超卖）；
 * 2) Product.version 乐观锁兜底，冲突时独立事务自动重试；
 * 与订单状态同事务的调用走 {@link #restore}/{@link #deduct}（参与当前事务，原子提交）。
 */
@Service
public class StockService {

    private static final int MAX_RETRY = 3;

    private final ProductRepository productRepository;
    private final TransactionTemplate txTemplate;

    public StockService(ProductRepository productRepository,
                        PlatformTransactionManager transactionManager) {
        this.productRepository = productRepository;
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 回补库存（取消/退款/注销结算/超时关单）。
     * 参与当前业务事务（REQUIRED）；独立调用时自开事务。
     * 调用前须判定货权在卖方侧；乐观锁冲突抛出由调用方事务回滚后重试。
     */
    @org.springframework.transaction.annotation.Transactional
    public void restore(Long productId, int qty) {
        if (productId == null || qty <= 0) {
            return;
        }
        Product product = productRepository.findByIdForUpdate(productId).orElse(null);
        if (product == null) {
            return;
        }
        int restored = product.getQuantity() != null ? product.getQuantity() : 0;
        product.setQuantity(restored + qty);
        int reserved = product.getReservedQuantity() != null ? product.getReservedQuantity() : 0;
        product.setReservedQuantity(Math.max(0, reserved - qty));
        if (ProductStatus.SOLD.equals(product.getStatus())) {
            product.setStatus(ProductStatus.ACTIVE);
        }
        productRepository.save(product);
    }

    /**
     * 扣减库存（下单占库存）。
     *
     * @return 扣减后剩余
     */
    @org.springframework.transaction.annotation.Transactional
    public int deduct(Long productId, int qty) {
        if (productId == null || qty <= 0) {
            throw new RuntimeException("购买数量无效");
        }
        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new RuntimeException("商品不存在"));
        if (!ProductStatus.ACTIVE.equals(product.getStatus())) {
            throw new RuntimeException("该商品已被购买或已下架");
        }
        int left = product.getQuantity() != null ? product.getQuantity() : 0;
        if (qty > left) {
            throw new RuntimeException("库存不足，当前仅剩 " + left + " 件");
        }
        product.setQuantity(left - qty);
        // 同步占用库存（总库存 = quantity + reservedQuantity）
        int reserved = product.getReservedQuantity() != null ? product.getReservedQuantity() : 0;
        product.setReservedQuantity(reserved + qty);
        if (left - qty <= 0) {
            product.setStatus(ProductStatus.SOLD);
        }
        productRepository.save(product);
        return left - qty;
    }

    /**
     * 独立事务回补 + 乐观锁自动重试。
     * 适用于不与订单同事务提交的场景（如管理后台手动改库存后的纠偏）。
     */
    public void restoreWithRetry(Long productId, int qty) {
        if (productId == null || qty <= 0) {
            return;
        }
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                txTemplate.executeWithoutResult(s -> restore(productId, qty));
                return;
            } catch (ObjectOptimisticLockingFailureException | OptimisticLockException e) {
                if (attempt >= MAX_RETRY) {
                    throw e;
                }
            }
        }
    }

    /** 标记售出并扣一件（无剩余则 status=sold） */
    public void markSold(Long productId) {
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                txTemplate.executeWithoutResult(s -> {
                    Product product = productRepository.findByIdForUpdate(productId)
                            .orElseThrow(() -> new RuntimeException("商品不存在"));
                    int qty = product.getQuantity() != null ? product.getQuantity() : 1;
                    if (qty <= 1) {
                        product.setQuantity(0);
                        product.setStatus(ProductStatus.SOLD);
                    } else {
                        product.setQuantity(qty - 1);
                    }
                    int reserved = product.getReservedQuantity() != null ? product.getReservedQuantity() : 0;
                    product.setReservedQuantity(Math.max(0, reserved - 1));
                    productRepository.save(product);
                });
                return;
            } catch (ObjectOptimisticLockingFailureException | OptimisticLockException e) {
                if (attempt >= MAX_RETRY) {
                    throw e;
                }
            }
        }
    }
}
