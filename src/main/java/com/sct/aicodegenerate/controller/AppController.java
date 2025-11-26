package com.sct.aicodegenerate.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.sct.aicodegenerate.annotation.AuthCheck;
import com.sct.aicodegenerate.common.BaseResponse;
import com.sct.aicodegenerate.common.DeleteRequest;
import com.sct.aicodegenerate.common.ResultUtils;
import com.sct.aicodegenerate.constant.AppConstant;
import com.sct.aicodegenerate.constant.UserConstant;
import com.sct.aicodegenerate.exception.BusinessException;
import com.sct.aicodegenerate.exception.ErrorCode;
import com.sct.aicodegenerate.exception.ThrowUtils;
import com.sct.aicodegenerate.model.dto.app.*;
import com.sct.aicodegenerate.model.entity.User;
import com.sct.aicodegenerate.model.enums.CodeGenTypeEnum;
import com.sct.aicodegenerate.model.enums.RateLimitType;
import com.sct.aicodegenerate.model.enums.UserRoleEnum;
import com.sct.aicodegenerate.model.vo.AppVO;
import com.sct.aicodegenerate.ratelimiter.annotation.RateLimit;
import com.sct.aicodegenerate.service.ProjectDownloadService;
import com.sct.aicodegenerate.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import com.sct.aicodegenerate.model.entity.App;
import com.sct.aicodegenerate.service.AppService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 应用 控制层。
 *
 * @author SCT
 */
@RestController
@RequestMapping("/app")
@Tag(name = "应用接口")
public class AppController {

    @Resource
    private AppService appService;
    @Resource
    private UserService userService;
    @Resource
    private ProjectDownloadService projectDownloadService;

    /**
     * 创建应用
     */
    @PostMapping("/add")
    @Operation(summary = "创建应用")
    public BaseResponse<Long> addApp(@RequestBody AppAddRequest addRequest,
                                     HttpServletRequest request) {
        ThrowUtils.throwIf(addRequest == null, ErrorCode.PARAMS_ERROR);
        Long appId =appService.addApp(addRequest, request);
        return ResultUtils.success(appId);
    }

