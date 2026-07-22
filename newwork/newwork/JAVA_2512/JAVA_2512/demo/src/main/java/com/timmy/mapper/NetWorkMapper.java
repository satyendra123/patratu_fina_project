package com.timmy.mapper;

import org.apache.ibatis.annotations.Param;

public interface NetWorkMapper {
    Integer selectNetworkIdBySlno(@Param("slno") String slno);

    int insertNetworkPlaceholder(@Param("slno") String slno);

    String selectGateBySlno(@Param("slno") String slno);

    int upsertGateBySlno(@Param("slno") String slno, @Param("gate") String gate);
}
