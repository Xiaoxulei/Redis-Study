package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.LoginUser;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @Author: xuxiaolei
 * @Description: TODO:
 * @CreatTime: 2025/08/08 11:46
 **/
@Service
public class UserDetailsServiceImpl implements UserDetailsService {
    @Autowired
    private UserMapper userMapper;
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
            //查询用户
        User user = userMapper
                .selectOne(new LambdaQueryWrapper<User>()
                        .eq(User::getPhone, username));
        if(user == null){
            throw new UsernameNotFoundException("手机号错误");
        }
        List<String> permissions = List.of("user:add", "user:update", "user:delete");

        return new LoginUser(user,permissions);
    }
}
