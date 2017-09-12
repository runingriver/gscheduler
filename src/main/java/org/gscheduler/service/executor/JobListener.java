package org.gscheduler.service.executor;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.zookeeper.CreateMode;
import org.gscheduler.commons.ZkHelper;
import org.gscheduler.entity.JobInfo;
import org.gscheduler.service.task.JobInfoService;
import org.gscheduler.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 它机任务监听:任务状态参数改变监听,任务执行状态监听(服务器不可用接管任务执行)
 * 开关方法:1.接口;2.properties文件
 */
@Component
public class JobListener {
    private static final Logger logger = LoggerFactory.getLogger(JobListener.class);

    @Resource
    ZkHelper zkHelper;

    @Resource
    JobManager jobManager;

    @Resource
    JobInfoService jobInfoService;

    @Value("${zookeeper.failover.tolerate.time}")
    String zkFailoverTolerateTime;

    @Value("${zookeeper.lock.tolerate.time}")
    String zkLockTolerateTime;

    private ZkHelper.ZKClient zkClient;

    //缓存zk监听的task
    private Map<String, Long> taskNodeMap = new ConcurrentHashMap<>(50);
    //保存failover机器上接管过来,并在本地执行的任务
    private final static Map<String, List<JobInfo>> removeNodeMap = Maps.newConcurrentMap();
    //job更新同步监测路径
    public static final String SERVICE_UPDATE_PATH = "/monitor/task/service_update";
    //服务监测同步锁,TOLERATE_TIME内只检测一次
    private static final String SERVICE_UPDATE_LOCK_PATH = "/monitor/task/lock/startup_lock";
    //服务可用监测路径
    private static final String SERVICE_AVAILABLE_PATH = "/monitor/task/service_available";
    //failover任务锁,failover后一个任务只能在一台service上执行
    private static final String SERVICE_AVAILABLE_LOCK = "/monitor/task/lock/failover_lock";
    //failover执行线程池
    private ExecutorService service;
    //failover等待最长时间,即发布容忍最长时间ms
    private int tolerateTime = 10 * 60 * 1000;
    //分布式锁获取超时时间ms
    private int lockAcquireTime = 10 * 1000;
    //是否在使用zk作为一致性的监听实现
    private boolean isInListening = false;

