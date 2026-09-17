/*
 * Liquid glass patches for X 12.25.2 (NewX):
 *
 *  1) NewX: Liquid glass chat
 *     X ships a native glass treatment for the conversation screen
 *     (ChatGlassAppBar / ChatGlassActionsPill / glass composer, rendered via
 *     the Haze blur library) that is gated off by the feature switch
 *     "xchat_liquid_glass_convo_header_enabled". We force the boolean result
 *     of that switch read to true at every call site (chat page state holder
 *     com/x/dms/components/chat/o0 and DM inbox com/x/dms/components/convlist/u1).
 *
 *  2) NewX: Liquid glass compose button
 *     The timeline FAB pill is drawn by androidx.compose.foundation.a0.invoke
 *     (case 1) through X's glass-aware Surface (material3.p6.a) but with an
 *     opaque container color (GlassStyle.b) and a null modifier — which is why
 *     it never matched the liquid-glass bottom bar. We replace the container
 *     color with the DIM-palette tint the bar's glass uses
 *     (com.x.xds.core.a.a @ 0.75 alpha, exactly per com.x.compose.core.f0.a)
 *     and attach the bar's glass modifier (f0.a) with the composition-scoped
 *     HazeState (dev.chrisbanes.haze.d.j).
 */
package app.crimera.patches.newx.misc.liquidglass

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

// Mirrors app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X (internal there)
private val COMPATIBILITY_NEW_X =
    Compatibility(
        packageName = "com.twitter.android",
        name = "NewX",
        apkFileType = ApkFileType.APKM,
        appIconColor = 0,
        targets =
            listOf(
                AppTarget("12.25.0-alpha.01"),
                AppTarget("12.25.0-prod.01"),
                AppTarget("12.25.2-prod.01"),
                AppTarget("12.26.0-alpha.01", isExperimental = true),
                AppTarget("12.26.0-alpha.02", isExperimental = true),
                AppTarget("12.26.0-alpha.03", isExperimental = true),
                AppTarget("12.27.0-alpha.01", isExperimental = true),
            ),
    )

private const val GLASS_FLAG = "xchat_liquid_glass_convo_header_enabled"

private const val FAB_CONTENT_CLASS = "Landroidx/compose/foundation/a0;"

private const val GLASS_STYLE_PROVIDER =
    "Lcom/google/android/gms/dynamite/e;->R(Landroidx/compose/runtime/Composer;I)Lcom/x/xds/core/a;"

private const val DIM_PALETTE_FIELD = "Lcom/x/xds/core/a;->a:J"

private const val COLOR_TRANSLUCENT = "Landroidx/compose/ui/graphics/z;->b(JF)J"

private const val EMPTY_MODIFIER = "Landroidx/compose/ui/t;->a:Landroidx/compose/ui/t;"

private const val GLASS_MODIFIER =
    "Lcom/x/compose/core/f0;->a(Landroidx/compose/ui/Modifier;Ldev/chrisbanes/haze/t;ZLandroidx/compose/runtime/Composer;I)Landroidx/compose/ui/Modifier;"

private const val HAZE_STATE_COMPOSABLE =
    "Ldev/chrisbanes/haze/d;->j(Landroidx/compose/runtime/Composer;)Ldev/chrisbanes/haze/t;"

private const val FAB_SURFACE_CALL = "Landroidx/compose/material3/p6;->a("

private fun Instruction.isConstString(value: String): Boolean =
    (this is ReferenceInstruction) &&
        (opcode == Opcode.CONST_STRING || opcode == Opcode.CONST_STRING_JUMBO) &&
        reference.toString() == value

/**
 * Returns every method whose instructions reference the given string constant.
 */
private fun BytecodePatchContext.methodsContainingString(value: String): List<Pair<ClassDef, MutableMethod>> {
    val out = mutableListOf<Pair<ClassDef, MutableMethod>>()
    classDefForEach { classDef ->
        val mutableClass = mutableClassDefBy(classDef)
        for (method in mutableClass.methods) {
            val found = method.implementation?.instructions?.any { ins ->
                ins.isConstString(value)
            } ?: false
            if (found) out += classDef to method
        }
    }
    return out
}

