#  黑马点评项目-redis实战

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

