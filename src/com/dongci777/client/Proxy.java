package com.dongci777.client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.Socket;

/**
 * @Author: zxb
 * @Date : 2021/4/8 9:21 下午
 */

/**
 * RPC客户端
 */
public class Proxy implements InvocationHandler {

    private final String ip = "127.0.0.1";
    private final int port = 9999;
    private Socket socket;
    Class<?> clazz;

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        try {
            socket = new Socket(ip, port);
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
            objectOutputStream.writeUTF(clazz.getName());
            objectOutputStream.writeUTF(method.getName());
            objectOutputStream.writeObject(method.getParameterTypes());
            objectOutputStream.writeObject(args);
            ObjectInputStream objectInputStream = new ObjectInputStream(socket.getInputStream());
            return objectInputStream.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public Object getInstance(Class<?> clazz) {
        this.clazz = clazz;
        return java.lang.reflect.Proxy.newProxyInstance(clazz.getClassLoader(), clazz.getInterfaces(), this);
    }
}
