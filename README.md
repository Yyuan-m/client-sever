# LUXURY CAR Customer Server

> 豪华汽车租赁平台 —— 用户端后端服务

基于 Spring Boot 3 + MyBatis Plus 构建的豪华汽车租赁用户端后端服务，为两套前端（Web [`customer-client`](../customer-client) 与多端 App [`customer-client-app`](../customer-client-app)）提供 RESTful API，覆盖鉴权、车辆、订单、购物车、优惠券、预约咨询、售后投诉、个人中心、文件上传等业务。

## 技术栈

| 分类 | 技术 |
| --- | --- |
| 框架 | Spring Boot 3.2.6 |
| 语言 | Java 17 |
| 安全 | Spring Security + JWT（jjwt 0.12） |
| ORM | MyBatis Plus 3.5 |
| 数据库 | MySQL 8 |
| 缓存 | Redis（Token 黑名单 / Refresh Token 存储 / 短信验证码） |
| 工具库 | Hutool 5.8、Lombok、Commons IO |
| 参数校验 | spring-boot-starter-validation |

## 功能模块

- **认证（auth）**：注册、登录、登出、忘记密码、短信验证码、双 Token 无感刷新、用户信息
- **车辆（car）**：车辆列表（筛选 / 排序 / 分页）、车辆详情、配置项、实图分组、可用期（已租出/整备期）
- **首页轮播（carousel）**：首页 Banner 配置
- **公告（announcement）**：公告列表 / 详情 / 置顶
- **购物车（cart）**：加车、修改租期、删除、清空、数量
- **订单（order）**：创建订单（支持多车合并）、订单列表、订单详情、取消、支付、进行中订单、可评价订单
- **价格（price）**：统一价格计算（日租金、租期折扣、长租优惠、节假日溢价、应付金额）
- **优惠券（coupon）**：领券中心、我的优惠券、下单抵扣、最优券自动预选
- **用户（user）**：个人资料、头像上传、实名认证、驾驶证上传、修改密码、收藏
- **预约咨询 / 反馈（feedback）**：预约咨询提交、我的预约（状态跟踪）、取消预约、联系客服、留言反馈
- **售后投诉（complaint）**：提交投诉、我的投诉、投诉详情、满意度评分
- **文件上传（upload）**：通用图片上传（身份证 / 驾驶证 / 评价图等）
- **系统配置（system）**：租车规则、商务联系方式、门店 / 城市 / 服务优势 / 客户评价 / 数据字典

## 项目结构

```
customer-server/
├── src/
│   └── main/
│       ├── java/com/car/customer/
│       │   ├── CustomerServerApplication.java   # 启动类
│       │   ├── common/                          # 公共模块
│       │   │   ├── exception/                   # 全局异常处理
│       │   │   ├── result/                      # 统一响应（Result / PageResult）
│       │   │   ├── security/                    # JWT 过滤器
│       │   │   └── util/                        # JwtUtil / SecurityUtil
│       │   ├── config/                          # 配置类
│       │   │   ├── GlobalCorsConfig.java        # 跨域
│       │   │   ├── MybatisPlusConfig.java       # 分页插件 / 字段自动填充
│       │   │   ├── RedisConfig.java             # Redis 序列化
│       │   │   ├── SecurityConfig.java          # Spring Security 配置
│       │   │   └── WebMvcConfig.java            # 静态资源映射
│       │   ├── entity/                          # 实体类（23 个，对应数据库表）
│       │   ├── mapper/                          # MyBatis Plus Mapper
│       │   └── module/                          # 业务模块（按业务拆分）
│       │       ├── auth/                        # 认证
│       │       ├── announcement/                # 公告
│       │       ├── car/                         # 车辆
│       │       ├── carousel/                    # 轮播
│       │       ├── cart/                        # 购物车
│       │       ├── complaint/                   # 售后投诉
│       │       ├── coupon/                      # 优惠券
│       │       ├── feedback/                    # 预约咨询 / 留言反馈
│       │       ├── order/                       # 订单
│       │       ├── price/                       # 价格计算
│       │       ├── review/                      # 车辆评价
│       │       ├── system/                      # 系统配置 / 字典
│       │       ├── upload/                      # 文件上传
│       │       └── user/                        # 用户中心
│       └── resources/
│           └── application.yml                  # 配置文件
├── .gitignore
├── pom.xml
└── README.md
```

每个业务模块遵循统一的分层：

```
module/<name>/
├── controller/    # 接口层
├── service/       # 业务层
├── dto/           # 入参对象
└── vo/            # 出参对象
```

## 环境要求

