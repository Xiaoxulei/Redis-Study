package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.User;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static com.hmdp.utils.RedisConstants.LOCK_KEY_PREFIX;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@EnableAspectJAutoProxy(exposeProxy = true)
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private StringRedisTemplate  stringRedisTemplate;

    @Resource
    private RedisIdWorker  redisIdWorker;
    @Autowired
    private VoucherOrderMapper voucherOrderMapper;

    @Transactional
    @Override
    public Result seckillVoucher(Long voucherId) {
        //1.查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //2.判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            //尚未开始
            return Result.fail("秒杀尚未开始!");
        }

        //3.判断是否结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已结束!");
        }
        //4.判断库存是否充足
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足!");
        }
        Long userId = UserHolder.getUser().getId();
        //1.创建锁对象
        SimpleRedisLock lock = new SimpleRedisLock("order" + userId, stringRedisTemplate);
        //2.获取锁
        boolean isLock = lock.tryLock(1200);
        if (!isLock) {
            //获取锁失败
            return Result.fail("不允许重复下单");
        }
        try {
            //获取代理对对象(事务)
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.getResult(voucherId, voucher); // 通过代理对象调用，增强逻辑生效
        } catch (IllegalStateException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();

        }

    }
    @Transactional
    @Override
    public  Result getResult(Long voucherId, SeckillVoucher voucher) {
        //判断用户是否下过单
        LambdaUpdateWrapper<VoucherOrder> userWrapper = new LambdaUpdateWrapper<>();
        userWrapper.eq(VoucherOrder::getUserId,UserHolder.getUser().getId())
                .eq(VoucherOrder::getVoucherId, voucherId);
        //false的意思如果查到多条，不抛异常，只返回第一条。
        VoucherOrder existingOrder = getOne(userWrapper,false);
        if (existingOrder != null) {
            return Result.fail("不能重复下单");
        }
        //5.扣减库存
        /*seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .update();*/
        //获取库存数量
        Integer stock = voucher.getStock();
        LambdaUpdateWrapper<SeckillVoucher> wrapper = new LambdaUpdateWrapper<>();
        //
        wrapper.setSql("stock = stock - 1")
                .eq(SeckillVoucher::getVoucherId, voucherId)
                //对比修改时库存和一开始查询的库存是否一致
                /*.eq(SeckillVoucher::getStock,stock);*/
                .gt(SeckillVoucher::getStock, 0); //库存必须大于零
        boolean success = seckillVoucherService.update(null, wrapper);
        if (!success) {
            return Result.fail("扣减失败");
        }
        //6.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //6.1订单Id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //6.2用户id
        voucherOrder.setUserId(UserHolder.getUser().getId());
        //6.3代金券Id
        voucherOrder.setVoucherId(voucherId);
        //7.返回订单id
        save(voucherOrder);
        return Result.ok(orderId);
    }
}
