package com.timmy.serviceImpl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.timmy.entity.Device;
import com.timmy.mapper.DeviceMapper;
import com.timmy.service.DeviceService;

@Service
public class DeviceServiceImpl implements DeviceService {
  
	@Autowired
	DeviceMapper deviceMapper;
	 
	@Override
	public List<Device> findAllDevice() {
		// TODO Auto-generated method stub
		List<Device>deviceLists=deviceMapper.findAllDevice();
		return deviceLists;
	}

	@Override
	public int deleteByPrimaryKey(Integer id) {
		// TODO Auto-generated method stub
		int i=deviceMapper.deleteByPrimaryKey(id);
		
		return i;
	}

	@Override
	public int insert(String serialNum, int status) {
		// TODO Auto-generated method stub
		int i=deviceMapper.insert(serialNum, status);
		return i;
	}

	@Override
	public int insertSelective(Device record) {
		// TODO Auto-generated method stub
		
		return 0;
	}

	@Override
	public Device selectByPrimaryKey(Integer id) {
		// TODO Auto-generated method stub
		Device device=deviceMapper.selectByPrimaryKey(id);
		return device;
	}

	@Override
	public int updateByPrimaryKeySelective(Device record) {
		// TODO Auto-generated method stub
		
		return 0;
	}

	@Override
	public int updateByPrimaryKey(Device record) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public Device selectDeviceBySerialNum(String serialNum) {
		// TODO Auto-generated method stub
	   Device device=deviceMapper.selectDeviceBySerialNum(serialNum);
		return device;
	}

	@Override
	public int updateStatusByPrimaryKey(int id, int status) {
		// TODO Auto-generated method stub
		return deviceMapper.updateStatusByPrimaryKey(id, status);
	}

	@Override
	public List<Device> selectAllDevice() {
		// TODO Auto-generated method stub
		return deviceMapper.findAllDevice();
	}
   
	
}
