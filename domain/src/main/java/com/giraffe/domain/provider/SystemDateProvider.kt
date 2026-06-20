package com.giraffe.domain.provider

import com.giraffe.domain.model.SimpleDate

interface SystemDateProvider {
    /**
     * Returns the current device date mapped to your custom domain model.
     */
    fun getCurrentGregorianDate(): SimpleDate
}