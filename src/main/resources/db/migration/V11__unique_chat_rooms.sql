-- V11: chat_rooms 唯一约束
-- 同一对用户 + 商品 + 会话类型只允许一个房间，防并发建房重复。
-- 列名为 JPA 物理命名结果 user1username / user2username（无下划线）。

-- 1) 重复房间的消息挂到保留房间
UPDATE chat_messages m
JOIN chat_rooms cr ON m.room_id = cr.id
JOIN chat_rooms keep
  ON cr.user1username = keep.user1username
 AND cr.user2username = keep.user2username
 AND cr.product_id = keep.product_id
 AND cr.room_type = keep.room_type
 AND keep.id < cr.id
SET m.room_id = keep.id;

-- 2) 删除重复房间（保留每组最小 id）
DELETE cr FROM chat_rooms cr
JOIN chat_rooms keep
  ON cr.user1username = keep.user1username
 AND cr.user2username = keep.user2username
 AND cr.product_id = keep.product_id
 AND cr.room_type = keep.room_type
 AND cr.id > keep.id;

-- 3) 唯一索引
ALTER TABLE chat_rooms
    ADD UNIQUE KEY uk_chat_rooms_pair (user1username, user2username, product_id, room_type);
