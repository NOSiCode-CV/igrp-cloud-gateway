package cv.igrp.platform.igrpcloudgateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

@Configuration
public class GatewayRoutesConfig {

    private static final String SERVICE_NAME = "cadastro-service";
    private static final String REWRITE_PATH_PATTERN = "/" + SERVICE_NAME + "/(?<remaining>.*)";
    private static final String REPLACEMENT_PATTERN = "/${remaining}";
    private static final String LOAD_BALANCED_URI = "lb://" + SERVICE_NAME;

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // GET /contribuinte - List contribuintes
                .route("get_contribuinte", r -> r
                        .path("/" + SERVICE_NAME + "/contribuinte")
                        .and()
                        .method(HttpMethod.GET.name())
                        .filters(f -> f
                                .rewritePath(REWRITE_PATH_PATTERN, REPLACEMENT_PATTERN)
                        )
                        .uri(LOAD_BALANCED_URI)
                )
                // POST /contribuinte/criar - Create contribuinte
                .route("post_contribuinte_criar", r -> r
                        .path("/" + SERVICE_NAME + "/contribuinte/criar")
                        .and()
                        .method(HttpMethod.POST.name())
                        .filters(f -> f
                                .rewritePath(REWRITE_PATH_PATTERN, REPLACEMENT_PATTERN)
                        )
                        .uri(LOAD_BALANCED_URI)
                )
                // PUT /contribuinte/{contribuinteId}/alterar - Update contribuinte
                .route("put_contribuinte_alterar", r -> r
                        .path("/" + SERVICE_NAME + "/contribuinte/{contribuinteId}/alterar")
                        .and()
                        .method(HttpMethod.PUT.name())
                        .filters(f -> f
                                .rewritePath(REWRITE_PATH_PATTERN, REPLACEMENT_PATTERN)
                        )
                        .uri(LOAD_BALANCED_URI)
                )
                // POST /contribuinte/{contribuinteId}/suspender - Suspend contribuinte
                .route("post_contribuinte_suspender", r -> r
                        .path("/" + SERVICE_NAME + "/contribuinte/{contribuinteId}/suspender")
                        .and()
                        .method(HttpMethod.POST.name())
                        .filters(f -> f
                                .rewritePath(REWRITE_PATH_PATTERN, REPLACEMENT_PATTERN)
                        )
                        .uri(LOAD_BALANCED_URI)
                )
                // POST /contribuinte/{contribuinteId}/reativar - Reactivate contribuinte
                .route("post_contribuinte_reativar", r -> r
                        .path("/" + SERVICE_NAME + "/contribuinte/{contribuinteId}/reativar")
                        .and()
                        .method(HttpMethod.POST.name())
                        .filters(f -> f
                                .rewritePath(REWRITE_PATH_PATTERN, REPLACEMENT_PATTERN)
                        )
                        .uri(LOAD_BALANCED_URI)
                )
                // POST /contribuinte/{contribuinteId}/cessar - Terminate contribuinte
                .route("post_contribuinte_cessar", r -> r
                        .path("/" + SERVICE_NAME + "/contribuinte/{contribuinteId}/cessar")
                        .and()
                        .method(HttpMethod.POST.name())
                        .filters(f -> f
                                .rewritePath(REWRITE_PATH_PATTERN, REPLACEMENT_PATTERN)
                        )
                        .uri(LOAD_BALANCED_URI)
                )
                // GET /contribuinte/{contribuinteId}/emitir-declaracao - Issue declaration
                .route("get_contribuinte_emitir_declaracao", r -> r
                        .path("/" + SERVICE_NAME + "/contribuinte/{contribuinteId}/emitir-declaracao")
                        .and()
                        .method(HttpMethod.GET.name())
                        .filters(f -> f
                                .rewritePath(REWRITE_PATH_PATTERN, REPLACEMENT_PATTERN)
                        )
                        .uri(LOAD_BALANCED_URI)
                )
                // GET /contribuinte/{contribuinteId}/detalhes - Get contribuinte details
                .route("get_contribuinte_detalhes", r -> r
                        .path("/" + SERVICE_NAME + "/contribuinte/{contribuinteId}/detalhes")
                        .and()
                        .method(HttpMethod.GET.name())
                        .filters(f -> f
                                .rewritePath(REWRITE_PATH_PATTERN, REPLACEMENT_PATTERN)
                        )
                        .uri(LOAD_BALANCED_URI)
                )
                .build();
    }
}
