package com.timmy.job;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;

import com.timmy.serviceImpl.DatabaseUserDeltaSyncService;

public class DeviceDeltaQueueSyncScheduler {

	private static final String ENABLE_PROPERTY = "device.delta.sync.scheduler.enabled";
	private static final String CRON_EXPRESSION = "${FP_SCHEDULER_CRON:0 0 19 * * *}";
	private static final String CRON_ZONE = "Asia/Kolkata";

	@Autowired
	private DatabaseUserDeltaSyncService databaseUserDeltaSyncService;

	@Scheduled(cron = CRON_EXPRESSION, zone = CRON_ZONE)
	public void syncDeviceDeltaQueue() {
		if (!isSchedulerEnabled()) {
			return;
		}
		try {
			DatabaseUserDeltaSyncService.SyncResult result = databaseUserDeltaSyncService
					.syncTodayUpdatedUsersByUpdDate("scheduler-cron");
			if (result.isSuccess()) {
				System.out.println("[DeviceDeltaQueueSyncScheduler] Cron direct sync done. todayUpdatedUsers="
						+ result.getActiveUsers() + ", enableToday=" + result.getEnabledUsers()
						+ ", disableToday=" + result.getDisabledUsers() + ", deletedToday="
						+ result.getChangedDeletedUsers() + ", sent(total="
						+ result.getTotalCommandsQueued() + ", enable=" + result.getEnableCommandsQueued()
						+ ", disable=" + result.getDisableCommandsQueued() + ", delete="
						+ result.getDeleteCommandsQueued() + "), devices=" + result.getDevices()
						+ ", onlineDevices=" + result.getOnlineDevices() + ", reason=" + result.getReason());
			} else {
				System.out.println("[DeviceDeltaQueueSyncScheduler] Cron direct sync failed: " + result.getError());
			}
		} catch (Exception ex) {
			System.out.println("[DeviceDeltaQueueSyncScheduler] Cron direct sync exception: " + ex.getMessage());
		}
	}

	private boolean isSchedulerEnabled() {
		String raw = System.getProperty(ENABLE_PROPERTY);
		if (raw == null || raw.trim().isEmpty()) {
			return true;
		}
		return "true".equalsIgnoreCase(raw.trim());
	}
}
