# 通用条件搜索组件
定义通用的前后端搜索协议,快速实现条件搜索逻辑(分页和列表条件搜索)  
支持:  
- 通用搜索DTO -> 搜索VO  
- 通用搜索DTO -> sql
- 完善的安全控制: 校验列名称和sql参数是否合法

## 使用方式
详细参见: UserSearchController
使用注意点: 当使用SearchConditionBeanUtil 工具时,扩展条件参数不生效
只有使用sql工具(SearchConditionSqlUtil)构建时,扩展参数才生效  

### 通用搜索DTO -> 搜索VO

- Controller
```java
package com.taoyuanx.common.search.api;

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

}


```
- 搜索实现
```java
package com.taoyuanx.common.search.dal.service.impl;

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
    // 自行实现搜索条件的视线逻辑即可
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

```
- 搜索条件转换  
```java
// 分页转换
SearchConditionBeanUtil.convertToPageQuery(UserQueryVO.class, pageRequest);
// 列表转换
SearchConditionBeanUtil.convertSearchBean(UserQueryVO.class, searchRequest);
```
- 搜索条件  
列表搜索:    
```json
{
    "conditions": [
        {
            "name": "name",
            "value": "zhangsan"
        },
        {
            "name": "id",
            "value": "1"
        },
        {
            "name": "age",
            "value": "19"
        }
    ],
    "sorts": [
        {
            "name": "age",
            "type": "ASC"
        }
    ]
}
```
分页搜索参数:  
```json
{
    "pageNum": 1,
    "pageSize": 10,
    "query": {
        "conditions": [
            {
                "name": "name",
                "value": "zhangsan"
            },
            {
                "name": "id",
                "value": "1"
            },
            {
                "name": "age",
                "value": "19"
            }
        ],
        "sorts": [
            {
                "name": "age",
                "type": "ASC"
            }
        ]
    }
}
```


### 通用搜索DTO -> sql

- Controller
```java
@RestController
@RequestMapping("user")
@RequiredArgsConstructor
public class UserSearchController {
    private final UserService userService;
    
    @PostMapping("searchWithSql")
    public List<User> searchWithSql(@RequestBody SearchCondition searchRequest) {
        return userService.searchUserWithSql(searchRequest);
    }

}
```
- 搜索实现
```java
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    
    @Override
    public List<User> searchUserWithSql(SearchCondition searchRequest) {
        String conditionSql = SearchConditionSqlUtil.buildConditionSql(searchRequest);
        return list(new LambdaQueryWrapper<User>().last(conditionSql));
    }
}
```
- 搜索条件转换
```java
// 搜索条件转sql
 SearchConditionSqlUtil.buildConditionSql(searchRequest);
```
- 搜索条件
```json
{
    "conditions": [
        {
            "name": "name",
            "value": "zhangsan"
        },
        {
            "name": "id",
            "value": "1"
        },
        {
            "name": "age",
            "value": "19"
        }
    ],
    "sorts": [
        {
            "name": "age",
            "type": "ASC"
        }
    ]
}
```

## 搜索条件协议
详情参见:com.taoyuanx.common.search.dto  

### 条件搜索

详情参见: com.taoyuanx.common.search.dto.query.SearchCondition  
示例:
```json
{
  "conditions": [
    {
      "name": "subjectId",
      "value": "1",
      "extInfo": {
        "valueType": "number",
        "operate": "EQUAL"
      }
    },
    {
      "name": "name",
      "value": "name",
      "extInfo": {
        "operate": "LIKE"
      }
    },
    {
      "name": "name",
      "value": "name",
      "extInfo": {
        "operate": "LEFT_LIKE"
      }
    },
    {
      "name": "name",
      "value": "name",
      "extInfo": {
        "operate": "RIGHT_LIKE"
      }
    },
    {
      "name": "status",
      "value": "1,2,3",
      "extInfo": {
        "valueType": "number",
        "operate": "IN"
      }
    },
    {
      "name": "status",
      "value": "1,2,3",
      "extInfo": {
        "valueType": "number",
        "operate": "NOT_IN"
      }
    },
    {
      "name": "name",
      "value": "张三,李四",
      "extInfo": {
        "operate": "NOT_IN"
      }
    },
    {
      "name": "createTime",
      "value": "2024-11-08,2024-11-09",
      "extInfo": {
        "operate": "BETWEEN_AND"
      }
    },
    {
      "extInfo": {
        "type": "complex",
        "composeType": "OR",
        "subConditions": [
          {
            "name": "subjectId",
            "value": "1",
            "extInfo": {
              "valueType": "number"
            }
          },
          {
            "name": "subjectId",
            "value": "2",
            "extInfo": {
              "valueType": "number"
            }
          },
          {
            "extInfo": {
              "type": "complex",
              "composeType": "OR",
              "subConditions": [
                {
                  "name": "subjectId",
                  "value": "3",
                  "extInfo": {
                    "valueType": "number"
                  }
                },
                {
                  "name": "subjectId",
                  "value": "4",
                  "extInfo": {
                    "valueType": "number"
                  }
                }
              ]
            }
          }
        ]
      }
    }
  ],
  "composeType": "AND",
  "sorts": [
    {
      "name": "createTime",
      "type": "ASC",
      "order": 1
    }
  ]
}
```


### 分页搜索
详情参见: com.taoyuanx.common.search.dto.query.PageRequest<SearchCondition> pageRequest  
示例:

```json
{
    "pageNum": 1,
    "pageSize": 10,
    "query": {
        "conditions": [
            {
                "name": "name",
                "value": "zhangsan"
            },
            {
                "name": "id",
                "value": "1"
            },
            {
                "name": "age",
                "value": "19"
            }
        ],
        "sorts": [
            {
                "name": "age",
                "type": "ASC"
            }
        ]
    }
}
```





