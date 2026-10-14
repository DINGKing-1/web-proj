-- V15: 首次登录强制改密（管理员初始口令）
ALTER TABLE users ADD COLUMN must_change_password BIT(1) NOT NULL DEFAULT 0;