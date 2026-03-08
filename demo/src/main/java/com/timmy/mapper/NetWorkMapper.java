package com.timmy.mapper;

import org.apache.ibatis.annotations.Param;

public interface NetWorkMapper {
    Integer selectNetworkIdBySlno(@Param("slno") String slno);

    int insertNetworkPlaceholder(@Param("slno") String slno);
}

