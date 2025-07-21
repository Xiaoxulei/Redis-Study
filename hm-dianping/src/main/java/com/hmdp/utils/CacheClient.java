package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * @Author: xuxiaolei
 * @Description: TODO: cacheClinet
 * @CreatTime: 2025/07/21 16:57
 **/
@Log4j2
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    //将对象序列化为json并存入redis
    public void set(String key, Object value, Long time, TimeUnit timeUnit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }

    //
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit timeUnit ){
        //设置过期时间
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <T,ID> T queryWithPassThrough(String keyPrefix, ID id, Class<T> type, Function<ID,T> dbFallBack,Long time, TimeUnit timeUnit){
        String key = keyPrefix + id;
        //1从redis查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if (StrUtil.isNotBlank(json)) {
            //3.存在直接返回
            return JSONUtil.toBean(json,type);
        }
        if(json == null){
            return null;
        }
        //4.不存在从数据库查询
        T t = dbFallBack.apply(id);
        //5.不存在，返回错误
        if(t == null){
            //将空值写入redis
            stringRedisTemplate.opsForValue().set(key,"",time,timeUnit);
            //返回错误信息
            return null;
        }
        //6.存在，写入redis
        this.set(key,JSONUtil.toJsonStr(t),time,timeUnit);
        //
        return t;
    }

}
