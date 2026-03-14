package com.timmy.controller;

import javax.servlet.http.HttpSession;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import com.timmy.util.SimpleAuthConfig;

@Controller
public class AuthController {

	@Autowired(required = false)
	private DataSource dataSource;

	private volatile Boolean dbLoginTableAvailable = null;

	@RequestMapping(value = "/login", method = RequestMethod.GET)
	public String loginPage(HttpSession session) {
		if (session != null && session.getAttribute(SimpleAuthConfig.SESSION_USER_KEY) != null) {
			return "redirect:/index.jsp";
		}
		return "login";
	}

	@RequestMapping(value = "/login", method = RequestMethod.POST)
	public String login(@RequestParam("username") String username, @RequestParam("password") String password,
			HttpSession session) {
		boolean validByDb = isValidCredentialFromDb(username, password);
		if (validByDb) {
			session.setAttribute(SimpleAuthConfig.SESSION_USER_KEY, username.trim());
			return "redirect:/index.jsp";
		}
		return "redirect:/login?error=1";
	}

	@RequestMapping(value = "/logout", method = RequestMethod.GET)
	public String logout(HttpSession session) {
		if (session != null) {
			session.invalidate();
		}
		return "redirect:/login";
	}

	private boolean isValidCredentialFromDb(String username, String password) {
		if (dataSource == null || username == null || password == null) {
			return false;
		}
		String normalizedUser = username.trim();
		if (normalizedUser.isEmpty()) {
			return false;
		}
		try {
			JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
			if (!isDbLoginTableAvailable(jdbcTemplate)) {
				return false;
			}
			Integer count = jdbcTemplate.queryForObject(
					"SELECT COUNT(1) FROM APP_LOGIN_USER WHERE ISNULL(IS_ACTIVE, 1) = 1 AND USERNAME = ? AND PASSWORD = ?",
					new Object[] { normalizedUser, password }, Integer.class);
			return count != null && count.intValue() > 0;
		} catch (Exception ex) {
			return false;
		}
	}

	private boolean isDbLoginTableAvailable(JdbcTemplate jdbcTemplate) {
		Boolean cached = dbLoginTableAvailable;
		if (Boolean.TRUE.equals(cached)) {
			return true;
		}
		try {
			Integer exists = jdbcTemplate.queryForObject(
					"SELECT COUNT(1) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'APP_LOGIN_USER'",
					Integer.class);
			boolean available = exists != null && exists.intValue() > 0;
			dbLoginTableAvailable = Boolean.valueOf(available);
			return available;
		} catch (Exception ex) {
			// Do not cache negative result permanently; transient DB issues should recover.
			return false;
		}
	}
}
