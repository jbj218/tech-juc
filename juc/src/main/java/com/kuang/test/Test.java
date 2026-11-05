package com.kuang.test;

public class Test {
    public static void test(){
        System.out.println("test");
    }
    public static Integer test2(){
        return 1;
    }
    public static void main(String[] args) {
        test();
        Integer i = test2();
    }
}
