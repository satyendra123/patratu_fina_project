import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.timmy.serviceImpl.DatabaseUserDeltaSyncService;

public class RunTodayYesterdaySyncOnce {
    public static void main(String[] args) {
        ClassPathXmlApplicationContext ctx = null;
        try {
            ctx = new ClassPathXmlApplicationContext("spring-mybatis.xml");
            DatabaseUserDeltaSyncService svc = ctx.getBean(DatabaseUserDeltaSyncService.class);
            DatabaseUserDeltaSyncService.SyncResult r = svc.syncTodayUpdatedUsersByUpdDate("manual-today-yesterday-test");
            System.out.println("SYNC_RESULT success=" + r.isSuccess()
                    + " trigger=" + r.getTrigger()
                    + " activeUsers=" + r.getActiveUsers()
                    + " enabledUsers=" + r.getEnabledUsers()
                    + " disabledUsers=" + r.getDisabledUsers()
                    + " changedDeletedUsers=" + r.getChangedDeletedUsers()
                    + " totalSent=" + r.getTotalCommandsQueued()
                    + " sentEnable=" + r.getEnableCommandsQueued()
                    + " sentDisable=" + r.getDisableCommandsQueued()
                    + " sentDelete=" + r.getDeleteCommandsQueued()
                    + " devices=" + r.getDevices()
                    + " onlineDevices=" + r.getOnlineDevices()
                    + " reason=" + (r.getReason() == null ? "" : r.getReason())
                    + " error=" + (r.getError() == null ? "" : r.getError()));
        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        } finally {
            if (ctx != null) {
                ctx.close();
            }
        }
    }
}
