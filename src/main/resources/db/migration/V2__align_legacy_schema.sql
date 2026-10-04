-- V2: 存量库对齐（历史 ddl-auto=update 残留的 datetime 精度差异）
-- 幂等：MODIFY 为同类型放宽/收紧，可重复执行

ALTER TABLE products MODIFY created_at DATETIME(6) NOT NULL;
ALTER TABLE users MODIFY created_at DATETIME(6) NOT NULL;
ALTER TABLE addresses MODIFY created_at DATETIME(6) NOT NULL;
ALTER TABLE cart_items MODIFY created_at DATETIME(6) NOT NULL;
ALTER TABLE comments MODIFY created_at DATETIME(6) NOT NULL;
ALTER TABLE orders MODIFY created_at DATETIME(6) NOT NULL;
