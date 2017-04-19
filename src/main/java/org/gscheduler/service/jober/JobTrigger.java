package org.gscheduler.service.jober;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.support.CronSequenceGenerator;

import com.google.common.base.Splitter;
import com.google.common.util.concurrent.AbstractScheduledService;

/**
 * 任务触发器,支持两种表达式: 1. 1/min 1/hour(延迟1分钟1小时执行一次);1/min 5/min(延迟1分钟5分钟执行一次);5/min 1/day(延迟5分钟1天执行一次) 2.
 * Spring的cron表达式,使用见CronSequenceGenerator说明
 */
public class JobTrigger {
    private static final Logger logger = LoggerFactory.getLogger(JobTrigger.class);
    private boolean isCronExpression;

    private CronTrigger cronTrigger;
    private PeriodTrigger periodTrigger;

    public JobTrigger(String cronExpression) {
        List<String> cronList = Splitter.on(' ').omitEmptyStrings().splitToList(cronExpression);
        this.isCronExpression = cronList.size() == 2 ? false : true;
        if (isCronExpression) {
            // cron表达式
            this.cronTrigger = new CronTrigger(cronExpression);
        } else {
            // 自定义表达式
            this.periodTrigger = new PeriodTrigger(cronExpression);
        }
    }

    public AbstractScheduledService.Scheduler getScheduler() {
        if (isCronExpression) {
            // cron表达式
            return cronTrigger;
        } else {
            // 自定义表达式
            return periodTrigger.getScheduler();
        }
    }

    public Date getNextExecutionDate() {
        if (isCronExpression) {
            return cronTrigger.getNextExecutionDate();
        } else {
            return DateUtils.addSeconds(new Date(), (int) periodTrigger.period);
        }
    }

    // 解析cron表达式,每次执行完任务都要重新获取并设置延迟,每个任务对应一个CronTrigger实例
    private final static class CronTrigger extends AbstractScheduledService.CustomScheduler {
        private final CronSequenceGenerator sequenceGenerator;
        private Date nextExecutionDate;

        private CronTrigger(String cronExpression) {
            this.sequenceGenerator = new CronSequenceGenerator(cronExpression);
        }

        public Date getNextExecutionDate() {
            return sequenceGenerator.next(nextExecutionDate);
        }

        protected Schedule getNextSchedule() throws Exception {
            Date now = new Date();
            nextExecutionDate = sequenceGenerator.next(now);

            // 如果timeDiff<0,ScheduleExecutorService会自动处理:将delay置0,立即执行
            long timeDiff = nextExecutionDate.getTime() - now.getTime();
            return new Schedule(timeDiff, TimeUnit.MILLISECONDS);
        }
    }

    // 用于解析自定义表达式,用于周期执行,线程安全
    private final static class PeriodTrigger {
        // 是否固定周期执行任务
        private static final boolean fixedRate = true;
        // 任务首次启动执行的延迟,单位秒
        private final long delay;
        // 任务周期运行的时长,单位秒
        private final long period;

        public PeriodTrigger(String cronExpression) {
            // 不支持cron类型的周期执行表达式解析,如果实例化对象的时候有异常会抛给JobScheduler.init方法拦截
            ParseExpression parse = ParseExpression.parse(cronExpression);
            this.delay = parse.delay;
            this.period = parse.period;
        }

        private AbstractScheduledService.Scheduler getScheduler() {
            if (fixedRate) {
                // 固定周期执行
                return AbstractScheduledService.Scheduler.newFixedRateSchedule(delay, period, TimeUnit.SECONDS);
            }
            return AbstractScheduledService.Scheduler.newFixedDelaySchedule(delay, period, TimeUnit.SECONDS);
        }
    }

    /**
     * 解析定时任务执行表达式,表达式格式定义为(delay/TimeUnit period/TimeUnit) 目前只支持周期执行的定时任务.
     */
    public static class ParseExpression {
        private static final String MINUTE = "min";
        private static final String HOUR = "hour";
        private static final String DAY = "day";
        private static final String MONTH = "month";
        private long delay;
        private long period;

        public static ParseExpression parse(String expression) {
            if (!checkExpression(expression)) {
                throw new RuntimeException("定时任务时间表达式错误!");
            }
            ParseExpression parseExpression = new ParseExpression();
            String delayString = expression.substring(0, expression.indexOf(' '));
            String periodString = expression.substring(expression.indexOf(' ') + 1, expression.length());
            long delayTime = parseStringToTime(delayString);
            if ((!StringUtils.equals(delayString.substring(0, delayString.indexOf('/')), "0") && delayTime == 0)
                    || delayTime < 0) {
                logger.error("延迟时间设置有误,delayString:{}", delayString);
                throw new RuntimeException("延迟时间设置有误.");
            }
            long periodTime = parseStringToTime(periodString);
            if (periodTime <= 0) {
                logger.error("执行周期参数设置错误,periodString:{}", periodString);
                throw new RuntimeException("执行周期参数设置错误.");
            }
            parseExpression.delay = delayTime;
            parseExpression.period = periodTime;
            return parseExpression;
        }

        // 将时间转换成秒
        private static long parseStringToTime(String formattedString) {
            long time = NumberUtils.toLong(formattedString.substring(0, formattedString.indexOf('/')).trim());
            String unit = formattedString.substring(formattedString.indexOf('/') + 1, formattedString.length())
                    .toLowerCase().trim();

            if (StringUtils.equals(unit, MINUTE)) {
                return time * 60;
            } else if (StringUtils.equals(unit, DAY)) {
                return time * 24 * 60 * 60;
            } else if (StringUtils.equals(unit, HOUR)) {
                return time * 60 * 60;
            } else if (StringUtils.equals(unit, MONTH)) {
                return time * 30 * 24 * 60 * 60;
            }
            return 0;
        }

        /**
         * 检查输入字符是否合法
         *
         * @param expression 定时任务字符表达式
         * @return true-合法,false-非法
         */
        public static boolean checkExpression(String expression) {
            if (StringUtils.isBlank(expression)) {
                return false;
            }
            Pattern pattern = Pattern.compile("\\d*/\\D*\\s\\d*/\\D*");
            Matcher matcher = pattern.matcher(expression);
            return matcher.matches();
        }
    }

    // 对提供的两种类型的表达式进行检查
    public static boolean checkExpression(String expression) {
        // 检查并解析表达式
        boolean illegaled;
        List<String> cronList = Splitter.on(' ').omitEmptyStrings().trimResults().splitToList(expression);

        if (cronList.size() == 2) {
            // 自定义表达式
            illegaled = ParseExpression.checkExpression(expression);
            if (!illegaled) {
                return true;
            }
        } else if (cronList.size() == 6) {
            // cron表达式
            for (String s : cronList) {
                illegaled = StringUtils.containsAny(s, "*,-/?0123456789");
                if (!illegaled) {
                    return true;
                }
            }
        } else {
            return true;
        }
        return false;
    }

    private JobTrigger() {
    }
}
