package com.campus.remarket.dao;

import com.campus.remarket.dto.TradeDto;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TradeDao {
    List<TradeDto> selectAll();

    TradeDto selectOne(@Param("id") String id);

    TradeDto lockOne(@Param("id") String id);

    int insert(TradeDto dto);

    int complete(@Param("id") String id);

    int meeting(TradeDto dto);

    int cancel(@Param("id") String id);
}
