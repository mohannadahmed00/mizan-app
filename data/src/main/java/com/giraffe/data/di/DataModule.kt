package com.giraffe.data.di

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module

@Module
@ComponentScan("com.giraffe.data")
@Configuration
class DataModule