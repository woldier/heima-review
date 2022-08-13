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



