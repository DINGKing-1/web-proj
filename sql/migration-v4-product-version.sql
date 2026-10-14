-- B-05：商品库存乐观锁版本号
-- 主防线是 productRepository.findByIdForUpdate 悲观行锁；version 作为并发更新冲突检测兜底
-- MySQL 8 可直接加列；历史库若无该列需先执行本脚本

ALTER TABLE products
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
