package com.gugucat.shortlink.admin.controller;

import com.gugucat.shortlink.admin.common.convention.result.Result;
import com.gugucat.shortlink.admin.common.convention.result.Results;
import com.gugucat.shortlink.admin.dto.reg.UserRegisterReqDTO;
import com.gugucat.shortlink.admin.dto.reg.UserUpdateReqDTO;
import com.gugucat.shortlink.admin.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


/**
 * 用户控制层
 */
@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 根据用户名查询用户信息
     */
    @GetMapping("/api/clearlink/v0.1/user/{username}")
    public Result getUserByUsername(@PathVariable("username") String username) {
        return Results.success(userService.getUserByUsername(username));
    }


    /**
     * 校验用户名是否存在
     */
    @GetMapping("/api/clearlink/v0.1/user/has-username")
    public Result hasUserName(@RequestParam("username") String username) {
        return Results.success(userService.hasUserName(username));
    }

    /**
     * 用户注册
     */
    @PostMapping("/api/clearlink/v0.1/user")
    public Result register(@RequestBody UserRegisterReqDTO requestParam) {
        userService.register(requestParam);
        return Results.success();
    }

    /**
     * 根据用户名修改（覆写）用户信息
     */
    @PutMapping("/api/clearlink/v0.1/user")
    public Result updateUser(@RequestBody UserUpdateReqDTO requestParam) {
        userService.updateUser(requestParam);
        return Results.success();
    }


}
