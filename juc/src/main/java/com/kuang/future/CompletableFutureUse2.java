package com.kuang.future;

import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
/*
* complete*/
public class CompletableFutureUse2 {
    private static final ThreadPoolExecutor THREAD_POOL_EXECUTOR = new ThreadPoolExecutor(5,
            10,
            30L,
            TimeUnit.SECONDS,
            new LinkedBlockingDeque<>(20),
            Executors.defaultThreadFactory(),
            new ThreadPoolExecutor.AbortPolicy());
}
