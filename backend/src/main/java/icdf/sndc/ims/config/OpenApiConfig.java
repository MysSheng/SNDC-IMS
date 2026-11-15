package icdf.sndc.ims.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI 加上「Authorize」按鈕，可貼上登入取得的 JWT 測試受保護 API。 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI sndcOpenApi() {
        return new OpenAPI()
                .info(new Info().title("SNDC-IMS API").version("v1"))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
