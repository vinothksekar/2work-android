package com.twowork.core

/**
 * Client-side mirror of the server's newPasswordSchema (server.js): min 12 / max
 * 128 chars with a lower-case letter, an upper-case letter, a number and a symbol.
 * The server stays the source of truth; this only lets the UI guide the user and
 * avoid a guaranteed-reject round trip. ASCII ranges match the server regexes
 * [a-z] / [A-Z] / [0-9] / [^A-Za-z0-9] exactly.
 */
object PasswordPolicy {
    const val MIN_LENGTH = 12
    const val MAX_LENGTH = 128

    data class Rule(val label: String, val satisfiedBy: (String) -> Boolean)

    val rules: List<Rule> = listOf(
        Rule("At least $MIN_LENGTH characters") { it.length in MIN_LENGTH..MAX_LENGTH },
        Rule("A lower-case letter") { p -> p.any { it in 'a'..'z' } },
        Rule("An upper-case letter") { p -> p.any { it in 'A'..'Z' } },
        Rule("A number") { p -> p.any { it in '0'..'9' } },
        Rule("A symbol") { p -> p.any { it !in 'a'..'z' && it !in 'A'..'Z' && it !in '0'..'9' } }
    )

    fun unmet(password: String): List<String> =
        rules.filterNot { it.satisfiedBy(password) }.map { it.label }

    fun isValid(password: String): Boolean = rules.all { it.satisfiedBy(password) }
}
