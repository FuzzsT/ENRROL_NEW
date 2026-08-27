package io.dpcaio.app

import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.dpcaio.core.model.CapabilityRequirements
import io.dpcaio.core.model.CapabilityResolver
import io.dpcaio.core.model.OwnershipRequirement
import io.dpcaio.core.model.RiskClass
import io.dpcaio.policy.android.AndroidDevicePolicyGateway
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory

class CredentialCenterActivity : Activity() {
    companion object {
        private const val REQUEST_CA = 4101
        private const val REQUEST_PKCS12 = 4102
    }

    private lateinit var gateway: AndroidDevicePolicyGateway

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Certificate & Credential Center"
        gateway = AndroidDevicePolicyGateway(this, ComponentName(this, AioDeviceAdminReceiver::class.java))
        render()
    }

    private fun render() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPaddingDp(24, 24, 24, 24) }
        val cap = CapabilityResolver.resolve(
            CapabilityRequirements(
                minApi = 21,
                ownership = OwnershipRequirement.DEVICE_OR_PROFILE_OWNER,
                risk = RiskClass.HIGH,
            ),
            ManagementContextFactory.create(this),
        )
        root.addView(TextView(this).apply { text = "Certificate & Credential Center\n${cap.availability}"; textSize = 20f })
        val certs = gateway.getInstalledCaCertificates()
        root.addView(TextView(this).apply { text = "CA Certificates • INSTALLED_USER_CA count=${certs.value?.size ?: 0}" })
        root.addView(TextView(this).apply { text = "Private key export: NOT PERMITTED" })

        root.addView(Button(this).apply {
            text = "Import CA PEM/DER"
            isEnabled = cap.executable
            setOnClickListener { openCaDocument() }
        })
        root.addView(Button(this).apply {
            text = "Import PKCS#12"
            isEnabled = cap.executable
            setOnClickListener { openPkcs12Document() }
        })

        val alias = EditText(this).apply { hint = "Key-pair alias" }
        val pkg = EditText(this).apply { hint = "Target package" }
        root.addView(alias)
        root.addView(pkg)
        root.addView(Button(this).apply {
            text = "Key-pair grant inventory"
            isEnabled = cap.executable
            setOnClickListener { show(gateway.getManagedKeyPairGrants(alias.text.toString()).toString()) }
        })
        root.addView(Button(this).apply {
            text = "Grant key pair to app"
            isEnabled = cap.executable
            setOnClickListener { show(gateway.grantManagedKeyPairToApp(alias.text.toString(), pkg.text.toString()).toString()) }
        })

        val delegate = EditText(this).apply { hint = "Certificate delegate package" }
        root.addView(delegate)
        root.addView(Button(this).apply {
            text = "Set certificate delegation"
            isEnabled = cap.executable
            setOnClickListener {
                val scopes = setOf(DevicePolicyManager.DELEGATION_CERT_INSTALL, DevicePolicyManager.DELEGATION_CERT_SELECTION)
                show(gateway.setDelegatedScopes(delegate.text.toString(), scopes).toString())
            }
        })

        root.addView(TextView(this).apply {
            text = "Imports use Android Storage Access Framework. PEM/DER is normalized as X.509; PKCS#12 is parsed in memory and only installed into the managed KeyChain. This UI never exposes private-key export."
        })
        root.addView(Button(this).apply {
            text = "Remove all shown CA certificates"
            isEnabled = cap.executable && !certs.value.isNullOrEmpty()
            setOnClickListener {
                AlertDialog.Builder(this@CredentialCenterActivity)
                    .setTitle("Confirm CA removal")
                    .setMessage("Remove all listed user CA certificates managed/visible to this admin?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Remove") { _, _ ->
                        certs.value.orEmpty().forEach(gateway::uninstallCaCertificate)
                        render()
                    }
                    .show()
            }
        })
        setContentView(DpcUiShell.scroll(this, root))
    }

    private fun openCaDocument() {
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(
                    Intent.EXTRA_MIME_TYPES,
                    arrayOf("application/x-x509-ca-cert", "application/pkix-cert", "application/x-pem-file", "text/plain"),
                )
            },
            REQUEST_CA,
        )
    }

    private fun openPkcs12Document() {
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/x-pkcs12"
            },
            REQUEST_PKCS12,
        )
    }

    @Deprecated("Legacy Activity result API retained to avoid adding an AndroidX dependency to the DPC shell")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            REQUEST_CA -> importCaFromUri(uri)
            REQUEST_PKCS12 -> requestPkcs12Password(uri)
        }
    }

    private fun importCaFromUri(uri: Uri) {
        val result = runCatching {
            contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Unable to open certificate document" }
                val certificate = CertificateFactory.getInstance("X.509").generateCertificate(input)
                gateway.installCaCertificate(certificate.encoded)
            }
        }
        show(result.fold({ it.toString() }, { "CERT_INVALID: ${it.javaClass.simpleName}: ${it.message}" }))
        render()
    }

    private fun requestPkcs12Password(uri: Uri) {
        val password = EditText(this).apply {
            hint = "PKCS#12 password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle("Import PKCS#12")
            .setMessage("The password is used in memory for this import and is not persisted.")
            .setView(password)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Import") { _, _ -> importPkcs12FromUri(uri, password.text.toString().toCharArray()) }
            .show()
    }

    private fun importPkcs12FromUri(uri: Uri, password: CharArray) {
        val result = runCatching {
            val keyStore = KeyStore.getInstance("PKCS12")
            contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Unable to open PKCS#12 document" }
                keyStore.load(input, password)
            }
            val aliases = keyStore.aliases()
            var sourceAlias: String? = null
            while (aliases.hasMoreElements()) {
                val candidate = aliases.nextElement()
                if (keyStore.isKeyEntry(candidate)) {
                    sourceAlias = candidate
                    break
                }
            }
            val selectedAlias = requireNotNull(sourceAlias) { "PKCS#12 contains no private-key entry" }
            val privateKey = keyStore.getKey(selectedAlias, password) as? PrivateKey
                ?: error("PKCS#12 key entry is not a PrivateKey")
            val chain: List<Certificate> = keyStore.getCertificateChain(selectedAlias)?.toList().orEmpty()
            require(chain.isNotEmpty()) { "PKCS#12 certificate chain is empty" }
            gateway.installManagedKeyPair(privateKey, chain, selectedAlias, false)
        }
        password.fill('\u0000')
        show(result.fold({ it.toString() }, { "KEY_IMPORT_FAILED: ${it.javaClass.simpleName}: ${it.message}" }))
        render()
    }

    private fun show(message: String) = AlertDialog.Builder(this).setMessage(message).setPositiveButton("OK", null).show()
}
