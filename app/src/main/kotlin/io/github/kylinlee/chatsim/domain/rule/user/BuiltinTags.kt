package io.github.kylinlee.chatsim.domain.rule.user

/** 内置标签：不允许删除，用户可以添加分类到这些标签的规则。 */
object BuiltinTags {
    const val VERIFICATION = "验证码"

    val all: List<String> = listOf(VERIFICATION)

    fun isBuiltin(tag: String): Boolean = tag in all
}
