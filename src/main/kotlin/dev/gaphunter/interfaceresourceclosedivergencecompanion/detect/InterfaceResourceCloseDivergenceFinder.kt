package dev.gaphunter.interfaceresourceclosedivergencecompanion.detect

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiModifier
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import dev.gaphunter.interfaceresourceclosedivergencecompanion.model.DivergenceHit

/**
 * The TRIPLE combination this plugin is built around -- three
 * techniques this catalog already proved separately, combined for the
 * first time: real Class Hierarchy Analysis
 * (`interface-exception-divergence-companion`'s own
 * `ClassInheritorsSearch`-based resolution of EVERY real
 * implementation) applied over [ProjectResourceCloseSummaryAnalyzer]'s
 * whole-project summaries, which are THEMSELVES the product of a real
 * Tarjan-SCC interprocedural fixed point
 * (`log-injection-companion`'s own technique) computed via a
 * branch-merging path-sensitive engine
 * (`jdbc-double-close-companion`'s own technique). For a call through
 * an interface-typed reference passing a resource-typed argument,
 * looks up EACH real implementation's own summary for that same
 * parameter position and flags the call site when they disagree on
 * whether the resource is guaranteed closed.
 */
object InterfaceResourceCloseDivergenceFinder {

    private const val MIN_IMPLEMENTATIONS = 2
    private const val MAX_IMPLEMENTATIONS = 10

    fun findAll(file: PsiFile): List<DivergenceHit> {
        val hits = mutableListOf<DivergenceHit>()
        val project = file.project
        file.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitMethodCallExpression(call: PsiMethodCallExpression) {
                super.visitMethodCallExpression(call)
                hitForCall(call, project)?.let { hits += it }
            }
        })
        return hits
    }

    private fun hitForCall(call: PsiMethodCallExpression, project: Project): DivergenceHit? {
        val qualifier = call.methodExpression.qualifierExpression ?: return null
        val interfaceClass = (qualifier.type as? PsiClassType)?.resolve() ?: return null
        if (!interfaceClass.isInterface) return null

        val interfaceMethod = call.resolveMethod() ?: return null
        if (interfaceMethod.containingClass != interfaceClass) return null

        val resourceParamIndex = interfaceMethod.parameterList.parameters.indexOfFirst { ResourceStateEngine.isResourceType(it.type) }
        if (resourceParamIndex < 0) return null

        val implementations = ClassInheritorsSearch.search(interfaceClass, GlobalSearchScope.projectScope(project), true)
            .findAll()
            .filter { !it.isInterface && !it.hasModifierProperty(PsiModifier.ABSTRACT) }
        if (implementations.size !in MIN_IMPLEMENTATIONS..MAX_IMPLEMENTATIONS) return null

        val summaries = ProjectResourceCloseSummaryAnalyzer.summariesFor(project)

        var realOverrideCount = 0
        var guaranteesCloseCount = 0
        for (implementation in implementations) {
            val overriding = implementation.findMethodBySignature(interfaceMethod, true) ?: continue
            if (overriding.containingClass != implementation) continue
            realOverrideCount++
            val summary = summaries[MethodKey.of(overriding)] ?: emptySet()
            if (resourceParamIndex in summary) guaranteesCloseCount++
        }
        if (realOverrideCount < MIN_IMPLEMENTATIONS) return null
        if (guaranteesCloseCount !in 1 until realOverrideCount) return null

        val anchor = call.methodExpression.referenceNameElement ?: call.methodExpression
        return DivergenceHit(anchor, interfaceClass.name ?: "<interface>", interfaceMethod.name)
    }
}
