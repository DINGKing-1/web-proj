-- V4: users 软删除列（回收站）
-- 既有库由 baseline 跳过 V1，实体已含 deleted/deleted_at/deleted_by，validate 需要此迁移
ALTER TABLE users
    ADD COLUMN deleted BIT(1) NOT NULL DEFAULT 0,
    ADD COLUMN deleted_at DATETIME(6) NULL,
    ADD COLUMN deleted_by VARCHAR(20) NULL;
