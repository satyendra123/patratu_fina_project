package com.timmy.mapper;

import java.util.Date;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.timmy.entity.MachineCommand;

public interface MachineCommandMapper {
    int deleteByPrimaryKey(Integer id);

    int insert(MachineCommand record);

    int insertSelective(MachineCommand record);

    MachineCommand selectByPrimaryKey(Integer id);

    int updateByPrimaryKeySelective(MachineCommand record);

    int updateByPrimaryKey(MachineCommand record);
    
    List<MachineCommand> findPendingCommand(@Param("sendStatus")int sendStatus,@Param("serial")String serial);
    
    int updateCommandStatus(@Param("status")int status,@Param("sendStatus")int sendStatus,@Param("runTime")Date runTime,@Param("id")int id);
}