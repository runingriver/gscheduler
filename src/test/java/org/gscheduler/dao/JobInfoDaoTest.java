package org.gscheduler.dao;

import org.gscheduler.entity.JobInfo;
import org.gscheduler.utils.Utils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:application-context.xml")
@Transactional
public class JobInfoDaoTest {
    private static final Logger logger = LoggerFactory.getLogger(JobInfoDaoTest.class);

    @Resource
    JobInfoDao jobInfoDao;

    @Test
    public void selectAllJobInfo() throws Exception {
        List<JobInfo> jobInfoList = jobInfoDao.selectAllJobInfo();
        for (JobInfo jobInfo : jobInfoList) {
            logger.info("{}", jobInfo.toString());
        }
    }

    @Test
    public void selectJobInfoForFailover() throws Exception {
        String hostName = Utils.getHostName();
        logger.info("local host:{}", hostName);
        List<JobInfo> jobInfoList = jobInfoDao.selectJobInfoForFailover(hostName, hostName);
        for (JobInfo jobInfo : jobInfoList) {
            logger.info("{}", jobInfo.toString());
        }
    }

    @Test
    public void selectJobInfoByClassName() throws Exception {
        JobInfo jobInfo = jobInfoDao.selectJobInfoByClassName("testJob");
        logger.info("结果:{}", jobInfo.toString());
    }

    @Test
    public void selectJobInfoByHostname() throws Exception {
        List<JobInfo> jobInfoList = jobInfoDao.selectJobInfoByHostname("localhost");
        for (JobInfo jobInfo : jobInfoList) {
            logger.info("{}", jobInfo.toString());
        }
    }

    @Test
    public void selectJobInfoById() throws Exception {
        JobInfo jobInfo = jobInfoDao.selectJobInfoById(1);
        Assert.assertEquals(jobInfo.getJobClass(), "demoJob");
        Assert.assertEquals(jobInfo.getId(), 1);
    }

    @Test
    public void insertJobInfo() throws Exception {
        JobInfo jobInfo = createTestJobInfo();
        Integer integer = jobInfoDao.insertJobInfo(jobInfo);
        logger.info("结果:{}", integer);
        JobInfo info = jobInfoDao.selectJobInfoByJobName("test_job");
        Assert.assertEquals(info.getJobName(), "test_job");
    }

    private JobInfo createTestJobInfo() {
        JobInfo jobInfo = new JobInfo();
        jobInfo.setConfigParameter("no");
        jobInfo.setCrontab("1/min 2/min");
        jobInfo.setExecuteHost("localhost");
        jobInfo.setExecuteStatus((short) 1);
        jobInfo.setHostList("localhost");
        jobInfo.setInitiateMode((short) 0);
        jobInfo.setJobClass("testJob");
        jobInfo.setJobName("test_job");
        return jobInfo;
    }

    @Test
    public void deleteJobInfoById() throws Exception {
        JobInfo jobInfo = createTestJobInfo();
        jobInfoDao.insertJobInfo(jobInfo);
        JobInfo info = jobInfoDao.selectJobInfoByJobName(jobInfo.getJobName());

        jobInfoDao.deleteJobInfoById(info.getId());
        JobInfo info2 = jobInfoDao.selectJobInfoByJobName("test_job");
        Assert.assertEquals(null, info2);
    }

}