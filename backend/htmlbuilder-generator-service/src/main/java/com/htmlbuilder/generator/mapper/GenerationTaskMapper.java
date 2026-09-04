package com.htmlbuilder.generator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.htmlbuilder.generator.entity.GenerationTask;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GenerationTaskMapper extends BaseMapper<GenerationTask> {
}