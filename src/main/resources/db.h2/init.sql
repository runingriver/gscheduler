DROP TABLE IF EXISTS task_schedule;
CREATE TABLE task_schedule(
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键id',
  task_name VARCHAR(30) NOT NULL DEFAULT '' COMMENT '任务执行名称',
  task_class VARCHAR(30) NOT NULL DEFAULT '' COMMENT '任务调用的类名',
  config_parameter VARCHAR(255) NOT NULL DEFAULT '' COMMENT '任务配置参数:网关,账号等',
  crontab VARCHAR(50) NOT NULL DEFAULT '' COMMENT '执行时间的正则',
  initiate_mode TINYINT NOT NULL DEFAULT 0 COMMENT '任务启用情况,1-启用,0-禁用',
  host_list VARCHAR(255) NOT NULL DEFAULT '' COMMENT '主机列表',
  execute_host VARCHAR(50) NOT NULL DEFAULT '' COMMENT '指定执行的机器',
  version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '实时更新状态,保证在运行状态,乐观锁',
  execute_status TINYINT NOT NULL DEFAULT -1 COMMENT '任务执行情况,-1:未执行,0:执行失败,1:执行中,2:成功',
  execute_time  VARCHAR(10) NOT NULL DEFAULT '0' COMMENT '最近任务执行时长,单位秒',
  last_execute_time DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '上一次执行时间',
  next_execute_time DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '下一次执行时间',
  description VARCHAR(255) NOT NULL DEFAULT '' COMMENT '任务描述',
  update_time TIMESTAMP NOT NULL DEFAULT current_timestamp ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uniq_task_name (task_name)
)ENGINE = InnoDB DEFAULT CHARSET = utf8 COMMENT '任务执行计划表';