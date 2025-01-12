package com.taoyuanx.common.search.dal.service;

import com.taoyuanx.common.search.dal.model.User;
import com.baomidou.mybatisplus.extension.service.IService;
import com.taoyuanx.common.search.dal.model.query.UserQueryVO;
import com.taoyuanx.common.search.dto.page.PageRequest;
import com.taoyuanx.common.search.dto.page.PageResult;
import com.taoyuanx.common.search.dto.query.SearchCondition;

import java.util.List;

/**
* @author dushitaoyuan
* @description 针对表【user(user table)】的数据库操作Service
* @createDate 2025-01-11 11:24:01
*/
public interface UserService extends IService<User> {

    PageResult<User> pageUser(PageRequest<UserQueryVO> pageRequest);

    List<User> searchUser(UserQueryVO searchRequest);

    List<User> searchUserWithSql(SearchCondition searchRequest);
}
