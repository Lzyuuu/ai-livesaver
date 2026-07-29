静态评审所需材料已齐。以下是完整评审报告。

---

# 静态评审报告：聊天/社交 UI 与动效

评审依据：`SocialWidgets.kt`、`ChatsScreen.kt`(MessageBubble)、`SocialScreens.kt`(PostMedia）源码；Fancy 参考截图（`Messenger.jpg`、`Rebbit.jpg`、`Ustagram.jpg`、`Y.jpg`、`Main_Menu.jpg` 及其中文版界面）与新 UI 截图（`progress-2026-07-28-2249/` 系列、`review-*.png`)。

## 总体结论

新 UI 的大方向已成功落地：纯黑底 + 荧光绿 accent、未读徽章、聊天气泡分组、Reddit 式投票、双击点赞均符合预期，与 Fancy 的语言基本一致。**最需要修的是动效的两个"首次组合误触发"bug**——这是 Emil Kowalski 反复强调的"动效必须只在状态变化时播放"原则的直接违反，用户每次打开 feed/滚动时都会看到无意义的弹跳。

---

## P0 必改

### 1. 点赞按钮在首次渲染时误播 pop 动效
- **位置**:`SocialWidgets.kt:138-150`(`AnimatedLikeButton`)
- **现状**:`LaunchedEffect(liked)` 在组合时立即执行。已被点赞的帖子（如种子中 Jett 那条）在 feed 首次打开、每次重组滚动回收后重新组合时，心形都会播一遍 1→1.4→1 弹跳。截图 `08-moments-list.png` 里红心帖子会在打开 Moments 瞬间"自己跳一下"。
- **建议**：跳过首次发射。用 `var isFirstRun by remember { mutableStateOf(true) }`,在 `LaunchedEffect` 首行 `if (isFirstRun) { isFirstRun = false; return@LaunchedEffect }`；或改用 `snapshotFlow { liked }.drop(1).collect { ... }`。

### 2. 投票分数在滚动回收时反复脉冲
- **位置**:`SocialWidgets.kt:194-199`(`VotePill`)
- **现状**:`LaunchedEffect(score, userVote)` 同样首次组合即触发。Commons 列表上下滚动时，每个重新进入视口的帖子的分数都会 1→1.18→1 脉冲一次，滚动越多越嘈杂——直接违反"克制"原则。
- **建议**：同上加首次跳过守卫；这样动效只在用户真实投票时播放。

---

## P1 应改

### 3. 点赞/投票缺少按压反馈
- **位置**:`SocialWidgets.kt:157-160`（点赞 `indication = null`)、`SocialWidgets.kt:226-231 / 252-257`（投票箭头仅有 `clip` + `clickable` 默认 indication，但 Box 无背景色，ripple 几乎不可见）
- **现状**：点赞按钮完全没有任何按下反馈（ripple 被显式关闭）;Apple HIG 与 Emil 都要求"每个交互必须有即时反馈"。
- **建议**：点赞改为 `indication = rememberRipple(bounded = false, radius = 22.dp)`，或按压时 icon scale 0.85(`collectIsPressedAsState`)；投票箭头给 `background(color, CircleShape)` 的 hover/pressed 态。

### 4. 动效两段式 tween→spring 速度不连续
- **位置**:`SocialWidgets.kt:140-147`（点赞）、`SocialWidgets.kt:196-197`（投票）、`SocialScreens.kt:1508-1516`（心形爆发）
- **现状**：先 `tween(110, FastOutSlowInEasing)` 推上去，再 `spring(MediumBouncy, MediumLow)` 弹回。tween 结束时速度最大，spring 从静止启动，两段之间存在速度跳变，细腻度不足；且 `StiffnessMediumLow`(200）对 19dp 小图标偏肉，回弹拖沓约 400ms+。
- **建议**：改单 spring 保速度连续——点赞：`scale.snapTo(1f)` 后 `animateTo(1.25f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessHigh))` 再 `animateTo(1f, spring(DampingRatioMediumBouncy, StiffnessMedium))`;1.4f 峰值过大（Twitter 实测约 1.2-1.3)，建议降到 1.25f。心形爆发同理，`StiffnessMediumLow` → `StiffnessMedium`。

### 5. 排序 Chip 与 Fancy 药丸风格不一致
- **位置**:`SocialScreens.kt` 中 `SocialScreen` 的 forum 排序行（最新/热门/活跃 FilterChip)
- **现状**:M3 `FilterChip` 默认 8dp 圆角 + 描边 + 选中对勾；Fancy 的 Rebbit 是全圆药丸（50%圆角）、选中项荧光绿填充/描边、无对勾。截图对比 `Rebbit.jpg` 的 Best/Hot/New/Top 差异明显。
- **建议**:`FilterChip(shape = RoundedCornerShape(50), leadingIcon = null, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary))`，选中改绿底黑字（Fancy 标志性处理）。

