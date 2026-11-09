# JUC 相关疑问

> 这里记的是看 JUC 时真正卡住过的问题：先把当时的疑问写下来，再把想通之后的结论整理一遍。

## 3. 回顾多线程

> 为什么说 wait 会释放锁，sleep 不会释放锁呢？他们不都是到点释放锁吗？

### 先澄清一个核心误区

你说“不都是到点释放锁吗”——**不是的**。准确地说：

- `sleep`：**从来就不涉及“释放锁”这个动作**，它只是让当前线程暂停执行。
- `wait`：**主动释放锁**，而且是它设计上必须做的事。

所以问题不是“到点释放”，而是“在等待期间，锁到底在谁手里”。

---

### `sleep` 为什么不释放锁？

`sleep` 是 `Thread` 类的静态方法，它的作用很简单：**让当前线程休眠指定时间**。

它和锁没有任何直接关系。如果当前线程恰好持有某把锁（比如在 `synchronized` 块里），那么：

```java
synchronized (lock) {
    Thread.sleep(1000); // 线程睡了，但锁还在它手里
}
```

在这 1 秒内，**其他线程依然无法进入这个 `synchronized` 块**，因为锁没有被释放。`sleep` 只是让 CPU 不再调度这个线程，但线程持有的监视器锁（monitor）依然归它所有。

换句话说：`sleep` 是“我歇会儿，但东西我还拿着”。

---

### `wait` 为什么必须释放锁？

`wait` 是 `Object` 类的方法，它必须在 `synchronized` 块中调用（否则抛 `IllegalMonitorStateException`）。它的语义是：

> **“我暂时不满足继续执行的条件，先释放锁，让别的线程进来干活；等有人通知我（`notify`/`notifyAll`）或者时间到了，我再重新竞争锁。”**

```java
synchronized (lock) {
    while (!condition) {
        lock.wait(); // 释放锁，线程进入等待队列
    }
    // 被唤醒后，重新拿到锁才会从这里继续
}
```

如果 `wait` 不释放锁，那就会死锁：生产者等着消费者消费，消费者又拿不到锁无法消费，双方永远卡死。所以 **`wait` 释放锁是它存在的根本前提**。

注意一个细节：`wait` 被唤醒后，**不是立刻继续执行**，而是要**先重新竞争到锁，才能从 `wait()` 返回**。也就是说，等待期间锁是释放的，醒来后要重新抢。

### 对比总结

| 特性 | `sleep` | `wait` |
| :--- | :--- | :--- |
| 所属类 | `Thread` | `Object` |
| 是否释放锁 | **不释放** | **释放** |
| 调用前提 | 任意位置 | 必须在 `synchronized` 中 |
| 唤醒方式 | 时间到自动醒 | `notify`/`notifyAll` 或超时 |
| 醒来后 | 继续执行 | 需重新竞争锁 |

---

### 一句话记忆

- `sleep`：**抱着锁睡觉**。
- `wait`：**放下锁等人叫**。

所以不是“到点释放锁”的问题，而是 `sleep` 压根就不放锁，`wait` 则是主动放锁。两者的设计目的完全不同：`sleep` 只是延时，`wait` 是线程间协作。

## 6.Synchronized和Lock的区别

1、Synchronized 内置的Java关键字， Lock 是一个Java类

2、Synchronized 无法判断获取锁的状态，Lock 可以判断是否获取到了锁

3、Synchronized 会自动释放锁，lock 必须要手动释放锁！如果不释放锁，**死锁**

4、Synchronized 线程 1（获得锁，阻塞）、线程2（等待，傻傻的等）；Lock锁就不一定会等待下

去；

5、Synchronized 可重入锁，不可以中断的，非公平；Lock ，可重入锁，可以 判断锁，非公平（可以

自己设置）；

6、Synchronized 适合锁少量的代码同步问题，Lock 适合锁大量的同步代码！



==本人困惑的区别解惑==

> synchronized锁可以通过锁的是类、静态方法、实例方法、字段、代码块等来判断是否锁的是同一资源，lock锁好像锁的都是代码片段，那我怎么保证锁的是同一资源来防止并发呢？比如lock锁的代码块中有一个静态方法，那他算锁的是当下对象还是那个唯一类对象呢

### 核心认知：锁的本质是“锁对象”，而不是“锁代码”

无论是 `synchronized` 还是 `Lock`，**代码本身是没法被“锁住”的**。代码只是指令，多线程可以同时执行同一段代码。
我们真正锁住的，是**内存中的某个对象（Object）**。

- **`synchronized` 的语法（锁实例、锁类、锁方法）**，本质上都是 JVM 帮你==隐式地指定了“锁对象”==（即 Monitor 关联的对象）。
- **`Lock` 的用法**，则是要求你**显式地**创建一个对象（通常是一个 `Lock` 的实现类，比如 `ReentrantLock`），把它的引用当作“锁对象”来用。

也就是说Synchronized锁的对象是**通过锁的位置来锚定某个对象作为争抢资源**。Lock锁是**自己创建一个锁资源用于争抢**，多线程抢的都是那个自己申请的Lock对象。

## 7. 传统的生产者消费者问题

> 为什么使用 if 判断会导致虚假唤醒问题，需要将 `if` 改为 `while`，不是有 synchronized 保证并发吗？

```java
package com.kuang.pc;

/**
 * 线程之间的通信问题：生产者和消费者问题！  等待唤醒，通知唤醒
 * 线程交替执行  A   B 操作同一个变量   num = 0
 * A num+1
 * B num-1
 */
public class A {
    public static void main(String[] args) {
        Data data = new Data();

        new Thread(()->{
            for (int i = 0; i < 10; i++) {
                try {
                    data.increment();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        },"A").start();

        new Thread(()->{
            for (int i = 0; i < 10; i++) {
                try {
                    data.decrement();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        },"B").start();

        new Thread(()->{
            for (int i = 0; i < 10; i++) {
                try {
                    data.increment();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        },"C").start();

        new Thread(()->{
            for (int i = 0; i < 10; i++) {
                try {
                    data.decrement();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        },"D").start();
    }
}

// 判断等待，业务，通知
class Data{ // 数字 资源类

    private int number = 0;

    //+1
    public synchronized void increment() throws InterruptedException {
        if (number!=0){  //0
            // 等待
            this.wait();
        }
        number++;
        System.out.println(Thread.currentThread().getName()+"=>"+number);
        // 通知其他线程，我+1完毕了
        //lock.notify()：随机唤醒等待队列中的一个线程。
        //lock.notifyAll()：唤醒等待队列中的所有线程。
        this.notifyAll();
    }

    //-1
    public synchronized void decrement() throws InterruptedException {
        if (number==0){ // 1
            // 等待
            this.wait();
        }
        number--;
        System.out.println(Thread.currentThread().getName()+"=>"+number);
        // 通知其他线程，我-1完毕了
        this.notifyAll();
    }

}
```

直接看一种真实会发生的调度顺序。

**初始状态：`number = 0`，A、C 在等 `number != 0`，B、D 不满足 `number == 0` 所以没进 wait（它们会先执行到 decrement 的 wait）。**

真正有杀伤力的时序是这样的：

| 步骤 | 线程 | `number` | 动作与说明 |
| :--- | :--- | :--- | :--- |
| 1 | A | 0 | `if(number!=0)` 为 false，跳过 wait，`number++` → **1** |
| 2 | — | 1 | `notifyAll()` 唤醒所有等待者 |
| 3 | C | 1 | 被唤醒，从 `wait()` 返回，**`if` 直接跳过判断** |
| 4 | — | 1 | C 执行 `number++` → **2**（本不该发生） |
| 5 | — | 2 | C 打印 `C=>2` |

关键在**第 3 步**：C 被唤醒时 `number` 已经是 1（A 刚改的），按业务语义它**应该继续等**（`number != 0` 仍成立），但 `if` 只判断过一次，醒来后就当条件成立了，于是又加了一次——输出变成 `A=>1`、`C=>2`，连续两次 `+1` 没有任何 `-1`，**交替被破坏**。

而 `while` 版本在第 3 步会重新回到判断：`number(1) != 0` 为 true → 再次 `wait()`，正确挂起。

> 为啥 C 线程会跳过 if 判断啊？C 线程不是一个新的线程，不是还得从上到下执行吗？怎么 while 就特殊了？

这是问题的真正症结所在，疑问可以归结为一句话：

**`wait()` 不是「方法的结束」，而是「方法的暂停点」。** 线程被唤醒后，**从 `wait()` 那一行的下一句继续执行**，而不是像新线程那样从方法第一行重新走一遍。

先看一句关键区分，这是理解全部问题的钥匙：

| | 执行起点 | 会不会重走前面的代码 |
| :--- | :--- | :--- |
| **新线程首次进入方法** | 方法第一行 | 会，从上到下全部走一遍 |
| **`wait()` 被唤醒后** | `wait()` 这一行的**下一行** | **不会**，前面的判断早就执行过了 |

**`if` 版本**

```text
行63: 判断 number != 0
行64: ifeq  行67        // 条件为 false → 跳到 67
行65: wait()
行67: number++    ← 唤醒后从 wait 返回，落到这里
行70: notifyAll()
```

**`while` 版本**

```text
行63: 判断 number != 0   ┐
行64: ifeq  行67         │ ← 这个「标签」就是循环头
行65: wait()             │
行66: goto  行63         ┘ ← 【关键：从这里跳回判断】
行67: number++
行70: notifyAll()
```

