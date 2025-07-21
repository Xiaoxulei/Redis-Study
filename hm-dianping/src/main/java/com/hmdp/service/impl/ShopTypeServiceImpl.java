package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.SHOP_TYPE_KEY;
import static com.hmdp.utils.RedisConstants.SHOP_TYPE_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryAllType() {

        /*List<ShopType> shopTypes = jsonList.stream()
        *        .map(json -> JSONUtil.toBean(json, ShopType.class))
        *        .collect(Collectors.toList());
        * jsonList.stream() 把 List<String> 转成 Stream 流，方便进行函数式操作（如 map/filter 等）。
        * .map(json -> JSONUtil.toBean(json, ShopType.class)) 对每个字符串 json：使用 Hutool 工具类 JSONUtil，把它从 JSON 字符串反序列化成一个 ShopType 对象。
        * JSONUtil.toBean("{\"id\":1,\"name\":\"美食\"}", ShopType.class) // => new ShopType(1L, "美食")
        * 所以这一步把 Stream<String> 转换成 Stream<ShopType>。
        * .collect(Collectors.toList())把处理后的 Stream<ShopType> 收集为 List<ShopType>。
        *  TODO： .collect(Collectors.toList())	Java 8 起  通用写法
        *  TODO: .toList()	Java 16+ 起
        * */
        // 1.先查 Redis 缓存
        List<String> shopTypeList = stringRedisTemplate.opsForList().range(SHOP_TYPE_KEY, 0, -1);
        if (shopTypeList != null && !shopTypeList.isEmpty()) {
            List<ShopType> shopTypesCache = shopTypeList.stream().
                    map(json -> JSONUtil.toBean(json, ShopType.class))
                    .toList();
            return Result.ok(shopTypesCache);
        }
        // 3.缓存未命中，查询数据库
        List<ShopType> shopTypes = lambdaQuery()
                .orderByAsc(ShopType::getSort)
                .list();
       /* List<ShopType> shopTypes = query().orderByAsc("sort").list();*/
        //4.不存在返回错误
        if(shopTypes == null ||shopTypes.isEmpty()){

            return Result.fail("商铺类型不存在");
        }
        //5.存在存入redis
        List<String> toCache = shopTypes.stream()
                .map(JSONUtil::toJsonStr)
                .toList();
        stringRedisTemplate.opsForList().leftPushAll(SHOP_TYPE_KEY, toCache);
        stringRedisTemplate.expire(SHOP_TYPE_KEY,SHOP_TYPE_TTL, TimeUnit.MINUTES);
        //6.返回
        return Result.ok(shopTypes);
    }
}
