import java.sql.*;

public class ShowCols {
    public static void main(String[] a) throws Exception {
        String url = "jdbc:mysql://localhost:3306/seconddata?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        try (Connection c = DriverManager.getConnection(url, a[0], a[1]);
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SHOW COLUMNS FROM chat_rooms")) {
            while (rs.next()) {
                System.out.println(rs.getString(1) + " | " + rs.getString(2));
            }
        }
    }
}
