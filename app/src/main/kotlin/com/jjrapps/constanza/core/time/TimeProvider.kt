package com.jjrapps.constanza.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * The one place in `:app` allowed to read the ambient clock (design.md §4). Every other call
 * site — mappers, repositories, workers, receivers — takes time as an injected parameter through
 * this interface instead of calling `Instant.now()` / `LocalDate.now()` / `ZoneId.systemDefault()`
 * directly. `:domain` enforces the same ban compile-adjacently via the `ForbiddenMethodCall`
 * detekt rule; `:app` has no such rule, so this abstraction is the boundary here.
 */
interface TimeProvider {
    fun now(): Instant
    fun today(): LocalDate
    fun zone(): ZoneId
}

class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun now(): Instant = Instant.now()
    override fun today(): LocalDate = LocalDate.now(zone())
    override fun zone(): ZoneId = ZoneId.systemDefault()
}
