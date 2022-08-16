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
            /*若为空,直接放行*/
            return true;
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
