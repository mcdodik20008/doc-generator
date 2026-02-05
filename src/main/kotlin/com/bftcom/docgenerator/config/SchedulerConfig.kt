package com.bftcom.docgenerator.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.scheduling.config.ScheduledTaskRegistrar

@Configuration
// TODO: @EnableScheduling закомментирован - если нужен, раскомментировать, если нет - удалить класс
//@EnableScheduling
class SchedulerConfig : SchedulingConfigurer {

    override fun configureTasks(taskRegistrar: ScheduledTaskRegistrar) {
        val scheduler = ThreadPoolTaskScheduler()
        // TODO: POOL_SIZE = 5 hardcoded - должен быть в application.yml конфигурации
        // TODO: Для больших нагрузок 5 потоков может быть недостаточно
        scheduler.poolSize = POOL_SIZE
        scheduler.setThreadNamePrefix("scheduled-task-")
        scheduler.initialize()
        taskRegistrar.setTaskScheduler(scheduler)
    }

    companion object {
        private const val POOL_SIZE = 5
    }
}