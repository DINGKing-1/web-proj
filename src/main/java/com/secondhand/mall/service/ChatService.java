package com.secondhand.mall.service;

import com.secondhand.mall.entity.ChatMessage;
import com.secondhand.mall.entity.ChatRoom;
import com.secondhand.mall.entity.Product;
import com.secondhand.mall.entity.User;
import com.secondhand.mall.repository.ChatMessageRepository;
import com.secondhand.mall.repository.ChatRoomRepository;
import com.secondhand.mall.repository.ProductRepository;
import com.secondhand.mall.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private static final Set<String> VALID_MESSAGE_TYPES = Set.of("text", "image");
    private static final int MAX_MESSAGE_LENGTH = 5000;

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public ChatService(ChatRoomRepository chatRoomRepository, ChatMessageRepository chatMessageRepository,
                       ProductRepository productRepository, UserRepository userRepository) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    public ChatRoom findRoom(String buyer, String seller, Long productId) {
        return chatRoomRepository.findByUsersAndProduct(buyer, seller, productId).orElse(null);
    }

    public ChatRoom getOrCreateRoom(String buyer, String seller, Long productId) {
        Optional<ChatRoom> existing = chatRoomRepository.findByUsers(buyer, seller);
        if (existing.isPresent()) {
            return existing.get();
        }

        ChatRoom room = new ChatRoom();
        room.setUser1Username(buyer);
        room.setUser2Username(seller);
        room.setProductId(productId);
        room.setLastMessage("");
        return chatRoomRepository.save(room);
    }

    public ChatMessage sendMessage(Long roomId, String sender, String content, String type, Long productId) {
        // 校验消息内容
        if (content == null || content.trim().isEmpty()) {
            throw new RuntimeException("消息内容不能为空");
        }
        if (content.length() > MAX_MESSAGE_LENGTH) {
            throw new RuntimeException("消息内容不能超过 " + MAX_MESSAGE_LENGTH + " 个字符");
        }

        // 校验消息类型
        String messageType = (type != null && VALID_MESSAGE_TYPES.contains(type)) ? type : "text";

        // 校验聊天室存在且发送者是成员
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("聊天室不存在"));
        verifyRoomMember(room, sender);

        ChatMessage msg = new ChatMessage();
        msg.setRoomId(roomId);
        msg.setSenderUsername(sender);
        msg.setContent(content.trim());
        msg.setMessageType(messageType);
        if (productId != null) {
            msg.setProductId(productId);
        } else if (room.getProductId() != null) {
            msg.setProductId(room.getProductId());
        }
        ChatMessage saved = chatMessageRepository.save(msg);

        room.setLastMessage("image".equals(messageType) ? "[图片]" : content.trim());
        room.setLastMessageTime(saved.getCreatedAt());

        if (room.getUser1Username().equals(sender)) {
            room.setUser2Unread(room.getUser2Unread() + 1);
        } else {
            room.setUser1Unread(room.getUser1Unread() + 1);
        }
        chatRoomRepository.save(room);

        return saved;
    }

    public List<ChatMessage> getMessages(Long roomId, String username, Long sinceId) {
        // 校验聊天室存在且用户是成员
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("聊天室不存在"));
        verifyRoomMember(room, username);

        if (sinceId != null && sinceId > 0) {
            return chatMessageRepository.findByRoomIdAndIdGreaterThanOrderByIdAsc(roomId, sinceId);
        }
        return chatMessageRepository.findByRoomIdOrderByCreatedAtAsc(roomId);
    }

    public List<Map<String, Object>> getRoomList(String username) {
        List<ChatRoom> rooms = chatRoomRepository.findActiveByUsername(username);

        // 批量查询关联的用户和商品，避免 N+1
        Set<String> usernames = new HashSet<>();
        Set<Long> productIds = new HashSet<>();
        for (ChatRoom room : rooms) {
            String other = room.getUser1Username().equals(username)
                    ? room.getUser2Username() : room.getUser1Username();
            usernames.add(other);
            if (room.getProductId() != null) {
                productIds.add(room.getProductId());
            }
        }

        Map<String, User> userMap = userRepository.findByUsernameIn(usernames).stream()
                .collect(Collectors.toMap(User::getUsername, u -> u));
        Map<Long, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        List<Map<String, Object>> result = new ArrayList<>();
        for (ChatRoom room : rooms) {
            String otherUsername = room.getUser1Username().equals(username)
                    ? room.getUser2Username() : room.getUser1Username();

            int unread = room.getUser1Username().equals(username)
                    ? room.getUser1Unread() : room.getUser2Unread();

            User other = userMap.get(otherUsername);
            Product product = room.getProductId() != null ? productMap.get(room.getProductId()) : null;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("roomId", room.getId());
            item.put("otherUsername", otherUsername);
            item.put("otherNickname", other != null ? (other.getNickname() != null ? other.getNickname() : otherUsername) : otherUsername);
            item.put("otherAvatar", other != null ? other.getAvatar() : "/user-avatar/default.jpg");
            item.put("otherLastActiveTime", other != null ? other.getLastActiveTime() : null);
            item.put("productId", room.getProductId());
            item.put("productName", product != null ? product.getName() : "已删除的商品");
            item.put("productImage", product != null ? getFirstImage(product.getImageUrl()) : "");
            item.put("lastMessage", room.getLastMessage() != null ? room.getLastMessage() : "");
            item.put("lastMessageTime", room.getLastMessageTime());
            item.put("unread", unread);
            result.add(item);
        }

        return result;
    }

    public void markAsRead(Long roomId, String username) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("聊天室不存在"));
        verifyRoomMember(room, username);

        if (room.getUser1Username().equals(username)) {
            room.setUser1Unread(0);
        } else {
            room.setUser2Unread(0);
        }
        chatRoomRepository.save(room);
    }

    public int getUnreadCount(String username) {
        List<ChatRoom> rooms = chatRoomRepository.findByUsername(username);
        int total = 0;
        for (ChatRoom room : rooms) {
            if (room.getUser1Username().equals(username)) {
                total += room.getUser1Unread();
            } else {
                total += room.getUser2Unread();
            }
        }
        return total;
    }

    public void deleteRoom(Long roomId, String username) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("聊天室不存在"));
        verifyRoomMember(room, username);
        room.setDeleted(true);
        chatRoomRepository.save(room);
    }

    /**
     * 校验用户是否是聊天室成员
     */
    private void verifyRoomMember(ChatRoom room, String username) {
        if (!room.getUser1Username().equals(username) && !room.getUser2Username().equals(username)) {
            throw new RuntimeException("您不是该聊天室的成员");
        }
    }

    private String getFirstImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) return "";
        if (imageUrl.contains(",")) return imageUrl.split(",")[0].trim();
        return imageUrl;
    }
}
