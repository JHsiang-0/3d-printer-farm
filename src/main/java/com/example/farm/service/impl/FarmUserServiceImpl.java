package com.example.farm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.farm.common.exception.BusinessException;
import com.example.farm.common.utils.JwtUtils;
import com.example.farm.common.utils.LoginProtectUtil;
import com.example.farm.common.utils.PasswordMigrationUtil;
import com.example.farm.entity.FarmUser;
import com.example.farm.entity.dto.*;
import com.example.farm.mapper.FarmUserMapper;
import com.example.farm.service.FarmUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <p>
 * 农场用户表 服务实现类
 * </p>
 *
 * @author codexiang
 * @since 2026-03-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FarmUserServiceImpl extends ServiceImpl<FarmUserMapper, FarmUser> implements FarmUserService {

    private final PasswordEncoder passwordEncoder;
    private final FarmUserMapper farmUserMapper;
    private final LoginProtectUtil loginProtectUtil;

    @Value("${jwt.expire-time:604800000}")
    private Long jwtExpireTime;

    @Override
    @Transactional
    public LoginResultDTO login(UserLoginDTO loginDTO) {
        String username = loginDTO.getUsername();
        
        // 1. 检查账号是否被锁定（防暴力破解）
        if (loginProtectUtil.isLocked(username)) {
            long remainingMinutes = loginProtectUtil.getRemainingLockTime(username);
            throw new BusinessException("账号已锁定，请 " + remainingMinutes + " 分钟后重试");
        }
        
        // 2. 查询用户
        FarmUser user = findByUsername(username);
        if (user == null) {
            // 记录失败次数
            loginProtectUtil.recordLoginFail(username);
            throw new BusinessException("账号或密码错误");
        }

        // 3. 校验密码（支持明文自动迁移）
        PasswordMigrationUtil.MigrateResult result = PasswordMigrationUtil.matchesAndMigrate(
                loginDTO.getPassword(),
                user.getPasswordHash()
        );

        if (!result.isMatches()) {
            // 记录失败次数
            int failCount = loginProtectUtil.recordLoginFail(username);
            int remainingAttempts = 5 - failCount;
            if (remainingAttempts > 0) {
                throw new BusinessException("账号或密码错误，还剩 " + remainingAttempts + " 次机会");
            } else {
                throw new BusinessException("账号已锁定，请 15 分钟后重试");
            }
        }
        
        // 登录成功，清除失败记录
        loginProtectUtil.clearLoginFail(username);

        // 3. 如果需要迁移（明文->密文），自动更新数据库
        if (result.needMigration()) {
            user.setPasswordHash(result.getNewHash());
            updateById(user);
            log.info("用户 [{}] 的密码已自动迁移为加密存储", user.getUsername());
        }

        // 4. 生成 Token
        String token = JwtUtils.generateToken(user.getId(), user.getUsername(), user.getRole());

        // 5. 构造返回结果
        LoginResultDTO resultDTO = new LoginResultDTO(
                token,
                jwtExpireTime / 1000, // 转换为秒
                user.getId(),
                user.getUsername(),
                user.getRole()
        );
        resultDTO.setEmail(user.getEmail());
        resultDTO.setPhone(user.getPhone());

        log.info("用户 [{}] 登录成功", user.getUsername());
        return resultDTO;
    }

    @Override
    @Transactional
    public Long register(UserRegisterDTO registerDTO) {
        // 1. 校验密码和确认密码是否一致
        if (!registerDTO.isPasswordMatch()) {
            throw new BusinessException("两次输入的密码不一致");
        }

        // 2. 检查用户名是否已存在
        if (isUsernameExists(registerDTO.getUsername())) {
            throw new BusinessException("用户名已被注册");
        }

        // 3. 检查邮箱是否已存在（如果提供了邮箱）
        if (StringUtils.isNotBlank(registerDTO.getEmail()) && isEmailExists(registerDTO.getEmail())) {
            throw new BusinessException("邮箱已被注册");
        }

        // 4. 创建新用户
        FarmUser newUser = new FarmUser();
        newUser.setUsername(registerDTO.getUsername());
        newUser.setPasswordHash(passwordEncoder.encode(registerDTO.getPassword()));
        newUser.setEmail(registerDTO.getEmail());
        newUser.setPhone(registerDTO.getPhone());
        newUser.setRole("OPERATOR"); // 默认角色为操作员

        // 5. 保存到数据库
        save(newUser);

        log.info("用户 [{}] 注册成功，ID: {}", newUser.getUsername(), newUser.getId());
        return newUser.getId();
    }

    @Override
    @Transactional
    public void changePassword(Long userId, ChangePasswordDTO changePasswordDTO) {
        // 1. 校验新密码和确认密码是否一致
        if (!changePasswordDTO.isNewPasswordMatch()) {
            throw new BusinessException("两次输入的新密码不一致");
        }

        // 2. 查询用户
        FarmUser user = getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // 3. 验证旧密码
        boolean matches;
        if (PasswordMigrationUtil.isEncrypted(user.getPasswordHash())) {
            matches = passwordEncoder.matches(changePasswordDTO.getOldPassword(), user.getPasswordHash());
        } else {
            matches = changePasswordDTO.getOldPassword().equals(user.getPasswordHash());
        }

        if (!matches) {
            throw new BusinessException("原密码错误");
        }

        // 4. 检查新密码是否与旧密码相同
        if (passwordEncoder.matches(changePasswordDTO.getNewPassword(), user.getPasswordHash())) {
            throw new BusinessException("新密码不能与旧密码相同");
        }

        // 5. 更新密码
        user.setPasswordHash(passwordEncoder.encode(changePasswordDTO.getNewPassword()));
        updateById(user);

        log.info("用户 [{}] 修改密码成功", user.getUsername());
    }

    @Override
    @Transactional
    public void updateUserInfo(UserUpdateDTO updateDTO) {
        // 1. 查询用户
        FarmUser user = getById(updateDTO.getId());
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // 2. 检查邮箱是否已被其他用户使用
        if (StringUtils.isNotBlank(updateDTO.getEmail()) && !updateDTO.getEmail().equals(user.getEmail())) {
            if (isEmailExists(updateDTO.getEmail())) {
                throw new BusinessException("邮箱已被其他用户使用");
            }
            user.setEmail(updateDTO.getEmail());
        }

        // 3. 更新信息
        if (StringUtils.isNotBlank(updateDTO.getPhone())) {
            user.setPhone(updateDTO.getPhone());
        }
        if (StringUtils.isNotBlank(updateDTO.getRole())) {
            user.setRole(updateDTO.getRole());
        }

        updateById(user);
        log.info("用户 [{}] 信息更新成功", user.getUsername());
    }

    @Override
    public FarmUser getCurrentUser(Long userId) {
        FarmUser user = getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        // 清除敏感信息
        user.setPasswordHash(null);
        return user;
    }

    @Override
    public IPage<FarmUser> pageUsers(UserQueryDTO queryDTO) {
        Page<FarmUser> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        LambdaQueryWrapper<FarmUser> wrapper = new LambdaQueryWrapper<>();

        // 用户名模糊查询
        if (StringUtils.isNotBlank(queryDTO.getUsername())) {
            wrapper.like(FarmUser::getUsername, queryDTO.getUsername());
        }

        // 角色精确查询
        if (StringUtils.isNotBlank(queryDTO.getRole())) {
            wrapper.eq(FarmUser::getRole, queryDTO.getRole());
        }

        // 邮箱模糊查询
        if (StringUtils.isNotBlank(queryDTO.getEmail())) {
            wrapper.like(FarmUser::getEmail, queryDTO.getEmail());
        }

        // 按创建时间倒序
        wrapper.orderByDesc(FarmUser::getCreatedAt);

        return page(page, wrapper);
    }

    @Override
    @Transactional
    public void disableUser(Long userId, Long adminId) {
        FarmUser user = getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // 不能禁用自己
        if (userId.equals(adminId)) {
            throw new BusinessException("不能禁用当前登录账号");
        }

        // TODO: 添加用户状态字段实现禁用功能
        // user.setStatus("DISABLED");
        // updateById(user);

        log.info("用户 [{}] 已被管理员 [{}] 禁用", user.getUsername(), adminId);
    }

    @Override
    @Transactional
    public void enableUser(Long userId, Long adminId) {
        FarmUser user = getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // TODO: 添加用户状态字段实现启用功能
        // user.setStatus("ENABLED");
        // updateById(user);

        log.info("用户 [{}] 已被管理员 [{}] 启用", user.getUsername(), adminId);
    }

    @Override
    public boolean isUsernameExists(String username) {
        LambdaQueryWrapper<FarmUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FarmUser::getUsername, username);
        return count(wrapper) > 0;
    }

    @Override
    public boolean isEmailExists(String email) {
        if (StringUtils.isBlank(email)) {
            return false;
        }
        LambdaQueryWrapper<FarmUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FarmUser::getEmail, email);
        return count(wrapper) > 0;
    }

    @Override
    @Transactional
    public PasswordMigrateResultDTO migrateAllPasswords() {
        int migratedCount = 0;
        int skippedCount = 0;

        for (FarmUser user : list()) {
            String currentHash = user.getPasswordHash();

            // 跳过已加密的
            if (PasswordMigrationUtil.isEncrypted(currentHash)) {
                skippedCount++;
                continue;
            }

            // 加密明文密码
            if (currentHash != null && !currentHash.isEmpty()) {
                user.setPasswordHash(passwordEncoder.encode(currentHash));
                updateById(user);
                migratedCount++;
                log.info("用户 [{}] 的密码已迁移", user.getUsername());
            }
        }

        return new PasswordMigrateResultDTO(migratedCount, skippedCount, migratedCount + skippedCount);
    }

    @Override
    public PasswordStatusResultDTO checkPasswordStatus() {
        int encryptedCount = 0;
        int plainCount = 0;

        for (FarmUser user : list()) {
            if (PasswordMigrationUtil.isEncrypted(user.getPasswordHash())) {
                encryptedCount++;
            } else {
                plainCount++;
            }
        }

        return new PasswordStatusResultDTO(encryptedCount, plainCount, encryptedCount + plainCount);
    }

    /**
     * 根据用户名查找用户
     */
    private FarmUser findByUsername(String username) {
        LambdaQueryWrapper<FarmUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FarmUser::getUsername, username);
        return getOne(wrapper);
    }
}
