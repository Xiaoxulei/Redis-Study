package com.hmdp.service.impl;

import ch.qos.logback.core.util.TimeUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.TimeoutUtils;
import org.springframework.stereotype.Service;

import java.nio.file.CopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.号码校验
        if (RegexUtils.isPhoneInvalid(phone)) {
            //2.不符合，返回错误信息
            return Result.fail("手机号格式错误");
        }
        //3.符合，生成验证码
        String code = RandomUtil.randomNumbers(6);
        //4.保存验证码到session,
        //session.setAttribute("code", code);
        //4.保存验证码到redis,
        stringRedisTemplate.opsForValue().set( LOGIN_CODE_KEY + phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //5发送验证码
        log.debug("发送短信验证码成功，验证码：{}",code);
        //返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            //2.不符合，返回错误信息
            return Result.fail("手机号格式错误");
        }
        //2.校验验证码
        //Object cacheCode = session.getAttribute("code");
        //从redis获取验证码并校验
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);

        log.debug("从redis获得的验证码：{}",cacheCode);

        /*if (cacheCode == null || !cacheCode.toString().equals(loginForm.getCode())) {
            //3.不一致，返回错误信息
            return Result.fail("验证码错误");
        }*/
        //
        if (cacheCode == null || !cacheCode.equals(loginForm.getCode())) {
            //3.不一致，返回错误信息
            return Result.fail("验证码错误");

        }
        //4.一致，根据手机号查询用户
        User user = query().eq("phone", phone).one();
        //5.判断用户是否存在
        if (user == null) {
           //6.不存在，创建新用户
           user = creatUserWithPhone(phone);
        }
        //7.保存用户到session
        /*session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));*/
        //TODO:7.保存用户到redis
        //7.1随机生成token，作为登录令牌
        String token = UUID.randomUUID().toString();
        //7.2将User对象转为hash存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userDTOMap = BeanUtil.beanToMap(userDTO);
        // 将 id 字段的值转成字符串（如果存在）
        Object idValue = userDTOMap.get("id");
        if (idValue != null) {
            userDTOMap.put("id", idValue.toString());
        }
        //7.3存储
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token,userDTOMap);
        //7.4设置token有效期
        stringRedisTemplate.expire(LOGIN_USER_KEY + token,LOGIN_USER_TTL, TimeUnit.MINUTES);
        //8.返回token
        return Result.ok(token);
    }

    @Override
    public Result logout(HttpServletRequest request) {
        String token = request.getHeader("authorization");
       /* if(stringRedisTemplate.opsForHash().entries(LOGIN_USER_KEY + token).isEmpty()) {
            return Result.ok();
        }*/
        stringRedisTemplate.delete(LOGIN_USER_KEY + token);
        return Result.ok();
    }

    //创建新用户
    public User creatUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomNumbers(10));
        boolean result = save(user);
        if (result) {
            System.out.println("User saved successfully.");
        } else {
            System.out.println("Failed to save user.");
        }
        return user;
    }
}
