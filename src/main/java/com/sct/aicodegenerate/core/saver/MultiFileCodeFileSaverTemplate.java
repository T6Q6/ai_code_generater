package com.sct.aicodegenerate.core.saver;

import cn.hutool.core.util.StrUtil;
import com.sct.aicodegenerate.exception.BusinessException;
import com.sct.aicodegenerate.exception.ErrorCode;
import com.sct.aicodegenerate.exception.ThrowUtils;
import com.sct.aicodegenerate.model.entity.MultiFileCodeResult;
import com.sct.aicodegenerate.model.enums.CodeGenTypeEnum;

/**
 * 多文件代码保存器
 */
public class MultiFileCodeFileSaverTemplate extends CodeFileSaverTemplate<MultiFileCodeResult> {
    @Override
    protected void saveFiles(String baseDirPath, MultiFileCodeResult result) {
        writToFile(baseDirPath, "index.html", result.getHtmlCode());
        writToFile(baseDirPath, "style.css", result.getCssCode());
        writToFile(baseDirPath, "script.js", result.getJsCode());
    }

    @Override
    protected CodeGenTypeEnum getCodeType() {
        return CodeGenTypeEnum.MULTI_FILE;
    }

    @Override
    protected void validateInput(MultiFileCodeResult result) {
        super.validateInput(result);
        //至少要有 HTML 代码，CSS 和 JS 可以为空
        ThrowUtils.throwIf(StrUtil.isBlank(result.getHtmlCode()),
                new BusinessException(ErrorCode.SYSTEM_ERROR, "HTML 代码不能为空"));
    }
}
