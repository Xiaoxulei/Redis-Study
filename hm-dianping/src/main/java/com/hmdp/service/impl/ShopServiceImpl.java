package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Resource
    private CacheClient cacheClient;

    //先从redis查询shop信息，如果redis没有，再从mysql查询并存入redis在返回数据
    @Override
    public Result getShopById(Long id) {
        //TODO:1.缓存穿透
        //Shop shop = queryWithPassThrough(id);
        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY,id,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);
        //TODO:互斥锁解决缓存击穿
        /*Shop shop = queryWithPassMutex(id);
        if (shop == null) {
            Result.fail("店铺不存在");
        }*/
        //TODO:逻辑过期时间
        /*Shop shop = queryWithLogicalExpire(id);*/
        return Result.ok(shop);

    }

    //TODO:防止缓存穿透
    //1.互斥锁
    public Shop queryWithPassMutex(Long id) {
        //1.先查询redis
        //存在返回数据
        String cacheShop = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        if (StrUtil.isNotBlank(cacheShop)) {
            return JSONUtil.toBean(cacheShop, Shop.class);
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

        Shop shop = null;
        try {
            boolean isLock = getLock(LOCK_SHOP_KEY + id);
            if(!isLock){
                Thread.sleep(500);
                return queryWithPassMutex(id);

            }
            //2.redis不存在，从mysql查询
            shop = getById(id);Thread.sleep(200);

            //查询redis是不是空数据，如果是，返回空数据
            if (shop == null) {
                stringRedisTemplate.opsForValue()
                        .set(CACHE_SHOP_KEY + id, "",CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //3.存入redis
            stringRedisTemplate.opsForValue()
                    .set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
            delLock(LOCK_SHOP_KEY + id);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            delLock(LOCK_SHOP_KEY + id);
        }
        //返回
        return shop;
    }
    //2.逻辑过期
    public Shop queryWithLogicalExpire(Long id) {
        // 1. 先查 Redis 缓存
        String shopCache = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

        // 2. 判断是否存在缓存
        if (StrUtil.isBlank(shopCache)) {
            // 缓存不存在，返回 null（实际项目中可返回错误或空数据）
            return null;
        }

        // 3. 缓存命中，反序列化为 RedisData
        RedisData redisData = JSONUtil.toBean(shopCache, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        //parseObj(...) 会自动将 Object 转为 JSONObject；
        /*Shop shop = JSONUtil.toBean(JSONUtil.parseObj(redisData.getData()), Shop.class);*/
        LocalDateTime expireTime = redisData.getExpireTime();

        // 4. 判断缓存是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 未过期，直接返回缓存中的数据
            return shop;
        }
        // 5. 过期，尝试获取互斥锁（防止多个线程同时重建缓存）
        boolean isLock = getLock(LOCK_SHOP_KEY + id);
        if (isLock) {
            // 获取锁成功，开启独立线程重建缓存
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    this.saveShop2Redis(id, 20L);  // 从数据库查询并重新写入 Redis，有效期 20 秒
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    delLock(LOCK_SHOP_KEY + id);
                }
            });
        }
        // 6. 无论是否获取锁，先返回旧的缓存数据
        return shop;
    }
    public void saveShop2Redis(Long id,Long expireTime) throws InterruptedException {
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

    }
    //TODO:防止缓存穿透
    public Shop queryWithPassThrough(Long id) {
        //1.先查询redis
        //存在返回数据
        String cacheShop = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        if (StrUtil.isNotBlank(cacheShop)) {
            return JSONUtil.toBean(cacheShop, Shop.class);
        }
        if(cacheShop != null){
            return null;
        }
        //2.redis不存在，从mysql查询
        Shop shopById = getById(id);
        //查询redis是不是空数据，如果是，返回空数据
        if (shopById == null) {
            stringRedisTemplate.opsForValue()
                    .set(CACHE_SHOP_KEY + id, "",CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //3.存入redis
        stringRedisTemplate.opsForValue()
                .set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shopById),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //返回
        return shopById;
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

    @Override
    public Result queryById(Long id) {
        //1.从redis查询商铺缓存
        String cacheShop = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //2.查询redis是否存在
        if(StrUtil.isNotBlank(cacheShop)){
            //3.存在直接返回
            Shop shop = JSONUtil.toBean(cacheShop, Shop.class, false);
            return Result.ok(shop);
        }
        //4.不存在从mysql查询
        Shop shop = getById(id);
        //5.不存在返回错误
        if(shop == null){
            return Result.fail("商铺不存在");
        }
        //6.存在写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //7.返回数据
        return Result.ok(shop);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        //1.修改数据库
        Long id = shop.getId();
        if(id == null){
            return Result.fail("店铺id不能为空");
        }
        update(shop);
        //2.删除redis缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
