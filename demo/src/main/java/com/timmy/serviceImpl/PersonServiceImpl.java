package com.timmy.serviceImpl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.timmy.entity.DeviceStatus;
import com.timmy.entity.EnrollInfo;
import com.timmy.entity.MachineCommand;
import com.timmy.entity.Person;
import com.timmy.entity.SendMessage;
import com.timmy.entity.UserInfo;
import com.timmy.mapper.MachineCommandMapper;
import com.timmy.mapper.PersonMapper;
import com.timmy.service.EnrollInfoService;
import com.timmy.service.PersonService;
import com.timmy.websocket.WebSocketPool;

@Service
public class PersonServiceImpl implements PersonService {
	// Keep bulk user sync disabled; single-user sync is allowed explicitly via controller flows.
	private static final boolean BULK_BACKUP_SYNC_ENABLED = false;
	
	@Autowired 
	PersonMapper personMapper;

	@Autowired
	EnrollInfoService enrollInfoService;
	
	@Autowired
	MachineCommandMapper machineCommandMapper;
	
	@Override
	public int updateByPrimaryKeySelective(Person record) {
		// TODO Auto-generated method stub
		return personMapper.updateByPrimaryKeySelective(record);
	}

	@Override
	public int updateByPrimaryKey(Person record) {
		// TODO Auto-generated method stub
		return personMapper.updateByPrimaryKey(record);
	}



	


	@Override
	public int insertSelective(Person person) {
		// TODO Auto-generated method stub
		return personMapper.insertSelective(person);
	}



	@Override
	public int insert(Person person) {
		// TODO Auto-generated method stub
		return personMapper.insert(person);
	}

	@Override
	public int deleteByPrimaryKey(Long id) {
		// TODO Auto-generated method stub
		return personMapper.deleteByPrimaryKey(id);
	}

	@Override
	public Person selectByPrimaryKey(Long id) {
		// TODO Auto-generated method stub
		return personMapper.selectByPrimaryKey(id);
	}

	@Override
	public List<Person> selectAll() {
		// TODO Auto-generated method stub
		return personMapper.selectAll();
	}

	@Override
	public List<Person> selectAllByKeyword(String keyword) {
		return personMapper.selectAllByKeyword(keyword);
	}

	@Override
	public List<Long> selectDeletedUserIds() {
		return personMapper.selectDeletedUserIds();
	}

      public void setUserToDevice(Long enrollId,String name,int backupnum,int admin,String records,String deviceSn) {
    	
    	  if(backupnum!=-1) {
    		  
    	 
    	  

			MachineCommand machineCommand=new MachineCommand();
			
			machineCommand.setName("setuserinfo");
			machineCommand.setStatus(0);
			machineCommand.setSendStatus(0);
			machineCommand.setErrCount(0);
			machineCommand.setSerial(deviceSn);
			machineCommand.setGmtCrate(new Date());
			machineCommand.setGmtModified(new Date());
	
    	  machineCommand.setContent("{\"cmd\":\"setuserinfo\",\"enrollid\":"+enrollId+ ",\"name\":\"" + name +"\",\"backupnum\":" + backupnum
					+ ",\"admin\":" + admin + ",\"record\":\"" + records + "\"}");; 
  		
  		if (backupnum==11||backupnum==10) {
  			machineCommand.setContent("{\"cmd\":\"setuserinfo\",\"enrollid\":"+enrollId+ ",\"name\":\"" + name +"\",\"backupnum\":" + backupnum
						+ ",\"admin\":" + admin + ",\"record\":" + records + "}"); 
			}
  		
    	 		
  		machineCommandMapper.insert(machineCommand);
    	  }else {
    		  
    		  StringBuilder sb=new StringBuilder();
  	    	sb.append("{\"cmd\":\"setusername\",\"count\":"+"1"+",\"record\":[");
  	    	
  				sb.append("{\"enrollid\":"+enrollId+",\"name\":\"" + name+"\"}");	
  	    		
  	    	sb.append("]}");
  	    	System.out.println("下发用户姓名"+sb);
  	    	MachineCommand machineCommand=new MachineCommand();
      		
      		machineCommand.setName("setusername");
      		machineCommand.setStatus(0);
      		machineCommand.setSendStatus(0);
      		machineCommand.setErrCount(0);
      		machineCommand.setSerial(deviceSn);
      		machineCommand.setGmtCrate(new Date());
      		machineCommand.setGmtModified(new Date());
      		machineCommand.setContent(sb.toString());
  	    	machineCommandMapper.insert(machineCommand);
    		  
    		  
    	  }
	  }
      
