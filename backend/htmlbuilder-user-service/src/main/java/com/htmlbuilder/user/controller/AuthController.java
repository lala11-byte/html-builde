package com.htmlbuilder.user.controller;

import com.htmlbuilder.common.result.Result;
import com.htmlbuilder.user.dto.LoginDTO;
import com.htmlbuilder.user.dto.RegisterDTO;
import com.htmlbuilder.user.service.UserService;
import com.htmlbuilder.user.vo.TokenVO;
import com.htmlbuilder.user.vo.UserVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public Result<UserVO> register(@Valid @RequestBody RegisterDTO dto) {
        return Result.success(userService.register(dto));
    }

    @PostMapping("/login")
    public Result<TokenVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.success(userService.login(dto));
    }

    @PostMapping("/refresh")
    public Result<TokenVO> refresh(@RequestHeader("Authorization") String authHeader) {
        String refreshToken = authHeader.replace("Bearer ", "");
        return Result.success(userService.refreshToken(refreshToken));
    }
}