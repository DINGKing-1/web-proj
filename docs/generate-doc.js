const fs = require('fs');
const { Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell,
        Header, Footer, AlignmentType, LevelFormat, HeadingLevel,
        BorderStyle, WidthType, ShadingType, PageNumber, PageBreak } = require('docx');

const border = { style: BorderStyle.SINGLE, size: 1, color: "CCCCCC" };
const borders = { top: border, bottom: border, left: border, right: border };
const cellMargins = { top: 60, bottom: 60, left: 100, right: 100 };

function headerCell(text, width) {
  return new TableCell({
    borders, width: { size: width, type: WidthType.DXA },
    shading: { fill: "2D5F8A", type: ShadingType.CLEAR },
    margins: cellMargins,
    verticalAlign: "center",
    children: [new Paragraph({ children: [new TextRun({ text, bold: true, color: "FFFFFF", font: "Arial", size: 20 })] })]
  });
}

function cell(text, width) {
  return new TableCell({
    borders, width: { size: width, type: WidthType.DXA },
    margins: cellMargins,
    children: [new Paragraph({ children: [new TextRun({ text, font: "Arial", size: 20 })] })]
  });
}

function codeCell(text, width) {
  return new TableCell({
    borders, width: { size: width, type: WidthType.DXA },
    margins: cellMargins,
    children: [new Paragraph({ children: [new TextRun({ text, font: "Consolas", size: 18 })] })]
  });
}

function sectionTitle(text) {
  return new Paragraph({ heading: HeadingLevel.HEADING_1, children: [new TextRun({ text, font: "Arial" })] });
}

function subTitle(text) {
  return new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun({ text, font: "Arial" })] });
}

function subSubTitle(text) {
  return new Paragraph({ heading: HeadingLevel.HEADING_3, children: [new TextRun({ text, font: "Arial" })] });
}

function para(text) {
  return new Paragraph({ spacing: { after: 120 }, children: [new TextRun({ text, font: "Arial", size: 22 })] });
}

function boldPara(label, text) {
  return new Paragraph({ spacing: { after: 120 }, children: [
    new TextRun({ text: label, bold: true, font: "Arial", size: 22 }),
    new TextRun({ text, font: "Arial", size: 22 })
  ]});
}

function makeTable(headers, rows, colWidths) {
  const totalWidth = colWidths.reduce((a, b) => a + b, 0);
  return new Table({
    width: { size: totalWidth, type: WidthType.DXA },
    columnWidths: colWidths,
    rows: [
      new TableRow({ children: headers.map((h, i) => headerCell(h, colWidths[i])) }),
      ...rows.map(row => new TableRow({
        children: row.map((c, i) => cell(c, colWidths[i]))
      }))
    ]
  });
}

// ========== 构建文档内容 ==========
const children = [];

// --- 封面 ---
children.push(new Paragraph({ spacing: { before: 3000 } }));
children.push(new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 200 }, children: [
  new TextRun({ text: "优品汇", font: "Arial", size: 56, bold: true, color: "2D5F8A" })
]}));
children.push(new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 100 }, children: [
  new TextRun({ text: "高端二手交易平台", font: "Arial", size: 36, color: "5A5A5A" })
]}));
children.push(new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 600 }, children: [
  new TextRun({ text: "系统功能与实现方法讲解", font: "Arial", size: 28, color: "888888" })
]}));
children.push(new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 100 }, children: [
  new TextRun({ text: "技术栈：Spring Boot 3.2.7 + Thymeleaf + MySQL 8.0 + JPA", font: "Arial", size: 22, color: "666666" })
]}));
children.push(new Paragraph({ alignment: AlignmentType.CENTER, children: [
  new TextRun({ text: "架构：前后端不分离 · Controller-Service-Repository 三层架构", font: "Arial", size: 22, color: "666666" })
]}));
children.push(new Paragraph({ children: [new PageBreak()] }));

