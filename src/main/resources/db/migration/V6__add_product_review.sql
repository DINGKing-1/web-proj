-- V6: 商品审核列
-- 发布后先进 pending，审核驳回时把理由写在 review_note，卖家可见并据此修改重提。

ALTER TABLE products
    ADD COLUMN review_note VARCHAR(255) NULL;
