package com.taoyuanx.common.search.dal.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.taoyuanx.common.search.dal.model.User;
import com.taoyuanx.common.search.dal.model.query.UserQueryVO;
import com.taoyuanx.common.search.dal.service.UserService;
import com.taoyuanx.common.search.dal.mapper.UserMapper;
import com.taoyuanx.common.search.util.SearchConditionSqlUtil;
import com.taoyuanx.common.search.dto.page.PageRequest;
import com.taoyuanx.common.search.dto.page.PageResult;
import com.taoyuanx.common.search.dto.query.SearchCondition;
import com.taoyuanx.common.search.util.PageUtil;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author dushitaoyuan
 * @description 针对表【user(user table)】的数据库操作Service实现
 * @createDate 2025-01-11 11:24:01
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Override
    public PageResult<User> pageUser(PageRequest<UserQueryVO> pageQuery) {
        Page<User> page = page(Page.of(pageQuery.getPageNum(), pageQuery.getPageSize()), searchConditionBuild(pageQuery.getQuery()));
        return PageUtil.toPageResult(page);
    }

    @Override
    public List<User> searchUser(UserQueryVO query) {
        return list(searchConditionBuild(query));
    }

    @Override
    public List<User> searchUserWithSql(SearchCondition searchRequest) {
        String conditionSql = SearchConditionSqlUtil.buildConditionSql(searchRequest,true);
        return list(new LambdaQueryWrapper<User>().last(conditionSql));
    }

    private LambdaQueryWrapper<User> searchConditionBuild(UserQueryVO query) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(query.getName())) {
            queryWrapper.like(User::getName, query.getName());
        }
        if (query.getAge() != null) {
            queryWrapper.eq(User::getAge, query.getAge());
        }
        if (query.getId() != null) {
            queryWrapper.eq(User::getId, query.getId());
        }
        if (CollectionUtils.isNotEmpty(query.getSorts())) {
            queryWrapper.last(SearchConditionSqlUtil.buildSortCondition(query.getSorts()));
        } else {
            queryWrapper.orderByDesc(User::getId);
        }

        return queryWrapper;
    }
}




