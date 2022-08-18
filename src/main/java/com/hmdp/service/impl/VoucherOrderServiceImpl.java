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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {


    @Resource
    private ISeckillVoucherService voucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    /**
     * 秒杀券下单
     * @param voucherId
     * @return
     */
    @Override
    @Transactional
    public Long seckillVoucher(Long voucherId) throws BizException {
        /*1.查询过期时间和库存*/
        SeckillVoucher seckillVoucher = voucherService.getById(voucherId);
        LocalDateTime now = LocalDateTime.now();
        if(seckillVoucher.getBeginTime().isAfter(now)) throw new BizException("秒杀尚未开始");
        if(seckillVoucher.getEndTime().isBefore(now)) throw new BizException("秒杀已经结束");
        if(seckillVoucher.getStock()<1) throw new BizException("库存不足");
        /*库存扣减 自定义乐观锁*/
        seckillVoucher.setStock(seckillVoucher.getStock()-1);
        boolean b = voucherService.update().
                setSql("stock = stock -1") //set stock = stock -1
                .eq("voucher_id",voucherId).gt("stock",0) //where id =? and stock >0
                .update(); //
        if(!b) throw new BizException("乐观锁");
        /*2.创建订单*/
        VoucherOrder voucherOrder = new VoucherOrder();
        /*得到用户id*/
        voucherOrder.setUserId(UserHolder.getUser().getId());
        /*生成id*/
        Long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        /*得到代金券id*/
        voucherOrder.setVoucherId(seckillVoucher.getVoucherId());
        this.save(voucherOrder);
        return orderId;
    }
}