    /**
     * 启动检查所有任务是否已经注册到zk,是-略过,否-注册.
     */
    public void init() {
        //参数配置
        isInListening = true;
        int configTolerateTime = NumberUtils.toInt(zkFailoverTolerateTime, 0);
        if (configTolerateTime > 0 && configTolerateTime != tolerateTime) {
            tolerateTime = configTolerateTime;
        }
        int configLockAcquireTime = NumberUtils.toInt(zkLockTolerateTime, 0);
        if (configLockAcquireTime > 0 && configLockAcquireTime != lockAcquireTime) {
            lockAcquireTime = configLockAcquireTime;
        }
        logger.info("isInListener:{},tolerateTime:{},lockAcquireTime:{}", isInListening, tolerateTime, lockAcquireTime);

        //检测
        zkClient = zkHelper.getDefaultZKClient();
        InterProcessMutex lock = new InterProcessMutex(zkClient.getZkCuratorClient(), SERVICE_UPDATE_LOCK_PATH);
        try {
            lock.acquire();

            long lockNodeData = NumberUtils.toLong(zkClient.getData(SERVICE_UPDATE_LOCK_PATH), 0);
            long date = new Date().getTime();
            //如果10分钟内更新过,即认为已是最新
            boolean isUpdated = (date - lockNodeData) <= tolerateTime;

            //检查路径存在
            boolean isCreatedAvailablePath = zkClient.checkAndCreatePath(SERVICE_AVAILABLE_PATH);
            boolean isCreatedUpdatePath = zkClient.checkAndCreatePath(SERVICE_UPDATE_PATH);
            logger.info("isCreatedAvailablePath:{},isCreatedUpdatePath:{}", isCreatedAvailablePath, isCreatedUpdatePath);
            //将自己注册上去
            String hostNodePath = SERVICE_AVAILABLE_PATH + "/" + Utils.getHostName();
            if (zkClient.checkNodeExisted(hostNodePath)) {
                zkClient.deleteNode(hostNodePath);
            }
            zkClient.createNode(hostNodePath, CreateMode.EPHEMERAL);

            //检查并设置任务监听
            List<JobInfo> scheduleList = jobInfoService.getAllJobInfo();
            for (JobInfo jobInfo : scheduleList) {
                String nodePath = SERVICE_UPDATE_PATH + "/" + jobInfo.getJobName();

                //检查是否所有节点存在
                boolean existed = zkClient.checkNodeExisted(nodePath);
                //节点不存在,创建一个,存在刷新值
                if (!existed) {
                    String taskJson = createJobJson(jobInfo, JobManager.JobOperator.NONE);
                    logger.info("create node,path:{},task:{}", nodePath, jobInfo.toString());
                    zkClient.createNode(nodePath, CreateMode.PERSISTENT, taskJson.getBytes());
                } else if (!isUpdated) {
                    //如果最近没有更新过,更新!
                    String taskJson = createJobJson(jobInfo, JobManager.JobOperator.NONE);
                    logger.info("update node:{} data:{}", nodePath, taskJson);
                    zkClient.setData(nodePath, taskJson.getBytes());
                }

                taskNodeMap.put(nodePath, jobInfo.getId());
            }

            //监听'/monitor/task/service_update'路径下的节点增删
            zkClient.setPathChildListener(SERVICE_UPDATE_PATH, taskNodeListener());
            //监听'/monitor/task/service_available'路径下的节点增删
            zkClient.setPathChildListener(SERVICE_AVAILABLE_PATH, serviceNodeListener());
            //执行完毕,设置lock时间戳
            zkClient.setData(SERVICE_UPDATE_LOCK_PATH, Long.toString(date).getBytes());
        } catch (Exception e) {
            logger.error("init zk listener exception.", e);
            zkClient.setData(SERVICE_UPDATE_LOCK_PATH, "exception".getBytes());
        } finally {
            try {
                lock.release();
            } catch (Exception e) {
                logger.error("release zk update lock exception.", e);
            }
        }

    }

    /**
     * 将必要的taskSchedule信息转换成json
     *
     * @param jobInfo  jobInfo
     * @param operator 接收到nodeChange通知时的操作
     * @return json
     */
    public static String createJobJson(JobInfo jobInfo, JobManager.JobOperator operator) {
        Map<String, String> jsonMap = Maps.newHashMap();
        jsonMap.put("id", Long.toString(jobInfo.getId()));
        jsonMap.put("configParameter", jobInfo.getConfigParameter());
        jsonMap.put("crontab", jobInfo.getCrontab());
        //jsonMap.put("taskClass", jobInfo.getTaskClass());
        //jsonMap.put("hostList", jobInfo.getHostList());
        jsonMap.put("executeHost", jobInfo.getExecuteHost());
        jsonMap.put("initiateMode", String.valueOf(jobInfo.getInitiateMode()));
        jsonMap.put("failExecuteHost", jobInfo.getFailExecuteHost());
        jsonMap.put("operator", operator.name());
        return JSON.toJSONString(jsonMap);
    }

    /**
     * 监听'/monitor/task/service_update'节点下的子节点的添加和删除事件
     *
     * @return PathChildrenCacheListener
     */
    private PathChildrenCacheListener taskNodeListener() {
        return new PathChildrenCacheListener() {
            @Override
            public void childEvent(CuratorFramework client, PathChildrenCacheEvent event) throws Exception {
                if (event == null || event.getData() == null || event.getData().getPath() == null) {
                    logger.warn("PathChildrenCacheListener event is null.");
                    return;
                }

                String nodePath = event.getData().getPath();
                logger.info("task node event,type:{},path:{}", event.getType().name(), nodePath);

                switch (event.getType()) {
                    case CHILD_UPDATED:
                        taskNodeUpdate(nodePath);
                        break;
                    case CHILD_ADDED:
                        taskNodeAdd(nodePath);
                        break;
                    case CHILD_REMOVED:
                        //记录已从db移除,直接关闭即可
                        if (taskNodeMap.containsKey(nodePath)) {
                            logger.info("kill and remove node:{}", nodePath);
                            jobManager.killSchedule(taskNodeMap.get(nodePath));
                            taskNodeMap.remove(nodePath);
                        }
                        break;
                    default:
                        break;
                }
            }
        };
    }

