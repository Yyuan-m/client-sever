# LUXURY CAR Customer Server

> 豪华汽车租赁平台 —— 用户端后端服务

基于 Spring Boot 3 + MyBatis Plus 构建的豪华汽车租赁用户端后端服务，为 [`customer-client`](../customer-client) 提供 RESTful API，覆盖鉴权、车辆、订单、购物车、优惠券、个人中心、文件上传等业务。

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
- **车辆（car）**：车辆列表（筛选 / 排序 / 分页）、车辆详情、配置项
- **首页轮播（carousel）**：首页 Banner 配置
- **购物车（cart）**：加车、修改、删除、批量操作
- **订单（order）**：创建订单、订单列表、订单详情、取消订单、支付
- **价格（price）**：统一价格计算（日租金、租期折扣、长租优惠、应付金额）
- **优惠券（coupon）**：领券中心、我的优惠券、下单抵扣
- **用户（user）**：个人资料、头像上传、实名认证、驾驶证上传、修改密码
- **文件上传（upload）**：通用图片上传（身份证 / 驾驶证等）
- **系统配置（system）**：租车规则、商务联系方式等动态配置
- **意见反馈（feedback）**：用户反馈收集

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
│       │   │   ├── MybatisPlusConfig.java       # 分页插件
│       │   │   ├── RedisConfig.java             # Redis 序列化
│       │   │   ├── SecurityConfig.java          # Spring Security 配置
│       │   │   └── WebMvcConfig.java            # 静态资源映射
│       │   ├── entity/                          # 实体类（对应数据库表）
│       │   ├── mapper/                          # MyBatis Plus Mapper
│       │   └── module/                          # 业务模块（按业务拆分）
│       │       ├── auth/                        # 认证
│       │       ├── car/                         # 车辆
│       │       ├── carousel/                    # 轮播
│       │       ├── cart/                        # 购物车
│       │       ├── coupon/                      # 优惠券
│       │       ├── feedback/                    # 意见反馈
│       │       ├── order/                       # 订单
│       │       ├── price/                       # 价格计算
│       │       ├── system/                      # 系统配置
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
- MySQL 8.0+
- Redis 5.0+

## 快速开始

### 1. 准备数据库

```sql
-- 创建业务库
CREATE DATABASE car_rental_customer DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 车辆 / 订单等数据来源于主业务库（后台管理系统维护）
-- 默认通过 car_rental.customer_order 等表跨库访问
```

> 表结构由后台管理系统统一维护，本服务不负责建表。

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
```

### 3. 启动服务

```bash
# 在项目根目录执行
mvn spring-boot:run
```

或在 IDEA 中直接运行 `CustomerServerApplication`。

服务启动后默认监听端口 **8089**，接口前缀 `/api`。

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
| `upload.base-url` | `http://192.168.5.185:8089` | 头像等资源外部访问基地址 |

### 鉴权机制

- **双 Token 方案**：access token（2h） + refresh token（7d）
- access token 过期 → 客户端用 refresh token 调 `/api/auth/refresh-token` 获取新 token（refresh token 轮换：旧 refresh 失效，下发新 refresh）
- refresh token 存 Redis，登出时删除，无法再用于刷新
- access token 黑名单存 Redis（TTL 与 token 有效期一致），登出后立即失效
- JwtTokenFilter 校验 access token 类型，拒绝 refresh token 被当作 access 使用

### 文件上传

- 上传目录：`${user.home}/car_rental_customer_uploads/yyyyMM/uuid.ext`
- 通过 `/uploads/**` 对外提供静态资源访问
- 头像上传走专用接口 `/api/user/avatar`，会自动拼接 `upload.base-url` 为完整 URL 后入库，确保后台管理系统可跨域访问

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
| 购物车 | GET | `/api/cart/list` | 购物车列表 |
| 购物车 | POST | `/api/cart/add` | 加入购物车 |
| 订单 | POST | `/api/order/create` | 创建订单 |
| 订单 | GET | `/api/order/list` | 订单列表 |
| 订单 | GET | `/api/order/detail/{id}` | 订单详情 |
| 订单 | PUT | `/api/order/cancel/{id}` | 取消订单 |
| 订单 | PUT | `/api/order/pay/{id}` | 订单支付 |
| 价格 | POST | `/api/price/calc` | 价格计算 |
| 优惠券 | GET | `/api/coupon/available` | 可领优惠券 |
| 优惠券 | GET | `/api/coupon/mine` | 我的优惠券 |
| 用户 | PUT | `/api/user/profile` | 更新资料 |
| 用户 | POST | `/api/user/avatar` | 上传头像 |
| 用户 | PUT | `/api/user/password` | 修改密码 |
| 上传 | POST | `/api/upload` | 通用图片上传 |
| 系统 | GET | `/api/system/config` | 系统配置 |
| 反馈 | POST | `/api/feedback/submit` | 提交反馈 |

> 登录 / 注册 / 短信验证码 / 忘记密码 / 刷新 token / 通用上传 / 反馈等接口为公开接口，其余需携带 `Authorization: Bearer <accessToken>`。

## 与前端的关系

本服务为后端 API 提供方，配套前端项目位于 [`customer-client`](../customer-client)。前端开发服务器（默认 3000 端口）通过 Vite proxy 将 `/api` 与 `/uploads` 请求代理到本服务的 8089 端口。

## License

MIT
