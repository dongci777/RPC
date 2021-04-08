package com.dongci777.impl;

import java.util.Random;
import java.util.UUID;

/**
 * @Author: zxb
 * @Date : 2021/4/8 9:51 下午
 */
public class TestImpl implements Test {
    @Override
    public int getRandom(String test, int a) {
        System.out.println("the String is" + test);
        System.out.println("the num is" + a);
        Random random = new Random();
        return random.nextInt(1000);
    }

    @Override
    public String getUUID() {
        return UUID.randomUUID().toString();
    }
}
