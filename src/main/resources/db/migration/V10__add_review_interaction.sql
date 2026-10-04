-- V10: 评价互动 + 举报处置留痕
-- review: 卖家回复、买家追评
-- report: 谁处理的、什么时候、做了什么处置（联动下架/删除）

ALTER TABLE reviews
    ADD COLUMN reply VARCHAR(500) NULL,
    ADD COLUMN replied_at DATETIME(6) NULL,
    ADD COLUMN follow_up_content VARCHAR(500) NULL,
    ADD COLUMN follow_up_at DATETIME(6) NULL;

ALTER TABLE reports
    ADD COLUMN handled_by VARCHAR(100) NULL,
    ADD COLUMN handled_at DATETIME(6) NULL,
    ADD COLUMN action VARCHAR(30) NULL;
