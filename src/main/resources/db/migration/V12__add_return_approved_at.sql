-- V12: 买家寄回窗口起算点
-- return_approved_at: 卖家同意退货（或平台超时自动同意）的时间。
-- 买家 7 天寄回窗口由此起算，而非 after_sale_at（申请时间），
-- 避免卖家拖 72h 处理吃掉买家的寄回时间。

ALTER TABLE orders
    ADD COLUMN return_approved_at DATETIME(6) NULL;
