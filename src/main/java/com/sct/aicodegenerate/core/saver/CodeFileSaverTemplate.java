package com.sct.aicodegenerate.core.saver;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.sct.aicodegenerate.constant.AppConstant;
import com.sct.aicodegenerate.exception.ErrorCode;
import com.sct.aicodegenerate.exception.ThrowUtils;
import com.sct.aicodegenerate.model.enums.CodeGenTypeEnum;

import java.io.File;

/**
 * 抽象代码文件保存器 - 模板方法模式
 */
public abstract class CodeFileSaverTemplate<T> {
    //文件保存根目录
    private static final String FILE_SAVE_ROOT_DIR = AppConstant.CODE_OUTPUT_ROOT_DIR;

    /**
     * 模板方法
     */
    public final File saveCode(T result, Long appId) {
        // 1.验证输入
        validateInput(result);
        // 2.构建唯一目录
        String baseDirPath = buildUniqueDir(appId);
        // 3.保存文件
        saveFiles(baseDirPath, result);
        // 4.返回目录文件对象
        return new File(baseDirPath);
    }

    /**
     * 写入单个文件
     *
     * @param dirPath
     * @param fileName
     * @param content
     */
    protected void writToFile(String dirPath, String fileName, String content) {
        String filePath = dirPath + File.separator + fileName;
        FileUtil.writeUtf8String(content, filePath);
    }

    /**
     * 验证输入
     *
     * @param result
     */
    protected void validateInput(T result) {
        ThrowUtils.throwIf(result == null, ErrorCode.PARAMS_ERROR);
    }

    /**
     * 构建唯一目录路径
     */
    protected String buildUniqueDir(Long appId) {
        String bizType = getCodeType().getValue();
//        String uniqueDirPath = StrUtil.format("{}_{}", bizType, IdUtil.getSnowflakeNextIdStr());
        String uniqueDirPath = StrUtil.format("{}_{}", bizType, appId);
        String dirPath = FILE_SAVE_ROOT_DIR + File.separator + uniqueDirPath;
        FileUtil.mkdir(dirPath);
        return dirPath;
    }

    /**
     * 子类实现保存文件
     *
     * @param baseDirPath
     * @param result
     */
    protected abstract void saveFiles(String baseDirPath, T result);

    /**
     * 获取代码生成类型
     *
     * @return
     */
    protected abstract CodeGenTypeEnum getCodeType();
}
