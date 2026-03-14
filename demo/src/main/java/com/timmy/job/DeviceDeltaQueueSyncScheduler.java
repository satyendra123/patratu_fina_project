package com.timmy.job;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;

import com.timmy.serviceImpl.DatabaseUserDeltaSyncService;

public class DeviceDeltaQueueSyncScheduler {

	private static final String ENABLE_PROPERTY = "device.delta.sync.scheduler.enabled";
	private static final String DAILY_CRON = "0 0 2 * * *";
	private static final String CRON_ZONE = "Asia/Kolkata";

	@Autowired
	private DatabaseUserDeltaSyncService databaseUserDeltaSyncService;

	@Scheduled(cron = DAILY_CRON, zone = CRON_ZONE)
	public void syncDeviceDeltaQueue() {
		if (!isSchedulerEnabled()) {
			return;
		}
		try {
			DatabaseUserDeltaSyncService.SyncResult result = databaseUserDeltaSyncService
					.syncChangedStatusTransitionsByVerify("scheduler-2am");
			if (result.isSuccess()) {
				System.out.println("[DeviceDeltaQueueSyncScheduler] 2AM transition sync done. activeUsers="
						+ result.getActiveUsers() + ", changedToEnable=" + result.getEnabledUsers()
						+ ", changedToDisable=" + result.getDisabledUsers() + ", queued(total="
						+ result.getTotalCommandsQueued() + ", enable=" + result.getEnableCommandsQueued()
						+ ", disable=" + result.getDisableCommandsQueued() + "), devices=" + result.getDevices()
						+ ", onlineDevices=" + result.getOnlineDevices() + ", reason=" + result.getReason());
			} else {
				System.out.println("[DeviceDeltaQueueSyncScheduler] 2AM transition sync failed: " + result.getError());
			}
		} catch (Exception ex) {
			System.out.println("[DeviceDeltaQueueSyncScheduler] 2AM transition sync exception: " + ex.getMessage());
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
