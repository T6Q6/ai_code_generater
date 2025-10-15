package com.sct.aicodegenerate.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.sct.aicodegenerate.model.dto.app.AppAddRequest;
import com.sct.aicodegenerate.model.dto.app.AppQueryRequest;
import com.sct.aicodegenerate.model.entity.App;
import com.sct.aicodegenerate.model.entity.User;
import com.sct.aicodegenerate.model.vo.AppVO;
import jakarta.servlet.http.HttpServletRequest;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 应用 服务层。
 *
 * @author SCT
 */
public interface AppService extends IService<App> {

    /**
     * 将 APP 转换为 VO
     */
    AppVO getAppVO(App app);

    /**
     * 构造查询对象
     */
    QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);

    /**
     * 将 APP 列表转换为 VO 列表
     */
    List<AppVO> getAppVOList(List<App> appList);

    /**
     * 应用生成
     */
    Flux<String> chatToGenCode(Long appId, String message, User loginUser,Boolean agent);

    /**
     * 部署服务
     */
    String deployApp(Long appId, User loginUser);

    /**
     * 添加应用
     */
    Long addApp(AppAddRequest addRequest, HttpServletRequest request);

}
