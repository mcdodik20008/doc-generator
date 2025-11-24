package com.bftcom.docgenerator.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.scheduling.config.ScheduledTaskRegistrar

@Configuration
//@EnableScheduling
class SchedulerConfig : SchedulingConfigurer {

    override fun configureTasks(taskRegistrar: ScheduledTaskRegistrar) {
        val scheduler = ThreadPoolTaskScheduler()
        scheduler.poolSize = POOL_SIZE
        scheduler.setThreadNamePrefix("scheduled-task-")
        scheduler.initialize()
        taskRegistrar.setTaskScheduler(scheduler)
    }

    companion object {
        private const val POOL_SIZE = 5
    }
}