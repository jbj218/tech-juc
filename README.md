# JUC 并发编程笔记

`java.util.concurrent` 这一块的笔记和疑问整理。

## 📖 内容

| 文件 | 说明 |
| --- | --- |
| [狂神说JUC笔记.md](./狂神说JUC笔记.md) | 按章节顺序整理的一份 JUC 笔记，21 节：JUC 是什么 → 线程与进程 → Lock 锁 → 生产者消费者 → 8 锁现象 → 集合类不安全 → Callable → 并发工具类 → 读写锁 → 阻塞队列 → 线程池 → 四大函数式接口 → Stream → ForkJoin → 异步回调 → JMM → volatile → 单例模式 → CAS → 原子引用 → 各种锁。含大量源码片段与源码级拆解。 |
| [JUC相关疑问.md](./JUC相关疑问.md) | 卡住过的问题的问答记录：wait / sleep 谁释放锁、虚假唤醒为什么必须用 while、lambda 为什么只能捕获 effectively final、读锁的意义、线程池参数怎么定、`sorted` 掉个头就变降序、`reduce` 的单位元、饿汉式单例为什么算浪费空间、`AtomicStampedReference` 的包装类陷阱。 |

## 🛠️ 覆盖内容

`JUC` `java.util.concurrent` `ReentrantLock` `Condition` `CountDownLatch` `CyclicBarrier` `Semaphore` `ReadWriteLock` `BlockingQueue` `SynchronousQueue` `ThreadPoolExecutor` `拒绝策略` `四大函数式接口` `Stream` `ForkJoin` `CompletableFuture` `JMM` `volatile` `CAS` `AtomicStampedReference` `DCL 单例` `自旋锁` `死锁排查`

## 说明

- 笔记按「狂神说 Java」JUC 课程的章节顺序整理，代码片段来自课程演示与 JDK 文档，批注是自己补的。
- `JUC相关疑问.md` 是遇到问题时记下的原始问答，问题保留了当时的问法。
- 课程附带的原始资料（PDF、JDK API 文档、课程源码工程）体积大且属于课程方素材，未随本仓库发布。