// --- 目录 ---
children.push(sectionTitle("目录"));
const tocItems = [
  "一、项目概述与技术架构",
  "二、数据库设计（9张表）",
  "三、用户模块：登录/注册/个人中心",
  "四、商品模块：发布/浏览/搜索/详情",
  "五、购物车模块",
  "六、订单模块：下单/支付/状态流转",
  "七、收藏模块",
  "八、评论模块",
  "九、聊天模块",
  "十、管理后台模块",
  "十一、前端实现机制",
  "十二、安全与Session管理"
];
tocItems.forEach(item => {
  children.push(new Paragraph({ spacing: { after: 80 }, children: [new TextRun({ text: item, font: "Arial", size: 22 })] }));
});
children.push(new Paragraph({ children: [new PageBreak()] }));

// ========== 一、项目概述 ==========
children.push(sectionTitle("一、项目概述与技术架构"));

children.push(subTitle("1.1 项目简介"));
children.push(para("优品汇是一个B2C模式的高端二手商品交易平台，采用前后端不分离架构。前端使用HTML5+CSS3+Vanilla JavaScript，后端基于Spring Boot 3.2.7，数据库使用MySQL 8.0，ORM层使用Spring Data JPA。"));

children.push(subTitle("1.2 技术栈"));
children.push(makeTable(
  ["层级", "技术", "说明"],
  [
    ["后端框架", "Spring Boot 3.2.7", "Java 17，内嵌Tomcat"],
    ["模板引擎", "Thymeleaf", "服务端渲染页面"],
    ["ORM", "Spring Data JPA", "Hibernate，ddl-auto=update"],
    ["安全", "Spring Security + BCrypt", "密码加密 + HttpSession认证"],
    ["数据库", "MySQL 8.0+", "库名 seconddata"],
    ["前端", "HTML5 + CSS3 + JS", "App命名空间封装"],
    ["图标", "Font Awesome 6.4.0", "图标库"],
  ],
  [2000, 3000, 4360]
));

children.push(subTitle("1.3 系统架构图"));
children.push(para("系统采用经典的MVC三层架构：浏览器 → Controller（页面路由 + REST API）→ Service（业务逻辑）→ Repository（JPA数据访问）→ MySQL数据库。"));
children.push(para("Controller层共7个控制器，Service层共10个服务，Repository层共9个数据访问接口，对应9张数据库表。"));

children.push(subTitle("1.4 项目目录结构"));
children.push(makeTable(
  ["目录", "内容", "数量"],
  [
    ["controller/", "控制器（PageController, ApiController, ChatApiController等）", "7个"],
    ["service/", "业务服务（ProductService, OrderService, UserService等）", "10个"],
    ["entity/", "JPA实体（User, Product, Order等）", "9个"],
    ["repository/", "数据访问接口", "9个"],
    ["config/", "配置类（Security, Session, WebConfig等）", "6个"],
    ["dto/", "数据传输对象", "4个"],
    ["templates/", "Thymeleaf页面模板", "17个"],
    ["static/css/", "样式表 style.css", "~4285行"],
    ["static/js/", "JavaScript main.js", "~1103行"],
  ],
  [2000, 5360, 2000]
));
children.push(new Paragraph({ children: [new PageBreak()] }));

// ========== 二、数据库设计 ==========
children.push(sectionTitle("二、数据库设计（9张表）"));

children.push(subTitle("2.1 ER关系"));
children.push(para("users表是核心用户表，与products（通过seller_username）、orders（通过username和seller_username）、cart_items、favorites、comments、addresses、chat_rooms均有关联关系。"));

children.push(subTitle("2.2 users 用户表"));
children.push(makeTable(
  ["字段", "类型", "说明"],
  [
    ["id", "BIGINT PK", "自增主键"],
    ["username", "VARCHAR(100) UNIQUE", "用户名"],
    ["password", "VARCHAR(255)", "BCrypt加密密码"],
    ["nickname", "VARCHAR(100)", "昵称"],
    ["email", "VARCHAR(100)", "邮箱"],
    ["phone", "VARCHAR(20)", "手机号"],
    ["role", "VARCHAR(20)", "角色（user/admin）"],
    ["balance", "DECIMAL(12,2)", "余额（默认¥10,000）"],
    ["avatar", "VARCHAR(255)", "头像路径"],
    ["session_id", "VARCHAR(100)", "当前会话ID"],
    ["last_active_time", "DATETIME", "最后活跃时间"],
  ],
  [2500, 3360, 3500]
));

