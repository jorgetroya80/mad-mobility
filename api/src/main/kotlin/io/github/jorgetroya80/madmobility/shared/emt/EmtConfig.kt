package io.github.jorgetroya80.madmobility.shared.emt

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(EmtProperties::class)
class EmtConfig
