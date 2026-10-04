package com.secondhand.mall.controller;

import com.secondhand.mall.exception.ForbiddenException;

import com.secondhand.mall.entity.User;
import com.secondhand.mall.repository.OrderRepository;
import com.secondhand.mall.repository.UserRepository;
import com.secondhand.mall.repository.WalletTransactionRepository;
import com.secondhand.mall.service.AdminService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据导出（CSV）：订单 / 用户 / 资金流水? * ?CSV 而非 Excel 是为了不引入 POI 依赖；带 UTF-8 BOM，Excel 打开中文不乱码? */
@RestController
@RequestMapping("/api/admin/export")
public class ExportController extends BaseApiController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AdminService adminService;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    public ExportController(AdminService adminService, OrderRepository orderRepository,
                            UserRepository userRepository,
                            WalletTransactionRepository walletTransactionRepository) {
        this.adminService = adminService;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.walletTransactionRepository = walletTransactionRepository;
    }

    private void checkAdmin(HttpSession session) {
        String username = (String) session.getAttribute("username");
        if (username == null || !adminService.isAdmin(username)) {
            throw new com.secondhand.mall.exception.ForbiddenException("无管理员权限");
        }
    }

    /** 订单导出（可按状态过滤） */
    @GetMapping("/orders")
    public ResponseEntity<byte[]> exportOrders(
            @RequestParam(required = false) String status, HttpSession session) {
        checkAdmin(session);
        List<String> header = List.of("订单号", "商品", "买家", "卖家", "单价", "数量",
                "金额", "状态", "支付方式", "快递单号", "创建时间");
        List<List<String>> rows = new ArrayList<>();
        var all = orderRepository.findAllByOrderByCreatedAtDesc();
        boolean filter = status != null && !status.isBlank() && !"all".equals(status);
        var orders = filter ? all.stream().filter(o -> status.equals(o.getStatus())).toList() : all;
        for (var o : orders) {
            int qty = o.getQuantity() != null && o.getQuantity() > 0 ? o.getQuantity() : 1;
            BigDecimal price = o.getProductPrice() != null ? o.getProductPrice() : BigDecimal.ZERO;
            rows.add(List.of(
                    String.valueOf(o.getOrderNumber()),
                    o.getProductName(),
                    o.getUsername(),
                    o.getSellerUsername() == null ? "" : o.getSellerUsername(),
                    price.toPlainString(),
                    String.valueOf(qty),
                    price.multiply(BigDecimal.valueOf(qty)).toPlainString(),
                    o.getStatus(),
                    o.getPaymentMethod() == null ? "" : o.getPaymentMethod(),
                    o.getTrackingNumber() == null ? "" : o.getTrackingNumber(),
                    o.getCreatedAt() == null ? "" : o.getCreatedAt().format(FMT)));
        }
        return csv("orders-" + nowStamp() + ".csv", header, rows);
    }

    /** 用户导出（不含密码等敏感字段?*/
    @GetMapping("/users")
    public ResponseEntity<byte[]> exportUsers(HttpSession session) {
        checkAdmin(session);
        List<String> header = List.of("用户名", "昵称", "邮箱", "手机", "余额", "角色",
                "封禁", "已注销", "注册时间", "最近活");
        List<List<String>> rows = new ArrayList<>();
        for (User u : userRepository.findAll()) {
            rows.add(List.of(
                    u.getUsername(),
                    u.getNickname() == null ? "" : u.getNickname(),
                    u.getEmail() == null ? "" : u.getEmail(),
                    u.getPhone() == null ? "" : u.getPhone(),
                    u.getBalance() != null ? u.getBalance().toPlainString() : "0",
                    u.getRole() == null ? "user" : u.getRole(),
                    Boolean.TRUE.equals(u.getBanned()) ? "是" : "否",
                    Boolean.TRUE.equals(u.getDeleted()) ? "是" : "否",
                    u.getCreatedAt() == null ? "" : u.getCreatedAt().format(FMT),
                    u.getLastActiveTime() == null ? "" : u.getLastActiveTime().format(FMT)));
        }
        return csv("users-" + nowStamp() + ".csv", header, rows);
    }

    /** 资金流水导出（对账用） */
    @GetMapping("/wallet")
    public ResponseEntity<byte[]> exportWallet(HttpSession session) {
        checkAdmin(session);
        List<String> header = List.of("用户名", "类型", "说明", "金额", "状态", "时间");
        List<List<String>> rows = new ArrayList<>();
        for (var tx : walletTransactionRepository.findAll()) {
            rows.add(List.of(
                    tx.getUsername(),
                    tx.getType() == null ? "" : tx.getType(),
                    tx.getTitle() == null ? "" : tx.getTitle(),
                    tx.getAmount() != null ? tx.getAmount().toPlainString() : "0",
                    tx.getStatus() == null ? "" : tx.getStatus(),
                    tx.getCreatedAt() == null ? "" : String.valueOf(tx.getCreatedAt())));
        }
        return csv("wallet-" + nowStamp() + ".csv", header, rows);
    }

    private ResponseEntity<byte[]> csv(String filename, List<String> header, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        // UTF-8 BOM：Excel 直接双击打开时中文不乱码
        sb.append("");
        sb.append(line(header));
        for (List<String> row : rows) {
            sb.append(line(row));
        }
        byte[] body = sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .contentLength(body.length)
                .body(body);
    }

    private String line(List<String> cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(escape(cells.get(i)));
        }
        return sb.append('\r').append('\n').toString();
    }

    /** CSV 转义：含逗号/引号/换行的字段用双引号包起来 */
    private String escape(String v) {
        if (v == null) return "";
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private String nowStamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }
}

