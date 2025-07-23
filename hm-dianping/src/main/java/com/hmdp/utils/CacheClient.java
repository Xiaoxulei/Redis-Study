package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;

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

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

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

    public <T,ID> T queryWithPassThrough(
            String keyPrefix,
            ID id,
            Class<T> type,
            Function<ID,T> dbFallBack,
            Long time,
            TimeUnit timeUnit){
        String key = keyPrefix + id;
        //1从redis查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if (StrUtil.isNotBlank(json)) {
            //3.存在直接返回
            return JSONUtil.toBean(json,type);
        }
        if(json != null){
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

    //1.互斥锁
    public <T,ID> T queryWithPassMutex(String keyPrefix ,
                                    ID id,
                                    Class<T> type,
                                    Long time,
                                    Function<ID,T> dbFallBack,
                                    TimeUnit timeUnit) {
        //1.先查询redis
        //存在返回数据
        String cacheShop = stringRedisTemplate.opsForValue().get(keyPrefix + id);
        if (StrUtil.isNotBlank(cacheShop)) {
            return JSONUtil.toBean(cacheShop, type);
        }
        if(cacheShop != null){
            return null;
        }

        /*实现缓存重建
         * 1.获取互斥锁
         * 2.判断时候获取成功
         * 3.获取失败休眠重试
         * 4.成功根据Id查询数据库
         * 5.删除锁
         * */

        T t = null;
        try {
            boolean isLock = getLock(LOCK_SHOP_KEY + id);
            if(!isLock){
                Thread.sleep(500);
                return queryWithPassMutex(keyPrefix,id,type,time,dbFallBack,timeUnit);

            }
            //2.redis不存在，从mysql查询
            t = dbFallBack.apply(id);
            Thread.sleep(200);

            //查询redis是不是空数据，如果是，返回空数据
            if (t == null) {
                stringRedisTemplate.opsForValue()
                        .set(keyPrefix + id, "",time, timeUnit);
                return null;
            }
            //3.存入redis
            stringRedisTemplate.opsForValue()
                    .set(keyPrefix + id, JSONUtil.toJsonStr(t),time, timeUnit);
            delLock(keyPrefix + id);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            delLock(keyPrefix + id);
        }
        //返回
        return t;
    }

    //2.逻辑过期
    public <T,ID> T queryWithLogicalExpire(
            String keyPrefix ,
            ID id,
            Class<T> type,
            Function<ID,T> dbFallBack,
            Long time,
            TimeUnit timeUnit) {
        // 1. 先查 Redis 缓存
        String key = keyPrefix + id;
        String shopCache = stringRedisTemplate.opsForValue().get(key);

        // 2. 判断是否存在缓存
        if (StrUtil.isBlank(shopCache)) {
            // 缓存不存在，返回 null（实际项目中可返回错误或空数据）
            return null;
        }

        // 3. 缓存命中，反序列化为 RedisData
        RedisData redisData = JSONUtil.toBean(shopCache, RedisData.class);
        T t = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        //parseObj(...) 会自动将 Object 转为 JSONObject；
        /*Shop shop = JSONUtil.toBean(JSONUtil.parseObj(redisData.getData()), Shop.class);*/
        LocalDateTime expireTime = redisData.getExpireTime();

        // 4. 判断缓存是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 未过期，直接返回缓存中的数据
            log.info("数据未过期，返回数据");
            return t;
        }
        // 5. 过期，尝试获取互斥锁（防止多个线程同时重建缓存）
        String lockKey = "lock:" + key;
        boolean isLock = getLock(lockKey);
        if (isLock) {
            log.info("获取锁成功，开始重建缓存...");
            // 获取锁成功，开启独立线程重建缓存
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    /*this.saveShop2Redis(id, 20L); */ // 从数据库查询并重新写入 Redis，有效期 20 秒
                    //1.查询数据库
                    log.info("查询数据库");
                    T t1 = dbFallBack.apply(id);
                    //2.写入redis
                    this.setWithLogicalExpire(key,t1,time,timeUnit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    log.info("释放锁");
                    delLock(lockKey);
                }
            });
        }
        // 6. 无论是否获取锁，先返回旧的缓存数据
        return t;
    }

    //获取锁
    private boolean getLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    //删除锁
    private void delLock(String key){
        stringRedisTemplate.delete(key);
    }

    /*public void saveShop2Redis(Long id,Long expireTime) throws InterruptedException {
        //1.查询店铺信息
        Shop shop = getById(id);
        Thread.sleep(200);
        //2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireTime));
        //3.写入redis
        stringRedisTemplate.opsForValue()
                .set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));

    }*/
}