    private void taskNodeUpdate(String nodePath) {
        logger.info("enter task node update event,nodePath:{}", nodePath);
        String jsonData = zkClient.getData(nodePath);
        if (StringUtils.isBlank(jsonData)) {
            logger.info("task node update event,cannot get zk node data.");
            return;
        }
        JSONObject jsonObject = JSON.parseObject(jsonData);
        String operator = jsonObject.getString("operator");
        String executeHost = jsonObject.getString("executeHost");
        String failExecuteHost = jsonObject.getString("failExecuteHost");
        String initiateMode = jsonObject.getString("initiateMode");
        long id;
        if (taskNodeMap.containsKey(nodePath)) {
            id = taskNodeMap.get(nodePath);
        } else {
            id = Long.parseLong(jsonObject.getString("id"));
        }

        logger.info("get zk task node data:{}", jsonData);
        //failover时执行操作
        if (StringUtils.equals(Utils.getHostName(), failExecuteHost)) {
            logger.info("failover executor,failExecuteHost:{},operator:{}", failExecuteHost, operator);
            //本机是failover的执行机器
            if (JobManager.JobOperator.valueOf(operator) == JobManager.JobOperator.STOP) {
                logger.info("stop failover job,id:{}", id);
                jobManager.stopSchedule(id);
            } else if (JobManager.JobOperator.valueOf(operator) == JobManager.JobOperator.START) {
                logger.info("start failover job,id:{}", id);
                jobManager.startSchedule(id);
            } else if (JobManager.JobOperator.valueOf(operator) == JobManager.JobOperator.RESTART) {
                logger.info("restart failover job,id:{}", id);
                jobManager.restartSchedule(id);
            } else if (JobManager.JobOperator.valueOf(operator) == JobManager.JobOperator.STOP_OR_START) {
                logger.info("local failover,id:{}", id);
                jobManager.restartSchedule(id);
            }
            return;
        }

        if (JobManager.JobOperator.valueOf(operator) == JobManager.JobOperator.NONE) {
            logger.info("node changed,do not exe any operator,operator:{}", operator);
            return;
        }

        //执行主机为本机
        if (StringUtils.equals(Utils.getHostName(), executeHost)) {
            if (JobManager.JobOperator.valueOf(operator) == JobManager.JobOperator.STOP) {
                logger.info("stop local job,id:{}", id);
                jobManager.stopSchedule(id);
            } else if (JobManager.JobOperator.valueOf(operator) == JobManager.JobOperator.START) {
                if (NumberUtils.toShort(initiateMode) == JobManager.AVAILABLE) {
                    logger.info("start local job,id:{}", id);
                    jobManager.startSchedule(id);
                }
            } else if (JobManager.JobOperator.valueOf(operator) == JobManager.JobOperator.RESTART) {
                if (NumberUtils.toShort(initiateMode) == JobManager.AVAILABLE) {
                    logger.info("restart local job,id:{}", id);
                    jobManager.restartSchedule(id);
                }
            } else if (JobManager.JobOperator.valueOf(operator) == JobManager.JobOperator.STOP_OR_START) {
                if (NumberUtils.toShort(initiateMode) == JobManager.AVAILABLE) {
                    logger.info("start local job,id:{}", id);
                    jobManager.restartSchedule(id);
                } else {
                    logger.info("stop local job,id:{}", id);
                    jobManager.stopSchedule(id);
                }
            }
        } else {
            //执行主机非本机
            if (JobManager.JobOperator.valueOf(operator) == JobManager.JobOperator.STOP_OR_START) {
                logger.info("host change,stop local job,id:{}", id);
                jobManager.stopSchedule(id);
            } else if (JobManager.JobOperator.valueOf(operator) == JobManager.JobOperator.STOP) {
                logger.info("stop local job if exist,id:{}", id);
                jobManager.stopSchedule(id);
            }
        }
    }

