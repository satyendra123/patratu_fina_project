package com.timmy.service;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.timmy.entity.EnrollInfo;
import com.timmy.entity.Person;

public interface PersonService {

	int deleteByPrimaryKey(Long id);

    int insert(Person record);

    int insertSelective(Person record);


    Person selectByPrimaryKey(Long id);

   
    int updateByPrimaryKeySelective(Person record);

    int updateByPrimaryKey(Person record);
    
    List<Person> selectAll();

    List<Person> selectAllByKeyword(String keyword);

    List<Long> selectDeletedUserIds();

    public void setUserToDevice(Long enrollId,String name,int backupnum,int admin,String records,String deviceSn);
    
    public void getSignature(int enrollId,String deviceSn,int backupNum);
    public void getSignatureByAll(int enrollId);
    public void setUserToDevice2(String deviceSn);
    
    void getSignature2(List<EnrollInfo>enrolls,String deviceSn);
    
    void deleteUserInfoFromDevice(Long enrollId,String deviceSn);
    
    public void setUsernameToDevice(String deviceSn);

    int updateUserEnableFlag(Long enrollId, int enFlag);
}
