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
 * 用户服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FarmUserServiceImpl extends ServiceImpl<FarmUserMapper, FarmUser> implements FarmUserService {

    private final PasswordEncoder passwordEncoder;
    private final LoginProtectUtil loginProtectUtil;

    @Value("${jwt.expire-time:604800000}")
    private Long jwtExpireTime;

    @Override
    @Transactional
    public LoginResultDTO login(UserLoginDTO loginDTO) {
        String username = loginDTO.getUsername();

        // 先拦截已锁定账号，避免继续参与密码校验，减轻数据库与加密计算压力。
        if (loginProtectUtil.isLocked(username)) {
            long remainingMinutes = loginProtectUtil.getRemainingLockTime(username);
            log.warn("登录被拒绝：账号已锁定，username={}, 剩余锁定时长={}分钟", username, remainingMinutes);
            throw new BusinessException("账号已锁定，请 " + remainingMinutes + " 分钟后重试");
        }

        FarmUser user = findByUsername(username);
        if (user == null) {
            loginProtectUtil.recordLoginFail(username);
            log.warn("登录失败：用户不存在或凭证错误，username={}", username);
            throw new BusinessException("账号或密码错误");
        }

        PasswordMigrationUtil.MigrateResult verifyResult = PasswordMigrationUtil.matchesAndMigrate(
                loginDTO.getPassword(), user.getPasswordHash());

        if (!verifyResult.isMatches()) {
            int failCount = loginProtectUtil.recordLoginFail(username);
            int remainingAttempts = 5 - failCount;
            log.warn("登录失败：密码错误，username={}, 剩余尝试次数={}", username, Math.max(remainingAttempts, 0));
            if (remainingAttempts > 0) {
                throw new BusinessException("账号或密码错误，还剩 " + remainingAttempts + " 次机会");
            }
            throw new BusinessException("账号已锁定，请 15 分钟后重试");
        }

        loginProtectUtil.clearLoginFail(username);

        // 登录成功时顺带迁移旧明文密码，能在不打断用户流程的前提下逐步完成存量治理。
        if (verifyResult.needMigration()) {
            user.setPasswordHash(verifyResult.getNewHash());
            updateById(user);
            log.info("用户 [{}] 的密码已自动迁移为加密存储", user.getUsername());
        }

        String token = JwtUtils.generateToken(user.getId(), user.getUsername(), user.getRole());
        LoginResultDTO result = new LoginResultDTO(
                token,
                jwtExpireTime / 1000,
                user.getId(),
                user.getUsername(),
                user.getRole()
        );
        result.setEmail(user.getEmail());
        result.setPhone(user.getPhone());

        log.info("用户 [{}] 登录成功", user.getUsername());
        return result;
    }

    @Override
    @Transactional
    public Long register(UserRegisterDTO registerDTO) {
        if (!registerDTO.isPasswordMatch()) {
            throw new BusinessException("两次输入的密码不一致");
        }
        if (isUsernameExists(registerDTO.getUsername())) {
            throw new BusinessException("用户名已被注册");
        }
        if (StringUtils.isNotBlank(registerDTO.getEmail()) && isEmailExists(registerDTO.getEmail())) {
            throw new BusinessException("邮箱已被注册");
        }

        FarmUser newUser = new FarmUser();
        newUser.setUsername(registerDTO.getUsername());
        newUser.setPasswordHash(passwordEncoder.encode(registerDTO.getPassword()));
        newUser.setEmail(registerDTO.getEmail());
        newUser.setPhone(registerDTO.getPhone());
        newUser.setRole("OPERATOR");

        save(newUser);
        log.info("用户 [{}] 注册成功，ID: {}", newUser.getUsername(), newUser.getId());
        return newUser.getId();
    }

    @Override
    @Transactional
    public void changePassword(Long userId, ChangePasswordDTO changePasswordDTO) {
        if (!changePasswordDTO.isNewPasswordMatch()) {
            throw new BusinessException("两次输入的新密码不一致");
        }

        FarmUser user = getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        boolean matches;
        if (PasswordMigrationUtil.isEncrypted(user.getPasswordHash())) {
            matches = passwordEncoder.matches(changePasswordDTO.getOldPassword(), user.getPasswordHash());
        } else {
            matches = changePasswordDTO.getOldPassword().equals(user.getPasswordHash());
        }

        if (!matches) {
            throw new BusinessException("原密码错误");
        }
        if (passwordEncoder.matches(changePasswordDTO.getNewPassword(), user.getPasswordHash())) {
            throw new BusinessException("新密码不能与旧密码相同");
        }

        user.setPasswordHash(passwordEncoder.encode(changePasswordDTO.getNewPassword()));
        updateById(user);
        log.info("用户 [{}] 修改密码成功", user.getUsername());
    }

    @Override
    @Transactional
    public void updateUserInfo(UserUpdateDTO updateDTO) {
        FarmUser user = getById(updateDTO.getId());
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // 邮箱变更需要先做唯一性校验，避免数据库出现多账号共享邮箱，影响找回与审计。
        if (StringUtils.isNotBlank(updateDTO.getEmail()) && !updateDTO.getEmail().equals(user.getEmail())) {
            if (isEmailExists(updateDTO.getEmail())) {
                throw new BusinessException("邮箱已被其他用户使用");
            }
            user.setEmail(updateDTO.getEmail());
        }

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
        user.setPasswordHash(null);
        return user;
    }

    @Override
    public IPage<FarmUser> pageUsers(UserQueryDTO queryDTO) {
        Page<FarmUser> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        LambdaQueryWrapper<FarmUser> wrapper = new LambdaQueryWrapper<>();

        // 仅在前端传了筛选条件时拼接 where，保证一个接口兼容“全量列表”和“条件检索”。
        if (StringUtils.isNotBlank(queryDTO.getUsername())) {
            wrapper.like(FarmUser::getUsername, queryDTO.getUsername());
        }
        if (StringUtils.isNotBlank(queryDTO.getRole())) {
            wrapper.eq(FarmUser::getRole, queryDTO.getRole());
        }
        if (StringUtils.isNotBlank(queryDTO.getEmail())) {
            wrapper.like(FarmUser::getEmail, queryDTO.getEmail());
        }

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
        if (userId.equals(adminId)) {
            throw new BusinessException("不能禁用当前登录账号");
        }
        log.info("用户 [{}] 已被管理员 [{}] 禁用", user.getUsername(), adminId);
    }

    @Override
    @Transactional
    public void enableUser(Long userId, Long adminId) {
        FarmUser user = getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
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
            if (PasswordMigrationUtil.isEncrypted(currentHash)) {
                skippedCount++;
                continue;
            }

            // 仅迁移非空明文，避免把脏数据写成“合法加密值”掩盖数据质量问题。
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
     * 根据用户名查询用户。
     */
    private FarmUser findByUsername(String username) {
        LambdaQueryWrapper<FarmUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FarmUser::getUsername, username);
        return getOne(wrapper);
    }
}