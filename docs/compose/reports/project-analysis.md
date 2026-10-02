# 优品汇 - 二手交易平台 系统分析报告

> **状态说明（2026-10）**：本文档描述的是重构前的 Thymeleaf SSR 版本。当前前端已改为 Vue 3 SPA（`frontend/`），由 `SpaController` 回退到 `index.html`；后端 REST API 与数据库模型基本未变。最新工程说明见根目录 `AGENT.md`。

## 一、项目概述

- **项目名称**：优品汇 (secondhand-mall)
- **项目类型**：B2C 二手商品交易平台（毕业设计）
- **架构模式**：前后端不分离，Thymeleaf 服务端渲染 (SSR)（**历史版本**；现行前端为 Vue 3 SPA）
- **运行端口**：localhost:8080
- **数据库**：MySQL 8.0+，库名 seconddata

## 二、技术栈

| 层级 | 技术选型 |
|------|----------|
| 后端框架 | Java 17 + Spring Boot 3.2.7 |
| 模板引擎 | Thymeleaf（服务端渲染页面） |
| ORM | Spring Data JPA（Hibernate，ddl-auto=update） |
| 安全框架 | Spring Security + BCrypt 密码加密 + HttpSession 认证 |
| 数据库 | MySQL 8.0+ |
| 前端 | HTML5 + CSS3 + Vanilla JavaScript（App 命名空间封装） |
| 图标库 | Font Awesome 6.4.0 |
| 字体 | Google Fonts Inter + 系统中文字体 |

## 三、系统架构

### 3.1 总体架构

采用经典的 MVC 三层架构：

```
浏览器 → Controller（页面路由 + REST API）→ Service（业务逻辑）→ Repository（JPA 数据访问）→ MySQL
```

### 3.2 目录结构

```
src/main/java/com/secondhand/mall/
├── SecondhandMallApplication.java       # 启动类 + JVM ShutdownHook
├── controller/                          # 控制器层（7个）
│   ├── PageController.java              # 页面路由（返回 Thymeleaf 模板名）
│   ├── ApiController.java               # 核心 REST API（商品/购物车/订单/地址/评论）
│   ├── ChatApiController.java           # 聊天 API（聊天室/消息）
│   ├── FavoriteApiController.java       # 收藏 API
│   ├── AdminApiController.java          # 管理后台 API
│   ├── UserController.java              # 用户头像上传 API
│   ├── BaseApiController.java           # 基类（统一响应构建 + 登录校验）
│   └── ShutdownController.java          # /internal/shutdown 端点
├── service/                             # 服务层（10个）
│   ├── ProductService.java              # 商品服务（头像跟随机制）
│   ├── OrderService.java                # 订单服务（悲观锁并发安全）
│   ├── UserService.java                 # 用户服务（BCrypt + Session）
│   ├── CartService.java                 # 购物车服务
│   ├── ChatService.java                 # 聊天服务（N+1 查询优化）
│   ├── FavoriteService.java             # 收藏服务
│   ├── CommentService.java              # 评论服务
│   ├── AddressService.java              # 地址服务
│   ├── AdminService.java                # 管理服务（级联删除）
│   └── FileUploadService.java           # 文件上传（Base64 解码存储）
├── entity/                              # JPA 实体（9个）
├── repository/                          # Spring Data JPA Repository（9个）
├── config/                              # 配置类
│   ├── SecurityConfig.java              # Spring Security 配置
│   ├── SessionValidationFilter.java     # Session 一致性校验过滤器
│   ├── GlobalModelAdvice.java           # 全局 Model 增强（注入登录状态）
│   ├── DataInitializer.java             # 启动初始化（admin 账号 + 密码修复）
│   ├── WebConfig.java                   # 静态资源映射
│   └── StartupRunner.java               # 启动执行器
├── dto/                                 # 数据传输对象
│   ├── ApiResponse.java                 # 统一 API 响应格式
│   ├── LoginRequest.java                # 登录请求体
│   ├── RegisterRequest.java             # 注册请求体
│   └── CartItemDTO.java                 # 购物车项 DTO
└── exception/                           # 自定义异常
    ├── UnauthorizedException.java       # 未授权异常
    └── NotFoundException.java           # 资源不存在异常

src/main/resources/
├── application.properties               # 应用配置
├── templates/                           # Thymeleaf 模板（17个页面）
│   ├── index.html                       # 首页（Premium 设计）
│   ├── category.html                    # 分类页（客户端 API 分页）
│   ├── product.html                     # 商品详情
│   ├── cart.html                        # 购物车
│   ├── checkout.html                    # 结算页
│   ├── upload.html                      # 发布商品
│   ├── messages.html                    # 聊天（inline style）
│   ├── favorites.html                   # 收藏页
│   ├── my-orders.html                   # 买家订单
│   ├── seller-orders.html               # 卖家订单
│   ├── order-detail.html                # 订单详情
│   ├── search.html                      # 搜索结果
│   ├── user.html                        # 个人中心
│   ├── login.html                       # 登录/注册（Tab 切换）
│   ├── base.html                        # 基础模板（大部分页面未引用）
│   ├── admin/dashboard.html             # 管理后台仪表盘
│   ├── admin/products.html              # 管理后台商品管理
│   └── admin/users.html                 # 管理后台用户管理
├── static/css/style.css                 # 主样式表（~4285行）
├── static/js/main.js                    # 主 JS（App 命名空间，~1103行）
└── static/images/sunset-beach.jpg       # 静态图片
```

