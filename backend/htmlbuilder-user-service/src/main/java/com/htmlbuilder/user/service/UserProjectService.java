package com.htmlbuilder.user.service;

import com.htmlbuilder.user.dto.BindProjectDTO;
import com.htmlbuilder.user.dto.ProjectRelationDTO;
import com.htmlbuilder.user.vo.UserProjectVO;

import java.util.List;

public interface UserProjectService {

    List<UserProjectVO> listMine(Long userId);

    UserProjectVO bind(Long userId, BindProjectDTO dto);

    UserProjectVO update(Long userId, Long projectId, ProjectRelationDTO dto);

    void unbind(Long userId, Long projectId);
}
