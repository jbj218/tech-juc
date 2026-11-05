package com.kuang.future;

import java.util.concurrent.*;

import static java.lang.Thread.sleep;

public class FutureCancel {
    public static void main(String[] args) {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<Integer> future1 = executorService.submit(() -> {
            Thread.sleep(2000);
            return 1+1;
            }
        );
        try {
            System.out.println(future1.get());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
        Future<Integer> future2 = executorService.submit(() -> 2+2);
        //对于没有正在执行的任务，cancel的true和false没有区别
        boolean result = future2.cancel(true);
        //boolean result = future2.cancel(false);
        try {
            System.out.println(future2.get());
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            executorService.shutdown();
        }
    }
}
class FutureCancel1 {

    public static void main(String[] args) {
        System.out.println("开始执行任务");
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<Integer> future = executorService.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {//线程不中断就打印这个
                Thread.sleep(1000);
                System.out.println("线程正在执行.......");
            }
            return 1 + 1;
        });
        //让线程开始跑起来，别上来就被取消了
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        //boolean result = future.cancel(true);
        boolean result = future.cancel(false);
        try {
            System.out.println(future.get());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (CancellationException e){
            throw new RuntimeException(e);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
        finally {
            executorService.shutdown();
        }
        System.out.println("result: " + result);
        System.out.println("任务结束");
    }
}