## 四、数据库设计

### 4.1 ER 关系图

```
users ──< cart_items >── products
  │                          │
  │──< orders >──────────────┘
  │
  │──< favorites >── products
  │
  │──< comments >── products
  │
  │──< addresses
  │
  └──< chat_rooms >── users (user1, user2)
         │
         └──< chat_messages
```

### 4.2 各表详细结构

#### users 用户表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, 自增 | 主键 |
| username | VARCHAR(100) | NOT NULL, UNIQUE | 用户名 |
| password | VARCHAR(255) | NOT NULL | BCrypt 加密密码 |
| email | VARCHAR(100) | | 邮箱 |
| phone | VARCHAR(20) | | 手机号 |
| nickname | VARCHAR(100) | | 昵称 |
| role | VARCHAR(20) | NOT NULL, DEFAULT 'user' | 角色（user/admin） |
| balance | DECIMAL(12,2) | NOT NULL, DEFAULT 10000.00 | 账户余额 |
| avatar | VARCHAR(255) | NOT NULL, DEFAULT '/user-avatar/default.jpg' | 头像路径 |
| session_id | VARCHAR(100) | | 当前会话 ID |
| last_active_time | DATETIME | | 最后活跃时间 |
| created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | 注册时间 |

#### products 商品表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, 自增 | 主键 |
| name | VARCHAR(255) | NOT NULL | 商品名称 |
| category | VARCHAR(100) | NOT NULL | 分类（手机/笔记本电脑/耳机/相机等） |
| description | TEXT | | 商品描述 |
| original_price | DECIMAL(12,2) | NOT NULL | 原价 |
| second_hand_price | DECIMAL(12,2) | NOT NULL | 二手售价 |
| item_condition | VARCHAR(50) | NOT NULL | 新旧程度（全新/几乎全新/轻微使用/明显使用/重度使用） |
| seller_name | VARCHAR(100) | NOT NULL | 卖家昵称 |
| seller_phone | VARCHAR(20) | | 卖家电话（nullable） |
| seller_username | VARCHAR(100) | | 卖家用户名 |
| seller_avatar | VARCHAR(255) | | 卖家头像（运行时从 User 表填充） |
| image_url | TEXT | | 商品图片 URL（多张逗号分隔存储） |
| quantity | INT | NOT NULL, DEFAULT 1 | 库存数量 |
| status | VARCHAR(20) | DEFAULT 'active' | 状态（active/sold/deleted） |
| created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | 创建时间 |

