# 实现自己的RPC框架

背景：面试官让实现一个RPC

自己本身对分布式尤其是RPC不够了解，加之当时对JDK动态代理也不是很熟悉，所以结果不太理想。后面自己查阅资料，自己实现了并优化了RPC框架，特此记录。本文记录用Java实现一个简单的RPC框架，后面会继续优化。本系列文章将会实现从简单RPC到考虑网络复杂性等问题进行改进再到整合Zookeeper实现服务注册和服务发现功能到整合Spring实现通过注解和配置文件的方式来发布服务等功能。



## 一、准备工作

首先，在实现RPC框架之前，我们需要知道RPC框架中所需的技术是什么。让我们来分析一下RPC，RPC要实现的功能是我们在调用远程服务的时候要像本地调用一样，要是使用者能够透明的调用远程方法。（为了描述简单起见，后文一律用服务端来表示被调用端，用客户端来表示调用端）。

1. 网络通信问题y
2. 序列化知识
3. Java反射相关知识
4. JDK动态代理相关知识

- 网络通信问题

我们先不考虑网络的复杂性以及远程应用可能发生错误或者挂掉等问题，要在本地调用服务端方法，肯定需要让服务端知道我们要调用的是哪个类的哪个方法，所以我们需要了解的有在客户端和服务端建立通信的方法。这里有很多方式，Socket编程、Netty，Mina等框架都能实现。当然涉及了网络，我们也得清楚我们是选择BIO还是NIO，由于本篇着重点在RPC的实现而不是IO，所以后文在IO方面不再赘述，本文为了简单性，使用Java原生的Socket和BIO来实现相关功能。

- 序列化知识

由于传递方法的参数等可能要涉及到序列化和反序列化的问题，所以我们得掌握序列化相关的知识。序列换的实现方式也有很多，常用的Java序列化、json、xml等序列化等。当然，还有很多第三方框架如protostuf等都提供了很好用的序列化方法，在本文中，我们直接使用Java的ObjectInputStream和ObjectOutputStream来实现相关功能。

- Java反射相关知识

由于在客户端并没有其调用方法的实现，客户端只知道有其调用方法的接口，所以在调用过程中客户端只知道其要调用方法所属的接口和方法名称和参数表。所以其通过网络传输给服务端的也只有这些内容，而服务端需要通过接口和方法名称来实现相关方法的调用，这里就涉及到Java反射技术。而Java反射技术简单说起来是只要知道一个类就可以知道类的所有属性和方法的能力，当然反射说起来简单，想要用好却得花大力气。

- JDK动态代理相关知识

由于要让客户端透明地实现远程方法的调用，所以只能借用JDK动态代理的技术将调用的网络通信等相关内容隐藏起来。当然，JDK动态代理也是Java中的进阶知识，但是简单的说起来，其就是可以动态地在调用方法前后加入相关内容来实现我们需要的功能。



## 二、代码实现

首先看下代码整体结构：

![](https://tva1.sinaimg.cn/large/008eGmZEgy1gpcpav9q11j31fh0u00z0.jpg)

1、客户端实现

```java
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
```

以上的代码就实现了一个很简单的RPC客户端，为了简单起见，我们将ip地址、端口号都写死在代码里，这不是一种好的实现方式。同时，我们可以看到该类实现了InvocationHandle接口。该接口即为JDK动态代理的接口，该接口只有一个方法即invoke方法，该方法有三个参数，分别是proxy即代理对象，method即被代理的方法，args即被代理方法的参数。然后我们在invoke方法中实现了和服务端进行通信以及返回返回值等操作，依托于jdk动态代理技术，这些操作在使用RPC时都是透明的。通过代码可以看到，我们建立了一个和服务端的Socket连接，然后使用ObjectOutputStream对象将类名（接口名），方法名发送给服务端，服务端在收到之后就可以通过反射的方式得到对应的对象和方法，当然，由于方法除了方法名之外还要参数列表才能确认是某一个方法，毕竟Java支持重载，所以还需要将序列化之后的参数表发送给客户端，最后是调用方法时的参数。在发送完之后接收服务端执行之后的返回值后在finally中关闭相关资源。

2、服务端代码

```java
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
```

通过上面的代码我们可以看到，我们首先是建立了一个服务端的socket连接，然后在死循环中等客户端的连接，在连接建立之后，就接收相关参数然后通过反射技术得到相应的方法，并在调用相应方法后通过Socket发送给客户端。



3、测试

测试接口，返回随机数和UUID

```java
package com.dongci777.impl;

/**
 * @Author: zxb
 * @Date : 2021/4/8 9:50 下午
 */
public interface Test {
    int getRandom(String test, int a);

    String getUUID();
}
```

测试类，实现了Test接口

```java
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
```

> 可以看到方法实现是打印参数然后返回[0，1000）的随机数以及返回UUID。



在客户端使用RPC调用方法

```java
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
```



结果：

客户端：

![](https://tva1.sinaimg.cn/large/008eGmZEgy1gpcp6zlf9vj30zm07g3zd.jpg)

服务端：

![](https://tva1.sinaimg.cn/large/008eGmZEgy1gpcp7pfvnaj310i0763z6.jpg)

结果正确，成功实现了一个简单的RPC。

