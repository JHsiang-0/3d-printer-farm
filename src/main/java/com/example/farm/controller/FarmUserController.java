package com.example.farm.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.farm.common.api.Result;
import com.example.farm.common.exception.BusinessException;
import com.example.farm.entity.FarmUser;
import com.example.farm.entity.dto.*;
import com.example.farm.service.FarmUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

/**
 * 用户认证与管理中心
 * 处理用户登录、注册、信息管理等
 */
@Slf4j
@Tag(name = "用户管理")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class FarmUserController {

    private final FarmUserService farmUserService;

    @Value("${admin.secret-key}")
    private String adminSecretKey;

    /**
     * 用户登录接口
     */
    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<LoginResultDTO> login(@Valid @RequestBody UserLoginDTO loginDTO) {
        LoginResultDTO result = farmUserService.login(loginDTO);
        return Result.success(result, "登录成功");
    }

    /**
     * 用户注册接口
     */
    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<Long> register(@Valid @RequestBody UserRegisterDTO registerDTO) {
        Long userId = farmUserService.register(registerDTO);
        return Result.success(userId, "注册成功");
    }

    /**
     * 修改密码接口
     */
    @Operation(summary = "修改密码")
    @PostMapping("/{userId}/change-password")
    public Result<String> changePassword(@PathVariable Long userId,
                                         @Valid @RequestBody ChangePasswordDTO changePasswordDTO) {
        farmUserService.changePassword(userId, changePasswordDTO);
        return Result.success(null, "密码修改成功");
    }

    /**
     * 获取当前登录用户信息
     */
    @Operation(summary = "获取当前用户信息")
    @GetMapping("/{userId}/profile")
    public Result<FarmUser> getCurrentUser(@PathVariable Long userId) {
        FarmUser user = farmUserService.getCurrentUser(userId);
        return Result.success(user);
    }

    /**
     * 更新用户信息
     */
    @Operation(summary = "更新用户信息")
    @PutMapping("/{userId}/profile")
    public Result<String> updateUserInfo(@PathVariable Long userId,
                                         @Valid @RequestBody UserUpdateDTO updateDTO) {
        // 确保只能修改自己的信息
        if (!userId.equals(updateDTO.getId())) {
            throw new BusinessException("只能修改自己的信息");
        }
        farmUserService.updateUserInfo(updateDTO);
        return Result.success(null, "信息更新成功");
    }

    // ==================== 管理员接口 ====================

    /**
     * 【管理员】分页查询用户列表
     */
    @Operation(summary = "【管理员】查询用户列表")
    @GetMapping("/admin/users")
    public Result<IPage<FarmUser>> pageUsers(UserQueryDTO queryDTO) {
        IPage<FarmUser> page = farmUserService.pageUsers(queryDTO);
        return Result.success(page);
    }

    /**
     * 【管理员】更新用户信息（可修改角色等）
     */
    @Operation(summary = "【管理员】更新用户信息")
    @PutMapping("/admin/users/{userId}")
    public Result<String> adminUpdateUser(@PathVariable Long userId,
                                          @Valid @RequestBody UserUpdateDTO updateDTO) {
        updateDTO.setId(userId);
        farmUserService.updateUserInfo(updateDTO);
        return Result.success(null, "用户信息更新成功");
    }

    /**
     * 【管理员】禁用用户
     */
    @Operation(summary = "【管理员】禁用用户")
    @PostMapping("/admin/users/{userId}/disable")
    public Result<String> disableUser(@PathVariable Long userId,
                                      @RequestParam Long adminId) {
        farmUserService.disableUser(userId, adminId);
        return Result.success(null, "用户已禁用");
    }

    /**
     * 【管理员】启用用户
     */
    @Operation(summary = "【管理员】启用用户")
    @PostMapping("/admin/users/{userId}/enable")
    public Result<String> enableUser(@PathVariable Long userId,
                                     @RequestParam Long adminId) {
        farmUserService.enableUser(userId, adminId);
        return Result.success(null, "用户已启用");
    }

    /**
     * 【管理员】批量迁移明文密码
     */
    @Operation(summary = "【管理员】批量迁移明文密码")
    @PostMapping("/admin/migrate-passwords")
    public Result<PasswordMigrateResultDTO> migrateAllPasswords(@RequestParam String adminSecret) {
        // 简单的安全校验
        if (!adminSecretKey.equals(adminSecret)) {
            throw new BusinessException("管理员密钥错误");
        }
        PasswordMigrateResultDTO result = farmUserService.migrateAllPasswords();
        return Result.success(result, 
            String.format("密码迁移完成：已迁移 %d 个用户，跳过 %d 个已加密用户", 
                result.getMigratedCount(), result.getSkippedCount()));
    }

    /**
     * 【管理员】检查密码存储状态
     */
    @Operation(summary = "【管理员】检查密码存储状态")
    @GetMapping("/admin/password-status")
    public Result<PasswordStatusResultDTO> checkPasswordStatus(@RequestParam String adminSecret) {
        // 简单的安全校验
        if (!adminSecretKey.equals(adminSecret)) {
            throw new BusinessException("管理员密钥错误");
        }
        PasswordStatusResultDTO result = farmUserService.checkPasswordStatus();
        return Result.success(result);
    }

    /**
     * 检查用户名是否可用
     */
    @Operation(summary = "检查用户名是否可用")
    @GetMapping("/check-username")
    public Result<Boolean> checkUsername(@RequestParam String username) {
        boolean exists = farmUserService.isUsernameExists(username);
        return Result.success(!exists, exists ? "用户名已被使用" : "用户名可用");
    }

    /**
     * 检查邮箱是否可用
     */
    @Operation(summary = "检查邮箱是否可用")
    @GetMapping("/check-email")
    public Result<Boolean> checkEmail(@RequestParam String email) {
        boolean exists = farmUserService.isEmailExists(email);
        return Result.success(!exists, exists ? "邮箱已被使用" : "邮箱可用");
    }
}
