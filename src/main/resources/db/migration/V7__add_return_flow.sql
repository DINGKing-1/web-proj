-- V7: 退货售后闭环
-- after_sale（待卖家处理）→ return_shipping（待买家寄回）→ return_sent（已寄回）→ refunded（已退款）
-- return_tracking_number: 买家寄回的退货运单号
-- after_sale_at: 申请时间，卖家超时未处理由定时任务兜底
-- return_sent_at: 买家寄回时间

ALTER TABLE orders
    ADD COLUMN return_tracking_number VARCHAR(100) NULL,
    ADD COLUMN after_sale_at DATETIME(6) NULL,
    ADD COLUMN return_sent_at DATETIME(6) NULL;
