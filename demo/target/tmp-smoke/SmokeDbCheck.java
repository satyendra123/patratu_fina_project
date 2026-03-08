import org.springframework.context.support.ClassPathXmlApplicationContext;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class SmokeDbCheck {
    public static void main(String[] args) throws Exception {
        ClassPathXmlApplicationContext ctx = null;
        try {
            ctx = new ClassPathXmlApplicationContext("spring-mybatis.xml");
            DataSource ds = (DataSource) ctx.getBean("dataSource");
            try (Connection conn = ds.getConnection()) {
                String dbName = null;
                long userCount = -1L;
                try (PreparedStatement ps = conn.prepareStatement("SELECT DB_NAME()")) {
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            dbName = rs.getString(1);
                        }
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM dbo.BIO_USERMAST")) {
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            userCount = rs.getLong(1);
                        }
                    }
                }
                System.out.println("SMOKE_OK db=" + dbName + " bio_usermast_count=" + userCount);
            }
        } finally {
            if (ctx != null) {
                ctx.close();
            }
        }
    }
}
