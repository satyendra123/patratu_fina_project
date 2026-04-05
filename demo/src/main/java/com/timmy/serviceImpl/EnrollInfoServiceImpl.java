package com.timmy.serviceImpl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.timmy.entity.EnrollInfo;
import com.timmy.entity.Person;
import com.timmy.entity.UserInfo;
import com.timmy.mapper.EnrollInfoMapper;
import com.timmy.mapper.PersonMapper;
import com.timmy.service.EnrollInfoService;

@Service
public class EnrollInfoServiceImpl implements EnrollInfoService{
	
	@Autowired
	EnrollInfoMapper enrollInfoMapper;
	
	@Autowired
	PersonMapper personMapper;

	@Override
	public int deleteByPrimaryKey(Integer id) {
		// TODO Auto-generated method stub
		return enrollInfoMapper.deleteByPrimaryKey(id);
	}

	

	@Override
	public int insertSelective(EnrollInfo record) {
		// TODO Auto-generated method stub
		return enrollInfoMapper.insertSelective(record);
	}

	@Override
	public EnrollInfo selectByPrimaryKey(Integer id) {
		// TODO Auto-generated method stub
		return enrollInfoMapper.selectByPrimaryKey(id);
	}

	@Override
	public int updateByPrimaryKeySelective(EnrollInfo record) {
		// TODO Auto-generated method stub
		return enrollInfoMapper.updateByPrimaryKeySelective(record);
	}

	@Override
	public int updateByPrimaryKeyWithBLOBs(EnrollInfo record) {
		// TODO Auto-generated method stub
		return enrollInfoMapper.updateByPrimaryKeyWithBLOBs(record);
	}



	@Override
	public int insert(Long enrollid, int backupnum,String imagePath,String signature) {
		// TODO Auto-generated method stub
		return enrollInfoMapper.insert(enrollid, backupnum,imagePath, signature);
	}



	@Override
	public EnrollInfo selectByBackupnum(Long enrollId, int backupnum) {
		// TODO Auto-generated method stub
		return enrollInfoMapper.selectByBackupnum(enrollId, backupnum);
	}



	@Override
	public List<UserInfo> usersToSendDevice() {
		List<Person> persons = personMapper.selectAll();
		List<EnrollInfo> enrollInfos = enrollInfoMapper.selectAll();
		List<UserInfo> userInfos = new ArrayList<UserInfo>();
		Map<Long, Person> personById = new LinkedHashMap<Long, Person>();
		for (Person person : persons) {
			if (person == null || person.getId() == null) {
				continue;
			}
			personById.put(person.getId(), person);
		}
		for (EnrollInfo enrollInfo : enrollInfos) {
			if (enrollInfo == null || enrollInfo.getEnrollId() == null || enrollInfo.getBackupnum() == null) {
				continue;
			}
			Person person = personById.get(enrollInfo.getEnrollId());
			if (person == null) {
				continue;
			}
			UserInfo userInfo = new UserInfo();
			userInfo.setAdmin(person.getRollId() == null ? 0 : person.getRollId());
			userInfo.setBackupnum(enrollInfo.getBackupnum());
			userInfo.setSourceId(enrollInfo.getId() == null ? null : Long.valueOf(enrollInfo.getId().longValue()));
			userInfo.setEnrollId(person.getId());
			userInfo.setName(person.getName() == null ? "" : person.getName());
			userInfo.setRecord(enrollInfo.getSignatures() == null ? "" : enrollInfo.getSignatures());
			userInfos.add(userInfo);
		}
		return userInfos;
	}



	@Override
	public List<EnrollInfo> selectAll() {
		// TODO Auto-generated method stub
		return enrollInfoMapper.selectAll();
	}



	@Override
	public List<EnrollInfo> selectByEnrollId(Long enrollId) {
		// TODO Auto-generated method stub
		return enrollInfoMapper.selectByEnrollId(enrollId);
	}



	@Override
	public int updateByEnrollIdAndBackupNum(String signatures, Long enrollId,
			int backupnum) {
		if (enrollId == null) {
			return 0;
		}
		if (backupnum == 50) {
			EnrollInfo existing = enrollInfoMapper.selectByBackupnum(enrollId, backupnum);
			String incoming = signatures == null ? "" : signatures;
			String current = existing == null || existing.getSignatures() == null ? "" : existing.getSignatures();
			if (existing != null && current.equals(incoming)) {
				// Same base64 already stored for this user photo; skip unnecessary LOB rewrite.
				return 0;
			}
		}
		return enrollInfoMapper.updateByEnrollIdAndBackupNum(signatures, enrollId, backupnum);
	}



	@Override
	public int deleteByEnrollId(Long id) {
		// TODO Auto-generated method stub
		return enrollInfoMapper.deleteByEnrollId(id);
	}

	
	
}
