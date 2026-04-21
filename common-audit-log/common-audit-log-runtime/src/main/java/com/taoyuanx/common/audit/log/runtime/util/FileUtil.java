package com.taoyuanx.common.audit.log.runtime.util;

import lombok.extern.slf4j.Slf4j;

import java.io.File;

/**
 *
 *
 *
 * @author taoyuanx
 * @date 2026/4/21 10:47
 */
@Slf4j
public class FileUtil {

    public static void foreMkDirs(String filePath) {
        try {
            File file = new File(filePath);
            if (file.exists()) {
                return;
            }
            file.mkdirs();
        } catch (Exception e) {
            log.error("foreMkDirs  error", e);
        }
    }


}
