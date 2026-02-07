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
     * Строит FQN для функции/метода: `owner.method(ParamType1,ParamType2)`.
     */
    fun buildFunctionFqn(
        ownerFqn: String?,
        packageFqn: String?,
        functionName: String,
        paramTypeNames: List<String> = emptyList(),
    ): String {
        val base = when {
            !ownerFqn.isNullOrBlank() -> "$ownerFqn.$functionName"
            !packageFqn.isNullOrBlank() -> "$packageFqn.$functionName"
            else -> functionName
        }
        return "$base(${paramTypeNames.joinToString(",")})"
    }

    /**
     * Строит FQN для поля: owner + fieldName
     */
    fun buildFieldFqn(
        ownerFqn: String?,
        fieldName: String,
    ): String = listOfNotNull(ownerFqn, fieldName).joinToString(".")
}
