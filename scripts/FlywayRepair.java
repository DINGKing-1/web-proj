import org.flywaydb.core.Flyway;

public class FlywayRepair {
    public static void main(String[] args) {
        String url = "jdbc:mysql://localhost:3306/seconddata?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true";
        String user = args.length > 0 ? args[0] : "root";
        String pass = args.length > 1 ? args[1] : "";
        Flyway flyway = Flyway.configure()
                .dataSource(url, user, pass)
                .locations("filesystem:src/main/resources/db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load();
        flyway.repair();
        System.out.println("repair done");
    }
}