children.push(subTitle("2.3 products 商品表"));
children.push(makeTable(
  ["字段", "类型", "说明"],
  [
    ["id", "BIGINT PK", "自增主键"],
    ["name", "VARCHAR(255)", "商品名称"],
    ["category", "VARCHAR(100)", "分类（手机/笔记本/耳机等）"],
    ["description", "TEXT", "商品描述"],
    ["original_price", "DECIMAL(12,2)", "原价"],
    ["second_hand_price", "DECIMAL(12,2)", "二手售价"],
    ["item_condition", "VARCHAR(50)", "新旧程度"],
    ["seller_name", "VARCHAR(100)", "卖家昵称"],
    ["seller_username", "VARCHAR(100)", "卖家用户名"],
    ["seller_avatar", "VARCHAR(255)", "卖家头像（运行时填充）"],
    ["image_url", "TEXT", "商品图片（多张逗号分隔）"],
    ["quantity", "INT", "库存数量"],
    ["status", "VARCHAR(20)", "active/sold/deleted"],
  ],
  [2500, 3360, 3500]
));

children.push(subTitle("2.4 orders 订单表"));
children.push(makeTable(
  ["字段", "类型", "说明"],
  [
    ["id", "BIGINT PK", "自增主键"],
    ["order_number", "VARCHAR(30) UNIQUE", "6位随机订单号"],
    ["username", "VARCHAR(100)", "买家用户名"],
    ["product_id", "BIGINT", "商品ID"],
    ["seller_username", "VARCHAR(100)", "卖家用户名"],
    ["product_name", "VARCHAR(255)", "商品名称（快照）"],
    ["product_price", "DECIMAL(12,2)", "成交价格（快照）"],
    ["quantity", "INT", "购买数量"],
    ["receiver_name", "VARCHAR(100)", "收货人"],
    ["receiver_phone", "VARCHAR(20)", "收货电话"],
    ["receiver_address", "TEXT", "收货地址"],
    ["payment_method", "VARCHAR(50)", "支付方式"],
    ["status", "VARCHAR(20)", "订单状态"],
    ["tracking_number", "VARCHAR(100)", "物流单号"],
  ],
  [2500, 3360, 3500]
));

children.push(subTitle("2.5 其他表"));
children.push(makeTable(
  ["表名", "说明", "核心字段"],
  [
    ["cart_items", "购物车", "username, product_id"],
    ["comments", "评论", "product_id, user_name, content"],
    ["favorites", "收藏", "username, product_id"],
    ["addresses", "收货地址", "username, province/city/district/detail, is_default"],
    ["chat_rooms", "聊天室", "user1_username, user2_username, product_id, unread计数"],
    ["chat_messages", "聊天消息", "room_id, sender_username, content, message_type"],
  ],
  [2000, 2000, 5360]
));
children.push(new Paragraph({ children: [new PageBreak()] }));

// ========== 三、用户模块 ==========
children.push(sectionTitle("三、用户模块：登录/注册/个人中心"));

children.push(subTitle("3.1 页面说明"));
children.push(makeTable(
  ["页面", "模板文件", "渲染方式", "功能"],
  [
    ["登录/注册", "login.html", "Thymeleaf+JS", "Tab切换登录和注册表单"],
    ["个人中心", "user.html", "JS客户端", "查看/编辑资料、修改密码、上传头像"],
  ],
  [1500, 2000, 2000, 3860]
));

children.push(subTitle("3.2 API接口"));
children.push(makeTable(
  ["方法", "路径", "功能", "请求体"],
  [
    ["POST", "/api/login", "用户登录", "{username, password}"],
    ["POST", "/api/register", "用户注册", "{username, password, nickname, email, phone}"],
    ["POST", "/api/logout", "退出登录", "无"],
    ["GET", "/api/session", "获取当前登录状态", "无"],
    ["GET", "/api/user", "获取用户信息", "无"],
    ["PUT", "/api/user", "更新用户信息", "{nickname, email, phone, currentPassword, password, avatar}"],
    ["POST", "/api/user/avatar", "上传头像", "FormData(file)"],
  ],
  [1000, 2500, 2500, 3360]
));

