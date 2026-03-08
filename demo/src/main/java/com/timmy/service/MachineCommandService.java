package com.timmy.service;

import com.timmy.entity.MachineCommand;


public interface MachineCommandService {
	
	public void addMachineCommand(MachineCommand machineCommand);
	
//	public void addGetUserCommand()
	
	public void addGetOneUserCommand(int enrollId,int backupNum,String serialNum);

}
