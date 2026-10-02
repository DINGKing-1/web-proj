-- 初始化数据
-- 注意：数据库表由JPA自动创建，这里只插入初始数据

-- 插入默认管理员账户（如果不存在）
INSERT IGNORE INTO users (username, password, email, phone, nickname) 
VALUES ('dky', 'dky123456', '2334575708@qq.com', '13800138000', 'dky');