children.push(subTitle("3.3 实现方法"));
children.push(subSubTitle("登录流程"));
children.push(para("1. 前端将用户名密码通过 App.api() 发送到 POST /api/login"));
children.push(para("2. ApiController.login() 接收请求，调用 UserService.login()"));
children.push(para("3. UserService 使用 BCryptPasswordEncoder.matches() 校验密码"));
children.push(para("4. 校验通过后，将 sessionId 存入 User 表，username/nickname/role 存入 HttpSession"));
children.push(para("5. 返回用户信息给前端，前端存入 sessionStorage"));

children.push(subSubTitle("注册流程"));
children.push(para("1. 前端校验用户名、昵称、密码非空"));
children.push(para("2. 调用 POST /api/register，UserService 检查用户名是否已存在"));
children.push(para("3. 使用 BCryptPasswordEncoder.encode() 加密密码"));
children.push(para("4. 新用户余额默认¥10,000，头像默认 /user-avatar/default.jpg"));
children.push(para("5. 注册成功后自动登录（Session写入）"));

children.push(subSubTitle("Session一致性校验"));
children.push(para("SessionValidationFilter 在每个请求时校验 HttpSession 中的 username 与数据库存储的 sessionId 是否一致。前端每5秒调用 /api/session 比对 HttpSession 和 sessionStorage，防止多标签页账号串扰。"));
children.push(new Paragraph({ children: [new PageBreak()] }));

// ========== 四、商品模块 ==========
children.push(sectionTitle("四、商品模块：发布/浏览/搜索/详情"));

children.push(subTitle("4.1 页面说明"));
children.push(makeTable(
  ["页面", "模板文件", "渲染方式", "功能"],
  [
    ["首页", "index.html", "Thymeleaf SSR", "Hero区+分类导航+最新商品(12件/页)"],
    ["分类页", "category.html", "JS客户端", "按分类浏览+多条件筛选+分页"],
    ["商品详情", "product.html", "JS客户端", "图片展示+评论+收藏+加购+联系卖家"],
    ["发布商品", "upload.html", "JS客户端", "多图上传(base64)+表单填写"],
    ["搜索结果", "search.html", "Thymeleaf SSR", "关键词搜索商品"],
  ],
  [1500, 2000, 2000, 3860]
));

children.push(subTitle("4.2 API接口"));
children.push(makeTable(
  ["方法", "路径", "功能", "参数/请求体"],
  [
    ["GET", "/api/products", "商品列表", "?category&minPrice&maxPrice&condition&sortBy&page&size"],
    ["GET", "/api/products/{id}", "商品详情", "路径参数id"],
    ["POST", "/api/products", "发布商品", "{name, category, description, originalPrice, secondHandPrice, condition, imageUrl, quantity}"],
    ["PUT", "/api/products/{id}", "更新商品", "{name, category, description, ...}"],
    ["DELETE", "/api/products/{id}", "删除商品", "路径参数id"],
    ["POST", "/api/upload-image", "上传图片", "{image: [base64 strings]}"],
    ["GET", "/api/user/products", "我的商品", "无"],
  ],
  [1000, 2500, 2000, 3860]
));

children.push(subTitle("4.3 实现方法"));
children.push(subSubTitle("商品发布"));
children.push(para("1. 前端将图片转为Base64编码，通过 POST /api/upload-image 上传"));
children.push(para("2. FileUploadService 解码Base64，保存到 product-images/ 目录，返回URL"));
children.push(para("3. 前端连同商品信息一起提交到 POST /api/products"));
children.push(para("4. ApiController 自动从 Session 获取卖家信息（username, nickname, avatar）"));
children.push(para("5. ProductService 校验价格合理性（二手价不能高于原价），保存商品"));

