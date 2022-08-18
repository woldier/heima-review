黑马点评项目-redis实战

## 1. 准备工作

- 导入初始项目到本地

- 创建数据库表

  数据库文件在resource目录下

  ![image-20220813091855489](https://woldier-pic-repo-1309997478.cos.ap-chengdu.myqcloud.com/image-20220813091855489.png)

- 前端工程的导入

解压前端工程到纯英文路径下

![image-20220813095220286](https://woldier-pic-repo-1309997478.cos.ap-chengdu.myqcloud.com/image-20220813095220286.png)

在该项目目录下运行 `start ./nginx.exe` 即可启动前端项目(`stop ./nginx.exe`即可停止)

访问`localhost:8080 `打开手机模式即可访问

![image-20220813095354542](https://woldier-pic-repo-1309997478.cos.ap-chengdu.myqcloud.com/image-20220813095354542.png)



##  2. 登录逻辑的开发

### 2.1 获取验证码

```java
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        // 发送短信验证码并保存验证码


        return   userService.sendCode(phone,session);
    }
```

###  2.2 登录

```java
    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session) throws BizException {
        // TODO 实现登录功能
        return userService.login(loginForm,session);
    }
```

 ###  2.3 登录验证

![image-20220813113423746](https://woldier-pic-repo-1309997478.cos.ap-chengdu.myqcloud.com/image-20220813113423746.png)



自定义拦截器

```java
package com.hmdp.interceptor;

import com.hmdp.dto.UserDTO;
import com.hmdp.exception.BizException;
import com.hmdp.utils.UserHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * 登录拦截器
 */
public class LoginInterceptor implements HandlerInterceptor {
    /**
     * 前置处理
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        /*获取session中的数据*/
        HttpSession session = request.getSession();
        UserDTO user = (UserDTO) session.getAttribute("user");
        /*若为空*/
        if(user==null) {
            /*未授权*/
            response.setStatus(401);
            return false;
        }
        /*存在保存到threadLocal*/
        UserHolder.saveUser(user);
        return true;
    }


    /**
     * 完成后处理
     * @param request
     * @param response
     * @param handler
     * @param ex
     * @throws Exception
     */

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}

```



将拦截器注册到mvc中

```java
package com.hmdp.config;

import com.hmdp.interceptor.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 配置mvc添加自定义拦截器
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        /*
         * 添加拦截器,设置排除路径
         */
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        "/shop/**",
                        "/voucher/**",
                        "/shop-type/**",
                        "/upload/**",
                        "/blog/hot",
                        "/user/code",
                        "/user/login"
                );
    }
}

```



##  3. redis代替session



![image-20220813155752758](https://woldier-pic-repo-1309997478.cos.ap-chengdu.myqcloud.com/image-20220813155752758.png)

为了节省内存空间,我们选择用hash类型保存数据

![image-20220813155811858](https://woldier-pic-repo-1309997478.cos.ap-chengdu.myqcloud.com/image-20220813155811858.png)

登录成功后我们通过token来作为键值

![image-20220813160023005](https://woldier-pic-repo-1309997478.cos.ap-chengdu.myqcloud.com/image-20220813160023005.png)

​	![image-20220813160040783](https://woldier-pic-repo-1309997478.cos.ap-chengdu.myqcloud.com/image-20220813160040783.png)



前端token的保存方式是通过sessionStorage保存的,在发起请求时通过vue的拦截器,在请求中添加token信息

![image-20220813160220150](https://woldier-pic-repo-1309997478.cos.ap-chengdu.myqcloud.com/image-20220813160220150.png)

![image-20220813160331953](https://woldier-pic-repo-1309997478.cos.ap-chengdu.myqcloud.com/image-20220813160331953.png)



###  3.1 验证码

放入session域中并且给定有效期

```java
    @Override
    public Result sendCode(String phone, HttpSession session) {
        /*校验电话号码是否符合*/
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误");
        }

        /*生成验证码*/
        String randomNumbers = RandomUtil.randomNumbers(6);
        log.info("验证码"+randomNumbers);
        /*存入session*/
//        session.setAttribute("phone",phone);
//        session.setAttribute("code",randomNumbers);

        /*存入redis 设置过期时间两分钟*/
        redisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,randomNumbers,LOGIN_CODE_TTL, TimeUnit.MINUTES);
        return Result.ok("验证码返回成功");
    }

```

### 3.2 登录

登成功,我们生成一个tocken作为用于redis中用户信息(用户信息通过hash存储)的key,给定一个初始有效期未30min

```java
  /**
     * 登录
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) throws BizException {
        /*判断手机号与session中是否一致*/
//        String phone = (String) session.getAttribute("phone");
//        String code = (String) session.getAttribute("code");
        /*从redis中取出验证码*/
        String code = redisTemplate.opsForValue().get(LOGIN_CODE_KEY + loginForm.getPhone());
        isEmptyString(code,"验证码已经过期");
        isEmptyString(loginForm.getPhone(),"上传的电话号码不能为空");
        isEmptyString(code,"上传的电话验证码不能为空");
        /*判断验证码是否一致*/
        isMatch(loginForm.getCode(),code,"验证码不匹配");
        /*登录成功,移除出验证码*/
//        session.removeAttribute("code");
//        session.removeAttribute("phone");
        /*查询数据库,保存user信息*/

        saveUserWithPhone(session, loginForm.getPhone());

        return Result.ok();

    }

    /**
     * 保存用户信息
     * @param session
     * @param phone
     * @throws BizException
     */

    private void saveUserWithPhone(HttpSession session, String phone) throws BizException {
        User one = save2Db(phone);
        /*保存到session*/
        //session.setAttribute("user",user2UserDto(one));
        /*保存到redis*/
            /*生成token*/
        String token = UUID.randomUUID().toString();
            /*userDTO转map*/
        Map<String, Object> map = BeanUtil.beanToMap(user2UserDto(one));
        /*存入*/
        redisTemplate.opsForHash().putAll(LOGIN_USER_KEY+token,map);
        /*设置有效期*/
        /*要实现用户活跃续期,我们需要在登录拦截器里面实现*/
        redisTemplate.expire(LOGIN_USER_KEY+token,LOGIN_USER_TTL,TimeUnit.MINUTES);
    }

    /**
     * 保存用户到数据库
     * @param phone
     * @return
     * @throws BizException
     */
    private User save2Db(String phone) throws BizException {
        LambdaQueryWrapper<User> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(User::getPhone, phone);
        User one = getOne(lambdaQueryWrapper);
        /*Weigh空表明不存在则创建*/
        if(one==null){
            one = new User().setPhone(phone).setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(12));
            if (!save(one) ){
                /*失败*/
                throw new BizException("新建用户失败");
            }
        }
        return one;
    }


    /**
     * 判断是否相等
     * @param local
     * @param phone
     * @throws BizException
     */

    private void isMatch(String local, String phone,String msg) throws BizException {
        if(!phone.equals(local)){
            throw new BizException(msg);
        }
    }

    /**
     * 判断字符串是否为空,并且抛出异常
     * @param phone
     * @param msg 设置异常的信息
     * @throws BizException
     */
    private void isEmptyString(String phone,String msg) throws BizException {
        if(StringUtils.isEmpty(phone)){
            /*session中不存在*/
            throw new BizException(msg);
        }
    }
```

###  3.3 登录验证

在之前的基础上,改用redis做登录验证,并且一旦请求了服务,我们给对应用户进行延期

```java
package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.exception.BizException;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;
import static net.sf.jsqlparser.util.validation.metadata.NamedObject.user;

/**
 * 登录拦截器
 */
public class LoginInterceptor implements HandlerInterceptor {

    private StringRedisTemplate redisTemplate;

    /**
     * 通过构造方式,传入
     * @param redisTemplate
     */
    public LoginInterceptor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 前置处理
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        /*获取session中的数据*/
//        HttpSession session = request.getSession();
//        UserDTO user = (UserDTO) session.getAttribute("user");
        /*从请求头获取token
         */
        String token = request.getHeader("authorization");
        /*若为空*/
        if(StringUtils.isEmpty(token)) {
            /*不存在,未授权*/
            response.setStatus(401);
            return false;
        }
        /*从redis中根据token获取user信息*/
        Map<Object, Object> map = redisTemplate.opsForHash().entries(LOGIN_USER_KEY + token);
        /*若为空*/
        if(map.isEmpty()) {
            /*未授权*/
            response.setStatus(401);
            return false;
        }
        /*设置延期*/
        redisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        /*转换*/
        UserDTO userDTO = BeanUtil.fillBeanWithMap(map, new UserDTO(), false);
        /*存在保存到threadLocal*/
        UserHolder.saveUser(userDTO);
        return true;
    }


    /**
     * 完成后处理
     * @param request
     * @param response
     * @param handler
     * @param ex
     * @throws Exception
     */

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}

```



更改webConfig 来传入StringRedisTemplate

```java
package com.hmdp.config;

import com.hmdp.interceptor.LoginInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 配置mvc添加自定义拦截器
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        /*
         * 添加拦截器,设置排除路径
         */
        registry.addInterceptor(new LoginInterceptor(redisTemplate))
                .excludePathPatterns(
                        "/shop/**",
                        "/voucher/**",
                        "/shop-type/**",
                        "/upload/**",
                        "/blog/hot",
                        "/user/code",
                        "/user/login"
                );
    }
}

```

###  3.4 登录刷新的小问题解决

但是前面的部分任然有些问题,因为如果我们一直访问的是excludePathPatterns的路径则不会刷新有效期,

![image-20220813165649226](https://woldier-pic-repo-1309997478.cos.ap-chengdu.myqcloud.com/image-20220813165649226.png)

修改LoginInterceptor代码

```java
package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.exception.BizException;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;
import static net.sf.jsqlparser.util.validation.metadata.NamedObject.user;

/**
 * 登录拦截器
 */
public class LoginInterceptor implements HandlerInterceptor {


    /**
     * 通过构造方式,传入
     * @param redisTemplate
     */

    /**
     * 前置处理
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        UserDTO user = UserHolder.getUser();
        if(user==null) {
            /*未授权*/
            response.setStatus(401);
            return false;
        }
        return true;
    }


    /**
     * 完成后处理
     * @param request
     * @param response
     * @param handler
     * @param ex
     * @throws Exception
     */

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
    }
}

```



添加RefreashTokenInterceptor

```java
package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * 登录拦截器
 */
public class RefreshTokenInterceptor implements HandlerInterceptor {

    private StringRedisTemplate redisTemplate;

    /**
     * 通过构造方式,传入
     * @param redisTemplate
     */
    public RefreshTokenInterceptor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 前置处理
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        /*获取session中的数据*/
//        HttpSession session = request.getSession();
//        UserDTO user = (UserDTO) session.getAttribute("user");
        /*从请求头获取token
         */
        String token = request.getHeader("authorization");
        /*若为空*/
        if(StringUtils.isEmpty(token)) {
            /*不存在,未授权*/
            response.setStatus(401);
            return false;
        }
        /*从redis中根据token获取user信息*/
        Map<Object, Object> map = redisTemplate.opsForHash().entries(LOGIN_USER_KEY + token);
        /*若为空,直接放行*/
        if(map.isEmpty()) {
          return true;
        }
        /*存在token 刷新时间*/
        /*设置延期*/
        redisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        /*转换*/
        UserDTO userDTO = BeanUtil.fillBeanWithMap(map, new UserDTO(), false);
        /*存在保存到threadLocal*/
        UserHolder.saveUser(userDTO);
        return true;
    }


    /**
     * 完成后处理
     * @param request
     * @param response
     * @param handler
     * @param ex
     * @throws Exception
     */

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}

```



配置mvc ,给拦截器添加顺序

```java
package com.hmdp.config;

import com.hmdp.interceptor.LoginInterceptor;
import com.hmdp.interceptor.RefreshTokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 配置mvc添加自定义拦截器
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        /*
         * 添加拦截器,设置排除路径
         */
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        "/shop/**",
                        "/voucher/**",
                        "/shop-type/**",
                        "/upload/**",
                        "/blog/hot",
                        "/user/code",
                        "/user/login"
                ).order(1);
        /**
         * 添加刷新拦截器
         */
        registry.addInterceptor(new RefreshTokenInterceptor(redisTemplate)).order(0);
    }
}

```

## 4. 商户信息缓存

![image-20220815145129959](https://woldier-pic-repo-1309997478.cos.ap-chengdu.myqcloud.com/image-20220815145129959.png)

```java
/**
     * 查询商户信息(redis)
     * @param id 商户id
     * @return 返回商户信息
     */
    @Override
    public Shop queryById(Long id) {
        final  String  REGIN = CACHE_SHOP_KEY + id;
        /*1.从redis查询*/
        String cache = redisTemplate.opsForValue().get(REGIN);
        /*2.redis存在直接返回,并设置刷新时间*/

        if (cache!=null) {
            /*刷新有效期*/
            redisTemplate.expire(REGIN,CACHE_SHOP_TTL, TimeUnit.MINUTES);
            Shop shop = JSONUtil.toBean(cache, Shop.class);
            return shop;
        }

        /*3.redis不在查询数据库*/
        Shop shop = this.getById(id);
        /*4.数据库不存在直接返回*/
        if (shop==null)
            return null;
        /*5.数据库存在设置到redis并返回*/
        String shopAsString = JSONUtil.toJsonStr(shop);
        redisTemplate.opsForValue().set(REGIN,shopAsString);
            /*设置过期时间*/
        redisTemplate.expire(REGIN,CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }
```

![image-20220815153409058](https://woldier-pic-repo-1309997478.cos.ap-chengdu.myqcloud.com/image-20220815153409058.png)

列表缓存

```java
   @GetMapping("list")
    public Result queryTypeList() {
        /*查询redis*/
        List<String> cache = redisTemplate.opsForList().range(CACHE_SHOP_LIST_KEY,0,9);
        if(cache.size()!=0){
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
```

##  5. 缓存更新策略

![image-20220815161536017](https://woldier-pic-repo-1309997478.cos.ap-chengdu.myqcloud.com/image-20220815161536017.png)

![image-20220815162314478](https://woldier-pic-repo-1309997478.cos.ap-chengdu.myqcloud.com/image-20220815162314478.png)

![image-20220815164728551](https://woldier-pic-repo-1309997478.cos.ap-chengdu.myqcloud.com/image-20220815164728551.png)

注: 这里的分布式系统TTC可参考Seata





更新方案

![image-20220815170207787](https://woldier-pic-repo-1309997478.cos.ap-chengdu.myqcloud.com/image-20220815170207787.png)

左图如果执行顺序如下,则不会出现错误情况,若线程执行如上图则会出现数据不一致,会出现线程安全问题.因为在删除缓存并且更新数据的过程中(这个更新数据库时间较长) ,在这是cache 未命中并且查询到旧数据的机会很大,因为查询时间相较于插入时间会短的多.

![image-20220815170420056](https://woldier-pic-repo-1309997478.cos.ap-chengdu.myqcloud.com/image-20220815170420056.png)

右图如果执行顺序如上,则不会出现数据不一致.



但是他也存在一些问题:

![image-20220815170841680](https://woldier-pic-repo-1309997478.cos.ap-chengdu.myqcloud.com/image-20220815170841680.png)

当查询线程先查询,未命中,则会查询数据库,若查询完后,线程2立即运行,数据库信息发生改变,但是这里写入的缓存任然是旧数据.

但是这种方式下在查询未命中查询数据库时(很小的一个时间范围)恰好有另一个线程来更新数据库的机会就小很多.

##  6. 缓存穿透

###  6.1 缓存穿透的概念 

缓存穿透是指客户端请求的数据在缓存中和数据库都不存在,这样缓存永远不会生效,这些请求都会达到数据库.

解决策略:

1. 缓存空对象

   优点:实现简单,维护方便

   缺点:额外的内存消耗,可能造成短期不一致.

   ![image-20220816100726092](https://woldier-pic-repo-1309997478.cos.ap-chengdu.myqcloud.com/image-20220816100726092.png)

2. 布隆过滤

   优点:内存占用少,没有多余的key

   缺点:实现复杂,存在误判可能.

 ### 6.2 解决策略的实现

![image-20220816101330206](https://woldier-pic-repo-1309997478.cos.ap-chengdu.myqcloud.com/image-20220816101330206.png)

```java
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

```

我们在第五步中判断了查询到的数据是否为空,为空设置了空缓存

在第二步中查询了缓存,并且判断了该缓存是否为空缓存.





布隆过滤的实现,在后面bitmap时进行实现.



##  7. 缓存雪崩

缓存雪崩是指在同一时间大量缓存的key同时失效或者redis服务宕机,导致大量请求到达数据库,带来巨大压力.

解决方案:

- 给不同key的TTL再随机添加一个随机值
- 利用redis集群提高服务的可用性(redis宕机)
- 给缓存业务添加降级限流策略(服务熔断)

##  8. 缓存击穿

###  8.1 缓存击穿的概念,及解决策略

缓存击穿问题也叫热点key问题,就是一个被`高并发访问`并且`缓存重建业务复杂`的key突然失效了,无效的请求访问会在瞬间给数据库带来巨大的冲击.

![image-20220816104337698](https://woldier-pic-repo-1309997478.cos.ap-chengdu.myqcloud.com/image-20220816104337698.png)

解决方案:

- 互斥锁

  此方法下,若重构线程耗时较长,可能会导致其他等待线程数量激增,性能不好

  ![image-20220816104550980](C:\Users\wang1\AppData\Roaming\Typora\typora-user-images\image-20220816104550980.png)

- 逻辑过期

在数据中添加一个过期字段手动取维护一个过期字段,在redis中不设置TTL

![image-20220816105116435](https://woldier-pic-repo-1309997478.cos.ap-chengdu.myqcloud.com/image-20220816105116435.png)

两种方案的对比

![image-20220816105137922](https://woldier-pic-repo-1309997478.cos.ap-chengdu.myqcloud.com/image-20220816105137922.png)

###  8.2 解决缓存击穿的实现

###  8.2.1 互斥锁解决方案

首先我们向基于redis实现互斥锁,这里我们获取锁用到了String中的`setnx key value` 命令,释放锁我们用到的是`del key`

,多个线程我们可以更具`setnx key value`返回的值来判断是否获取锁成功.

![image-20220816110238516](https://woldier-pic-repo-1309997478.cos.ap-chengdu.myqcloud.com/image-20220816110238516.png)

> -- 当然,我们在设置锁的时候也会给他一个有效期,解决获取锁的线程如果在获取锁之后出现宕机的情况. --

注意下面的流程图中,获取锁之后,还应该再次查看redis中是否又数据,若有则直接放回(这是因为,有可能有两个线程同时贱则到缓存为空,都要获取锁,但是可能出现的情况是线程1获取锁成功进行缓存重建释放了锁之后,线程2才获取锁,这时候明显获取锁成功了,这时候会导致缓存重建两次)

![image-20220816113801971](https://woldier-pic-repo-1309997478.cos.ap-chengdu.myqcloud.com/image-20220816113801971.png)

```java
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
```

此处用jmeter进行测试,可以在服务器控制台查看到只请求了一次服务器

###  8.2.2 基于逻辑过期解决缓存击穿问题



![image-20220816122059834](https://woldier-pic-repo-1309997478.cos.ap-chengdu.myqcloud.com/image-20220816122059834.png)



首先我们来完成查询数据库并且设置逻辑过期时间保存的代码

```java
 public void saveShop2Redis(Long id,Long ttl){
        /*查询数据库*/
        Shop shop = this.getById(id);
        /*数据封装*/
        RedisData cache = new RedisData();
        /*设置逻辑过期时间为当前时间+ttl*/
        cache.setExpireTime(LocalDateTime.now().plusSeconds(ttl));
        /*设置shop*/
        cache.setData(shop);
        /*存入热点数据,并且不设置有效期*/
        redisTemplate.opsForValue().set( CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(cache));
    }
```

```java
 private Shop queryByIdWithLogicExpire(Long id) {
        final  String  REGIN = CACHE_SHOP_KEY + id;
        /*1.从redis查询*/
        String cache = redisTemplate.opsForValue().get(REGIN);
        /*2.检查过期时间,若没有过期直接返回*/
        RedisData redisData = JSONUtil.toBean(cache, RedisData.class);
        /*获取过期时间*/
        LocalDateTime expireTime = redisData.getExpireTime();
        /*获取商户数据*/
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime now = LocalDateTime.now();
        if(now.isBefore(expireTime))/*未过期直接返回*/
            return shop;

        /*过期进行数据的重载*/
        /*3.获取锁*/
        boolean lock = getLock(id);
        /*3.1获取不成功,使用原始数据*/
        if (lock) {

//                /*3.2获取成功,开启线程,在开启线程之前再次查询redis 检查是否过期*/
//                /*3.2.1.从redis查询*/
//                cache = redisTemplate.opsForValue().get(REGIN);
//                /*3.2.2.检查过期时间,若没有过期直接返回*/
//                /*在重建数据之前再此检测是否过期*/
//                redisData = JSONUtil.toBean(cache, RedisData.class);
//                /*获取过期时间*/
//                expireTime = redisData.getExpireTime();
//                /*获取商户数据*/
//                shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
//                if(LocalDateTime.now().isBefore(expireTime)) /*未过期直接返回*/
//                    return shop;
                /*4过期进行数据重建*/
                CACHE_REBUILD_SERVICE.submit(()->{
                    try {
                        this.saveShop2Redis(id,LOCK_SHOP_TTL);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }finally {
                        /*释放锁*/
                        unLock(id);
                    }
                });
            }



        return shop;
    }
```

![image-20220816161751849](https://woldier-pic-repo-1309997478.cos.ap-chengdu.myqcloud.com/image-20220816161751849.png)

![image-20220816161811108](https://woldier-pic-repo-1309997478.cos.ap-chengdu.myqcloud.com/image-20220816161811108.png)

我们通过数据库工具修改shop信息,经过并发测试可以发现经过一段时间后,数据发生更正.

##  9. Redis工具类的封装

![image-20220816164536397](https://woldier-pic-repo-1309997478.cos.ap-chengdu.myqcloud.com/image-20220816164536397.png)

 ```java
package com.hmdp.utils;


import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import com.hmdp.exception.BizException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/**
 * redis 封装
 */
@Component
public class CacheClient {

    private final StringRedisTemplate redisTemplate;
    /**
     * 工作线程池
     */
    private static final ExecutorService CACHE_REBUILD_SERVICE = Executors.newFixedThreadPool(10);
    public CacheClient(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }


    /*-----------------------------------------------------*/

    /**
     * 设置key
     * @param key
     * @param value
     * @param time
     * @param timeUnit
     */
    public void set(String key, Object value, Long time , TimeUnit timeUnit){
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,timeUnit);
    }

    public void set(String key, Object value){
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value));
    }

    /**
     * 设置key 并且给定逻辑过期时间
     * @param key
     * @param value
     * @param time
     * @param timeUnit
     */
    public void setWithLogicalExpire(String key, Object value, Long time , TimeUnit timeUnit){
        RedisData redisData = new RedisData();
        redisData.setData(value);
        /*设置过期时间为现在的多少秒之后*/
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        this.set(key,redisData);
    }

    /**
     * 缓存穿透get
     * @param keyPrefix key前缀
     * @param id key值
     * @param type 返回类型
     * @param dbCallback  查询数据库的回调函数
     * @param time 过期时间TTL
     * @param timeUnit 时间单位
     * @param <R> 返回泛型
     * @param <ID> id/key 的泛型
     * @return
     * @throws BizException
     */
    public  <R,ID> R queryByIdWithCacheThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID,R> dbCallback, Long time , TimeUnit timeUnit
    ) throws BizException {
        String  REGIN = keyPrefix + id;
        /*1.从redis查询*/
        String cache = redisTemplate.opsForValue().get(REGIN);
        /*2.redis存在直接返回,并设置刷新时间*/
        if (cache!=null) {
            if ("".equals(cache))
                /*表明为空缓存,防止击穿抛出异常*/
                throwShopInfoNE();
            /*刷新有效期*/
            redisTemplate.expire(REGIN,time, timeUnit);
            return JSONUtil.toBean(cache, type);
        }

        /*3.redis不在查询数据库*/
        //Shop shop = this.getById(id);
        R r = dbCallback.apply(id);
        /*4.数据库不存在*/
        if (r==null) {
            /*设置空缓存防止内存穿透,并且抛出异常*/
            redisTemplate.opsForValue().set(REGIN,"");
            redisTemplate.expire(REGIN,time,timeUnit);
            throwShopInfoNE();
        }

        /*5.数据库存在设置到redis并返回*/
        String shopAsString = JSONUtil.toJsonStr(r);
        redisTemplate.opsForValue().set(REGIN,shopAsString);
        /*设置过期时间*/
        redisTemplate.expire(REGIN,time,timeUnit);
        return r;
    }
    private void throwShopInfoNE() throws BizException {
        throw new BizException("商户不存在");
    }



    public   <R,ID> R queryByIdWithLogicExpire(  String keyPrefix,String lockPrefix, ID id, Class<R> type, Function<ID,R> dbCallback, Long time ,Long lockTime, TimeUnit timeUnit) {
        String  REGIN = keyPrefix + id;
        String  LOCK_REGIN = lockPrefix + id;
        /*1.从redis查询*/
        String cache = redisTemplate.opsForValue().get(REGIN);
        /*2.检查过期时间,若没有过期直接返回*/
        RedisData redisData = JSONUtil.toBean(cache, RedisData.class);
        /*获取过期时间*/
        LocalDateTime expireTime = redisData.getExpireTime();
        /*获取商户数据*/
        R r = JSONUtil.toBean((JSONObject) redisData.getData(),type);
        LocalDateTime now = LocalDateTime.now();
        if(now.isBefore(expireTime))/*未过期直接返回*/
            return r;
        /*过期进行数据的重载*/
        /*3.获取锁*/
        boolean lock = getLock(LOCK_REGIN,lockTime);
        /*3.1获取不成功,使用原始数据*/
        if (lock) {
            CACHE_REBUILD_SERVICE.submit(()->{
                try {
                    /*查询数据库*/
                    R r1 = dbCallback.apply(id);
                    /*设置逻辑过期时间*/
                    this.setWithLogicalExpire(REGIN,r1,time,timeUnit);
                } catch (Exception e) {
                    e.printStackTrace();
                }finally {
                    /*释放锁*/
                    unLock(LOCK_REGIN);
                }
            });
        }
        return r;
    }


    /**
     * 缓存击穿+缓存穿透, mutex实现
     * @throws BizException
     */
    public   <R,ID> R  queryByIdWithCacheMutex( String keyPrefix,String lockPrefix, ID id, Class<R> type, Function<ID,R> dbCallback, Long time ,Long lockTime, TimeUnit timeUnit) throws BizException {
        String  REGIN = keyPrefix + id;
        String  LOCK_REGIN = lockPrefix + id;
        /*1.从redis查询*/
        String cache = redisTemplate.opsForValue().get(REGIN);
        R r = null;
        /*2.redis存在直接返回,并设置刷新时间*/
        if (cache!=null) {
            if ("".equals(cache))
                /*表明为空缓存,防止击穿抛出异常*/
                throwShopInfoNE();
            /*刷新有效期*/
            redisTemplate.expire(REGIN,CACHE_SHOP_TTL, TimeUnit.MINUTES);
            r = JSONUtil.toBean(cache, type);
            return r;
        }
        try {
            /*3.redis不在查询数据库*/
            /*3.1获取锁*/
            if(! getLock(LOCK_REGIN,lockTime)){
                /*3.3失败则睡眠,再重新调用本方法*/
                Thread.sleep(50);
                return queryByIdWithCacheMutex(keyPrefix,lockPrefix,id,type,dbCallback,time,lockTime,timeUnit);
            }
            /*3.2获取成功查询数据库*/
            /*3.2.1在查询数据库之前,我们应该再次查询redis 看看数据是否存在 存在则无需重建缓存*/
            /*3.2.1.1从redis查询*/
            cache = redisTemplate.opsForValue().get(REGIN);
            /*3.2.1.2.redis存在直接返回,并设置刷新时间*/
            if (cache!=null) {

                if ("".equals(cache))
                    /*表明为空缓存,防止击穿抛出异常*/
                    throwShopInfoNE();
                /*刷新有效期*/
                redisTemplate.expire(REGIN,time, timeUnit);
                r = JSONUtil.toBean(cache, type);
                return r;
            }
            r = dbCallback.apply(id);
            /*4.数据库不存在*/
            if (r==null) {
                /*设置空缓存防止内存穿透,并且抛出异常*/
                redisTemplate.opsForValue().set(REGIN,"");
                redisTemplate.expire(REGIN,time, timeUnit);
                throwShopInfoNE();

            }
            /*5.数据库存在设置到redis并返回*/
            String shopAsString = JSONUtil.toJsonStr(r);
            redisTemplate.opsForValue().set(REGIN,shopAsString);
            /*5.1设置过期时间*/
            redisTemplate.expire(REGIN,time, timeUnit);
        } catch (InterruptedException e) {
            throw  new RuntimeException();
        } finally {
            /*6.释放锁*/
            unLock(LOCK_REGIN);
        }
        return r;
    }



    /**
     * 获取锁
     * @param key
     * @return
     */
    public boolean getLock(String key,Long time){
        /*得到锁并设置有效时间*/
        return redisTemplate.opsForValue().setIfAbsent(key, "1", time, TimeUnit.SECONDS).booleanValue();

    }

    /**
     * 释放锁
     * @param key
     */
    public void unLock(String key){
        redisTemplate.delete(key);
    }



}

 ```

## 10. 优惠券秒杀

### 10.1 全局唯一id

对于优惠券订单,id的生成是需要保证唯一性,且不能有规律性

![image-20220816222541261](https://woldier-pic-repo-1309997478.cos.ap-chengdu.myqcloud.com/image-20220816222541261.png)

![image-20220816222411751](https://woldier-pic-repo-1309997478.cos.ap-chengdu.myqcloud.com/image-20220816222411751.png)



![image-20220816222701089](https://woldier-pic-repo-1309997478.cos.ap-chengdu.myqcloud.com/image-20220816222701089.png)

````java

/**
 * 全局唯一id生成器
 * id结构如下
 *    64   (数字位恒0)     |          63-32 (时间从某个时间开始的秒数)       |        31-0(数字自增)
 *    这里我们以2022-1-1-00:00:00为基准
 */
@Slf4j
@Component
public class RedisIdWorker {
    private final long BEGIN_TIME = 1640995200L;

    private final StringRedisTemplate redisTemplate;

    public RedisIdWorker(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public long nextId(String key){

        //1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long second = now.toEpochSecond(ZoneOffset.UTC);
        second = second - BEGIN_TIME;
        //2.获取自增数
        //2.1生成当天日期yyyy:MM:dd
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        Long increment = redisTemplate.opsForValue().increment("inc:" + key + ":" + date);
        //3.拼接返回
        return second <<32 | increment;
    }



    /**
     *
     * 得到一个基准时间
     * @param args
     */
    public static void main(String[] args) {
        long second = LocalDateTime.of(2022, 1, 1, 0, 0, 0).toEpochSecond(ZoneOffset.UTC);
        log.info(""+second);
    }
}

````



接下来进行测试

```java
 @Test
    public void test2() throws InterruptedException {
        CountDownLatch downLatch = new CountDownLatch(500);

        Runnable runnable = () ->{
            for (int i = 0; i < 100; i++) {
                System.out.println(redisIdWorker.nextId("shop"));
            }
            downLatch.countDown();
        };
        long millis = System.currentTimeMillis();
        for (int i = 0; i < 500; i++) {
            executorService.submit(runnable);
        }

        downLatch.await();
        long millis2 = System.currentTimeMillis();
        System.out.println(millis2-millis);

    }
```

经过测试3s钟大概可以跑50000条id

其他全局唯一id策略

![image-20220816231939596](https://woldier-pic-repo-1309997478.cos.ap-chengdu.myqcloud.com/image-20220816231939596.png)

- UUID是jdk自带的
- redis实现如上
- 雪花算法也是知名的全局算吗,不过其需要保证机器码独有
- 数据库自增,是指单独拿数据库钟一张表来做自增得到id的0-31位,看一看作redis实现的数据库版.

###  10.2 秒杀下单功能

```java
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
        /*库存扣减*/
        seckillVoucher.setStock(seckillVoucher.getStock()-1);
        boolean b = voucherService.updateById(seckillVoucher);
        if(!b) throw new BizException("库存不足");
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
```

###  10.3 超卖问题

商品减少与订单增加的数目不一致.

乐观锁-版本号法

![image-20220818172842151](C:\Users\wang1\IdeaProjects\hm-dianping\readme.assets\image-20220818172842151.png)

![image-20220818173327155](C:\Users\wang1\IdeaProjects\hm-dianping\readme.assets\image-20220818173327155.png)

- mybatis实现乐观锁

数据库加入value字段

实体version字段加入乐观锁注解`@Vesion`

```java
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_seckill_voucher")
public class SeckillVoucher implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 关联的优惠券的id
     */
    @TableId(value = "voucher_id", type = IdType.INPUT)
    private Long voucherId;

    /**
     * 库存
     */
    private Integer stock;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 生效时间
     */
    private LocalDateTime beginTime;

    /**
     * 失效时间
     */
    private LocalDateTime endTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 锁
     */
    @Version
    private Integer version;

}

```

配置mytatis加入乐观锁组件

```java
@Configuration
public class MybatisConfig {
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        /*乐观锁*/
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }
}

```

下单代码不做更改

- 自编码实现乐观锁

上面mybatis实现乐观锁,虽然解决了超卖,但是现目前任然有问题,如果100个线程获取version都相同,那么只有一个线程成功,用户体验不好.失败率大大升高.

因此,我们为了增加成功率我们改用stock(库存作为判定标准)自行实现

![image-20220818181148498](C:\Users\wang1\IdeaProjects\hm-dianping\readme.assets\image-20220818181148498.png)