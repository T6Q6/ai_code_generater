package com.sct.aicodegenerate.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.sct.aicodegenerate.ai.AiCodeGenTypeRoutingService;
import com.sct.aicodegenerate.ai.AiCodeGenTypeRoutingServiceFactory;
import com.sct.aicodegenerate.ai.AiCodeGeneratorService;
import com.sct.aicodegenerate.constant.AppConstant;
import com.sct.aicodegenerate.core.AiCodeGeneratorFacade;
import com.sct.aicodegenerate.core.builder.VueProjectBuilder;
import com.sct.aicodegenerate.core.handler.StreamHandlerExecutor;
import com.sct.aicodegenerate.exception.BusinessException;
import com.sct.aicodegenerate.exception.ErrorCode;
import com.sct.aicodegenerate.exception.ThrowUtils;
import com.sct.aicodegenerate.langgraph4j.CodeGenConcurrentWorkflow;
import com.sct.aicodegenerate.model.dto.app.AppAddRequest;
import com.sct.aicodegenerate.model.dto.app.AppQueryRequest;
import com.sct.aicodegenerate.model.entity.App;
import com.sct.aicodegenerate.mapper.AppMapper;
import com.sct.aicodegenerate.model.entity.User;
import com.sct.aicodegenerate.model.enums.ChatHistoryMessageTypeEnum;
import com.sct.aicodegenerate.model.enums.CodeGenTypeEnum;
import com.sct.aicodegenerate.model.vo.AppVO;
import com.sct.aicodegenerate.model.vo.UserVO;
import com.sct.aicodegenerate.service.AppService;
import com.sct.aicodegenerate.service.ChatHistoryService;
import com.sct.aicodegenerate.service.ScreenshotService;
import com.sct.aicodegenerate.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 应用 服务层实现。
 *
 * @author SCT
 */
@Service
@Slf4j
public class AppServiceImpl extends ServiceImpl<AppMapper, App> implements AppService {

    @Resource
    private UserService userService;
    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;
    @Resource
    private ChatHistoryService chatHistoryService;
    @Resource
    private StreamHandlerExecutor streamHandlerExecutor;
    @Resource
    private VueProjectBuilder vueProjectBuilder;
    @Resource
    private ScreenshotService screenshotService;
    @Resource
    private AiCodeGenTypeRoutingServiceFactory aiCodeGenTypeRoutingServiceFactory;

