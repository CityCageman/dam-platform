# 角色、产品、数据范围授权矩阵

## 一、角色

```text
super_admin       超级管理员
executive         院领导
department_head   科主任
data_ops          数据运维
developer         数据开发
governor          数据管家
business          业务用户
security          安全管理员
```

## 二、数据范围

| 角色 | 范围 | 说明 |
|---|---|---|
| super_admin | ALL | 全量 |
| executive | ALL | 全院查看 |
| department_head | DEPARTMENT | 本科室 |
| data_ops | ALL | 平台运行 |
| developer | SELF | 本人开发对象 |
| governor | DEPARTMENT | 本科室治理 |
| business | SELF | 本人申请/使用 |
| security | ALL | 安全审计 |

## 三、产品授权

图例：

```text
F = 完整管理
R = 只读
D = 本科室范围
U = 使用
- = 不显示
```

| 产品 | super_admin | executive | department_head | data_ops | developer | governor | business | security |
|---|---|---|---|---|---|---|---|---|
| DAM 数据资产与主数据 | F | R | D | R | R | F | R | R |
| DPM 数据中台管理 | F | - | - | F | - | R | - | R |
| DAP 数据接入 | F | - | - | F | U | R | - | R |
| DGP 数据治理 | F | R | D | R | R | F | - | F |
| DPP 数据处理 | F | - | - | R | F | - | - | - |
| DSP 数据服务 | F | R | D | F | F | R | U | R |
| DAN 数据分析 | F | R | D | - | - | R | R | - |
| DAPP 数据应用 | F | R | U | - | F | - | U | - |
| AI 工作台 | F | R | U | - | U | U | U | R |
| 系统配置 | F | - | - | F | - | F | - | F |

## 四、驾驶舱

| 角色 | 默认驾驶舱 | 额外可见 |
|---|---|---|
| super_admin | `/cockpit/super` | 全部驾驶舱 |
| executive | `/cockpit/executive` | 无 |
| department_head | `/cockpit/department` | 无 |
| data_ops | `/cockpit/ops` | 系统配置 |
| developer | `/cockpit/developer` | 无 |
| governor | `/cockpit/governance` | 系统配置 |
| business | `/cockpit/business` | 无 |
| security | `/cockpit/security` | 系统配置 |

## 五、菜单规则

```text
驾驶舱：按角色显示自己的驾驶舱
产品中心：显示有产品授权的产品
产品模块：显示有模块授权的模块
系统配置：仅授权角色
个人中心：所有登录用户
```

## 六、后端强制规则

```text
角色只决定功能权限
数据范围必须由后端根据 token 强制过滤
前端传 department 只能作为筛选条件，不能作为授权依据
```

## 七、产品授权表

```text
role_product(role_id, product_code, access_level)
role_module(role_id, module_code, access_level)
role_cockpit(role_id, cockpit_code, is_default)
```
