import java.sql.*;

public class ShowHist {
    public static void main(String[] a) throws Exception {
        String url = "jdbc:mysql://localhost:3306/seconddata?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        try (Connection c = DriverManager.getConnection(url, a[0], a[1]);
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT version, description, success, checksum FROM flyway_schema_history ORDER BY installed_rank")) {
            while (rs.next()) {
                System.out.println(rs.getString(1) + " | " + rs.getString(2) + " | success=" + rs.getBoolean(3) + " | cs=" + rs.getInt(4));
            }
        }
    }
}
