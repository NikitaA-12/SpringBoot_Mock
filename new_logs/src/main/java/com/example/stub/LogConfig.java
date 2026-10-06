package com.example.stub;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CommonsRequestLoggingFilter;
@Configuration
public class LogConfig {
    @Bean
    public CommonsRequestLoggingFilter requestLoggingFilter() {
        CommonsRequestLoggingFilter loggingFilter = 
                new CommonsRequestLoggingFilter();
        // Включить логирование query параметров
        loggingFilter.setIncludeQueryString(true);
        // Включить логирование тела запроса
        loggingFilter.setIncludePayload(true);
        // Максимальная длина тела для логирования
        loggingFilter.setMaxPayloadLength(10000);
        // Не логировать заголовки
        loggingFilter.setIncludeHeaders(false);
        // Префикс сообщения в логе
        loggingFilter.setAfterMessagePrefix("REQUEST DATA: ");
        return loggingFilter;
    }
}
