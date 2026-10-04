package com.secondhand.mall.service;

import com.secondhand.mall.constant.OrderStatus;
import com.secondhand.mall.entity.Order;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 物流轨迹（教学演示版）。
 * 真实快递 100/菜鸟接口需企业资质；此处按「快递单号 + 发货时间」生成可演示的完整轨迹时间线，
 * 阶段随真实时间推进，答辩/演示足够。
 */
@Service
public class LogisticsService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public Map<String, Object> track(Order order) {
        Map<String, Object> result = new LinkedHashMap<>();
        String tracking = order.getTrackingNumber();
        if (tracking == null || tracking.isBlank()) {
            result.put("success", true);
            result.put("trackingNumber", "");
            result.put("company", "");
            result.put("status", "none");
            result.put("statusText", "暂无物流信息");
            result.put("tracks", List.of());
            return result;
        }

        LocalDateTime shippedAt = order.getShippedAt() != null ? order.getShippedAt() : order.getCreatedAt();
        if (shippedAt == null) shippedAt = LocalDateTime.now();

        String company = guessCompany(tracking);
        String status = resolveStatus(order.getStatus(), shippedAt);
        String statusText = statusText(status);

        List<Map<String, Object>> tracks = buildTimeline(tracking, company, shippedAt, order.getStatus());

        result.put("success", true);
        result.put("trackingNumber", tracking);
        result.put("company", company);
        result.put("status", status);
        result.put("statusText", statusText);
        result.put("tracks", tracks);
        return result;
    }

    /** 按发货后经过小时数推进轨迹阶段 */
    private List<Map<String, Object>> buildTimeline(String tracking, String company,
                                                    LocalDateTime shippedAt, String orderStatus) {
        long hours = java.time.Duration.between(shippedAt, LocalDateTime.now()).toHours();
        List<Map<String, Object>> tracks = new ArrayList<>();

        // 0h 已揽收
        tracks.add(event(shippedAt, company, "快件已揽收，快递员[" + courierName(tracking) + "]已取件"));
        // 6h 离开始发地
        if (hours >= 6 || isFinished(orderStatus)) {
            tracks.add(event(shippedAt.plusHours(6), company,
                    "快件已离开【" + cityA(tracking) + "转运中心】，发往【" + cityB(tracking) + "】"));
        }
        // 18h 到达目的地
        if (hours >= 18 || isFinished(orderStatus)) {
            tracks.add(event(shippedAt.plusHours(18), company,
                    "快件已到达【" + cityB(tracking) + "转运中心】"));
        }
        // 24h 派送中
        if (hours >= 24 || isFinished(orderStatus)) {
            tracks.add(event(shippedAt.plusHours(24), company,
                    "快件已到达【" + cityB(tracking) + "】，快递员[" + courierName(tracking) + "]正在派送"));
        }
        // 36h 或已收货/完成 → 签收
        if (hours >= 36 || OrderStatus.RECEIVED.equals(orderStatus)
                || OrderStatus.COMPLETED.equals(orderStatus)) {
            tracks.add(event(shippedAt.plusHours(36), company, "快件已签收，签收人：本人"));
        }

        // 新事件在前（与常见快递查询一致）
        List<Map<String, Object>> reversed = new ArrayList<>();
        for (int i = tracks.size() - 1; i >= 0; i--) {
            reversed.add(tracks.get(i));
        }
        return reversed;
    }

    private Map<String, Object> event(LocalDateTime time, String company, String desc) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("time", time.format(FMT));
        e.put("company", company);
        e.put("desc", desc);
        return e;
    }

    private boolean isFinished(String orderStatus) {
        return OrderStatus.RECEIVED.equals(orderStatus)
                || OrderStatus.COMPLETED.equals(orderStatus);
    }

    private String resolveStatus(String orderStatus, LocalDateTime shippedAt) {
        if (OrderStatus.RECEIVED.equals(orderStatus) || OrderStatus.COMPLETED.equals(orderStatus)) {
            return "signed";
        }
        if (!OrderStatus.SHIPPED.equals(orderStatus) && !OrderStatus.AFTER_SALE.equals(orderStatus)) {
            return "none";
        }
        long hours = java.time.Duration.between(shippedAt, LocalDateTime.now()).toHours();
        if (hours >= 36) return "signed";
        if (hours >= 24) return "delivering";
        if (hours >= 6) return "transporting";
        return "picked";
    }

    private String statusText(String status) {
        return switch (status) {
            case "picked" -> "已揽收";
            case "transporting" -> "运输中";
            case "delivering" -> "派送中";
            case "signed" -> "已签收";
            default -> "暂无物流信息";
        };
    }

    /** 由单号确定性生成演示数据，同一单号结果稳定 */
    private String guessCompany(String tracking) {
        if (tracking.startsWith("SF")) return "顺丰速运";
        if (tracking.startsWith("JD")) return "京东物流";
        if (tracking.startsWith("YT")) return "圆通速递";
        if (tracking.startsWith("ZT")) return "中通快递";
        if (tracking.startsWith("ST")) return "申通快递";
        if (tracking.startsWith("YD")) return "韵达速递";
        return "优品汇合作快递";
    }

    private String courierName(String tracking) {
        String[] names = {"王磊", "李强", "张伟", "刘洋", "陈杰", "赵鹏"};
        return names[Math.floorMod(tracking.hashCode(), names.length)];
    }

    private String cityA(String tracking) {
        String[] cities = {"杭州", "上海", "广州", "深圳", "成都", "武汉", "南京"};
        return cities[Math.floorMod(tracking.hashCode(), cities.length)];
    }

    private String cityB(String tracking) {
        String[] cities = {"北京", "重庆", "西安", "长沙", "苏州", "青岛", "厦门"};
        return cities[Math.floorMod(tracking.hashCode() * 31, cities.length)];
    }
}
