package com.secondhand.mall.controller;

import com.secondhand.mall.entity.ChatMessage;
import com.secondhand.mall.entity.ChatRoom;
import com.secondhand.mall.entity.Product;
import com.secondhand.mall.service.ChatService;
import com.secondhand.mall.service.ProductService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatApiController extends BaseApiController {

    private final ChatService chatService;
    private final ProductService productService;

    public ChatApiController(ChatService chatService, ProductService productService) {
        this.chatService = chatService;
        this.productService = productService;
    }

    @PostMapping("/room")
    public Map<String, Object> getOrCreateRoom(@RequestBody Map<String, Object> request, HttpSession session) {
        try {
            String username = getLoginUsername(session);
            Long productId = Long.parseLong(request.get("productId").toString());

            Product product = productService.getProductById(productId)
                    .orElseThrow(() -> new RuntimeException("商品不存在"));

            String seller = product.getSellerUsername();
            if (username.equals(seller)) {
                return fail("不能和自己聊天").toMap();
            }

            // 只查找已存在的聊天室，不创建新的
            ChatRoom room = chatService.findRoom(username, seller, productId);
            if (room != null) {
                return ok("获取成功")
                        .put("roomId", room.getId())
                        .put("sellerNickname", seller)
                        .put("exists", true)
                        .toMap();
            }

            // 聊天室不存在，返回卖家信息供前端使用
            return ok("获取成功")
                    .put("roomId", null)
                    .put("sellerUsername", seller)
                    .put("sellerNickname", product.getSellerName())
                    .put("sellerAvatar", product.getSellerAvatar())
                    .put("exists", false)
                    .toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    @PostMapping("/room/create")
    public Map<String, Object> createRoom(@RequestBody Map<String, Object> request, HttpSession session) {
        try {
            String username = getLoginUsername(session);
            Object productIdRaw = request.get("productId");
            Long productId = null;
            if (productIdRaw != null && !String.valueOf(productIdRaw).isBlank()) {
                productId = Long.parseLong(String.valueOf(productIdRaw));
            }

            String seller;
            if (productId != null) {
                // 卖家以商品归属为准，不信任请求体（防对任意用户建会话骚扰）
                Product product = productService.getProductById(productId)
                        .orElseThrow(() -> new RuntimeException("商品不存在"));
                seller = product.getSellerUsername();
            } else {
                // 店铺页「联系卖家」：无商品上下文，直接按卖家建会话
                String sellerUsername = (String) request.get("sellerUsername");
                if (sellerUsername == null || sellerUsername.isBlank()) {
                    return fail("缺少商品或卖家信息").toMap();
                }
                seller = sellerUsername.trim();
            }

            if (username.equals(seller)) {
                return fail("不能和自己聊天").toMap();
            }

            ChatRoom room = chatService.getOrCreateRoom(username, seller, productId);
            return ok("创建成功")
                    .put("roomId", room.getId())
                    .toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    @GetMapping("/rooms")
    public Map<String, Object> getRoomList(HttpSession session) {
        try {
            String username = getLoginUsername(session);
            List<Map<String, Object>> rooms = chatService.getRoomList(username);
            return ok("获取成功").put("rooms", rooms).put("total", rooms.size()).toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    @PostMapping("/message")
    public Map<String, Object> sendMessage(@RequestBody Map<String, Object> request, HttpSession session) {
        try {
            String username = getLoginUsername(session);
            Long roomId = Long.parseLong(request.get("roomId").toString());
            String content = (String) request.get("content");
            String type = (String) request.get("type");

            // 传递 productId 以记录消息的商品上下文
            Long productId = request.get("productId") != null
                    ? Long.parseLong(request.get("productId").toString()) : null;

            ChatMessage msg = chatService.sendMessage(roomId, username, content, type, productId);

            return ok("发送成功")
                    .put("messageId", msg.getId())
                    .put("createdAt", msg.getCreatedAt())
                    .toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    @GetMapping("/messages/{roomId}")
    public Map<String, Object> getMessages(@PathVariable Long roomId,
                                           @RequestParam(required = false) Long sinceId,
                                           HttpSession session) {
        try {
            String username = getLoginUsername(session);
            // getMessages 内部已校验用户是否是聊天室成员
            List<ChatMessage> messages = chatService.getMessages(roomId, username, sinceId);
            chatService.markAsRead(roomId, username);

            return ok("获取成功").put("messages", messages).put("total", messages.size()).toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    @DeleteMapping("/room/{roomId}")
    public Map<String, Object> deleteRoom(@PathVariable Long roomId, HttpSession session) {
        try {
            String username = getLoginUsername(session);
            chatService.deleteRoom(roomId, username);
            return ok("聊天室已删除").toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    @GetMapping("/unread")
    public Map<String, Object> getUnreadCount(HttpSession session) {
        try {
            String username = getLoginUsername(session);
            int count = chatService.getUnreadCount(username);
            return ok("获取成功").put("unread", count).toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }
}
