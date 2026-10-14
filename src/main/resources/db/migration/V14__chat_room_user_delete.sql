-- V14: 聊天会话改为用户维度逻辑删除
-- 一方删除不影响另一方会话与记录可见性
ALTER TABLE chat_rooms ADD COLUMN deleted_by_user1 BIT(1) NOT NULL DEFAULT 0;
ALTER TABLE chat_rooms ADD COLUMN deleted_by_user2 BIT(1) NOT NULL DEFAULT 0;