- JDK 17+
- Maven 3.6+
- MySQL 8.0+（涉及 `car_rental_customer` 与 `car_rental` 两个库）
- Redis 5.0+

## 快速开始

### 1. 准备数据库

```sql
-- 创建业务库（表结构由后台管理系统统一维护，本服务不负责建表）
CREATE DATABASE car_rental_customer DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

- **car_rental_customer**：会员（member）、购物车（cart）、反馈/预约（feedback）、投诉相关、短信验证码等 C 端自持数据
- **car_rental**：车辆（car_info）、图片（car_image）、订单（customer_order）、优惠券、门店、字典等由后台管理系统维护，本服务通过跨库访问（如 `@TableName("car_rental.car_info")`）

### 2. 修改配置

编辑 `src/main/resources/application.yml`，按需调整：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/car_rental_customer?...
    username: root
    password: 123456
  data:
    redis:
      host: localhost
      port: 6379

jwt:
  secret: luxury-car-customer-jwt-secret-key-2024-very-long-secret
  expiration: 7200000            # access token 2 小时
  refresh-expiration: 604800000  # refresh token 7 天

upload:
  path: ${user.home}/car_rental_customer_uploads
  base-url: http://192.168.5.185:8089   # 头像等资源的外部访问基地址

mybatis-plus:
  global-config:
    db-config:
      logic-delete-field: isDelete   # 逻辑删除字段（用于 car_info 等跨库实体）
```

### 3. 启动服务

```bash
mvn spring-boot:run
```

或在 IDEA 中直接运行 `CustomerServerApplication`。服务启动后默认监听端口 **8089**，接口前缀 `/api`。

## 配置说明

### application.yml 关键项

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `server.port` | 8089 | 服务端口 |
| `spring.datasource.url` | `jdbc:mysql://localhost:3306/car_rental_customer` | 业务库连接 |
| `spring.data.redis.*` | localhost:6379 | Redis 连接 |
| `mybatis-plus.global-config.db-config.logic-delete-field` | isDelete | 逻辑删除字段 |
| `jwt.expiration` | 7200000（2h） | access token 有效期 |
| `jwt.refresh-expiration` | 604800000（7d） | refresh token 有效期 |
| `upload.path` | `${user.home}/car_rental_customer_uploads` | 文件存储目录 |

### 鉴权机制

- **双 Token 方案**：access token（2h） + refresh token（7d）
- access token 过期 → 客户端用 refresh token 调 `/api/auth/refresh-token` 获取新 token（refresh token 轮换：旧 refresh 失效，下发新 refresh）
- refresh token 存 Redis，登出时删除，无法再用于刷新
- access token 黑名单存 Redis（TTL 与 token 有效期一致），登出后立即失效
- JwtTokenFilter 校验 access token 类型，拒绝 refresh token 被当作 access 使用

### 文件上传

- 上传目录：`${user.home}/car_rental_customer_uploads/yyyyMM/uuid.ext`
- 通过 `/uploads/**` 对外提供静态资源访问
- 上传接口统一返回**相对路径**（如 `/uploads/yyyyMM/uuid.jpg`），由前端按资源归属（客户端 8089 / 后台 8088）拼接完整 URL

## API 概览

所有接口统一返回 `{ code, msg, data }`，成功码 `200`。

