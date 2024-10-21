/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2020-2030 郑庚伟 ZHENGGENGWEI (码匠君), <herodotus@aliyun.com> Licensed under the AGPL License
 *
 * This file is part of Dante Cloud.
 *
 * Dante Cloud is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Dante Cloud is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.herodotus.cn>.
 */

package org.dromara.dante.authentication.autoconfigure;

import cn.herodotus.engine.assistant.core.utils.ResourceResolver;
import cn.herodotus.engine.assistant.definition.constants.DefaultConstants;
import cn.herodotus.engine.oauth2.authentication.configurer.OAuth2AuthenticationProviderConfigurer;
import cn.herodotus.engine.oauth2.authentication.consumer.OAuth2AuthorizationCodeAuthenticationProviderConsumer;
import cn.herodotus.engine.oauth2.authentication.consumer.OAuth2ClientCredentialsAuthenticationProviderConsumer;
import cn.herodotus.engine.oauth2.authentication.customizer.OAuth2FormLoginConfigurerCustomizer;
import cn.herodotus.engine.oauth2.authentication.oidc.HerodotusOidcUserInfoMapper;
import cn.herodotus.engine.oauth2.authentication.properties.OAuth2AuthenticationProperties;
import cn.herodotus.engine.oauth2.authentication.provider.OAuth2ResourceOwnerPasswordAuthenticationConverter;
import cn.herodotus.engine.oauth2.authentication.provider.OAuth2SocialCredentialsAuthenticationConverter;
import cn.herodotus.engine.oauth2.authentication.response.DefaultOAuth2AuthenticationEventPublisher;
import cn.herodotus.engine.oauth2.authentication.response.OAuth2AuthenticationFailureResponseHandler;
import cn.herodotus.engine.oauth2.authentication.utils.OAuth2ConfigurerUtils;
import cn.herodotus.engine.oauth2.authorization.customizer.OAuth2ResourceServerConfigurerCustomer;
import cn.herodotus.engine.oauth2.authorization.customizer.OAuth2SessionManagementConfigurerCustomer;
import cn.herodotus.engine.oauth2.authorization.properties.OAuth2AuthorizationProperties;
import cn.herodotus.engine.oauth2.core.definition.service.ClientDetailsService;
import cn.herodotus.engine.oauth2.core.enums.Certificate;
import cn.herodotus.engine.oauth2.management.response.OAuth2AccessTokenResponseHandler;
import cn.herodotus.engine.oauth2.management.response.OAuth2DeviceVerificationResponseHandler;
import cn.herodotus.engine.oauth2.management.response.OidcClientRegistrationResponseHandler;
import cn.herodotus.engine.rest.condition.properties.EndpointProperties;
import cn.herodotus.engine.rest.protect.crypto.processor.HttpCryptoProcessor;
import cn.herodotus.engine.rest.protect.tenant.MultiTenantFilter;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.web.authentication.*;
import org.springframework.security.rsa.crypto.KeyStoreKeyFactory;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.UUID;

/**
 * <p>Description: 认证服务器配置 </p>
 * <p>
 * 1. 权限核心处理 {@link org.springframework.security.web.access.intercept.FilterSecurityInterceptor}
 * 2. 默认的权限判断 {@link org.springframework.security.access.vote.AffirmativeBased}
 * 3. 模式决策 {@link org.springframework.security.authentication.ProviderManager}
 *
 * @author : gengwei.zheng
 * @date : 2022/2/12 20:57
 */
