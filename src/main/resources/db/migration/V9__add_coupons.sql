-- V9: 优惠券
-- coupons     平台发放的券（满减）
-- user_coupons 用户领券记录（unused / used / expired）
-- orders.discount_amount 该笔订单用券抵扣了多少（退款、结算都要按实付算）

CREATE TABLE IF NOT EXISTS coupons (
    id               BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code             VARCHAR(40)   NOT NULL,
    name             VARCHAR(100)  NOT NULL,
    discount_amount  DECIMAL(12,2) NOT NULL,
    min_amount       DECIMAL(12,2) NOT NULL DEFAULT 0,
    total_quantity   INT           NOT NULL DEFAULT 0,
    issued_count     INT           NOT NULL DEFAULT 0,
    per_user_limit   INT           NOT NULL DEFAULT 1,
    start_at         DATETIME(6)   NOT NULL,
    end_at           DATETIME(6)   NOT NULL,
    enabled          BIT(1)        NOT NULL DEFAULT 1,
    created_at       DATETIME(6)   NOT NULL,
    UNIQUE KEY uk_coupons_code (code)
);

CREATE TABLE IF NOT EXISTS user_coupons (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    coupon_id     BIGINT       NOT NULL,
    username      VARCHAR(100) NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'unused',
    used_order_id BIGINT       NULL,
    claimed_at    DATETIME(6)  NOT NULL,
    used_at       DATETIME(6)  NULL,
    KEY idx_user_coupons_user (username),
    KEY idx_user_coupons_coupon (coupon_id)
);

ALTER TABLE orders
    ADD COLUMN discount_amount DECIMAL(12,2) NULL;
