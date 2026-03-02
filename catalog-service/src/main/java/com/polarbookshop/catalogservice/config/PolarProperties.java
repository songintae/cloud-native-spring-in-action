package com.polarbookshop.catalogservice.config;


import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@Getter
@ConfigurationProperties(prefix = "polar")
public class PolarProperties {

    private String greeting;

}
