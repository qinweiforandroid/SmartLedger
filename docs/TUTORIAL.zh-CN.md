# SmartLedger 中文教程

> 面向读者：熟悉 Android 原生开发（MVVM / Coroutines / Room），但第一次接触 Jetpack Compose 与 Kotlin Multiplatform 的开发者。
>
> 本教程基于本仓库当前代码（`shared` + `androidApp` + `desktopApp` + `iosApp` 四模块结构），不是通用模板翻译，每一章都能在源码里找到对应文件。

---

## 目录

1. [项目全景](#1-项目全景)
2. [跑起来](#2-跑起来)
3. [源码集与 expect/actual](#3-源码集与-expectactual)
4. [数据层：Room 的 KMP 写法](#4-数据层room-的-kmp-写法)
5. [Repository：为什么要多包一层](#5-repository为什么要多包一层)
6. [ViewModel 与 StateFlow 数据流](#6-viewmodel-与-stateflow-数据流)
7. [Compose 速成：给 XML 开发者](#7-compose-速成给-xml-开发者)
8. [明细页拆解](#8-明细页拆解)
9. [统计页与 Canvas 手绘图表](#9-统计页与-canvas-手绘图表)
10. [日期与时区：最容易翻车的地方](#10-日期与时区最容易翻车的地方)
11. [增删改与撤销删除](#11-增删改与撤销删除)
12. [三端入口：同一份 UI 怎么启动](#12-三端入口同一份-ui-怎么启动)
13. [扩展实战](#13-扩展实战)
14. [常见坑与排查](#14-常见坑与排查)
15. [附录：文件索引与待清理项](#15-附录文件索引与待清理项)

---

## 1. 项目全景

### 1.1 技术栈

| 领域 | 选型 | 版本（见 `gradle/libs.versions.toml`） |
|---|---|---|
| 语言 | Kotlin Multiplatform | 2.4.21 |
| UI | Compose Multiplatform (Material 3) | 1.12.2 / material3 1.12.0-alpha03 |
| 数据库 | Room Multiplatform + BundledSQLiteDriver | 2.8.5 |
| 异步 | kotlinx-coroutines | 1.11.0 |
| 时间 | kotlinx-datetime | 0.7.1 |
| 构建 | AGP 9.1.1 + KSP 2.3.5 | | 

注意 AGP 9 的一个关键变化：KMP 模块不再用 `com.android.library`，而是用 `com.android.kotlin.multiplatform.library`，并且 `android { }` 配置块被挪进了 `kotlin { }` 里面。`shared/build.gradle.kts` 就是这么写的，如果你从 AGP 8 的老项目迁移过来，这是第一处会卡住的地方。

### 1.2 模块结构

```
SmartLedger/
├── shared/          # 核心：模型、Room、Repository、ViewModel、全部 UI
│   └── src/
│       ├── commonMain/     # 三端共用（99% 的代码在这里）
│       ├── androidMain/    # Android 特有：Context、DB 路径
│       ├── iosMain/        # iOS 特有：Documents 目录、UIViewController
│       ├── jvmMain/        # Desktop 特有：user.home 路径
│       ├── commonTest/     # 公共测试
│       ├── androidHostTest/# Android JVM 单测（Robolectric 风格）
│       ├── jvmTest/        # Desktop 单测
│       └── iosTest/        # iOS 单测
├── androidApp/      # 空壳 Activity，只做入口注入
├── desktopApp/      # 空壳 main()，只开窗口
└── iosApp/          # Xcode 工程，只做 UIViewControllerRepresentable 桥接
```

一条重要的架构认知：**三个 app 模块几乎不含业务逻辑**。它们唯一的工作是「创建数据库 → 造 Repository → 调用 `App(repository)`」。所有界面、状态、持久化逻辑都在 `shared`。

这和传统 Android 项目「Activity 是入口也是主角」的思路不同，请把它当成「`shared` 是一个跨平台的 SDK，三端各写一个 30 行的壳」。

### 1.3 数据流总览

```
SQLite 文件
   ↓ Room 生成 Dao 实现
TransactionDao.observeAll(): Flow<List<Transaction>>
   ↓
TransactionRepository（薄封装，留出未来加缓存/同步的口子）
   ↓
LedgerViewModel
   ├─ allTransactions  (StateFlow，全量)
   ├─ 按 selectedMonth 过滤 → monthTransactions
   └─ 再派生 → groupedTransactions / monthlyIncome / monthlyExpense / balance
              / expenseBreakdown / incomeBreakdown
   ↓ collectAsState()
Composable（LedgerScreen / StatsScreen）
   ↓ 用户操作
viewModel.addTransaction() / updateTransaction() / deleteTransaction()
   ↓ 写库
Room 发出新的 Flow 值 → 整条链路自动重算 → UI 重组
```

**没有一处手动刷新逻辑**。改一次库，列表、合计、饼图全部自动更新。这是 Flow + Compose 最舒服的地方，也正是从 LiveData 迁过来时要重新建立直觉的地方。

### 1.4 源文件清单

| 文件 | 职责 |
|---|---|
| [model/Transaction.kt](../shared/src/commonMain/kotlin/com/qinwei/smartledger/model/Transaction.kt) | Room 实体 + `TransactionType` 枚举 |
| [model/Category.kt](../shared/src/commonMain/kotlin/com/qinwei/smartledger/model/Category.kt) | 分类定义 + **全部图标用 Canvas 手绘** |
| [data/AppDatabase.kt](../shared/src/commonMain/kotlin/com/qinwei/smartledger/data/AppDatabase.kt) | `@Database` + `@ConstructedBy` |
| [data/TransactionDao.kt](../shared/src/commonMain/kotlin/com/qinwei/smartledger/data/TransactionDao.kt) | 增删改查 |
| [data/TransactionRepository.kt](../shared/src/commonMain/kotlin/com/qinwei/smartledger/data/TransactionRepository.kt) | 仓储层 + 示例数据 |
| [data/DatabaseFactory.kt](../shared/src/commonMain/kotlin/com/qinwei/smartledger/data/DatabaseFactory.kt) | `expect` 三端建库 + 全局懒加载实例 |
| [viewmodel/LedgerViewModel.kt](../shared/src/commonMain/kotlin/com/qinwei/smartledger/viewmodel/LedgerViewModel.kt) | 全部派生状态与业务动作 |
| [ui/LedgerScreen.kt](../shared/src/commonMain/kotlin/com/qinwei/smartledger/ui/LedgerScreen.kt) | 主界面：月份切换、明细列表、记账对话框 |
| [ui/StatsScreen.kt](../shared/src/commonMain/kotlin/com/qinwei/smartledger/ui/StatsScreen.kt) | 统计页：圆环图 + 分类排行 |
| [util/DateTimeUtils.kt](../shared/src/commonMain/kotlin/com/qinwei/smartledger/util/DateTimeUtils.kt) | 时间戳 ↔ 本地日期、中文日期文案 |

---

## 2. 跑起来

### 2.1 前置条件

- JDK 17+（AGP 9 要求；`gradle.properties` 里给 Gradle 分配了 4G 堆）
- Android Studio（建议最新稳定版，需要支持 AGP 9）
- Xcode 15+（仅 iOS 需要，必须 macOS）
- 首次同步会下载 Kotlin/Native 工具链，比较慢，耐心等

### 2.2 三个平台的启动方式

**Android**

```bash
./gradlew :androidApp:assembleDebug
# 或者直接在 IDE 里点绿三角
```

**Desktop（推荐日常开发用）**

```bash
./gradlew :desktopApp:run          # 普通启动
./gradlew :desktopApp:hotRun --auto # 热重载，改 Compose 代码自动生效
```

热重载是本项目开发效率的关键——因为 UI 全在 `commonMain`，改一次就能在桌面窗口里立刻看到效果，不用装 APK。

**iOS**

用 Xcode 打开 [iosApp/](../iosApp/) 目录直接 Run。

Xcode 的 Build Phase 里挂了一段脚本（见 `project.pbxproj`）：

```bash
cd "$SRCROOT/.."
./gradlew :shared:embedAndSignAppleFrameworkForXcode
```

它会先把 Kotlin 代码编成 `Shared.framework` 再嵌入。所以 iOS 端**不需要**手动跑 Gradle，但**必须在项目根目录有可用的 Gradle wrapper**。如果 Xcode 报 `Shared module not found`，八成是这段脚本失败了，去 Xcode 的 Report Navigator 看 Gradle 输出，通常是 `Config.xcconfig` 里的 `TEAM_ID` 为空导致签名失败。

### 2.3 跑测试

```bash
./gradlew :shared:jvmTest                  # Desktop 端，最快
./gradlew :shared:testAndroidHostTest      # Android JVM 单测
./gradlew :shared:iosSimulatorArm64Test    # iOS 模拟器
```

> 现有的四个测试文件内容都是 `assertEquals(3, 1 + 2)` 的模板残留，等于还没写测试。第 13 章会给几个真正值得测的点。

---

## 3. 源码集与 expect/actual

### 3.1 源码集的含义

Kotlin Multiplatform 用目录名决定「这段代码编到哪些平台」：

- `commonMain` — 所有平台都编。**只能使用三端都有的 API**。
- `androidMain` — 只有 Android。可以 `import android.*`。
- `iosMain` — 只有 iOS。可以 `import platform.Foundation.*`、`platform.UIKit.*`。
- `jvmMain` — 只有 Desktop。可以 `import java.io.*`。

规则文件 `CLAUDE.md` 里划的红线很明确：**`commonMain` 里绝不能出现 `android.content.Context`、`java.util.Date`、`SimpleDateFormat`、`android.widget.Toast`**。

这不是洁癖。`commonMain` 会被编到 Kotlin/Native（iOS），那里没有 JVM 标准库，一旦写了 `SimpleDateFormat`，iOS 直接编译失败，而且报错信息很难懂。

### 3.2 时间 API 怎么替代

| Android 老写法 | 本项目写法 |
|---|---|
| `System.currentTimeMillis()` | `Clock.System.now().toEpochMilliseconds()` |
| `java.util.Date()` | `Instant.fromEpochMilliseconds(millis)` |
| `SimpleDateFormat("yyyy年M月d日")` | 手写字符串模板（见 `toChineseDate()`） |
| `Calendar.getInstance()` | `TimeZone.currentSystemDefault()` |
| `LocalDate.now()` | `Clock.System.now().toLocalDateTime(tz).date` |

注意 kotlinx-datetime 0.7 的一个破坏性变化：`Clock` 和 `Instant` 挪到了 `kotlin.time` 包，而且需要 `@OptIn(ExperimentalTime::class)`。所以 `DateTimeUtils.kt` 第一行是：

```kotlin
@file:OptIn(ExperimentalTime::class)
package com.qinwei.smartledger.util

import kotlin.time.Clock
import kotlin.time.Instant
```

`import kotlinx.datetime.Clock` 的写法在 0.7 已经废弃了，这是升级时最常见的编译错误。

### 3.3 expect/actual：一次声明，三处实现

以建库为例。`commonMain` 里只声明「有这么个函数」：

```kotlin
// shared/src/commonMain/kotlin/com/qinwei/smartledger/data/DatabaseFactory.kt
expect fun createDatabase(): RoomDatabase.Builder<AppDatabase>

val appDatabase: AppDatabase by lazy { createDatabase().build() }
```

三个平台各给一份实现，因为「数据库文件放哪」天然是平台相关的：

```kotlin
// androidMain：需要 Context 拿私有目录
actual fun createDatabase(): RoomDatabase.Builder<AppDatabase> {
    val context = requireNotNull(appContext) { "先调用 initializeDatabase(context)" }
    return Room.databaseBuilder<AppDatabase>(
        context = context,
        name = context.getDatabasePath("smartledger.db").absolutePath
    ).setDriver(BundledSQLiteDriver())
}

// iosMain：沙盒 Documents
actual fun createDatabase(): RoomDatabase.Builder<AppDatabase> {
    val documentsDir = NSSearchPathForDirectoriesInDomains(...).first() as String
    return Room.databaseBuilder<AppDatabase>(name = "$documentsDir/smartledger.db")
        .setDriver(BundledSQLiteDriver())
}

// jvmMain：用户主目录
actual fun createDatabase(): RoomDatabase.Builder<AppDatabase> {
    val dbFile = File(System.getProperty("user.home"), ".smartledger/smartledger.db")
    dbFile.parentFile?.mkdirs()
    return Room.databaseBuilder<AppDatabase>(name = dbFile.absolutePath)
        .setDriver(BundledSQLiteDriver())
}
```

三个设计点值得记住：

1. **Android 的 Context 通过 `initializeDatabase(context)` 注入**，而不是给 `createDatabase` 加参数。因为加参数的话，另外两个平台的 `actual` 也得挂一个用不上的参数，反而更脏。
2. **`by lazy` 而不是 `synchronized`**。`synchronized` 是 JVM 专有 API，写在 `commonMain` 里 iOS 编不过。`lazy` 默认线程安全（SYNCHRONIZED 模式），K/N 也支持。
3. **`BundledSQLiteDriver` 三端统一**。Room KMP 默认各平台用各自的 SQLite 实现，版本/行为有差异（比如 Android 系统 SQLite 不支持某些窗口函数）。打包一份 SQLite 进去，行为完全一致，代价是 APK 大约多 1MB。这个取舍对账本这种数据敏感的应用是值得的。

### 3.4 `@ConstructedBy`：Room KMP 的硬性要求

```kotlin
@Database(entities = [Transaction::class], version = 1, exportSchema = false)
@TypeConverters(TransactionTypeConverters::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
}

@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
```

为什么需要这个？Android 上 Room 一直用反射 + 无参构造造 `AppDatabase` 实例。但 iOS 的 Kotlin/Native **没有运行时反射**，无法凭空 new 一个抽象类。所以 Room KMP 要求你声明一个 `RoomDatabaseConstructor`，由 KSP 在处理每个平台时生成它的 `actual` 实现，里面直接 `return AppDatabase_Impl()`。

`@Suppress("KotlinNoActualForExpect")` 是为了消除 IDE 误报：KSP 生成的 `actual` 在构建目录里���IDE 静态分析看不见，会以为你没写实现。

---

## 4. 数据层：Room 的 KMP 写法

### 4.1 实体

```kotlin
@Entity(tableName = "transactions", indices = [Index(value = ["timestamp"])])
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val amount: Double,
    val type: TransactionType,
    val category: String,
    val timestamp: Long,
    val note: String = ""
)
```

几个值得说的决策：

- **`timestamp` 用 `Long`（epoch 毫秒）而不是 `LocalDateTime` 或 `java.util.Date`**。`java.util.Date` 在 commonMain 不可用；存 `LocalDateTime` 又会在「用户换时区」时产生歧义（那天 12:00 到底是北京时间还是纽约时间？）。存 UTC 毫秒是唯一无歧义的方案，显示时再转本地日期。
- **`amount` 永远是正数**，正负由 `type` 决定。好处是统计时不用处理符号，`sumOf` 直接可用；坏处是每次展示都要拼 `+` / `-` 前缀（见 `TransactionRow`）。
- **给 `timestamp` 建索引**。列表按时间倒序查、按月过滤，是最热的查询路径。数据量上千条后差别明显。
- **金额用 `Double`**。对个人记账够用，但严格来说财务计算应该用整数分（`Long`）或 `BigDecimal`。100 条账单的浮点误差不会被 `formatMoney` 的两位小数舍入暴露出来，但如果你想做成正式产品，建议改成 `amountCents: Long`。

### 4.2 Dao

```kotlin
@Dao
interface TransactionDao {
    @Insert suspend fun insert(transaction: Transaction): Long
    @Insert suspend fun insertAll(transactions: List<Transaction>)
    @Update suspend fun update(transaction: Transaction)
    @Query("DELETE FROM transactions WHERE id = :id") suspend fun deleteById(id: Long)
    @Query("SELECT COUNT(*) FROM transactions") suspend fun count(): Int
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC") fun observeAll(): Flow<List<Transaction>>
}
```

关键点：**`observeAll()` 返回 `Flow` 而不是 `suspend`**。Room 会跟踪这条查询涉及的表，任何写入后自动重新执行并发射新列表。这是整个应用「零手动刷新」的源头。

### 4.3 枚举转换器

Room 只认基础类型，`TransactionType` 需要转换：

```kotlin
class TransactionTypeConverters {
    @TypeConverter fun fromTransactionType(value: TransactionType): String = value.name
    @TypeConverter fun toTransactionType(value: String): TransactionType = TransactionType.valueOf(value)
}
```

存 `value.name`（字符串）而不是 `ordinal`（序号）。用序号的话，将来在枚举中间插一个值，历史数据全部错位；字符串就不会。

---

## 5. Repository：为什么要多包一层

```kotlin
class TransactionRepository(private val dao: TransactionDao) {
    fun observeAll(): Flow<List<Transaction>> = dao.observeAll()
    suspend fun add(transaction: Transaction): Long = dao.insert(transaction)
    suspend fun update(transaction: Transaction) = dao.update(transaction)
    suspend fun delete(id: Long) = dao.deleteById(id)
    suspend fun restore(transaction: Transaction): Long = dao.insert(transaction)
    suspend fun seedIfEmpty() { if (dao.count() == 0) dao.insertAll(sampleTransactions()) }
}
```

这一层目前几乎是纯转发，看起来多余。留着它的理由：

1. **ViewModel 不依赖 Dao**。以后要加「云同步」「本地缓存」「导入导出」，都在这一层加，ViewModel 一行不改。
2. **跨 Dao 的原子操作有地方落**。比如「删除分类时把该分类的账单改成其他」需要两个 Dao 协作，写在 Repository 里最自然。
3. **`seedIfEmpty()` 这种带业务判断的逻辑**天然属于仓储，不属于 Dao。

`restore()` 有个小技巧：`dao.insert()` 传入 `id != 0` 的记录时，Room 会**沿用这个 id** 插入，而不是自增一个新的。所以撤销删除后，记录的主键不变，UI 里用作 key 的 `transaction.id` 也不会冲突。

---

## 6. ViewModel 与 StateFlow 数据流

### 6.1 为什么过滤放在 ViewModel

```kotlin
private val monthTransactions: StateFlow<List<Transaction>> =
    combine(allTransactions, _selectedMonth) { list, month ->
        list.filter { localDateOf(it.timestamp).yearMonth == month }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
```

注释里已经写明了理由，再展开一下：

如果把月份条件写进 SQL（`WHERE timestamp BETWEEN ? AND ?`），那么每次用户点「上个月」都得新建一个 Flow、取消旧的订阅。而账本数据量本来就在千条级别，全量拉进内存过滤是同步的、零延迟的。

**判断标准**：数据量大到内存放不下、或者筛选条件多到组合爆炸时才下推到 SQL。个人记账永远碰不到这条线。

### 6.2 StateFlow 派生链路

```kotlin
// 全量
private val allTransactions = repository.observeAll()
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

// 当月
private val monthTransactions = combine(allTransactions, _selectedMonth) { ... }
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

// 日分组
val groupedTransactions = monthTransactions
    .map { it.groupBy { t -> localDateOf(t.timestamp) }.map { (d, items) -> DailyGroup(d, items) } }
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

// 合计
val monthlyIncome = monthTransactions.map { ... }.stateIn(...)
val monthlyExpense = monthTransactions.map { ... }.stateIn(...)
val balance = combine(monthlyIncome, monthlyExpense) { i, e -> i - e }.stateIn(...)

// 分类构成
val expenseBreakdown = monthTransactions.map { breakdownOf(it, EXPENSE) }.stateIn(...)
```

这是标准的 **单向数据流（UDF）**：状态往下流，事件往上走。每个 `StateFlow` 都是只读的（`asStateFlow()` 或 `stateIn` 的结果），UI 只能读、不能改，改动一律走 `fun showNextMonth()` 这类方法。

### 6.3 `SharingStarted.Eagerly` 的选择

三个选项：

| 值 | 行为 | 适用 |
|---|---|---|
| `Eagerly` | ViewModel 一创建就收集 | 本项目全部用这个 |
| `Lazily` | 第一个订阅者出现才开始 | 省资源，但首个订阅者会拿到初始值 |
| `WhileSubscribed(5000)` | 最后一个订阅者消失 5 秒后停 | 省电，但切 Tab 会重启流 |

本项目 UI 永远在收集这些 Flow（列表和统计页都在同一个 `LedgerScreen` 里读），用 `Eagerly` 最简单，没有「切 Tab 后数据闪一下初始值」的问题。

如果将来拆成独立的页面（导航到统计页才创建对应的 ViewModel），统计相关的 Flow 应该换成 `WhileSubscribed(5000)`，否则后台会一直算饼图。

### 6.4 `canGoToNextMonth` 防未来月份

```kotlin
val canGoToNextMonth: StateFlow<Boolean> = _selectedMonth
    .map { it < today().yearMonth }
    .stateIn(viewModelScope, SharingStarted.Eagerly, false)
```

`YearMonth` 实现了 `Comparable`，可以直接比大小。UI 把这个布尔值直接喂给箭头的 `enabled`，用户就翻不到未来的空账期。

注意 `showNextMonth()` 里还有一道 `if (next <= today().yearMonth)` 的守卫——**UI 禁用 + 业务层校验**双重保险，比只靠 UI 禁用可靠。

### 6.5 一个可以改进的点

`today()` 在 `canGoToNextMonth` 每次发射时都重新调用。如果用户跨过午夜还开着 App，`canGoToNextMonth` 不会自动更新（没有触发重新计算的信号）。跨日这种边界场景这里没有处理，属于可接受的简化——真要处理，需要在 `onStart` 里加一个「到下一个整点重新发射」的 Flow。

---

## 7. Compose 速成：给 XML 开发者

这一章只讲「读本项目代码需要知道的」，不铺开讲 Compose 全套。

### 7.1 布局对照表

| XML | Compose | 本项目例子 |
|---|---|---|
| `LinearLayout(vertical)` | `Column` | `OverviewCard` 内容 |
| `LinearLayout(horizontal)` | `Row` | `TransactionRow`、`MonthSwitcher` |
| `FrameLayout` | `Box` | 圆环图上的圆心文字 |
| `ScrollView` | `Column` + `Modifier.verticalScroll()` | 记账对话框、统计页 |
| `RecyclerView` | `LazyColumn` + `items()` | 明细列表 |
| `TextView` | `Text` | 到处 |
| `EditText` | `OutlinedTextField` / `TextField` | 金额、备注输入 |
| `margins` | `Modifier.padding()` | — |
| `layout_weight="1"` | `Modifier.weight(1f)` | 概览卡片两栏 |
| `visibility="GONE"` | `if (cond) { ... }` | FAB 只在明细页显示 |
| `style.xml` | `MaterialTheme.typography.xxx` | — |
| `dimens.xml` | 直接写 `16.dp` | — |
| 自定义 `View.onDraw()` | `Canvas` + `DrawScope` | 图标、圆环图 |
| `setOnClickListener` | `Modifier.clickable { }` | 账单行 |
| `findViewById` | **不需要**——直接调用 Composable 函数 | — |

最后一行是最需要转变的：Compose 里没有 View 树，你写的 Composable 函数直接调用子 Composable，「声明」和「实例化」是同一件事。

### 7.2 重组（Recomposition）代替重绘

XML 世界里你改数据 → 手动 `adapter.notifyDataSetChanged()` 或 `textView.text = ...`。
Compose 里你改 State → 读了这个 State 的 Composable 自动重新执行。

```kotlin
val groups by viewModel.groupedTransactions.collectAsState()
// 只要 groups 变了，LedgerTab 就会重新执行
```

只有**读了 State 的那部分**会重新执行，不是整个界面。这就是 Compose 比 XML 快的地方，也是为什么必须把「读 State」尽量下沉到小 Composable 里（本项目做得比较粗，在 `LedgerScreen` 顶部统一 `collectAsState`，可以改成把 `by` 声明挪进对应的子组件）。

### 7.3 `remember` 与 `remember(key)`

```kotlin
var currentTab by remember { mutableStateOf(0) }
```

`remember` 让值在重组之间存活。没有它，每次重组 `currentTab` 都会重置成 0。

带 key 的版本会在 key 变化时**重建**状态：

```kotlin
var amountText by remember(state) {
    mutableStateOf(existing?.amount?.let { formatMoney(it) } ?: "")
}
```

`state` 是「正在新增 / 正在编辑的账单」。用户编辑 A 账单后关掉、再点开 B，如果没这个 key，表单里会残留 A 的内容。加 `key(state)` 就干净了。

**这是 Compose 里最常见的 bug 来源之一**，请记住这条。

### 7.4 `Column` / `Row` / `Box` 的关系

```kotlin
Box {                        // 叠放，后者在上
    Canvas(...)              // 圆环图
    Column { Text("总支出"); Text("¥ 123.00") }  // 叠在圆心
}
```

```kotlin
Row {                        // 横排
    Text("日期", Modifier.weight(1f))  // 占满剩余空间，把右边的推过去
    Text("今天")
}
```

`Modifier.weight(1f)` 等价于 XML 的 `layout_weight`，这里用来做「左内容右对齐」的经典布局。

### 7.5 Modifier 链是有序的

```kotlin
Modifier
    .fillMaxWidth()
    .padding(horizontal = 16.dp)
```

先 `fillMaxWidth` 再 `padding`：宽度先拉满，然后在内部留内边距。
反过来先 `padding` 再 `fillMaxWidth`：先留边距，剩下的宽度拉满。**视觉结果不同**。

XML 里 `width` 和 `padding` 是属性，顺序无所谓；Compose 里 Modifier 是层层包裹的装饰器，顺序即语义。

### 7.6 主题与语义色

```kotlin
MaterialTheme {
    LedgerScreen(repository = repository)
}
```

`MaterialTheme` 提供 `colorScheme`、`typography`、`shapes`。本项目没有自定义配色，用的是 Material 3 默认色板，但语义色用得很规范：

- `MaterialTheme.colorScheme.primary` — 主色
- `onSurfaceVariant` — 次要文字（不用写死灰色，暗色模式自动适配）
- `primaryContainer` / `onPrimaryContainer` — 卡片容器及其上的文字
- `MaterialTheme.colorScheme.error` — 错误提示

而「收入绿 / 支出红」是本应用的业务语义，定义在文件顶部：

```kotlin
private val IncomeColor = Color(0xFF2E7D32)
private val ExpenseColor = Color(0xFFC62828)
```

这两个颜色是写死的。如果你要做暗色模式，绿色在深色背景上对比度会不够，需要提供第二套值（用 `isSystemInDarkTheme()` 切换）。

---

## 8. 明细页拆解

### 8.1 整体骨架：Scaffold

[LedgerScreen.kt:117](../shared/src/commonMain/kotlin/com/qinwei/smartledger/ui/LedgerScreen.kt#L117) 的注释已经把结构写清楚了，这里对照 XML 再讲一遍：

```kotlin
Scaffold(
    topBar = { TopAppBar(title = { MonthSwitcher(...) }) },
    snackbarHost = { SnackbarHost(snackbarHostState) },
    bottomBar = { NavigationBar { ... } },
    floatingActionButton = { if (currentTab == 0) FloatingActionButton(...) }
) { innerPadding ->
    when (currentTab) {
        0 -> LedgerTab(..., modifier = Modifier.fillMaxSize().padding(innerPadding))
        else -> StatsScreen(..., modifier = Modifier.fillMaxSize().padding(innerPadding))
    }
}
```

`Scaffold` 相当于一个「带槽位的 FrameLayout」，本身不管内容，只管把 topBar / bottomBar / FAB / Snackbar 摆在正确位置，并把剩余区域通过 `innerPadding` 交给你。

**`innerPadding` 必须用**。它是 topBar 和 bottomBar 占掉的高度。忘了 `padding(innerPadding)` 的话，列表第一项会被顶栏遮住、最后一项被底部导航遮住——这是 Compose 新手最常犯的错。

注意它已经在 `Scaffold` 里做了 `enableEdgeToEdge` 的适配（Android 端在 `MainActivity` 调了 `enableEdgeToEdge()`），所以 `innerPadding` 里也包含状态栏和手势条的高度，不需要额外处理。

### 8.2 概览卡片

```kotlin
Card(colors = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.primaryContainer
)) {
    Column(Modifier.padding(20.dp)) {
        Text("本月结余", style = labelMedium)
        Text("¥ ${formatMoney(balance)}", style = headlineMedium, fontWeight = Bold)
        Row {  // 两栏
            SummaryCell("本月收入", income, IncomeColor, Modifier.weight(1f))
            SummaryCell("本月支出", expense, ExpenseColor, Modifier.weight(1f))
        }
    }
}
```

`Card` ≈ XML 的 `CardView`，`CardDefaults.cardColors()` ≈ `app:cardBackgroundColor`。

`Modifier.weight(1f)` 让两个格子各占一半。

### 8.3 日期分组列表

```kotlin
LazyColumn(contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 88.dp)) {
    groups.forEach { group ->
        item(key = "header-${group.date}") { DayHeader(group) }   // 分组头
        items(group.items, key = { it.id }) { transaction ->      // 当天的账单
            TransactionRow(...)
        }
    }
}
```

几个要点：

1. **`contentPadding` 的 bottom 给了 88.dp**，专门留出 FAB 的空间，否则最后一条账单永远被悬浮按钮挡住。这是 `clipToPadding` 的 Compose 版本。
2. **`key` 必须给且要唯一**。分组头用 `"header-$date"`（带前缀是因为它和账单的 Long id 类型不同，混用会冲突），账单用 `transaction.id`。没有 key 的话，删除中间一条会导致后面所有 item 复用错位，出现「删除后界面错乱」。
3. **数据已经分好组，所以是 `groups.forEach` 里再嵌 `items`**。一个更"Compose"的写法是用 `stickyHeader`，但 Material 3 的 sticky header 需要额外 API，当前实现够清晰。

### 8.4 账单行

```kotlin
OutlinedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
    Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)) {
        // ① 圆形底衬 + 手绘图标
        Surface(shape = CircleShape, color = accent.copy(alpha = 0.12f), Modifier.size(40.dp)) {
            Box(contentAlignment = Center) {
                CategoryGlyph(icon = iconOf(category), color = accent, Modifier.size(20.dp))
            }
        }
        // ② 分类名 + 时间·备注
        Column(Modifier.weight(1f)) {
            Text(category, style = bodyLarge, fontWeight = Medium)
            Text("14:05 · 午饭", style = bodySmall, maxLines = 1)
        }
        // ③ 金额
        Text("+8000.00", color = accent)
        // ④ 删除
        TextButton(onClick = onDelete) { Text("删除") }
    }
}
```

`accent` 根据收支类型取绿或红，图标底衬用 `accent.copy(alpha = 0.12f)` 得到「同色系浅底」的效果——这是 Material 3 里做图标底衬的标准手法，比写死一个浅灰色好，因为它跟随语义色。

`alpha = 0.12f` 这个魔数不是随便取的：Material 3 规范里「容器色调」的推荐透明度就是 8%–12%。

### 8.5 手绘图标：`CategoryGlyph`

[Category.kt](../shared/src/commonMain/kotlin/com/qinwei/smartledger/model/Category.kt) 里 10 个图标全部用 `Canvas` 画出来，没有引入任何图标库。核心手法：

```kotlin
Canvas(modifier = modifier) {
    val factor = size.minDimension / 24f
    scale(factor, factor, pivot = Offset.Zero) {
        // 此处一律使用 0..24 的「设计稿坐标」
        drawRoundRect(...)
        drawLine(...)
    }
}
```

**为什么用 24×24 设计坐标？** 因为 Material Symbols 的官方图标就是 24×24 网格。你照着标准图标数着格子画，画出来的形状自然规范；而如果直接用百分比坐标，每换一个尺寸都要重算。

`scale()` 把设计坐标等比映射到实际像素尺寸，所以调用方传 `22.dp` 还是 `40.dp`，线宽和比例都保持一致。

饭团图标的实现：

```kotlin
private fun DrawScope.drawFood(color: Color, stroke: Stroke) {
    // 碗身：下半圆弧
    drawArc(color, startAngle = 0f, sweepAngle = 180f, useCenter = false,
        topLeft = Offset(5.5f, 7f), size = Size(13f, 13f), style = stroke)
    // 碗口
    drawLine(color, Offset(3.5f, 13.5f), Offset(20.5f, 13.5f), strokeWidth = stroke.width, cap = Round)
    // 两支筷子
    drawLine(color, Offset(9f, 7f), Offset(10.5f, 3f), ...)
    drawLine(color, Offset(15f, 7f), Offset(16.5f, 3f), ...)
}
```

`Stroke(cap = StrokeCap.Round, join = StrokeJoin.Round)` 让所有端点都是圆头——这正是 Material Symbols 线性图标的特征（对比 Material Icons Outlined 的方头）。这一个参数决定了「像不像官方图标」。

**想加新图标**：在 `CategoryIcon` 枚举里加一项，写一个 `drawXxx` 函数，在 `when (icon)` 里加分支。别忘了同时在 `ExpenseCategories` 或 `IncomeCategories` 里加一个 `CategoryOption`。

### 8.6 记账对话框

[LedgerScreen.kt:679](../shared/src/commonMain/kotlin/com/qinwei/smartledger/ui/LedgerScreen.kt#L679)。结构从上到下：

```
单选框：收入 / 支出     (SingleChoiceSegmentedButtonRow)
     ↓
金额输入                 (OutlinedTextField + Decimal 键盘)
     ↓
日期行                   (点击展开 DatePicker)
     ↓
分类网格 3 列            (手工 chunked(3) 的 Row，无需第三方 Grid)
     ↓
备注输入
     ↓
[取消]  [保存]
```

**为什么分类网格不用 `LazyVerticalGrid`？**

```kotlin
options.chunked(3).forEach { rowItems ->
    Row { rowItems.forEach { ... }; repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) } }
}
```

因为网格在一个可滚动的 `Column`（对话框内容）里，而 `LazyVerticalGrid` 自己也是可滚动的——嵌套可滚动容器在 Compose 里会导致高度测量无限（`IllegalStateException: Vertically scrollable component was measured with an infinity maximum height`）。用固定行数的 `Row` 尺寸自然撑开，没有这个问题。

`repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }` 是补齐最后一行的空位，保证宽度一致。

**表单状态全部用 `remember(state)`**，切换编辑目标时自动重建。`showError` 也是独立状态，只要用户开始改金额就清掉。

**校验在确认按钮里**：

```kotlin
val amount = amountText.toDoubleOrNull()
if (amount == null || amount <= 0 || category.isBlank()) {
    showError = true
} else {
    onConfirm(amount, type, category, noteText.trim(), date)
}
```

`toDoubleOrNull()` 天然处理了「用户输入了非数字」的情况。`amount <= 0` 挡住 0 和负数。

### 8.7 金额格式化

```kotlin
private fun formatMoney(value: Double): String {
    val negative = value < 0
    val cents = (abs(value) * 100).roundToLong()
    val intPart = cents / 100
    val decimalPart = cents % 100
    val decimalStr = if (decimalPart < 10) "0$decimalPart" else decimalPart.toString()
    return (if (negative) "-" else "") + "$intPart.$decimalStr"
}
```

为什么不用 `String.format("%.2f", value)`？那是 JVM API，`commonMain` 里不可用（iOS 编不过）。

这个手写版本把金额转成「分」再拆分，规避了浮点误差：`0.1 + 0.2 = 0.30000000000000004` 经过 `* 100 roundToLong()` 变成 `30`，输出 `0.30`，正确。

**注意**：这个函数在 `LedgerScreen.kt` 和 `StatsScreen.kt` **各写了一份**（都是 `private`）。建议抽到 `util/MoneyFormat.kt` 里，否则将来要改成「千分位分隔」就得改两处，一定漏一个。

---

## 9. 统计页与 Canvas 手绘图表

[StatsScreen.kt](../shared/src/commonMain/kotlin/com/qinwei/smartledger/ui/StatsScreen.kt)。同样不用第三方图表库，全部用 `Canvas` 画。

### 9.1 圆环图

```kotlin
Canvas(Modifier.fillMaxSize()) {
    val strokeWidthPx = size.minDimension * 0.14f
    val inset = strokeWidthPx / 2f
    val arcSize = Size(size.width - strokeWidthPx, size.height - strokeWidthPx)
    val topLeft = Offset(inset, inset)

    val gap = if (data.size > 1) GAP_DEGREES else 0f
    var startAngle = -90f   // -90° = 正上方

    data.forEachIndexed { index, item ->
        val sweep = item.share * 360f
        // 太小的扇区不扣缝，否则角度变负画不出来
        val drawn = if (sweep > gap * 2f) sweep - gap else sweep
        if (drawn > 0f) {
            drawArc(
                color = colorAt(type, index),
                startAngle = startAngle + gap / 2f,
                sweepAngle = drawn,
                useCenter = false,   // ← 关键：不连圆心，得到的是圆环
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Butt)
            )
        }
        startAngle += sweep
    }
}
```

画圆环的三个技巧：

1. **`useCenter = false` + 粗描边**。`useCenter = true` 画出来是扇形（从圆心出发），`false` 只画弧线；再给一个等于环宽的 `strokeWidth`，环就出来了。这比用两个圆相减（`drawCircle` + `blendMode`）简单得多。
2. **`inset = strokeWidthPx / 2`**。描边是沿着路径中心向两侧各扩一半，所以要把绘制区域向内缩半个线宽，否则外侧会被裁掉。
3. **起始角 `-90f`**。Compose 的 `drawArc` 0° 在三点钟方向，顺时针为正。减 90° 让第一个扇区从正上方开始，符合大多数饼图的惯例。

`GAP_DEGREES = 3f` 是扇区之间的空隙，让相近的颜色不会糊在一起。

**边界处理**：`if (sweep > gap * 2f) sweep - gap else sweep`。假设某个分类只占 1%，`sweep = 3.6f`，`3.6 - 3 = 0.6` 还画得出来；但如果 `gap` 设成 5，`3.6 - 5 = -1.4` 就是负角度，`drawArc` 什么都不画，那个分类会凭空消失。这个判断解决了该问题。

### 9.2 圆心信息

```kotlin
Box(Modifier, contentAlignment = Alignment.Center) {
    Canvas(Modifier.fillMaxSize()) { /* 圆环 */ }
    Column(horizontalAlignment = CenterHorizontally) {
        Text("总支出", style = labelMedium)
        Text("¥ ${formatMoney(total)}", style = titleLarge, fontWeight = Bold)
    }
}
```

`Box` 的叠放特性：`Canvas` 先画（在下层），`Column` 后画（在上层）。`contentAlignment = Alignment.Center` 让 `Column` 居中。

这等价于 XML 里 `FrameLayout` 里放 `ImageView` 和 `LinearLayout`，后者 `layout_gravity="center"`。

### 9.3 分类排行：进度条

```kotlin
Box(Modifier.fillMaxWidth().height(6.dp)
    .background(surfaceVariant, RoundedCornerShape(3.dp))) {   // 底层轨道
    Box(Modifier
        .fillMaxWidth(fraction = item.share.coerceIn(0f, 1f))  // 按占比铺宽
        .height(6.dp)
        .background(color, RoundedCornerShape(3.dp)))
}
```

用「一个 Box 里包一个窄 Box」实现进度条，没用 `LinearProgressIndicator`，原因是可以精确控制颜色、高度、圆角，且没有内置动画的干扰。

`coerceIn(0f, 1f)` 防止浮点误差导致 `share` 略微超过 1 时 `fillMaxWidth` 抛异常。

### 9.4 配色

```kotlin
private val ExpensePalette = listOf(
    Color(0xFFC62828), // 红
    Color(0xFFEF6C00), // 橙
    Color(0xFFF9A825), // 琥珀
    Color(0xFF00897B), // 青
    Color(0xFF5E35B1), // 紫
    Color(0xFF546E7A)  // 蓝灰
)
```

选色原则写在注释里了：相邻扇区明度差异够大，色盲用户也能区分。首色沿用全局的支出红，保证和明细页语义一致。

```kotlin
private fun colorAt(type: TransactionType, index: Int): Color {
    val palette = paletteOf(type)
    return palette[index % palette.size]   // 超出调色板时循环复用
}
```

分类多于调色板时循环取色，不会出现无色扇区——但会出现两个相邻扇区同色。真遇到这种情况（支出分类只有 6 个，目前不会触发），应该按色相均分生成颜色而不是循环。

---

## 10. 日期与时区：最容易翻车的地方

### 10.1 核心原则

数据库存 **UTC epoch 毫秒**（`Long`），展示时转成 **系统时区下的 `LocalDate`**。

为什么不用 `LocalDateTime` 直接存？因为「2026-11-12 14:05」这条记录在用户飞到纽约后应该是几点？如果存的是本地时间字面量，它会固执地显示成 14:05，但那时北京时间是凌晨 2 点。存时间戳则始终指向同一个物理时刻，转换后的显示随设备时区变化，这才是正确行为。

### 10.2 工具函数

```kotlin
// 时间戳 → 本地自然日
fun localDateOf(timestamp: Long): LocalDate =
    Instant.fromEpochMilliseconds(timestamp)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date

// 本地自然日 → 当天 00:00 的时间戳
fun LocalDate.startOfDayMillis(): Long =
    atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()

// 今天的自然日
fun today(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
```

`TimeZone.currentSystemDefault()` 每次调用都会读系统时区。用户中途改了时区，后续的转换会自动跟随——这是期望行为。

### 10.3 「今天/昨天/前天」

```kotlin
fun relativeDayLabel(date: LocalDate, base: LocalDate = today()): String? = when (date) {
    base -> "今天"
    base.minus(1, DateTimeUnit.DAY) -> "昨天"
    base.minus(2, DateTimeUnit.DAY) -> "前天"
    else -> null   // 交给调用方格式化具体日期
}
```

用 `minus(1, DateTimeUnit.DAY)` 而不是「减 86400000 毫秒」。因为 `LocalDate` 的运算能正确处理月末（11月1日的前一天是10月31日）和闰年。

返回 `null` 表示「不适用」，由调用方回退到具体日期：

```kotlin
fun LocalDate.toGroupDateLabel(base: LocalDate = today()): String =
    relativeDayLabel(this, base) ?: toChineseDate()   // → "2026年11月3日"
```

`base` 参数默认 `today()`，但允许传入——这让这个函数**可测试**。写单元测试时可以固定 `base`，不受运行日期影响（见第 13 章）。

### 10.4 DatePicker 的时区陷阱

这是本项目最隐蔽的一个坑，注释里已经标出来了：

```kotlin
val zone = TimeZone.UTC
val state = rememberDatePickerState(
    initialSelectedDateMillis = initialDate.atStartOfDayIn(zone).toEpochMilliseconds(),
    ...
)

// 取回时
val picked = Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone).date
```

Material 3 的 `DatePicker` **用 UTC 当天 00:00 的毫秒值表示选中日**，和你设备所在时区无关。

如果你像处理账单时间戳那样用 `TimeZone.currentSystemDefault()` 转换，在北京（UTC+8）会得到「前一天 16:00」，取 `.date` 就是**前一天**，用户的日期永远差一天。

所以这里必须显式用 `TimeZone.UTC`，两边都用，保持自洽。

**记住这条区别**：
- 账单时间戳（表示一个物理时刻）→ 用系统时区
- DatePicker 返回值（表示一个日历日期）→ 用 UTC

### 10.5 补记过去日期的处理

```kotlin
private fun timestampForDate(date: LocalDate): Long {
    val now = nowMillis()
    if (date == today()) return now
    val dayStart = date.startOfDayMillis()
    val offset = if (dayStart <= now) now - today().startOfDayMillis() else 0L
    return dayStart + offset.coerceAtLeast(0L)
}
```

用户补记前几天的账单时，时分秒沿用的是「现在」的时刻。这样同一天内的先后顺序才符合直觉：今天 20:00 补记昨天的晚饭，时间显示 20:00 而不是 00:00。

未来日期（DatePicker 已经禁用了，但这里再防一层）偏移量取 0，落到当天 00:00。

### 10.6 保存后跟随月份

```kotlin
when (state) { ... }          // 新增或编辑
viewModel.selectMonth(date.yearMonth)   // 记账日期可能落在别的月份
editorTarget = null
```

如果用户在 11 月补记一笔 10 月的账单，保存后列表是按 11 月过滤的，新记录看不见——用户会以为没保存成功。所以保存后强制把月份切到账单所在的月份。

**这是一个「数据正确但体验错误」的典型场景**，产品逻辑上必须处理。

---

## 11. 增删改与撤销删除

### 11.1 新增

```kotlin
fun addTransaction(amount, type, category, note, date) {
    viewModelScope.launch {
        repository.add(Transaction(
            amount = amount, type = type, category = category,
            timestamp = timestampForDate(date), note = note
            // id 默认 0，交给 Room 自增
        ))
    }
}
```

`viewModelScope.launch` 启动的协程在 ViewModel 销毁时自动取消，不需要手动管理生命周期——这是 Kotlin 协程相对于 `AsyncTask` / 手动 `CoroutineScope` 的最大优势。

### 11.2 编辑

```kotlin
fun updateTransaction(id, amount, type, category, note, date, originalTimestamp) {
    viewModelScope.launch {
        // 日期没改动时沿用原时间，避免编辑备注把手记的时分秒冲掉
        val timestamp =
            if (localDateOf(originalTimestamp) == date) originalTimestamp
            else timestampForDate(date)
        repository.update(Transaction(id, amount, type, category, timestamp, note))
    }
}
```

这个细节很重要：用户只是改个备注，日期没动。如果无脑调 `timestampForDate(date)`，原来 08:15 记的账会变成「现在」的时间（比如 23:40），列表顺序全乱。所以要判断日期是否真的变了。

### 11.3 删除 + 撤销

这是 Compose 里做 Snackbar 撤销的标准模式：

```kotlin
fun requestDelete(transaction: Transaction) {
    viewModel.deleteTransaction(transaction) {           // 删完回调
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "已删除「${transaction.category}」账单",
                actionLabel = "撤销",
                withDismissAction = true,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDelete(transaction)
            }
        }
    }
}
```

`showSnackbar` 是一个**挂起函数**：它会一直挂起到用户点击「撤销」、或者超时、或者被新 Snackbar 顶掉，然后返回对应的 `SnackbarResult`。所以删除回调之后才能决定要不要撤销。

`undoDelete` 调用 `repository.restore(transaction)`，用原 id 插回去。因为 `TransactionDao.delete` 是物理删除，这条记录已经从库里消失，重新插入不会冲突。

**为什么不用软删除（`isDeleted` 字段）？**

软删除的正确性更好（撤销就是改一个布尔值），但每个查询都要加 `WHERE isDeleted = 0`，且无法区分「用户真的要删」和「用户在犹豫」。当前方案简单，且删除后立即进入 Snackbar 3 秒窗口，窗口一过就真删了——行为符合用户心智。

真要做回收站功能，就得改成软删除，那是另一个需求。

### 11.4 `rememberCoroutineScope` 是什么

```kotlin
val scope = rememberCoroutineScope()
```

它给 Composable 一个绑定到当前组合生命周期的 `CoroutineScope`。和 `viewModelScope` 的区别：

- `viewModelScope` — 跟着 ViewModel 走。ViewModel 销毁才取消。适合「业务操作」。
- `rememberCoroutineScope()` — 跟着 Composable 走。界面离开组合就取消。适合「UI 交互」。

弹 Snackbar 显然属于 UI 交互（界面都没了还弹什么），所以用后者。

---

## 12. 三端入口：同一份 UI 怎么启动

### 12.1 共享入口

```kotlin
// commonMain/App.kt
@Composable
fun App(repository: TransactionRepository) {
    MaterialTheme {
        LedgerScreen(repository = repository)
    }
}
```

`App` 只做两件事：套主题、传 Repository。**它不创建数据库**，因为创建数据库需要平台上下文。

### 12.2 Android

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        initializeDatabase(this)          // 注入 Context
        val repository = TransactionRepository(appDatabase.transactionDao())

        setContent {
            App(repository = repository)
        }
    }
}
```

`setContent { }` 就是 Compose 版的 `setContentView(R.layout.xxx)`，只不过参数是一段 Composable lambda 而不是布局 ID。

`enableEdgeToEdge()` 让内容延伸到状态栏和导航栏下方，实际留白由 `Scaffold` 的 `innerPadding` 处理。

一个可以改进的点：`repository` 在 `onCreate` 里创建，Activity 重建（旋转屏幕）时会重新创建一次。因为 `appDatabase` 是全局 `lazy` 的，重连开销可接受，但更规范的做法是放进 `Application` 或者用依赖注入。

### 12.3 Desktop

```kotlin
fun main() = application {
    val repository = TransactionRepository(appDatabase.transactionDao())
    Window(onCloseRequest = ::exitApplication, title = "SmartLedger") {
        App(repository = repository)
    }
}
```

`application { }` 是 Compose Desktop 的入口 DSL，`Window { }` 相当于开一个顶层窗口。

### 12.4 iOS

Swift 侧只有一层桥：

```swift
// ContentView.swift
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Self.Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Self.Context) {}
}
```

Kotlin 侧：

```kotlin
// iosMain/MainViewController.kt
fun MainViewController() = ComposeUIViewController {
    val repository = TransactionRepository(appDatabase.transactionDao())
    App(repository = repository)
}
```

`ComposeUIViewController { }` 把 Composable 包成一个 `UIViewController`，Swift 通过 `UIViewControllerRepresentable` 嵌进 SwiftUI。

注意 `MainViewControllerKt`——Kotlin 顶层函数会被编译成名为 `<文件名>Kt` 的类的静态方法，所以 `MainViewController.kt` 里的顶层函数就是 `MainViewControllerKt.MainViewController()`。

`.ignoresSafeArea()` 让 Compose 自己处理安全区，与 Android 的 `enableEdgeToEdge()` 对应。

---

## 13. 扩展实战

### 13.1 练习一：给 Transaction 加一个「账户」字段

这是最典型的需求（现金 / 银行 / 信用卡）。步骤：

1. `Transaction` 加字段：`val account: String = ""`
2. `AppDatabase` 的 `version` 改 `2`
3. 补 Migration：

```kotlin
// shared/src/commonMain/kotlin/com/qinwei/smartledger/data/AppDatabase.kt
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE transactions ADD COLUMN account TEXT NOT NULL DEFAULT ''")
    }
}
```

然后在三个平台的 `createDatabase()` 里都加 `.addMigrations(MIGRATION_1_2)`。

注意 Room KMP 的 `Migration.migrate` 参数是 `SQLiteConnection`（`androidx.sqlite` 的新 API），不是老版的 `SupportSQLiteDatabase`。

4. 对话框加一个账户选择（可以复用 `CategoryGrid` 的模式）
5. `breakdownOf` 加一个按账户分组的版本

### 13.2 练习二：加一个分类

以「教育」为例：

```kotlin
// Category.kt
enum class CategoryIcon {
    FOOD, TRANSPORT, SHOPPING, ENTERTAINMENT, HOME, MEDICAL,
    SALARY, BONUS, INVESTMENT, OTHER,
    EDUCATION   // ← 新增
}

val ExpenseCategories = listOf(
    ...
    CategoryOption("教育", CategoryIcon.EDUCATION, TransactionType.EXPENSE)
)

// 画一个「书」图标
private fun DrawScope.drawEducation(color: Color, stroke: Stroke) {
    drawRoundRect(color, topLeft = Offset(3.5f, 4f), size = Size(17f, 16f),
        cornerRadius = CornerRadius(1.5f), style = stroke)
    drawLine(color, Offset(12f, 5f), Offset(12f, 19f),   // 书脊
        strokeWidth = stroke.width, cap = StrokeCap.Round)
}
```

再在 `CategoryGlyph` 的 `when` 里加一行 `CategoryIcon.EDUCATION -> drawEducation(color, stroke)`。

**别忘了 `ExpensePalette`**：目前 6 个颜色对应 6 个支出分类，加了第 7 个之后会循环复用第一个颜色（红），饼图首尾同色。记得往调色板里补一个。

### 13.3 练习三：加一个柱状图

统计页现在是圆环 + 排行。加一个「近 6 个月支出趋势」柱状图：

```kotlin
@Composable
private fun BarChart(
    values: List<Double>,
    labels: List<String>,
    modifier: Modifier = Modifier
) {
    val maxValue = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    Canvas(modifier) {
        val barWidth = size.width / (values.size * 2f)
        val gap = barWidth
        values.forEachIndexed { index, value ->
            val ratio = (value / maxValue).toFloat()
            val barHeight = size.height * ratio
            drawRoundRect(
                color = MaterialTheme.colorScheme.primary,
                topLeft = Offset(index * (barWidth + gap) + gap / 2f, size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 4f)
            )
        }
    }
}
```

数据来源需要新的 ViewModel 状态：按月聚合计 6 个月。

注意：`MaterialTheme.colorScheme` 在 `Canvas` 的 lambda 里不能直接读（那是 `DrawScope`，不是 `@Composable`）。要把颜色提到外面：

```kotlin
val barColor = MaterialTheme.colorScheme.primary
Canvas(modifier) {
    drawRoundRect(color = barColor, ...)   // ← 用捕获的变量
}
```

这是 Compose 新手常犯的错，编译器报错信息是 `@Composable invocations can only happen from the context of a @Composable function`。

### 13.4 练习四：写第一个有意义的测试

现有的测试都是 `assertEquals(3, 1+2)`。真正值得测的是纯函数，比如日期逻辑：

```kotlin
// shared/src/commonTest/kotlin/com/qinwei/smartledger/DateLabelTest.kt
class DateLabelTest {
    @Test
    fun relativeLabelUsesBase() {
        val base = LocalDate(2026, 11, 12)
        assertEquals("今天", LocalDate(2026, 11, 12).relativeDayLabel(base))
        assertEquals("昨天", LocalDate(2026, 11, 11).relativeDayLabel(base))
        assertEquals("前天", LocalDate(2026, 11, 10).relativeDayLabel(base))
        assertNull(LocalDate(2026, 11, 9).relativeDayLabel(base))
    }

    @Test
    fun relativeLabelHandlesMonthBoundary() {
        val base = LocalDate(2026, 11, 1)
        assertEquals("昨天", LocalDate(2026, 10, 31).relativeDayLabel(base))
    }
}
```

`relativeDayLabel` 的 `base` 参数就是为了这种测试而设计的——不传的话每次测试结果都依赖运行日期。

`commonTest` 里的测试会自动在所有平台跑，所以 `./gradlew :shared:jvmTest` 和 `:shared:testAndroidHostTest` 都会执行它。

---

## 14. 常见坑与排查

### 14.1 编译期

| 症状 | 原因 | 解决 |
|---|---|---|
| `Unresolved reference: java` | `commonMain` 里用了 JVM API | 换成 kotlinx 对应 API |
| `Unresolved reference: Clock`（`kotlinx.datetime.Clock`） | 0.7 版本 API 挪了包 | 改成 `import kotlin.time.Clock` |
| `This API requires opt-in` (ExperimentalTime) | `Instant`/`Clock` 还是实验性的 | 文件头加 `@file:OptIn(ExperimentalTime::class)` |
| `KotlinNoActualForExpect` 警告 | KSP 生成的 `actual` IDE 看不见 | `@Suppress`（已在代码里） |
| `RoomDatabaseConstructor` 未生成 | KSP 没配置或没 sync | 检查 `build.gradle.kts` 里的 `add("kspAndroid", ...)` 等四行 |
| `Cannot access 'AppDatabase'` 在 `androidApp` 模块 | Room 依赖用了 `implementation` | 必须是 `api(libs.androidx.room.runtime)`（本项目已如此） |

### 14.2 运行时

| 症状 | 原因 | 解决 |
|---|---|---|
| 界面顶部被遮挡 | 忘了 `padding(innerPadding)` | `Modifier.padding(innerPadding)` |
| 最后一条被 FAB 挡住 | `contentPadding` 底部不够 | 给 88.dp 左右 |
| 删除后列表错乱 | `items` 没给 `key` | `key = { it.id }` |
| 编辑 A 后再编辑 B 残留 A 的内容 | `remember` 缺 key | `remember(state)` |
| 日期选择器选的日期差一天 | 用了系统时区转 UTC millis | 统一用 `TimeZone.UTC` |
| `Vertically scrollable component was measured with an infinity maximum height` | 嵌套可滚动容器 | 内层改 `Column`（见分类网格） |
| `@Composable invocations can only happen...` 在 Canvas 里 | 在 `DrawScope` 里读 `MaterialTheme` | 提到 Canvas 外面存成局部变量 |
| 保存后看不见新账单 | 账单日期不在当前月份 | `selectMonth(date.yearMonth)` |

### 14.3 平台相关

**iOS 报 `Shared.framework not found`**
→ Xcode 的 Build Phase 脚本失败了。去 Report Navigator 看 Gradle 输出。常见原因：`Config.xcconfig` 的 `TEAM_ID` 为空、Gradle 没权限、或者 Xcode 选择的架构和 `shared/build.gradle.kts` 里声明的目标不匹配（本项目只有 `iosArm64` 和 `iosSimulatorArm64`，**没有 Intel Mac 模拟器用的 `iosX64`**，老 Mac 上跑模拟器会失败）。

**Android 报 `先调用 initializeDatabase(context)`**
→ `MainActivity` 里漏了 `initializeDatabase(this)`，或者有其他地方在 `onCreate` 之前访问了 `appDatabase`。

**Desktop 数据库在哪**
→ `~/.smartledger/smartledger.db`。想重置数据就删这个目录。

**Android 数据库在哪**
→ `/data/data/com.qinwei.smartledger/databases/smartledger.db`，用 Android Studio 的 Device Explorer 看，或者 `adb shell run-as com.qinwei.smartledger`。

### 14.4 热重载不生效

`./gradlew :desktopApp:hotRun --auto` 只对 Composable 函数的**函数体改动**生效。以下情况必须重启：

- 改了函数签名（参数增删、类型变化）
- 新增/删除了 Composable
- 改了 `remember` 的 key 结构
- 改了非 UI 代码（ViewModel、Repository）

这是 Compose 热重载的固有限制，不是配置问题。

---

## 15. 附录：文件索引与待清理项

### 15.1 按「改什么功能看哪个文件」

| 需求 | 位置 |
|---|---|
| 加字段 | `model/Transaction.kt` + `AppDatabase` 版本号 + Migration |
| 加分类/图标 | `model/Category.kt` + `StatsScreen` 的 `ExpensePalette` |
| 改业务规则（例如预算提醒） | `viewmodel/LedgerViewModel.kt` |
| 改界面布局 | `ui/LedgerScreen.kt` / `ui/StatsScreen.kt` |
| 改数据库类型转换 | `data/TransactionTypeConverters.kt` |
| 加平台能力（导出 CSV 等） | `commonMain` 写 `expect`，三个 `xMain` 写 `actual` |
| 改数据库位置 | 三个 `DatabaseFactory.*.kt` |

### 15.2 模板残留（可以清理）

这三个文件是 KMP 项目模板自带、本项目实际未使用的死代码：

| 文件 | 状态 |
|---|---|
| `Greeting.kt` | 未被任何代码引用 |
| `GreetingUtil.kt` | 未被任何代码引用 |
| `Platform.kt` + 三个 `Platform.*.kt` | 只被 `Greeting.kt` 引用，等效于未使用 |
| `util/Time.kt` + 三个 `Time.*.kt` | `expect fun currentTimeMillis()` 无人调用（`DateTimeUtils` 用的是 `Clock.System`） |
| 四个 `Shared*Test.kt` | 内容都是 `assertEquals(3, 1+2)` |

删掉它们不影响任何功能。`Platform` 将来可能有用（比如在「关于」页显示版本），可以先留着；`Greeting` 系列和 `Time.kt` 建议删掉。

### 15.3 其他可以改进的点

1. **`formatMoney` 重复了两份**（`LedgerScreen.kt:980` 和 `StatsScreen.kt:321`）。抽到 `util/MoneyFormat.kt`。
2. **`LedgerScreen` 顶部一次性 `collectAsState` 了 8 个 StateFlow**，任何一个变化都会让整个 `LedgerScreen` 重组。可以把 `collectAsState` 下沉到子 Composable（比如统计页自己收集 `expenseBreakdown`），重组范围更精确。数据量小的时候无感，但这是个好习惯。
3. **金额用 `Double`**。要做严肃的财务计算建议改成 `Long`（单位：分）。
4. **没有可配置的预算/提醒功能**，这是账本类应用最该有的下一步功能。
5. **`CLAUDE.md` 里写的路径是 `composeApp/src/commonMain/kotlin`，但实际模块名是 `shared`**。新加入的协作者会找不到目录，建议顺手改一下。
6. **测试覆盖率几乎为零**。优先给 `relativeDayLabel` / `formatTimeOfDay` / `breakdownOf` 这类纯函数补测试，性价比最高。

---

## 结语

这个项目的技术选型很克制：没有 DI 框架（手动注入）、没有导航库（两个 Tab 用 `when` 切换）、没有图标库（Canvas 手绘）、没有图表库（Canvas 手绘）。对于一个个人账本应用，这个复杂度是合适的——每一层都能用一屏代码讲清楚。

真正需要花时间适应的是三件事：

1. **Compose 的心智模型**：声明式 + 重组，而不是命令式的 `findViewById` + 手动更新。
2. **commonMain 的边界**：写代码前先问「这个 API 在 iOS 上有吗」。
3. **Flow 驱动的数据流**：状态派生状态，而不是到处手动刷新。

这三件事过了，剩下的都是熟悉度问题。

建议的下一步：先跑通 Desktop 热重载，然后做 13.2 的练习（加一个分类），因为那个练习会同时碰 Model、Category、UI、Canvas 四处，是最快建立全局认知的路径。
