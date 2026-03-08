package com.timmy.mapper;

import com.timmy.entity.Records;

import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface RecordsMapper {
   

    int deleteByPrimaryKey(Integer id);

    int insert(Records record);

    int insertSelective(Records record);

  

    Records selectByPrimaryKey(Integer id);

 

    int updateByPrimaryKeySelective(Records record);

    int updateByPrimaryKey(Records record);
    
    List<Records> selectAllRecords();
}