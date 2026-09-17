# 原项目能力、本次贡献与面试准备

## 能力归属

| 原项目已有代码 | 本次 CampusExchange 修改 |
| --- | --- |
| Java 17 / Spring Boot 单体、JPA、MySQL、React、TypeScript、Vite、MUI、Redux 目录结构 | 保持技术栈与单体结构；审核依赖、运行配置、构建入口与真实启动 |
| User / Seller / Product / Order / OrderItem 等电商实体及 repository | Product 增加校园二手字段；统一 User 买卖身份；复用订单和单件 OrderItem，新增 Reservation |
| 商品列表、卡片、详情、Navbar、账户/卖家页面和主题 | 在原页面位置改造校园英文流程；跨公寓筛选；发布、预约、自提与订金操作；隐藏商业入口 |
| JWT 工具、密码处理和原安全配置 | 环境变量 JWT secret、规范化邮箱、最小 DTO、默认拒绝旧接口、参与者权限与真实 JWT 测试 |
| 创建 Stripe Checkout 链接的代码及旧 Razorpay 分支 | 仅保留 Stripe 运行依赖；验证签名后向 Stripe 查证；金额/货币/模式匹配；通知幂等、退款持久状态及模拟模式 |
| 原电商下单、优惠券、报表、AI 等代码 | 不把这些原有代码算作新增贡献；不宣称本次测试验证了其可靠性；商业支付与 AI 移入 legacy |
| 原作者 `com.zosh` 包名、署名和 Maven wrapper 许可证头 | 保留来源说明与原有许可声明；没有凭空添加开源许可证 |

“原有代码存在”不等于“功能已可靠或可用于生产”。本次只对 CampusExchange 启用的路径声明测试结果。前端保留 React/Vite 和 MUI 体系，但主动简化原购物车/商家流程；没有声称原 UI 原封不动即可工作。

## 可以在面试中解释的具体工作

- 为什么公寓是商品筛选字段而不是用户权限边界：核心问题是跨群信息分散，而非封闭的楼栋社区。
- 为什么保留单体并复用 Order/OrderItem：项目规模与个人学习目标不需要微服务，订单的持久化关系已有价值；预约生命周期与订金状态则需要独立表达。
- 为什么只锁 Product：稀缺资源是一件商品。所有相关写操作按 Product → Reservation 顺序加锁，事务内检查状态并写入预约，解决重复占用。
- 实际调试案例：H2 全过不等于 MySQL 全过；MySQL 默认 REPEATABLE READ 的快照与锁定读取混用，让第二个买家看到 activeReservationId 却看不到其对应行/订单。先修锁定读取，再显式使用 READ_COMMITTED，保留回归测试。
- 为什么不能信任支付成功跳转：浏览器参数可伪造；服务端验证签名并查询 Checkout Session，再校验关联 ID、金额、货币和 test mode。
- 为什么需要两套状态：取消预约可以立即释放商品，但退款是否成功由支付服务决定，不能把 CANCELLED 等同于 REFUNDED。
- 如何处理迟到支付：旧预约仍然过期/取消，订金进入退款流程；绝不抢走新买家的商品。
- 如何处理网络未知结果：退款请求超时不等于退款失败。保留 pending，复用幂等键，并通过退款 metadata 找回外部已成功但本地未记录的结果。
- 权限测试为何需要 HTTP 层：除了 service 所有权检查，还必须确认匿名访问、伪造 Token、角色字段注入和旧接口均被正确限制。

## 简历表达示例（只使用可验证内容）

> Adapted a Spring Boot/MySQL and React marketplace into a campus secondhand trading app with cross-apartment discovery, single-item pickup reservations, and optional test-mode deposits. Implemented transactional inventory locking, participant-scoped pickup details, idempotent payment handling, and asynchronous refund states; validated concurrency and failure paths with database integration and browser tests.

应明确说明这是基于原有项目的个人改造，能解释自己理解并验证过的代码。不要称原始电商架构全部原创，不编造用户量、吞吐量、收益、性能提升、生产运营记录或一周开发历史。Git 提交按实际创建时间记录，没有 Co-authored-by 署名；原作者来源说明仍保留。

## 后续可自行练习的维护任务

这些是未来练习，不是本次已经完成的功能：数据库版本化迁移、支付成功的主动对账、重启恢复故障注入、更完善的发布编辑/下架、认证限流、图片上传、邮件验证、应用依赖升级和按路由拆分前端包。先选择一个真实需求或失败案例，再写修复和测试，避免为了提交数量制造改动。
