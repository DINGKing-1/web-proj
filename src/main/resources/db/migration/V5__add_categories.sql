-- V5: 分类表
-- 此前分类是 products.category 里的自由字符串 + 前端硬编码常量，后台无法维护。
-- 这里建表并把现有分类落库；无分类/异常分类的商品统一归到「其他」。

CREATE TABLE IF NOT EXISTS categories (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    sort       INT          NOT NULL DEFAULT 0,
    enabled    BIT(1)       NOT NULL DEFAULT 1,
    created_at DATETIME(6)  NOT NULL,
    UNIQUE KEY uk_categories_name (name)
);

-- 幂等：name 唯一键 + INSERT IGNORE，重复执行不会翻倍
INSERT IGNORE INTO categories (name, sort, enabled, created_at) VALUES
    ('手机',       1, 1, NOW(6)),
    ('笔记本电脑', 2, 1, NOW(6)),
    ('耳机',       3, 1, NOW(6)),
    ('相机',       4, 1, NOW(6)),
    ('手机壳',     5, 1, NOW(6)),
    ('空调',       6, 1, NOW(6)),
    ('冰箱',       7, 1, NOW(6)),
    ('半身裙',     8, 1, NOW(6)),
    ('其他',       9, 1, NOW(6));

-- 脏数据清洗：空值或未登记的分类统一并入「其他」
UPDATE products
SET category = '其他'
WHERE category IS NULL
   OR TRIM(category) = ''
   OR category NOT IN (SELECT name FROM categories);