children.push(subSubTitle("商品筛选与分页"));
children.push(para("ProductService.getFilteredProducts() 支持分类、价格区间、新旧程度的任意组合筛选，以及价格升序/降序和最新排序。API返回全量筛选结果后，由Controller层进行内存分页（每页12件）。"));

children.push(subSubTitle("头像跟随机制"));
children.push(para("ProductService.fillSellerAvatar() 在每次获取商品时，实时从 User 表读取最新头像和昵称，覆盖商品中的 sellerAvatar 和 sellerName。这确保用户修改头像后，所有关联商品立即显示新头像。"));
children.push(new Paragraph({ children: [new PageBreak()] }));

// ========== 五、购物车模块 ==========
children.push(sectionTitle("五、购物车模块"));

children.push(subTitle("5.1 页面说明"));
children.push(makeTable(
  ["页面", "模板文件", "渲染方式", "功能"],
  [
    ["购物车", "cart.html", "JS客户端", "查看购物车商品列表、移除商品、清空购物车、去结算"],
  ],
  [1500, 2000, 2000, 3860]
));

children.push(subTitle("5.2 API接口"));
children.push(makeTable(
  ["方法", "路径", "功能", "请求体"],
  [
    ["POST", "/api/cart", "加入购物车", "{productId}"],
    ["GET", "/api/cart", "购物车列表", "无"],
    ["GET", "/api/cart/count", "购物车数量角标", "无"],
    ["DELETE", "/api/cart/{id}", "移除购物车项", "路径参数id"],
    ["DELETE", "/api/cart", "清空购物车", "无"],
  ],
  [1000, 2500, 2500, 3360]
));

children.push(subTitle("5.3 实现方法"));
children.push(para("CartService.addToCart() 在添加前进行三项校验：商品是否存在且未售出、是否为卖家自己发布的商品、是否已在购物车中（防重复添加）。购物车角标通过 GET /api/cart/count 获取，前端每5秒定时刷新，并在加入/移除/清空/支付后立即刷新。"));
children.push(new Paragraph({ children: [new PageBreak()] }));

// ========== 六、订单模块 ==========
children.push(sectionTitle("六、订单模块：下单/支付/状态流转"));

children.push(subTitle("6.1 页面说明"));
children.push(makeTable(
  ["页面", "模板文件", "渲染方式", "功能"],
  [
    ["结算页", "checkout.html", "JS客户端", "选择收货地址+支付方式+确认下单"],
    ["买家订单", "my-orders.html", "JS客户端", "查看我购买的订单列表"],
    ["卖家订单", "seller-orders.html", "JS客户端", "查看我卖出的订单列表"],
    ["订单详情", "order-detail.html", "Thymeleaf+JS", "订单详情+状态时间线"],
  ],
  [1500, 2000, 2000, 3860]
));

children.push(subTitle("6.2 API接口"));
children.push(makeTable(
  ["方法", "路径", "功能", "说明"],
  [
    ["POST", "/api/orders", "创建订单", "body: productId, 收货信息, 支付方式"],
    ["GET", "/api/orders", "买家订单列表", "?status 筛选"],
    ["GET", "/api/orders/{id}", "订单详情", "需校验买家或卖家身份"],
    ["PUT", "/api/orders/{id}", "更新状态", "body: {status, trackingNumber}"],
    ["DELETE", "/api/orders/{id}", "删除订单", "仅已完成/已取消"],
    ["POST", "/api/orders/{id}/cancel", "取消订单", "退还余额"],
    ["POST", "/api/orders/{id}/complete", "确认收货", "买家操作"],
    ["GET", "/api/seller/orders", "卖家订单列表", "?status 筛选"],
  ],
  [1000, 2500, 2000, 3860]
));

children.push(subTitle("6.3 实现方法"));
children.push(subSubTitle("下单流程（悲观锁并发控制）"));
children.push(para("1. 使用 JPA @Lock(LockModeType.PESSIMISTIC_WRITE) 锁定商品记录"));
children.push(para("2. 校验：商品未售出、库存>0、非自买、余额充足、价格一致"));
children.push(para("3. 扣减买家余额"));
children.push(para("4. 库存递减（归零则标记status=sold）"));
children.push(para("5. 增加卖家余额"));
children.push(para("6. 生成6位随机订单号（唯一性校验，最多重试10次）"));
children.push(para("7. 创建订单记录"));

