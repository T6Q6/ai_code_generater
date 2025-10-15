package com.sct.aicodegenerate.core.handler;

import cn.hutool.core.util.StrUtil;
import com.sct.aicodegenerate.model.entity.User;
import com.sct.aicodegenerate.model.enums.ChatHistoryMessageTypeEnum;
import com.sct.aicodegenerate.service.ChatHistoryService;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
public class SimpleTextStreamHandler {

    /**
     * 处理传统流（HTML，MULTI_FILE）
     */
    public Flux<String> handle(Flux<String> originFlux,
                               ChatHistoryService chatHistoryService,
                               long appId, User loginUser){
        StringBuilder aiResponseBuilder = new StringBuilder();
        return originFlux.map(chunk -> {
            aiResponseBuilder.append(chunk);
            return chunk;
        }).doOnComplete(() -> {
            // 流式响应完成后保存
            String aiResponse = aiResponseBuilder.toString();
            if (StrUtil.isNotBlank(aiResponse)) {
                chatHistoryService.addChatMessage(appId, aiResponse, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
            }
        }).doOnError(e -> {
            // AI 响应错误记录
            String errorMessage = "AI回复失败：" + e.getMessage();
            chatHistoryService.addChatMessage(appId, errorMessage, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
        });
    }
}