### 6. 触摸目标小于 48dp 无障碍下限
- **位置**:`SocialWidgets.kt:170`（点赞 icon 19dp + 上下 4dp padding ≈ 27dp 高）、`SocialWidgets.kt:230/255`（投票箭头 20dp + 6dp padding = 32dp)、`ChatsScreen.kt:1202-1211`（重新生成图标 26dp)
- **现状**：均低于 Android 48dp 最小触摸目标。
- **建议**：点赞行 vertical padding 4→10dp；投票箭头 padding 6→10dp(icon 可缩到 18dp 保持视觉重量）；重生成图标 `size(26)` → `size(36).padding(6)`。

### 7. 新消息无入场动效
- **位置**:`ChatsScreen.kt` `ConversationScreen` 的 `itemsIndexed(messages, ...)`（约 966 行附近）
- **现状**：发送/接收消息直接闪现出现；Telegram 与 iMessage 都有轻微的上浮+淡入。
- **建议**：给气泡项加 `Modifier.animateItem(fadeInSpec = tween(150), placementSpec = spring(stiffness = Spring.StiffnessMediumLow))`(LazyColumn 自带 API)，发送消息时列表位移也会有弹簧感而非瞬移。

### 8. 帖子图片固定 320dp 高，裁切僵硬
- **位置**:`SocialScreens.kt:1527-1529`
- **现状**:`Modifier.fillMaxWidth().height(320.dp)` + `ContentScale.Crop`，竖构图图片被拦腰裁；Fancy 的 Ustagram 是按宽高比自适应（4:5~16:9)。
- **建议**：读取 bitmap 宽高比，`aspectRatio(ratio.coerceIn(0.8f, 1.91f))` 替代固定高度（Twitter 区间）。

---

## P2 可选

### 9. 心形爆发节奏微调
- **位置**:`SocialScreens.kt:1515-1516`
- **现状**：停住 400ms + 淡出 180ms;Instagram 实测停住约 600-800ms、淡出约 250ms，当前略显仓促。
- **建议**:`delay(550)`、`tween(220)`；双击连点时 `burst` 值不变导致动效不重播，可在 `onDoubleTap` 里先 `burst = false` 再 `burst = true`（或改用 `MutableSharedFlow` 触发）。

### 10. 输入三点脉冲偏快
- **位置**:`SocialWidgets.kt:295-301`
- **现状**：单周期 900ms,Telegram 约 1200ms，且只有位移动画。
- **建议**:`durationMillis = 1200`，错峰 `index * 150`；叠加 alpha 0.4↔1 同步呼吸，更接近 Telegram 质感。

### 11. 会话列表分隔线与 Fancy 不一致
- **位置**:`ChatsScreen.kt` `ChatListScreen` 的 `HorizontalDivider`(itemsIndexed 内）
- **现状**:Fancy 的 Messenger 列表**完全没有分隔线**，靠行距呼吸；我们加了 0.45 alpha 细线。
- **建议**：删除分隔线，行 vertical padding 12→14dp 补偿密度。若保留也建议 alpha 降到 0.25。

### 12. 特别关注星标 vs Fancy 的"Online"绿字
- **位置**:`ChatsScreen.kt` ChatListScreen 星标 Icon
- **现状**:Fancy Messenger 在名字下方用荧光绿小字 "Online" 表达状态，我们用 14dp 星标。
- **建议**：预览行前缀荧光绿小圆点或"特别关注"绿字，更贴 Fancy；星标可保留但在设计上弱于文字表达。

### 13. 评论数为 0 时完全隐藏计数
- **位置**:`SocialScreens.kt` PostCard 评论图标块
- **现状**:Y.jpg(Twitter）即使 0 也显示计数；当前 0 时不显示。
- **建议**：可常显 "0"，保持动作列对齐稳定；或维持现状（Reddit/IG 也隐藏 0)，属风格选择。

### 14. 投票 UI 形态：底部药丸 vs Fancy 左侧纵列
- **位置**:`SocialScreens.kt` PostCard 动作行 `VotePill`
- **现状**:Fancy Rebbit 是卡片左侧竖排 ↑分↓（桌面 Reddit 风），我们用底部横排药丸（Reddit 官方移动版风）。
- **建议**：保留现状——与 Reddit 官方移动 App 一致，且符合用户"参考业界实际产品"的要求；若要严格贴 Fancy，需把 VotePill 竖排移到卡片左缘，工作量中等。

### 15. 聊天气泡固定 300dp 上限
- **位置**:`ChatsScreen.kt:1096`
- **现状**：手机端 300/360≈83% 合理，折叠屏/平板偏窄。
- **建议**：改 `fillMaxWidth(0.82f)` 包一层，或 `widthIn(max = (LocalConfiguration.screenWidthDp * 0.82).dp)`。

### 16. 流式光标可再加呼吸
- **位置**:`ChatsScreen.kt:1110`(`"$visibleBody▍"`)
- **现状**：静态竖线光标。
- **建议**：对 "▍" 做 `infiniteRepeatable` alpha 0.2↔1(800ms）闪烁；reduced-motion 时保持静态（现状）。

---

## 做得好的地方（保持）

- 未读徽章荧光绿圆点 + 未读时时间变绿，与 Fancy Messenger 完全同构（`review2-chats` 验证）
- 用户气泡用 primary 荧光绿、对方气泡深灰 + 组尾 5dp 小角，Telegram 语义正确
- `TypingIndicator` 与 `AnimatedLikeButton` 都尊重 `systemAnimationsEnabled` 并给了静态降级——reduced-motion 处理规范
- 长按气泡弹出操作菜单（重新生成/版本/重写），隐藏低频操作、保持气泡干净，符合克制原则
- 红心 `#FF4D67`、投票 up=荧光绿/down=蓝紫，与 Ustagram/Reddit 色板对齐
- 排序"热门"按 voteScore 排序、DB v22 投票/已读表与种子数据事务化，数据层支撑完整

**下一步建议**：先修 P0 两项（各约 5 行改动），再做 P1 的 3/4/5/7 四项动效与 Chip 形状，即可使动效达到 Emil 标准且视觉上与 Fancy 完全同族。
