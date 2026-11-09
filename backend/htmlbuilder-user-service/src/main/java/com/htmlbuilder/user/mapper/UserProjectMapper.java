package com.htmlbuilder.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.htmlbuilder.user.entity.UserProject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserProjectMapper extends BaseMapper<UserProject> {

    @Select("SELECT * FROM user_project WHERE user_id = #{userId} AND project_id = #{projectId}")
    UserProject selectByUserAndProject(Long userId, Long projectId);

    @Select("SELECT * FROM user_project WHERE user_id = #{userId} ORDER BY pinned DESC, starred DESC, created_at DESC")
    List<UserProject> selectByUserId(Long userId);
}
