package com.sct.aicodegenerate.core.handler;

import com.sct.aicodegenerate.model.entity.User;
import com.sct.aicodegenerate.model.enums.CodeGenTypeEnum;
import com.sct.aicodegenerate.service.ChatHistoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Slf4j
@Component
public class StreamHandlerExecutor {

    @Resource
    private JsonMessageStreamHandler jsonMessageStreamHandler;

    /**
     * 创建流处理器处理聊天历史记录并
     */
    public Flux<String> doExecute(Flux<String> originFlux,
                                  ChatHistoryService chatHistoryService,
                                  long appId, User loginUser,
                                  CodeGenTypeEnum codeGenType){
        return switch (codeGenType){
            case VUE_PROJECT -> jsonMessageStreamHandler.handle(originFlux, chatHistoryService, appId, loginUser);
            case HTML , MULTI_FILE-> new SimpleTextStreamHandler().handle(originFlux, chatHistoryService, appId, loginUser);
        };
    }
}
