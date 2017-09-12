package org.gscheduler.service.task.impl;

import org.gscheduler.entity.JobInfo;
import org.gscheduler.service.task.JobInfoService;
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
public class JobInfoServiceImplTest {
    private static final Logger logger = LoggerFactory.getLogger(JobInfoServiceImplTest.class);

    @Resource
    JobInfoService jobInfoService;

    @Test
    public void getAllJobInfo() throws Exception {
        List<JobInfo> infoList = jobInfoService.getAllJobInfo();
        for (JobInfo jobInfo : infoList) {
            logger.info("{}", jobInfo);
        }
    }

}