package com.giraffe.domain.model

data class Task(
    val id: Long,
    val name: String,
    val category: Category,
    val points: Int,
    val activeDays: Set<Day>
)