差别就在**多了一条 `goto`（回跳指令）**。`while` 编译出来的字节码在 `wait()` 之后有一个**无条件跳转，指回条件判断处**；而 `if` 编译出来后，`wait()` 的下一句就是 `number++`。

## 8.Lock版生产者消费者问题

## 15.异步回调

### 前置知识：线程中断

==线程中断只是信号，不是结果==

> future接口中的cancel方法中传入true 和false的意义何在呢？如果填入false对于正在执行的线程不会中断。那这个cancel的意义何在呢？

关键在于：**`cancel` 的核心语义是"取消任务的执行权"，而不是"杀死线程"**。`mayInterruptIfRunning` 参数控制的是"取消时要不要顺带发一个中断信号"，这两件事是分开的。

### 先看 `cancel` 到底做了什么

以 `FutureTask` 的实现为例，`cancel(true)` 和 `cancel(false)` 的共同点远大于差异：

java

```java
public boolean cancel(boolean mayInterruptIfRunning) {
    // 1. 状态机 CAS：NEW -> CANCELLED（false）或 NEW -> INTERRUPTING（true）
    if (!(state == NEW &&
          UNSAFE.compareAndSwapInt(this, stateOffset, NEW,
              mayInterruptIfRunning ? INTERRUPTING : CANCELLED)))
        return false;   // 已经完成/已取消，取消失败

    try {
        if (mayInterruptIfRunning) {
            try {
                Thread t = runner;
                if (t != null)
                    t.interrupt();   // 2. 唯一区别：是否发中断
            } finally {
                state = INTERRUPTED;
            }
        }
    } finally {
        runner = null;
        state = COMPLETING;  // 收尾
    }
    return true;
}
```



两者**共同**的效果：

1. **任务还没开始跑** → 状态直接置为 CANCELLED，之后 `run()` 里 `if (state != NEW) return;` 直接跳过，任务**永远不会执行**。
2. **任务正在跑，但阻塞在 `get()` 上** → 所有 `get()` 调用者立刻抛 `CancellationException`，不用等到任务跑完。
3. **`isCancelled()` 返回 true**，`isDone()` 返回 true。
4. 如果任务已经完成或已经取消，`cancel` 返回 `false`。

**唯一区别**：==`true` 会对执行线程 `interrupt()`，`false` 不会。==

### 那 `cancel(false)` 的意义在哪？

#### 1. 让等待者立刻解脱，而不是等任务跑完

想象一个任务要跑 10 分钟，另一个线程阻塞在 `future.get()` 上。`cancel(false)` 让这个 `get()` 立刻抛 `CancellationException` 返回。如果不用 `cancel` 而只是"不管它"，`get()` 还得傻等 10 分钟。这是 `cancel(false)` 最实用的场景。

#### 2. 任务还没启动时，阻止它启动

线程池里任务在队列里排队，`cancel(false)` 能让它被丢弃（前提是没被 worker 取走）。这是纯粹的"取消执行权"。

## 16. CyclicBarrier

```java
package com.kuang.add;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

public class CyclicBarrierDemo {
    public static void main(String[] args) {
        /**
         * 集齐7颗龙珠召唤神龙
         */
        // 召唤龙珠的线程
        CyclicBarrier cyclicBarrier = new CyclicBarrier(8,()->{
            System.out.println("召唤神龙成功！");
        });

        for (int i = 1; i <=7 ; i++) {
            final int temp = i;
            // lambda能操作到 i 吗
            new Thread(()->{
                System.out.println(Thread.currentThread().getName()+"收集"+temp+"个龙珠");
                try {
                    cyclicBarrier.await(); // 等待
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (BrokenBarrierException e) {
                    e.printStackTrace();
                }
            }).start();
        }

    }
}
```

> 这里的 new Thread() 里面的 Runnable 可以直接拿到外面的 i 吗？直接拿拿不到，因为这里本质是里面放了一个类。可为啥使用 final 修饰一个中间变量就可以拿到了？

### 第一部分：为什么外面的 `i` 拿不到（针对局部变量）

#### 1. 先看要捕获的到底是什么

你的代码：

```java
for (int i = 1; i <= 7; i++) {
    new Thread(() -> {
        System.out.println(... + i + ...);   // 试图引用外面的 i
    }).start();
}
```

这里 Lambda 想用的 `i`，是 `main` 方法**栈帧里的一个局部变量**。要理解为什么拿不到，先搞清局部变量住在哪。

#### 2. 局部变量住在「栈」上，而栈是随方法结束就销毁的

JVM 的内存模型里，方法调用会创建一个**栈帧（Stack Frame）**，局部变量就存在栈帧的「局部变量表」里：

```text
main 线程的栈
┌─────────────────────────┐
│ main 栈帧                │
│  ├─ i = 1  ← 局部变量     │  ← 方法执行期间存在
│  └─ ...                  │
└─────────────────────────┘
        ↓ main 方法结束
┌─────────────────────────┐
│ (栈帧弹出，i 随之消失)     │  ← 方法结束后彻底不存在
└─────────────────────────┘
```

栈帧的特点是：**方法一结束就被弹出，里面的局部变量随之消失。** 这是局部变量和对象的本质区别——对象存在**堆**上，由 GC 管理，生命周期不跟方法绑定。

#### 3. 问题就出在「生命周期对不上」

Lambda 里那个 `Runnable` 是要交给**另一个线程**去跑的：

```java
new Thread(() -> { ... 使用 i ... }).start();
//            ↑ 这个 Runnable 可能在 main 结束很久之后才真正执行
```

时序可能是这样：

```text
main 线程:  创建 Lambda → 启动 Thread → main 方法结束，栈帧弹出，i 消失
                              ↓
子线程:                      ...稍后才开始执行 Lambda，想读 i
                            ✗ i 已经不在了
```

于是矛盾出现了：

> **`i` 的生命周期跟着 `main` 的栈帧走，而 Lambda 的执行时机在另一个线程、可能晚得多。** 当 Lambda 想读 `i` 的时候，`i` 所在的栈帧可能早就不存在了。

这就是「拿不到」的根本原因——不是访问权限的问题，而是**要读的东西可能已经随栈帧一起被销毁了**。

### 第二部分：为什么加了 `final` 就能拿到？

首先要明白一个机制：**当内部类要访问外部局部变量时**，为了实现内部类能访问外部局部变量，**编译器默认把该局部变量的值复制一份，作为内部类对象的一个字段存进去，访问时读的是这个副本，而不是原来的栈变量。**

**但这里就有问题了。** 这会产生**语义不一致**：内部类看到的 `x` 和外部方法的 `x` 可能不一样，而且两边各自修改互不影响，非常容易写出 bug。

Java 的设计者为了**避免这种歧义**，干脆规定：**能被内部类访问的外部局部变量，必须是 `final` 的**——即赋值后不再改变。这样两份数据永远一致，不会出现“改了一边、另一边没变”的困惑。

> 一句话：**因为内部类拿到的是副本，为了保证副本和原始变量永远相同，就强制原始变量不能改。**

#### Java 8 的放宽：effectively final

Java 8 之后不再强制写 `final`，但要求变量是 **effectively final**（事实上的 final）——即：

> “事实上的不可变”在 Java 语法里的定义是：变量声明并初始化后，从头到尾绝对没有被重新赋值过。

```java
int x = 10;
Runnable r = () -> System.out.println(x); // ✅ x 没再改，合法
```

```java
int x = 10;
x = 20;                                    // 改了
Runnable r = () -> System.out.println(x);  // ❌ 编译错误
```

编译器会自动判断“你有没有改过”。**本质约束没变，只是省了你手写 `final`。** 所以 Java 8 不是取消了限制，而是把“显式 final”放宽成了“事实上 final”。

**反例：**第一次赋值了`null`,第二次赋值了`LazySingle.getInstance()`，结果还是报错

~~~java
package com.kuang.single.my;

import java.util.concurrent.ThreadPoolExecutor;

public class test {

    public static void main(String[] args) {
        LazySingle instance2=null;
        LazySingle instance3 = null;
        new Thread(() -> {
            instance2 = LazySingle.getInstance();
        }).start();
        new Thread(() -> {
            instance3 = LazySingle.getInstance();
        }).start();
        System.out.println(instance2 == instance3);
    }
}

~~~



> 为什么这里没有要求事实不可变呢？也不报错。

> 注：这段本身也有并发问题（可见性导致的）。

