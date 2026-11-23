package com.geomaster.core.budget;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class BudgetConfig {

    @Bean
    @ConditionalOnMissingBean(BudgetGuard.class)
    BudgetGuard budgetGuard() {
        return new AllowAllBudgetGuard();
    }
}