#### orders 订单表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, 自增 | 主键 |
| order_number | VARCHAR(30) | UNIQUE | 订单编号（6位随机数字） |
| username | VARCHAR(100) | NOT NULL | 买家用户名 |
| product_id | BIGINT | NOT NULL | 商品 ID |
| seller_username | VARCHAR(100) | | 卖家用户名 |
| product_name | VARCHAR(255) | NOT NULL | 商品名称（快照） |
| product_price | DECIMAL(12,2) | NOT NULL | 成交价格（快照） |
| quantity | INT | | 购买数量 |
| category | VARCHAR(100) | NOT NULL | 商品分类（快照） |
| image_url | TEXT | NOT NULL | 商品图片（快照） |
| receiver_name | VARCHAR(100) | NOT NULL | 收货人姓名 |
| receiver_phone | VARCHAR(20) | NOT NULL | 收货人电话 |
| receiver_address | TEXT | NOT NULL | 收货地址 |
| payment_method | VARCHAR(50) | NOT NULL | 支付方式 |
| status | VARCHAR(20) | DEFAULT 'unshipped' | 订单状态 |
| tracking_number | VARCHAR(100) | | 物流单号 |
| created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | 下单时间 |

**订单状态流转**：unshipped → shipped → received → completed（或 cancelled）

#### 其他表

- **cart_items**：购物车项（username + product_id）
- **comments**：评论（product_id + user_name + content）
- **favorites**：收藏（username + product_id）
- **addresses**：收货地址（username + 省市区详细地址 + is_default）
- **chat_rooms**：聊天室（user1_username + user2_username + product_id + 未读计数）
- **chat_messages**：聊天消息（room_id + sender_username + content + message_type）

## 五、后端实现详解

### 5.1 认证与安全

**认证机制**：基于 HttpSession 的传统认证，非 JWT。

1. **登录流程**：用户提交用户名密码 → UserService 校验 BCrypt → 将 username/nickname/role 存入 HttpSession → 返回用户信息给前端
2. **Session 校验**：SessionValidationFilter 在每个请求时校验 HttpSession 中的 username 与数据库中存储的 sessionId 是否一致，防止多标签页账号串扰
3. **密码安全**：BCryptPasswordEncoder 加密存储，DataInitializer 启动时自动修复非 BCrypt 格式的密码
4. **Spring Security 配置**：禁用 CSRF，所有请求放行（权限由 Controller 层 Session 校验），禁用表单登录和 HTTP Basic

### 5.2 商品模块

**ProductService** 核心逻辑：

- **头像跟随机制**：`fillSellerAvatar()` 方法在每次获取商品时，实时从 User 表读取最新头像和昵称，覆盖商品中的 sellerAvatar 和 sellerName。这确保用户修改头像后，所有关联商品立即显示新头像
- **多条件筛选**：支持分类、价格区间、新旧程度的任意组合筛选，以及价格升序/降序和最新排序
- **手动分页**：API 返回全量筛选结果后，由 Controller 层进行内存分页（每页12件）
- **商品图片**：支持多张图片，以逗号分隔存储在 TEXT 字段中，通过 `getImageUrls()`/`setImageUrls()` 辅助方法转换

**FileUploadService**：前端将图片转为 Base64 编码发送，后端解码后保存到 `product-images/` 目录，文件名格式为 `UUID_时间戳.扩展名`。单张最大 5MB，最多 9 张。

### 5.3 订单模块

**OrderService** 核心逻辑：

- **悲观锁并发控制**：`findByIdForUpdate()` 使用 JPA `@Lock(LockModeType.PESSIMISTIC_WRITE)` 锁定商品记录，防止并发下单导致超卖
- **下单流程**：
  1. 锁定商品记录
  2. 校验：商品未售出、库存充足、非自买、余额充足、价格一致
  3. 扣减买家余额
  4. 库存递减（归零则标记 status=sold）
  5. 增加卖家余额
  6. 生成6位随机订单号（唯一性校验，最多重试10次）
  7. 创建订单记录
- **取消订单**：仅 unshipped 状态可取消，退还买家余额、扣回卖家余额、恢复商品库存
- **状态流转校验**：使用 Map 定义合法的状态转换路径，非法转换抛出异常
- **权限控制**：发货操作仅卖家可执行，确认收货仅买家可执行

### 5.4 聊天模块

**ChatService** 核心逻辑：

