package com.secondhand.mall.controller;

import com.secondhand.mall.entity.Offer;
import com.secondhand.mall.service.OfferService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 议价：买家出价 / 卖家接受拒绝 */
@RestController
@RequestMapping("/api/offers")
public class OfferController {

    private final OfferService offerService;

    public OfferController(OfferService offerService) {
        this.offerService = offerService;
    }

    private String requireUser(HttpSession session) {
        Object u = session.getAttribute("username");
        if (u == null) throw new RuntimeException("请先登录");
        return u.toString();
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body, HttpSession session) {
        try {
            String username = requireUser(session);
            Long productId = Long.valueOf(String.valueOf(body.get("productId")));
            BigDecimal price = new BigDecimal(String.valueOf(body.get("offerPrice")));
            String message = body.get("message") != null ? String.valueOf(body.get("message")) : null;
            Offer offer = offerService.createOffer(username, productId, price, message);
            return Map.of("success", true, "message", "出价已发送，等待卖家回应", "offer", offer);
        } catch (RuntimeException e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    @GetMapping("/mine")
    public Map<String, Object> mine(HttpSession session) {
        try {
            String username = requireUser(session);
            List<Offer> offers = offerService.listBuyerOffers(username);
            return Map.of("success", true, "offers", offers);
        } catch (RuntimeException e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    @GetMapping("/received")
    public Map<String, Object> received(HttpSession session) {
        try {
            String username = requireUser(session);
            List<Offer> offers = offerService.listSellerOffers(username);
            return Map.of("success", true, "offers", offers);
        } catch (RuntimeException e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    @PostMapping("/{id}/accept")
    public Map<String, Object> accept(@PathVariable Long id, HttpSession session) {
        try {
            String username = requireUser(session);
            Offer offer = offerService.acceptOffer(id, username);
            return Map.of("success", true, "message", "已接受出价，订单已生成，请等待买家付款",
                    "offer", offer, "orderId", offer.getOrderId() != null ? offer.getOrderId() : 0L);
        } catch (RuntimeException e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    @PostMapping("/{id}/reject")
    public Map<String, Object> reject(@PathVariable Long id, HttpSession session) {
        try {
            String username = requireUser(session);
            offerService.rejectOffer(id, username);
            return Map.of("success", true, "message", "已拒绝出价");
        } catch (RuntimeException e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    @PostMapping("/{id}/cancel")
    public Map<String, Object> cancel(@PathVariable Long id, HttpSession session) {
        try {
            String username = requireUser(session);
            offerService.cancelOffer(id, username);
            return Map.of("success", true, "message", "已撤销出价");
        } catch (RuntimeException e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }
}