![image-20261011170517594](https://natureshiyi-image.oss-cn-hangzhou.aliyuncs.com/img/image-20261011170517594.png)

### 关键区别：`num` 是成员变量，不是局部变量

#### 那条“不可变”规则到底是什么

Java 规定：

> 匿名内部类 / lambda 如果访问**局部变量**（方法内定义的变量），这个变量必须是 **final** 或 **effectively final**（赋值后没再改过）。

#### 为什么局部变量有这个限制？

因为 lambda / 匿名内部类可能在新线程、稍后执行，而**局部变量存在于栈上**，方法结束后就销毁了。如果允许修改，lambda 执行时看到的值可能和捕获时不一致，语义会很混乱。

所以 Java 干脆规定：局部变量捕获后**不能改**——lambda 拿到的是一个**快照副本**。

#### 为什么成员变量没有这个限制？

成员变量（包括 `static` 字段）**不存在栈上**，而是存在**堆里的对象中**（静态字段存在方法区 / 类对象里），生命周期和对象 / 类一样长。

- lambda 访问成员变量时，不是“捕获副本”，而是**直接通过 `this`（或类引用）去访问那个字段**。
- 成员变量**不捕获副本，而是每次去读字段**，所以**允许**被改、**有机会**读到新值；但**能不能真读到最新值，取决于可见性**，没有 `volatile` 照样可能读到旧值。

这就是为什么你截图里 `num = 1` 不报错——`num` 是成员变量，不受“局部变量必须 effectively final”那条规则约束。

## 18. ReadWriteLock

> 读写锁中，读锁的意义是什么呢？我就算不加读锁，多线程不也是并发的读吗？

类比 MySQL 中表锁的**表共享读锁、表独占写锁**，行锁中的**共享锁、排他锁**。

一句话：**读锁的意义不是“让读变并发”，而是“阻止写”。**

不加读锁确实能并发读，但你挡不住写。举例：

```java
// 不加读锁
Thread A: if (map.get(key) != null) { 用这个值 }
Thread B: 同时 map.remove(key)
```

A 判断不为 null 之后、用之前，B 把它删了——A 就用了一个已经失效的数据。加读锁后，B 必须等 A 读完才能写，这个窗口被堵住。

所以读锁的作用是**给读操作一个“与写互斥”的身份**：

- 读锁 vs 读锁 → 共享，仍并发（这是你观察到的现象，没错）；
- 读锁 vs 写锁 → 互斥（这才是加读锁的收益，不加就得不到）。

对照 `ReadWriteLockDemo` 的语义就是：**读锁不解决读的并发问题，它解决的是“读的时候不许写”**。如果一段数据只读不写，确实可以完全不用锁——代价是你同时也放弃了“将来能安全加写”的可能。

判断标准很简单：**只要有写线程存在，且读线程是“读了还要用”（如判空后再取值、遍历后做计算），就必须加读锁；纯粹无状态的一次性读取（如 `get` 完就打印），不加锁也无害。**

## 24. CPU 密集型和 IO 密集型

> 开发中如何具体设置 CPU 密集型和 IO 密集型的参数？如何知道是 CPU 密集还是 IO 密集？

**判断依据**：看任务在不在等外部资源。只做计算、不调 IO 的是 CPU 密集型；有数据库、RPC、文件、网络调用的，是 IO 密集型。混合的就拆开分池。

**参数设置**：

- CPU 密集型：**核数 + 1**。线程再多也只是增加上下文切换，没有收益。多出的 1 个用来顶替偶发的 GC、页缺失造成的空档。
- IO 密集型：**2 × 核数**。因为线程大部分时间在等 IO，CPU 是空闲的，所以要用更多线程把 CPU 喂饱。等待占比越高，线程数就该越大。

**更精确的算法**：用 `ThreadMXBean` 分别测任务的 CPU 时间和挂钟时间，算出等待 / 计算比值，代进公式 `N = 核数 × (1 + W/C)`，就能算出该配多少线程。

这几个都只是初始值，最终要靠压测调，并且要看队列积压和 CPU 利用率；另外线程池要和下游容量对齐，DB 连接池只有 20 个，线程池开 200 也只会排队等连接。

## 27. Stream 流式计算

> 为什么这里掉个头就能表示升序或降序呢？按理说它返回的不就只是正负数和 0 吗？

![image-20261011111110605](https://natureshiyi-image.oss-cn-hangzhou.aliyuncs.com/img/image-20261011111110605.png)

**核心矛盾点在于：你误以为 `sorted` 内部会“看情况”决定升序降序，其实它只认一条死规则。**

### 先看 `sorted` 内部干了什么

`sorted(comparator)` 这个流操作，最终把元素交给排序算法（TimSort）。而排序算法有一条**写死、绝不变更**的规则：

> **`compare(a, b) < 0` → a 排前面；`compare(a, b) > 0` → a 排后面。**

它**永远**把“被判定为小”的那个放前面，从不反过来。它根本不关心你写的是升序还是降序，它只执行这一条。

### 再看你代码里的 lambda

```java
sorted((uu1, uu2) -> { return uu2.compareTo(uu1); })
```

这个 lambda 就是 `Comparator.compare(uu1, uu2)` 的实现。注意参数顺序：**第一个参数是 `uu1`，第二个是 `uu2`**。

它返回的是 `uu2.compareTo(uu1)`，也就是**拿第二个和第一个比**。

### 为什么“掉个头”就变成降序？

我们代入具体数字，走一遍排序算法的判断逻辑。

假设流里有两个 User，年龄 20 和 30。排序算法拿到它们，问你的 lambda：“这俩谁排前面？”

算法调用：`compare(20岁, 30岁)`，对应 lambda 参数 `uu1=20岁, uu2=30岁`。

计算：

```java
uu2.compareTo(uu1)  →  30岁.compareTo(20岁)
```

年龄 30 > 20，`compareTo` 返回**正数**。

排序算法看到正数，按它的死规则：**正数 → 第一个参数排后面**。

第一个参数是 20 岁，所以 20 岁排后面。

**结果：30 岁在前，20 岁在后 → 降序。**

### 对比一下不“掉头”的写法

如果是 `uu1.compareTo(uu2)`：

```java
compare(20岁, 30岁) → uu1.compareTo(uu2) → 20.compareTo(30) → 负数
```

排序算法看到负数 → **第一个参数排前面** → 20 岁排前面 → **升序**。

### 关键在于：算法规则没变，变的是“谁被判定为小”

| 写法 | 算法问“20岁 vs 30岁”时，返回 | 算法判定 | 结果 |
| :--- | :--- | :--- | :--- |
| `uu1.compareTo(uu2)` | 负数（20 < 30） | 20 算小，排前面 | 升序 |
| `uu2.compareTo(uu1)` | 正数（30 > 20） | 20 算大，排后面 | 降序 |

**排序算法始终是“小的排前面”**。你只是通过交换 `compareTo` 的调用方向，让“20岁”在这次比较中被判定成了“大”的而已。

### 类比

老师（排序算法）的规则永远是：“**被我判定为矮的站前面**”。

- 你如实报告（`uu1.compareTo(uu2)`）：20 岁说“我比他矮” → 20 站前面 → 升序。
- 你反着报告（`uu2.compareTo(uu1)`）：20 岁被报告成“我比他高” → 20 站后面 → 降序。

老师没变，规则没变。变的是你**怎么报告大小关系**。

> `sorted` 里的排序算法永远把“被判定为小”的放前面，这条规则固定不变。你写 `uu2.compareTo(uu1)`，相当于在每次比较时把两个参数的“大小身份”对调，让原本小的被判定成大，于是排到了后面——看起来就成了降序。返回的正负数本身没变含义，变的是**谁和谁比、谁当第一个参数**。

## 28. ForkJoin 详解

解析一下这段代码：

```java
long sum = LongStream.rangeClosed(0L, 10_0000_0000L).parallel().reduce(0, Long::sum);
```

这行代码是用**并行流**对 0 到 10 亿的所有整数求和。逐部分拆解。

### 整体功能

计算：

```text
0 + 1 + 2 + ... + 1_000_000_000
```

结果应该是 500000000500000000（等差数列求和公式 `n(n+1)/2`，n = 10 亿）。

### 逐段拆解

#### ① `LongStream.rangeClosed(0L, 10_0000_0000L)`

```java
LongStream.rangeClosed(0L, 10_0000_0000L)
```

- `LongStream` 是专门处理 `long` 的流，避免装箱拆箱。
- `rangeClosed(start, end)` 生成从 `start` 到 `end` 的**闭区间**，包含两端。
- 所以这里生成 `0, 1, 2, ..., 1_000_000_000`，共 **10 亿 + 1** 个元素。
- 注意：`10_0000_0000L` 里的下划线是 Java 7 引入的数字分隔符，纯粹为了可读性，等价于 `1000000000L`。
  - 这里的分组有点怪：`10_0000_0000` = 10 亿。更常见的写法是 `1_000_000_000L`，两者数值一样。

#### ② `.parallel()`

```java
.parallel()
```

- 把顺序流转成**并行流**。
- 底层用 `ForkJoinPool`（默认是 `ForkJoinPool.commonPool()`），把任务拆成多个子任务，分给多个 CPU 核心并行计算。
- 目的：利用多核加速。

#### ③ `.reduce(0, Long::sum)`

```java
.reduce(0, Long::sum)
```

`reduce` 是**归约**操作，把流里的所有元素合并成一个值。这里用的是两参数版本：

```java
T reduce(T identity, BinaryOperator<T> accumulator)
```

- **`identity`（初始值）= `0`**：每个并行子任务都以 0 为起点开始累加。
- **`accumulator`（累加器）= `Long::sum`**：把两个值相加。

`Long::sum` 等价于 `(a, b) -> a + b`。

所以整个 reduce 的意思是：从 0 开始，不断把元素累加进去，最终得到总和。

### 并行 reduce 的工作原理

`parallel().reduce(0, Long::sum)` 不是简单顺序累加，而是：

1. 把 0~10 亿拆成若干段（比如 0~1亿、1亿~2亿……）。
2. 每个线程独立算自己那段的局部和，起点都是 `identity`（0）。
3. 最后把各段局部和再用 `Long::sum` 合并，得到总和。

示意：

```text
线程1: 0 + 1 + ... + 1亿       → 局部和 S1
线程2: 0 + 1亿+1 + ... + 2亿   → 局部和 S2
...
主线程: 0 + S1 + S2 + ...      → 总和
```

> 不太理解这个单位元到底是啥？

### 单位元

“单位元”这个词听起来抽象，其实特别简单。**它就是“一个和任何东西运算，都不改变那个东西”的特殊值。**

#### 一句话定义

对某个运算 `⊕`，如果存在一个值 `e`，使得对**任何** `x` 都满足：

```text
e ⊕ x = x
x ⊕ e = x
```

那么 `e` 就是这个运算的**单位元**（identity element）。

关键词：**加了等于没加，乘了等于没乘，拼了等于没拼。**

#### 加法里的单位元是 0

```text
0 + 5 = 5
0 + 100 = 100
0 + (-3) = -3
0 + 0 = 0
```

任何数加 0 都还是它自己。所以 **0 是加法的单位元**。

#### 乘法里的单位元是 1

```text
1 × 5 = 5
1 × 100 = 100
1 × (-3) = -3
```

任何数乘 1 都还是它自己。所以 **1 是乘法的单位元**。

#### 字符串拼接里的单位元是空字符串 ""

```text
"" + "abc" = "abc"
"abc" + "" = "abc"
```

空串拼上去等于没拼。所以 **"" 是字符串拼接的单位元**。

#### 为什么 reduce 需要单位元？

回到你的代码：

```java
.reduce(0, Long::sum)
```

`reduce` 的语义是：

```text
结果 = identity ⊕ 元素1 ⊕ 元素2 ⊕ ... ⊕ 元素n
```

它把 `identity` 作为**累加的起点**。为了让这个起点“不污染结果”，`identity` 必须是单位元。

#### 并行执行时

每个子任务都从 `identity` 开始：

```text
子任务1: 0 + (1+2+3)      = 6
子任务2: 0 + (4+5+6)      = 15
合并:    0 + 6 + 15       = 21
```

每个子任务开头的 0 都不影响它那段的真实和，因为 0 是单位元。

#### 如果单位元选错了会怎样？

假设写成 `reduce(1, Long::sum)`：

```text
子任务1: 1 + (1+2+3)  = 7   ← 多了一个 1
子任务2: 1 + (4+5+6)  = 16  ← 多了一个 1
合并:    1 + 7 + 16   = 24  ← 又多了 1
正确结果应该是 21，现在变成 24
```

因为 1 **不是**加法的单位元（`1 + 5 ≠ 5`），每段都被多加了一次，结果就错了。

#### 总结表

| 运算 | 单位元 | 因为 |
| :--- | :--- | :--- |
| 加法 `+` | `0` | `0 + x = x` |
| 乘法 `×` | `1` | `1 × x = x` |
| 字符串拼接 | `""` | `"" + x = x` |
| 集合并集 | `∅` | `∅ ∪ x = x` |
| 逻辑与 `&&` | `true` | `true && x = x` |
| 逻辑或 `\|\|` | `false` | `false \|\| x = x` |
| 求最大值 | `-∞`（或类型最小值） | `max(-∞, x) = x` |

#### 一句话总结

> 单位元就是“和任何东西运算都不改变它”的那个特殊值。加法的单位元是 0，乘法的单位元是 1，字符串拼接的单位元是空串。`reduce` 要求你提供单位元作为累加起点，这样无论并行拆成多少段，每段从它开始都不会污染结果。所以 `reduce(0, Long::sum)` 里的 0 不能随便换，它必须是加法的单位元。

## 33. 彻底玩转单例模式

### 饿汉式

> 这个饿汉式单例，我不用它，它不也没有申请对象吗？为啥说他浪费空间呢？

```java
package com.kuang.single;

// 饿汉式单例
public class Hungry {

    // 可能会浪费空间
    private byte[] data1 = new byte[1024*1024];
    private byte[] data2 = new byte[1024*1024];
    private byte[] data3 = new byte[1024*1024];
    private byte[] data4 = new byte[1024*1024];

    private Hungry(){

    }

    private final static Hungry HUNGRY = new Hungry();

    public static Hungry getInstance(){
        return HUNGRY;
    }

}
```

因为你默认了**“类加载”和“使用类”是同时发生的**。但实际上，**只要类被加载了，即使你一行代码都没用过它，静态变量也会被初始化。**

我们来拆解一下这里的核心逻辑：

#### 为什么“不用它”也会申请对象？

在 Java 中，**静态变量（`static`）是在类加载（Class Loading）阶段就被初始化的**，而不是在第一次调用 `getInstance()` 时。

只要你触发了这个类的加载（比如：

- `Class.forName("Hungry")`
- 通过反射调用它
- 或者只是简单地在别的地方引用了 `Hungry` 这个类名
- 甚至某些 JVM 在启动时扫描类路径就会加载），

`private static final Hungry HUNGRY = new Hungry();` 这行代码就会**立刻执行**。

执行这行代码时，JVM 会：

1. 分配内存给 `new Hungry()`。
2. 执行 `Hungry` 的构造函数。
3. 把引用赋值给 `HUNGRY`。

**重点在于：** 这个 `new Hungry()` 是在**类初始化**时发生的，而不是在**调用 `getInstance()`** 时发生的。所以，哪怕你只是加载了类，还没开始用它，`Hungry` 对象就已经在堆内存里建好了。

### 静态内部类

关于静态内部类的底层疑问以及逻辑梳理详看：https://chat.deepseek.com/share/qsbej2rbnqicmgt4b6

## 35. 原子引用解决 ABA 问题

> 在测试原子类 AtomicInteger 时，测试结果为 true 符合预期，但测试原子引用 AtomicStampedReference 时，使用 Integer，测试结果为 false，按理说应该是 true 才对。

1、AtomicInteger 中 compareAndSet（cas）比较实际用的是基本类型

```java
AtomicInteger atomicInteger = new AtomicInteger(2020);
System.out.println(atomicInteger.compareAndSet(2020,2023));
```

```java
public final boolean compareAndSet(int expectedValue, int newValue) {
    return U.compareAndSetInt(this, VALUE, expectedValue, newValue);
}
```

2、AtomicStampedReference 中 compareAndSet（cas）比较的是泛型。首先定义的是泛型，不会有基本类型。比如定义了泛型是 Integer，那么实际使用的也是 Integer。**这就会有经典的包装类范围问题，如果 Integer 超过了 -128~127，就会分配新的内存空间，不会复用，导致虽然都是 2020，但实际在两个不同的内存空间。**

```java
AtomicStampedReference<Integer> atomicStampedReference = new AtomicStampedReference<>(2020,1);
System.out.println(atomicStampedReference.compareAndSet(2020, 2021,
        atomicStampedReference.getStamp(), atomicStampedReference.getStamp() + 1));
```

```java
public boolean compareAndSet(V   expectedReference,
                                 V   newReference,
                                 int expectedStamp,
                                 int newStamp) {
        Pair<V> current = pair;
        return
            expectedReference == current.reference &&
            expectedStamp == current.stamp &&
            ((newReference == current.reference &&
              newStamp == current.stamp) ||
             casPair(current, Pair.of(newReference, newStamp)));
    }
```

> 原子类不也会出现 ABA 吗？正是因为有 ABA 问题才会出现原子引用来解决吧？

### 1. 原子类也会出现 ABA 吗？

**会，但通常不影响结果。**

ABA 问题的本质是：一个值从 A 变成 B，又变回 A，CAS 检查时发现“还是 A”，于是认为没有变化，但实际上中间已经发生过变化。

对于 `AtomicInteger` / `AtomicLong` 这类**数值型原子类**：

- 如果你只是做 `incrementAndGet`、`addAndGet` 这种**纯累加 / 计数**操作，ABA 几乎不会造成问题。因为计数关心的是“最终值对不对”，而不是“中间有没有被改过又改回来”。
- 但如果你用 `compareAndSet` 做**有业务语义的状态判断**，比如“只有当前是 0 才能改成 1”，那么 ABA 确实可能出问题：线程 A 读到 0，线程 B 把 0→1→0，线程 A 再 CAS(0,1) 会成功，但它以为没人动过。

所以准确地说：**ABA 不是原子引用特有的，原子类同样会有。** 只是原子类最常见的用法（计数）对 ABA 不敏感，所以大家很少讨论它。

### 2. 原子引用是为了解决 ABA 才出现的吗？

**不是。** 这是最关键的一点。

`AtomicReference` 出现的原因是：**CAS 只能作用于一个内存位置，而 `AtomicInteger` 只能操作 int 值，无法原子地替换一个对象引用。** 当你想用 CAS 去更新一个对象、一个节点、一个状态对象时，就需要 `AtomicReference`。

也就是说：

- `AtomicInteger` → CAS 操作 **int 值**
- `AtomicReference` → CAS 操作 **对象引用**

它们解决的是**“能 CAS 什么类型”**的问题，而不是“要不要解决 ABA”的问题。

`AtomicReference` 本身**并不会自动解决 ABA**。如果你用 `AtomicReference` 存一个可变对象，照样会有 ABA。它只是让你能对引用做 CAS 而已。

### 3. 真正解决 ABA 的是谁？

真正用来解决 ABA 的是 **`AtomicStampedReference` 和 `AtomicMarkableReference`**。

- `AtomicStampedReference`：给引用配一个**版本号（stamp）**，每次修改版本号 +1。CAS 时同时比较引用和版本号，A→B→A 会因为版本号不同而失败。
- `AtomicMarkableReference`：配一个 **boolean 标记**，适合只关心“有没有被改过”的场景。

它们才是专门为 ABA 设计的。而 `AtomicReference` 只是“能 CAS 引用”的基础设施。

# JUC知识点补充

## 1.ThreadLocal

### ThreadLocal作用

通常情况下，我们创建的变量可以被任何一个线程访问和修改。这在多线程环境中可能导致数据竞争和线程安全问题。那么，**如果想让每个线程都有自己的专属本地变量，该如何实现呢？**

JDK 中提供的 `ThreadLocal` 类正是为了解决这个问题。**`ThreadLocal` 类允许每个线程绑定自己的值**，可以将其形象地比喻为一个“存放数据的盒子”。每个线程都有自己独立的盒子，用于存储私有数据，确保不同线程之间的数据互不干扰。

当你创建一个 `ThreadLocal` 变量时，每个访问该变量的线程都会拥有一个独立的副本。这也是 `ThreadLocal` 名称的由来。线程可以通过 `get()` 方法获取自己线程的本地副本，或通过 `set()` 方法修改该副本的值，从而避免了线程安全问题。

举个简单的例子：假设有两个人去宝屋收集宝物。如果他们共用一个袋子，必然会产生争执；但如果每个人都有一个独立的袋子，就不会有这个问题。如果将这两个人比作线程，那么 `ThreadLocal` 就是用来避免这两个线程竞争同一个资源的方法。

```java
public class ThreadLocalExample {
    private static ThreadLocal<Integer> threadLocal = ThreadLocal.withInitial(() -> 0);

    public static void main(String[] args) {
        Runnable task = () -> {
            int value = threadLocal.get();
            value += 1;
            threadLocal.set(value);
            System.out.println(Thread.currentThread().getName() + " Value: " + threadLocal.get());
        };

        Thread thread1 = new Thread(task, "Thread-1");
        Thread thread2 = new Thread(task, "Thread-2");

        thread1.start(); // 输出: Thread-1 Value: 1
        thread2.start(); // 输出: Thread-2 Value: 1
    }
}
```

### ThreadLocal内存泄漏问题

==关键点==：`ThreadLocalMap` 中的**key是弱引用，value是强引用**。如果**Thread实例**失去强引用后，弱引用会被回收，而value这个强引用不会回收，导致别的Thread读到脏值。



每个线程维护一个名为 `ThreadLocalMap` 的 map。 当你使用 `ThreadLocal` 存储值时，实际上是将值存储在当前线程的 `ThreadLocalMap` 中，其中 `ThreadLocal` 实例本身作为 key，而你要存储的值作为 value。


`ThreadLocalMap` 的 `key` 和 `value` 引用机制：

- **key 是弱引用**：`ThreadLocalMap` 中的 key 是 `ThreadLocal` 的弱引用 (`WeakReference<ThreadLocal<?>>`)。 这意味着，如果 `ThreadLocal` 实例不再被任何强引用指向，垃圾回收器会在下次 GC 时回收该实例，导致 `ThreadLocalMap` 中对应的 key 变为 `null`。
- **value 是强引用**：即使 `key` 被 GC 回收，`value` 仍然被 `ThreadLocalMap.Entry` 强引用存在，无法被 GC 回收。

当 `ThreadLocal` 实例失去强引用后，其对应的 value 仍然存在于 `ThreadLocalMap` 中，因为 `Entry` 对象强引用了它。如果线程持续存活（例如线程池中的线程），`ThreadLocalMap` 也会一直存在，导致 key 为 `null` 的 entry 无法被垃圾回收，即会造成内存泄漏。

也就是说，内存泄漏的发生需要同时满足==两个条件==：

1. `ThreadLocal` 实例不再被强引用；
2. 线程持续存活，导致 `ThreadLocalMap` 长期存在。

虽然 `ThreadLocalMap` 在 `get()`, `set()` 和 `remove()` 操作时会尝试清理 key 为 null 的 entry，但这种清理机制是被动的，并不完全可靠。

**如何避免内存泄漏的发生？**

1. 在使用完 `ThreadLocal` 后，务必调用 `remove()` 方法。 这是最安全和最推荐的做法。 `remove()` 方法会从 `ThreadLocalMap` 中显式地移除对应的 entry，彻底解决内存泄漏的风险。 即使将 `ThreadLocal` 定义为 `static final`，也强烈建议在每次使用后调用 `remove()`。
2. 在线程池等线程复用的场景下，使用 `try-finally` 块可以确保即使发生异常，`remove()` 方法也一定会被执行。

#### [为什么 Entry 的 key 要设计为弱引用？](https://javaguide.cn/java/concurrent/java-concurrent-questions-03.html#为什么-entry-的-key-要设计为弱引用)

这是一个经典的面试追问。很多同学知道 `ThreadLocalMap` 的 key 是弱引用，但不清楚**为什么要这样设计**，以及如果换成强引用会怎样。

我们先来看完整的引用链路。当一个线程使用 `ThreadLocal` 时，涉及以下引用关系：

Thread可用通过`Thread.currentThread()`获取到`ThreadLocalMap`的`key`，再通过`getMap(Thread t)` 可以访问到该线程的 `ThreadLocalMap` 对象。

```
强引用（栈/静态变量）──→ ThreadLocal 实例
                              ↑
Thread ──→ ThreadLocalMap ──→ Entry ─── key（WeakReference）──┘
                              │
                              └─── value（强引用）──→ 实际存储的对象
```

理解了这条引用链路，我们来对比两种设计方案：

**假设 key 使用强引用（实际没有采用）：**

当业务代码中的 `ThreadLocal` 引用被置为 `null`（例如方法执行结束、对象被回收），此时虽然业务代码已经不再需要这个 `ThreadLocal`，但由于 `ThreadLocalMap` 的 Entry 对 key 持有**强引用**，`ThreadLocal` 实例仍然无法被 GC 回收。只要线程不终止，这个 `ThreadLocal` 和它对应的 value 都会一直存在于内存中，造成 key 和 value **都无法回收**的内存泄漏。

**key 使用弱引用（实际采用的方案）：**

当业务代码中的 `ThreadLocal` 引用被置为 `null` 后，由于 Entry 的 key 是弱引用，`ThreadLocal` 实例在下次 GC 时会被回收，key 变为 `null`。此时虽然 value 仍然存在（强引用），但 `ThreadLocalMap` 在执行 `get()`、`set()`、`remove()` 等操作时，会主动探测并清理这些 key 为 `null` 的 "stale entry"（过期条目），从而释放 value 对象。

也就是说，**弱引用的设计是一种“兜底”防御机制**——即便开发者忘记调用 `remove()`，JVM 的 GC 配合 `ThreadLocalMap` 的自清理逻辑，仍然有机会回收泄漏的数据。而如果使用强引用，一旦忘记 `remove()`，就完全没有任何补救机会了。

> 需要注意的是，这种自清理机制是**被动触发**的（只在 `get`/`set`/`remove` 操作时顺便清理），并不能保证所有过期条目都被及时清理。因此，**弱引用只是降低了内存泄漏的风险，并没有彻底消除它**，手动调用 `remove()` 仍然是必须的。

> Thread是不是同时被强引用和弱引用了？

#### ThreadLocal结构框架

```text
                  ┌─────────────────────┐
   TL (static 强引用) │  ThreadLocal 实例    │
        ────────────→│  (key)              │
                    └─────────────────────┘
                              ↑
                              │ 弱引用
                    ┌─────────────────────┐
                    │  Entry              │
                    │  value ─────────────┼──强引用──→ value 对象
                    └─────────────────────┘
```

- **ThreadLocal 实例**：被 `TL`（强）+ `Entry`（弱）同时指着 → **你问的"同时被强弱引用"就是这个**
- **value 对象**：只被 `Entry.value` 强引用

#### 是不是同时被弱引用和强引用？

要看你说的是哪个对象。

**1. ThreadLocal 实例（key）**

它通常被**两层引用**同时指着：

java

```java
private static final ThreadLocal<User> TL = new ThreadLocal<>();
//      ↑ 强引用                              ↑ 这个实例
```

- `TL` 这个 static 字段 → **强引用** ThreadLocal 实例
- `Entry`（弱引用） → **弱引用**同一个 ThreadLocal 实例

所以 **ThreadLocal 实例确实同时被强引用（TL 变量）和弱引用（Entry）指着**。这就是你说的那种情况。

**只要 `TL` 这个强引用还在，ThreadLocal 实例就不会被回收**，即使 GC 发生，弱引用也不生效（因为还有强引用兜着）。

### ThreadLocal异步线程（跨线程使用）

![image-20261014111207692](C:\Users\沨烨\AppData\Roaming\Typora\typora-user-images\image-20261014111207692.png)

## 2、AQS

### AQS是什么？

![image-20261014165558965](C:\Users\沨烨\AppData\Roaming\Typora\typora-user-images\image-20261014165558965.png)

### AQS的工作流程

通过对State变量进行**cas**操作，成功了加锁，失败了放入双向队列等待加锁。

![image-20261014165906996](C:\Users\沨烨\AppData\Roaming\Typora\typora-user-images\image-20261014165906996.png)

**tip:**公平锁、非公平锁的实现思路就是就是取决于双向队列以什么规则来放出等待的线程，排队释放就是公平锁，**新来的线程不需要排队，可以直接去 CAS 抢锁，抢不到再去排队就是非公平锁。**

```text
公平锁：
  新线程来 → 先检查队列有没有人在等
            有 → 乖乖排队，不抢
            没有 → 才去抢

非公平锁：
  新线程来 → 直接 CAS 抢，不管队列有没有人
            抢到 → 拿走
            抢不到 → 再排队
```

### 利用AQS实现自定义锁

![image-20261014170719442](C:\Users\沨烨\AppData\Roaming\Typora\typora-user-images\image-20261014170719442.png)

### 手写简单独占锁

~~~java
public class SimpleLock {
    private static class Sync extends AbstractQueuedSynchronizer {
        @Override
        protected boolean tryAcquire(int acquires) {
            if (compareAndSetState(0, 1)) {
                setExclusiveOwnerThread(Thread.currentThread());
                return true;
            }
            return false;
        }

        @Override
        protected boolean tryRelease(int releases) {
            if (getState() == 0) throw new IllegalMonitorStateException();
            setExclusiveOwnerThread(null);
            setState(0);
            return true;
        }

        @Override
        protected boolean isHeldExclusively() {
            return getState() == 1;
        }
    }

    private final Sync sync = new Sync();

    public void lock() {
        sync.acquire(1);
    }

    public void unlock() {
        sync.release(1);
    }
}

~~~

## 3、CurrentHashMap

### 三者对比

| 特性      | HashMap | Hashtable         | ConcurrentHashMap |
| --------- | ------- | ----------------- | ----------------- |
| 线程安全  | 否      | 是                | 是                |
| 锁机制    | 无      | 全局 synchronized | CAS + 桶级锁      |
| null 键值 | 允许    | 不允许            | 不允许            |
| 性能      | 最高    | 最低              | 高并发下最优      |
| 推荐场景  | 单线程  | 不推荐使用        | 多线程            |

### HashMap 多线程会出什么问题？

回答：最典型的就是 JDK 1.7 的死循环问题。扩容时如果两个线程同时触发 rehash，链表迁移可能形成环，后续 get 操作会无限循环，CPU 直接打满。JDK 1.8 改成尾插法解决了成环问题，但多线程同时 put 还是会丢数据，两个线程算到同一个桶位置，后写的直接覆盖先写的。

### CurrentHashMap 1.8实现过程

### 1.8 细粒度锁实现细节

1.8 做了更细粒度的锁控制，可以理解为数组的每个位置都是一把锁，扩容了锁也会变多，并发度也会增加。

![20220219203455.png](https://pic.code-nav.cn/mianshiya/question_picture/1783397053004488705/20220219203455_mianshiya.webp)

思想转变就是把粒度更加细化。不分段了，直接把 Node 数组的每个节点分别上一把锁，这样并发度不就更高了吗？

1.8 也不借助 ReentrantLock 了，直接用 synchronized，这也侧面证明都到 1.8 了，synchronized 优化后的速度已经不下于 ReentrantLock。

**具体实现思路**：当塞入一个值的时候，先计算 key 的 hash 后的下标，如果**计算到的下标还未有 Node，就通过 CAS 塞入新的 Node**。**如果已经有 Node 则通过 synchronized 将这个 Node 上锁**，这样别的线程就无法访问这个 Node 及其之后的所有节点。然后判断 key 是否相等，相等则是替换 value，反之则是新增一个 Node，这个操作和 HashMap 一样。



> 对于这个过程**”如果已经有 Node 则通过 synchronized 将这个 Node 上锁“**。有 node 时为啥不也 CAS 呢？

**因为在发生哈希冲突、形成链表或红黑树后，要做的事情太复杂了，CAS 这种“轻量级、只比较一次、只能改一个值”的原子操作，根本无力应对。**

具体举例： 结构变更太复杂，CAS 做不到

- **空桶时（无 Node）**：你要做的事很简单，就是把数组的这个位置从 `null` 变成 `new Node(key, value)`。这其实就是**把一个引用指向新对象**。CAS 刚好就是干这个的（通过硬件指令 `cmpxchg` 比较并交换）。
- **有 Node 时（发生冲突）**：你要做的事情包括：
  - 如果当前节点是链表：你需要沿着链表遍历，找到相同的 key 就替换 value；如果没找到，就要把新节点挂到链表的**末尾**（1.8 是尾插法）。这涉及到了指针的修改和链表的遍历。
  - 如果当前节点是红黑树：你需要执行红黑树的插入和自平衡（左旋、右旋、变色），这涉及到非常多的指针操作。
  - 如果链表长度达到了阈值（8）：你还要把链表**转成红黑树**，或者触发了扩容，还要协助**扩容（helpTransfer）**。

**CAS 的基本要求是“无锁但只做简单修改”（比如修改一个 `int` 或一个 `Node` 的引用）**。如果要处理复杂的链表遍历、红黑树旋转，用 CAS 根本没法保证原子性，总不能遍历一次链表就加一堆 CAS 校验吧？那样代码极其复杂且容易出错。

## 4、Future

### 线程中断

### Future基础应用



### CompletableFuture

#### 为什么需要 CompletableFuture？

在 Java 8 之前，我们用 Future 处理异步任务，但它有明显缺陷：

- 无法直接对多个 Future 进行链式组合
- 不能优雅地处理异常
- 缺乏回调机制，需要阻塞等待结果

CompletableFuture 实现了 Future 和 CompletionStage 接口，弥补了这些不足，让异步编程更灵活。

#### 编排任务关系

主要有串行关系和并行，我们分别来看下:

##### **串行关系**：当一个任务依赖上一个任务的结果时。

- `thenApply()`: 获取上一步结果，**处理并返回一个新结果**。这是最常用的串行方法。 (例如：查询到用户信息后，提取用户名)。参数是 V Function<T,V>，一进一出
- `thenAccept()`: 获取上一步结果，**纯消费，无返回值**。 (例如：查询到用户信息后，打印日志)。参数是void Comsumer<T>，只进不出
- `thenRun()`: **不关心上一步结果**，只要上一步执行完，就开始执行。 (例如：任务A完成后，触发一个清理任务)
- `thenCompose()`: 当下一步操作本身也是一个异步任务时使用，它可以将两层 `CompletableFuture<CompletableFuture<T>>` ==扁平化==为一层 `CompletableFuture<T>`，避免层层嵌套。

**thenCompose()应用：**

~~~java
package com.kuang.future;

import java.util.concurrent.*;
/*
* compose:将两个CompletableFuture组合成一个CompletableFuture，当两个CompletableFuture都执行完毕时，将结果进行组合
* 需求：
// 任务A 实现返回字符串"2"
// 任务B 基于任务A 的结果，转换为数字2
// 任务C 基于任务B 的结果，计算 2*10 的结果
// 关键点：任务A、B、C 都是异步任务；*/
//这个也是多任务，但这个多任务在业务逻辑上是串行的，下一个任务必须拿到上一个任务才能执行。和后面的并行多任务编排还是有区别

public class CompletableFutureUse2 {
    private static final ThreadPoolExecutor THREAD_POOL_EXECUTOR = new ThreadPoolExecutor(5,
            10,
            30L,
            TimeUnit.SECONDS,
            new LinkedBlockingDeque<>(20),
            Executors.defaultThreadFactory(),
            new ThreadPoolExecutor.AbortPolicy());

    public static CompletableFuture<String> methodA() {
        return CompletableFuture.supplyAsync(() -> "2", THREAD_POOL_EXECUTOR);
    }

    public static CompletableFuture<Integer> methodB(String str) {
        return CompletableFuture.supplyAsync(() -> {
            return Integer.parseInt(str);
        }, THREAD_POOL_EXECUTOR);
    }

    public static CompletableFuture<Integer> methodC(Integer i) {
        return CompletableFuture.supplyAsync(() -> {
            return i * 10;
        }, THREAD_POOL_EXECUTOR);
    }

    public static void main(String[] args) {
        //方式一 逻辑签到十分复杂
       methodA().thenApply(aResult->methodB(aResult))
               .thenApply(bFuture->bFuture.
                       thenApply(bResult->methodC(bResult)).
                       thenApply(cFuture->cFuture.
                               thenAccept(cResult-> System.out.println(cResult))));

       //方式二 compose，自动展开嵌套逻辑直接获取结果传入下一个。
        methodA().thenCompose(aResult->methodB(aResult)).
                thenCompose(bResult->methodC(bResult)).
                thenAccept(cResult-> System.out.println(cResult));
    }
}

~~~

##### **并行关系**：当多个任务可以同时执行时，又分为 AND 和 OR。

**AND 组合**: 等待**所有**任务都完成。

- `thenCombine()`: 组合**两个**任务，当它们都完成后，可以将两个任务的结果整合起来。
- `allOf()`: 组合**任意多个**任务，等待它们全部完成。但它的返回值是 `CompletableFuture<Void>`，需要额外处理来获取所有任务的结果。

**OR 组合**: 等待**任意一个**任务完成。

- `anyOf()`: 组合任意多个任务，只要其中一个完成了，它就会进入下一步。

多任务并行应用

~~~java
package com.kuang.future;

import java.util.Random;
import java.util.concurrent.*;

public class CompletableFuture4 {
    private static final ThreadPoolExecutor THREAD_POOL_EXECUTOR = new ThreadPoolExecutor(5,
            10,
            30L,
            TimeUnit.SECONDS,
            new LinkedBlockingDeque<>(20),
            Executors.defaultThreadFactory(),
            new ThreadPoolExecutor.AbortPolicy());
    /*All of +join || anyOf */

    public static void main(String[] args) {
        CompletableFuture<Integer> taskA = CompletableFuture.supplyAsync(() -> {
            return new Random().nextInt(100);
        }, THREAD_POOL_EXECUTOR);
        taskA.thenAccept(result -> System.out.println("任务A结果：" + result));

        CompletableFuture<Integer> taskB = CompletableFuture.supplyAsync(() -> {
            return new Random().nextInt(100);
        }, THREAD_POOL_EXECUTOR);
        taskB.thenAccept(result -> System.out.println("任务B结果：" + result));

        CompletableFuture<Integer> taskC = CompletableFuture.supplyAsync(() -> {
            return new Random().nextInt(100);
        }, THREAD_POOL_EXECUTOR);
        taskC.thenAccept(result -> System.out.println("任务C结果：" + result));
        //all of, 等待所有任务完成再执行，不直接返回结果，需要通过join手动获取后再定义自己的操作
        CompletableFuture<Void> taskD = CompletableFuture.allOf(taskA, taskB, taskC);
        taskD.thenApply(result->{
            //直接返回result,结果是null;
            //result result;
            return taskA.join() + taskB.join() + taskC.join();

        }).thenAccept(result -> System.out.println("任务D结果：" + result));

        //anyof 等待所有任务，只要有一个任务执行完，就执行下一步
        CompletableFuture<Object> taskE = CompletableFuture.anyOf(taskA, taskB, taskC);
        taskE.thenAccept(result -> System.out.println("任务E结果：" + result));
        THREAD_POOL_EXECUTOR.shutdown();
    }

}

~~~

> 用allof 和anyof时并没有指定线程池，它使用的是那个线程呢？有自定义的线程池吗？

`CompletableFuture.allOf(...)` 和 `anyOf(...)` 都是**静态方法**。
当你调用它们时，它们会在**当前调用它们的线程**（比如 main 线程）中瞬间执行完毕。

它们做的事情仅仅是：**把你传进去的那几个任务（taskA、taskB、taskC）打包成一个“依赖组”**。
它们**不负责执行**任务，只负责**监控**任务的状态（全完成？还是任意一个完成？）。
因此，`allOf` 和 `anyOf` 自身**没有线程池，也不需要线程池**。



##### 最后还需要有收尾与异常处理:

1、语法

- `whenComplete()`: 无论成功还是失败都会执行，可以获取结果或异常，但不能改变返回值。
- `exceptionally()`: 类似于 `try-catch` 中的 `catch` 块，当上游任务抛出异常时，它可以**捕获异常**并返回一个默认值，从而让流程继续下去。
- `handle()`: 无论成功还是失败都会执行，并且可以**处理结果和异常**，然后返回一个新结果。是 `whenComplete` 和 `exceptionally` 的结合体。

exceptionally使用：处理异常时需要返回一个默认值；这个默认值可以流向下游处理

~~~java
 public static void main(String[] args)  {
        CompletableFuture<Void> taskA = CompletableFuture.supplyAsync(() -> {
                    int i = new Random().nextInt(100);

                    for (int j = 0; j < 100; j++) {
                        i++;
                    }
                    i=i/0;//异常
                    return i;
                }, THREAD_POOL_EXECUTOR)
            //捕获异常
                .exceptionally(e->{
                    System.out.println("任务A异常：" + e.getMessage());
                    return 1;//返回默认值
                }).thenAccept(result -> System.out.println("任务A结果：" + result));

        THREAD_POOL_EXECUTOR.shutdown();
    }
~~~

![image-20261106163502965](C:\Users\沨烨\AppData\Roaming\Typora\typora-user-images\image-20261106163502965.png)

handle使用

~~~java
CompletableFuture<Integer> taskB = CompletableFuture.supplyAsync(() -> {
            int i = new Random().nextInt(100);

            for (int j = 0; j < 100; j++) {
                i++;
            }
            i = i / 0;//异常
            return i;
        }, THREAD_POOL_EXECUTOR).handle((result, e) -> {
            if (e != null) {
                System.out.println("任务B异常：" + e.getMessage());
                return 1;//返回默认值
            }
            System.out.println("打印日志....result:" + result);
            return result;
        }).thenApply(result -> {
            System.out.println("任务B结果：" + result);
            return result;
        });
~~~



![image-20261106165206128](C:\Users\沨烨\AppData\Roaming\Typora\typora-user-images\image-20261106165206128.png)



**所以日常是`handle`和`exceptionally`结合,`handle`在前面捕获异常记录日志将异常抛出，`exceptionally`在后面接着专门用来处理异常。**

~~~java
        CompletableFuture<Integer> taskC = CompletableFuture.supplyAsync(() -> {
                    int i = new Random().nextInt(100);

                    for (int j = 0; j < 100; j++) {
                        i++;
                    }
                    i = i / 0;//异常
                    return i;
                }, THREAD_POOL_EXECUTOR)
                //第一层捕获异常不处理向外抛出，记录日志
                .handle((result, e) -> {
                    if (e != null) {
                        System.out.println("任务C异常：" + e.getMessage());
                        throw new RuntimeException(e);
                    }
                    System.out.println("打印日志....result:" + result);
                    return result;
                //第二层捕获异常处理
                }).exceptionally(e -> {
                    System.out.println("任务C结果：" + e.getMessage());
                    return 0;
                //第三层业务处理
                }).thenApply(result -> {
                    System.out.println("任务C结果：" + result);
                    return result;
                });
~~~



![image-20261105223136323](C:\Users\沨烨\AppData\Roaming\Typora\typora-user-images\image-20261105223136323.png)

2、异常规则

`CompletableFuture`的异常中断原则：

 `CompletableFuture` 最基础的级联原则：**一旦某一步出错，整条链后续的“正常逻辑”全部被跳过（短路），直到遇到异常处理节点。**

~~~java
        CompletableFuture<Integer> taskA = CompletableFuture.supplyAsync(() -> {
            return new Random().nextInt(100);
        }, THREAD_POOL_EXECUTOR);
        taskA.thenAccept(result -> System.out.println("任务A结果：" + result));

        CompletableFuture<Integer> taskB = CompletableFuture.supplyAsync(() -> {
            throw new RuntimeException("taskB exception");
        }, THREAD_POOL_EXECUTOR);
        taskB.thenAccept(result -> System.out.println("任务B结果：" + result));

        CompletableFuture<Void> taskC = CompletableFuture.allOf(taskA, taskB);
        taskC.thenApply(result -> {
            return taskA.join() + taskB.join();
        }).thenAccept(result -> System.out.println("任务C结果：" + result));
        THREAD_POOL_EXECUTOR.shutdown();

任务A结果：6
~~~

这里只会有异常前任务A的结果，任务B异常，后面的流程跳过且不抛异常。

3、为啥要做异常处理？

**默认情况下，异步任务抛出异常，如果你不主动处理，它表面上看起来就像“什么都没发生”一样（静默失败）。**

如果你调用 `join()` 或 `get()` 去拿结果，异常就会被抛出来。

- `get()` 会抛出受检异常 `ExecutionException`（内部包裹了真实异常）。
- `join()` 会抛出非受检异常 `CompletionException`。

但如果你连 `get/join` 都没调，异常就丢失了。

就算用了`get/join`,此时获取的异常时间和真实异常发生的时间也是不同的，如果需要确切的时间做日志，这是不可接受的。

~~~java
package com.kuang.future;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.concurrent.*;

public class CompletableFutureExeception {
    private static final ThreadPoolExecutor THREAD_POOL_EXECUTOR = new ThreadPoolExecutor(5,
            10,
            30L,
            TimeUnit.SECONDS,
            new LinkedBlockingDeque<>(20),
            Executors.defaultThreadFactory(),
            new ThreadPoolExecutor.AbortPolicy());

    public static void main(String[] args)  {
        CompletableFuture<Void> taskA = CompletableFuture.runAsync(() -> {

            System.out.println("异常发生时间："+LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            throw new RuntimeException("taskA exception");
        }, THREAD_POOL_EXECUTOR);
        //模拟主线程执行耗时任务
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        try {
            System.out.println("获取异常时间："+LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            taskA.get();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
        THREAD_POOL_EXECUTOR.shutdown();
    }


}

~~~

![image-20261106162514575](C:\Users\沨烨\AppData\Roaming\Typora\typora-user-images\image-20261106162514575.png)

## 5、队列

### 队列体系

![java-queue-tree](https://natureshiyi-image.oss-cn-hangzhou.aliyuncs.com/img/java-queue-tree.png)

#### 一、继承关系

`java.util.Queue` 继承自 `Collection`，往下可以分成三类：

- **普通队列**：`PriorityQueue`、`ConcurrentLinkedQueue`、`ConcurrentLinkedDeque`
- **双端队列 `Deque`**：`ArrayDeque`、`LinkedList`、`LinkedBlockingDeque`
- **阻塞队列 `BlockingQueue`**：`ArrayBlockingQueue`、`LinkedBlockingQueue`、`PriorityBlockingQueue`、`DelayQueue`、`SynchronousQueue`、`LinkedTransferQueue`

> 记忆口诀：类名带 `Blocking` / `Concurrent` 的是线程安全的；`PriorityQueue`、`ArrayDeque`、`LinkedList` 这些普通实现都不是。

#### 二、速查表

##### 重点队列

##### ArrayBlockingQueue——有界数组阻塞队列

最经典的阻塞队列：容量固定、实现直白，读懂它就理解了阻塞队列的整套机制。

- 底层是 Object[] 循环数组，容量在构造时确定，之后不能扩容。
- 只有一把 ReentrantLock，靠 notEmpty / notFull 两个 Condition 完成阻塞与唤醒。
- 构造参数 fair 控制公平性：公平模式按 FIFO 唤醒、吞吐略低，默认非公平。
- 不允许 null；put / take 会阻塞，offer / poll 支持超时版本。
- 典型场景：ThreadPoolExecutor 配上它做有界队列实现背压，任务堆满后触发拒绝策略，避免 OOM。

##### LinkedBlockingQueue——链表阻塞队列

Executors 的默认任务队列，吞吐高，但默认容量这件事坑过很多人。

- 底层是单向链表 Node，默认容量 Integer.MAX_VALUE，不传容量就近似无界。
- putLock / takeLock 两把锁分离，入队和出队可以并行，吞吐通常高于 ArrayBlockingQueue。
- 元素计数用 AtomicInteger，size() 是 O(1)。
- 最大的坑：默认无界，生产速度长期大于消费速度会撑爆内存，务必显式指定容量。
- 典型场景：newFixedThreadPool、newSingleThreadExecutor 的默认队列，以及高吞吐生产消费。

##### SynchronousQueue——零容量手递手队列

容量为 0，不存元素，只负责把生产者直接匹配给消费者。

- put 必须等到有线程 take，take 也必须等到有线程 put；没有消费者时 put 一直阻塞。
- 公平模式基于 TransferQueue（FIFO），非公平模式基于 TransferStack（LIFO），默认非公平。
- offer() 不做等待、立即返回 false，适合“要么立刻被接走，要么失败”的语义。
- 典型场景：newCachedThreadPool——任务直接交给空闲线程，没有空闲线程就新建线程。

##### PriorityQueue——二叉堆优先级队列

非线程安全的优先级队列，算法题里的常客。

- 用 Object[] 数组维护二叉小顶堆，默认容量 11，满了自动扩容，逻辑上无界。
- 元素要么实现 Comparable，要么构造时传入 Comparator；不允许 null，出队顺序不保证稳定。
- offer / poll 是 O(log n)，peek 是 O(1)。
- 典型场景：优先级任务、Top-K、Dijkstra；并发环境改用 PriorityBlockingQueue。

##### DelayQueue——延迟队列

只有“到期”的元素才能被取走，是实现定时与超时功能的基础设施。

- 元素必须实现 Delayed 接口：getDelay(TimeUnit) 返回剩余延迟，compareTo 决定先后。
- 内部用 PriorityQueue 按到期时间排序，再配合 ReentrantLock + Condition 等待。
- 采用 leader/follower 模式，只让一个线程做定时等待，避免消费者集体空转。
- 典型场景：订单超时关闭、缓存过期清理、定时任务；ScheduledThreadPoolExecutor 内部的 DelayedWorkQueue 是同类实现。

##### ConcurrentLinkedQueue——CAS 无锁队列

基于 CAS 的非阻塞队列，线程不会被锁挂起，适合超高并发。

- 单向链表 + CAS 更新 head / tail，入队出队都不加锁。
- size() 需要遍历链表，O(n) 且弱一致，不要拿它做精确计数。
- 不允许 null，没有容量上限。
- 典型场景：高并发事件采集、日志缓冲、非阻塞生产消费链路。

##### ArrayDeque——循环数组双端队列

单线程下做栈或队列的首选，比 Stack 和 LinkedList 都更划算。

- 循环数组 + head / tail 下标，扩容时容量翻倍。
- 不允许 null；push / pop 当栈，offer / poll 当队列，addFirst / addLast 当双端队列。
- 作为栈比遗留的 Stack 快，作为队列比 LinkedList 更省内存。
- 典型场景：替代 Stack、滑动窗口、回溯、树的迭代遍历。

##### 其余队列一句话

- LinkedList：双向链表，同时实现 List 和 Deque，头尾操作 O(1)、随机访问 O(n)。
- LinkedBlockingDeque：链表双端阻塞队列，两端都能 put / take。
- PriorityBlockingQueue：线程安全的无界优先级阻塞队列，写不阻塞、读按优先级。
- LinkedTransferQueue：transfer() 有消费者就直交，没有就入队，兼具移交与缓冲。
- ConcurrentLinkedDeque：CAS 无锁双端队列，适合高并发双端操作。

##### 线程池里的队列

- newFixedThreadPool → LinkedBlockingQueue（无界，任务会一直堆积）
- newSingleThreadExecutor → LinkedBlockingQueue
- newCachedThreadPool → SynchronousQueue（空闲线程不够就新建，最大 Integer.MAX_VALUE）
- newScheduledThreadPool → DelayedWorkQueue（内部类，按执行时间排序）
- 自定义 ThreadPoolExecutor → 推荐 ArrayBlockingQueue 做有界队列，也可用 LinkedBlockingQueue、SynchronousQueue、PriorityBlockingQueue

| 需求                 | 首选                    | 一句话理由                 |
| -------------------- | ----------------------- | -------------------------- |
| 线程池有界队列、背压 | `ArrayBlockingQueue`    | 容量固定，满了触发拒绝策略 |
| 追求高吞吐、任务量大 | `LinkedBlockingQueue`   | 双锁分离，入队出队并行     |
| 不排队、直接移交线程 | `SynchronousQueue`      | 零容量，put/take 直接配对  |
| 任务有优先级         | `PriorityBlockingQueue` | 堆排序，按优先级出队       |
| 延时、定时执行       | `DelayQueue`            | 到期才能取，天然支持定时   |
| 高并发非阻塞         | `ConcurrentLinkedQueue` | CAS 无锁，不挂起线程       |
| 单线程栈 / 队列      | `ArrayDeque`            | 数组实现，比 `Stack` 快    |

### 常用队列实现

#### 1. ArrayBlockingQueue —— 有界数组阻塞队列

容量在构造时固定，只有一把 `ReentrantLock`，再加 `notEmpty` / `notFull` 两个 `Condition`；`put` / `take` 会阻塞，`offer` / `poll` 支持超时。队列满时，`offer`超时返回`false`；队列空时`poll`超时返回`null`表示获取不到新值了（因此实际使用，肯定提前判断null,防止空指针操作`null.XXX`）。

```java
BlockingQueue<Task> q = new ArrayBlockingQueue<>(100); // 有界

q.put(task);                            // 满则阻塞
q.offer(task, 1, TimeUnit.SECONDS);     // 满则最多等 1s
Task t = q.take();                      // 空则阻塞
Task t2 = q.poll(1, TimeUnit.SECONDS);  // 空则最多等 1s

// 配线程池做背压：队列满了走拒绝策略
ThreadPoolExecutor pool = new ThreadPoolExecutor(
    4, 8, 60L, TimeUnit.SECONDS,
    new ArrayBlockingQueue<>(200),
    new ThreadPoolExecutor.CallerRunsPolicy());
```

#### 2. LinkedBlockingQueue —— Executors 默认队列

链表实现，`putLock` / `takeLock` 两把锁分离，吞吐通常高于 `ArrayBlockingQueue`。**坑在默认容量**：不传就是 `Integer.MAX_VALUE`，生产快于消费时内存会一直涨。

```java
// Executors.newFixedThreadPool 用的就是它（默认无界）
ExecutorService pool = Executors.newFixedThreadPool(4);

// 业务代码建议显式指定容量
BlockingQueue<String> q = new LinkedBlockingQueue<>(1000);
q.put("job");
String job = q.take();

// 别用 new LinkedBlockingQueue<>()：生产快于消费会堆到 OOM
```

#### 3. SynchronousQueue —— 零容量直接移交

容量为 0，不存元素：`put` 必须等到有人 `take`，`take` 也必须等到有人 `put`。`newCachedThreadPool` 内部就是它。

```java
// 容量为 0：put 必须有人 take，take 必须有人 put
BlockingQueue<String> q = new SynchronousQueue<>();

new Thread(() -> {
    try { q.put("A"); } catch (InterruptedException e) { }
}).start();

String v = q.take();  // 与 put 配对成功，v = "A"

// CachedThreadPool：来任务直接交给空闲线程，没有就新建
ExecutorService pool = Executors.newCachedThreadPool();
```

#### 4. DelayQueue —— 延迟 / 超时任务

元素必须实现 `Delayed` 接口，只有 `getDelay()` 返回非正数时才能被取走。订单超时关闭、缓存过期清理都用它。

```java
class Order implements Delayed {
    private final long expireAt;               // 到期时间戳(ms)
    Order(long expireAt) { this.expireAt = expireAt; }

    public long getDelay(TimeUnit unit) {
        return unit.convert(expireAt - System.currentTimeMillis(),
                            TimeUnit.MILLISECONDS);
    }
    public int compareTo(Delayed o) {
        return Long.compare(this.getDelay(TimeUnit.MILLISECONDS),
                            o.getDelay(TimeUnit.MILLISECONDS));
    }
}

DelayQueue<Order> dq = new DelayQueue<>();
dq.put(new Order(System.currentTimeMillis() + 30_000));
Order o = dq.take();   // 阻塞到最早的元素到期
```

#### 5. PriorityBlockingQueue —— 优先级任务

内部还是二叉堆，只是包了锁和 `Condition`。**它是无界的，`put` 永远不会阻塞**，读取时按优先级出队。

```java
BlockingQueue<Task> q = new PriorityBlockingQueue<>(
        11, Comparator.comparingInt(Task::priority));

q.put(new Task(5));    // 无界，put 不会阻塞
Task t = q.take();     // 每次取优先级最高的
```

#### 6. ConcurrentLinkedQueue —— 无锁高并发

基于 CAS 的非阻塞队列，入队出队都不加锁。注意 `size()` 要遍历整条链表，是 `O(n)` 且弱一致的，不能拿来做容量判断。

```java
Queue<String> q = new ConcurrentLinkedQueue<>();

q.offer("a");
q.poll();          // 无锁、非阻塞，空队列返回 null

// size() 要遍历链表：O(n) 且弱一致
// 判断空别用 size() == 0，直接用 poll() == null
```

#### 7. ArrayDeque —— 单线程栈 / 队列

循环数组实现，非线程安全，但单线程下比 `Stack`、`LinkedList` 都更快，当栈、当队列、当双端队列都行。

```java
Deque<Integer> stack = new ArrayDeque<>();
stack.push(1);          // 当栈
int top = stack.pop();

Deque<Integer> queue = new ArrayDeque<>();
queue.offer(1);         // 当队列
queue.poll();
queue.offerFirst(0);    // 当双端队列
```
