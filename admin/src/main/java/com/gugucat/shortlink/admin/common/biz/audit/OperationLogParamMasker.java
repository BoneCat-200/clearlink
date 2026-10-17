package com.gugucat.shortlink.admin.common.biz.audit;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 审计参数脱敏与截断
 * <p>
 * 审计日志要可追溯，但不能把密码、令牌、手机号、邮箱原样落库。
 * 这里先序列化参数，再按键名递归打码，最后按长度截断，避免大 body 撑爆表。
 */
public final class OperationLogParamMasker {

    /**
     * 需要打码的字段名
     */
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "token", "phone", "mail", "idCard", "bankCard", "realName");

    /**
     * 参数最大长度
     */
    private static final int MAX_PARAM_LENGTH = 2000;

    private OperationLogParamMasker() {
    }

    /**
     * 序列化并脱敏方法参数
     *
     * @param args 方法参数
     * @return 脱敏后的 JSON；序列化失败时返回占位文案
     */
    public static String mask(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        try {
            // Servlet 对象序列化出来又大又无意义，先过滤掉
            List<Object> serializableArgs = Arrays.stream(args)
                    .filter(arg -> !(arg instanceof ServletRequest) && !(arg instanceof ServletResponse))
                    .collect(Collectors.toList());
            Object json = JSON.toJSON(serializableArgs);
            maskNode(json);
            return StrUtil.maxLength(JSON.toJSONString(json), MAX_PARAM_LENGTH);
        } catch (Throwable ex) {
            // 审计参数只是辅助信息，不能因为某个对象不可序列化影响主流程
            return "[参数序列化失败]";
        }
    }

    /**
     * 从参数里提取用户名
     * <p>
     * 登录/注册接口执行时 UserContext 还没有用户，审计需要从请求参数里取用户名。
     *
     * @param args 方法参数
     * @return 用户名；取不到返回 null
     */
    public static String extractUsername(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            try {
                Object json = JSON.toJSON(arg);
                if (json instanceof JSONObject) {
                    String username = ((JSONObject) json).getString("username");
                    if (StrUtil.isNotBlank(username)) {
                        return username;
                    }
                }
            } catch (Throwable ignored) {
                // 参数不是 JSON 对象，继续看下一个
            }
        }
        return null;
    }

    /**
     * 递归打码
     *
     * @param node JSON 节点
     */
    private static void maskNode(Object node) {
        if (node instanceof JSONObject) {
            JSONObject jsonObject = (JSONObject) node;
            for (String key : new ArrayList<>(jsonObject.keySet())) {
                if (SENSITIVE_KEYS.contains(key)) {
                    jsonObject.put(key, "***");
                } else {
                    maskNode(jsonObject.get(key));
                }
            }
        } else if (node instanceof JSONArray) {
            for (Object item : (JSONArray) node) {
                maskNode(item);
            }
        }
    }
}
