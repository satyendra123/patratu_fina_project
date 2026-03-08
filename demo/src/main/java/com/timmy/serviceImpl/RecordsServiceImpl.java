package com.timmy.serviceImpl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.timmy.entity.Records;
import com.timmy.mapper.RecordsMapper;
import com.timmy.service.RecordsService;

@Service
public class RecordsServiceImpl implements RecordsService{
	
	@Autowired
	RecordsMapper recordsMapper;

	@Override
	public int deleteByPrimaryKey(Integer id) {
		// TODO Auto-generated method stub
		return recordsMapper.deleteByPrimaryKey(id);
	}

	@Override
	public int insert(Records record) {
		// TODO Auto-generated method stub
		return recordsMapper.insert(record);
	}

	@Override
	public int insertSelective(Records record) {
		// TODO Auto-generated method stub
		return recordsMapper.insert(record);
	}

	@Override
	public Records selectByPrimaryKey(Integer id) {
		// TODO Auto-generated method stub
		return recordsMapper.selectByPrimaryKey(id);
	}

	@Override
	public int updateByPrimaryKeySelective(Records record) {
		// TODO Auto-generated method stub
		return recordsMapper.updateByPrimaryKeySelective(record);
	}

	@Override
	public int updateByPrimaryKey(Records record) {
		// TODO Auto-generated method stub
		return recordsMapper.updateByPrimaryKey(record);
	}

	@Override
	public List<Records> selectAllRecords() {
		// TODO Auto-generated method stub
		return recordsMapper.selectAllRecords();
	}

}
