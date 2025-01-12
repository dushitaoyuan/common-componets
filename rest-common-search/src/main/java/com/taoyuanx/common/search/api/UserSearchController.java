package com.taoyuanx.common.search.api;

import com.taoyuanx.common.search.dal.model.User;
import com.taoyuanx.common.search.dal.model.query.UserQueryVO;
import com.taoyuanx.common.search.dal.service.UserService;
import com.taoyuanx.common.search.util.SearchConditionBeanUtil;
import com.taoyuanx.common.search.dto.page.PageRequest;
import com.taoyuanx.common.search.dto.page.PageResult;
import com.taoyuanx.common.search.dto.query.SearchCondition;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


/**
 * 通用搜索例子
 * @author dushitaoyuan
 * @date 2025/1/11 11:07
 */
@RestController
@RequestMapping("user")
@RequiredArgsConstructor
public class UserSearchController {
    private final UserService userService;

    @PostMapping("page")
    public PageResult<User> page(@RequestBody PageRequest<SearchCondition> pageRequest) {

        return userService.pageUser(SearchConditionBeanUtil.convertToPageQuery(UserQueryVO.class, pageRequest));
    }

    @PostMapping("search")
    public List<User> search(@RequestBody SearchCondition searchRequest) {
        return userService.searchUser(SearchConditionBeanUtil.convertSearchBean(UserQueryVO.class, searchRequest));
    }

    @PostMapping("searchWithSql")
    public List<User> searchWithSql(@RequestBody SearchCondition searchRequest) {
        return userService.searchUserWithSql(searchRequest);
    }

}