- **聊天室管理**：基于商品的1对1聊天，查找或创建聊天室
- **消息发送**：校验消息内容（非空、长度限制5000字符）、消息类型（text/image）、用户是否为聊天室成员
- **未读计数**：ChatRoom 中维护 user1Unread 和 user2Unread 两个计数器，发送消息时递增对方计数，打开聊天室时清零
- **N+1 查询优化**：`getRoomList()` 方法批量查询关联的用户和商品信息，避免逐个聊天室分别查询
- **软删除**：聊天室删除采用标记 `deleted=true`，而非物理删除

### 5.5 管理后台

**AdminService**：

- **统计数据**：返回商品总数、用户总数、评论总数
- **用户管理**：删除用户时级联删除其所有关联数据（订单、购物车、收藏、地址、评论、聊天记录、聊天室）
- **权限校验**：`checkAdmin()` 方法在每个管理 API 调用前验证当前用户 role 是否为 admin

### 5.6 全局配置

- **GlobalModelAdvice**：通过 `@ControllerAdvice` 向所有 Thymeleaf 页面注入 `isLoggedIn`、`sessionUsername`、`sessionNickname`、`sessionRole`，实现服务端渲染正确的导航栏状态
- **WebConfig**：将 `product-images/` 和 `user-avatar/` 目录映射为可访问的静态资源
- **DataInitializer**：应用启动时确保 admin 账号存在且密码正确，自动修复非 BCrypt 格式的密码（重置为 123456）

## 六、前端实现详解

### 6.1 页面渲染策略

| 页面 | 渲染方式 | 数据来源 | 分页 |
|------|----------|----------|------|
| 首页 | Thymeleaf SSR | Controller 传入 Model | 服务端（12件/页） |
| 分类页 | JS 客户端 | `/api/products` API | 客户端（12件/页） |
| 商品详情 | JS 客户端 | `/api/products/{id}` API | 无 |
| 购物车 | JS 客户端 | `/api/cart` API | 无 |
| 结算 | JS 客户端 | `/api/addresses` + `/api/cart` API | 无 |
| 发布商品 | JS 客户端 | 前端表单 → `/api/upload-image` + `/api/products` | 无 |
| 聊天 | JS 客户端 | `/api/chat/*` API | 无 |
| 收藏 | JS 客户端 | `/api/favorites` API | 无 |
| 买家订单 | JS 客户端 | `/api/orders` API | 无 |
| 卖家订单 | JS 客户端 | `/api/seller/orders` API | 无 |
| 搜索 | Thymeleaf SSR | Controller 传入 Model | 无 |
| 登录/注册 | Thymeleaf + JS | 前端表单 → `/api/login` + `/api/register` | 无 |
| 管理后台 | 独立 inline style | `/api/admin/*` API | 无 |

### 6.2 JavaScript 架构（App 命名空间）

`main.js` 使用 IIFE 封装为 `window.App` 对象，提供以下模块：

| 模块 | 功能 |
|------|------|
| Toast | 消息提示组件（success/error/warning，自动消失） |
| Modal | 模态对话框（支持确认/取消回调） |
| API Helper | `App.api(url, options)` 封装 fetch，自动设置 Content-Type，处理 401 跳转 |
| User State | 使用 sessionStorage 存储登录状态，标签页关闭自动清除 |
| Session Check | 每5秒调用 `/api/session` 比对 HttpSession 与本地状态，防止账号串扰 |
| Navigation Update | 动态更新导航栏（登录显示头像+昵称+下拉菜单，未登录显示登录/注册按钮） |
| Dropdown | 用户下拉菜单（事件委托，动态计算位置，支持滚动跟随） |
| Ripple | Material Design 风格的点击波纹效果 |
| Back to Top | 滚动超过400px显示返回顶部按钮 |
| Lazy Load | IntersectionObserver 实现图片懒加载（100px 预加载距离） |
| 3D Card | 鼠标追踪的卡片3D倾斜效果 |
| Cursor Glow | 鼠标跟随的光晕效果（平滑跟随 + 交互元素高亮） |

### 6.3 CSS 设计系统

`style.css`（~4285行）定义了完整的视觉设计系统：

