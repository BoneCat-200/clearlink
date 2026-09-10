package com.gugucat.shortlink.admin.controller;

import com.gugucat.shortlink.admin.common.convention.result.Result;
import com.gugucat.shortlink.admin.common.convention.result.Results;
import com.gugucat.shortlink.admin.common.enums.UserErrorCodeEnum;
import com.gugucat.shortlink.admin.dto.reps.UserRespDTO;
import com.gugucat.shortlink.admin.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;


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
    @GetMapping("/api/shortlink/v1/user/{username}")
    public Result getUserByUsername(@PathVariable("username") String username) {
        UserRespDTO result = userService.getUserByUsername(username);
//        Result<UserRespDTO> result = new Result<UserRespDTO>().setCode(UserErrorCodeEnum.USER_NULL.code())
//                .setMessage(UserErrorCodeEnum.USER_NULL.message())
//                .setData(dto);
//        return result;
        if (result == null){
            return Results.failure(UserErrorCodeEnum.USER_NULL.code(), UserErrorCodeEnum.USER_NULL.message());
        }else {
            return Results.success(result);
        }

    }

}
