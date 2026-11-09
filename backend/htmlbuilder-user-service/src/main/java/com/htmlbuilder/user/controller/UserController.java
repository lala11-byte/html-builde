package com.htmlbuilder.user.controller;

import com.htmlbuilder.common.result.Result;
import com.htmlbuilder.user.dto.UpdateNicknameDTO;
import com.htmlbuilder.user.service.UserService;
import com.htmlbuilder.user.vo.UserVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public Result<UserVO> me(@RequestHeader("X-User-Id") Long userId) {
        return Result.success(userService.getUserInfo(userId));
    }

    @PutMapping("/me/nickname")
    public Result<UserVO> updateNickname(@RequestHeader("X-User-Id") Long userId,
                                        @Valid @RequestBody UpdateNicknameDTO dto) {
        return Result.success(userService.updateNickname(userId, dto.getNickname()));
    }
}