| 模块 | 方法 | 路径 | 说明 |
| --- | --- | --- | --- |
| 认证 | POST | `/api/auth/login` | 登录 |
| 认证 | POST | `/api/auth/register` | 注册 |
| 认证 | POST | `/api/auth/sms-code` | 发送短信验证码 |
| 认证 | POST | `/api/auth/forgot-password` | 忘记密码 |
| 认证 | POST | `/api/auth/refresh-token` | 刷新 access token |
| 认证 | GET | `/api/auth/user-info` | 获取当前用户信息 |
| 认证 | POST | `/api/auth/logout` | 登出 |
| 车辆 | GET | `/api/car/list` | 车辆列表 |
| 车辆 | GET | `/api/car/detail/{id}` | 车辆详情 |
| 车辆 | GET | `/api/car/hot` | 热门车型 |
| 车辆 | GET | `/api/car/{id}/images` | 图片分组 |
| 车辆 | GET | `/api/car/{id}/availability` | 车辆可用期（已租出/整备期） |
| 公告 | GET | `/api/announcement/top`、`/page`、`/{id}` | 公告列表 / 详情 |
| 轮播 | GET | `/api/carousel/active` | 轮播图 |
| 购物车 | GET | `/api/cart/list` | 购物车列表 |
| 购物车 | GET | `/api/cart/count` | 购物车数量 |
| 购物车 | POST | `/api/cart/add` | 加入购物车 |
| 购物车 | PUT | `/api/cart/update/{id}` | 修改租期/数量 |
| 购物车 | DELETE | `/api/cart/{id}` | 移除单项 |
| 购物车 | DELETE | `/api/cart/clear` | 清空购物车 |
| 订单 | POST | `/api/order/create` | 创建订单（多车合并） |
| 订单 | GET | `/api/order/list`、`/detail/{id}` | 订单列表 / 详情 |
| 订单 | PUT | `/api/order/cancel/{id}` | 取消订单 |
| 订单 | PUT | `/api/order/pay/{id}` | 订单支付 |
| 订单 | PUT | `/api/order/complete/{id}` | 确认还车 |
| 订单 | GET | `/api/order/active`、`/reviewable` | 进行中 / 可评价订单 |
| 价格 | POST | `/api/price/car` | 单车价格计算 |
| 价格 | POST | `/api/price/cart` | 购物车批量价格 |
| 优惠券 | GET | `/api/coupon/available`、`/mine`、`/claimed-ids` | 领券相关 |
| 优惠券 | POST | `/api/coupon/receive/{id}`、`/lock`、`/cancel-lock`、`/verify` | 领券 / 锁定 / 核销 |
| 用户 | PUT | `/api/user/profile` | 更新资料 |
| 用户 | POST | `/api/user/avatar` | 上传头像 |
| 用户 | PUT | `/api/user/password` | 原密码验证改密 |
| 用户 | POST | `/api/user/verify` | 提交实名认证 |
| 用户 | GET | `/api/user/favorites` | 我的收藏 |
| 反馈/预约 | POST | `/api/feedback/submit` | 提交预约咨询 / 留言反馈 |
| 反馈/预约 | GET | `/api/feedback/appointments` | 我的预约列表（分页+状态筛选） |
| 反馈/预约 | POST | `/api/feedback/appointments/{id}/cancel` | 取消预约 |
| 反馈/预约 | GET | `/api/feedback/appointments/{id}/contact` | 查看联系人完整信息（本人） |
| 投诉 | POST | `/api/complaint/submit` | 提交投诉 |
| 投诉 | GET | `/api/complaint/mine` | 我的投诉记录 |
| 投诉 | GET | `/api/complaint/{id}` | 投诉详情 |
| 投诉 | POST | `/api/complaint/{id}/rate` | 投诉满意度评分 |
| 评价 | POST | `/api/review/submit` | 提交评价 / 追加评价 |
| 评价 | GET | `/api/review/order/{orderId}` | 订单评价 |
| 上传 | POST | `/api/upload` | 通用图片上传 |
| 系统 | GET | `/api/system/config`、`/cities`、`/dict/{dictType}` 等 | 系统配置 / 字典 |

> 登录 / 注册 / 短信验证码 / 忘记密码 / 刷新 token / 反馈提交 / 公开列表接口为公开接口，其余需携带 `Authorization: Bearer <accessToken>`。

## 关键业务说明

### 预约咨询（feedback）

- 类型：`appointment`（预约咨询，含意向车型/取车日期）、`feedback`（留言反馈）
- 状态机：`pending`（待处理）→ `handled`（已处理，后台标记同时写处理人/备注/时间）；用户自主取消为 `cancelled`
- 登录态提交自动绑定 `member_id`，供「我的预约」查询；姓名与手机号在列表返回时**脱敏**，完整信息走 `/contact` 接口（仅本人）
- 数据表位于 `car_rental_customer.feedback`，由后台管理系统「预约咨询」页面处理跟进

### 售后投诉（complaint）

- 提交的投诉写入后台共用表 `car_rental.after_sales_complaint`
- 状态流转由后台管理系统「售后投诉」页面处理；处理完成后 C 端可对已解决投诉评分（1-5 星满意度）

### 车辆数据来源

- 车辆及其图片、订单、优惠券等数据来自 **car_rental** 库（后台管理系统维护）；C 端 store 对部分业务（订单统计等）做实时计算覆盖冗余值
- 车辆封面由 `car_info.images` 字段（JSON 数组字符串）解析，取首图作为 cover

## 与前端的关系

本服务为后端 API 提供方，配套前端项目：

- [`customer-client`](../customer-client)：Vue3 Web 端（Element Plus），开发端口 3000，通过 Vite proxy 将 `/api` 与 `/uploads` 转发到 8089
- [`customer-client-app`](../customer-client-app)：uni-app 多端（微信小程序 / H5 / App），H5 走 proxy，小程序 / App 端通过环境变量拼接完整域名

## License

MIT