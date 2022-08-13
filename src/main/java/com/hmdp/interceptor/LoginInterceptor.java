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
