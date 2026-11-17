package com.techie.microservices.user.config;

import com.techie.microservices.user.client.KeycloakAdminClient;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.time.Duration;

/**
 * Wires the {@link KeycloakAdminClient} declarative HTTP client. A client_credentials token for the
 * {@code user-service} Keycloak client is fetched and cached by Spring Security's authorized-client
 * manager and attached to every request by {@link OAuth2ClientHttpRequestInterceptor}. 3s connect/read
 * timeouts bound the call so a slow/unreachable Keycloak surfaces quickly (handled as 503 upstream).
 */
@Configuration
public class KeycloakClientConfig {

    public static final String REGISTRATION_ID = "keycloak-admin";

    @Value("${keycloak.base-url}")
    private String keycloakBaseUrl;

    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientService authorizedClientService) {
        var provider = OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build();
        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                clientRegistrationRepository, authorizedClientService);
        manager.setAuthorizedClientProvider(provider);
        return manager;
    }

    @Bean
    public KeycloakAdminClient keycloakAdminClient(OAuth2AuthorizedClientManager authorizedClientManager) {
        var bearerInterceptor = new OAuth2ClientHttpRequestInterceptor(authorizedClientManager);
        bearerInterceptor.setClientRegistrationIdResolver(request -> REGISTRATION_ID);

        RestClient restClient = RestClient.builder()
                .baseUrl(keycloakBaseUrl)
                .requestFactory(clientHttpRequestFactory())
                .requestInterceptor(bearerInterceptor)
                .build();

        var adapter = RestClientAdapter.create(restClient);
        return HttpServiceProxyFactory.builderFor(adapter).build().createClient(KeycloakAdminClient.class);
    }

    private ClientHttpRequestFactory clientHttpRequestFactory() {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(3))
                .withReadTimeout(Duration.ofSeconds(3));
        return ClientHttpRequestFactoryBuilder.simple().build(settings);
    }
}
