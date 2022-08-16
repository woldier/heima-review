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
        //缓存穿透return queryByIdWithCacheThrough(id);
        //缓存击穿
        return  queryByIdWithCacheMutex(id);
    }

    /**
     * 缓存穿透
     * @param id
     * @return
     * @throws BizException
     */
    private Shop queryByIdWithCacheThrough(Long id) throws BizException {
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

    /**
     * 缓存击穿+缓存穿透, mutex实现
     * @throws BizException
     */
    private Shop queryByIdWithCacheMutex(Long id) throws BizException {
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
        Shop shop = null;

        try {
            /*3.redis不在查询数据库*/
            /*3.1获取锁*/
            if(! getLock(id)){
                /*3.3失败则睡眠,再重新调用本方法*/
                 Thread.sleep(50);
                 return queryByIdWithCacheMutex(id);
            }
            /*3.2获取成功查询数据库*/
            /*3.2.1在查询数据库之前,我们应该再次查询redis 看看数据是否存在 存在则无需重建缓存*/
            /*3.2.1.1从redis查询*/
            String cache2 = redisTemplate.opsForValue().get(REGIN);
            /*3.2.1.2.redis存在直接返回,并设置刷新时间*/
            if (cache2!=null) {

                if ("".equals(cache2))
                    /*表明为空缓存,防止击穿抛出异常*/
                    throwShopInfoNE();
                /*刷新有效期*/
                redisTemplate.expire(REGIN,CACHE_SHOP_TTL, TimeUnit.MINUTES);
                shop = JSONUtil.toBean(cache2, Shop.class);
                return shop;
            }


            shop = this.getById(id);
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
            /*5.1设置过期时间*/
            redisTemplate.expire(REGIN,CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw  new RuntimeException();
        } finally {
            /*6.释放锁*/
            unLock(id);
        }




        return shop;

    }

    /**
     * 获取锁
     * @param id
     * @return
     */
    public boolean getLock(Long id){
        /*得到锁并设置有效时间*/
        return redisTemplate.opsForValue().setIfAbsent(LOCK_SHOP_KEY + id, "1", LOCK_SHOP_TTL, TimeUnit.MINUTES).booleanValue();

    }

    /**
     * 释放锁
     * @param id
     */
    public void unLock(Long id){
        redisTemplate.delete(LOCK_SHOP_KEY + id);
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
