package com.valor.assets;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class AssetClockConfig {
    @Bean Clock assetBusinessClock() { return Clock.systemUTC(); }
}