children.push(subSubTitle("订单状态流转"));
children.push(para("状态流转路径：unshipped(待发货) → shipped(运输中) → received(待收货) → completed(已完成)。取消操作仅unshipped状态可执行，取消后退还买家余额、扣回卖家余额、恢复商品库存。"));

children.push(subSubTitle("权限控制"));
children.push(para("发货操作（shipped）仅卖家可执行，确认收货（received/completed）仅买家可执行。非法状态流转和越权操作均抛出异常。"));
children.push(new Paragraph({ children: [new PageBreak()] }));

// ========== 七、收藏模块 ==========
children.push(sectionTitle("七、收藏模块"));

children.push(subTitle("7.1 页面与API"));
children.push(makeTable(
  ["页面", "模板文件", "功能"],
  [
    ["收藏页", "favorites.html", "查看已收藏商品列表，支持取消收藏"],
  ],
  [1500, 2000, 5860]
));
children.push(makeTable(
  ["方法", "路径", "功能"],
  [
    ["POST", "/api/favorites/toggle", "收藏/取消收藏切换"],
    ["GET", "/api/favorites/check/{productId}", "检查是否已收藏"],
    ["GET", "/api/favorites", "收藏列表"],
    ["DELETE", "/api/favorites/{favoriteId}", "取消收藏"],
  ],
  [1000, 4000, 4360]
));

children.push(subTitle("7.2 实现方法"));
children.push(para("FavoriteService.toggleFavorite() 实现收藏切换：如果已收藏则删除，未收藏则新增。收藏列表通过 FavoriteService.getFavorites() 获取，同样使用头像跟随机制从User表获取最新头像。"));
children.push(new Paragraph({ children: [new PageBreak()] }));

// ========== 八、评论模块 ==========
children.push(sectionTitle("八、评论模块"));

children.push(subTitle("8.1 页面与API"));
children.push(para("评论功能集成在商品详情页（product.html）中，支持查看评论列表、发表评论、删除评论。"));
children.push(makeTable(
  ["方法", "路径", "功能"],
  [
    ["GET", "/api/products/{id}/comments", "获取商品评论列表"],
    ["POST", "/api/products/{id}/comments", "发表评论（body: {content}）"],
    ["DELETE", "/api/products/{id}/comments/{commentId}", "删除评论"],
  ],
  [1000, 4000, 4360]
));

children.push(subTitle("8.2 实现方法"));
children.push(para("CommentService.addComment() 校验评论内容非空且不超过500字。评论列表展示时，从User表获取评论者的最新头像和昵称。删除权限：评论者本人或商品拥有者均可删除。"));
children.push(new Paragraph({ children: [new PageBreak()] }));

// ========== 九、聊天模块 ==========
children.push(sectionTitle("九、聊天模块"));

children.push(subTitle("9.1 页面与API"));
children.push(makeTable(
  ["页面", "模板文件", "渲染方式", "功能"],
  [
    ["消息", "messages.html", "JS客户端(inline style)", "左侧联系人列表+右侧聊天消息区"],
  ],
  [1500, 2000, 2500, 3360]
));
children.push(makeTable(
  ["方法", "路径", "功能"],
  [
    ["POST", "/api/chat/room", "查找聊天室（body: {productId}）"],
    ["POST", "/api/chat/room/create", "创建聊天室"],
    ["GET", "/api/chat/rooms", "聊天室列表（含未读数）"],
    ["POST", "/api/chat/message", "发送消息（body: {roomId, content, type}）"],
    ["GET", "/api/chat/messages/{roomId}", "获取消息记录（?sinceId增量拉取）"],
    ["DELETE", "/api/chat/room/{roomId}", "删除聊天室（软删除）"],
    ["GET", "/api/chat/unread", "获取总未读消息数"],
  ],
  [1000, 4000, 4360]
));

