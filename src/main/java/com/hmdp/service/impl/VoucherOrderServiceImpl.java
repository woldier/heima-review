package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.exception.BizException;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import com.hmdp.utils.lock.SimpleRedisLock;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
@EnableAspectJAutoProxy(exposeProxy = true,proxyTargetClass = true)

public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {


    @Resource
    private ISeckillVoucherService voucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;
    /**
     * 业务代理对象
     */
    private IVoucherOrderService proxy;
    /**
     * 阻塞队列
     */
    private BlockingQueue<VoucherOrder> orders = new ArrayBlockingQueue<>(1024 * 1024);
    /**
     * 线程池
     */
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    /**
     * lua脚本
     */
    private final static DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        //从classpath加载
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }


    /**
     * 秒杀券下单 基于java实现
     *
     * @param voucherId
     * @return
     */
//    @Override
//
//    public Long seckillVoucher(Long voucherId) throws BizException {
//        /*1.查询过期时间和库存*/
//        SeckillVoucher seckillVoucher = voucherService.getById(voucherId);
//        LocalDateTime now = LocalDateTime.now();
//        if(seckillVoucher.getBeginTime().isAfter(now)) throw new BizException("秒杀尚未开始");
//        if(seckillVoucher.getEndTime().isBefore(now)) throw new BizException("秒杀已经结束");
//        if(seckillVoucher.getStock()<1) throw new BizException("库存不足");
//        //------------------------synchronized实现-------------------------------------
////        synchronized (UserHolder.getUser().getId().toString().intern()){ //以userid加锁
////            // 锁加在这里而没有加载函数里是因为只有函数结束提交事务之后(因为加了@Transactional)数据库信息才会更新,而解锁是在事务提交之前,因此出现问题.
////
////        //return getOrder(voucherId, seckillVoucher);
////            //事务是基于代理的, 使用this.getOrder 代理不会生效
////            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
////            return proxy.getOrder(voucherId, seckillVoucher);
////        }
//        //------------------------synchronized实现-------------------------------------
//        //以order: 与userid作为锁id
//        //SimpleRedisLock lock = new SimpleRedisLock(stringRedisTemplate,"order:"+UserHolder.getUser().getId());
//        RLock lock = redissonClient.getLock("lock:order:"+UserHolder.getUser().getId());
//        try {
//            //if (!lock.tryLock(10L))
//            if (!lock.tryLock())
//                throw  new BizException("不允许一人多单");
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.getOrder(voucherId, seckillVoucher);
//            //return getOrder(voucherId, seckillVoucher);
//            //事务是基于代理的, 使用this.getOrder 代理不会生效
//        } finally {
//            lock.unlock();
//        }
//
//    }

    //    @Transactional
//    public Long getOrder(Long voucherId, SeckillVoucher seckillVoucher) throws BizException {
//        /*库存充足,实现一人一单(查询用户是否已经有某优惠券)*/
//        LambdaQueryWrapper<VoucherOrder> lambdaQueryWrapper = new LambdaQueryWrapper<>();
//        lambdaQueryWrapper.eq(VoucherOrder::getVoucherId, voucherId).eq(VoucherOrder::getUserId, UserHolder.getUser().getId());
//        int count = count(lambdaQueryWrapper);
//        /*查看是否有数据*/
//        if (count > 0) throw new BizException("用户已经持有了该秒杀券");
//        /*库存扣减 自定义乐观锁*/
//        seckillVoucher.setStock(seckillVoucher.getStock() - 1);
//        boolean b = voucherService.update().
//                setSql("stock = stock -1") //set stock = stock -1
//                .eq("voucher_id", voucherId).gt("stock", 0) //where id =? and stock >0
//                .update(); //
//        if (!b) throw new BizException("乐观锁");
//        /*2.创建订单*/
//        VoucherOrder voucherOrder = new VoucherOrder();
//        /*得到用户id*/
//        voucherOrder.setUserId(UserHolder.getUser().getId());
//        /*生成id*/
//        Long orderId = redisIdWorker.nextId("order");
//        voucherOrder.setId(orderId);
//        /*得到代金券id*/
//        voucherOrder.setVoucherId(seckillVoucher.getVoucherId());
//        this.save(voucherOrder);
//        return orderId;
//    }

    /*监听*/
    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(
                this::call
        );
    }



    /**
     * 秒杀券下单 基于lua脚本
     *
     * @param voucherId
     * @return
     */
    @Override
    public Long seckillVoucher(Long voucherId) throws BizException {
        //1.执行lua脚本
        Long execute = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.EMPTY_LIST //key参数没有不能传null
                , voucherId.toString(), UserHolder.getUser().getId().toString());
        int res = execute.intValue();
        // 2.判断结构是否位0(不为0返回异常信息)
        if (res != 0)
            if (res == 1) throw new BizException("库存不足");
        if (res == 2) throw new BizException("重复下单");
        //3.将优惠券id,用户id,订单id加入到阻塞队列中
        /*3.1生成id*/
        VoucherOrder voucherOrder = new VoucherOrder();
        /*得到用户id*/
        voucherOrder.setUserId(UserHolder.getUser().getId());
        /*生成id*/
        Long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        /*得到代金券id*/
        voucherOrder.setVoucherId(voucherId);
        //4.加入阻塞队列
        orders.add(voucherOrder);
        //返回订单id
        return orderId;
    }


    private Object call() {
        /*处理订单*/
        while (true) {
            try {
                VoucherOrder voucherOrder = orders.take();
                RLock lock = redissonClient.getLock("lock:order:" + voucherOrder.getUserId());//在新的线程中获取的,因此不能从UserHolder中取
                if (!lock.tryLock()) throw new BizException("不允许一人多单");
                try {
                    if (!lock.tryLock())
                        throw new BizException("不允许一人多单");
                    /*库存充足,实现一人一单(查询用户是否已经有某优惠券)*/
                    LambdaQueryWrapper<VoucherOrder> lambdaQueryWrapper = new LambdaQueryWrapper<>();
                    lambdaQueryWrapper.eq(VoucherOrder::getVoucherId, voucherOrder.getVoucherId()).eq(VoucherOrder::getUserId, voucherOrder.getUserId());
                    int count = count(lambdaQueryWrapper);
                    /*查看是否有数据*/
                    if (count > 0) throw new BizException("用户已经持有了该秒杀券");
                    /*库存扣减 自定义乐观锁*/
                    boolean b = voucherService.update().
                            setSql("stock = stock -1") //set stock = stock -1
                            .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0) //where id =? and stock >0
                            .update(); //
                    if (!b) throw new BizException("乐观锁");
                    this.save(voucherOrder);
                } finally {
                    lock.unlock();
                }


            } catch (Exception e) {
                log.error(e.getMessage());
            }
        }
    }
}
