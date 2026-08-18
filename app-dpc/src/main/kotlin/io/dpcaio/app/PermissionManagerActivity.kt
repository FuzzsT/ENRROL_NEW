package io.dpcaio.app

import android.app.Activity
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.UserHandle
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import io.dpcaio.permission.PermissionCatalogClassifier
import io.dpcaio.permission.PermissionSeedQueries
import io.dpcaio.permission.android.AndroidPermissionCatalog
import io.dpcaio.permission.android.AndroidPermissionGrantCoordinator
import io.dpcaio.shizuku.ShizukuUserServiceClient

class PermissionManagerActivity : Activity() {
    private lateinit var output: TextView
    private lateinit var query: EditText
    private lateinit var targetPackage: EditText
    private val shizuku by lazy { ShizukuUserServiceClient(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "DPC-AIO Permission Manager"
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 20, 20, 20) }
        targetPackage = EditText(this).apply { hint = "target package, e.g. com.example.app" }
        query = EditText(this).apply {
            hint = "permission / group / com.sec / com.samsung / com.google..."
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val scan = Button(this).apply { text = "Scan device permissions"; setOnClickListener { scanCatalog() } }
        val extended = Button(this).apply { text = "Extended scan via Shizuku"; setOnClickListener { scanShizuku() } }
        val auto = Button(this).apply { text = "AUTO: requested permissions"; setOnClickListener { autoRequestedPermissions() } }
        val configs = Button(this).apply { text = "System permission/sysconfig XML index"; setOnClickListener { scanSystemConfigs() } }
        output = TextView(this).apply { setTextIsSelectable(true) }
        root.addView(targetPackage)
        root.addView(query, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        root.addView(scan); root.addView(extended); root.addView(auto); root.addView(configs)
        root.addView(output, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setContentView(root)
        shizuku.bind()
    }

    private fun scanCatalog() {
        output.text = "Scanning..."
        Thread {
            val snapshot = AndroidPermissionCatalog(this).scan()
            val q = query.text.toString().trim().lowercase()
            val groups = snapshot.groups.filter { q.isBlank() || it.name.lowercase().contains(q) }
            val perms = snapshot.permissions.filter {
                q.isBlank() || it.name.lowercase().contains(q) || it.group?.lowercase()?.contains(q) == true || it.declaringPackage?.lowercase()?.contains(q) == true
            }
            val text = buildString {
                appendLine("PermissionManager: groups=${snapshot.groups.size}, permissions=${snapshot.permissions.size}")
                appendLine("Undocumented/vendor candidates=${snapshot.permissions.count(PermissionCatalogClassifier::isUndocumentedCandidate)}")
                appendLine("Known search hints: ${PermissionSeedQueries.groupHints.size + PermissionSeedQueries.permissionHints.size}")
                appendLine("\nGROUPS")
                groups.take(200).forEach { appendLine("${it.name}  pkg=${it.declaringPackage}  public=${it.publicSdkConstant}") }
                appendLine("\nPERMISSIONS")
                perms.take(1000).forEach {
                    appendLine("${it.name}\n  group=${it.group} pkg=${it.declaringPackage} protection=${it.protection} raw=0x${it.rawProtectionLevel.toString(16)} flags=0x${it.permissionFlags.toString(16)} public=${it.publicSdkConstant}")
                }
            }
            runOnUiThread { output.text = text }
        }.start()
    }

    @Suppress("DEPRECATION")
    private fun autoRequestedPermissions() {
        val pkg = targetPackage.text.toString().trim()
        if (pkg.isBlank()) { output.text = "Target package required"; return }
        output.text = "Planning/applying verified routes..."
        Thread {
            val snapshot = AndroidPermissionCatalog(this).scan()
            val byName = snapshot.permissions.associateBy { it.name }
            val requested = runCatching {
                packageManager.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS).requestedPermissions?.toList().orEmpty()
            }.getOrElse { emptyList() }
            val entries = requested.mapNotNull(byName::get)
            val coordinator = AndroidPermissionGrantCoordinator(
                this,
                ComponentName(this, AioDeviceAdminReceiver::class.java),
                shizuku = shizuku
            )
            val results = coordinator.grantAllAuto(pkg, UserHandle.myUserId(), entries, systemPrivilegedAvailable = false)
            val text = buildString {
                appendLine("AUTO permission result for $pkg")
                appendLine("requested=${requested.size} catalog-matched=${entries.size}")
                results.forEach { appendLine("${it.permission}: ${it.route} verified=${it.verified} ${it.detail}") }
            }
            runOnUiThread { output.text = text }
        }.start()
    }

    private fun scanSystemConfigs() {
        shizuku.bind()
        output.postDelayed({
            val permissions = shizuku.listPermissionConfigFiles() ?: "permission config unavailable"
            val sysconfig = shizuku.listSysconfigFiles() ?: "sysconfig unavailable"
            output.text = "PERMISSIONS XML\n$permissions\n\nSYSCONFIG XML\n$sysconfig"
        }, 500)
    }

    private fun scanShizuku() {
        shizuku.bind()
        output.postDelayed({ output.text = shizuku.listPermissions() ?: "Shizuku not ready/authorized" }, 500)
    }
}
