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
 *     (com.x.xds.core.a.a @ 0.75 alpha, exactly per com.x.compose.core.f0.a),
 *     attach the bar's glass modifier (f0.a) with the composition-scoped
 *     HazeState (dev.chrisbanes.haze.d.j), attach a 1.0dp outline border
 *     (com.x.compose.theme.b.h), and set dynamic on-surface content color
 *     (com.x.compose.theme.b.c) so inner icons/shapes are visible in light mode.
 *     Also clears bit 1 (0x2) of defaultMask (0x60) so Compose preserves our modifier.
 *
 *  3) NewX: Liquid glass new posts pill
 *     The floating "new posts" pill that pops up when scrolling timelines is
 *     rendered by com.x.urt.instructions.n.a (tag "ntp") with an opaque Twitter
 *     blue background (u1.g) and hardcoded white text/icon (u1.B1). We transform
 *     it into liquid glass with DIM translucent tint, Haze blur, 1.0dp outline
 *     border, and adaptive on-surface text/icon color.
 */
package app.crimera.patches.newx.misc.liquidglass

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
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
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction

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

private val FAB_CONTENT_CLASSES = setOf(
    "Landroidx/compose/foundation/a0;",
    "Lcom/x/aitrend/p;",
)

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

private const val THEME_PROVIDER =
    "Lcom/google/android/gms/dynamite/e;->F(Landroidx/compose/runtime/Composer;I)Lcom/x/compose/theme/b;"

private const val THEME_CONTENT_FIELD = "Lcom/x/compose/theme/b;->c:J"

private const val THEME_DIVIDER_FIELD = "Lcom/x/compose/theme/b;->h:J"

private const val BORDER_MODIFIER =
    "Landroidx/compose/foundation/p;->k(Landroidx/compose/ui/Modifier;FJLandroidx/compose/ui/graphics/c1;)Landroidx/compose/ui/Modifier;"

private const val BACKGROUND_MODIFIER =
    "Landroidx/compose/foundation/p;->h(Landroidx/compose/ui/Modifier;JLandroidx/compose/ui/graphics/c1;)Landroidx/compose/ui/Modifier;"

private const val CIRCLE_SHAPE_FIELD =
    "Landroidx/compose/foundation/shape/i;->a:Landroidx/compose/foundation/shape/h;"

private const val NEW_POSTS_TAG = "ntp"

private const val TWITTER_BLUE_FIELD = "Lcom/x/compose/core/u1;->g:J"

