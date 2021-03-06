package com.zsinda.fdp.config;

import com.zsinda.fdp.component.FdpWebResponseExceptionTranslator;
import com.zsinda.fdp.constant.AuthorizationConstants;
import com.zsinda.fdp.service.FdpUserDetailsService;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.oauth2.config.annotation.configurers.ClientDetailsServiceConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configuration.AuthorizationServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableAuthorizationServer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerEndpointsConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerSecurityConfigurer;
import org.springframework.security.oauth2.provider.client.JdbcClientDetailsService;
import org.springframework.security.oauth2.provider.token.DefaultTokenServices;
import org.springframework.security.oauth2.provider.token.TokenEnhancer;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.oauth2.provider.token.store.redis.RedisTokenStore;

import javax.sql.DataSource;

/**
 * @program: FDPlatform
 * @description: AuthorizationServerConfig
 * @author: Sinda
 * @create: 2019-12-27 22:26
 **/
@Configuration
@EnableAuthorizationServer
@AllArgsConstructor
public class FdpAuthorizationServerConfig extends AuthorizationServerConfigurerAdapter {

    private final AuthenticationManager authenticationManager;

    private final FdpUserDetailsService fdpUserDetailsService;

    private final DataSource dataSource;

    private final RedisConnectionFactory redisConnectionFactory;

    private final TokenEnhancer fdpTokenEnhancer;

    /**
     * 配置客户端信息
     * @param clientDetailsServiceConfigurer
     * @throws Exception
     */
    @Override
    public void configure(ClientDetailsServiceConfigurer clientDetailsServiceConfigurer) throws Exception {
        JdbcClientDetailsService jdbcClientDetailsService = new JdbcClientDetailsService(dataSource);
        jdbcClientDetailsService.setSelectClientDetailsSql(AuthorizationConstants.DEFAULT_SELECT_STATEMENT);
        jdbcClientDetailsService.setFindClientDetailsSql(AuthorizationConstants.DEFAULT_FIND_STATEMENT);
        clientDetailsServiceConfigurer.withClientDetails(jdbcClientDetailsService);
    }

    @Bean
    public TokenStore tokenStore() {
        RedisTokenStore tokenStore = new RedisTokenStore(redisConnectionFactory);
        return tokenStore;
    }

    @Override
    public void configure(AuthorizationServerSecurityConfigurer security) {
        security.allowFormAuthenticationForClients()
                .checkTokenAccess("isAuthenticated()");
    }

    public DefaultTokenServices defaultTokenServices() {
        DefaultTokenServices tokenServices = new DefaultTokenServices();
        tokenServices.setTokenStore(tokenStore());
        tokenServices.setSupportRefreshToken(true);
        // token有效期自定义设置，90天
        tokenServices.setAccessTokenValiditySeconds(60 * 60 * 24 * 900);
        // refresh_token 90天
        tokenServices.setRefreshTokenValiditySeconds(60 * 60 * 24 * 900);
        return tokenServices;
    }

    /**
     * 配置有哪些用户可以来访问认证服务器
     */
    @Override
    public void configure(AuthorizationServerEndpointsConfigurer endpoints) {
        endpoints
                .allowedTokenEndpointRequestMethods(HttpMethod.GET, HttpMethod.POST)
                .tokenEnhancer(fdpTokenEnhancer)
                .userDetailsService(fdpUserDetailsService)
                .authenticationManager(authenticationManager)//校验用户信息是否合法
                .reuseRefreshTokens(false)
                .tokenServices(defaultTokenServices())
                .pathMapping("/oauth/confirm_access", "/token/confirm_access")//设置成自己的授权页面
                .exceptionTranslator(new FdpWebResponseExceptionTranslator()); //修改Oauth2定义的错误信息 为我们定义的错误信息


    }
}
