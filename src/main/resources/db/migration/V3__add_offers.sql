-- V3: 议价表
CREATE TABLE IF NOT EXISTS offers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    image_url VARCHAR(255),
    buyer_username VARCHAR(100) NOT NULL,
    seller_username VARCHAR(100) NOT NULL,
    offer_price DECIMAL(12,2) NOT NULL,
    message VARCHAR(200),
    status VARCHAR(20) NOT NULL,
    order_id BIGINT,
    created_at DATETIME(6) NOT NULL,
    responded_at DATETIME(6),
    PRIMARY KEY (id),
    KEY idx_offers_buyer (buyer_username),
    KEY idx_offers_seller (seller_username),
    KEY idx_offers_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
