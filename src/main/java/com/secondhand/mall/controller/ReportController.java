package com.secondhand.mall.controller;

import com.secondhand.mall.entity.Product;
import com.secondhand.mall.entity.Report;
import com.secondhand.mall.repository.ProductRepository;
import com.secondhand.mall.repository.ReportRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ReportController extends BaseApiController {

    private final ReportRepository reportRepository;
    private final ProductRepository productRepository;

    public ReportController(ReportRepository reportRepository, ProductRepository productRepository) {
        this.reportRepository = reportRepository;
        this.productRepository = productRepository;
    }

    @PostMapping("/reports")
    public Map<String, Object> create(@RequestBody Map<String, Object> body, HttpSession session) {
        try {
            String username = getLoginUsername(session);
            Long productId = Long.valueOf(String.valueOf(body.get("productId")));
            String reason = body.get("reason") == null ? "其他" : String.valueOf(body.get("reason"));
            String detail = body.get("detail") == null ? "" : String.valueOf(body.get("detail"));

            // 校验：商品存在、长度合法、重复举报防刷
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new RuntimeException("商品不存在"));
            if (reason.length() > 50) {
                return fail("举报原因过长").toMap();
            }
            if (detail.length() > 500) {
                return fail("补充说明不能超过 500 字").toMap();
            }
            if (reportRepository.existsByReporterUsernameAndProductIdAndStatus(username, productId, "pending")) {
                return fail("您已举报过该商品，请等待平台处理").toMap();
            }

            Report r = new Report();
            r.setProductId(productId);
            r.setReporterUsername(username);
            r.setReason(reason);
            r.setDetail(detail);
            // 商品名以服务端为准，防伪造
            r.setProductName(product.getName());
            reportRepository.save(r);
            return ok("举报已提交，平台将尽快处理").toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }
}
