-- 修复：orders.image_url 历史为 VARCHAR(255)，多图逗号串会 Data truncation
-- 实体已声明 TEXT，但 ddl-auto=update 不会扩列，需手动执行

ALTER TABLE orders MODIFY image_url TEXT NOT NULL;
ALTER TABLE products MODIFY image_url TEXT;

-- 评价媒体字段（若缺失则为 TEXT）
ALTER TABLE reviews MODIFY images TEXT;
ALTER TABLE reviews MODIFY videos TEXT;
