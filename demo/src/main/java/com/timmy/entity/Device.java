package com.timmy.entity;

public class Device {
    private Integer id;

    private String serialNum;

    private Integer status;

    private String inOutMode;

    private String locationName;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getSerialNum() {
        return serialNum;
    }

    public void setSerialNum(String serialNum) {
        this.serialNum = serialNum == null ? null : serialNum.trim();
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getInOutMode() {
        return inOutMode;
    }

    public void setInOutMode(String inOutMode) {
        this.inOutMode = inOutMode == null ? null : inOutMode.trim();
    }

    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        this.locationName = locationName == null ? null : locationName.trim();
    }

	@Override
	public String toString() {
		return "Device [id=" + id + ", serialNum=" + serialNum + ", status="
				+ status + ", inOutMode=" + inOutMode + ", locationName=" + locationName + "]";
	}
    
    
}
