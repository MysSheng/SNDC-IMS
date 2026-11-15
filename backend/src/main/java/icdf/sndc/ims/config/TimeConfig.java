package icdf.sndc.ims.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/** Server 業務時區（APP_TIMEZONE）。相對日期查詢（如「今年」）需使用此 Clock。 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock businessClock(AppProperties props) {
        return Clock.system(ZoneId.of(props.timezone()));
    }
}
