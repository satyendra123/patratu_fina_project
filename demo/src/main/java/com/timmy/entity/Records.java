package com.timmy.entity;

import java.util.Date;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public class Records {
	
   private int id;
   @JsonSerialize(using=ToStringSerializer.class)
   private Long enrollId;
   private String recordsTime;
   private int mode;
   private int intout;
   private int event;
   private String deviceSerialNum;
   private double temperature;
   private String image;
	
	
	public int getId() {
		return id;
	}
	public void setId(int id) {
		this.id = id;
	}
	public Long getEnrollId() {
		return enrollId;
	}
	public void setEnrollId(Long enrollId) {
		this.enrollId = enrollId;
	}
	public String getRecordsTime() {
		return recordsTime;
	}
	public void setRecordsTime(String recordTime) {
		this.recordsTime = recordTime;
	}
	public int getMode() {
		return mode;
	}
	public void setMode(int mode) {
		this.mode = mode;
	}
	public int getIntout() {
		return intout;
	}
	public void setIntout(int intout) {
		this.intout = intout;
	}
	public int getEvent() {
		return event;
	}
	public void setEvent(int event) {
		this.event = event;
	}
	public String getDeviceSerialNum() {
		return deviceSerialNum;
	}
	public void setDeviceSerialNum(String deviceSerialNum) {
		this.deviceSerialNum = deviceSerialNum;
	}

	public double getTemperature() {
		return temperature;
	}
	public void setTemperature(double temperature) {
		this.temperature = temperature;
	}
	public String getImage() {
		return image;
	}
	public void setImage(String image) {
		this.image = image;
	}
	
}
