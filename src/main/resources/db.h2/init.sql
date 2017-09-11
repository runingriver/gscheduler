DROP TABLE IF EXISTS job_info;
CREATE TABLE job_info(
  id INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键id',
  task_name VARCHAR(30) NOT NULL DEFAULT '' COMMENT '任务执行名称',
  task_class VARCHAR(30) NOT NULL DEFAULT '' COMMENT '任务调用的类名',
  parent_name VARCHAR(255) NOT NULL DEFAULT '' COMMENT '任务调用的类名',
  sub_task VARCHAR(255) NOT NULL DEFAULT '' COMMENT '任务调用的类名',
  job_version VARCHAR(255) NOT NULL DEFAULT '' COMMENT '任务调用的类名',
  config_parameter VARCHAR(255) NOT NULL DEFAULT '' COMMENT '任务配置参数',
  crontab VARCHAR(50) NOT NULL DEFAULT '' COMMENT '执行时间的正则',
  initiate_mode TINYINT NOT NULL DEFAULT 0 COMMENT '任务启用情况,1-启用,0-禁用',
  host_list VARCHAR(255) NOT NULL DEFAULT '' COMMENT '主机列表',
  execute_host VARCHAR(255) NOT NULL DEFAULT '' COMMENT '指定执行的机器',
  fail_execute_host VARCHAR(255) NOT NULL DEFAULT '' COMMENT '指定执行的机器',
  version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '实时更新状态,保证在运行状态,乐观锁',
  execute_status TINYINT NOT NULL DEFAULT -1 COMMENT '任务执行情况,-1:未执行,0:执行失败,1:运行中,2:成功',
  execute_time  VARCHAR(10) NOT NULL DEFAULT '0' COMMENT '最近任务执行时长,单位秒',
  last_execute_time TIMESTAMP NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '上一次执行时间',
  next_execute_time TIMESTAMP NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '下一次执行时间',
  description VARCHAR(255) NOT NULL DEFAULT '' COMMENT '任务描述',
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uniq_task_name (task_name)
);

INSERT INTO job_info (task_name, task_class, crontab, initiate_mode, host_list, execute_host, execute_status , description)
    VALUES ('demo job','demoJob','30 */5 * * * *',1,'zongzhe','zongzhe','1','测试任务');