package org.gscheduler.commons;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.api.CuratorWatcher;
import org.apache.curator.framework.recipes.cache.NodeCache;
import org.apache.curator.framework.recipes.cache.NodeCacheListener;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.utils.ZKPaths;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.Map;

/**
 * zk封装类
 */
@Component
public class ZkHelper {
    private static final Logger logger = LoggerFactory.getLogger(ZkHelper.class);

    private volatile Map<String, ZKClient> clientMap = Maps.newConcurrentMap();

    @Value("zookeeper.address")
    String connectionUrl;

    @PreDestroy
    public void closeZkClient() {
        logger.info("start close zk.");
        for (ZKClient zkClient : clientMap.values()) {
            try {
                zkClient.close();
            } catch (Exception e) {
                logger.error("close Curator client exception.", e);
            }
        }
        logger.info("zkClients closed.");
    }

    public ZKClient getDefaultZKClient() {
        ZKClient zkClient = getZKClient(connectionUrl);
        return zkClient;
    }

    public ZKClient getZKClient(String address) {
        Preconditions.checkArgument(StringUtils.isNotBlank(address), "address is illegal.");

        if (clientMap.containsKey(address)) {
            return clientMap.get(address);
        }
        synchronized (this) {
            if (clientMap.containsKey(address)) {
                return clientMap.get(address);
            } else {
                ZKClient zkClient = new ZKClient(address);
                clientMap.put(address, zkClient);
                return zkClient;
            }
        }
    }

    public static class ZKClient {
        private String connectString;
        private CuratorFramework client;
        private static final int TIMEOUT = 5000;

        //注册,仅用作关闭!
        private Map<String, NodeCache> nodeCacheMap = Maps.newConcurrentMap();
        private Map<String, PathChildrenCache> pathChildrenCacheMap = Maps.newConcurrentMap();

        public ZKClient(String address) {
            this.connectString = address;
            //new ExponentialBackoffRetry(5000, 3),new RetryNTimes(3, 5000)
            client = CuratorFrameworkFactory.newClient(address, TIMEOUT, TIMEOUT, new ExponentialBackoffRetry(TIMEOUT, 3));
            client.start();
            this.setConnWatcher(new ConnectionStateListener() {
                @Override
                public void stateChanged(CuratorFramework client, ConnectionState newState) {
                    logger.info("conn:{},state changed:{}", connectString, newState.name());
                }
            });
        }

        public CuratorFramework getZkCuratorClient() {
            return client;
        }

        public String getConnectString() {
            return connectString;
        }

        public boolean createNode(String path, CreateMode mode, byte[] data) {
            try {
                ZKPaths.mkdirs(client.getZookeeperClient().getZooKeeper(), path, false);
                client.create().creatingParentsIfNeeded().withMode(mode).forPath(path, data);
                return true;
            } catch (Exception e) {
                logger.error("createPersistentNode node exception.", e);
            }
            return false;
        }

        public boolean createNode(String path, CreateMode mode) {
            try {
                ZKPaths.mkdirs(client.getZookeeperClient().getZooKeeper(), path, false);
                client.create().creatingParentsIfNeeded().withMode(mode).forPath(path);
            } catch (Exception e) {
                logger.error("createPersistentNode node exception.", e);
                return false;
            }
            return true;
        }

        public String getData(String path) {
            if (!checkNodeExisted(path)) {
                logger.warn("the node does not exist.");
                return "";
            }
            byte[] bytes = new byte[0];
            try {
                bytes = client.getData().forPath(path);
            } catch (Exception e) {
                logger.error("get zk node data exception.", e);
            }
            return new String(bytes);
        }

        public boolean setData(String path, byte[] data) {
            try {
                if (!checkNodeExisted(path)) {
                    logger.info("the node does not exist,create one,path:{}", path);
                    ZKPaths.mkdirs(client.getZookeeperClient().getZooKeeper(), path, true);
                }
                client.setData().forPath(path, data);
            } catch (Exception e) {
                logger.error("set zk node data exception.", e);
                return false;
            }
            return true;
        }

        /**
         * 给指定路径节点设置一个监控,监控zk节点的变化,只监控一次
         *
         * @param path           zk路径
         * @param curatorWatcher 监控方法
         */
        public void setNodeWatcher(String path, CuratorWatcher curatorWatcher) {
            Preconditions.checkArgument(checkNodeExisted(path), "the node does not exist.");
            try {
                client.getData().usingWatcher(curatorWatcher).forPath(path);
            } catch (Exception e) {
                logger.error("set {} watcher exception.", path, e);
            }
        }

