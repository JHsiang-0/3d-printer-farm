package com.example.farm.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.farm.common.api.Result;
import com.example.farm.common.exception.BusinessException;
import com.example.farm.common.utils.JwtUtils;
import com.example.farm.common.utils.PasswordMigrationUtil;
import com.example.farm.entity.FarmUser;
import com.example.farm.entity.dto.LoginDTO;
import com.example.farm.service.FarmUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

/**
 * 用户认证控制器
 * 处理用户登录、注册等认证相关请求
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class FarmUserController {

    private final FarmUserService userService;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.secret-key}")
    private String adminSecretKey;

    /**
     * 用户登录接口
     * 支持明文密码自动迁移：如果检测到明文存储，登录成功后会自动加密更新
     *
     * @param loginDTO 登录请求参数（用户名和密码）
     * @return 包含 JWT Token 的响应结果
     */
    @PostMapping("/login")
    public Result<String> login(@Valid @RequestBody LoginDTO loginDTO) {
        // 1. 去数据库查询该用户
        LambdaQueryWrapper<FarmUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FarmUser::getUsername, loginDTO.getUsername());
        FarmUser user = userService.getOne(wrapper);

        // 2. 校验账号是否存在
        if (user == null) {
            throw new BusinessException("账号或密码错误");
        }

        // 3. 校验密码（支持明文自动迁移）
        PasswordMigrationUtil.MigrateResult result = PasswordMigrationUtil.matchesAndMigrate(
                loginDTO.getPassword(),
                user.getPasswordHash()
        );

        if (!result.isMatches()) {
            throw new BusinessException("账号或密码错误");
        }

        // 4. 如果需要迁移（明文->密文），自动更新数据库
        if (result.needMigration()) {
            user.setPasswordHash(result.getNewHash());
            userService.updateById(user);
            log.info("用户 [{}] 的密码已自动迁移为加密存储", user.getUsername());
        }

        // 5. 账号密码正确，颁发 Token
        String token = JwtUtils.generateToken(user.getId(), user.getUsername(), user.getRole());

        // 6. 将 Token 返回给前端
        return Result.success(token, "登录成功");
    }

    /**
     * 用户注册接口（供管理员创建用户或开放注册使用）
     * 密码会自动使用 BCrypt 加密存储
     *
     * @param loginDTO 注册请求参数
     * @return 注册结果
     */
    @PostMapping("/register")
    public Result<String> register(@Valid @RequestBody LoginDTO loginDTO) {
        // 1. 检查用户名是否已存在
        LambdaQueryWrapper<FarmUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FarmUser::getUsername, loginDTO.getUsername());
        if (userService.getOne(wrapper) != null) {
            throw new BusinessException("用户名已存在");
        }

        // 2. 创建新用户
        FarmUser newUser = new FarmUser();
        newUser.setUsername(loginDTO.getUsername());
        // 使用 BCrypt 加密密码
        newUser.setPasswordHash(passwordEncoder.encode(loginDTO.getPassword()));
        newUser.setRole("OPERATOR"); // 默认角色

        // 3. 保存到数据库
        userService.save(newUser);

        return Result.success(null, "注册成功");
    }

    /**
     * 修改密码接口
     *
     * @param userId      用户ID
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     * @return 修改结果
     */
    @PostMapping("/change-password")
    public Result<String> changePassword(@RequestParam Long userId,
                                         @RequestParam String oldPassword,
                                         @RequestParam String newPassword) {
        FarmUser user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // 验证旧密码（支持明文和加密）
        boolean matches;
        if (PasswordMigrationUtil.isEncrypted(user.getPasswordHash())) {
            matches = passwordEncoder.matches(oldPassword, user.getPasswordHash());
        } else {
            matches = oldPassword.equals(user.getPasswordHash());
        }

        if (!matches) {
            throw new BusinessException("原密码错误");
        }

        // 更新为新密码（BCrypt 加密）
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userService.updateById(user);

        return Result.success(null, "密码修改成功");
    }

    /**
     * 【管理员】批量迁移所有明文密码
     * 将所有明文存储的密码自动加密
     * 建议：部署后执行一次，然后可以删除或禁用此接口
     *
     * @param adminSecret 管理员密钥（防止误操作）
     * @return 迁移结果统计
     */
    @PostMapping("/admin/migrate-passwords")
    public Result<String> migrateAllPasswords(@RequestParam String adminSecret) {
        // 简单的安全校验（生产环境建议使用更严格的权限控制）
        if (!adminSecretKey.equals(adminSecret)) {
            throw new BusinessException("管理员密钥错误");
        }

        int migratedCount = 0;
        int skippedCount = 0;

        // 查询所有用户
        for (FarmUser user : userService.list()) {
            String currentHash = user.getPasswordHash();

            // 跳过已加密的
            if (PasswordMigrationUtil.isEncrypted(currentHash)) {
                skippedCount++;
                continue;
            }

            // 加密明文密码
            if (currentHash != null && !currentHash.isEmpty()) {
                user.setPasswordHash(passwordEncoder.encode(currentHash));
                userService.updateById(user);
                migratedCount++;
                log.info("用户 [{}] 的密码已迁移", user.getUsername());
            }
        }

        String message = String.format("密码迁移完成：已迁移 %d 个用户，跳过 %d 个已加密用户",
                migratedCount, skippedCount);
        log.info(message);

        return Result.success(null, message);
    }

    /**
     * 【管理员】检查密码存储状态
     * 统计当前有多少用户仍在使用明文密码
     *
     * @return 统计信息
     */
    @GetMapping("/admin/password-status")
    public Result<String> checkPasswordStatus() {
        int encryptedCount = 0;
        int plainCount = 0;

        for (FarmUser user : userService.list()) {
            if (PasswordMigrationUtil.isEncrypted(user.getPasswordHash())) {
                encryptedCount++;
            } else {
                plainCount++;
            }
        }

        String message = String.format("密码存储统计：加密 %d 人，明文 %d 人，总计 %d 人",
                encryptedCount, plainCount, encryptedCount + plainCount);

        return Result.success(message);
    }
}