private const val WHITE_COLOR_FIELD = "Lcom/x/compose/core/u1;->B1:J"

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
        "Gives the timeline and chat tab compose buttons (FAB) the same liquid-glass material, " +
            "subtle outline border, adaptive icon/shape color, and DIM-palette tint as the bottom navigation bar.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_NEW_X)

    execute {
        val candidates = methodsContainingString("\$this\$AnimatedVisibility")
            .filter { (classDef, _) -> classDef.type in FAB_CONTENT_CLASSES }
        if (candidates.isEmpty()) {
            throw PatchException("Compose button renderers $FAB_CONTENT_CLASSES not found — app version may be incompatible")
        }

        var totalPatched = 0
        for ((classDef, method) in candidates) {
            val impl = method.implementation as MutableMethodImplementation
            val instructions = impl.instructions

            val surfaceCallIndexes = instructions.withIndex().filter { (_, ins) ->
                ins.opcode == Opcode.INVOKE_STATIC_RANGE &&
                    (ins as? ReferenceInstruction)?.reference.toString().startsWith(FAB_SURFACE_CALL)
            }.map { it.index }

            if (surfaceCallIndexes.isEmpty()) continue

            // Patch in reverse order so instruction indexes remain stable
            for (surfaceCallIdx in surfaceCallIndexes.asReversed()) {
                val ins = instructions[surfaceCallIdx]
                val rangeIns = ins as? RegisterRangeInstruction ?: continue
                val a = rangeIns.startRegister
                if (a + 11 > 15) {
                    println("[liquid-glass] skipping call site at index $surfaceCallIdx in ${classDef.type}::${method.name}: startRegister $a + 11 > 15")
                    continue
                }

                val vMod = a + 1
                val vShape = a + 2
                val vColor = a + 3
                val vColorHigh = a + 4
                val vContent = a + 5
                val vContentHigh = a + 6
                val vElev = a + 7
                val vComp = a + 9
                val vInt1 = a + 10
                val vInt2 = a + 11

                val glassInjection =
                    """const/4 v$vMod, 0x0
                    invoke-static {v$vComp, v$vMod}, $GLASS_STYLE_PROVIDER
                    move-result-object v$vMod
                    iget-wide v$vColor, v$vMod, $DIM_PALETTE_FIELD
                    const/high16 v$vMod, 0x3f400000
                    invoke-static {v$vColor, v$vColorHigh, v$vMod}, $COLOR_TRANSLUCENT
                    move-result-wide v$vColor
                    sget-object v$vMod, $EMPTY_MODIFIER
                    invoke-static {v$vComp}, $HAZE_STATE_COMPOSABLE
                    move-result-object v$vInt1
                    const/4 v$vInt2, 0x1
                    const/4 v$vElev, 0x0
                    invoke-static {v$vMod, v$vInt1, v$vInt2, v$vComp, v$vElev}, $GLASS_MODIFIER
                    move-result-object v$vMod
                    const/4 v$vElev, 0x0
                    invoke-static {v$vComp, v$vElev}, $THEME_PROVIDER
                    move-result-object v$vElev
                    iget-wide v$vContent, v$vElev, $THEME_CONTENT_FIELD
                    iget-wide v$vInt1, v$vElev, $THEME_DIVIDER_FIELD
                    const/high16 v$vElev, 0x3f800000
                    invoke-static {v$vMod, v$vElev, v$vInt1, v$vInt2, v$vShape}, $BORDER_MODIFIER
                    move-result-object v$vMod
                    const/4 v$vElev, 0x0
                    const/high16 v$vInt1, 0xc00000
                    const/16 v$vInt2, 0x60""".trimIndent()

                method.addInstructions(surfaceCallIdx, glassInjection)
                totalPatched++
                println("[liquid-glass] applied glass + border to compose button at index $surfaceCallIdx (range v$a..v${a + 11}) in ${classDef.type}::${method.name}")
            }
        }
        if (totalPatched == 0) {
            throw PatchException("Compose button surface call site not found — app version may be incompatible")
        }
        println("[liquid-glass] applied glass + border to $totalPatched compose button call site(s)")
    }
}

