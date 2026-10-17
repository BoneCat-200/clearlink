package com.gugucat.shortlink.admin.controller;

import com.gugucat.shortlink.admin.common.biz.audit.OperationLog;
import com.gugucat.shortlink.admin.common.biz.idempotent.Idempotent;
import com.gugucat.shortlink.admin.common.convention.result.Result;
import com.gugucat.shortlink.admin.common.convention.result.Results;
import com.gugucat.shortlink.admin.dto.req.UserLoginReqDTO;
import com.gugucat.shortlink.admin.dto.req.UserRegisterReqDTO;
import com.gugucat.shortlink.admin.dto.req.UserUpdateReqDTO;
import com.gugucat.shortlink.admin.dto.resp.UserLoginRespDTO;
import com.gugucat.shortlink.admin.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


/**
 * 用户控制层
 */
@RestController
@RequiredArgsConstructor // 生成构造函数，包含所有 final 字段
public class UserController {

    private final UserService userService;

    /**
     * 根据用户名查询用户信息
     */
    @GetMapping("/api/clearlink/admin/v0.1/user/{username}")
    public Result getUserByUsername(@PathVariable("username") String username) {
        return Results.success(userService.getUserByUsername(username));
    }


    /**
     * 校验用户名是否存在
     */
    @GetMapping("/api/clearlink/admin/v0.1/user/has-username")
    public Result hasUserName(@RequestParam("username") String username) {
        return Results.success(userService.hasUserName(username));
    }

    /**
     * 用户注册
     */
    @OperationLog(module = "用户", action = "注册")
    @PostMapping("/api/clearlink/admin/v0.1/user")
    public Result register(@RequestBody UserRegisterReqDTO requestParam) {
        userService.register(requestParam);
        return Results.success();
    }

    /**
     * 根据用户名修改（覆写）用户信息
     */
    @OperationLog(module = "用户", action = "修改")
    @Idempotent
    @PutMapping("/api/clearlink/admin/v0.1/user")
    public Result updateUser(@RequestBody UserUpdateReqDTO requestParam) {
        userService.updateUser(requestParam);
        return Results.success();
    }

    /**
     * 用户登录
     */
    @OperationLog(module = "用户", action = "登录")
    @PostMapping("/api/clearlink/admin/v0.1/user/login")
    public Result<UserLoginRespDTO> login(@RequestBody UserLoginReqDTO requestParam) {
        return Results.success(userService.login(requestParam));
    }

    /**
     * 检查用户是否已登录
     */
    @GetMapping("/api/clearlink/admin/v0.1/user/login/check")
    // TODO 将token封装到请求头中
    public Result<Boolean> checkLogin(@RequestParam("username") String username, @RequestParam("token") String token) {
        return Results.success(userService.checkLogin(username, token));
    }

    /**
     * 用户登出
     */
    @OperationLog(module = "用户", action = "登出")
    @DeleteMapping("/api/clearlink/admin/v0.1/user/logout")
    public Result logout(@RequestParam("username") String username, @RequestParam("token") String token) {
        userService.logout(username, token);
        return Results.success();
    }


}
