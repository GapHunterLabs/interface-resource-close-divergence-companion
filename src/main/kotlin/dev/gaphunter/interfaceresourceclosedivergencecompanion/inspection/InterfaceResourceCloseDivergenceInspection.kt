package dev.gaphunter.interfaceresourceclosedivergencecompanion.inspection

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiFile
import dev.gaphunter.interfaceresourceclosedivergencecompanion.detect.InterfaceResourceCloseDivergenceFinder
import dev.gaphunter.interfaceresourceclosedivergencecompanion.model.DivergenceHit
import dev.gaphunter.interfaceresourceclosedivergencecompanion.review.ReviewPrompt

/** Flags a call through an interface-typed reference where real implementations diverge on guaranteeing to close a resource parameter -- see [InterfaceResourceCloseDivergenceFinder]. */
class InterfaceResourceCloseDivergenceInspection : LocalInspectionTool() {

    companion object {
        const val MAX_FILE_LENGTH = 500_000
    }

    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
        if (file.text.length > MAX_FILE_LENGTH) return null

        val hits = InterfaceResourceCloseDivergenceFinder.findAll(file)
        if (hits.isEmpty()) return null

        val problems = hits.map { hit ->
            manager.createProblemDescriptor(
                hit.anchor,
                messageFor(hit),
                isOnTheFly,
                emptyArray(),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            )
        }

        val path = file.virtualFile?.path
        if (path != null) {
            for (hit in hits) {
                val lineNumber = file.viewProvider.document?.getLineNumber(hit.anchor.textRange.startOffset) ?: -1
                ReviewPrompt.recordHit(file.project, "$path:$lineNumber:${hit.methodName}")
            }
        }

        return problems.toTypedArray()
    }

    private fun messageFor(hit: DivergenceHit): String =
        "This call to ${hit.interfaceName}.${hit.methodName}() passes a resource that SOME real implementations guarantee " +
            "closing on every path (CWE-772) while at least one other real implementation never does -- the interface type " +
            "alone never guarantees which behavior you get once dependency injection swaps implementations"
}
