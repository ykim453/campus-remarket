package com.campus.remarket.dao;

import com.campus.remarket.dto.BoardCategory;
import com.campus.remarket.dto.BoardCommentDto;
import com.campus.remarket.dto.BoardDto;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BoardDao {
    List<BoardDto> selectAll(
            @Param("schoolId") Long schoolId, @Param("categoryId") Integer categoryId);

    BoardDto selectOne(@Param("id") Long id);

    int insert(BoardDto post);

    int update(BoardDto post);

    int delete(@Param("id") Long id);

    List<BoardCategory> categories();

    boolean categoryExists(@Param("id") Integer id);

    List<BoardCommentDto> comments(@Param("postId") Long postId);

    BoardCommentDto comment(@Param("id") Long id);

    int insertComment(BoardCommentDto comment);

    int deleteComment(@Param("id") Long id, @Param("memberId") Long memberId);
}
