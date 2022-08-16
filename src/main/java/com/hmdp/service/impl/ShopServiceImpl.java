package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.entity.Shop;
import com.hmdp.exception.BizException;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

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

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * spring 自带的对象序列化字符串工具
     */
    private static final ObjectMapper objectmapper = new ObjectMapper();

    /**
     * 查询商户信息(redis)
     * @param id 商户id
     * @return 返回商户信息
     */
    @Override
    public Shop queryById(Long id) throws BizException {
        final  String  REGIN = CACHE_SHOP_KEY + id;
        /*1.从redis查询*/
        String cache = redisTemplate.opsForValue().get(REGIN);
        /*2.redis存在直接返回,并设置刷新时间*/
        if (cache!=null) {

            if ("".equals(cache))
                /*表明为空缓存,防止击穿抛出异常*/
                throwShopInfoNE();
            /*刷新有效期*/
            redisTemplate.expire(REGIN,CACHE_SHOP_TTL, TimeUnit.MINUTES);
            Shop shop = JSONUtil.toBean(cache, Shop.class);
            return shop;
        }

        /*3.redis不在查询数据库*/
        Shop shop = this.getById(id);
        /*4.数据库不存在*/
        if (shop==null) {
            /*设置空缓存防止内存穿透,并且抛出异常*/
            redisTemplate.opsForValue().set(REGIN,"");
            redisTemplate.expire(REGIN,CACHE_NULL_TTL, TimeUnit.MINUTES);
            throwShopInfoNE();

        }

        /*5.数据库存在设置到redis并返回*/
        String shopAsString = JSONUtil.toJsonStr(shop);
        redisTemplate.opsForValue().set(REGIN,shopAsString);
            /*设置过期时间*/
        redisTemplate.expire(REGIN,CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }

    private void throwShopInfoNE() throws BizException {
        throw new BizException("商户不存在");
    }

    /**
     * 更新商户数据
     * @param shop
     */
    @Override
    @Transactional
    public void update(Shop shop) {
        /*
        * 更新数据库
        * */
        this.updateById(shop);

        /*
         *删除缓存
         */
        final  String  REGIN = CACHE_SHOP_KEY + shop.getId();
        redisTemplate.delete(REGIN);
    }
}
