import java.sql.*;

/** 一次性修复 flyway_schema_history 中 V4 的 checksum（与本地脚本对齐） */
public class FlywayRepairV4 {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:mysql://localhost:3306/seconddata?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true";
        String user = args.length > 0 ? args[0] : "root";
        String pass = args.length > 1 ? args[1] : "";
        try (Connection c = DriverManager.getConnection(url, user, pass)) {
            // 读本地 V4 文件算 Flyway checksum（CRC32 of UTF-8 bytes, as Flyway does）
            java.nio.file.Path p = java.nio.file.Paths.get("src/main/resources/db/migration/V4__add_user_soft_delete.sql");
            byte[] bytes = java.nio.file.Files.readAllBytes(p);
            java.util.zip.CRC32 crc = new java.util.zip.CRC32();
            crc.update(bytes);
            // Flyway 9 checksum is CRC32 as signed int
            int checksum = (int) crc.getValue();
            System.out.println("computed checksum=" + checksum);

            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE flyway_schema_history SET checksum=? WHERE version='4'")) {
                ps.setInt(1, checksum);
                int n = ps.executeUpdate();
                System.out.println("updated rows=" + n);
            }
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery(
                    "SELECT version, description, checksum FROM flyway_schema_history ORDER BY installed_rank")) {
                while (rs.next()) {
                    System.out.println(rs.getString(1) + " | " + rs.getString(2) + " | " + rs.getInt(3));
                }
            }
        }
    }
}
