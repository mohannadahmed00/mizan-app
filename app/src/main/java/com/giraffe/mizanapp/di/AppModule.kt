package com.giraffe.mizanapp.di

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module

@KoinApplication
@Module
@ComponentScan("com.giraffe.mizanapp")
@Configuration
class MyApp