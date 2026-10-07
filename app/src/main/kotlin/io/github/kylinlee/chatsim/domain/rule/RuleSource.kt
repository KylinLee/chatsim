package io.github.kylinlee.chatsim.domain.rule

interface RuleSource {
    fun rules(): List<CompiledRule>
}
