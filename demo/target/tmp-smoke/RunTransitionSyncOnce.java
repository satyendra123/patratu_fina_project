import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.timmy.serviceImpl.DatabaseUserDeltaSyncService;

public class RunTransitionSyncOnce {
    public static void main(String[] args) throws Exception {
        ClassPathXmlApplicationContext ctx = null;
        try {
            ctx = new ClassPathXmlApplicationContext("spring-mybatis.xml");
            DatabaseUserDeltaSyncService svc = ctx.getBean(DatabaseUserDeltaSyncService.class);
            DatabaseUserDeltaSyncService.SyncResult r = svc.syncChangedStatusTransitionsByVerify("manual-transition-test");
            long started = r.getStartedAt() == null ? 0L : r.getStartedAt().getTime();
            long finished = r.getFinishedAt() == null ? 0L : r.getFinishedAt().getTime();
            System.out.println("SYNC_RESULT success=" + r.isSuccess()
                    + " trigger=" + r.getTrigger()
                    + " activeUsers=" + r.getActiveUsers()
                    + " enabledUsers=" + r.getEnabledUsers()
                    + " disabledUsers=" + r.getDisabledUsers()
                    + " changedStatusUsers=" + r.getChangedStatusUsers()
                    + " totalCommandsQueued=" + r.getTotalCommandsQueued()
                    + " enableCommandsQueued=" + r.getEnableCommandsQueued()
                    + " disableCommandsQueued=" + r.getDisableCommandsQueued()
                    + " devices=" + r.getDevices()
                    + " onlineDevices=" + r.getOnlineDevices()
                    + " reason=" + (r.getReason() == null ? "" : r.getReason())
                    + " error=" + (r.getError() == null ? "" : r.getError())
                    + " startedAtEpochMs=" + started
                    + " finishedAtEpochMs=" + finished);
        } finally {
            if (ctx != null) {
                ctx.close();
            }
        }
    }
}
