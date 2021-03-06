package org.apereo.cas.adaptors.authy.config;

import org.apereo.cas.CentralAuthenticationService;
import org.apereo.cas.adaptors.authy.web.flow.AuthyAuthenticationWebflowAction;
import org.apereo.cas.adaptors.authy.web.flow.AuthyAuthenticationWebflowEventResolver;
import org.apereo.cas.adaptors.authy.web.flow.AuthyMultifactorTrustWebflowConfigurer;
import org.apereo.cas.adaptors.authy.web.flow.AuthyMultifactorWebflowConfigurer;
import org.apereo.cas.authentication.AuthenticationServiceSelectionPlan;
import org.apereo.cas.authentication.AuthenticationSystemSupport;
import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.services.MultifactorAuthenticationProviderSelector;
import org.apereo.cas.services.ServicesManager;
import org.apereo.cas.ticket.registry.TicketRegistrySupport;
import org.apereo.cas.trusted.authentication.api.MultifactorAuthenticationTrustStorage;
import org.apereo.cas.web.flow.CasWebflowConfigurer;
import org.apereo.cas.web.flow.CasWebflowExecutionPlan;
import org.apereo.cas.web.flow.CasWebflowExecutionPlanConfigurer;
import org.apereo.cas.web.flow.authentication.RankedMultifactorAuthenticationProviderSelector;
import org.apereo.cas.web.flow.resolver.CasWebflowEventResolver;

import lombok.val;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.web.util.CookieGenerator;
import org.springframework.webflow.config.FlowDefinitionRegistryBuilder;
import org.springframework.webflow.definition.registry.FlowDefinitionRegistry;
import org.springframework.webflow.engine.builder.support.FlowBuilderServices;
import org.springframework.webflow.execution.Action;

/**
 * This is {@link AuthyConfiguration}.
 *
 * @author Misagh Moayyed
 * @since 5.0.0
 */
@Configuration("authyConfiguration")
@EnableConfigurationProperties(CasConfigurationProperties.class)
public class AuthyConfiguration implements CasWebflowExecutionPlanConfigurer {

    @Autowired
    @Qualifier("servicesManager")
    private ObjectProvider<ServicesManager> servicesManager;

    @Autowired
    private CasConfigurationProperties casProperties;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    @Qualifier("loginFlowRegistry")
    private FlowDefinitionRegistry loginFlowDefinitionRegistry;

    @Autowired
    private FlowBuilderServices flowBuilderServices;

    @Autowired
    @Qualifier("centralAuthenticationService")
    private ObjectProvider<CentralAuthenticationService> centralAuthenticationService;

    @Autowired
    @Qualifier("defaultAuthenticationSystemSupport")
    private ObjectProvider<AuthenticationSystemSupport> authenticationSystemSupport;

    @Autowired
    @Qualifier("defaultTicketRegistrySupport")
    private ObjectProvider<TicketRegistrySupport> ticketRegistrySupport;

    @Autowired
    @Qualifier("multifactorAuthenticationProviderSelector")
    private ObjectProvider<MultifactorAuthenticationProviderSelector> multifactorAuthenticationProviderSelector;

    @Autowired
    @Qualifier("warnCookieGenerator")
    private CookieGenerator warnCookieGenerator;


    @Autowired
    @Qualifier("authenticationServiceSelectionPlan")
    private AuthenticationServiceSelectionPlan authenticationRequestServiceSelectionStrategies;

    @Bean
    public FlowDefinitionRegistry authyAuthenticatorFlowRegistry() {
        val builder = new FlowDefinitionRegistryBuilder(this.applicationContext, this.flowBuilderServices);
        builder.setBasePath("classpath*:/webflow");
        builder.addFlowLocationPattern("/mfa-authy/*-webflow.xml");
        return builder.build();
    }

    @RefreshScope
    @Bean
    public CasWebflowEventResolver authyAuthenticationWebflowEventResolver() {
        return new AuthyAuthenticationWebflowEventResolver(authenticationSystemSupport.getIfAvailable(),
            centralAuthenticationService.getIfAvailable(),
            servicesManager.getIfAvailable(),
            ticketRegistrySupport.getIfAvailable(),
            warnCookieGenerator,
            authenticationRequestServiceSelectionStrategies,
            multifactorAuthenticationProviderSelector.getIfAvailable(RankedMultifactorAuthenticationProviderSelector::new));
    }

    @ConditionalOnMissingBean(name = "authyMultifactorWebflowConfigurer")
    @Bean
    @DependsOn("defaultWebflowConfigurer")
    public CasWebflowConfigurer authyMultifactorWebflowConfigurer() {
        return new AuthyMultifactorWebflowConfigurer(flowBuilderServices,
            loginFlowDefinitionRegistry, authyAuthenticatorFlowRegistry(), applicationContext, casProperties);
    }

    @RefreshScope
    @Bean
    public Action authyAuthenticationWebflowAction() {
        return new AuthyAuthenticationWebflowAction(authyAuthenticationWebflowEventResolver());
    }

    @Override
    public void configureWebflowExecutionPlan(final CasWebflowExecutionPlan plan) {
        plan.registerWebflowConfigurer(authyMultifactorWebflowConfigurer());
    }

    /**
     * The Authy multifactor trust configuration.
     */
    @ConditionalOnClass(value = MultifactorAuthenticationTrustStorage.class)
    @ConditionalOnProperty(prefix = "cas.authn.mfa.authy", name = "trustedDeviceEnabled", havingValue = "true", matchIfMissing = true)
    @Configuration("authyMultifactorTrustConfiguration")
    public class AuthyMultifactorTrustConfiguration implements CasWebflowExecutionPlanConfigurer {

        @ConditionalOnMissingBean(name = "authyMultifactorTrustWebflowConfigurer")
        @Bean
        @DependsOn("defaultWebflowConfigurer")
        public CasWebflowConfigurer authyMultifactorTrustWebflowConfigurer() {
            val deviceRegistrationEnabled = casProperties.getAuthn().getMfa().getTrusted().isDeviceRegistrationEnabled();
            return new AuthyMultifactorTrustWebflowConfigurer(flowBuilderServices,
                loginFlowDefinitionRegistry, deviceRegistrationEnabled,
                authyAuthenticatorFlowRegistry(), applicationContext, casProperties);
        }

        @Override
        public void configureWebflowExecutionPlan(final CasWebflowExecutionPlan plan) {
            plan.registerWebflowConfigurer(authyMultifactorTrustWebflowConfigurer());
        }
    }
}
