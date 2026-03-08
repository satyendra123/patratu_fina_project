package com.timmy.entity;

import java.util.Date;

public class MachineCommand {
    private Integer id;
    
    private String serial;

    private String name;

    private String content;

    private Integer status;
    
    private Integer sendStatus;
    
    private Integer errCount;

    private Date runTime;

    private Date gmtCrate;

    private Date gmtModified;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    
    public String getSerial() {
		return serial;
	}

	public void setSerial(String serial) {
		this.serial = serial;
	}

	public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? null : name.trim();
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content == null ? null : content.trim();
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    
    public Integer getSendStatus() {
		return sendStatus;
	}

	public void setSendStatus(Integer sendStatus) {
		this.sendStatus = sendStatus;
	}

	
	public Integer getErrCount() {
		return errCount;
	}

	public void setErrCount(Integer errCount) {
		this.errCount = errCount;
	}

	public Date getRunTime() {
        return runTime;
    }

    public void setRunTime(Date runTime) {
        this.runTime = runTime;
    }

    public Date getGmtCrate() {
        return gmtCrate;
    }

    public void setGmtCrate(Date gmtCrate) {
        this.gmtCrate = gmtCrate;
    }

    public Date getGmtModified() {
        return gmtModified;
    }

    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }

	@Override
	public String toString() {
		return "MachineCommand [id=" + id + ", serial=" + serial + ", name=" + name + ", content=" + content
				+ ", status=" + status + ", sendStatus=" + sendStatus + ", errCount=" + errCount + ", runTime="
				+ runTime + ", gmtCrate=" + gmtCrate + ", gmtModified=" + gmtModified + "]";
	}
    
    
}