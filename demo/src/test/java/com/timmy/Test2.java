package com.timmy;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.timmy.entity.MachineCommand;
import com.timmy.entity.Temp;
import com.timmy.mapper.MachineCommandMapper;
import com.timmy.util.ImageProcess;

@RunWith(SpringJUnit4ClassRunner.class)     //表示继承了SpringJUnit4ClassRunner类 

@ContextConfiguration(locations = {"classpath:spring-mybatis.xml"}) 
public class Test2 {

	@Autowired
	MachineCommandMapper machineCommandMapper;
	
	
	
	@Test
	public void name() {
		
		 String fName ="D:\\EmpImages\\";    
		   
		    
		    File dir = new File(fName);
		    List<File>fileList=new ArrayList<File>();
	        File[] files = dir.listFiles(); // 该文件目录下文件全部放入数组
	    //    System.out.println("文件名-----"+files[1]);
	        if (files != null) {
	        	
	            for (int i = 0; i < files.length; i++) {
	                String fileName1 = files[i].getName();
	            //    System.out.println("文件名-----"+fileName1);
	                if (files[i].isFile()) { 
	                    String strFileName = files[i].getAbsolutePath();
	                    System.out.println("---" + strFileName);
	                    fileList.add(files[i]);
	                }
	            }
	        }

		    List<Temp>temps=new ArrayList<Temp>();
	        for (int i = 0; i < fileList.size()-1; i++) {
	        	// File tempFile =new File(fName.trim());  	  
	    	     String fileName = fileList.get(i).getName(); 
	    	    
	    	     String [] str=fileName.split("\\.");
	    	     System.out.println("文件名"+str[0]);
	    	     
	    	    Long enrollId= Long.valueOf(str[0]);
	    	    
	    	    Temp temp=new Temp();
	    	    temp.setEnrollId(enrollId);
	    	    temp.setRecord(ImageProcess.imageToBase64Str(files[i].getAbsolutePath()));
	    	    temps.add(temp);
			}
	        
	     //   System.out.println("数据"+);
	        for (int i = 0; i < temps.size(); i++) {
	        	  MachineCommand machineCommand=new MachineCommand();
	      		
	      		machineCommand.setName("setuserinfo");
	      		machineCommand.setStatus(0);
	      		machineCommand.setSendStatus(0);
	      		machineCommand.setErrCount(0);
	      		machineCommand.setSerial("ZXZX1234567");
	      		machineCommand.setGmtCrate(new Date());
	      		machineCommand.setGmtModified(new Date());
	      		
	      		  machineCommand.setContent("{\"cmd\":\"setuserinfo\",\"enrollid\":"+temps.get(i).getEnrollId()+ ",\"name\":\"" + temps.get(i).getEnrollId() +"\",\"backupnum\":" +"50"
	      					+ ",\"admin\":" + "0" + ",\"record\":\"" + temps.get(i).getRecord() + "\"}");; 
	      			
	      					machineCommandMapper.insert(machineCommand);
			}
		    System.out.println("执行完成====================");
	        
		
		
	}
}
