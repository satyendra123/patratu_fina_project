package com.timmy.datasource;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.dbcp.BasicDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads SQL Server native auth DLL proactively when integrated authentication is configured.
 * This avoids dependence on a specific java.library.path setup in every runtime.
 */
public class SqlServerAwareDataSource extends BasicDataSource {

	private static final Logger log = LoggerFactory.getLogger(SqlServerAwareDataSource.class);
	private static final AtomicBoolean DLL_LOAD_ATTEMPTED = new AtomicBoolean(false);
	private static final String AUTH_DLL_PROPERTY = "sqlserver.auth.dll.path";
	private static final String AUTH_DLL_ENV = "SQLSERVER_JDBC_AUTH_DLL";

	@Override
	public Connection getConnection() throws SQLException {
		ensureNativeAuthDllLoaded(getUrl());
		return super.getConnection();
	}

	@Override
	public Connection getConnection(String username, String password) throws SQLException {
		ensureNativeAuthDllLoaded(getUrl());
		return super.getConnection(username, password);
	}

	private void ensureNativeAuthDllLoaded(String jdbcUrl) {
		if (!isWindows()) {
			return;
		}
		if (!isIntegratedSqlServerUrl(jdbcUrl)) {
			return;
		}
		if (!DLL_LOAD_ATTEMPTED.compareAndSet(false, true)) {
			return;
		}

		List<File> candidates = collectDllCandidates();
		if (candidates.isEmpty()) {
			log.warn(
					"[SQLSERVER-AUTH] integrated auth is enabled but no mssql-jdbc_auth DLL was found. "
							+ "Set -D{}=<full_dll_path> or SQLSERVER_JDBC_AUTH_DLL env var.",
					AUTH_DLL_PROPERTY);
			return;
		}

		for (File candidate : candidates) {
			try {
				System.load(candidate.getAbsolutePath());
				log.info("[SQLSERVER-AUTH] loaded native auth DLL: {}", candidate.getAbsolutePath());
				return;
			} catch (UnsatisfiedLinkError ex) {
				String message = ex.getMessage();
				if (message != null
						&& message.toLowerCase(Locale.ENGLISH).contains("already loaded in another classloader")) {
					log.info(
							"[SQLSERVER-AUTH] native auth DLL already loaded by another classloader, reusing existing load: {}",
							candidate.getAbsolutePath());
					return;
				}
				log.warn("[SQLSERVER-AUTH] failed to load DLL {}: {}", candidate.getAbsolutePath(), ex.getMessage());
			}
		}

		log.error(
				"[SQLSERVER-AUTH] all DLL load attempts failed. Configure SQL auth (username/password) "
						+ "or provide a valid auth DLL path.");
	}

	private List<File> collectDllCandidates() {
		List<File> candidates = new ArrayList<File>();
		Set<String> seen = new LinkedHashSet<String>();

		addExplicitDllCandidate(candidates, seen, System.getProperty(AUTH_DLL_PROPERTY));
		addExplicitDllCandidate(candidates, seen, System.getenv(AUTH_DLL_ENV));

		String libraryPath = System.getProperty("java.library.path", "");
		if (libraryPath != null && libraryPath.length() > 0) {
			String[] entries = libraryPath.split(File.pathSeparator);
			for (String entry : entries) {
				addMatchingDllsFromDirectory(candidates, seen, entry);
			}
		}

		String catalinaBase = System.getProperty("catalina.base");
		String catalinaHome = System.getProperty("catalina.home");
		String userDir = System.getProperty("user.dir");

		addMatchingDllsFromDirectory(candidates, seen, joinPath(catalinaBase, "bin"));
		addMatchingDllsFromDirectory(candidates, seen, joinPath(catalinaHome, "bin"));
		addMatchingDllsFromDirectory(candidates, seen, joinPath(userDir, "sqljdbc-auth-x64"));
		addMatchingDllsFromDirectory(candidates, seen, joinPath(userDir, "sqljdbc-auth-x86"));
		addMatchingDllsFromDirectory(candidates, seen, userDir);

		return candidates;
	}

	private void addExplicitDllCandidate(List<File> candidates, Set<String> seen, String fullPath) {
		if (fullPath == null || fullPath.trim().isEmpty()) {
			return;
		}
		File file = new File(fullPath.trim());
		if (file.isFile()) {
			addCandidate(candidates, seen, file);
		}
	}

	private void addMatchingDllsFromDirectory(List<File> candidates, Set<String> seen, String directory) {
		if (directory == null || directory.trim().isEmpty()) {
			return;
		}
		File dir = new File(directory.trim());
		if (!dir.isDirectory()) {
			return;
		}

		File[] matches = dir.listFiles((d, name) -> {
			String lower = name.toLowerCase(Locale.ENGLISH);
			return lower.startsWith("mssql-jdbc_auth-") && lower.endsWith(".dll");
		});
		if (matches == null || matches.length == 0) {
			return;
		}

		final boolean prefer64Bit = is64BitJvm();
		Arrays.sort(matches, (a, b) -> scoreDll(b, prefer64Bit) - scoreDll(a, prefer64Bit));
		for (File match : matches) {
			addCandidate(candidates, seen, match);
		}
	}

	private int scoreDll(File file, boolean prefer64Bit) {
		String name = file.getName().toLowerCase(Locale.ENGLISH);
		int score = 0;
		if (prefer64Bit && name.contains("x64")) {
			score += 20;
		}
		if (!prefer64Bit && name.contains("x86")) {
			score += 20;
		}
		if (name.contains("13.2.1")) {
			score += 10;
		}
		return score;
	}

	private void addCandidate(List<File> candidates, Set<String> seen, File file) {
		try {
			String canonical = file.getCanonicalPath();
			if (seen.add(canonical)) {
				candidates.add(file);
			}
		} catch (Exception ex) {
			String absolute = file.getAbsolutePath();
			if (seen.add(absolute)) {
				candidates.add(file);
			}
		}
	}

	private String joinPath(String first, String second) {
		if (first == null || first.trim().isEmpty()) {
			return null;
		}
		return new File(first, second).getAbsolutePath();
	}

	private boolean isIntegratedSqlServerUrl(String jdbcUrl) {
		if (jdbcUrl == null) {
			return false;
		}
		String lower = jdbcUrl.toLowerCase(Locale.ENGLISH);
		return lower.startsWith("jdbc:sqlserver:") && lower.contains("integratedsecurity=true");
	}

	private boolean isWindows() {
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH);
		return os.contains("win");
	}

	private boolean is64BitJvm() {
		String model = System.getProperty("sun.arch.data.model", "");
		String arch = System.getProperty("os.arch", "");
		return "64".equals(model) || arch.contains("64");
	}
}
