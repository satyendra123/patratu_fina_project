package com.timmy.job;

import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import com.timmy.entity.Device;
import com.timmy.entity.MachineCommand;
import com.timmy.service.DeviceService;
import com.timmy.service.MachineCommandService;

public class CheckMachineStatus {
	
	@Autowired
	DeviceService deviceService;
	
	@Autowired 
	MachineCommandService machineCommandService;
	
	public void doJob() {
	    List<Device>devices=deviceService.findAllDevice();
		for (int i = 0; i < devices.size(); i++) {
			String message="{\"cmd\":\"getdevinfo\"}";
			MachineCommand machineCommand=new MachineCommand();
			machineCommand.setContent(message);
			machineCommand.setName("getdevinfo");
			machineCommand.setStatus(0);
			machineCommand.setSendStatus(0);
			machineCommand.setErrCount(0);
			machineCommand.setSerial(devices.get(i).getSerialNum());
			machineCommand.setGmtCrate(new Date());
			machineCommand.setGmtModified(new Date());
			machineCommand.setContent(message);
			machineCommandService.addMachineCommand(machineCommand);
			
		}
	}

}