children.push(subTitle("9.2 实现方法"));
children.push(para("ChatService 基于商品的1对1聊天。消息发送时校验内容非空、长度不超过5000字符、用户为聊天室成员。ChatRoom 中维护 user1Unread 和 user2Unread 计数器，发送消息时递增对方计数，打开聊天室时清零。getRoomList() 使用批量查询避免N+1问题。"));
children.push(new Paragraph({ children: [new PageBreak()] }));

// ========== 十、管理后台 ==========
children.push(sectionTitle("十、管理后台模块"));

children.push(subTitle("10.1 页面与API"));
children.push(makeTable(
  ["页面", "模板文件", "功能"],
  [
    ["仪表盘", "admin/dashboard.html", "统计数据（商品数/用户数/评论数）"],
    ["商品管理", "admin/products.html", "查看所有商品，支持删除"],
    ["用户管理", "admin/users.html", "查看所有用户，支持删除（级联删除关联数据）"],
  ],
  [1500, 2500, 5360]
));
children.push(makeTable(
  ["方法", "路径", "功能"],
  [
    ["POST", "/api/admin/login", "管理员登录"],
    ["GET", "/api/admin/stats", "统计数据"],
    ["GET", "/api/admin/products", "所有商品列表"],
    ["DELETE", "/api/admin/products/{id}", "删除商品（物理删除）"],
    ["GET", "/api/admin/users", "所有用户列表"],
    ["DELETE", "/api/admin/users/{id}", "删除用户（级联删除）"],
  ],
  [1000, 3000, 5360]
));

children.push(subTitle("10.2 实现方法"));
children.push(para("AdminService.deleteUser() 执行级联删除：依次删除用户的订单、购物车、收藏、地址、评论、聊天消息、聊天室，最后删除用户本身。所有操作在 @Transactional 事务中执行。"));
children.push(new Paragraph({ children: [new PageBreak()] }));

// ========== 十一、前端实现 ==========
children.push(sectionTitle("十一、前端实现机制"));

children.push(subTitle("11.1 JavaScript架构（App命名空间）"));
children.push(para("main.js 封装为 window.App IIFE，提供以下核心模块："));
children.push(makeTable(
  ["模块", "功能", "实现方式"],
  [
    ["Toast", "消息提示", "动态创建DOM元素，自动消失（success/error/warning）"],
    ["Modal", "模态对话框", "支持确认/取消回调，点击外部关闭"],
    ["API Helper", "请求封装", "App.api()封装fetch，自动处理401跳转"],
    ["User State", "登录状态", "sessionStorage存储，标签页关闭自动清除"],
    ["Session Check", "账号串扰防护", "每5秒比对HttpSession和sessionStorage"],
    ["Navigation", "导航栏更新", "根据登录状态动态渲染导航内容"],
    ["Dropdown", "用户下拉菜单", "事件委托，动态计算位置"],
    ["Lazy Load", "图片懒加载", "IntersectionObserver，100px预加载"],
    ["3D Card", "卡片3D效果", "鼠标追踪的倾斜效果"],
    ["Cursor Glow", "光晕效果", "鼠标跟随的平滑光晕"],
  ],
  [2000, 2500, 4860]
));

children.push(subTitle("11.2 CSS设计系统"));
children.push(para("style.css 定义了完整的视觉设计系统，包含："));
children.push(para("配色方案：暖金色奶油色系（背景 #FFF8F0，强调色 #E8913A）"));
children.push(para("Glass效果：仅用于导航栏、模态框、Toast（backdrop-filter: blur）"));
children.push(para("z-index Token：9级层级管理（base:1 到 overlay:800）"));
children.push(para("CSS变量：颜色、字体、间距、阴影、圆角、过渡动画全部token化"));

