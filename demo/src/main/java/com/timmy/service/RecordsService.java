package com.timmy.service;

import java.util.List;

import com.timmy.entity.Records;

public interface RecordsService {
	 int deleteByPrimaryKey(Integer id);

	    int insert(Records record);

	    int insertSelective(Records record);

	  

	    Records selectByPrimaryKey(Integer id);

	 

	    int updateByPrimaryKeySelective(Records record);

	    int updateByPrimaryKey(Records record);
	    
	    List<Records> selectAllRecords();
}