@Suppress("unused")
val liquidGlassNewPostsPillPatch = bytecodePatch(
    name = "NewX: Liquid glass new posts pill",
    description =
        "Gives the floating new posts pill (which pops up while scrolling) the same liquid-glass material, " +
            "subtle outline border, and adaptive icon/text color as the bottom navigation bar.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_NEW_X)

    execute {
        val sites = methodsContainingString(NEW_POSTS_TAG)
        if (sites.isEmpty()) {
            throw PatchException("New posts pill renderer with tag \"$NEW_POSTS_TAG\" not found — app version may be incompatible")
        }

        var totalPatched = 0
        for ((classDef, method) in sites) {
            val impl = method.implementation as MutableMethodImplementation
            val instructions = impl.instructions

            // Find Composer register from call to e->P(Composer, I) or fallback to 13
            val compReg = instructions.mapNotNull { ins ->
                if (ins.opcode == Opcode.INVOKE_STATIC && (ins as? ReferenceInstruction)?.reference.toString().startsWith("Lcom/google/android/gms/dynamite/e;->P(")) {
                    (ins as? FiveRegisterInstruction)?.registerC
                } else null
            }.firstOrNull() ?: 13

            // Find sget-wide of white color u1.B1 (icon and text color)
            val whiteSgets = instructions.withIndex().filter { (_, ins) ->
                ins.opcode == Opcode.SGET_WIDE &&
                    (ins as? ReferenceInstruction)?.reference.toString() == WHITE_COLOR_FIELD
            }.map { (idx, ins) -> idx to (ins as OneRegisterInstruction).registerA }

            // Find sget-wide of Twitter blue followed by Modifier.background(p.h)
            val blueBgSites = instructions.withIndex().filter { (idx, ins) ->
                ins.opcode == Opcode.SGET_WIDE &&
                    (ins as? ReferenceInstruction)?.reference.toString() == TWITTER_BLUE_FIELD &&
                    idx + 1 < instructions.size &&
                    instructions[idx + 1].opcode == Opcode.INVOKE_STATIC &&
                    (instructions[idx + 1] as? ReferenceInstruction)?.reference.toString().startsWith("Landroidx/compose/foundation/p;->h(")
            }.map { it.index }

            if (blueBgSites.isEmpty()) continue

            // Patch in reverse order:
            // 1. Text & icon color sites (later in method)
            for ((idx, reg) in whiteSgets.asReversed()) {
                val tail =
                    """invoke-static {v$compReg, v$reg}, $THEME_PROVIDER
                    move-result-object v$reg
                    iget-wide v$reg, v$reg, $THEME_CONTENT_FIELD""".trimIndent()
                method.replaceInstruction(idx, "const/4 v$reg, 0x0")
                method.addInstructions(idx + 1, tail)
                println("[liquid-glass] adapted new posts pill content color at index $idx (reg v$reg) to theme on-surface in ${classDef.type}::${method.name}")
            }

            // 2. Background + Haze + Border site (earlier in method)
            for (blueSgetIdx in blueBgSites.asReversed()) {
                // blueSgetIdx: sget-wide v14, u1.g
                // blueSgetIdx + 1: invoke-static {v3, v14, v15, v8}, p;->h
                // Replace both with liquid glass injection:
                val pillGlassInjection =
                    """const/4 v7, 0x0
                    invoke-static {v$compReg, v7}, $GLASS_STYLE_PROVIDER
                    move-result-object v7
                    iget-wide v14, v7, $DIM_PALETTE_FIELD
                    const/high16 v7, 0x3f400000
                    invoke-static {v14, v15, v7}, $COLOR_TRANSLUCENT
                    move-result-wide v14
                    sget-object v7, $CIRCLE_SHAPE_FIELD
                    invoke-static {v3, v14, v15, v7}, $BACKGROUND_MODIFIER
                    move-result-object v3
                    invoke-static {v$compReg}, $HAZE_STATE_COMPOSABLE
                    move-result-object v7
                    const/4 v14, 0x1
                    const/4 v15, 0x0
                    invoke-static {v3, v7, v14, v$compReg, v15}, $GLASS_MODIFIER
                    move-result-object v3
                    const/4 v7, 0x0
                    invoke-static {v$compReg, v7}, $THEME_PROVIDER
                    move-result-object v7
                    iget-wide v14, v7, $THEME_DIVIDER_FIELD
                    sget-object v7, $CIRCLE_SHAPE_FIELD
                    const/high16 v8, 0x3f800000
                    invoke-static {v3, v8, v14, v15, v7}, $BORDER_MODIFIER""".trimIndent()

                method.removeInstructions(blueSgetIdx, 2)
                method.addInstructions(blueSgetIdx, pillGlassInjection)
                totalPatched++
                println("[liquid-glass] applied liquid glass + border to new posts pill at index $blueSgetIdx in ${classDef.type}::${method.name}")
            }
        }
        if (totalPatched == 0) {
            throw PatchException("Failed to patch new posts pill background call site")
        }
        println("[liquid-glass] applied glass + border to $totalPatched new posts pill call site(s)")
    }
}
