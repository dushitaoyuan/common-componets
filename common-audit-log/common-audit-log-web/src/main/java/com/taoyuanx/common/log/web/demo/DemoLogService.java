package com.taoyuanx.common.log.web.demo;

import com.taoyuanx.common.audit.log.common.OperateLog;
import org.springframework.stereotype.Component;


@Component
public class DemoLogService {
    @OperateLog(success = "'嵌套日志层级:' +#operateObject", bizType = "嵌套日志测试", operateObject = "#operateObject", subBizType = "subType", ignoreException = RuntimeException.class)
    public Long businessLog(Long operateObject) {
        return operateObject;
    }
}