        public boolean deleteNode(String path) {
            if (!checkNodeExisted(path)) {
                return false;
            }
            try {
                client.delete().forPath(path);
            } catch (Exception e) {
                logger.error("delete node exception.", e);
                return false;
            }
            return true;
        }

        /**
         * 给指定路径节点的所有子节点设置一个监控,只监控一次
         *
         * @param path           zk路径
         * @param curatorWatcher 监控方法
         */
        public void setChildrenWatcher(String path, CuratorWatcher curatorWatcher) {
            Preconditions.checkArgument(checkNodeExisted(path), "the node does not exist.");
            try {
                client.getChildren().usingWatcher(curatorWatcher).forPath(path);
            } catch (Exception e) {
                logger.error("set {} watcher exception.", path, e);
            }
        }

        public boolean checkNodeExisted(String path) {
            if (StringUtils.isBlank(path)) {
                logger.info("path is empty.");
                return false;
            }
            Stat stat = null;
            try {
                stat = client.checkExists().forPath(path);
            } catch (Exception e) {
                logger.error("check node exist exception.path:{}", path, e);
            }

            return stat == null ? false : true;
        }

        public boolean checkAndCreatePath(String path) {
            if (checkNodeExisted(path)) {
                return true;
            }
            try {
                ZKPaths.mkdirs(client.getZookeeperClient().getZooKeeper(), path, true);
            } catch (Exception e) {
                logger.error("make zk path error.path:{}", path, e);
                return false;
            }
            return true;
        }

        /**
         * 给该连接设置一个监控,连接每发生一次变化,都会回调,重复监听
         * Curator具有连接中断重连机制!
         *
         * @param connWatcher
         */
        public void setConnWatcher(ConnectionStateListener connWatcher) {
            client.getConnectionStateListenable().addListener(connWatcher);
        }

        /**
         * 监听Node节点中数据改变事件,自动重复监听
         *
         * @param path
         * @param listener
         * @return
         */
        public boolean setNodeCacheListener(String path, NodeCacheListener listener) {
            if (!checkNodeExisted(path)) {
                logger.info("path does not exist.path:{}", path);
                return false;
            }

            NodeCache nodeCache = new NodeCache(client, path);
            nodeCacheMap.put(path, nodeCache);
            try {
                nodeCache.start();
                nodeCache.getListenable().addListener(listener);
            } catch (Exception e) {
                logger.info("set node listener exception.path:{}", path, e);
                return false;
            }
            logger.info("set node update listener success.path:{}", path);
            return true;
        }

        /**
         * 关闭指定path的NodeCache
         *
         * @param path 指定path
         */
        public void closeNodeCache(String path) {
            if (nodeCacheMap.containsKey(path)) {
                try {
                    nodeCacheMap.get(path).close();
                } catch (IOException e) {
                    logger.info("close node cached exception,nodeCache:{}",
                            nodeCacheMap.get(path).getCurrentData().toString(), e);
                }
            }
        }

        public void closePathChildrenCache(String path) {
            if (pathChildrenCacheMap.containsKey(path)) {
                PathChildrenCache childrenCache = pathChildrenCacheMap.get(path);
                closePathChildrenCache(childrenCache);
            }
        }

        private void closePathChildrenCache(PathChildrenCache childrenCache) {
            try {
                childrenCache.close();
                //清空缓存值
                childrenCache.clear();
                //清除map中的listener对象
                childrenCache.getListenable().clear();
            } catch (IOException e) {
                logger.error("close PathChildrenCache exception.", e);
            }
        }

        /**
         * 关闭ZKClient对象
         */
        public void close() {
            for (NodeCache nodeCache : nodeCacheMap.values()) {
                try {
                    nodeCache.close();
                } catch (IOException e) {
                    logger.error("close node cached exception,nodeCache:{}",
                            nodeCache.getCurrentData().toString(), e);
                }
            }
            for (PathChildrenCache childrenCache : pathChildrenCacheMap.values()) {
                closePathChildrenCache(childrenCache);
            }
            try {
                client.close();
            } catch (Exception e) {
                logger.error("zk client close exception.", e);
            }
        }

        public boolean setPathChildListener(String path, PathChildrenCacheListener listener) {
            if (!checkNodeExisted(path)) {
                logger.info("path does not exist.path:{}", path);
                return false;
            }
            PathChildrenCache childrenCache = new PathChildrenCache(this.getZkCuratorClient(), path, false);
            pathChildrenCacheMap.put(path, childrenCache);
            try {
                childrenCache.start();
                childrenCache.getListenable().addListener(listener);
            } catch (Exception e) {
                logger.error("start PathChildrenCache error.", e);
                return false;
            }
            logger.info("set Path Child Listener success.path:{}", path);
            return true;
        }

    }

}
