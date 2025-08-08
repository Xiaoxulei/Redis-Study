package com.hmdp.filter;

import com.hmdp.dto.LoginUser;
import com.hmdp.utils.JwtUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY_PREFIX;

/**
 * @Author: xuxiaolei
 * @Description: TODO:
 * @CreatTime: 2025/08/08 11:55
 **/
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    @Autowired
    private JwtUtil jwtUtil;

    @Resource
    private RedisTemplate<String,Object> redisTemplate;
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
            try{
                // 1. 获取请求头中的 Authorization
                String token = request.getHeader("Authorization");
                // 2. 检查 token 是否存在且格式正确
                if(StringUtils.hasText(token) && token.startsWith("Bearer ")){
                    // 3. 去掉前缀，获取实际 token
                    token = token.substring(7);
                    // 4. 从 token 中解析出用户Id（或用户名）
                    String userId = jwtUtil.extractUserId(token);
                    if(userId != null){
                        //查询redis中的用户信息
                        String redisKey = LOGIN_USER_KEY_PREFIX + userId;
                        Object cacheUser = redisTemplate.opsForValue().get(redisKey);
                        if(cacheUser instanceof LoginUser loginUser ){
                            // 6. 构造 Authentication 并注入上下文
                            UsernamePasswordAuthenticationToken authenticationToken =
                                    new UsernamePasswordAuthenticationToken(
                                            loginUser,
                                            null,
                                            loginUser.getAuthorities());
                            //
                            //刷新redis用户信息时间
                            redisTemplate.expire(redisKey, Duration.ofMinutes(10));
                            SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                        }else {
                            // Redis 中没有用户信息（可能未登录或已被登出）
                            // 此处可记录日志，也可不处理，走匿名流程
                            System.out.printf("\"用户未登录或登录信息已过期：\" "+ userId);
                        }

                    }
                }
            }catch(Exception e){
                // JWT 解析失败或 Redis 查询异常，记录日志但不抛出异常
                System.err.println("JWT 校验失败: " + e.getMessage());
                // 也可以选择响应 401，但通常交给全局异常或权限拦截处理器统一处理
            }
            // 继续处理后续过滤器链（不管有没有登录）
            filterChain.doFilter(request, response);

    }
}
