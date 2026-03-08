package com.timmy.serviceImpl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.timmy.entity.ClimsViewRecord;
import com.timmy.mapper.ClimsViewMapper;
import com.timmy.service.ClimsViewService;

@Service
public class ClimsViewServiceImpl implements ClimsViewService {

    @Autowired
    private ClimsViewMapper climsViewMapper;

    @Override
    public List<ClimsViewRecord> selectAll() {
        return climsViewMapper.selectAll();
    }
}