    private void taskNodeAdd(String nodePath) {
        logger.info("enter task node add event,nodePath:{}", nodePath);
        if (taskNodeMap.containsKey(nodePath)) {
            logger.info("existed node,ignore child add event.");
            return;
        }

        String data = zkClient.getData(nodePath);
        if (StringUtils.isBlank(data)) {
            logger.warn("get zk data error,ignore child add event.");
            return;
        }
        logger.info("task node add event,get zk data:{}", data);

        JSONObject jsonObject = JSON.parseObject(data);
        String operator = jsonObject.getString("operator");
        //广播操作为NONE,则无需操作.
        if (JobManager.JobOperator.valueOf(operator) == JobManager.JobOperator.NONE) {
            return;
        }

        logger.info("do new task add logic,set Node Listener and start task if ok.");
        String executeHost = jsonObject.getString("executeHost");
        short initiateMode = NumberUtils.toShort(jsonObject.getString("initiateMode"));
        long id = Long.parseLong(jsonObject.getString("id"));

        if (StringUtils.equals(Utils.getHostName(), executeHost) && initiateMode == JobManager.AVAILABLE) {
            //新增job,如果是启动状态则启动
            jobManager.startSchedule(id);
        }
        taskNodeMap.put(nodePath, id);
    }

    /**
     * 监听'/monitor/task/service_available'节点下的临时子节点的添加和删除事件
     * 监听在其他机器上执行的任务,且本机在机器执行列表中,心跳停止,机器连接中断.
     *
     * @return PathChildrenCacheListener
     */
    private PathChildrenCacheListener serviceNodeListener() {
        return new PathChildrenCacheListener() {
            @Override
            public void childEvent(CuratorFramework client, PathChildrenCacheEvent event) throws Exception {
                if (event == null || event.getData() == null || event.getData().getPath() == null) {
                    logger.warn("PathChildrenCacheListener event is null.");
                    return;
                }

                //实现节点down掉监控
                String path = event.getData().getPath();
                StringBuilder alarmString = new StringBuilder(50);
                switch (event.getType()) {
                    case CHILD_ADDED:
                        logger.info("service node add,path:{}", path);
                        alarmString.append("service add!path:").append(path);
                        doServiceNodeAdd(path);
                        break;
                    case CHILD_REMOVED:
                        logger.info("service node removed,path:{}", path);
                        alarmString.append("service down!path:").append(path);
                        doServiceNodeRemove(path);
                        break;
                    case CONNECTION_RECONNECTED:
                        alarmString.append("connection_reconnected!path:").append(path);
                        logger.warn("service transport reconnected.");
                        break;
                    case CONNECTION_LOST:
                        alarmString.append("connection_lost!path:").append(path);
                        logger.warn("service transport lost.");
                        break;
                    default:
                        break;
                }
            }
        };
    }


