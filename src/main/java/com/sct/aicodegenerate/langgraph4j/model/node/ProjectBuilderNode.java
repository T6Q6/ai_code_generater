package com.sct.aicodegenerate.langgraph4j.model.node;

import com.sct.aicodegenerate.core.builder.VueProjectBuilder;
import com.sct.aicodegenerate.exception.BusinessException;
import com.sct.aicodegenerate.exception.ErrorCode;
import com.sct.aicodegenerate.util.SpringContextUtil;
import com.sct.aicodegenerate.langgraph4j.state.WorkflowContext;
import com.sct.aicodegenerate.model.enums.CodeGenTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.io.File;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
public class ProjectBuilderNode {
    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 项目构建");
            
            // 获取必要的参数
            String generatedCodeDir = context.getGeneratedCodeDir();
            CodeGenTypeEnum generationType = context.getGenerationType();
            String buildResultDir;
            // Vue 项目类型：使用 VueProjectBuilder 进行构建
            if (generationType == CodeGenTypeEnum.VUE_PROJECT){
                try {
                    VueProjectBuilder vueProjectBuilder = SpringContextUtil.getBean(VueProjectBuilder.class);
                    // 执行 Vue 项目构建（npm install + npm run build）
                    boolean buildSuccess = vueProjectBuilder.buildVueProject(generatedCodeDir);
                    if (buildSuccess){
                        // 构建成功，返回 dist 目录路径
                        buildResultDir = generatedCodeDir + File.separator + "dist";
                        log.info("项目构建成功，结果目录: {}", buildResultDir);
                    }else {
                        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "项目构建失败");
                    }
                } catch (BusinessException e) {
                    log.error("项目构建失败：{}", e.getMessage());
                    buildResultDir = generatedCodeDir;
                }
            }else {
                // HTML 和MULTI_FILE 代码已经保存好了
                buildResultDir = generatedCodeDir;
            }

            // 更新状态
            context.setCurrentStep("项目构建");
            context.setBuildResultDir(buildResultDir);
            log.info("项目构建完成，结果目录: {}", buildResultDir);
            return WorkflowContext.saveContext(context);
        });
    }
}