      public void setUserToDevice2(String deviceSn) {
	  	if (!BULK_BACKUP_SYNC_ENABLED) {
	  		return;
	  	}
    	  List<UserInfo>userInfos=enrollInfoService.usersToSendDevice();
		    
	    	System.out.println(userInfos.size());
	    	for (int i = 0; i < userInfos.size(); i++) {
	    		Long enrollId=userInfos.get(i).getEnrollId();
				String name=userInfos.get(i).getName();
				int backupnum=userInfos.get(i).getBackupnum();
				int admin=userInfos.get(i).getAdmin();
				String record=userInfos.get(i).getRecord();						
				SendMessage message=new SendMessage();								
	    		MachineCommand machineCommand=new MachineCommand();
	    		
	    		machineCommand.setName("setuserinfo");
	    		machineCommand.setStatus(0);
	    		machineCommand.setSendStatus(0);
	    		machineCommand.setErrCount(0);
	    		machineCommand.setSerial(deviceSn);
	    		machineCommand.setGmtCrate(new Date());
	    		machineCommand.setGmtModified(new Date());
	    		
		  
				machineCommand.setContent("{\"cmd\":\"setuserinfo\",\"enrollid\":"+enrollId+ ",\"name\":\"" + name +"\",\"backupnum\":" + backupnum
						+ ",\"admin\":" + admin + ",\"record\":\"" + record + "\"}"); 
	    	
	    		if (backupnum==11||backupnum==10) {
	    			machineCommand.setContent("{\"cmd\":\"setuserinfo\",\"enrollid\":"+enrollId+ ",\"name\":\"" + name +"\",\"backupnum\":" + backupnum
							+ ",\"admin\":" + admin + ",\"record\":" + record + "}"); 
				}
	    		
	    		machineCommandMapper.insert(machineCommand);
	    		
	    		
	    	 }	
			
	}
      
      
       
      public void getSignature(int enrollId,String deviceSn,int backupNum) {
    	  try {
	   			Thread.sleep(400);
	   		} catch (InterruptedException e) {
	   			// TODO Auto-generated catch block
	   			e.printStackTrace();
	   		}
    	// List<Person>persons=personMapper.selectAll();
    	 String message1="{\"cmd\":\"getuserinfo\",\"enrollid\":"+enrollId+",\"backupnum\":"+backupNum+"}";	
    	 DeviceStatus deviceStatus=WebSocketPool.getDeviceStatus(deviceSn);
    	 System.out.println("socket连接"+WebSocketPool.getDeviceSocketBySn(deviceSn));
    //	 WebSocketPool.sendMessageToAll(message);
 		if(deviceStatus.getStatus()==1){
 			//WebSocketPool.sendMessageToAll(message);
 			deviceStatus.setStatus(0);
	 		updateDevice(deviceSn, deviceStatus);          
	 		if (null!=deviceStatus.getWebSocket()) {
				deviceStatus.getWebSocket().send(message1);
	 			
			}
 		}else{
 			try {
	   			Thread.sleep(400);
	   		} catch (InterruptedException e) {
	   			// TODO Auto-generated catch block
	   			e.printStackTrace();
	   		}
 			deviceStatus.setStatus(0);
	 		updateDevice(deviceSn, deviceStatus);
	 		if (null!=deviceStatus.getWebSocket()) {
	 			WebSocketPool.sendMessageToDeviceStatus(deviceSn, message1);
				//WebSocketPool.sendMessageToAll(message);
			}
 		}
	}
    
