-- B-07/B-08：管理员强退留痕 + 打款异常人工队列
-- ddl-auto=validate，需手动执行

ALTER TABLE orders
    ADD COLUMN pay_exception TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN pay_exception_reason VARCHAR(255) NULL,
    ADD COLUMN admin_cancel_reason VARCHAR(255) NULL;
