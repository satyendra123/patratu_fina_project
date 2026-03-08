package com.timmy.entity;

public class SetUserReturnInfo {
	
	private String ret;
	private String sn;
	private Boolean result;
	public String getRet() {
		return ret;
	}
	public void setRet(String ret) {
		this.ret = ret;
	}
	public String getSn() {
		return sn;
	}
	public void setSn(String sn) {
		this.sn = sn;
	}
	public Boolean getResult() {
		return result;
	}
	public void setResult(Boolean result) {
		this.result = result;
	}
	@Override
	public String toString() {
		return "SetUserReturnInfo [ret=" + ret + ", sn=" + sn + ", result="
				+ result + "]";
	}
	
	

}
