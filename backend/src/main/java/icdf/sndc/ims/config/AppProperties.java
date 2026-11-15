package icdf.sndc.ims.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 集中管理的應用程式設定（application.yml 的 app.*）。 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(String timezone, String baseUrl, Paging paging) {

    public record Paging(int defaultSize, int maxSize) {}

    public AppProperties {
        if (timezone == null || timezone.isBlank()) {
            timezone = "UTC";
        }
        if (paging == null) {
            paging = new Paging(20, 100);
        }
    }
}
