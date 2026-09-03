package dev.gaphunter.interfaceresourceclosedivergencecompanion.detect

import com.intellij.psi.PsiBlockStatement
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiCodeBlock
import com.intellij.psi.PsiDeclarationStatement
import com.intellij.psi.PsiDoWhileStatement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiExpressionStatement
import com.intellij.psi.PsiForStatement
import com.intellij.psi.PsiIfStatement
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiStatement
import com.intellij.psi.PsiTryStatement
import com.intellij.psi.PsiType
import com.intellij.psi.PsiWhileStatement

/** `true` = closed, `false` = open, on a given reaching path -- the SAME per-variable state-set lattice `jdbc-double-close-companion` already proved, extended here with a genuinely interprocedural transition (see [ResourceStateEngine.propagate]'s [ResolveParamCloseSummary] callback). Same copy (duplicated per this catalog's "no shared library" convention) as `interprocedural-resource-leak-companion`'s own engine -- reused here as-is, since the whole-project summary map it computes ALREADY covers every interface implementation's own override method (a regular project method like any other). */
typealias ResourceState = Map<String, Set<Boolean>>

object ResourceStateEngine {

    private val RESOURCE_TYPE_SIMPLE_NAMES = setOf(
        "Connection", "Statement", "PreparedStatement", "CallableStatement", "ResultSet",
        "InputStream", "OutputStream", "Reader", "Writer",
    )

    fun isResourceType(type: PsiType): Boolean = (type as? PsiClassType)?.className in RESOURCE_TYPE_SIMPLE_NAMES

    /** Looks up the set of parameter indices [callee] guarantees to close on every path -- null when unknown (not yet computed, or not a tracked method), never a guess. */
    fun interface ResolveParamCloseSummary {
        fun resolve(callee: PsiMethod): Set<Int>?
    }

    fun propagate(body: PsiCodeBlock, initialState: ResourceState, resolveSummary: ResolveParamCloseSummary): ResourceState =
        Propagator(resolveSummary).propagate(body.statements.toList(), initialState)

    private class Propagator(private val resolveSummary: ResolveParamCloseSummary) {

        fun propagate(statements: List<PsiStatement>, incoming: ResourceState): ResourceState {
            var state = incoming
            for (stmt in statements) state = propagateStatement(stmt, state)
            return state
        }

        fun propagateStatement(stmt: PsiStatement, state: ResourceState): ResourceState = when (stmt) {
            is PsiDeclarationStatement -> propagateDeclaration(stmt, state)
            is PsiExpressionStatement -> propagateExpressionStatement(stmt.expression, state)
            is PsiBlockStatement -> propagate(stmt.codeBlock.statements.toList(), state)
            is PsiIfStatement -> propagateIf(stmt, state)
            is PsiTryStatement -> propagateTry(stmt, state)
            is PsiWhileStatement -> propagateLoop(stmt.body, state)
            is PsiForStatement -> propagateLoop(stmt.body, state)
            is PsiDoWhileStatement -> propagateLoop(stmt.body, state)
            else -> state
        }

        private fun propagateDeclaration(stmt: PsiDeclarationStatement, state: ResourceState): ResourceState {
            var result = state
            for (element in stmt.declaredElements) {
                val variable = element as? PsiLocalVariable ?: continue
                if (!isResourceType(variable.type)) continue
                if (variable.initializer == null) continue
                result = result + (variable.name to setOf(false))
            }
            return result
        }

        private fun propagateExpressionStatement(expression: PsiExpression, state: ResourceState): ResourceState {
            val call = expression as? PsiMethodCallExpression ?: return state
            var result = state

            val methodName = call.methodExpression.referenceName
            val qualifier = call.methodExpression.qualifierExpression as? PsiReferenceExpression
            if (methodName == "close" && qualifier != null && qualifier.qualifierExpression == null) {
                val name = qualifier.referenceName
                if (name != null && name in result) result = result + (name to setOf(true))
            }

            val callee = call.resolveMethod() ?: return result
            val summary = resolveSummary.resolve(callee) ?: return result
            val arguments = call.argumentList.expressions
            for (index in summary) {
                val argument = arguments.getOrNull(index) as? PsiReferenceExpression ?: continue
                if (argument.qualifierExpression != null) continue
                val name = argument.referenceName ?: continue
                if (name in result) result = result + (name to setOf(true))
            }
            return result
        }

        private fun propagateIf(stmt: PsiIfStatement, state: ResourceState): ResourceState {
            val thenState = stmt.thenBranch?.let { propagateStatement(it, state) } ?: state
            val elseState = stmt.elseBranch?.let { propagateStatement(it, state) } ?: state
            return merge(thenState, elseState)
        }

        private fun propagateTry(stmt: PsiTryStatement, state: ResourceState): ResourceState {
            val tryEnd = stmt.tryBlock?.let { propagate(it.statements.toList(), state) } ?: state
            var mergedForFinally = tryEnd
            for (catchBlock in stmt.catchBlocks) {
                val catchEnd = propagate(catchBlock.statements.toList(), state)
                mergedForFinally = merge(mergedForFinally, catchEnd)
            }
            val finallyBlock = stmt.finallyBlock
            return if (finallyBlock != null) propagate(finallyBlock.statements.toList(), mergedForFinally) else mergedForFinally
        }

        private fun propagateLoop(body: PsiStatement?, state: ResourceState): ResourceState {
            if (body == null) return state
            val bodyEnd = propagateStatement(body, state)
            return merge(state, bodyEnd)
        }

        private fun merge(a: ResourceState, b: ResourceState): ResourceState =
            (a.keys + b.keys).associateWith { key -> (a[key] ?: emptySet()) + (b[key] ?: emptySet()) }
    }
}
