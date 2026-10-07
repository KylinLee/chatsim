package io.github.kylinlee.chatsim.domain.rule

import io.github.kylinlee.chatsim.domain.rule.builtin.BuiltinRuleSource

/** 进程级单例：无 Hilt 的调用方（Receiver、兼容层）直接使用。 */
object RuleEngines {
    val engine: RuleEngine by lazy { RuleEngine(BuiltinRuleSource()) }
}
