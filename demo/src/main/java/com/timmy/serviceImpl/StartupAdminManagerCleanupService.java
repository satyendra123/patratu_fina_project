package com.timmy.serviceImpl;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class StartupAdminManagerCleanupService {

	private static final Logger log = LoggerFactory.getLogger(StartupAdminManagerCleanupService.class);
	private static final AtomicBoolean STARTUP_CLEAN_DONE = new AtomicBoolean(false);
	private static final int QUERY_TIMEOUT_SECONDS = 30;
	private static final String CLEANUP_THREAD_NAME = "startup-admin-cleanup";

	private static final String RESET_DB_ADMIN_SQL = "UPDATE BIO_USERMAST "
			+ "SET PRI = '0', UPD_DATE = GETDATE() "
			+ "WHERE ISNULL(ISDELETED, 0) = 0 "
			+ "AND ISNULL(CASE WHEN ISNUMERIC(PRI) = 1 THEN CONVERT(INT, PRI) ELSE NULL END, 0) <> 0";

	private static final String CLEAR_PENDING_ADMIN_SETUSERINFO_SQL = "DELETE FROM DEVICECMD "
			+ "WHERE CMD_DESC = 'JAVA:setuserinfo' "
			+ "AND ISNULL(CASE WHEN ISNUMERIC(DC_RES) = 1 THEN CONVERT(INT, DC_RES) ELSE NULL END, 0) = 0 "
			+ "AND ISNULL(CONVERT(INT, IS_DEL_EXECUTED), 0) = 0 "
			+ "AND ("
			+ "    (REPLACE(CAST(DC_CMD AS NVARCHAR(MAX)), ' ', '') LIKE '%\"admin\":%' "
			+ "     AND REPLACE(CAST(DC_CMD AS NVARCHAR(MAX)), ' ', '') NOT LIKE '%\"admin\":0%') "
			+ " OR REPLACE(CAST(DC_CMD AS NVARCHAR(MAX)), ' ', '') LIKE '%\"enrollid\":1,%'"
			+ ")";

	private static final String QUEUE_CLEANADMIN_ALL_DEVICES_SQL = "INSERT INTO DEVICECMD "
			+ "(SLNO, DC_CMD, DC_DATE, DC_EXECDATE, DC_RES, DC_RESDATE, CMD_DESC, REF_ID, src_sno, DC_cmd_date, IS_DEL_EXECUTED) "
			+ "SELECT d.SLNO, '{\"cmd\":\"cleanadmin\"}', GETDATE(), NULL, '0', NULL, 'JAVA:cleanadmin', 0, NULL, GETDATE(), 0 "
			+ "FROM (SELECT DISTINCT LTRIM(RTRIM(SLNO)) AS SLNO FROM DEVICEINFO WHERE ISNULL(LTRIM(RTRIM(SLNO)), '') <> '') d "
			+ "WHERE NOT EXISTS ( "
			+ "    SELECT 1 FROM DEVICECMD c "
			+ "    WHERE c.SLNO = d.SLNO "
			+ "      AND c.CMD_DESC = 'JAVA:cleanadmin' "
			+ "      AND ISNULL(CASE WHEN ISNUMERIC(c.DC_RES) = 1 THEN CONVERT(INT, c.DC_RES) ELSE NULL END, 0) = 0 "
			+ "      AND ISNULL(CONVERT(INT, c.IS_DEL_EXECUTED), 0) = 0 "
			+ ")";

	@Autowired
	private DataSource dataSource;

	@PostConstruct
	public void cleanupAdminAndManagerAtStartup() {
		if (!STARTUP_CLEAN_DONE.compareAndSet(false, true)) {
			return;
		}
		Thread cleanupThread = new Thread(new Runnable() {
			@Override
			public void run() {
				runCleanupSafely();
			}
		}, CLEANUP_THREAD_NAME);
		cleanupThread.setDaemon(true);
		cleanupThread.start();
	}

	private void runCleanupSafely() {
		int dbAdminsReset = -1;
		int pendingSetUserInfoRemoved = -1;
		int cleanAdminQueued = -1;
		try {
			JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
			jdbcTemplate.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
			log.info("[STARTUP-ADMIN-CLEAN] background cleanup started.");
			dbAdminsReset = runUpdateSafely(jdbcTemplate, RESET_DB_ADMIN_SQL, "reset-db-admin");
			pendingSetUserInfoRemoved = runUpdateSafely(jdbcTemplate, CLEAR_PENDING_ADMIN_SETUSERINFO_SQL,
					"delete-pending-admin-setuserinfo");
			cleanAdminQueued = runUpdateSafely(jdbcTemplate, QUEUE_CLEANADMIN_ALL_DEVICES_SQL, "queue-cleanadmin");
		} catch (Exception ex) {
			log.error("[STARTUP-ADMIN-CLEAN] failed to run startup admin cleanup.", ex);
			return;
		}
		log.info(
				"[STARTUP-ADMIN-CLEAN] completed | db_admin_rows_reset={} | pending_admin_setuserinfo_removed={} | cleanadmin_queued_devices={}",
				Integer.valueOf(dbAdminsReset), Integer.valueOf(pendingSetUserInfoRemoved),
				Integer.valueOf(cleanAdminQueued));
	}

	private int runUpdateSafely(JdbcTemplate jdbcTemplate, String sql, String stepName) {
		try {
			return jdbcTemplate.update(sql);
		} catch (Exception ex) {
			log.warn("[STARTUP-ADMIN-CLEAN] step '{}' skipped due to error: {}", stepName, ex.getMessage());
			return -1;
		}
	}
}
