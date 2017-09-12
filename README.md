# 简介
gscheduler基于guava,spring开发的一个致力于简单易用的任务调度系统,具备任务实时监控,日志查看,任务控制等基本功能.
整个系统采用Spring+SpringMVC+mybatis+Guava+bootstrap等框架,对任务调度核心功能进行模拟展示!

# 特点
当今最流行的任务调度莫过于quartz!为任务调度提供了一整套可靠的解决方案,但是当需要进行分布式部署和持久化时需要建立12张表.
对于小型任务调度需求,使用12张表可能是一件Uncomfortable的事情,所以gscheduler的目标就克服quartz的Uncomfortable,使任务调度变得简单易用可靠!

gscheduler特点如下:
1. 只需要在数据库中建立一张表,核心类只有4个,且非常简单!
2. 提供简单页面查看任务和日志,并可以控制任务的添加,修改,删除,执行,停止!
3. 提供任务监听和分布式下的failover功能.
4. 对于简单的任务调度需求,可以直接将jober中的类迁移到您的项目中,个性化定制!


缺点:
1. 强依赖与guava,利用guava提供的并发包控制任务,对spring的依赖可以去抽离,因为当前所有的开发都是基于spring,所以融合进了spring.
2. 暂未支持依赖调度.

# 运行
1. 无需配置,jdk1.7+maven可以直接运行！
cd到工程目录,命令行运行: mvn clean tomcat:run
访问：http://localhost:8080/gscheduler/job/list
2. idea运行,clone下来后,配置`Edit Configuration...`,然后直接运行.
如果,需要多台机器运行,启动zk,实现一致性,配置`config.properties`中相关参数!

# 使用
1. 继承JobProcess类,实现execute方法,在execute方法中填入业务逻辑,添加@Component注解将类交给spring管理.
2. 在前台页面添加相关任务,类名一行以首字母小写的类名(spring bean默认命名方式,eg:类DemoTest -> demoTest).
3. 时间正则支持与spring cron相同的表达式,也支持`[value]/[min,hour,day] [value]/[min,hour,day]`的格式.
eg:(1/min 5/min,延迟1分钟后执行,每5分钟执行一次),(1/hour,2/day)延迟1小时后执行,每两天执行一次.
4. done


# 备注
工作之中有感而发,尚且是我闲暇之余的重复造轮子!for up,for open source!

# 后续计划
1. 提供基于注解的任务定义模式
2. 重写内核,使得其不再依赖guava,不强依赖spring.
3. 实现任务依赖调度
4. 实现任务执行日志

# 简单示例截图

![image](https://github.com/runingriver/gscheduler/blob/master/images/gschedule_demo.png)