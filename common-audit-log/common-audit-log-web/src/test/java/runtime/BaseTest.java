package runtime;

import com.taoyuanx.common.log.web.AuditLogWebStartApplication;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * @author taoyuan
 * @date 2025/7/11 10:44
 */
@Slf4j
@SpringBootTest(classes = AuditLogWebStartApplication.class)
@SpringJUnitConfig
public abstract class BaseTest {

}