@AutoConfiguration
public class AuthorizationServerAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationServerAutoConfiguration.class);

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain authorizationServerSecurityFilterChain(
            HttpSecurity httpSecurity,
            PasswordEncoder passwordEncoder,
            UserDetailsService userDetailsService,
            ClientDetailsService clientDetailsService,
            HttpCryptoProcessor httpCryptoProcessor,
            OidcClientRegistrationResponseHandler oidcClientRegistrationResponseHandler,
            OAuth2AuthenticationProperties oauth2AuthenticationProperties,
            OAuth2DeviceVerificationResponseHandler oauth2DeviceVerificationResponseHandler,
            OAuth2FormLoginConfigurerCustomizer oauth2FormLoginConfigurerCustomizer,
            OAuth2ResourceServerConfigurerCustomer oauth2ResourceServerConfigurerCustomer,
            OAuth2SessionManagementConfigurerCustomer oauth2sessionManagementConfigurerCustomer
    ) throws Exception {

        log.debug("[Herodotus] |- Bean [Authorization Server Security Filter Chain] Auto Configure.");

        SessionRegistry sessionRegistry = OAuth2ConfigurerUtils.getOptionalBean(httpSecurity, SessionRegistry.class);

        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer = new OAuth2AuthorizationServerConfigurer();
        httpSecurity.with(authorizationServerConfigurer, (configurer) -> {});

        OAuth2AuthenticationFailureResponseHandler errorResponseHandler = new OAuth2AuthenticationFailureResponseHandler();
        authorizationServerConfigurer.clientAuthentication(endpoint -> {
            endpoint.errorResponseHandler(errorResponseHandler);
            endpoint.authenticationProviders(new OAuth2ClientCredentialsAuthenticationProviderConsumer(httpSecurity, clientDetailsService));
        });
        authorizationServerConfigurer.authorizationEndpoint(endpoint -> {
            endpoint.errorResponseHandler(errorResponseHandler);
            endpoint.consentPage(DefaultConstants.AUTHORIZATION_CONSENT_URI);
        });
        authorizationServerConfigurer.deviceAuthorizationEndpoint(endpoint -> {
            endpoint.errorResponseHandler(errorResponseHandler);
            endpoint.verificationUri(DefaultConstants.DEVICE_ACTIVATION_URI);
        });
        authorizationServerConfigurer.deviceVerificationEndpoint(endpoint -> {
            endpoint.errorResponseHandler(errorResponseHandler);
            endpoint.consentPage(DefaultConstants.AUTHORIZATION_CONSENT_URI);
            endpoint.deviceVerificationResponseHandler(oauth2DeviceVerificationResponseHandler);
        });
        authorizationServerConfigurer.tokenEndpoint(endpoint -> {
            AuthenticationConverter authenticationConverter = new DelegatingAuthenticationConverter(
                    Arrays.asList(
                            new OAuth2AuthorizationCodeAuthenticationConverter(),
                            new OAuth2RefreshTokenAuthenticationConverter(),
                            new OAuth2ClientCredentialsAuthenticationConverter(),
                            new OAuth2DeviceCodeAuthenticationConverter(),
                            new OAuth2ResourceOwnerPasswordAuthenticationConverter(httpCryptoProcessor),
                            new OAuth2SocialCredentialsAuthenticationConverter(httpCryptoProcessor)));
            endpoint.accessTokenRequestConverter(authenticationConverter);
            endpoint.errorResponseHandler(errorResponseHandler);
            endpoint.accessTokenResponseHandler(new OAuth2AccessTokenResponseHandler(httpCryptoProcessor));
            endpoint.authenticationProviders(new OAuth2AuthorizationCodeAuthenticationProviderConsumer(httpSecurity, sessionRegistry));
        });
        authorizationServerConfigurer.tokenIntrospectionEndpoint(endpoint -> endpoint.errorResponseHandler(errorResponseHandler));
        authorizationServerConfigurer.tokenRevocationEndpoint(endpoint -> endpoint.errorResponseHandler(errorResponseHandler));
        authorizationServerConfigurer.oidc(oidc -> {
            oidc.clientRegistrationEndpoint(endpoint -> {
                endpoint.errorResponseHandler(errorResponseHandler);
                endpoint.clientRegistrationResponseHandler(oidcClientRegistrationResponseHandler);
            });
            oidc.userInfoEndpoint(userInfo -> userInfo
                    .userInfoMapper(new HerodotusOidcUserInfoMapper()));
        });

        RequestMatcher endpointsMatcher = authorizationServerConfigurer.getEndpointsMatcher();
        // 仅拦截 OAuth2 Authorization Server 的相关 endpoint
        httpSecurity.securityMatcher(endpointsMatcher)
                // 开启请求认证
                .authorizeHttpRequests(authorizeRequests -> authorizeRequests.anyRequest().authenticated())
                // 禁用对 OAuth2 Authorization Server 相关 endpoint 的 CSRF 防御
                .csrf(csrf -> csrf.ignoringRequestMatchers(endpointsMatcher))
                .formLogin(oauth2FormLoginConfigurerCustomizer)
                .sessionManagement(oauth2sessionManagementConfigurerCustomer)
                .addFilterBefore(new MultiTenantFilter(), AuthorizationFilter.class)
                .oauth2ResourceServer(oauth2ResourceServerConfigurerCustomer)
                .with(new OAuth2AuthenticationProviderConfigurer(sessionRegistry, passwordEncoder, userDetailsService, oauth2AuthenticationProperties), (configurer) -> {});

        // 这里增加 DefaultAuthenticationEventPublisher 配置，是为了解决 ProviderManager 在初次使用时，外部定义DefaultAuthenticationEventPublisher 不会注入问题
        // 外部注入DefaultAuthenticationEventPublisher是标准配置方法，两处都保留是为了保险，还需要深入研究才能决定去掉哪个。
        AuthenticationManagerBuilder authenticationManagerBuilder = httpSecurity.getSharedObject(AuthenticationManagerBuilder.class);
        ApplicationContext applicationContext = httpSecurity.getSharedObject(ApplicationContext.class);
        authenticationManagerBuilder.authenticationEventPublisher(new DefaultOAuth2AuthenticationEventPublisher(applicationContext));

        // build() 方法会让以上所有的配置生效
        return httpSecurity.build();
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource(OAuth2AuthorizationProperties authorizationProperties) throws NoSuchAlgorithmException {

        OAuth2AuthorizationProperties.Jwk jwk = authorizationProperties.getJwk();

        KeyPair keyPair = null;
        if (jwk.getCertificate() == Certificate.CUSTOM) {
            try {
                Resource[] resource = ResourceResolver.getResources(jwk.getJksKeyStore());
                if (ArrayUtils.isNotEmpty(resource)) {
                    KeyStoreKeyFactory keyStoreKeyFactory = new KeyStoreKeyFactory(resource[0], jwk.getJksStorePassword().toCharArray());
                    keyPair = keyStoreKeyFactory.getKeyPair(jwk.getJksKeyAlias(), jwk.getJksKeyPassword().toCharArray());
                }
            } catch (IOException e) {
                log.error("[Herodotus] |- Read custom certificate under resource folder error!", e);
            }

        } else {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            keyPair = keyPairGenerator.generateKeyPair();
        }

        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(UUID.randomUUID().toString())
                .build();
        JWKSet jwkSet = new JWKSet(rsaKey);
        return (jwkSelector, securityContext) -> jwkSelector.select(jwkSet);
    }

    /**
     * jwt 解码
     */
    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings(EndpointProperties endpointProperties) {
        return AuthorizationServerSettings.builder()
                .issuer(endpointProperties.getIssuerUri())
                .authorizationEndpoint(endpointProperties.getAuthorizationEndpoint())
                .deviceAuthorizationEndpoint(endpointProperties.getDeviceAuthorizationEndpoint())
                .deviceVerificationEndpoint(endpointProperties.getDeviceVerificationEndpoint())
                .tokenEndpoint(endpointProperties.getAccessTokenEndpoint())
                .tokenIntrospectionEndpoint(endpointProperties.getTokenIntrospectionEndpoint())
                .tokenRevocationEndpoint(endpointProperties.getTokenRevocationEndpoint())
                .jwkSetEndpoint(endpointProperties.getJwkSetEndpoint())
                .oidcLogoutEndpoint(endpointProperties.getOidcLogoutEndpoint())
                .oidcUserInfoEndpoint(endpointProperties.getOidcUserInfoEndpoint())
                .oidcClientRegistrationEndpoint(endpointProperties.getOidcClientRegistrationEndpoint())
                .build();
    }
}
