package com.timmy.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public class UserTemp {

	@JsonSerialize(using=ToStringSerializer.class)
	Long enrollId;
	int admin;
	int backupnum;
	public Long getEnrollId() {
		return enrollId;
	}
	public void setEnrollId(Long enrollId) {
		this.enrollId = enrollId;
	}
	public int getAdmin() {
		return admin;
	}
	public void setAdmin(int admin) {
		this.admin = admin;
	}
	public int getBackupnum() {
		return backupnum;
	}
	public void setBackupnum(int backupnum) {
		this.backupnum = backupnum;
	}
	@Override
	public String toString() {
		return "UserTemp [enrollId=" + enrollId + ", admin=" + admin
				+ ", backupnum=" + backupnum + "]";
	}
	
	
}
