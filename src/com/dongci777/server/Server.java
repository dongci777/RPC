package com.dongci777.server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * @Author: zxb
 * @Date : 2021/4/8 9:39 下午
 */
public class Server {
    private int port = 9999;

    private void run() {
        ServerSocket serverSocket = null;
        Socket socket = null;
        try {
            serverSocket = new ServerSocket(port);
            while (true) {
                socket = serverSocket.accept();
                ObjectInputStream objectInputStream = new ObjectInputStream(socket.getInputStream());
                String objectName = objectInputStream.readUTF();
                String methodName = objectInputStream.readUTF();
                Class<?>[] parameters = (Class<?>[]) objectInputStream.readObject();
                Object[] args = (Object[]) objectInputStream.readObject();
                Class<?> clazz = Class.forName(objectName);
                Method method = clazz.getMethod(methodName, parameters);
                Object o = method.invoke(clazz.newInstance(), args);
                ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
                objectOutputStream.writeObject(o);
            }
        } catch (IOException | ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        } finally {
            assert socket != null;
            try {
                socket.close();
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        new Server().run();
    }
}
