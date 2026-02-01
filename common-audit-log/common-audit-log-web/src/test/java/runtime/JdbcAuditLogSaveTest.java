package runtime;

import com.alibaba.fastjson2.JSON;
import com.taoyuanx.common.audit.log.model.AuditLogModel;
import com.taoyuanx.common.audit.log.model.AuditLogQueryModel;
import com.taoyuanx.common.audit.log.model.PageModel;
import com.taoyuanx.common.audit.log.runtime.single.JdbcTemplateSqlLogService;
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
    private JdbcTemplateSqlLogService jdbcTemplateSingleLogService;

    @Autowired
    private DemoLogService demoLogService;

    @Test
    public void logSave() throws InterruptedException {
        demoLogService.businessLog(10L);
        Thread.currentThread().sleep(100000L);
    }

    @Test
    public void auditLogQuery() {
        AuditLogQueryModel query = new AuditLogQueryModel();
        query.setPageNum(1);
        query.setPageSize(10);
        PageModel<AuditLogModel> page = jdbcTemplateSingleLogService.page(query);
        System.out.println(JSON.toJSONString(page));
    }
}
