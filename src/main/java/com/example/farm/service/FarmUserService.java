package com.example.farm.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.farm.entity.FarmUser;
import com.example.farm.entity.dto.*;

/**
 * <p>
 * 农场用户表 服务类
 * </p>
 *
 * @author codexiang
 * @since 2026-03-01
 */
public interface FarmUserService extends IService<FarmUser> {

    /**
     * 用户登录
     *
     * @param loginDTO 登录参数
     * @return 登录结果（包含 Token）
     */
    LoginResultDTO login(UserLoginDTO loginDTO);

    /**
     * 用户注册
     *
     * @param registerDTO 注册参数
     * @return 用户ID
     */
    Long register(UserRegisterDTO registerDTO);

    /**
     * 修改密码
     *
     * @param userId 用户ID
     * @param changePasswordDTO 密码修改参数
     */
    void changePassword(Long userId, ChangePasswordDTO changePasswordDTO);

    /**
     * 更新用户信息
     *
     * @param updateDTO 更新参数
     */
    void updateUserInfo(UserUpdateDTO updateDTO);

    /**
     * 获取当前登录用户信息
     *
     * @param userId 用户ID
     * @return 用户信息
     */
    FarmUser getCurrentUser(Long userId);

    /**
     * 分页查询用户列表
     *
     * @param queryDTO 查询参数
     * @return 用户分页列表
     */
    IPage<FarmUser> pageUsers(UserQueryDTO queryDTO);

    /**
     * 禁用用户账号
     *
     * @param userId 用户ID
     * @param adminId 操作管理员ID
     */
    void disableUser(Long userId, Long adminId);

    /**
     * 启用用户账号
     *
     * @param userId 用户ID
     * @param adminId 操作管理员ID
     */
    void enableUser(Long userId, Long adminId);

    /**
     * 检查用户名是否已存在
     *
     * @param username 用户名
     * @return true-已存在
     */
    boolean isUsernameExists(String username);

    /**
     * 检查邮箱是否已存在
     *
     * @param email 邮箱
     * @return true-已存在
     */
    boolean isEmailExists(String email);

    /**
     * 【管理员】批量迁移明文密码为加密存储
     *
     * @return 迁移结果统计
     */
    PasswordMigrateResultDTO migrateAllPasswords();

    /**
     * 【管理员】检查密码存储状态
     *
     * @return 密码存储状态统计
     */
    PasswordStatusResultDTO checkPasswordStatus();
}
