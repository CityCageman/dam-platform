package com.dam.auth.service;

import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.dam.auth.dto.LoginRequest;
import com.dam.auth.model.pojo.SysUser;
import com.dam.auth.repository.SysUserMapper;
import com.dam.common.exception.BusinessException;
import com.dam.common.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证服务
 * 负责用户登录、登出、当前用户信息查询
 * 登录成功后基于 Sa-Token 签发 token，并更新用户最后登录信息（sys_user.last_login_at/last_login_ip）
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    /** 用户数据访问对象 */
    private final SysUserMapper userMapper;

    /** 密码加解密服务（登录密码 RSA 解密） */
    private final CryptoService cryptoService;

    /**
     * 用户登录
     * 校验用户名与密码（BCrypt），检查账号状态，签发 Sa-Token，并更新最后登录信息
     *
     * @param req 登录请求（用户名+密码）
     * @return 携带 token 与用户信息的成功结果
     */
    public Result<Map<String, Object>> login(LoginRequest req) {
        // 按用户名查询用户记录
        SysUser user = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, req.getUsername())
        );
        // 用户不存在：统一提示（避免暴露用户名是否存在）
        if (user == null) {
            throw new BusinessException("用户名或密码错误");
        }
        // 密码校验（BCrypt 哈希比对；前端传输为 RSA 密文，先解密再比对）
        if (!BCrypt.checkpw(cryptoService.decryptPassword(req.getPassword()), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        // 账号状态校验：0-禁用
        if (user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用");
        }

        // 更新最后登录信息（时间+IP，写入 sys_user 真实列）
        user.setLastLoginAt(java.time.LocalDateTime.now());
        user.setLastLoginIp(resolveClientIp());
        userMapper.updateById(user);

        // 签发 Sa-Token 会话
        StpUtil.login(user.getId());
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();

        // 返回 token 与用户信息
        return Result.success(buildLoginResult(tokenInfo.getTokenValue(), user));
    }

    /**
     * 解析客户端真实IP
     * 优先取 X-Forwarded-For 首个IP（网关转发场景），其次 X-Real-IP，最后回退本地地址
     *
     * @return 客户端IP字符串
     */
    private String resolveClientIp() {
        try {
            var req = cn.dev33.satoken.context.SaHolder.getRequest();
            // 优先从转发链首部提取真实IP
            String fwd = req.getHeader("X-Forwarded-For");
            if (fwd != null && !fwd.isBlank()) {
                return fwd.split(",")[0].trim();
            }
            // 次选代理设置的直连IP
            String realIp = req.getHeader("X-Real-IP");
            return realIp != null && !realIp.isBlank() ? realIp : "127.0.0.1";
        } catch (Exception e) {
            // 解析失败时回退本地地址，不影响登录主流程
            return "127.0.0.1";
        }
    }

    /**
     * 组装登录结果（token + 用户信息）
     *
     * @param token Sa-Token 值
     * @param user  登录用户
     * @return 登录结果 Map
     */
    private Map<String, Object> buildLoginResult(String token, SysUser user) {
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("userInfo", buildUserInfo(user));
        return result;
    }

    /**
     * 组装前端需要的用户信息（安全字段如密码不返回）
     *
     * @param user 用户实体
     * @return 用户信息 Map
     */
    private Map<String, Object> buildUserInfo(SysUser user) {
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("realName", user.getRealName());
        // role/department/avatar 为过渡字段（exist=false），当前版本可能为空
        userInfo.put("role", user.getRole());
        userInfo.put("department", user.getDepartment());
        userInfo.put("avatar", user.getAvatar());
        return userInfo;
    }

    /**
     * 退出登录
     * 使当前会话失效（Sa-Token）
     */
    public Result<Void> logout() {
        StpUtil.logout();
        return Result.success();
    }

    /**
     * 获取当前登录用户信息
     *
     * @return 当前用户信息
     */
    public Result<Map<String, Object>> getCurrentUser() {
        // 从会话中取当前用户ID
        long userId = StpUtil.getLoginIdAsLong();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        Map<String, Object> userInfo = buildUserInfo(user);
        return Result.success(userInfo);
    }
}
