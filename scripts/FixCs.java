import java.sql.*;
import java.nio.file.*;
import java.util.zip.CRC32;

public class FixCs {
    public static void main(String[] a) throws Exception {
        byte[] b = Files.readAllBytes(Paths.get("src/main/resources/db/migration/V4__add_user_soft_delete.sql"));
        CRC32 c = new CRC32();
        c.update(b);
        int cs = (int) c.getValue();
        System.out.println("new checksum=" + cs);
        String url = "jdbc:mysql://localhost:3306/seconddata?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        try (Connection conn = DriverManager.getConnection(url, a[0], a[1]);
             PreparedStatement ps = conn.prepareStatement("UPDATE flyway_schema_history SET checksum=? WHERE version='4'")) {
            ps.setInt(1, cs);
            System.out.println("rows=" + ps.executeUpdate());
        }
        try (Connection conn = DriverManager.getConnection(url, a[0], a[1]);
             Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT version, checksum FROM flyway_schema_history ORDER BY installed_rank")) {
            while (rs.next()) {
                System.out.println(rs.getString(1) + " => " + rs.getInt(2));
            }
        }
    }
}
