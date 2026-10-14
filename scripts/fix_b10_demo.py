from pathlib import Path

# ---- B-10 DemoDataInitializer ----
p = Path('src/main/java/com/secondhand/mall/config/DemoDataInitializer.java')
t = p.read_text(encoding='utf-8')

t = t.replace(
''' * 演示账号统一密码 demo123456：
 *   买家 demo_buyer1/2/3  卖家 demo_seller1/2/3
 */
@Component
@ConditionalOnProperty(name = "mall.demo-data.enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataInitializer implements CommandLineRunner {''',
''' * 演示账号密码随机生成并在启动日志打印，绝不使用固定弱口令。
 * 仅 dev / test 生效；生产（prod）不加载本类。
 */
@Component
@Profile({"dev", "test"})
@ConditionalOnProperty(name = "mall.demo-data.enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataInitializer implements CommandLineRunner {'''
)

t = t.replace(
'''    private static final String PWD = "demo123456";
    private static final String BUYER1 = "demo_buyer1";''',
'''    private static final String BUYER1 = "demo_buyer1";'''
)

t = t.replace(
'''        u.setPassword(passwordEncoder.encode(PWD));
        u.setNickname(nickname);
        u.setBalance(BigDecimal.valueOf(balance));
        u.setRole("user");
        u.setEmail(username + "@demo.local");
        userRepository.save(u);
        log.info("✓ 演示{}账号已创建: {} / {}", kind, username, PWD);''',
'''        String pwd = randomDemoPassword();
        u.setPassword(passwordEncoder.encode(pwd));
        u.setNickname(nickname);
        u.setBalance(BigDecimal.valueOf(balance));
        u.setRole("user");
        u.setEmail(username + "@demo.local");
        userRepository.save(u);
        log.info("✓ 演示{}账号已创建: {} / {}", kind, username, pwd);'''
)

# add import Profile and random password helper before createUser
t = t.replace(
'''import org.springframework.boot.CommandLineRunner;''',
'''import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;'''
)

t = t.replace(
'''    private void createUser(String username, String nickname, String kind, double balance) {''',
'''    /** 演示口令：每次创建随机，只写启动日志，禁止固定弱口令 */
    private static String randomDemoPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(chars.charAt(ThreadLocalRandom.current().nextInt(chars.length())));
        }
        return sb.toString();
    }

    private void createUser(String username, String nickname, String kind, double balance) {'''
)

p.write_text(t, encoding='utf-8')
print('DemoDataInitializer OK, PWD left:', t.count('PWD'), 'demo123456 left:', t.count('demo123456'))
