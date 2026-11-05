package com.kuang.future;

import java.util.concurrent.*;

public class CompletableFutureUse {
    private static final ThreadPoolExecutor THREAD_POOL_EXECUTOR = new ThreadPoolExecutor(5,
            10,
            30L,
            TimeUnit.SECONDS,
            new LinkedBlockingDeque<>(20),
            Executors.defaultThreadFactory(),
            new ThreadPoolExecutor.AbortPolicy());

    public static void main(String[] args) {
        //1.runAsync 默认线程池ForkJoinPool.commonPool()
        CompletableFuture.runAsync(()->{
            System.out.println("无返回值异步:"+Thread.currentThread().getName());
        });
        //2.runAsync 自定义线程池
        CompletableFuture.runAsync(()->{
            System.out.println("无返回值异步+自定义线程池:"+Thread.currentThread().getName());
        },THREAD_POOL_EXECUTOR);
        //3.supplyAsync 默认线程池ForkJoinPool.commonPool()
        CompletableFuture.supplyAsync(()->{
            System.out.println("有返回值异步:"+Thread.currentThread().getName());
            return "result";
        });
        //4.supplyAsync 自定义线程池
        CompletableFuture.supplyAsync(()->{
            System.out.println("有返回值异步+自定义线程池:"+Thread.currentThread().getName());
            return "result";
        },THREAD_POOL_EXECUTOR);

}
}
