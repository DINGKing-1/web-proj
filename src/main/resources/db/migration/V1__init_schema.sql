-- V1: 全量初始化（新环境）
-- 已有环境由 Flyway baseline-version=1 跳过本脚本
-- 表结构与 JPA 实体对齐，ddl-auto=validate 依赖此基线

CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(100) NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100),
    phone VARCHAR(20),
    nickname VARCHAR(100),
    role VARCHAR(20) NOT NULL,
    balance DECIMAL(12,2) NOT NULL,
    avatar VARCHAR(255) NOT NULL,
    session_id VARCHAR(100),
    created_at DATETIME(6) NOT NULL,
    last_active_time DATETIME(6),
    banned BIT(1) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS products (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(255) NOT NULL,
    description TEXT,
    original_price DECIMAL(12,2) NOT NULL,
    second_hand_price DECIMAL(12,2) NOT NULL,
    item_condition VARCHAR(255) NOT NULL,
    seller_name VARCHAR(255) NOT NULL,
    seller_phone VARCHAR(255),
    seller_username VARCHAR(100),
    seller_avatar VARCHAR(255),
    image_url TEXT,
    quantity INT NOT NULL,
    status VARCHAR(255) NOT NULL,
    view_count INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS orders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_number VARCHAR(30) NOT NULL,
    username VARCHAR(255) NOT NULL,
    product_id BIGINT NOT NULL,
    seller_username VARCHAR(100),
    product_name VARCHAR(255) NOT NULL,
    product_price DECIMAL(12,2) NOT NULL,
    quantity INT NOT NULL,
    category VARCHAR(255) NOT NULL,
    image_url TEXT NOT NULL,
    receiver_name VARCHAR(255) NOT NULL,
    receiver_phone VARCHAR(255) NOT NULL,
    receiver_address TEXT NOT NULL,
    payment_method VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    tracking_number VARCHAR(100),
    rating INT,
    review_content VARCHAR(500),
    reviewed_at DATETIME(6),
    after_sale_reason VARCHAR(200),
    prev_status VARCHAR(20),
    seller_paid BIT(1) NOT NULL,
    received_at DATETIME(6),
    shipped_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_orders_order_number (order_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cart_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(255) NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS comments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    user_name VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS favorites (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(100) NOT NULL,
    product_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS addresses (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(255) NOT NULL,
    receiver_name VARCHAR(255) NOT NULL,
    phone VARCHAR(255) NOT NULL,
    province VARCHAR(255) NOT NULL,
    city VARCHAR(255) NOT NULL,
    district VARCHAR(255) NOT NULL,
    detail VARCHAR(255) NOT NULL,
    is_default BIT(1) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS chat_rooms (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user1username VARCHAR(100) NOT NULL,
    user2username VARCHAR(100) NOT NULL,
    product_id BIGINT NOT NULL,
    last_message VARCHAR(500),
    last_message_time DATETIME(6) NOT NULL,
    user1unread INT NOT NULL,
    user2unread INT NOT NULL,
    room_type VARCHAR(20) NOT NULL,
    deleted BIT(1) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    room_id BIGINT NOT NULL,
    sender_username VARCHAR(100) NOT NULL,
    product_id BIGINT,
    content TEXT NOT NULL,
    message_type VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(100) NOT NULL,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content VARCHAR(500),
    ref_type VARCHAR(50),
    ref_id VARCHAR(50),
    read_flag BIT(1) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS reviews (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    buyer_username VARCHAR(100) NOT NULL,
    seller_username VARCHAR(100),
    rating INT NOT NULL,
    rating_goods INT,
    rating_delivery INT,
    rating_service INT,
    content VARCHAR(500),
    images TEXT,
    videos TEXT,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_reviews_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS reports (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(255),
    reporter_username VARCHAR(100) NOT NULL,
    reason VARCHAR(50) NOT NULL,
    detail VARCHAR(500),
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS quick_replies (
    id BIGINT NOT NULL AUTO_INCREMENT,
    category VARCHAR(50),
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    sort_order INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS wallet_transactions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL,
    title VARCHAR(200),
    amount DECIMAL(12,2) NOT NULL,
    status VARCHAR(20),
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS announcements (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(100) NOT NULL,
    content VARCHAR(500),
    type VARCHAR(20) NOT NULL,
    image_url VARCHAR(500),
    link_url VARCHAR(500),
    sort_order INT NOT NULL,
    enabled BIT(1) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS withdraw_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(100) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    account VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL,
    reject_reason VARCHAR(200),
    processed_at DATETIME(6),
    processed_by VARCHAR(100),
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
