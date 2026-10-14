-- V13: 商品库存拆分为总库存 + 占用库存
-- quantity = 总库存（卖家可改，须 >= reserved_quantity）
-- reserved_quantity = 已下单/已付款未终结订单占用
ALTER TABLE products ADD COLUMN reserved_quantity INT NOT NULL DEFAULT 0;