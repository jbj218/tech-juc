package com.kuang.future;

import java.util.concurrent.*;

public class FutureUse {
    public static void main(String[] args) {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        /*Future<Integer> future = executorService.submit(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                Thread.sleep(2000);
                return 1 + 1;
            }
        });*/
        Future<Integer> future = executorService.submit(() -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return 1 + 1;
        });
        System.out.println("主线程做自己的事，不依赖于异步线程");
        // 获取异步线程的执行结果
        try {
            System.out.println(future.get());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }finally {
            executorService.shutdown();
        }

    }
}
