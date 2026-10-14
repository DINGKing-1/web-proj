-- 聊天会话用户维度逻辑删除
ALTER TABLE chat_rooms ADD COLUMN deleted_by_user1 BIT(1) NOT NULL DEFAULT 0;
ALTER TABLE chat_rooms ADD COLUMN deleted_by_user2 BIT(1) NOT NULL DEFAULT 0;