    public void getSignature2(List<EnrollInfo>enrolls,String deviceSn) {
		
    	for (int i = 0; i < enrolls.size(); i++) {
			
    		String message1="{\"cmd\":\"getuserinfo\",\"enrollid\":"+enrolls.get(i).getEnrollId()+",\"backupnum\":"+enrolls.get(i).getBackupnum()+"}";	
    	//	String message="{\"cmd\":\"getuserinfo\",\"enrollid\":"+enrollId+",\"backupnum\":"+ backupNum+"}";
    		MachineCommand machineCommand=new MachineCommand();
    		machineCommand.setContent(message1);
    		machineCommand.setName("getuserinfo");
    		machineCommand.setStatus(0);
    		machineCommand.setSendStatus(0);
    		machineCommand.setErrCount(0);
    		machineCommand.setSerial(deviceSn);
    		machineCommand.setGmtCrate(new Date());
    		machineCommand.setGmtModified(new Date());
    		
    		machineCommandMapper.insert(machineCommand);
		}
		
		
	    	
	}  
      
      
      
      public void updateDevice(String sn,DeviceStatus deviceStatus) {
    	  if(WebSocketPool.getDeviceStatus(sn)!=null){
  			WebSocketPool.removeDeviceStatus(sn);
  			WebSocketPool.addDeviceAndStatus(sn, deviceStatus);
  		}else{
  			WebSocketPool.addDeviceAndStatus(sn, deviceStatus);
  		}
	}

	@Override
	public void getSignatureByAll(int enrollId) {
		// TODO Auto-generated method stub
		//List<Person>persons=personMapper.selectAll();
		try {
			Thread.sleep(400);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
	}

	@Override
	public void deleteUserInfoFromDevice(Long enrollId,
			String deviceSn) {
		
		
			int backupnum=13;
			String message="{\"cmd\":\"deleteuser\",\"enrollid\":"+enrollId+",\"backupnum\":"+backupnum+"}";
		
			MachineCommand machineCommand=new MachineCommand();
			machineCommand.setContent(message);
			machineCommand.setName("deleteuser");
			machineCommand.setStatus(0);
			machineCommand.setSendStatus(0);
			machineCommand.setErrCount(0);
			machineCommand.setSerial(deviceSn);
			machineCommand.setGmtCrate(new Date());
			machineCommand.setGmtModified(new Date());
			
			machineCommandMapper.insert(machineCommand);
	    	 personMapper.deleteByPrimaryKey(enrollId);
	    	 enrollInfoService.deleteByEnrollId(enrollId);
	    	 	 
	}

	@Override
	public void setUsernameToDevice(String deviceSn) {
		// TODO Auto-generated method stub
		
		 List<Person>persons=personMapper.selectAll();
		    
	    	System.out.println(persons.size());
	    	int i=0;		    
	    	StringBuilder sb=new StringBuilder();
	    	sb.append("{\"cmd\":\"setusername\",\"count\":"+persons.size()+",\"record\":[");
	    	for (int j = 0; j < persons.size(); j++) {
	    		if(j==persons.size()-1||persons.size()==1){
				sb.append("{\"enrollid\":"+persons.get(j).getId()+",\"name\":\"" + persons.get(j).getName()+"\"}");	
	    		}else{
	    		sb.append("{\"enrollid\":"+persons.get(j).getId()+",\"name\":\"" + persons.get(j).getName()+"\"},");
	    		}
			}
	    	sb.append("]}");
	    	System.out.println("下发用户姓名"+sb);
	    	MachineCommand machineCommand=new MachineCommand();
    		
    		machineCommand.setName("setusername");
    		machineCommand.setStatus(0);
    		machineCommand.setSendStatus(0);
    		machineCommand.setErrCount(0);
    		machineCommand.setSerial(deviceSn);
    		machineCommand.setGmtCrate(new Date());
    		machineCommand.setGmtModified(new Date());
    		machineCommand.setContent(sb.toString());
	    	machineCommandMapper.insert(machineCommand);
	}

	@Override
	public int updateUserEnableFlag(Long enrollId, int enFlag) {
		return personMapper.updateUserEnableFlag(enrollId, enFlag);
	}
}
