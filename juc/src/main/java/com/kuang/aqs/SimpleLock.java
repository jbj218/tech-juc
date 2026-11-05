package com.kuang.aqs;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

public class SimpleLock {
    private static class Sync extends AbstractQueuedSynchronizer{
        @Override
        protected boolean tryAcquire(int acquires) {
            if(compareAndSetState(0,1)){
                setExclusiveOwnerThread(Thread.currentThread());
                return true;
            }
            return false;
        }

        @Override
        protected boolean tryRelease(int releases) {
            if(getState()==0) throw new IllegalMonitorStateException();
            setExclusiveOwnerThread(null);
            setState(0);
            return true;
        }

        @Override
        protected boolean isHeldExclusively() {
            return getState()==1;
        }
        private final Sync sync = new Sync();
        public void lock(){
            sync.acquire(1);
        }
        public void unlock(){
            sync.release(1);
        }
    }
}
