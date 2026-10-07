package io.github.kylinlee.chatsim.domain.rule.user

/** 一次记录求值的结果：拦截优先，标签叠加，垃圾箱仅在未拦截时生效。 */
data class UserRuleOutcome(
    val blocked: Boolean,
    val trash: Boolean,
    val tags: List<String>,
    val hits: List<UserRule>,
) {
    val hasActions: Boolean
        get() = blocked || trash || tags.isNotEmpty()
}

object UserRuleEngine {
    fun evaluate(
        record: RuleRecord,
        rules: List<UserRule>,
        now: Long = System.currentTimeMillis(),
    ): UserRuleOutcome {
        val hits = rules.filter { rule ->
            rule.enabled && UserRuleExpression.evaluate(rule.conditions, record, now)
        }
        if (hits.isEmpty()) return UserRuleOutcome(blocked = false, trash = false, tags = emptyList(), hits = emptyList())

        val blocked = hits.any { it.action == UserRuleAction.BLOCK }
        val tags = hits.flatMap { it.tags }.distinct()
        val trash = !blocked && hits.any { it.action == UserRuleAction.TRASH }

        return UserRuleOutcome(blocked = blocked, trash = trash, tags = tags, hits = hits)
    }
}
