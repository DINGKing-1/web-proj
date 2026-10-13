package com.secondhand.mall.service;

import com.secondhand.mall.dto.LoginRequest;
import com.secondhand.mall.dto.RegisterRequest;
import com.secondhand.mall.entity.User;
import com.secondhand.mall.repository.UserRepository;
import com.secondhand.mall.storage.StorageCategory;
import com.secondhand.mall.storage.StorageService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserService {

    /** 登录/密码重置失败防护：key -> [失败次数, 窗口起始毫秒, 解锁时间毫秒] */
    private final Map<String, long[]> failureGuards = new ConcurrentHashMap<>();
    private static final int MAX_FAILURES = 5;
    private static final long LOCK_WINDOW_MS = 15 * 60 * 1000L;

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AdminService adminService;
    private final StorageService storageService;
    private final EmailCodeService emailCodeService;
    private final com.secondhand.mall.config.MallProperties mallProperties;
    private final WalletService walletService;

    public UserService(UserRepository userRepository, BCryptPasswordEncoder passwordEncoder,
                       AdminService adminService, StorageService storageService,
                       EmailCodeService emailCodeService,
                       com.secondhand.mall.config.MallProperties mallProperties,
                       WalletService walletService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminService = adminService;
        this.storageService = storageService;
        this.emailCodeService = emailCodeService;
        this.mallProperties = mallProperties;
        this.walletService = walletService;
    }

    public User register(RegisterRequest request, String sessionId) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("用户名已存在");
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            String em = request.getEmail().trim();
            if (!em.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                throw new RuntimeException("邮箱格式不正确");
            }
            if (userRepository.existsByEmailIgnoreCase(em)) {
                throw new RuntimeException("该邮箱已被其他账号绑定");
            }
            request.setEmail(em);
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname() != null ? request.getNickname() : request.getUsername());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setRole("user");
        user.setBanned(false);
        // 初始余额可配置：演示给启动资金，生产应为 0
        java.math.BigDecimal initial = mallProperties.getUser().getInitialBalance();
        user.setBalance(initial != null && initial.compareTo(java.math.BigDecimal.ZERO) >= 0
                ? initial : java.math.BigDecimal.ZERO);
        user.setAvatar("/user-avatar/default.jpg");
        user.setSessionId(sessionId);

        return userRepository.save(user);
    }

    public User login(LoginRequest request, String sessionId) {
        String username = request.getUsername();
        // 防暴力破解：失败次数超限后临时锁定
        checkNotLocked("login:" + username, "登录失败次数过多，请 15 分钟后再试");

        Optional<User> userOpt = userRepository.findByUsername(username);
        // 统一错误文案，防用户枚举
        String genericError = "用户名或密码错误";
        if (userOpt.isEmpty()) {
            recordFailure("login:" + username);
            throw new RuntimeException(genericError);
        }

        User user = userOpt.get();
        if (Boolean.TRUE.equals(user.getDeleted())) {
            recordFailure("login:" + username);
            throw new RuntimeException(genericError);
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            recordFailure("login:" + username);
            throw new RuntimeException(genericError);
        }
        if (Boolean.TRUE.equals(user.getBanned())) {
            throw new RuntimeException("账号已被封禁，请联系平台客服");
        }

        clearFailures("login:" + username);
        user.setSessionId(sessionId);
        userRepository.save(user);
        return user;
    }

    /** 校验失败保护：未锁定则放行 */
    private void checkNotLocked(String key, String message) {
        long[] guard = failureGuards.get(key);
        if (guard != null && System.currentTimeMillis() < guard[2]) {
            throw new RuntimeException(message);
        }
    }

    private void recordFailure(String key) {
        long now = System.currentTimeMillis();
        failureGuards.compute(key, (k, guard) -> {
            if (guard == null || now - guard[1] > LOCK_WINDOW_MS) {
                return new long[]{1, now, 0};
            }
            guard[0]++;
            if (guard[0] >= MAX_FAILURES) {
                guard[2] = now + LOCK_WINDOW_MS;
            }
            return guard;
        });
    }

    private void clearFailures(String key) {
        failureGuards.remove(key);
    }

    public boolean validateSession(String username, String sessionId) {
        if (username == null || sessionId == null) return false;
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) return false;
        User user = userOpt.get();
        if (Boolean.TRUE.equals(user.getDeleted())) return false;
        String storedSessionId = user.getSessionId();
        return sessionId.equals(storedSessionId);
    }

    public void setSessionId(String username, String sessionId) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.setSessionId(sessionId);
            userRepository.save(user);
        });
    }

    @org.springframework.transaction.annotation.Transactional
    public void updateLastActiveTime(String username) {
        userRepository.touchLastActive(username, LocalDateTime.now());
    }

    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
    }

    public User updateUser(String username, String nickname, String email, String phone, String avatar) {
        User user = findByUsername(username);

        if (nickname != null) user.setNickname(nickname);
        if (email != null) {
            String em = email.trim();
            if (!em.isEmpty()) {
                // 格式 + 唯一：防止绑成他人邮箱去收验证码/干扰找回密码
                if (!em.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                    throw new RuntimeException("邮箱格式不正确");
                }
                var owner = userRepository.findByEmailIgnoreCase(em)
                        .orElse(null);
                if (owner != null && !owner.getUsername().equals(username)) {
                    throw new RuntimeException("该邮箱已被其他账号绑定");
                }
            }
            user.setEmail(em.isEmpty() ? null : em);
        }
        if (phone != null) user.setPhone(phone);
        // 头像仅允许服务端生成的相对路径，防任意字符串/外链注入
        if (avatar != null && !avatar.isEmpty()) {
            if (!avatar.startsWith("/user-avatar/") || avatar.contains("..") || avatar.contains("\\")) {
                throw new RuntimeException("非法的头像地址");
            }
            user.setAvatar(avatar);
        }

        return userRepository.save(user);
    }

    @org.springframework.transaction.annotation.Transactional
    public User updateBalance(String username, BigDecimal newBalance) {
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("余额不能为负数");
        }
        User user = userRepository.findByUsernameForUpdate(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        user.setBalance(newBalance);
        return userRepository.save(user);
    }

    /** 余额充值（模拟支付）。生产必须关闭 mock 开关，改走真实支付回调。 */
    @org.springframework.transaction.annotation.Transactional
    public User recharge(String username, BigDecimal amount) {
        var wallet = mallProperties.getWallet();
        if (!wallet.isMockRechargeEnabled()) {
            throw new RuntimeException("充值通道已关闭，请联系管理员");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("充值金额必须大于 0");
        }
        BigDecimal maxSingle = wallet.getMockRechargeMaxSingle();
        if (maxSingle != null && amount.compareTo(maxSingle) > 0) {
            throw new RuntimeException("单次充值不能超过 ¥" + maxSingle.toPlainString());
        }
        User user = userRepository.findByUsernameForUpdate(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        // 累计充值上限：防止脚本反复调用刷余额
        BigDecimal maxCumulative = wallet.getMockRechargeMaxCumulative();
        if (maxCumulative != null) {
            BigDecimal recharged = walletService.sumRechargeAmount(username);
            if (recharged.add(amount).compareTo(maxCumulative) > 0) {
                throw new RuntimeException("累计模拟充值已达上限 ¥" + maxCumulative.toPlainString());
            }
        }
        user.setBalance(user.getBalance().add(amount));
        User saved = userRepository.save(user);
        // 余额与流水同事务落账，避免只加余额无流水
        walletService.addRecharge(username, amount.toPlainString());
        return saved;
    }

    /** 发送找回密码验证码到注册邮箱 */
    public void sendResetCode(String username, String email) {
        if (username == null || username.trim().isEmpty()) {
            throw new RuntimeException("请填写用户名");
        }
        if (email == null || email.trim().isEmpty()) {
            throw new RuntimeException("请填写邮箱");
        }
        String genericError = "用户名与邮箱不匹配";
        User user = userRepository.findByUsername(username.trim()).orElse(null);
        if (user == null || user.getEmail() == null
                || !email.trim().equalsIgnoreCase(user.getEmail())) {
            throw new RuntimeException(genericError);
        }
        if (user.getEmail().isBlank()) {
            throw new RuntimeException("该账号未绑定邮箱，请联系管理员");
        }
        emailCodeService.sendResetCode(user.getUsername(), user.getEmail());
    }

    /**
     * 忘记密码：邮箱验证码校验后重置（限流 + 重置后吊销旧会话）。
     * 比「用户名+邮箱即可改密」更安全：必须持有邮箱验证码。
     */
    public void resetPassword(String username, String email, String code, String newPassword) {
        if (newPassword == null || newPassword.length() < 6) {
            throw new RuntimeException("新密码至少 6 位");
        }
        if (username == null || username.trim().isEmpty()) {
            throw new RuntimeException("请填写用户名");
        }
        // 防暴力枚举/爆破：同账号 15 分钟内最多 5 次尝试
        String guardKey = "reset:" + username.trim();
        checkNotLocked(guardKey, "操作过于频繁，请 15 分钟后再试");

        String genericError = "用户名与邮箱不匹配";
        User user = userRepository.findByUsername(username.trim()).orElse(null);
        if (user == null || email == null || email.trim().isEmpty()
                || user.getEmail() == null
                || !email.trim().equalsIgnoreCase(user.getEmail())) {
            recordFailure(guardKey);
            throw new RuntimeException(genericError);
        }

        // 验证码校验（通过即作废）
        emailCodeService.verifyResetCode(user.getUsername(), user.getEmail(), code);
        clearFailures(guardKey);

        user.setPassword(passwordEncoder.encode(newPassword));
        // 重置密码后吊销全部已有会话
        user.setSessionId(null);
        userRepository.save(user);
    }

    /** 发送修改密码验证码：必须已绑定邮箱，发往绑定邮箱 */
    public void sendChangePasswordCode(String username) {
        User user = findByUsername(username);
        requireBoundEmail(user);
        emailCodeService.sendCode(user.getUsername(), user.getEmail(), EmailCodeService.PURPOSE_CHANGE);
    }

    /**
     * 登录后修改密码：需已绑定邮箱 + 当前密码 + 邮箱验证码。
     * 改密后吊销其他会话；当前会话续期，避免把自己踢下线。
     */
    public void changePassword(String username, String currentPassword, String newPassword,
                               String code, String currentSessionId) {
        User user = findByUsername(username);
        requireBoundEmail(user);

        if (currentPassword == null || currentPassword.isEmpty()) {
            throw new RuntimeException("请输入当前密码");
        }
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new RuntimeException("当前密码错误");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new RuntimeException("新密码至少 6 位");
        }

        // 验证码校验（通过即作废）；与找回密码的码隔离，不能混用
        emailCodeService.verifyCode(user.getUsername(), user.getEmail(), code, EmailCodeService.PURPOSE_CHANGE);

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setSessionId(currentSessionId);
        userRepository.save(user);
    }

    private static void requireBoundEmail(User user) {
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new RuntimeException("请先绑定邮箱后再修改密码");
        }
    }

    /** 注销账号（需密码确认）：软删除进回收站，数据保留以便追回 */
    @org.springframework.transaction.annotation.Transactional
    public void deleteAccount(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        if (password == null || password.isEmpty() || !passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("密码错误");
        }
        // 软删除：踢下线 + 商品进回收站；业务数据保留，管理员可在回收站恢复
        adminService.softDeleteUser(user, "self");
    }

    public String uploadAvatar(String username, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("请选择图片文件");
        }

        // 验证文件大小（最大 5MB）
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new RuntimeException("图片大小不能超过 5MB");
        }

        // 按文件魔数识别真实类型，拒绝伪造 Content-Type（含 SVG 存储型 XSS）
        byte[] bytes = file.getBytes();
        String extension = detectImageExt(bytes);

        // 落盘交给 StorageService（本地/共享盘/对象存储可切换）
        String avatarUrl = storageService.store(StorageCategory.USER_AVATAR, bytes, extension);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        // 删除旧头像（仅限自有 user-avatar 路径）
        if (user.getAvatar() != null && !user.getAvatar().contains("default")
                && user.getAvatar().startsWith(StorageCategory.USER_AVATAR.getUrlPrefix())) {
            storageService.deleteByUrl(user.getAvatar());
        }

        user.setAvatar(avatarUrl);
        userRepository.save(user);

        return avatarUrl;
    }

    /** 按魔数判断图片类型；无法识别则拒绝 */
    private String detectImageExt(byte[] bytes) {
        if (bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89 && (bytes[1] & 0xFF) == 0x50
                && (bytes[2] & 0xFF) == 0x4E && (bytes[3] & 0xFF) == 0x47) {
            return "png";
        }
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (bytes.length >= 6
                && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') {
            return "gif";
        }
        if (bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "webp";
        }
        throw new RuntimeException("仅支持 JPG/PNG/GIF/WebP 图片");
    }
}