children.push(subTitle("11.3 页面渲染策略"));
children.push(makeTable(
  ["页面", "渲染方式", "数据来源", "分页"],
  [
    ["首页", "Thymeleaf SSR", "Controller传入Model", "服务端12件/页"],
    ["分类页", "JS客户端", "/api/products API", "客户端12件/页"],
    ["商品详情", "JS客户端", "/api/products/{id}", "无"],
    ["购物车", "JS客户端", "/api/cart API", "无"],
    ["结算", "JS客户端", "/api/addresses + /api/cart", "无"],
    ["发布商品", "JS客户端", "前端表单→API", "无"],
    ["聊天", "JS客户端", "/api/chat/* API", "无"],
    ["搜索", "Thymeleaf SSR", "Controller传入Model", "无"],
    ["管理后台", "独立inline style", "/api/admin/* API", "无"],
  ],
  [2000, 2000, 2860, 2500]
));
children.push(new Paragraph({ children: [new PageBreak()] }));

// ========== 十二、安全 ==========
children.push(sectionTitle("十二、安全与Session管理"));

children.push(subTitle("12.1 Spring Security配置"));
children.push(para("SecurityConfig 禁用CSRF，所有请求放行（权限由Controller层Session校验），禁用表单登录和HTTP Basic。登录成功后通过SessionValidationFilter校验Session一致性。"));

children.push(subTitle("12.2 密码安全"));
children.push(para("使用BCryptPasswordEncoder加密存储密码。DataInitializer启动时自动修复非BCrypt格式的密码（重置为123456）。"));

children.push(subTitle("12.3 Session管理"));
children.push(para("前端使用sessionStorage存储登录状态（标签页关闭自动清除）。SessionValidationFilter在每个请求时校验HttpSession中的username与数据库存储的sessionId是否一致。前端每5秒调用/api/session比对HttpSession和sessionStorage，防止多标签页账号串扰。"));

children.push(subTitle("12.4 核心业务规则汇总"));
children.push(makeTable(
  ["规则", "说明"],
  [
    ["头像跟随", "所有显示头像处实时从User表获取，不在商品表中固化"],
    ["登录状态", "sessionStorage存储，禁止beforeunload调logout"],
    ["购物车角标", "每5秒定时刷新+关键操作后刷新"],
    ["分页规则", "每页12件，首页服务端分页，分类页客户端分页"],
    ["端口管理", "JVM ShutdownHook + /internal/shutdown API + stop.ps1脚本"],
    ["悲观锁下单", "findByIdForUpdate防止并发超卖"],
  ],
  [2500, 6860]
));

// ========== 构建文档 ==========
const doc = new Document({
  styles: {
    default: { document: { run: { font: "Arial", size: 22 } } },
    paragraphStyles: [
      { id: "Heading1", name: "Heading 1", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 32, bold: true, font: "Arial", color: "2D5F8A" },
        paragraph: { spacing: { before: 360, after: 200 }, outlineLevel: 0 } },
      { id: "Heading2", name: "Heading 2", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 26, bold: true, font: "Arial", color: "3D7AB5" },
        paragraph: { spacing: { before: 240, after: 160 }, outlineLevel: 1 } },
      { id: "Heading3", name: "Heading 3", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 22, bold: true, font: "Arial", color: "5A5A5A" },
        paragraph: { spacing: { before: 200, after: 120 }, outlineLevel: 2 } },
    ]
  },
  sections: [{
    properties: {
      page: {
        size: { width: 12240, height: 15840 },
        margin: { top: 1440, right: 1440, bottom: 1440, left: 1440 }
      }
    },
    headers: {
      default: new Header({ children: [new Paragraph({
        alignment: AlignmentType.RIGHT,
        children: [new TextRun({ text: "优品汇 - 系统功能与实现方法讲解", font: "Arial", size: 18, color: "999999" })]
      })] })
    },
    footers: {
      default: new Footer({ children: [new Paragraph({
        alignment: AlignmentType.CENTER,
        children: [new TextRun({ text: "第 ", font: "Arial", size: 18, color: "999999" }),
          new TextRun({ children: [PageNumber.CURRENT], font: "Arial", size: 18, color: "999999" }),
          new TextRun({ text: " 页", font: "Arial", size: 18, color: "999999" })]
      })] })
    },
    children
  }]
});

Packer.toBuffer(doc).then(buffer => {
  fs.writeFileSync("E:\\mimo-worker\\javaweb\\优品汇-系统功能讲解.docx", buffer);
  console.log("Word document generated: 优品汇-系统功能讲解.docx");
});
