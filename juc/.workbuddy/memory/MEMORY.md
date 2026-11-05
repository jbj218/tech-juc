# 项目约定（JUC 学习项目 D:/ideaProject/juc）

## 协作约定
- **回答不要包含「疑问」小节/环节**（2026-10-10 用户明确要求）。用户认为这属于额外的注意力负担。所有解释直接在正文说清，不要末尾另起「疑问」「易错点提醒」这类附加板块。
- 代码改动前先与用户确认，未经允许不擅自改代码。
- 讲解风格：先给一句话结论，再按需展开；细节（源码行、字节码、边界条件）放追问层。
- 正文只做知识点讲解，不穿插「坑/踩坑」式提醒。

## 项目背景
- 目录结构：`src/main/java/com/kuang/`，按主题分包（pc、lock、add、bq、cas、tvolatile、unsafe、single、function、stream、forkjoin、pool、future 等），跟随狂神说 / 黑马 JUC 教程。
- 关键文件：
  - `pc/A.java`：synchronized 生产者消费者，4 线程 + notifyAll，用 `while`。
  - `pc/B.java`：ReentrantLock + Condition 版本（decrement 上误带 synchronized）。
  - `pc/C.java`：Data3 三 Condition 定向唤醒，A→B→C 接力。
  - `add/CyclicBarrierDemo.java`：7 线程集龙珠，含 `final int temp = i;` 捕获示例。

## 已讲透的核心结论（可复用口径）
1. **wait 是挂起点不是方法结束**：唤醒后从 `wait()` 下一行继续，不回方法头。`while` 靠编译期生成的 `goto` 跳回判断；`if` 无此跳转。
2. **synchronized 管互斥/可见性，不管唤醒后条件是否成立**；`while` 承担「唤醒后复检」。
3. **Lambda 捕获的是值副本**（capture by value），复制发生在 **main 创建 Lambda 对象那一刻**，非子线程执行时。用户最易错点：以为子线程运行时才去访问 main 变量。
4. **职责分离**：复制到堆 → 解决生命周期；`final`/有效 final → 解决语义歧义。二者不可互换。
5. 循环变量不满足有效 final（被 `i++` 重赋值）；`final int temp = i;` 须在循环体内声明。