    /**
     * 更新应用（用户只能更新自己的应用名称）
     */
    @PostMapping("/update")
    @Operation(summary = "更新应用（用户只能更新自己的应用名称）")
    public BaseResponse<Boolean> updateApp(@RequestBody AppUpdateRequest appUpdateRequest,
                                           HttpServletRequest request) {
        ThrowUtils.throwIf(appUpdateRequest == null || appUpdateRequest.getId() == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        Long id = appUpdateRequest.getId();
        //判断是否存在
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        //仅本人可以更新
        if (!oldApp.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        App app = new App();
        BeanUtils.copyProperties(appUpdateRequest, app);
        //设置编辑时间
        app.setEditTime(LocalDateTime.now());
        boolean result = appService.updateById(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 删除应用（用户只能删除自己的应用）
     */
    @PostMapping("/delete")
    @Operation(summary = "删除应用（用户只能删除自己的应用）")
    public BaseResponse<Boolean> deleteApp(@RequestBody DeleteRequest deleteRequest,
                                           HttpServletRequest request) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        Long id = deleteRequest.getId();
        //判断是否存在
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        //仅本人或管理员可以删除
        if (!oldApp.getUserId().equals(loginUser.getId()) && !UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = appService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 获取应用详情
     */
    @GetMapping("/get/vo")
    @Operation(summary = "根据 id 获取应用详情")
    public BaseResponse<AppVO> getAppVOById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        //查询数据库
        App app = appService.getById(id);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        //获取封装类并返回
        return ResultUtils.success(appService.getAppVO(app));
    }

    /**
     * 分页获取当前用户创建的应用列表
     */
    @PostMapping("/list/page/vo")
    @Operation(summary = "分页获取当前用户创建的应用列表")
    public BaseResponse<Page<AppVO>> listAppVOByPage(@RequestBody AppQueryRequest appQueryRequest,
                                                     HttpServletRequest request) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        //限制每页最多 20 个，防止爬虫
        long pageSize = appQueryRequest.getPageSize();
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR, "每页最多查询 20 个");
        long pageNum = appQueryRequest.getPageNum();
        //只查询当前用户的应用
        appQueryRequest.setUserId(loginUser.getId());
        QueryWrapper queryWrapper = appService.getQueryWrapper(appQueryRequest);
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize), queryWrapper);
        //数据封装
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalPage());
        List<AppVO> appVOList = appService.getAppVOList(appPage.getRecords());
        appVOPage.setRecords(appVOList);
        return ResultUtils.success(appVOPage);
    }

    /**
     * 分页获取精选应用列表
     */
    @Operation(summary = "分页获取精选应用列表")
    @PostMapping("/good/list/page/vo")
    @Cacheable(value = "good_app_page",
    key = "T(com.sct.aicodegenerate.util.CacheKeyUtils).generateCacheKey(#appQueryRequest)",
    condition = "#appQueryRequest.pageNum <= 10")
    public BaseResponse<Page<AppVO>> listGoodAppVOByPage(@RequestBody AppQueryRequest appQueryRequest) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        //限制每页最多 20 个，防止爬虫
        long pageSize = appQueryRequest.getPageSize();
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR, "每页最多查询 20 个");
        long pageNum = appQueryRequest.getPageNum();
        //只查询精选的应用
        appQueryRequest.setPriority(AppConstant.GOOD_APP_PRIORITY);
        QueryWrapper queryWrapper = appService.getQueryWrapper(appQueryRequest);
        //分页查询
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize), queryWrapper);
        //数据封装
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalPage());
        List<AppVO> appVOList = appService.getAppVOList(appPage.getRecords());
        appVOPage.setRecords(appVOList);
        return ResultUtils.success(appVOPage);
    }

    /**
     * 管理员删除应用
     */
    @PostMapping("/admin/delete")
    @Operation(summary = "管理员删除应用")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteApp(@RequestBody DeleteRequest deleteRequest) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);
        Long id = deleteRequest.getId();
        //判断是否存在
        App app = appService.getById(id);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = appService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 管理员更新应用
     */
    @PostMapping("/admin/update")
    @Operation(summary = "管理员更新应用")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateApp(@RequestBody AppAdminUpdateRequest appAdminUpdateRequest) {
        ThrowUtils.throwIf(appAdminUpdateRequest == null || appAdminUpdateRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);
        Long id = appAdminUpdateRequest.getId();
        //判断是否存在
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        App app = new App();
        BeanUtils.copyProperties(appAdminUpdateRequest, app);
        //设置编辑时间
        app.setEditTime(LocalDateTime.now());
        boolean result = appService.updateById(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 管理员分页查询应用
     */
    @PostMapping("/admin/list/page/vo")
    @Operation(summary = "管理员分页查询应用")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<AppVO>> listAppVOByPage(@RequestBody AppQueryRequest appQueryRequest) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long pageNum = appQueryRequest.getPageNum();
        long pageSize = appQueryRequest.getPageSize();
        QueryWrapper queryWrapper = appService.getQueryWrapper(appQueryRequest);
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize), queryWrapper);
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalPage());
        List<AppVO> appVOList = appService.getAppVOList(appPage.getRecords());
        appVOPage.setRecords(appVOList);
        return ResultUtils.success(appVOPage);
    }

    /**
     * 管理员根据 id 查看应用详情
     */
    @GetMapping("/admin/get/vo")
    @Operation(summary = "管理员根据 id 查看应用详情")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<AppVO> getAppVOByIdByAdmin(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        App app = appService.getById(id);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(appService.getAppVO(app));
    }

    /**
     * 应用聊天生成代码（流式 SSE）
     */
    @GetMapping(value = "chat/gen/code", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimit(limitType = RateLimitType.USER,rate = 5,rateInterval = 60,message = "请勿频繁请求，请稍后再试")
    public Flux<ServerSentEvent<String>> chatGenCode(@RequestParam("appId") Long appId,
                                                     @RequestParam("message") String message,
                                                     @RequestParam(defaultValue = "false") boolean agent,
                                                     HttpServletRequest request) {
        // 校验参数
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "用户消息不能为空");
        User loginUser = userService.getLoginUser(request);
        //调用服务生成代码（流式）
        Flux<String> contentFlux = appService.chatToGenCode(appId, message, loginUser,agent);
        //转换为SSE格式
        return contentFlux.map(chunk -> {
            //将内容包装成JSON对象（解决空格不能被正确输出的问题）
            Map<String, String> wrapper = Map.of("d", chunk);
            String jsonData = JSONUtil.toJsonStr(wrapper);
            return ServerSentEvent.<String>builder()
                    .data(jsonData)
                    .build();
        }).concatWith(Mono.just(
                //发送结束事件
                ServerSentEvent.<String>builder()
                        .event("done")
                        .data("")
                        .build()
        ));
    }

    /**
     * 应用部署
     */
    @PostMapping("/deploy")
    @Operation(summary = "应用部署")
    public BaseResponse<String> deployApp(@RequestBody AppDeployRequest appDeployRequest,
                                          HttpServletRequest request) {
        ThrowUtils.throwIf(appDeployRequest == null, ErrorCode.PARAMS_ERROR);
        Long appId = appDeployRequest.getAppId();
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR);
        //获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        //调用服务部署
        String deployUrl = appService.deployApp(appId, loginUser);
        return ResultUtils.success(deployUrl);
    }

    /**
     * 下载应用代码
     */
    @GetMapping("/download/{appId}")
    @Operation(summary = "下载应用代码")
    public void downloadAppCode(@PathVariable("appId") Long appId,
            HttpServletRequest request,
            HttpServletResponse response){
        // 1.基础校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR);
        // 2.查询应用信息
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        // 3.权限校验
        User loginUser = userService.getLoginUser(request);
        if (!app.getUserId().equals(loginUser.getId())){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 4.构建应用代码路径（生成目录，非部署目录）
        String codeGenType = app.getCodeGenType();
        String sourceDirName =codeGenType+"_"+appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR+ File.separator+sourceDirName;
        // 5.检查代码目录是否存在
        File sourceDir = new File(sourceDirPath);
        ThrowUtils.throwIf(!sourceDir.exists()||!sourceDir.isDirectory()
                , ErrorCode.NOT_FOUND_ERROR);
        // 6.生成下载文件名
        String downloadFileName = String.valueOf(appId);
        // 7.调用通用下载服务
        projectDownloadService.downloadProjectAsZip(sourceDirPath, downloadFileName, response);
    }
}
