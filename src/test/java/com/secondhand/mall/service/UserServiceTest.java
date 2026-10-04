package com.secondhand.mall.service;

import com.secondhand.mall.dto.RegisterRequest;
import com.secondhand.mall.entity.User;
import com.secondhand.mall.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void testRegister_ShouldSetSessionId() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("testuser");
        request.setPassword("password123");
        request.setNickname("测试用户");

        String sessionId = "test-session-id";
        User user = userService.register(request, sessionId);

        assertNotNull(user);
        assertEquals(sessionId, user.getSessionId());
        assertEquals("testuser", user.getUsername());
    }

    @Test
    void testRecharge_RespectsCumulativeCap() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("richuser");
        request.setPassword("password123");
        userService.register(request, "sid");

        // 单笔 5 万上限
        assertThrows(RuntimeException.class,
                () -> userService.recharge("richuser", new java.math.BigDecimal("50001")));

        // 累计上限 20 万：分次充到接近上限后应拒绝
        for (int i = 0; i < 4; i++) {
            userService.recharge("richuser", new java.math.BigDecimal("50000"));
        }
        assertThrows(RuntimeException.class,
                () -> userService.recharge("richuser", new java.math.BigDecimal("1")),
                "累计模拟充值超过上限应被拒绝");
    }

    @Test
    void testUploadAvatar_Success() throws IOException {
        // 创建用户
        RegisterRequest request = new RegisterRequest();
        request.setUsername("testuser");
        request.setPassword("password123");
        request.setNickname("测试用户");
        userService.register(request, "test-session-id");

        // 真实 JPEG 魔数（FF D8 FF），扩展名按魔数识别
        byte[] jpegBytes = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.jpg", "image/jpeg", jpegBytes
        );

        String avatarUrl = userService.uploadAvatar("testuser", file);

        assertNotNull(avatarUrl);
        assertTrue(avatarUrl.startsWith("/user-avatar/"));
        assertTrue(avatarUrl.endsWith(".jpg"));
    }

    @Test
    void testUploadAvatar_RejectFakeImage() throws IOException {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("testuser2");
        request.setPassword("password123");
        userService.register(request, "test-session-id");

        // 伪造 Content-Type 的非图片内容应被拒绝
        MockMultipartFile fake = new MockMultipartFile(
                "file", "evil.svg", "image/svg+xml", "<svg onload=alert(1)/>".getBytes()
        );
        assertThrows(RuntimeException.class, () -> userService.uploadAvatar("testuser2", fake));
    }
}
