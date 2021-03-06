package kr.bookstorage;

import kr.bookstorage.dto.UserDto;
import kr.bookstorage.security.CustomAuthenticationEntryPoint;
import kr.bookstorage.security.filter.StatelessLoginFilter;
import kr.bookstorage.security.handler.CustomAuthenticationFailureHandler;
import kr.bookstorage.security.handler.CustomAuthenticationSuccessHandler;
import kr.bookstorage.security.handler.CustomLogoutSuccessHandler;
import kr.bookstorage.security.service.CmmLoginHelper;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.ObjectPostProcessor;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.session.SessionManagementFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.social.connect.Connection;
import org.springframework.social.connect.UserProfile;
import org.springframework.social.connect.web.ProviderSignInUtils;
import org.springframework.social.security.SocialAuthenticationFilter;
import org.springframework.social.security.SpringSocialConfigurer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

import javax.persistence.EntityManagerFactory;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Created by 권성봉 on 8/10/16.
 */
@SpringBootApplication(scanBasePackages = "kr.bookstorage")
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 86400)
@EnableTransactionManagement
@EnableConfigurationProperties
@RestController
public class Application extends SpringBootServletInitializer {

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private ProviderSignInUtils providerSignInUtils;

    @SuppressWarnings("SpringJavaAutowiringInspection")
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Bean
    public PlatformTransactionManager transactionManager() {
        PlatformTransactionManager transactionManager = new JpaTransactionManager(entityManagerFactory);
        return transactionManager;
    }

    @RequestMapping("/")
    public CsrfToken main(CsrfToken token) {
        return token;
    }

    @RequestMapping("/me")
    public UserDto.Response me() {
        return modelMapper.map(CmmLoginHelper.getUser(), UserDto.Response.class);
    }

    @RequestMapping(value = "/signup", method = RequestMethod.GET)
    public UserDto.Response socialSignUp(WebRequest request){
        Connection<?> connection = providerSignInUtils.getConnectionFromSession(request);
        UserProfile userProfile = connection.fetchUserProfile();

        return null;
    }

    public static void main(String[] args){
        SpringApplication.run(Application.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder){
        return builder.sources(applicationClass);
    }

    private static Class<Application> applicationClass = Application.class;

    @Configuration
    @EnableWebSecurity
    @Order(SecurityProperties.ACCESS_OVERRIDE_ORDER)
    protected static class SecurityConfiguration extends WebSecurityConfigurerAdapter {

        @Autowired
        private CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler;

        @Autowired
        private CustomAuthenticationFailureHandler customAuthenticationFailureHandler;

        @Autowired
        private CustomLogoutSuccessHandler customLogoutSuccessHandler;

        @Autowired
        private AuthenticationProvider customAuthProvider;

        @Autowired
        private CustomAuthenticationEntryPoint customAuthenticationEntryPoint;

        @Autowired
        public void globalUserDetails(AuthenticationManagerBuilder auth) throws Exception {
            auth.authenticationProvider(customAuthProvider);
        }

        @Override
        protected void configure(HttpSecurity http) throws Exception {
            StatelessLoginFilter statelessLoginFilter = new StatelessLoginFilter("/login", customAuthProvider);
            statelessLoginFilter.setAuthenticationSuccessHandler(customAuthenticationSuccessHandler);
            statelessLoginFilter.setAuthenticationFailureHandler(customAuthenticationFailureHandler);

            final SpringSocialConfigurer socialConfigurer = new SpringSocialConfigurer();
            socialConfigurer.addObjectPostProcessor(new ObjectPostProcessor<SocialAuthenticationFilter>() {
                @Override
                public <O extends SocialAuthenticationFilter> O postProcess(O socialAuthenticationFilter) {
                    socialAuthenticationFilter.setAuthenticationSuccessHandler(customAuthenticationSuccessHandler);
                    return socialAuthenticationFilter;
                }
            });

            http
                .csrf().disable()
                .formLogin()
                    .loginProcessingUrl("/login")
                    .usernameParameter("email")
                    .passwordParameter("password")
                    .successHandler(customAuthenticationSuccessHandler)
                    .failureHandler(customAuthenticationFailureHandler)
                .and()
                    .authenticationProvider(customAuthProvider)
                    .exceptionHandling()
                    .authenticationEntryPoint(customAuthenticationEntryPoint)
                .and()
                    .logout()
                    .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                    .logoutSuccessHandler(customLogoutSuccessHandler)
                .and()
                    .authorizeRequests()
//                    .antMatchers(HttpMethod.OPTIONS,"/**").permitAll()
                    .antMatchers("/login", "/", "/auth", "/auth/me", "/connect/**", "/auth/connect/**", "/auth/**", "/signup", "/auth/signup").permitAll()
                    .anyRequest().authenticated()
                .and()
                    .csrf().csrfTokenRepository(csrfTokenRepository())
                .and()
                    .apply(socialConfigurer)
                .and()
                    .addFilterBefore(statelessLoginFilter, UsernamePasswordAuthenticationFilter.class)
                    .addFilterAfter(csrfHeaderFilter(), SessionManagementFilter.class);
        }

        private Filter csrfHeaderFilter() {
            return new OncePerRequestFilter() {
                @Override
                protected void doFilterInternal(HttpServletRequest request,
                                                HttpServletResponse response, FilterChain filterChain)
                        throws ServletException, IOException {
                    CsrfToken csrf = (CsrfToken) request.getAttribute(CsrfToken.class
                            .getName());
                    if (csrf != null) {
                        Cookie cookie = WebUtils.getCookie(request, "XSRF-TOKEN");
                        String token = csrf.getToken();
                        if (cookie == null || token != null
                                && !token.equals(cookie.getValue())) {
                            cookie = new Cookie("XSRF-TOKEN", token);
                            cookie.setPath("/");
                            response.addCookie(cookie);
                        }
                    }
                    filterChain.doFilter(request, response);
                }
            };
        }

        private CsrfTokenRepository csrfTokenRepository() {
            HttpSessionCsrfTokenRepository repository = new HttpSessionCsrfTokenRepository();
            repository.setHeaderName("X-XSRF-TOKEN");
            return repository;
        }
    }
}
