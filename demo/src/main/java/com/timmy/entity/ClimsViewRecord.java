package com.timmy.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public class ClimsViewRecord {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long deviceLogId;
    private String downloadDate;
    private String projectId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    private String logDate;
    private String direction;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long deviceId;

    public Long getDeviceLogId() {
        return deviceLogId;
    }

    public void setDeviceLogId(Long deviceLogId) {
        this.deviceLogId = deviceLogId;
    }

    public String getDownloadDate() {
        return downloadDate;
    }

    public void setDownloadDate(String downloadDate) {
        this.downloadDate = downloadDate;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getLogDate() {
        return logDate;
    }

    public void setLogDate(String logDate) {
        this.logDate = logDate;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(Long deviceId) {
        this.deviceId = deviceId;
    }
}
