package com.zachary.sitecheck;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SiteCheckApplication {

    public static void main(String[] args) {
        SpringApplication.run(SiteCheckApplication.class, args);
    }

}