**配色方案（暖金色奶油色）**：
- 背景：多层 radial-gradient `#FFF8F0 → #FFF3E6 → #FFEDDA`
- 强调色：金色 `#E8913A` / `#D47A2A`
- 文字：深色 `#2D2420`，次要 `#5A4A3A`
- 成功：绿色 `#5A9A5A`
- 错误：红色 `#D4604A`

**Glass 效果**：仅保留于导航栏、模态框、Toast 三个组件，使用 `backdrop-filter: blur()` 实现毛玻璃效果。

**z-index Token 系统**：
```
--z-base: 1
--z-dropdown: 100
--z-sticky: 200
--z-nav: 300
--z-modal-backdrop: 400
--z-modal: 500
--z-toast: 600
--z-tooltip: 700
--z-overlay: 800
```

**CSS 变量体系**：颜色、字体、间距、阴影、圆角、过渡动画全部通过 CSS 自定义属性（`--color-*`、`--font-*`、`--shadow-*`、`--radius-*`、`--transition-*`）管理，便于全局主题调整。

### 6.4 聊天页特殊实现

聊天页使用 inline style 而非引用 style.css，原因是旧的聊天类名与新的设计系统冲突。布局为：
- 居中卡片：`max-width: 1000px; width: 90%; margin: 20px auto`
- 左侧联系人列表固定宽度 260px
- 右侧聊天区 `flex: 1`
- 消息气泡 `max-width: 260px`

## 七、API 接口清单

### 7.1 公开 API（无需登录）

| 方法 | 路径 | 请求体/参数 | 响应 |
|------|------|-------------|------|
| POST | `/api/login` | `{username, password}` | `{success, username, nickname, balance, avatar, role}` |
| POST | `/api/register` | `{username, password, nickname, email, phone}` | `{success, username, nickname, balance, avatar}` |
| GET | `/api/products` | `?category&minPrice&maxPrice&condition&sortBy&page&size` | `{success, products[], total, totalPages, currentPage}` |
| GET | `/api/products/{id}` | | `{success, product}` |

### 7.2 需登录 API

**会话管理**：
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/session` | 获取当前用户信息 + 买家/卖家订单角标 |
| POST | `/api/logout` | 退出登录，清除 Session |

**购物车**：
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/cart` | 加入购物车（body: `{productId}`） |
| GET | `/api/cart` | 获取购物车列表 |
| GET | `/api/cart/count` | 获取购物车数量角标 |
| DELETE | `/api/cart/{id}` | 移除购物车项 |
| DELETE | `/api/cart` | 清空购物车 |

**订单**：
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/orders` | 创建订单（body: 含 productId, 收货信息, 支付方式） |
| GET | `/api/orders` | 买家订单列表（`?status` 筛选） |
| GET | `/api/orders/{id}` | 订单详情 |
| PUT | `/api/orders/{id}` | 更新订单状态（body: `{status, trackingNumber}`） |
| DELETE | `/api/orders/{id}` | 删除订单（仅已完成/已取消） |
| POST | `/api/orders/{id}/cancel` | 取消订单（退还余额） |
| POST | `/api/orders/{id}/complete` | 确认收货 |
| GET | `/api/seller/orders` | 卖家订单列表 |

**商品**：
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/products` | 发布商品 |
| PUT | `/api/products/{id}` | 更新商品（仅卖家） |
| DELETE | `/api/products/{id}` | 删除商品（仅卖家，软删除） |
| POST | `/api/upload-image` | 上传图片（Base64，支持多张） |
| GET | `/api/user/products` | 获取当前用户发布的商品 |

**评论**：
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/products/{id}/comments` | 获取商品评论列表 |
| POST | `/api/products/{id}/comments` | 发表评论（body: `{content}`） |
| DELETE | `/api/products/{id}/comments/{commentId}` | 删除评论（评论者或商品拥有者） |

**收藏**：
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/favorites/toggle` | 收藏/取消收藏切换 |
| GET | `/api/favorites/check/{productId}` | 检查是否已收藏 |
| GET | `/api/favorites` | 收藏列表 |
| DELETE | `/api/favorites/{favoriteId}` | 取消收藏 |

