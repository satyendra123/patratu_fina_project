package com.timmy.util;

import org.springframework.beans.factory.annotation.Autowired;

import com.timmy.service.DeviceService;
import com.timmy.service.EnrollInfoService;
import com.timmy.service.MachineCommandService;
import com.timmy.service.PersonService;
import com.timmy.service.RecordsService;

public class ControllerBase {
    
    @Autowired
    protected  EnrollInfoService enrollInfoService;
    
    @Autowired
    protected  PersonService personService;
    
    @Autowired
    protected  RecordsService recordService;
    
    @Autowired
    protected  DeviceService deviceService;

    @Autowired
    protected MachineCommandService machineComandService;
}