@Suppress("unused")
val liquidGlassChatPatch = bytecodePatch(
    name = "NewX: Liquid glass chat",
    description =
        "Forces on X's native liquid-glass treatment for the chat page (glass conversation header, " +
            "actions pill, text input, and bottom buttons) and the DM inbox header.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_NEW_X)

    execute {
        val sites = methodsContainingString(GLASS_FLAG)
        if (sites.isEmpty()) {
            throw PatchException("No call sites of \"$GLASS_FLAG\" found — app version may be incompatible")
        }

        var patched = 0
        for ((classDef, method) in sites) {
            val impl = method.implementation as MutableMethodImplementation
            // Collect indexes first; patch in reverse so indexes stay valid while inserting.
            val constIndexes = impl.instructions.withIndex().mapNotNull { (idx, ins) ->
                if (ins.isConstString(GLASS_FLAG)) idx else null
            }
            for (constIdx in constIndexes.asReversed()) {
                val moveIdx = impl.instructions.withIndex()
                    .filter { it.index > constIdx }
                    .firstOrNull { it.value.opcode == Opcode.MOVE_RESULT }
                    ?.index ?: continue
                val reg = (impl.instructions[moveIdx] as OneRegisterInstruction).registerA
                val setIns = if (reg > 15) "const/16 v$reg, 0x1" else "const/4 v$reg, 0x1"
                method.addInstruction(moveIdx + 1, setIns)
                patched++
            }
            println("[liquid-glass] forced $GLASS_FLAG=true in ${classDef.type}::${method.name}")
        }
        if (patched == 0) {
            throw PatchException("Failed to patch any call site of \"$GLASS_FLAG\"")
        }
        println("[liquid-glass] enabled glass chat at $patched call site(s)")
    }
}

@Suppress("unused")
val liquidGlassComposeButtonPatch = bytecodePatch(
    name = "NewX: Liquid glass compose button",
    description =
        "Gives the timeline compose button (FAB) the same liquid-glass material and " +
            "DIM-palette tint as the bottom navigation bar.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_NEW_X)

    execute {
        val candidates = methodsContainingString("\$this\$AnimatedVisibility")
            .filter { (classDef, _) -> classDef.type == FAB_CONTENT_CLASS }
        if (candidates.isEmpty()) {
            throw PatchException("Compose button renderer $FAB_CONTENT_CLASS not found — app version may be incompatible")
        }

        var patched = false
        for ((classDef, method) in candidates) {
            val impl = method.implementation as MutableMethodImplementation
            val instructions = impl.instructions

            // Anchor: the FAB pill block reads the glass style container color.
            val colorReadIdx = instructions.indexOfFirst { ins ->
                ins.opcode == Opcode.IGET_WIDE &&
                    (ins as? ReferenceInstruction)?.reference.toString() == "Lcom/x/compose/theme/b;->b:J"
            }
            if (colorReadIdx < 0) continue

            // The Surface call that consumes the color.
            val surfaceCallIdx = instructions.withIndex()
                .filter { it.index > colorReadIdx }
                .firstOrNull { (_, ins) ->
                    (ins as? ReferenceInstruction)?.reference.toString().startsWith(FAB_SURFACE_CALL)
                }
                ?.index ?: continue

            val colorReg = (instructions[colorReadIdx] as TwoRegisterInstruction).registerA

            // --- Insert A: translucent DIM color -> v(colorReg) ---
            // Scratch: v1, v5, v6, v8 (all re-initialised by the original code below).
            val insertA =
                """const/4 v1, 0x0
                invoke-static {v9, v1}, $GLASS_STYLE_PROVIDER
                move-result-object v1
                iget-wide v5, v1, $DIM_PALETTE_FIELD
                const/high16 v8, 0x3f400000
                invoke-static {v5, v6, v8}, $COLOR_TRANSLUCENT
                move-result-wide v$colorReg"""

            // --- Insert B: the bottom bar's glass modifier -> v1 (Modifier arg) ---
            // Placed right before the Surface call; scratch v13/v14/v15 (dead params).
            // f0.a(Modifier, HazeState, Z, Composer, I): force-enable via Z=true.
            val insertB =
                """sget-object v1, $EMPTY_MODIFIER
                invoke-static {v9}, $HAZE_STATE_COMPOSABLE
                move-result-object v13
                const/4 v14, 0x1
                const/4 v15, 0x0
                invoke-static {v1, v13, v14, v9, v15}, $GLASS_MODIFIER
                move-result-object v1"""

            // Insert B at surfaceCallIdx FIRST (colorReadIdx is before surfaceCallIdx and unaffected)
            method.addInstructions(surfaceCallIdx, insertB.trimIndent())
            // Insert A right after colorReadIdx SECOND
            method.addInstructions(colorReadIdx + 1, insertA.trimIndent())

            println("[liquid-glass] applied glass to compose button in ${classDef.type}::${method.name}")
            patched = true
            break
        }
        if (!patched) {
            throw PatchException("Compose button surface call site not found — app version may be incompatible")
        }
    }
}