    /**
     * 将 App 转为 AppVO
     *
     * @param app 实体
     * @return AppVO
     */
    @Override
    public AppVO getAppVO(App app) {
        if (app == null) {
            return null;
        }
        AppVO appVO = new AppVO();
        BeanUtils.copyProperties(app, appVO);
        //关联查询用户信息
        Long userId = app.getUserId();
        if (userId != null) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            appVO.setUser(userVO);
        }
        return appVO;
    }

    /**
     * 获取查询条件
     *
     * @param appQueryRequest
     * @return
     */
    @Override
    public QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        Long id = appQueryRequest.getId();
        String appName = appQueryRequest.getAppName();
        String cover = appQueryRequest.getCover();
        String initPrompt = appQueryRequest.getInitPrompt();
        String codeGenType = appQueryRequest.getCodeGenType();
        String deployKey = appQueryRequest.getDeployKey();
        Integer priority = appQueryRequest.getPriority();
        Long userId = appQueryRequest.getUserId();
        int pageNum = appQueryRequest.getPageNum();
        int pageSize = appQueryRequest.getPageSize();
        String sortField = appQueryRequest.getSortField();
        String sortOrder = appQueryRequest.getSortOrder();

        return QueryWrapper.create()
                .eq(App::getId, id)
                .eq(App::getCodeGenType, codeGenType)
                .eq(App::getUserId, userId)
                .eq(App::getPriority, priority)
                .eq(App::getDeployKey, deployKey)
                .like(App::getAppName, appName)
                .like(App::getCover, cover)
                .like(App::getInitPrompt, initPrompt)
                .orderBy(sortField, "ascend".equals(sortOrder));
    }

    /**
     * 获取 AppVO 列表
     *
     * @param appList
     * @return
     */
    @Override
    public List<AppVO> getAppVOList(List<App> appList) {
        ThrowUtils.throwIf(appList == null, ErrorCode.PARAMS_ERROR);
        //批量获取用户信息，避免 N+1 查询问题
        Set<Long> userIds = appList.stream()
                .map(App::getUserId)
                .collect(Collectors.toSet());
        Map<Long, UserVO> userVOMap = userService.listByIds(userIds)
                .stream()
                .collect(Collectors.toMap(User::getId, user -> userService.getUserVO(user)));
        return appList.stream()
                .map(app -> {
                    AppVO appVO = getAppVO(app);
                    UserVO userVO = userVOMap.get(app.getUserId());
                    appVO.setUser(userVO);
                    return appVO;
                }).collect(Collectors.toList());
    }

    /**
     * 应用聊天生成代码
     *
     * @param appId
     * @param message
     * @param loginUser
     * @param agent 是否启用agent模式
     * @return
     */
    @Override
    public Flux<String> chatToGenCode(Long appId, String message, User loginUser,Boolean agent) {
        // 1.参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "用户消息不能为空");
        // 2.查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        // 3.验证用户是否有权限访问该应用，仅本人可以生成代码
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 4.获取应用的代码生成类型
        String codeGenType = app.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        ThrowUtils.throwIf(codeGenTypeEnum == null, ErrorCode.PARAMS_ERROR);
        // 5.添加用户消息到对话历史
        chatHistoryService.addChatMessage(appId, message, ChatHistoryMessageTypeEnum.USER.getValue(), loginUser.getId());
        // 6.根据是否为 Agent 模式，执行不同的工作流
        Flux<String> codeStream;
        if (agent){
            // Agent 模式
            codeStream = new CodeGenConcurrentWorkflow().executeWorkflowWithFlux(message,appId);
        }else {
            // 调用 AI 生成代码（流式）
            codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream(message, codeGenTypeEnum, appId);
        }
        // 7.收集 AI 响应的流式内容，并保存到数据库
        return streamHandlerExecutor.doExecute(codeStream,chatHistoryService,appId,loginUser,codeGenTypeEnum);
    }

    /**
     * 应用部署
     *
     * @param appId
     * @param loginUser
     * @return
     */
    @Override
    public String deployApp(Long appId, User loginUser) {
        // 1.参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        // 2.查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        // 3.验证用户是否有权限访问该应用，仅本人可以部署
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 4.检查是否已有 deployKey
        String deployKey = app.getDeployKey();
        // 没有则生成 6 位 deployKey（大小写字母 + 数字）
        if (StrUtil.isBlank(deployKey)) {
            deployKey = RandomUtil.randomString(6);
        }
        // 5.获取代码生成类型，构建源目录路径
        String codeGenType = app.getCodeGenType();
        String sourceDirName = codeGenType + "_" + appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
        // 6.检查目录是否存在
        File sourceDir = new File(sourceDirPath);
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "源目录不存在");
        }
        // 7.Vue 项目特殊处理: 执行构建
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT){
            // Vue 项目需要构建
            boolean buildSuccess = vueProjectBuilder.buildVueProject(sourceDirPath);
            ThrowUtils.throwIf(!buildSuccess, ErrorCode.SYSTEM_ERROR, "构建失败");
            // 检查 dist 目录是否存在
            File distDir = new File(sourceDirPath, "dist");
            ThrowUtils.throwIf(!distDir.exists() || !distDir.isDirectory(), ErrorCode.SYSTEM_ERROR, "dist 目录不存在");
            // 将 dist 目录复制到部署目录
            sourceDir = distDir;
            log.info("构建成功，开始复制文件到部署 dist 目录: {}", distDir.getAbsolutePath());
        }
        // 8.复制文件到部署目录
        String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;
        try {
            FileUtil.copyContent(sourceDir, new File(deployDirPath), true);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "部署失败: " + e.getMessage());
        }
        // 9.更新应用的 deployKey 和部署时间
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setDeployKey(deployKey);
        updateApp.setDeployedTime(LocalDateTime.now());
        boolean result = this.updateById(updateApp);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        // 10.构建应用访问 url
        String deployUrl = String.format("%s/%s/", AppConstant.CODE_DEPLOY_HOST, deployKey);
        // 11.异步生成截图并更新应用封面
        generateAppScreenshotAsync(appId, deployUrl);
        // 12.返回可访问的 url
        return deployUrl;
    }

    /**
     * 添加应用
     *
     * @param addRequest
     * @param request
     * @return
     */
    @Override
    public Long addApp(AppAddRequest addRequest, HttpServletRequest request) {
        String initPrompt = addRequest.getInitPrompt();
        ThrowUtils.throwIf(StrUtil.isBlank(initPrompt), ErrorCode.PARAMS_ERROR, "初始化 prompt 不能为空");
        //获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        //构造入库对象
        App app = new App();
        BeanUtils.copyProperties(addRequest, app);
        app.setUserId(loginUser.getId());
        // TODO 应用名称暂时为 initPrompt 前 12 位
        app.setAppName(initPrompt.substring(0, Math.min(12, initPrompt.length())));
        // 使用 AI 智能选择代码生成类型
        AiCodeGenTypeRoutingService routingService = aiCodeGenTypeRoutingServiceFactory.createAiCodeGenTypeRoutingService();
        CodeGenTypeEnum codeGenTypeEnum = routingService.routeCodeGenType(initPrompt);
        app.setCodeGenType(codeGenTypeEnum.getValue());
        //插入数据库
        boolean result = this.save(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return app.getId();
    }



    /**
     * 异步生成应用截图
     * @param appId
     * @param deployUrl
     */
    private void generateAppScreenshotAsync(Long appId, String deployUrl) {
        // 使用虚拟线程异步执行
        Thread.ofVirtual().start(() -> {
            // 调用截图服务生成截图并上传
            String screenshotUrl = screenshotService.generateAndUploadScreenshot(deployUrl);
            // 更新应用封面字段
            App updateApp = new App();
            updateApp.setId(appId);
            updateApp.setCover(screenshotUrl);
            boolean result = this.updateById(updateApp);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        });
    }

    /**
     * 删除应用
     *
     * @param id
     * @return
     */
    @Override
    public boolean removeById(Serializable id) {
        if (id == null) {
            return false;
        }
        // 转换为 Long 类型
        Long appId = Long.valueOf(id.toString());
        if (appId <= 0) {
            return false;
        }
        // 先删除关联的对话历史
        try {
            chatHistoryService.deleteByApp(appId);
        } catch (Exception e) {
            // 记录日志但不阻止应用的删除
            log.error("删除关联的对话历史失败：{}", e.getMessage());
        }
        // 删除应用
        return super.removeById(appId);
    }
}
