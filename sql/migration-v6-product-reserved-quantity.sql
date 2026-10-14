-- 库存拆分：总库存 quantity + 占用库存 reserved_quantity
-- 可售库存 = quantity - reserved_quantity
-- 卖家仅可修改总库存，且不得低于占用库存
ALTER TABLE products ADD COLUMN reserved_quantity INT NOT NULL DEFAULT 0;