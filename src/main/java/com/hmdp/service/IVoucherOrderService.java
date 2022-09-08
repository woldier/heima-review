package com.hmdp.service;

import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.exception.BizException;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Long seckillVoucher(Long voucherId) throws BizException;

    //Long getOrder(Long voucherId, SeckillVoucher seckillVoucher) throws BizException;

    //void getOrder2(VoucherOrder voucherOrder) throws BizException;
}
