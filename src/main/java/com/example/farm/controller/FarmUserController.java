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
 * 用户认证与管理接口。
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
     * 用户登录。
     *
     * @param loginDTO 登录参数
     * @return 登录结果（含 Token）
     * @throws BusinessException 当账号被锁定或凭证错误时抛出
     */
    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<LoginResultDTO> login(@Valid @RequestBody UserLoginDTO loginDTO) {
        return Result.success(farmUserService.login(loginDTO), "登录成功");
    }

    /**
     * 用户注册。
     *
     * @param registerDTO 注册参数
     * @return 新用户 ID
     * @throws BusinessException 当用户名或邮箱已存在时抛出
     */
    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<Long> register(@Valid @RequestBody UserRegisterDTO registerDTO) {
        return Result.success(farmUserService.register(registerDTO), "注册成功");
    }

    /**
     * 修改密码。
     *
     * @param userId 用户 ID
     * @param changePasswordDTO 密码变更参数
     * @return 修改结果
     * @throws BusinessException 当原密码错误或新密码不合法时抛出
     */
    @Operation(summary = "修改密码")
    @PostMapping("/{userId}/change-password")
    public Result<String> changePassword(@PathVariable Long userId,
                                         @Valid @RequestBody ChangePasswordDTO changePasswordDTO) {
        farmUserService.changePassword(userId, changePasswordDTO);
        return Result.success(null, "密码修改成功");
    }

    /**
     * 查询当前用户信息。
     *
     * @param userId 用户 ID
     * @return 用户详情（已脱敏）
     * @throws BusinessException 当用户不存在时抛出
     */
    @Operation(summary = "获取当前用户信息")
    @GetMapping("/{userId}/profile")
    public Result<FarmUser> getCurrentUser(@PathVariable Long userId) {
        return Result.success(farmUserService.getCurrentUser(userId));
    }

    /**
     * 更新用户信息。
     *
     * @param userId 路径用户 ID
     * @param updateDTO 更新参数
     * @return 更新结果
     * @throws BusinessException 当越权修改他人信息时抛出
     */
    @Operation(summary = "更新用户信息")
    @PutMapping("/{userId}/profile")
    public Result<String> updateUserInfo(@PathVariable Long userId,
                                         @Valid @RequestBody UserUpdateDTO updateDTO) {
        if (!userId.equals(updateDTO.getId())) {
            throw new BusinessException("只能修改自己的信息");
        }
        farmUserService.updateUserInfo(updateDTO);
        return Result.success(null, "信息更新成功");
    }

    /**
     * 管理员分页查询用户列表。
     *
     * @param queryDTO 查询参数
     * @return 用户分页数据
     */
    @Operation(summary = "管理员查询用户列表")
    @GetMapping("/admin/users")
    public Result<IPage<FarmUser>> pageUsers(UserQueryDTO queryDTO) {
        return Result.success(farmUserService.pageUsers(queryDTO));
    }

    /**
     * 管理员更新用户信息。
     *
     * @param userId 用户 ID
     * @param updateDTO 更新参数
     * @return 更新结果
     * @throws BusinessException 当用户不存在时抛出
     */
    @Operation(summary = "管理员更新用户信息")
    @PutMapping("/admin/users/{userId}")
    public Result<String> adminUpdateUser(@PathVariable Long userId,
                                          @Valid @RequestBody UserUpdateDTO updateDTO) {
        updateDTO.setId(userId);
        farmUserService.updateUserInfo(updateDTO);
        return Result.success(null, "用户信息更新成功");
    }

    /**
     * 管理员禁用用户。
     *
     * @param userId 用户 ID
     * @param adminId 管理员 ID
     * @return 禁用结果
     * @throws BusinessException 当用户不存在或试图禁用自己时抛出
     */
    @Operation(summary = "管理员禁用用户")
    @PostMapping("/admin/users/{userId}/disable")
    public Result<String> disableUser(@PathVariable Long userId, @RequestParam Long adminId) {
        farmUserService.disableUser(userId, adminId);
        return Result.success(null, "用户已禁用");
    }

    /**
     * 管理员启用用户。
     *
     * @param userId 用户 ID
     * @param adminId 管理员 ID
     * @return 启用结果
     * @throws BusinessException 当用户不存在时抛出
     */
    @Operation(summary = "管理员启用用户")
    @PostMapping("/admin/users/{userId}/enable")
    public Result<String> enableUser(@PathVariable Long userId, @RequestParam Long adminId) {
        farmUserService.enableUser(userId, adminId);
        return Result.success(null, "用户已启用");
    }

    /**
     * 管理员批量迁移明文密码。
     *
     * @param adminSecret 管理员密钥
     * @return 迁移统计结果
     * @throws BusinessException 当管理员密钥错误时抛出
     */
    @Operation(summary = "管理员批量迁移明文密码")
    @PostMapping("/admin/migrate-passwords")
    public Result<PasswordMigrateResultDTO> migrateAllPasswords(@RequestParam String adminSecret) {
        validateAdminSecret(adminSecret);
        PasswordMigrateResultDTO result = farmUserService.migrateAllPasswords();
        return Result.success(result,
                String.format("密码迁移完成：已迁移 %d 个，跳过 %d 个", result.getMigratedCount(), result.getSkippedCount()));
    }

    /**
     * 管理员查询密码存储状态。
     *
     * @param adminSecret 管理员密钥
     * @return 状态统计结果
     * @throws BusinessException 当管理员密钥错误时抛出
     */
    @Operation(summary = "管理员检查密码存储状态")
    @GetMapping("/admin/password-status")
    public Result<PasswordStatusResultDTO> checkPasswordStatus(@RequestParam String adminSecret) {
        validateAdminSecret(adminSecret);
        return Result.success(farmUserService.checkPasswordStatus());
    }

    /**
     * 检查用户名是否可用。
     *
     * @param username 用户名
     * @return 可用性结果
     */
    @Operation(summary = "检查用户名是否可用")
    @GetMapping("/check-username")
    public Result<Boolean> checkUsername(@RequestParam String username) {
        boolean exists = farmUserService.isUsernameExists(username);
        return Result.success(!exists, exists ? "用户名已被使用" : "用户名可用");
    }

    /**
     * 检查邮箱是否可用。
     *
     * @param email 邮箱
     * @return 可用性结果
     */
    @Operation(summary = "检查邮箱是否可用")
    @GetMapping("/check-email")
    public Result<Boolean> checkEmail(@RequestParam String email) {
        boolean exists = farmUserService.isEmailExists(email);
        return Result.success(!exists, exists ? "邮箱已被使用" : "邮箱可用");
    }

    /**
     * 管理员密钥校验。
     *
     * @param adminSecret 管理员密钥
     * @throws BusinessException 当密钥不匹配时抛出
     */
    private void validateAdminSecret(String adminSecret) {
        if (!adminSecretKey.equals(adminSecret)) {
            throw new BusinessException("管理员密钥错误");
        }
    }
}