package com.example.farm.service.impl;

import com.example.farm.entity.FarmUser;
import com.example.farm.mapper.FarmUserMapper;
import com.example.farm.service.FarmUserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 农场用户表 服务实现类
 * </p>
 *
 * @author codexiang
 * @since 2026-03-01
 */
@Service
public class FarmUserServiceImpl extends ServiceImpl<FarmUserMapper, FarmUser> implements FarmUserService {

}
