package com.sct.aicodegenerate.core.handler;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.sct.aicodegenerate.ai.model.message.*;
import com.sct.aicodegenerate.ai.tools.BaseTool;
import com.sct.aicodegenerate.ai.tools.ToolManager;
import com.sct.aicodegenerate.constant.AppConstant;
import com.sct.aicodegenerate.core.builder.VueProjectBuilder;
import com.sct.aicodegenerate.model.entity.User;
import com.sct.aicodegenerate.model.enums.ChatHistoryMessageTypeEnum;
import com.sct.aicodegenerate.service.ChatHistoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Component
public class JsonMessageStreamHandler {

    @Resource
    private VueProjectBuilder vueProjectBuilder;
    @Resource
    private ToolManager toolManager;

    /**
     * 处理 TokenStream（VUE_PROJECT）
     * 解析 JSON 消息并重组为完整的响应格式
     */
    public Flux<String> handle(Flux<String> originFlux,
                               ChatHistoryService chatHistoryService,
                               long appId, User loginUser){
        // 收集数据用于生成后端记忆格式
        StringBuilder chatHistoryStringBuilder = new StringBuilder();
        // 用于跟踪已经见过工具 ID，判断是否是第一次调用
        Set<String> seenToolIds = new HashSet<>();
        return originFlux
                .map(chunk->{
                    // 解析每个 JSON 消息块
                    return handleJsonMessageChunk(chunk, chatHistoryStringBuilder,seenToolIds);
                })
                .filter(StrUtil::isNotEmpty)
                .doOnComplete(() -> {
                    log.info("流式响应完成，保存数据");
                    // 保存数据
                    chatHistoryService.addChatMessage(appId, chatHistoryStringBuilder.toString(), ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
//                    // 异步构建 Vue 项目
//                    String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + "vue_project_" + appId;
//                    vueProjectBuilder.buildVueProject(projectPath);
                })
                .doOnError(e -> {
                    String errorMessage = "AI回复失败：" + e.getMessage();
                    chatHistoryService.addChatMessage(appId, errorMessage,ChatHistoryMessageTypeEnum.AI.getValue(),loginUser.getId());
                });
    }

    /**
     * 解析并收集 TokenStream 中的 JSON 消息块
     * @param chunk
     * @param chatHistoryStringBuilder
     * @return
     */
    private String handleJsonMessageChunk(String chunk, StringBuilder chatHistoryStringBuilder, Set<String> seenToolIds) {
        // 解析 JSON
        StreamMessage streamMessage = JSONUtil.toBean(chunk, StreamMessage.class);
        StreamMessageTypeEnum typeEnum = StreamMessageTypeEnum.getEnumByValue(streamMessage.getType());
        switch (typeEnum){
            case AI_RESPONSE -> {
                AiResponseMessage aiResponseMessage = JSONUtil.toBean(chunk, AiResponseMessage.class);
                String data = aiResponseMessage.getData();
                // 直接拼接响应
                chatHistoryStringBuilder.append(data);
                return data;
            }
            case TOOL_REQUEST -> {
                ToolRequestMessage toolRequestMessage = JSONUtil.toBean(chunk, ToolRequestMessage.class);
                String toolId = toolRequestMessage.getId();
                String toolName = toolRequestMessage.getName();
                // 检查是否是第一次看到这个工具 ID
                if (toolId!=null && !seenToolIds.contains(toolId)){
                    // 是第一次
                    seenToolIds.add(toolId);
                    // 根据工具名称获取工具实例
                    BaseTool tool = toolManager.getTool(toolName);
                    // 返回格式化的工具调用信息
                    return tool.generateToolRequestResponse();
                }else {
                    // 不是第一次
                    return "";
                }
            }
            case TOOL_EXECUTED -> {
                ToolExecutedMessage toolExecutedMessage = JSONUtil.toBean(chunk, ToolExecutedMessage.class);
                JSONObject jsonObject = JSONUtil.parseObj(toolExecutedMessage);
                String toolName = toolExecutedMessage.getName();
                // 根据工具名称获取工具实例并生成相应的结果格式
                BaseTool tool = toolManager.getTool(toolName);
                String result = tool.generateToolExecutedResult(jsonObject);
                // 输出前端和要持久化的内容
                String output = String.format("\n\n%s\n\n", result);
                chatHistoryStringBuilder.append(output);
                return output;
            }
            default -> {
                log.error("未知的流消息类型：{}", typeEnum);
                return "";
            }
        }
    }
}
