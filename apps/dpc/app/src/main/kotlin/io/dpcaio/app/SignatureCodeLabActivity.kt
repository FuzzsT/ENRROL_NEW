package io.dpcaio.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import io.dpcaio.app.engineering.SignatureCodeEvidenceResolver
import io.dpcaio.app.engineering.SigningIdentityReadResult
import io.dpcaio.app.engineering.SigningIdentityReader
import io.dpcaio.engineering.AppSigningIdentity
import io.dpcaio.engineering.AutoSignatureCodeCatalog
import io.dpcaio.engineering.DerivedCodeCandidate
import io.dpcaio.engineering.DerivedCodeKind
import io.dpcaio.engineering.SampleMatcherLimits
import io.dpcaio.engineering.SignatureCodeDerivationEngine
import io.dpcaio.engineering.SignatureCodeDerivationResult
import io.dpcaio.engineering.SignatureCodeInputKind
import io.dpcaio.engineering.SignatureCodeRecipe
import io.dpcaio.engineering.SignatureCodeSample
import io.dpcaio.engineering.SignatureCodeSampleMatcher
import io.dpcaio.engineering.SignatureCodeSampleMatcherResult
import io.dpcaio.engineering.SignatureCodeSource
import io.dpcaio.engineering.SignatureCodeStep
import io.dpcaio.engineering.SignatureCodeVendorProfileMatcher
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SignatureCodeLabActivity : Activity() {
    private lateinit var packageField: EditText
    private lateinit var sampleCodeField: EditText
    private lateinit var customDigitsField: EditText
    private lateinit var signerOutput: TextView
    private lateinit var resultOutput: TextView

    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private val signingReader by lazy { SigningIdentityReader(this) }
    private val derivationEngine = SignatureCodeDerivationEngine()
    private val evidenceResolver by lazy { SignatureCodeEvidenceResolver(this) }

    @Volatile
    private var currentIdentity: AppSigningIdentity? = null
    @Volatile
    private var lastCandidates: List<DerivedCodeCandidate> = emptyList()
    @Volatile
    private var lastFormula: String = ""
    @Volatile
    private var lastReport: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Signature Code Lab"

        val body = ModernUi.page(this)
        body.addView(
            ModernUi.hero(
                this,
                title = "Signature Code Lab",
                subtitle = "Read-only service-code derivation from installed app signing certificates. Derived values are candidates, not execution permission.",
                eyebrow = "SIGNATURE • DERIVATION • RECEIVER EVIDENCE",
            )
        )

        body.addView(ModernUi.section(this, "App / signer identity"))
        body.addView(
            ModernUi.card(this, emphasized = true).apply {
                packageField = ModernUi.field(this@SignatureCodeLabActivity, "Package name / search").apply {
                    setText(savedInstanceState?.getString(STATE_PACKAGE).orEmpty())
                }
                addView(packageField)
                addView(ModernUi.spacer(this@SignatureCodeLabActivity, 8))
                addView(
                    ModernUi.actionRow(
                        this@SignatureCodeLabActivity,
                        ModernUi.action(this@SignatureCodeLabActivity, "Use this app") {
                            packageField.setText(packageName)
                        },
                        ModernUi.action(this@SignatureCodeLabActivity, "Load signer", primary = true) {
                            loadSigner()
                        },
                    )
                )
                signerOutput = ModernUi.body(this@SignatureCodeLabActivity, "No package loaded.\nSHA-256: —\nSHA-1: —\nSigning history: —")
                addView(ModernUi.spacer(this@SignatureCodeLabActivity, 8))
                addView(signerOutput)
            }
        )

        body.addView(ModernUi.section(this, "Auto derive"))
        body.addView(
            ModernUi.card(this).apply {
                addView(ModernUi.body(this@SignatureCodeLabActivity, "Runs the fixed bounded AutoSignatureCodeCatalog against every current signer."))
                addView(ModernUi.action(this@SignatureCodeLabActivity, "Run auto derivation", primary = true) { runAutoDerivation() })
            }
        )

        body.addView(ModernUi.section(this, "Vendor profile"))
        body.addView(
            ModernUi.card(this).apply {
                addView(ModernUi.body(this@SignatureCodeLabActivity, "Vendor profile matching is declarative and fail-closed. Profiles are not invented from certificate subject names."))
                addView(ModernUi.action(this@SignatureCodeLabActivity, "Check vendor profile") { checkVendorProfiles() })
            }
        )

        body.addView(ModernUi.section(this, "Custom formula"))
        body.addView(
            ModernUi.card(this).apply {
                addView(ModernUi.body(this@SignatureCodeLabActivity, "Bounded preset builder: SHA-256(cert DER) → suffix 4 bytes → UINT_BE → MOD 10^N → zero-pad N. N is restricted to 4..8."))
                customDigitsField = ModernUi.field(this@SignatureCodeLabActivity, "Digits 4..8").apply {
                    setText(savedInstanceState?.getString(STATE_CUSTOM_DIGITS) ?: "6")
                }
                addView(customDigitsField)
                addView(ModernUi.action(this@SignatureCodeLabActivity, "Calculate custom formula") { runCustomFormula() })
            }
        )

        body.addView(ModernUi.section(this, "Sample matcher"))
        body.addView(
            ModernUi.card(this).apply {
                addView(ModernUi.body(this@SignatureCodeLabActivity, "Enter a known code for the currently loaded package. One sample stays UNVERIFIED; the matcher never treats it as proof."))
                sampleCodeField = ModernUi.field(this@SignatureCodeLabActivity, "Known code sample").apply {
                    setText(savedInstanceState?.getString(STATE_SAMPLE).orEmpty())
                }
                addView(sampleCodeField)
                addView(ModernUi.action(this@SignatureCodeLabActivity, "Run Sample matcher") { runSampleMatcher() })
            }
        )

        body.addView(ModernUi.section(this, "Receiver evidence"))
        body.addView(
            ModernUi.card(this).apply {
                addView(ModernUi.body(this@SignatureCodeLabActivity, "Compare with receivers uses the read-only manifest inventory and query-only runtime verifier. It never broadcasts a derived code."))
                addView(ModernUi.action(this@SignatureCodeLabActivity, "Compare with receivers", primary = true) { compareWithReceivers() })
            }
        )

        body.addView(ModernUi.section(this, "Results"))
        resultOutput = ModernUi.outputCard(this, "Ready.")
        body.addView(resultOutput)
        body.addView(
            ModernUi.card(this).apply {
                addView(
                    ModernUi.actionRow(
                        this@SignatureCodeLabActivity,
                        ModernUi.action(this@SignatureCodeLabActivity, "Copy candidate") { copyCandidate() },
                        ModernUi.action(this@SignatureCodeLabActivity, "Copy formula") { copyText("formula", lastFormula) },
                    )
                )
                addView(ModernUi.action(this@SignatureCodeLabActivity, "Copy derivation report") { copyText("derivation report", lastReport) })
            }
        )

        setContentView(ModernUi.scrollPage(this, body))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_PACKAGE, packageField.text.toString())
        outState.putString(STATE_SAMPLE, sampleCodeField.text.toString())
        outState.putString(STATE_CUSTOM_DIGITS, customDigitsField.text.toString())
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        currentIdentity = null
        lastCandidates = emptyList()
        lastFormula = ""
        lastReport = ""
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun loadSigner() {
        val target = packageField.text.toString().trim()
        showResult("Loading signing identity…")
        worker.execute {
            when (val result = signingReader.readInstalled(target)) {
                is SigningIdentityReadResult.Success -> {
                    currentIdentity = result.identity
                    lastCandidates = emptyList()
                    val text = renderIdentity(result.identity)
                    runOnUiThread {
                        signerOutput.text = text
                        resultOutput.text = "Signing identity loaded for ${result.identity.packageName}."
                    }
                }
                is SigningIdentityReadResult.Failure -> showResult("Signer read failed: ${result.error}\n${result.message}")
            }
        }
    }

    private fun runAutoDerivation() {
        withIdentity { identity ->
            val candidates = buildList {
                identity.currentSigners.forEach { signer ->
                    AutoSignatureCodeCatalog.recipes().forEach { recipe ->
                        val result = derivationEngine.derive(identity, recipe, signer, source = SignatureCodeSource.AUTO)
                        if (result is SignatureCodeDerivationResult.Success) add(result.candidate)
                    }
                }
            }
            lastCandidates = candidates
            lastFormula = "AutoSignatureCodeCatalog (${AutoSignatureCodeCatalog.recipes().size} fixed recipes)"
            lastReport = renderCandidates("AUTO DERIVATION", candidates)
            showResult(lastReport)
        }
    }

    private fun checkVendorProfiles() {
        withIdentity { identity ->
            val matches = SignatureCodeVendorProfileMatcher.match(identity, emptyList())
            val text = if (matches.isEmpty()) {
                "Vendor profile\nNo active built-in profile matched. The profile engine is fail-closed until verified profiles with provenance are supplied."
            } else {
                buildString {
                    appendLine("Vendor profile matches=${matches.size}")
                    matches.forEach { appendLine("${it.profile.id}: ${it.reason}") }
                }.trimEnd()
            }
            lastReport = text
            showResult(text)
        }
    }

    private fun runCustomFormula() {
        val digits = customDigitsField.text.toString().toIntOrNull()
        if (digits == null || digits !in 4..8) {
            showResult("Custom formula rejected: digits must be 4..8.")
            return
        }
        withIdentity { identity ->
            val modulus = pow10(digits)
            val recipe = SignatureCodeRecipe(
                id = "ui-custom-sha256-suffix4-$digits",
                displayName = "Custom SHA-256 suffix4 → $digits digits",
                input = SignatureCodeInputKind.CERT_DER,
                steps = listOf(
                    SignatureCodeStep.Sha256,
                    SignatureCodeStep.SuffixBytes(4),
                    SignatureCodeStep.UIntBe,
                    SignatureCodeStep.Mod(modulus),
                    SignatureCodeStep.Decimal,
                    SignatureCodeStep.PadLeft(digits),
                ),
                outputKind = DerivedCodeKind.NUMERIC,
            )
            val candidates = identity.currentSigners.mapNotNull { signer ->
                when (val result = derivationEngine.derive(identity, recipe, signer, source = SignatureCodeSource.CUSTOM_FORMULA)) {
                    is SignatureCodeDerivationResult.Success -> result.candidate
                    is SignatureCodeDerivationResult.Failure -> null
                }
            }
            lastCandidates = candidates
            lastFormula = recipe.steps.joinToString(" → ") { it.toString() }
            lastReport = renderCandidates("CUSTOM FORMULA", candidates)
            showResult(lastReport)
        }
    }

    private fun runSampleMatcher() {
        val expected = sampleCodeField.text.toString().trim()
        if (expected.isEmpty()) {
            showResult("Sample matcher requires a known code sample.")
            return
        }
        withIdentity { identity ->
            val result = SignatureCodeSampleMatcher.match(
                samples = listOf(SignatureCodeSample(identity, expected)),
                limits = SampleMatcherLimits(maxSamples = 32, maxRecipesEvaluated = 5_000),
            )
            lastReport = when (result) {
                is SignatureCodeSampleMatcherResult.Success -> renderSampleMatches(result.matches, result.evaluatedRecipes)
                is SignatureCodeSampleMatcherResult.SearchLimitReached -> "SEARCH LIMIT REACHED after ${result.evaluatedRecipes} recipes\n${renderSampleMatches(result.matches, result.evaluatedRecipes)}"
                is SignatureCodeSampleMatcherResult.Failure -> "Sample matcher failed: ${result.error}\n${result.message}"
            }
            showResult(lastReport)
        }
    }

    private fun compareWithReceivers() {
        val candidate = lastCandidates.firstOrNull()
        if (candidate == null) {
            showResult("Derive at least one candidate first.")
            return
        }
        showResult("Comparing ${candidate.candidate} with manifest/runtime receiver evidence…")
        worker.execute {
            val evidence = evidenceResolver.resolve(candidate)
            lastReport = buildString {
                appendLine("RECEIVER EVIDENCE")
                appendLine("candidate=${evidence.normalizedCandidate}")
                appendLine("evidence=${evidence.evidence.joinToString()}")
                appendLine("manifest matches=${evidence.inventoryMatches.size}")
                evidence.inventoryMatches.take(20).forEach {
                    appendLine("  ${it.packageName}/${it.receiverClassName} permission=${it.requiredPermission ?: "none"}")
                }
                appendLine("runtime receivers=${evidence.runtimeReceivers.size}")
                evidence.runtimeReceivers.take(20).forEach {
                    appendLine("  ${it.packageName}/${it.className} enabled=${it.enabled} exported=${it.exported} permission=${it.requiredPermission ?: "none"}")
                }
            }.trimEnd()
            showResult(lastReport)
        }
    }

    private fun withIdentity(block: (AppSigningIdentity) -> Unit) {
        val identity = currentIdentity
        if (identity == null) {
            showResult("Load an installed package signer first.")
            return
        }
        showResult("Working…")
        worker.execute { block(identity) }
    }

    private fun renderIdentity(identity: AppSigningIdentity): String = buildString {
        appendLine("package=${identity.packageName}")
        appendLine("versionCode=${identity.versionCode ?: "unknown"}")
        appendLine("current signers=${identity.currentSigners.size}")
        identity.currentSigners.forEachIndexed { index, signer ->
            appendLine("signer[$index] SHA-256: ${signer.sha256Hex}")
            appendLine("signer[$index] SHA-1: ${signer.sha1Hex}")
            signer.subjectSummary?.let { appendLine("subject=$it") }
        }
        appendLine("Signing history: ${identity.signingHistory.size}")
        identity.signingHistory.forEachIndexed { index, signer -> appendLine("history[$index] SHA-256: ${signer.sha256Hex}") }
    }.trimEnd()

    private fun renderCandidates(title: String, candidates: List<DerivedCodeCandidate>): String = buildString {
        appendLine(title)
        appendLine("candidates=${candidates.size}")
        candidates.forEachIndexed { index, candidate ->
            appendLine("[$index] ${candidate.candidate} • ${candidate.recipeName}")
            appendLine("  signer=${candidate.signerFingerprintSha256}")
            appendLine("  source=${candidate.source}")
        }
    }.trimEnd()

    private fun renderSampleMatches(matches: List<io.dpcaio.engineering.SignatureCodeSampleMatch>, evaluated: Int): String = buildString {
        appendLine("Sample matcher")
        appendLine("evaluated recipes=$evaluated matches=${matches.size}")
        matches.take(20).forEach { match ->
            appendLine("${match.recipe.displayName}: ${match.exactMatches}/${match.sampleCount} confidence=${match.confidence} ambiguity=${match.ambiguityCount}")
        }
        if (matches.isEmpty()) append("No bounded formula matched the supplied sample.")
    }.trimEnd()

    private fun copyCandidate() {
        copyText("candidate", lastCandidates.firstOrNull()?.candidate.orEmpty())
    }

    private fun copyText(label: String, value: String) {
        if (value.isBlank()) {
            showResult("Nothing to copy for $label.")
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        showResult("Copied $label.")
    }

    private fun showResult(text: String) {
        runOnUiThread { resultOutput.text = text }
    }

    private fun pow10(digits: Int): ULong {
        var value = 1uL
        repeat(digits) { value *= 10uL }
        return value
    }

    companion object {
        private const val STATE_PACKAGE = "signature_code_lab.package"
        private const val STATE_SAMPLE = "signature_code_lab.sample"
        private const val STATE_CUSTOM_DIGITS = "signature_code_lab.custom_digits"
    }
}
