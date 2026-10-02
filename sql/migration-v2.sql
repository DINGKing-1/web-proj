-- 优品汇 - 数据库迁移脚本 v2
-- 修改 seller_phone 字段为可选

ALTER TABLE products MODIFY COLUMN seller_phone VARCHAR(20) NULL;
