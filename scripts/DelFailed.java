import java.sql.*;

public class DelFailed {
    public static void main(String[] a) throws Exception {
        String url = "jdbc:mysql://localhost:3306/seconddata?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        try (Connection c = DriverManager.getConnection(url, a[0], a[1]);
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM flyway_schema_history WHERE version='11' AND success=0")) {
            System.out.println("deleted=" + ps.executeUpdate());
        }
    }
}
