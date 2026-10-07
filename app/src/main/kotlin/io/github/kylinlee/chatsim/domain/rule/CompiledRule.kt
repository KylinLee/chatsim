package io.github.kylinlee.chatsim.domain.rule

class CompiledRule(
    val id: String,
    val name: String,
    val priority: Int,
    val triggers: Set<RuleTrigger>,
    val pattern: Regex?,
    val actions: List<RuleAction>,
    val matcher: ((RuleEvent, RuleContext) -> Boolean)? = null,
)
