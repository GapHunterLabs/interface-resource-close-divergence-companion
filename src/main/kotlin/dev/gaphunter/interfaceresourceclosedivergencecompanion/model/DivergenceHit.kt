package dev.gaphunter.interfaceresourceclosedivergencecompanion.model

import com.intellij.psi.PsiElement

/** A confirmed resource-close divergence: a call through [interfaceName].[methodName] (the interface type) passes a resource that SOME real implementations guarantee closing on every path (via their own interprocedural, path-sensitive summary) and at least one other real implementation does NOT. */
data class DivergenceHit(val anchor: PsiElement, val interfaceName: String, val methodName: String)
