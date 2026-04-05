package com.timmy.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public class UserInfo {

	Long sourceId;
	@JsonSerialize(using=ToStringSerializer.class)
	Long enrollId;
	String name;
	int backupnum;
	int admin;
	Integer status;
	String imagePath;
	String record;
	public Long getSourceId() {
		return sourceId;
	}
	public void setSourceId(Long sourceId) {
		this.sourceId = sourceId;
	}
	public Long getEnrollId() {
		return enrollId;
	}
	public void setEnrollId(Long enrollId) {
		this.enrollId = enrollId;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public int getBackupnum() {
		return backupnum;
	}
	public void setBackupnum(int backupnum) {
		this.backupnum = backupnum;
	}
	public int getAdmin() {
		return admin;
	}
	public void setAdmin(int admin) {
		this.admin = admin;
	}
	public Integer getStatus() {
		return status;
	}
	public void setStatus(Integer status) {
		this.status = status;
	}
	
	
	public String getImagePath() {
		return imagePath;
	}
	public void setImagePath(String imagePath) {
		this.imagePath = imagePath;
	}
	public String getRecord() {
		return record;
	}
	public void setRecord(String record) {
		this.record = record;
	}
	@Override
	public String toString() {
		return "UserInfo [sourceId=" + sourceId + ", enrollId=" + enrollId + ", name=" + name + ", backupnum="
				+ backupnum + ", admin=" + admin + ", status=" + status + ", imagePath=" + imagePath + ", record="
				+ record + "]";
	}
	
	
	
}
