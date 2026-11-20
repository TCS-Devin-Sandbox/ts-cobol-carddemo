package com.carddemo.interest.job;

import com.carddemo.interest.calc.GroupWriter;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.SpringApplication;

public final class CrashCloseProcess {
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(InterestCloseApplication.class);
        application.setDefaultProperties(Map.of("spring.config.location", "classpath:job/close.properties"));
        application.addInitializers(context -> {
            var definition = new RootBeanDefinition(GroupWriter.CommitHook.class, () -> new GroupWriter.CommitHook() {
                void crash(UUID run, long ordinal, String phase) {
                    if (ordinal == 1 && phase.equals(System.getProperty("crash.phase"))) {
                        System.out.println("CRASH run=" + run + " ordinal=" + ordinal + " phase=" + phase);
                        System.out.flush();
                        Runtime.getRuntime().halt(77);
                    }
                }
                @Override public void beforeCommit(UUID run, long ordinal) { crash(run, ordinal, "before"); }
                @Override public void afterCommit(UUID run, long ordinal) { crash(run, ordinal, "after"); }
            });
            definition.setPrimary(true);
            ((BeanDefinitionRegistry) context.getBeanFactory()).registerBeanDefinition("crashHook", definition);
            context.addBeanFactoryPostProcessor(factory -> {
                for (String name : factory.getBeanNamesForType(GroupWriter.CommitHook.class, false, false)) {
                    factory.getBeanDefinition(name).setPrimary(name.equals("crashHook"));
                }
            });
        });
        try (var context = application.run(args)) {
            System.exit(context.getBean(InterestCloseApplication.ExitCode.class).value);
        }
    }
}
