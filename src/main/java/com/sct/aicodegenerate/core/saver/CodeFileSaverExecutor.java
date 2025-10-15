package com.sct.aicodegenerate.core.saver;

import com.sct.aicodegenerate.exception.BusinessException;
import com.sct.aicodegenerate.exception.ErrorCode;
import com.sct.aicodegenerate.model.entity.HtmlCodeResult;
import com.sct.aicodegenerate.model.entity.MultiFileCodeResult;
import com.sct.aicodegenerate.model.enums.CodeGenTypeEnum;

import java.io.File;

/**
 * 代码保存执行器
 */
public class CodeFileSaverExecutor {
    private static final HtmlCodeFileSaverTemplate htmlCodeFileSaverTemplate = new HtmlCodeFileSaverTemplate();
    private static final MultiFileCodeFileSaverTemplate multiFileCodeFileSaverTemplate = new MultiFileCodeFileSaverTemplate();

    /**
     * 保存代码
     */
    public static File executeSaver(Object codeResult, CodeGenTypeEnum codeGenType, Long appId) {
        return switch (codeGenType) {
            case HTML -> htmlCodeFileSaverTemplate.saveCode((HtmlCodeResult) codeResult, appId);
            case MULTI_FILE -> multiFileCodeFileSaverTemplate.saveCode((MultiFileCodeResult) codeResult, appId);
            default ->
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的生成类型: " + codeGenType.getValue());
        };
    }
}
