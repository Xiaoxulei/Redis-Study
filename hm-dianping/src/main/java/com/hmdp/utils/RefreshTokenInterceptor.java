package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * @Program: hm-dianping
 * @Description: TODO: LoginInterceptor
 * @Author: XuXiaoLei
 * @Create: 2025/07/18 22:35
 **/
public class RefreshTokenInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) throws Exception {
        //1.获取session
        //HttpSession session = request.getSession();
        //TODO:1.获取请求头中的token
        String token = request.getHeader("authorization");
        if(token == null){
           return true;
        }
        //TODO:2.基于TOKEN获取redis中的用户
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(LOGIN_USER_KEY + token);
        //2.获取session中的用户
        //Object user = session.getAttribute("user");
        //3.判断用户是否存在
        //TODO:3.判断用户是否存在
        if(userMap.isEmpty()){
            return true;
        }
        //TODO:4.将查询到的hash数据转化为userDTO
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), true);
        //TODO:5.保存用户信息到ThreadLocal
        UserHolder.saveUser(userDTO);
        //TODO:6.刷新token有效期
        stringRedisTemplate.expire(LOGIN_USER_KEY+token,LOGIN_USER_TTL, TimeUnit.MINUTES);
        //6.放行
        return true;

    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }

    //将USer转化为UserDTO
    /*private UserDTO convertToDTO(User user) {
        if (user == null) return null;
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setNickName(user.getNickName());
        dto.setIcon(user.getIcon());
        return dto;
    }*/
}
