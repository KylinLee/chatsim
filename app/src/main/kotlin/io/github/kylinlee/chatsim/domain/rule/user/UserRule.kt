package io.github.kylinlee.chatsim.domain.rule.user

import kotlinx.serialization.Serializable

/** 规则动作：添加标签 / 拦截 / 移入垃圾箱。 */
@Serializable
enum class UserRuleAction {
    ADD_TAG,
    BLOCK,
    TRASH,
}

/** 一条用户规则；[tags] 对任意动作都可选（ADD_TAG 至少一个），多条规则的标签会叠加。 */
data class UserRule(
    val id: Long,
    val name: String,
    val action: UserRuleAction,
    val tags: List<String>,
    val conditions: List<RuleCondition>,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
)
