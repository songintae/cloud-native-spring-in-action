package com.polarbookshop.catalogservice.config;


import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConstructorBinding
@ConfigurationProperties(prefix = "polar")
public record PolarProperties(String greeting) {
}
