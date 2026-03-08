package com.timmy.job;

import java.time.LocalTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;

import com.timmy.serviceImpl.DatabaseUserDeltaSyncService;

public class DatabaseUserSyncScheduler {

	private static final long INITIAL_DELAY_MS = 60000L;
	private static final long FIXED_DELAY_MS = 300000L;
	private static final String ENABLE_PROPERTY = "db.sync.scheduler.enabled";
	private static final LocalTime RUN_WINDOW_START = LocalTime.MIDNIGHT; // 00:00
	private static final LocalTime RUN_WINDOW_END = LocalTime.of(6, 0); // 06:00

	@Autowired
	private DatabaseUserDeltaSyncService databaseUserDeltaSyncService;

	@Scheduled(initialDelay = INITIAL_DELAY_MS, fixedDelay = FIXED_DELAY_MS)
	public void syncUsersFromDatabaseToAllDevices() {
		if (!isSchedulerEnabled()) {
			return;
		}
		LocalTime now = LocalTime.now();
		if (!isWithinRunWindow(now)) {
			System.out.println("[DatabaseUserSyncScheduler] Skip auto sync. currentTime=" + now
					+ ", allowedWindow=00:00-06:00");
			return;
		}
		try {
			DatabaseUserDeltaSyncService.SyncResult result = databaseUserDeltaSyncService
					.syncChangedUsersToAllDevices("scheduler");
			if (result.isSuccess()) {
				System.out.println("[DatabaseUserSyncScheduler] Delta sync executed. devices=" + result.getDevices()
						+ ", changedStatusUsers=" + result.getChangedStatusUsers() + ", changedDeletedUsers="
						+ result.getChangedDeletedUsers() + ", totalCommandsQueued=" + result.getTotalCommandsQueued());
			} else {
				System.out.println("[DatabaseUserSyncScheduler] Delta sync failed: " + result.getError());
			}
		} catch (Exception ex) {
			System.out.println("[DatabaseUserSyncScheduler] Delta sync exception: " + ex.getMessage());
		}
	}

	private boolean isSchedulerEnabled() {
		String raw = System.getProperty(ENABLE_PROPERTY);
		if (raw == null || raw.trim().isEmpty()) {
			return true;
		}
		return "true".equalsIgnoreCase(raw.trim());
	}

	private boolean isWithinRunWindow(LocalTime now) {
		if (now == null) {
			return false;
		}
		return !now.isBefore(RUN_WINDOW_START) && now.isBefore(RUN_WINDOW_END);
	}
}
