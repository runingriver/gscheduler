package org.gscheduler.web.controller;

import java.util.List;

import javax.annotation.Resource;

import org.gscheduler.entity.JobInfo;
import org.gscheduler.service.jober.JobManager;
import org.gscheduler.service.task.JobInfoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

/**
 * 任务调度交互控制
 */
@Controller
public class JobInfoController {
    private static final Logger logger = LoggerFactory.getLogger(JobInfoController.class);

    @Resource
    JobInfoService jobInfoService;

    @Resource
    JobManager jobManager;

    // 列出所有任务
    @RequestMapping(value = "/job/list")
    public ModelAndView jobList() {
        ModelAndView model = new ModelAndView("job_list");
        List<JobInfo> jobList = jobInfoService.getAllJobInfo();
        model.addObject("jobList", jobList);
        return model;
    }

    // 修改任务
    @RequestMapping(value = "/job/modify/{id}")
    public ModelAndView modifyJob(@PathVariable("id") long id) {
        JobInfo job = jobInfoService.getJobInfoById(id);
        ModelAndView model = new ModelAndView("job_modify");
        model.addObject("job", job);
        return model;
    }

    // 更新任务
    @RequestMapping(value = "/job/update")
    public String updateJob(@ModelAttribute("jobSchedule") JobInfo jobInfo) {
        logger.info("手动,更新任务:{}...", jobInfo.getJobName());
        // 修改数据库
        trimFields(jobInfo);
        jobInfoService.modifyJobInfo(jobInfo);
        jobManager.updateSchedule(jobInfo.getId());
        return "redirect:/job/list";
    }

    private void trimFields(@ModelAttribute("jobInfo") JobInfo jobInfo) {
        jobInfo.setJobClass(jobInfo.getJobClass().trim());
        jobInfo.setConfigParameter(jobInfo.getConfigParameter().trim());
        jobInfo.setCrontab(jobInfo.getCrontab().trim());
        jobInfo.setHostList(jobInfo.getHostList().trim());
        jobInfo.setExecuteHost(jobInfo.getExecuteHost().trim());
    }

    // 保存定时任务
    @RequestMapping(value = "/job/save")
    public String addJob(@ModelAttribute("jobInfo") JobInfo jobInfo) {
        trimFields(jobInfo);
        jobInfoService.saveJobInfo(jobInfo);
        return "redirect:/job/list";
    }

    // 删除一个任务记录
    @RequestMapping(value = "/job/delete/{id}")
    public String deleteJob(@PathVariable("id") long id) {
        logger.info("手动,删除任务:{}...", id);
        jobManager.stopSchedule(id);
        jobInfoService.removeJobInfo(id);
        return "redirect:/job/list";
    }

    // 停止一个任务
    @RequestMapping(value = "/job/stop/{id}")
    public String stopJob(@PathVariable("id") long id) {
        logger.info("手动,停止任务:{}...", id);
        jobManager.stopSchedule(id);
        return "redirect:/job/list";
    }

    // 停止所有任务
    @RequestMapping(value = "/job/close/all")
    public String stopAllJob() {
        logger.info("手动,停止所有任务...");
        jobManager.stopAllScheduler();
        return "redirect:/job/list";
    }

    // 立即执行任务
    @RequestMapping(value = "/job/execute/{id}")
    public String executeJob(@PathVariable("id") long id) {
        logger.info("手动,开启任务:{}...", id);
        jobManager.startSchedule(id);
        return "redirect:/job/list";
    }

    /*-------------------仅用作跳转-----------------------------*/
    @RequestMapping(value = "/job/add")
    public String addJob() {
        return "/job_add";
    }

    @RequestMapping(value = "/job/log")
    public String log() {
        return "/job_log";
    }

    @RequestMapping(value = "/job/about")
    public String about() {
        return "/job_about";
    }

    // 处理所有未知的和静态资源的请求
    @RequestMapping(value = "/")
    public String index() {
        return "redirect:/job/list";
    }
    /*-------------------end:仅用作跳转-----------------------------*/
}
