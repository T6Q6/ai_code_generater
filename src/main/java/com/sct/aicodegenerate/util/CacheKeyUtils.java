package com.sct.aicodegenerate.util;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;

public class CacheKeyUtils {

    /**
     * 根据对象生成缓存key（JSON + MD5）
     */
    public static String generateCacheKey(Object obj) {
        if (obj == null){
            return DigestUtil.md5Hex("null");
        }
        // 先转JSON，再MD5
        String jsonStr = JSONUtil.toJsonStr(obj);
        return DigestUtil.md5Hex(jsonStr);
    }
}