**用户**：
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/user` | 获取用户信息 |
| PUT | `/api/user` | 更新用户信息（含密码修改） |
| POST | `/api/user/avatar` | 上传头像（MultipartFile） |

**地址**：
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/addresses` | 获取地址列表 |
| POST | `/api/addresses` | 添加地址 |
| PUT | `/api/addresses/{id}` | 更新地址 |
| DELETE | `/api/addresses/{id}` | 删除地址 |

**聊天**：
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/chat/room` | 查找聊天室（body: `{productId}`） |
| POST | `/api/chat/room/create` | 创建聊天室 |
| GET | `/api/chat/rooms` | 聊天室列表（含未读数） |
| POST | `/api/chat/message` | 发送消息（body: `{roomId, content, type}`） |
| GET | `/api/chat/messages/{roomId}` | 获取消息记录（`?sinceId` 增量拉取） |
| DELETE | `/api/chat/room/{roomId}` | 删除聊天室（软删除） |
| GET | `/api/chat/unread` | 获取总未读消息数 |

### 7.3 管理 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/admin/login` | 管理员登录 |
| GET | `/api/admin/stats` | 统计数据（商品数/用户数/评论数） |
| GET | `/api/admin/products` | 所有商品列表 |
| DELETE | `/api/admin/products/{id}` | 管理员删除商品（物理删除） |
| GET | `/api/admin/users` | 所有用户列表 |
| DELETE | `/api/admin/users/{id}` | 删除用户（级联删除所有关联数据） |

### 7.4 内部 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/internal/shutdown` | 优雅关闭应用（无需登录） |
| POST | `/api/admin/shutdown` | 管理员关闭应用 |

## 八、核心业务规则

### 8.1 头像跟随（最高优先级）

所有显示用户头像的地方，必须实时从 User 表获取最新头像。通过 `ProductService.fillSellerAvatar()` 方法实现，每次获取商品时覆盖。不在商品表中固化头像，确保用户修改头像后所有关联页面立即生效。

### 8.2 登录状态管理

- 使用 `sessionStorage`（浏览器标签页关闭自动清除）
- 禁止在 `beforeunload` 中调用 logout（页面跳转会误触发）
- `validateSessionConsistency()` 每5秒比对 HttpSession 和 sessionStorage，防止多标签页账号串扰

### 8.3 购物车角标

导航栏购物车图标显示实时数量角标，通过 `GET /api/cart/count` 获取。刷新时机：页面加载、每5秒定时、加入/移除/清空/支付后。

### 8.4 分页规则

- 每页12件商品
- 首页：服务端 Thymeleaf 分页（`?page=N`）
- 分类页：客户端 API 分页
- 分类栏"全部"按钮所有页面都需添加

### 8.5 端口管理

应用结束运行时必须释放8080端口：
- `SecondhandMallApplication.java` 注册 JVM ShutdownHook
- `POST /internal/shutdown` API 端点
- `stop.ps1` 脚本：先 API 关闭，再 taskkill

## 九、设计规范

### 9.1 配色

暖金色奶油色主题，背景使用多层 radial-gradient，强调色为金色系。

### 9.2 Glass 效果

仅保留于3个组件：导航栏、模态框、Toast。其余使用纯白背景 + 细微阴影。

### 9.3 字体

通过 Google Fonts `@import` 引入 Inter 字体（保留 `display=swap`），中文使用系统字体栈（PingFang SC / Microsoft YaHei / Noto Sans SC）。

### 9.4 页面布局

所有页面统一使用：`header → category-bar → main-content → footer` 布局结构。

## 十、测试覆盖

项目包含21个单元测试，覆盖：AdminService、BigDecimal、CartService、ChatService、CommentService、OrderService（并发/状态流转/退款）、ProductService、UserService。

## 十一、部署与运行

**前置条件**：JDK 17 + MySQL 8.0+

```bash
# 启动
.\.maven\apache-maven-3.9.6\bin\mvn.cmd clean spring-boot:run

# 停止
.\stop.ps1
# 或
curl -X POST http://localhost:8080/internal/shutdown
```

**默认账号**：admin / admin123（管理员），新注册用户默认余额 ¥10,000。
