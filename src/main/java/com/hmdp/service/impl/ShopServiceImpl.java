package com.hmdp.service.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.entity.Shop;
import com.hmdp.exception.BizException;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    @Autowired
    private CacheClient cacheClient;


    /**
     * 查询商户信息(redis)
     * @param id 商户id
     * @return 返回商户信息
     */
    @Override
    public Shop queryById(Long id) throws BizException {
        //缓存穿透
        //return cacheClient.queryByIdWithCacheThrough(CACHE_SHOP_KEY,id,Shop.class,e -> this.getById(e),CACHE_SHOP_TTL,TimeUnit.MINUTES);
        //缓存击穿
        //return cacheClient.queryByIdWithCacheMutex(CACHE_SHOP_KEY,id,Shop.class,e -> this.getById(e),CACHE_SHOP_TTL,TimeUnit.MINUTES);
        return  cacheClient.queryByIdWithLogicExpire(CACHE_SHOP_KEY,LOCK_SHOP_KEY,id,Shop.class,e -> this.getById(e),CACHE_SHOP_TTL,LOCK_SHOP_TTL,TimeUnit.SECONDS);
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
