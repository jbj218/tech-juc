package com.kuang.future;

import java.util.Locale;
import java.util.concurrent.*;

public class CompletableFutureUse1 {
    private static final ThreadPoolExecutor THREAD_POOL_EXECUTOR = new ThreadPoolExecutor(5,
            10,
            30L,
            TimeUnit.SECONDS,
            new LinkedBlockingDeque<>(20),
            Executors.defaultThreadFactory(),
            new ThreadPoolExecutor.AbortPolicy());
    public static void main(String[] args) {
        //thenApply 中间操作数据 Function;thenAccept 终端消费数据 Consumer
        //thenApply thenAccept 可能使用前面的异步线程，如果线程被回收使用调用者的线程如这里的主线程
        CompletableFuture.supplyAsync(()->{
            System.out.println("线程"+Thread.currentThread().getName());
            return "result";
            }
                        , THREAD_POOL_EXECUTOR)
                .thenApply(result->{
                    System.out.println("线程"+Thread.currentThread().getName());
                    return result.toUpperCase();
                })
                .thenAccept(result-> {
                    System.out.println("线程"+Thread.currentThread().getName());
                    System.out.println(result);
                });
        //thenApplyAsync、thenAcceptAsync 使用异步线程；
        CompletableFuture.supplyAsync(()->{
                            System.out.println("线程"+Thread.currentThread().getName());
                            return "result";
                        }
                        , THREAD_POOL_EXECUTOR)
                .thenApplyAsync(result->{
                    System.out.println("线程"+Thread.currentThread().getName());
                    return result.toUpperCase();
                })
                .thenAcceptAsync(result-> {
                    System.out.println("线程"+Thread.currentThread().getName());
                    System.out.println(result);
                });

    }
}
