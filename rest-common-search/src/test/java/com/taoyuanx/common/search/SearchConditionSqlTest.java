package com.taoyuanx.common.search;

import com.alibaba.fastjson2.JSON;
import com.taoyuanx.common.search.util.SearchConditionSqlUtil;
import com.taoyuanx.common.search.dto.query.SearchCondition;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;


/**
 * @author dushitaoyuan
 * @date 2025/1/10 17:19
 */
public class SearchConditionSqlTest {
    @Test
    public void buildSimpleSqlTest() {
        String json = "{\n" + "    \"conditions\": [\n" + "        {\n" + "            \"name\": \"subjectId\",\n" + "            \"value\": \"1\"\n" + "    \n" + "        },\n" + "        {\n" + "            \"name\": \"status\",\n" + "            \"value\": \"1\"\n" + "        }\n" + "    ],\n" + "    \"composeType\": \"AND\",\n" + "    \"sorts\": [\n" + "        {\n" + "            \"name\": \"createTime\",\n" + "            \"type\": \"ASC\",\n" + "            \"order\": 1\n" + "        }\n" + "    ]\n" + "}";
        SearchCondition searchCondition = JSON.parseObject(json, SearchCondition.class);


        String sql = SearchConditionSqlUtil.buildConditionSql(searchCondition, true);
        String expectSql = " WHERE subject_id = '1' AND status = '1' ORDER BY create_time ASC";
        System.out.println(sql);
        Assertions.assertEquals(expectSql, sql);
    }

    @Test
    public void buildSqlWithValueTypeTest() throws IOException {
        String sql = buildConditionSql("simple_with_value_type.json");
        String expectSql = " WHERE id = 1 AND name like '%name%' AND age = 19 ORDER BY id ASC";
        System.out.println(sql);
        Assertions.assertEquals(expectSql, sql);
    }

    @Test
    public void buildSqlWithMultipleAndOperate() throws Exception {
        String sql = buildConditionSql("complex.json");
        System.out.println(sql);

        String expectSql = " WHERE subject_id = 1 AND name like '%name%' AND name like 'name%' AND name like '%name' AND status in (1,2,3) AND status not in (1,2,3) AND name not in ('张三','李四') AND create_time BETWEEN '2024-11-08' AND '2024-11-09' AND (subject_id = 1 OR subject_id = 2 OR (subject_id = 3 OR subject_id = 4)) ORDER BY create_time ASC";
        Assertions.assertEquals(expectSql, sql);
    }

    @Test
    public void buildSqlWithOperate() throws Exception {
        String sql = buildConditionSql("simple_operate.json");
        System.out.println(sql);
        String expectSql =" WHERE id = 1 AND name like '%name%' AND name like 'name%' AND name like '%name' AND create_time BETWEEN '2024-12-13' AND '2024-12-14' AND id in (1,2,3) AND name in ('张三','李四') AND id not in (1,2,3) ORDER BY id ASC, age DESC";
        Assertions.assertEquals(expectSql, sql);
    }

    private String buildConditionSql(String jsonFile) {

        try {
            String json = IOUtils.toString(SearchConditionSqlTest.class.getClassLoader().getResourceAsStream(jsonFile), "UTF-8");
            return SearchConditionSqlUtil.buildConditionSql(JSON.parseObject(json, SearchCondition.class), true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


}
