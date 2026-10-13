package com.secondhand.mall.service;

import com.secondhand.mall.dto.RegisterRequest;
import com.secondhand.mall.entity.User;
import com.secondhand.mall.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailCodeService emailCodeService;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

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

    @Test
    void testChangePassword_RequiresBoundEmail() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("noemail");
        request.setPassword("password123");
        userService.register(request, "sid-1");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.changePassword("noemail", "password123", "newpass123", "123456", "sid-2"));
        assertTrue(ex.getMessage().contains("绑定邮箱"));
        assertThrows(RuntimeException.class, () -> userService.sendChangePasswordCode("noemail"));
    }

    @Test
    void testChangePassword_RequiresEmailCodeAndCurrentPassword() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("changepwd");
        request.setPassword("password123");
        request.setEmail("change@example.com");
        userService.register(request, "sid-1");

        // 未获取验证码
        assertThrows(RuntimeException.class,
                () -> userService.changePassword("changepwd", "password123", "newpass123", "000000", "sid-2"));

        userService.sendChangePasswordCode("changepwd");
        // 验证码错误
        RuntimeException wrongCode = assertThrows(RuntimeException.class,
                () -> userService.changePassword("changepwd", "password123", "newpass123", "999999", "sid-2"));
        assertTrue(wrongCode.getMessage().contains("验证码"));

        // 当前密码错误
        String code = emailCodeService.peekCode("changepwd", "change@example.com", EmailCodeService.PURPOSE_CHANGE);
        assertNotNull(code);
        RuntimeException badPwd = assertThrows(RuntimeException.class,
                () -> userService.changePassword("changepwd", "wrongpwd", "newpass123", code, "sid-2"));
        assertTrue(badPwd.getMessage().contains("当前密码"));
    }

    @Test
    void testChangePassword_Success_RotatesSession() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("changepwd2");
        request.setPassword("password123");
        request.setEmail("change2@example.com");
        userService.register(request, "sid-old");

        userService.sendChangePasswordCode("changepwd2");
        String code = emailCodeService.peekCode("changepwd2", "change2@example.com", EmailCodeService.PURPOSE_CHANGE);
        assertNotNull(code);

        userService.changePassword("changepwd2", "password123", "newpass123", code, "sid-new");
        User after = userService.findByUsername("changepwd2");
        assertEquals("sid-new", after.getSessionId(), "改密后当前会话应续期，其他会话失效");
        assertTrue(passwordEncoder.matches("newpass123", after.getPassword()));

        // 验证码一次有效：用过的码不能再改密
        assertThrows(RuntimeException.class,
                () -> userService.changePassword("changepwd2", "newpass123", "another456", code, "sid-new"));
    }

    @Test
    void testChangePassword_ResetCodeCannotBeUsed() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("mixcode");
        request.setPassword("password123");
        request.setEmail("mix@example.com");
        userService.register(request, "sid-1");

        // 发的是「找回密码」验证码，不能拿来登录后改密
        emailCodeService.sendCode("mixcode", "mix@example.com", EmailCodeService.PURPOSE_RESET);
        String resetCode = emailCodeService.peekCode("mixcode", "mix@example.com", EmailCodeService.PURPOSE_RESET);
        assertNotNull(resetCode);

        assertThrows(RuntimeException.class,
                () -> userService.changePassword("mixcode", "password123", "newpass123", resetCode, "sid-2"));
    }
}
