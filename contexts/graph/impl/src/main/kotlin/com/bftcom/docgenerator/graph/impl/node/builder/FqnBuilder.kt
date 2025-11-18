package com.bftcom.docgenerator.graph.impl.node.builder

/**
 * Утилита для построения Fully Qualified Names (FQN) для нод.
 */
object FqnBuilder {
    /**
     * Строит FQN для типа: package + className
     */
    fun buildTypeFqn(
        packageFqn: String?,
        className: String,
    ): String = listOfNotNull(packageFqn?.takeIf { it.isNotBlank() }, className).joinToString(".")

    /**
     * Строит FQN для функции/метода: owner + methodName или package + methodName
     */
    fun buildFunctionFqn(
        ownerFqn: String?,
        packageFqn: String?,
        functionName: String,
    ): String =
        when {
            !ownerFqn.isNullOrBlank() -> "$ownerFqn.$functionName"
            !packageFqn.isNullOrBlank() -> "$packageFqn.$functionName"
            else -> functionName
        }

    /**
     * Строит FQN для поля: owner + fieldName
     */
    fun buildFieldFqn(
        ownerFqn: String?,
        fieldName: String,
    ): String = listOfNotNull(ownerFqn, fieldName).joinToString(".")
}
