package runtime;

import com.alibaba.fastjson2.JSON;
import com.taoyuanx.common.audit.log.model.AuditLogModel;
import com.taoyuanx.common.audit.log.runtime.ext.AuditLogQueryService;
import com.taoyuanx.common.audit.log.runtime.model.AuditLogQueryModel;
import com.taoyuanx.common.audit.log.runtime.model.AuditLogViewModel;
import com.taoyuanx.common.audit.log.runtime.model.PageModel;
import com.taoyuanx.common.audit.log.runtime.model.PageQueryModel;
import com.taoyuanx.common.log.web.demo.DemoLogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * <p></p>
 *
 * @author taoyuan
 * @date 2025/7/28 18:10
 */
public class JdbcAuditLogSaveTest extends BaseTest {


    @Autowired
    private AuditLogQueryService auditLogQueryService;

    @Autowired
    private DemoLogService demoLogService;

    @Test
    public void logSave() throws InterruptedException {
        demoLogService.businessLog(10L);
        Thread.currentThread().sleep(100000L);
    }

    @Test
    public void auditLogQuery() {
        PageQueryModel<AuditLogQueryModel> query=new PageQueryModel<AuditLogQueryModel>();
        query.setPageNum(1);
        query.setPageSize(10);
        PageModel<AuditLogViewModel> page = auditLogQueryService.page(query);
        System.out.println(JSON.toJSONString(page));
    }
}
