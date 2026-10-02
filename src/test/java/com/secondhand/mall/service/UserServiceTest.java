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
    void testUploadAvatar_Success() throws IOException {
        // 创建用户
        RegisterRequest request = new RegisterRequest();
        request.setUsername("testuser");
        request.setPassword("password123");
        request.setNickname("测试用户");
        userService.register(request, "test-session-id");

        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.jpg", "image/jpeg", "test image content".getBytes()
        );

        String avatarUrl = userService.uploadAvatar("testuser", file);

        assertNotNull(avatarUrl);
        assertTrue(avatarUrl.startsWith("/user-avatar/"));
        assertTrue(avatarUrl.endsWith(".jpeg"));
    }
}
