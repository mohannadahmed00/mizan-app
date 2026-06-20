package com.giraffe.domain.di

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module

@Module
@ComponentScan("com.giraffe.domain")
@Configuration
class DomainModule