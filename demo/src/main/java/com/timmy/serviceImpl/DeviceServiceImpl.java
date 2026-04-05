package com.timmy.serviceImpl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.timmy.entity.Device;
import com.timmy.mapper.DeviceMapper;
import com.timmy.service.DeviceService;
import com.timmy.websocket.WebSocketPool;

@Service
public class DeviceServiceImpl implements DeviceService {

	private static final Logger log = LoggerFactory.getLogger(DeviceServiceImpl.class);

	@Autowired
	DeviceMapper deviceMapper;
	 
	@Override
	public List<Device> findAllDevice() {
		List<Device>deviceLists=deviceMapper.findAllDevice();
		reconcileLiveStatuses(deviceLists);
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
		return findAllDevice();
	}

	private void reconcileLiveStatuses(List<Device> devices) {
		if (devices == null || devices.isEmpty()) {
			return;
		}
		for (Device device : devices) {
			if (device == null) {
				continue;
			}
			String serialNum = normalizeSerial(device.getSerialNum());
			int liveStatus = serialNum != null && WebSocketPool.getDeviceStatus(serialNum) != null ? 1 : 0;
			Integer currentStatus = device.getStatus();
			if (currentStatus == null || currentStatus.intValue() != liveStatus) {
				device.setStatus(liveStatus);
				persistReconciledStatus(device, serialNum, liveStatus);
			}
		}
	}

	private void persistReconciledStatus(Device device, String serialNum, int liveStatus) {
		try {
			if (device.getId() != null) {
				deviceMapper.updateStatusByPrimaryKey(device.getId().intValue(), liveStatus);
				return;
			}
			if (serialNum != null) {
				deviceMapper.insert(serialNum, liveStatus);
			}
		} catch (Exception ex) {
			log.warn("Failed to reconcile live device status. serial:{} status:{}", serialNum, liveStatus, ex);
		}
	}

	private String normalizeSerial(String serialNum) {
		if (serialNum == null) {
			return null;
		}
		String trimmed = serialNum.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
