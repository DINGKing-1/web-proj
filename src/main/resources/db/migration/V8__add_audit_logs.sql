-- V8: 管理端操作审计日志
-- 谁在什么时候对哪个对象做了什么，用于答辩时回答「管理员误操作如何追溯」。

CREATE TABLE IF NOT EXISTS audit_logs (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    operator      VARCHAR(100) NOT NULL,
    action        VARCHAR(100) NOT NULL,
    target_type   VARCHAR(50)  NULL,
    target_id     VARCHAR(64)  NULL,
    detail        VARCHAR(500) NULL,
    success       BIT(1)       NOT NULL DEFAULT 1,
    created_at    DATETIME(6)  NOT NULL,
    KEY idx_audit_operator (operator),
    KEY idx_audit_created_at (created_at)
);
