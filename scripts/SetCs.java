import java.sql.*;

public class SetCs {
    public static void main(String[] a) throws Exception {
        String url = "jdbc:mysql://localhost:3306/seconddata?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        try (Connection c = DriverManager.getConnection(url, a[0], a[1]);
             PreparedStatement ps = c.prepareStatement("UPDATE flyway_schema_history SET checksum=? WHERE version='4'")) {
            ps.setInt(1, Integer.parseInt(a[2]));
            System.out.println("rows=" + ps.executeUpdate());
        }
    }
}
