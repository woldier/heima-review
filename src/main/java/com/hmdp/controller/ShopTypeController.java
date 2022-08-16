package com.hmdp.controller;


import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.service.IShopTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_LIST_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private IShopTypeService typeService;

    @Autowired
    private StringRedisTemplate redisTemplate;
    @GetMapping("list")
    public Result queryTypeList() {
        /*查询redis*/
        List<String> cache = redisTemplate.opsForList().range(CACHE_SHOP_LIST_KEY,0,9);
        if(cache.size()!=0){
            /*遍历*/
            List<ShopType> shopTypes = cache.stream().map( e -> JSONUtil.toBean(e,ShopType.class)).collect(Collectors.toList());
            /*设置有效时间*/
            redisTemplate.expire(CACHE_SHOP_LIST_KEY,CACHE_SHOP_TTL, TimeUnit.MINUTES);
            return  Result.ok(shopTypes);
        }

        List<ShopType> typeList = typeService
                .query().orderByAsc("sort").list();
        typeList.stream().forEach(e -> redisTemplate.opsForList().rightPush(CACHE_SHOP_LIST_KEY,JSONUtil.toJsonStr(e)));
        /*设置有效时间*/
        redisTemplate.expire(CACHE_SHOP_LIST_KEY,CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return Result.ok(typeList);
    }
}
