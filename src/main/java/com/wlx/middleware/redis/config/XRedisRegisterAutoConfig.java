package com.wlx.middleware.redis.config;

import com.wlx.middleware.redis.annotation.XRedis;
import com.wlx.middleware.redis.reflect.XRedisFactoryBean;
import com.wlx.middleware.redis.util.SimpleMetadataReader;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.beans.Introspector;
import java.util.List;

@Configuration
@EnableConfigurationProperties(XRedisProperties.class)
public class XRedisRegisterAutoConfig implements InitializingBean {

    @Autowired
    private XRedisProperties xRedisProperties;

    @Bean
    @ConditionalOnMissingBean
    public Jedis jedis() {
        JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
        jedisPoolConfig.setMaxIdle(5);
        jedisPoolConfig.setTestOnBorrow(false);
        JedisPool jedisPool = new JedisPool(jedisPoolConfig, xRedisProperties.getHost(), xRedisProperties.getPort());
        return jedisPool.getResource();
    }

    public static class XRedisRegister implements BeanFactoryAware, ImportBeanDefinitionRegistrar {

        private BeanFactory beanFactory;

        @Override
        public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
            this.beanFactory = beanFactory;
        }

        @Override
        public void registerBeanDefinitions(AnnotationMetadata annotationMetadata, BeanDefinitionRegistry registry) {
            try {
                if (!AutoConfigurationPackages.has(beanFactory)) {
                    return;
                }

                List<String> packages = AutoConfigurationPackages.get(beanFactory);
                String basePackage = StringUtils.collectionToCommaDelimitedString(packages);

                String basePackagePath = "classpath*:" + basePackage.replace(".", "/") + "/**/*.class";
                ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();
                Resource[] resources = resourcePatternResolver.getResources(basePackagePath);

                for (Resource resource : resources) {
                    MetadataReader metadataReader = new SimpleMetadataReader(resource, ClassUtils.getDefaultClassLoader());
                    XRedis annotation = Class.forName(metadataReader.getClassMetadata().getClassName()).getAnnotation(XRedis.class);
                    if (annotation == null) {
                        continue;
                    }

                    ScannedGenericBeanDefinition beanDefinition = new ScannedGenericBeanDefinition(metadataReader);
                    String beanName = Introspector.decapitalize(ClassUtils.getShortName(beanDefinition.getBeanClassName()));

                    beanDefinition.setResource(resource);
                    beanDefinition.setSource(resource);
                    beanDefinition.setScope("singleton");
                    beanDefinition.getConstructorArgumentValues().addGenericArgumentValue(beanDefinition.getBeanClassName());
                    beanDefinition.setBeanClass(XRedisFactoryBean.class);

                    BeanDefinitionHolder definitionHolder = new BeanDefinitionHolder(beanDefinition, beanName);
                    registry.registerBeanDefinition(beanName, definitionHolder.getBeanDefinition());
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Configuration
    @Import(XRedisRegister.class)
    public static class MapperScannerRegistrarNotFoundConfiguration implements InitializingBean {

        @Override
        public void afterPropertiesSet() throws Exception {

        }
    }


    @Override
    public void afterPropertiesSet() throws Exception {

    }
}
