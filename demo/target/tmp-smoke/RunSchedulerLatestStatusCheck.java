import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

public class RunSchedulerLatestStatusCheck {
    public static void main(String[] args) {
        ClassPathXmlApplicationContext ctx = null;
        try {
            ctx = new ClassPathXmlApplicationContext("spring-mybatis.xml");
            DataSource ds = (DataSource) ctx.getBean("dataSource");
            JdbcTemplate jdbcTemplate = new JdbcTemplate(ds);
            String table = "JAVA_VERIFY_SCHEDULER_AUDIT";
            String sql = "WITH base AS ("
                    + " SELECT ID, STATUS, STARTED_AT, FINISHED_AT, ISNULL(ACTIVE_USER,0) AS ACTIVE_USER, ISNULL(DISABLE_USER,0) AS DISABLE_USER, ISNULL(DELETED_USER,0) AS DELETED_USER, CAST(STARTED_AT AS date) AS started_day"
                    + " FROM " + table
                    + " WHERE STARTED_AT >= DATEADD(day, -(? - 1), CAST(GETDATE() AS date))"
                    + "), daily AS ("
                    + " SELECT started_day, COUNT(1) AS runCount, MIN(STARTED_AT) AS firstStartedAt, MAX(FINISHED_AT) AS lastFinishedAt"
                    + " FROM base GROUP BY started_day"
                    + "), latest AS ("
                    + " SELECT started_day, STATUS, ACTIVE_USER, DISABLE_USER, DELETED_USER, ROW_NUMBER() OVER (PARTITION BY started_day ORDER BY STARTED_AT DESC, ID DESC) AS rn"
                    + " FROM base"
                    + ")"
                    + " SELECT CONVERT(VARCHAR(10), d.started_day, 120) AS runDate, d.runCount AS runCount,"
                    + " CASE WHEN l.STATUS='SUCCESS' THEN 1 ELSE 0 END AS successCount,"
                    + " CASE WHEN l.STATUS='FAILED' THEN 1 ELSE 0 END AS failedCount,"
                    + " l.ACTIVE_USER AS enabledUsers, l.DISABLE_USER AS disabledUsers, l.DELETED_USER AS deletedUsers,"
                    + " CASE WHEN l.STATUS IN ('SUCCESS','FAILED','RUNNING') THEN l.STATUS ELSE 'UNKNOWN' END AS dailyStatus"
                    + " FROM daily d LEFT JOIN latest l ON l.started_day=d.started_day AND l.rn=1"
                    + " ORDER BY d.started_day DESC";
            List<Map<String,Object>> rows = jdbcTemplate.queryForList(sql, Integer.valueOf(7));
            System.out.println("ROWS=" + rows.size());
            for (int i = 0; i < rows.size() && i < 5; i++) {
                Map<String,Object> r = rows.get(i);
                System.out.println("runDate=" + r.get("runDate")
                        + " runCount=" + r.get("runCount")
                        + " successCount=" + r.get("successCount")
                        + " failedCount=" + r.get("failedCount")
                        + " enabledUsers=" + r.get("enabledUsers")
                        + " disabledUsers=" + r.get("disabledUsers")
                        + " deletedUsers=" + r.get("deletedUsers")
                        + " dailyStatus=" + r.get("dailyStatus"));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        } finally {
            if (ctx != null) ctx.close();
        }
    }
}
