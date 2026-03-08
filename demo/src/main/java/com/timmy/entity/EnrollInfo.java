package com.timmy.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public class EnrollInfo {
    private Integer id;

    @JsonSerialize(using=ToStringSerializer.class)
    private Long enrollId;

    private Integer backupnum;
    private String imagePath;

    private String signatures;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Long getEnrollId() {
        return enrollId;
    }

    public void setEnrollId(Long enrollId) {
        this.enrollId = enrollId;
    }

    public Integer getBackupnum() {
        return backupnum;
    }

    public void setBackupnum(Integer backupnum) {
        this.backupnum = backupnum;
    }

    
  
	public String getImagePath() {
		return imagePath;
	}

	public void setImagePath(String imagePath) {
		this.imagePath = imagePath;
	}

	public String getSignatures() {
        return signatures;
    }

    public void setSignatures(String signatures) {
        this.signatures = signatures == null ? null : signatures.trim();
    }

	@Override
	public String toString() {
		return "EnrollInfo [id=" + id + ", enrollId=" + enrollId + ", backupnum=" + backupnum + ", imagePath="
				+ imagePath + ", signatures=" + signatures + "]";
	}


	
    
    
}