# 演示步骤

先按 README 启动 MySQL、后端和前端。默认 `PAYMENT_MODE=mock`、`DEMO_DATA=true`；所有账户、商品、公寓与地址均为虚构数据。顶部必须显示 **SIMULATION MODE**。

1. 不登录浏览首页，查看 North/South Campus、Maple Court/River House 的商品。尝试关键词 `desk`，再组合分类、价格、成色与公寓筛选；任何用户都能跨公寓浏览。
2. 以 Maya 登录，选择 **List an item**。填写商品、公寓、公共地点、私有地址、未来时段和价格。勾选订金时，金额须大于零且不超过总价。发布后，公开详情不应显示私有地址。
3. 用另一浏览器会话以 Alex 登录，选择该商品和卖家提供的时间。预约前看到总价、订金、尾款及取消规则。
4. 若无订金，预约直接进入 **reserved**。有订金则进入 **pending payment**，My pickups 显示截止时间；此时不能被另一买家成功预留。
5. 点击 **Simulate failure**：显示失败，截止前仍可重试。再点击 **Simulate payment success**：预约变为 reserved，自提详细地址出现。此按钮只模拟，不收钱。
6. Alex 或 Maya 在成交前取消：商品重新 available，已付订金先显示 **refund pending**，后台任务确认后才显示模拟 **refunded**。
7. 重新预约另一商品，完成模拟支付。Maya 在 My pickups 点击 **Confirm handoff completed**；确认已验货、交付及线下结清尾款后，商品 sold。买家没有这个按钮，服务端也拒绝买家确认成交。
8. 演示超时：将 `HOLD_MINUTES=1` 后重启后端，新建一笔需订金预约，不付款。约一分钟后加最多一个扫描周期，旧预约 expired，商品可再次预约。已有预约保留创建时的截止时间。
9. 迟到支付、并发抢占、退款失败/未知结果、重复签名通知通过自动化测试演示；不要把普通的 UI 成功点击当作这些异常场景的证明。

可用 `npm run test:e2e` 自动完成浏览、筛选、登录、发布、预约、模拟付款、取消退款及移动端布局检查。它需要已经启动的真实 API 和 demo 账户；参见 TESTING.md。

演示数据只在第一次创建 Maya 账户时初始化，时段是首次启动后的第 2/3 天。几天后请发布新商品和未来时段；不要在含有要保留数据的数据库上删除重建来刷新演示。

Stripe 测试模式的人工验证：按 README 配置自己的 test key 和 webhook listener；从预约按钮打开 Checkout，用测试卡支付；确认跳转本身不改变状态，服务端收到签名通知并查证后才变为 reserved。取消后检查 Stripe 测试控制台与应用退款状态一致。未配置真实测试密钥前，本项目不宣称这一步已完成。
