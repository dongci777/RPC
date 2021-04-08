package com.dongci777.test;

import com.dongci777.client.Proxy;
import com.dongci777.impl.Test;
import com.dongci777.impl.TestImpl;

/**
 * @Author: zxb
 * @Date : 2021/4/8 9:52 下午
 */
public class Main {
    public static void main(String[] args) {
        Test test = (Test) new Proxy().getInstance(TestImpl.class);
        System.out.println(test.getRandom("fdkfjakf", 10));
        System.out.println(test.getUUID());
    }
}
