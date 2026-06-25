package com.secondhand.mall.service;

import com.secondhand.mall.dto.LoginRequest;
import com.secondhand.mall.dto.RegisterRequest;
import com.secondhand.mall.entity.User;
import com.secondhand.mall.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, BCryptPasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("用户名已存在");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname() != null ? request.getNickname() : request.getUsername());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setRole("user");
        user.setBalance(new BigDecimal("10000.00"));
        user.setAvatar("/user-avatar/default.jpg");

        return userRepository.save(user);
    }

    public User login(LoginRequest request, String sessionId) {
        Optional<User> userOpt = userRepository.findByUsername(request.getUsername());
        if (userOpt.isEmpty()) {
            throw new RuntimeException("用户不存在");
        }

        User user = userOpt.get();
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("密码错误");
        }

        user.setSessionId(sessionId);
        userRepository.save(user);
        return user;
    }

    public boolean validateSession(String username, String sessionId) {
        if (username == null || sessionId == null) return false;
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) return false;
        String storedSessionId = userOpt.get().getSessionId();
        return sessionId.equals(storedSessionId);
    }

    public void setSessionId(String username, String sessionId) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.setSessionId(sessionId);
            userRepository.save(user);
        });
    }

    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
    }

    public User updateUser(String username, String nickname, String email, String phone,
                           String currentPassword, String newPassword, String avatar) {
        User user = findByUsername(username);

        if (nickname != null) user.setNickname(nickname);
        if (email != null) user.setEmail(email);
        if (phone != null) user.setPhone(phone);
        if (avatar != null) user.setAvatar(avatar);

        // 修改密码需验证当前密码
        if (newPassword != null && !newPassword.isEmpty()) {
            if (currentPassword == null || currentPassword.isEmpty()) {
                throw new RuntimeException("请输入当前密码");
            }
            if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
                throw new RuntimeException("当前密码错误");
            }
            if (newPassword.length() < 6) {
                throw new RuntimeException("新密码至少6位");
            }
            user.setPassword(passwordEncoder.encode(newPassword));
        }

        return userRepository.save(user);
    }

    public User updateBalance(String username, BigDecimal newBalance) {
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("余额不能为负数");
        }
        User user = findByUsername(username);
        user.setBalance(newBalance);
        return userRepository.save(user);
    }
}
