package com.xsm.common.registry.zookeeper;

import com.xsm.common.bean.Constant;
import com.xsm.common.registry.ServiceRegistry;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;

import java.util.concurrent.CountDownLatch;

/**
 * @author xsm
 * @Date 2020/5/20 22:40
 * zk 服务注册
 */
public class ZkServiceRegistry implements ServiceRegistry {

    private final CuratorFramework curatorFramework;
    private final CountDownLatch countDownLatch = new CountDownLatch(1);

    public ZkServiceRegistry(String address) {
        this.curatorFramework = CuratorFrameworkFactory.builder()
                .connectString(address)
                .sessionTimeoutMs(Constant.ZK_SESSION_TIMEOUT)
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                .build();
    }

    @Override
    public void registry(String data) throws Exception {
        curatorFramework.start();

        String path = Constant.ZK_CHILDREN_PATH;
        curatorFramework.create()
                .creatingParentsIfNeeded()
                .withMode(CreateMode.EPHEMERAL_SEQUENTIAL)
                .forPath(path, data.getBytes());

    }
}
