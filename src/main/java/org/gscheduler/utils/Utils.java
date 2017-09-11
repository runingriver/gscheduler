package org.gscheduler.utils;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;

public class Utils {
    private static final Logger logger = LoggerFactory.getLogger(Utils.class);
    private static String hostName = null;

    /**
     * 获取本地主机名
     *
     * @return 主机名查询方命令:hostname,l-sms.monitor2.wap.cn6
     */
    public static String getHostName() {
        if (hostName != null) {
            return hostName;
        }

        try {
            hostName = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            logger.error("get the host name failure.!", e);
        }

        return hostName;
    }

    /**
     * 判读Integer是否不为null且正数
     *
     * @param number the number
     * @return the boolean 正数-true;null or 负数-false
     */
    public static boolean isPositiveNumber(Integer number) {
        if (number != null && number > 0) {
            return true;
        }
        return false;
    }

    /**
     * 判读Long是否不为null且正数
     *
     * @param number the number
     * @return the boolean 正数-true;null or 负数-false
     */
    public static boolean isPositiveNumber(Long number) {
        if (number != null && number > 0) {
            return true;
        }
        return false;
    }

    /**
     * 将String转为Date
     *
     * @param dateString yyyy-MM-dd HH:mm:ss
     * @return Date对象
     */
    public static Date stringToDate(String dateString) {
        Preconditions.checkArgument(StringUtils.isNotBlank(dateString), "argument illegal.");
        if (dateString.contains(".")) {
            dateString = dateString.substring(0, dateString.indexOf('.'));
        }

        DateTime dateTime = DateTime.parse(dateString, DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss"));
        return dateTime.toDate();
    }

    /**
     * 将Date转为String
     *
     * @param date 时间对象
     * @return yyyy-MM-dd HH:mm:ss
     */
    public static String dateToString(Date date) {
        return dateToString(date, "yyyy-MM-dd HH:mm:ss");
    }

    /**
     * 将Date转为String
     *
     * @param date         Date
     * @param defaultValue 默认时间
     * @param format       null-yyyy-MM-dd HH:mm:ss
     * @return
     */
    public static String dateToString(Date date, String defaultValue, String format) {
        if (StringUtils.isBlank(format)) {
            format = "yyyy-MM-dd HH:mm:ss";
        }
        String result = defaultValue;
        try {
            result = dateToString(date, format);
        } catch (Exception e) {
            // 异常则使用默认字符串
            // logger.error("parse date exception");
        }
        return result;
    }

    /**
     * 将时间转换成指定的格式
     */
    public static String dateToString(Date date, String format) {
        Preconditions.checkArgument(date != null && StringUtils.isNotBlank(format), "argument illegal.");
        DateTime dateTime = new DateTime(date);
        return dateTime.toString(format);
    }



}
