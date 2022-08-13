package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.exception.BizException;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.dto.UserDTO.user2UserDto;
import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {


    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 验证码的获取
     *
     * @param phone
     * @param session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        /*校验电话号码是否符合*/
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }

        /*生成验证码*/
        String randomNumbers = RandomUtil.randomNumbers(6);
        log.info("验证码" + randomNumbers);
        /*存入session*/
//        session.setAttribute("phone",phone);
//        session.setAttribute("code",randomNumbers);

        /*存入redis 设置过期时间两分钟*/
        redisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, randomNumbers, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        return Result.ok("验证码返回成功");
    }


    /**
     * 登录
     *
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
        isEmptyString(code, "验证码已经过期");
        isEmptyString(loginForm.getPhone(), "上传的电话号码不能为空");
        isEmptyString(code, "上传的电话验证码不能为空");
        /*判断验证码是否一致*/
        isMatch(loginForm.getCode(), code, "验证码不匹配");
        /*登录成功,移除出验证码*/
//        session.removeAttribute("code");
//        session.removeAttribute("phone");
        redisTemplate.delete(LOGIN_CODE_KEY + loginForm.getPhone());
        /*查询数据库,保存user信息*/

        String token = saveUserWithPhone(session, loginForm.getPhone());
        /*返回token*/
        return Result.ok(token);

    }

    /**
     * 保存用户信息
     *
     * @param session
     * @param phone
     * @throws BizException
     */

    private String saveUserWithPhone(HttpSession session, String phone) throws BizException {
        User one = save2Db(phone);
        /*保存到session*/
        //session.setAttribute("user",user2UserDto(one));
        /*保存到redis*/
        /*生成token*/
        String token = UUID.randomUUID().toString();
        /*userDTO转map*/
        Map<String, Object> map = BeanUtil.beanToMap(user2UserDto(one),new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor(
                                (a,b) -> b.toString()
                        )
        );
        /*存入*/
        redisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, map);
        /*设置有效期*/
        /*要实现用户活跃续期,我们需要在登录拦截器里面实现*/
        redisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        return token;
    }

    /**
     * 保存用户到数据库
     *
     * @param phone
     * @return
     * @throws BizException
     */
    private User save2Db(String phone) throws BizException {
        LambdaQueryWrapper<User> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(User::getPhone, phone);
        User one = getOne(lambdaQueryWrapper);
        /*Weigh空表明不存在则创建*/
        if (one == null) {
            one = new User().setPhone(phone).setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(12));
            if (!save(one)) {
                /*失败*/
                throw new BizException("新建用户失败");
            }
        }
        return one;
    }


    /**
     * 判断是否相等
     *
     * @param local
     * @param phone
     * @throws BizException
     */

    private void isMatch(String local, String phone, String msg) throws BizException {
        if (!phone.equals(local)) {
            throw new BizException(msg);
        }
    }

    /**
     * 判断字符串是否为空,并且抛出异常
     *
     * @param phone
     * @param msg   设置异常的信息
     * @throws BizException
     */
    private void isEmptyString(String phone, String msg) throws BizException {
        if (StringUtils.isEmpty(phone)) {
            /*session中不存在*/
            throw new BizException(msg);
        }
    }
}
