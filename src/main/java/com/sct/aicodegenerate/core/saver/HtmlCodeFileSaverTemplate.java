package com.sct.aicodegenerate.core.saver;

import cn.hutool.core.util.StrUtil;
import com.sct.aicodegenerate.exception.BusinessException;
import com.sct.aicodegenerate.exception.ErrorCode;
import com.sct.aicodegenerate.exception.ThrowUtils;
import com.sct.aicodegenerate.model.entity.HtmlCodeResult;
import com.sct.aicodegenerate.model.enums.CodeGenTypeEnum;

/**
 * HTML 模式代码保存器
 */
public class HtmlCodeFileSaverTemplate extends CodeFileSaverTemplate<HtmlCodeResult> {
    @Override
    protected void saveFiles(String baseDirPath, HtmlCodeResult result) {
        //保存HTML文件
        writToFile(baseDirPath, "index.html", result.getHtmlCode());
    }

    @Override
    protected CodeGenTypeEnum getCodeType() {
        return CodeGenTypeEnum.HTML;
    }

    @Override
    protected void validateInput(HtmlCodeResult result) {
        super.validateInput(result);
        // HTML 代码不能为空
        ThrowUtils.throwIf(StrUtil.isBlank(result.getHtmlCode()),
                new BusinessException(ErrorCode.SYSTEM_ERROR, "HTML代码不能为空"));
    }
}
