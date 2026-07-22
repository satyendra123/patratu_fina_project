package com.timmy.entity;

public class recordsLog<T> {
	String cmd;
	String serialNum;
	int count;
	int longdex;
	private T data;
	public String getCmd() {
		return cmd;
	}
	public void setCmd(String cmd) {
		this.cmd = cmd;
	}
	public String getSerialNum() {
		return serialNum;
	}
	public void setSerialNum(String serialNum) {
		this.serialNum = serialNum;
	}
	public int getCount() {
		return count;
	}
	public void setCount(int count) {
		this.count = count;
	}
	public int getLongdex() {
		return longdex;
	}
	public void setLongdex(int longdex) {
		this.longdex = longdex;
	}
	public T getData() {
		return data;
	}
	public void setData(T data) {
		this.data = data;
	}
	@Override
	public String toString() {
		return "recordsLog [cmd=" + cmd + ", serialNum=" + serialNum
				+ ", count=" + count + ", longdex=" + longdex + ", data="
				+ data + "]";
	}
	
    
}
