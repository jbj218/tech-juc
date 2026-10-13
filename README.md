# JUC 并发编程笔记

`java.util.concurrent` 这一块的笔记和疑问整理。

## 📖 内容

| 文件 | 说明 |
| --- | --- |
| [狂神说JUC笔记.md](./狂神说JUC笔记.md) | 按章节顺序整理的一份 JUC 笔记，21 节：JUC 是什么 → 线程与进程 → Lock 锁 → 生产者消费者 → 8 锁现象 → 集合类不安全 → Callable → 并发工具类 → 读写锁 → 阻塞队列 → 线程池 → 四大函数式接口 → Stream → ForkJoin → 异步回调 → JMM → volatile → 单例模式 → CAS → 原子引用 → 各种锁。含大量源码片段与源码级拆解。 |
| [JUC相关疑问.md](./JUC相关疑问.md) | 卡住过的问题的问答记录：wait / sleep 谁释放锁、虚假唤醒为什么必须用 while、lambda 为什么只能捕获 effectively final、读锁的意义、线程池参数怎么定、`sorted` 掉个头就变降序、`reduce` 的单位元、饿汉式单例为什么算浪费空间、`AtomicStampedReference` 的包装类陷阱。 |

### 附：课程原始资料

`JUC狂神源资料/` 是与笔记配套的原始素材（约 44 MB），一并收录：

| 文件 | 说明 |
| --- | --- |
| `狂神说JUC笔记.pdf` | 课程讲义 PDF（2.2 MB） |
| `jdk api 1.8_google.CHM` | JDK 1.8 API 中英对照离线文档（40.9 MB） |
| `代码/` | 课程演示源码工程（Maven 项目，`com.kuang.*` 各章节示例） |
| `代码.rar` | 上述源码工程的压缩包备份 |

## 🛠️ 覆盖内容

`JUC` `java.util.concurrent` `ReentrantLock` `Condition` `CountDownLatch` `CyclicBarrier` `Semaphore` `ReadWriteLock` `BlockingQueue` `SynchronousQueue` `ThreadPoolExecutor` `拒绝策略` `四大函数式接口` `Stream` `ForkJoin` `CompletableFuture` `JMM` `volatile` `CAS` `AtomicStampedReference` `DCL 单例` `自旋锁` `死锁排查`

## 说明

- 笔记按「狂神说 Java」JUC 课程的章节顺序整理，代码片段来自课程演示与 JDK 文档，批注是自己补的。
- `JUC相关疑问.md` 是遇到问题时记下的原始问答，问题保留了当时的问法。
- `JUC狂神源资料/` 为课程方提供的原始素材（讲义 PDF、JDK API 离线文档、演示源码），版权归原作者，此处仅作个人存档与学习检索之用，请勿用于商业用途。如果原作者认为不妥，可随时删除。
