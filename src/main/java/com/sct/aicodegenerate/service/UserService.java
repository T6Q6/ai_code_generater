package com.sct.aicodegenerate.service;

import cn.hutool.http.HttpRequest;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.sct.aicodegenerate.model.dto.user.UserLoginRequest;
import com.sct.aicodegenerate.model.dto.user.UserQueryRequest;
import com.sct.aicodegenerate.model.dto.user.UserRegisterRequest;
import com.sct.aicodegenerate.model.entity.User;
import com.sct.aicodegenerate.model.vo.LoginUserVO;
import com.sct.aicodegenerate.model.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * 用户 服务层。
 */
public interface UserService extends IService<User> {

    /**
     * 用户注册
     *
     * @param userRegisterRequest
     * @return
     */
    long userRegister(UserRegisterRequest userRegisterRequest);

    /**
     * 获取用户登录的脱敏信息
     *
     * @param user
     * @return
     */
    LoginUserVO getLoginUserVO(User user);

    /**
     * 用户登录
     *
     * @param userLoginRequest
     * @param request
     * @return
     */
    LoginUserVO userLogin(UserLoginRequest userLoginRequest, HttpServletRequest request);

    /**
     * 获取当前登录用户
     *
     * @param request
     * @return
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 退出登录
     *
     * @param request
     * @return
     */
    boolean userLogout(HttpServletRequest request);

    /**
     * 获取脱敏后的单个用户信息
     */
    UserVO getUserVO(User user);

    /**
     * 获取脱敏后的用户列表
     */
    List<UserVO> getUserVOList(List<User> userList);

    /**
     * 将查询请求转为 QueryWrapper 对象
     */
    QueryWrapper getQueryWrapper(UserQueryRequest userQueryRequest);

    /**
     * 加密
     *
     * @param userPassword
     * @return
     */
    String getEncryptPassword(String userPassword);
}