    /**
     * 节点被移除时的一系列操作
     *
     * @param path 节点路径
     */
    private void doServiceNodeRemove(final String path) {
        if (StringUtils.isBlank(path)) {
            return;
        }

        final String host = path.substring(path.lastIndexOf('/') + 1);
        if (StringUtils.equals(host, Utils.getHostName())) {
            //本机host,重新注册上去
            if (!zkClient.checkNodeExisted(path)) {
                zkClient.createNode(path, CreateMode.EPHEMERAL);
            }
            return;
        }

        final List<JobInfo> nonLocalTaskList = jobInfoService.getJobInfoForFailover(host);
        if (nonLocalTaskList.isEmpty()) {
            logger.info("no contains local running task,ignore failover!");
            //没有包含本机的failover任务
            return;
        }

        //放入一个空的list
        removeNodeMap.put(path, new ArrayList<JobInfo>());
        if (service == null) {
            service = Executors.newCachedThreadPool();
        }
        service.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    TimeUnit.MILLISECONDS.sleep(tolerateTime);
                    if (!removeNodeMap.containsKey(path)) {
                        return;
                    }

                    doFailoverTask(path, host);
                } catch (Exception e) {
                    logger.error("executor error.", e);
                }
            }
        });
    }

    /**
     * 处理节点down掉后,将down掉的service中所有任务中hostList包含localHost的任务,在local启动执行.
     *
     * @param path 被移除的节点路径
     */
    private void doFailoverTask(String path, String host) {
        //超过failover设定最大时间,执行failover
        logger.info("exceed tolerate time,execute failover,path:{}", path);
        //先拿到执行权
        InterProcessMutex lock = new InterProcessMutex(zkClient.getZkCuratorClient(), SERVICE_AVAILABLE_LOCK);
        try {
            if (!lock.acquire(lockAcquireTime, TimeUnit.MILLISECONDS)) {
                logger.info("lock:{} request failure.participant:{}", SERVICE_AVAILABLE_LOCK, lock.getParticipantNodes().toString());
                return;
            }
            logger.info("lock:{} request lock success.", SERVICE_AVAILABLE_LOCK);

            //获取最新的任务状态
            List<JobInfo> taskSchedules = jobInfoService.getJobInfoForFailover(host);
            for (JobInfo jobInfo : taskSchedules) {
                if (StringUtils.isNotBlank(jobInfo.getFailExecuteHost())) {
                    //如果该任务已经有机器执行,则跳过
                    logger.info("task has been failover,failover host:{}", jobInfo.getFailExecuteHost());
                    continue;
                }

                //如果任务没有执行,则修改为本机,并启动执行
                if (jobInfo.getInitiateMode() == JobManager.AVAILABLE) {
                    jobInfoService.modifyFailExecuteHost(jobInfo.getId(), Utils.getHostName());
                    //更新zk
                    jobInfo.setFailExecuteHost(Utils.getHostName());
                    String jobJson = JobListener.createJobJson(jobInfo, JobManager.JobOperator.NONE);
                    String nodePath = JobListener.SERVICE_UPDATE_PATH + "/" + jobInfo.getJobName();
                    zkClient.setData(nodePath, jobJson.getBytes());

                    jobManager.initJobScheduler(jobInfo).startAsync().awaitRunning();
                    //加入到removeMap中
                    removeNodeMap.get(path).add(jobInfo);
                    logger.info("local take over and exe failover task:{}", jobInfo.toString());
                }
            }
        } catch (Exception e) {
            logger.error("execute failover error,path:{}", path, e);
        } finally {
            try {
                lock.release();
            } catch (Exception e) {
                logger.error("release zk update lock exception.", e);
            }
        }
    }

    /**
     * 当节点复活,关闭本地执行的复活节点的任务.
     *
     * @param path 复活节点路径
     */
    private void doServiceNodeAdd(String path) {
        if (removeNodeMap.containsKey(path)) {
            logger.info("close task and remove item,path:{}", path);
            //如果已经failover,则先关闭任务,再移除
            List<JobInfo> taskSchedules = removeNodeMap.get(path);
            for (JobInfo jobInfo : taskSchedules) {
                logger.info("remove and kill failover task:{}", jobInfo.toString());
                jobManager.killSchedule(jobInfo.getId());

                //如果failExecuteHost不空,则置空
                if (StringUtils.isNotBlank(jobInfo.getFailExecuteHost())) {
                    jobInfoService.modifyFailExecuteHost(jobInfo.getId(), "");
                }
            }
            removeNodeMap.remove(path);
        }
    }

    public void close() {
        logger.info("close zookeeper task listener.");
        isInListening = false;
        try {
            zkClient.closePathChildrenCache(SERVICE_UPDATE_PATH);
            zkClient.closePathChildrenCache(SERVICE_AVAILABLE_PATH);
            taskNodeMap.clear();
        } catch (Exception e) {
            logger.error("close JobListener exception.", e);
        }
    }

    public boolean isInListening() {
        return isInListening;
    }
}
