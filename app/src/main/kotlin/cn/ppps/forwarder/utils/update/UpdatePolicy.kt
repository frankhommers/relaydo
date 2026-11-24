package cn.ppps.forwarder.utils.update

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import java.util.Locale

/**
 * Pure, Android-free rules of the in-app updater. Everything that decides whether a file may be
 * handed to the package installer lives here so it can be unit tested on the JVM.
 *
 * Source: GitHub Releases of the owner's fork. Releases are pre-releases, so the release LIST is read
 * (the "latest" endpoint skips pre-releases). Each release carries the universal APK plus a small JSON
 * manifest (MANIFEST_ASSET) produced by scripts/release.py.
 */
object UpdatePolicy {
    const val OWNER = "frankhommers"
    const val REPO = "relaydo"
    const val PACKAGE_NAME = "tools.franks.relaydo"

    /** The only API endpoint the updater ever requests. */
    const val RELEASES_URL = "https://api.github.com/repos/$OWNER/$REPO/releases?per_page=10"

    /** Asset name of the manifest inside every release. */
    const val MANIFEST_ASSET = "smsforwarder-update.json"
    const val MANIFEST_FORMAT = 1

    /** Tag naming: build-<build number>, for example build-59 for universal versionCode 100059 (never starts with "v"). */
    const val TAG_PREFIX = "build-"

    /** app/build.gradle: universal APK versionCode = 1 * 100000 + build_versions.version_code. */
    const val UNIVERSAL_BASE = 100000L

    const val MAX_LIST_BYTES = 1L * 1024 * 1024
    const val MAX_MANIFEST_BYTES = 64L * 1024
    const val MAX_APK_BYTES = 80L * 1024 * 1024
    const val MAX_REDIRECTS = 5

    /** Hosts the updater may talk to. Release assets redirect from github.com to a githubusercontent.com host. */
    private val EXACT_HOSTS = setOf("api.github.com", "github.com")
    private const val ASSET_HOST_SUFFIX = ".githubusercontent.com"

    fun isAllowedHost(host: String?): Boolean {
        if (host.isNullOrEmpty()) return false
        val h = host.lowercase(Locale.ROOT).trimEnd('.')
        if (h.any { !(it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '.') }) return false
        return h in EXACT_HOSTS || (h.endsWith(ASSET_HOST_SUFFIX) && h.length > ASSET_HOST_SUFFIX.length)
    }

    /** HTTPS, default port, an allowed host, no user info. */
    fun isAllowedUrl(url: String?): Boolean = try {
        val u = URI(url ?: "")
        u.scheme.equals("https", ignoreCase = true) && u.rawUserInfo == null &&
            (u.port == -1 || u.port == 443) && isAllowedHost(u.host)
    } catch (_: Exception) { false }

    /**
     * Downloads must come from this repository's own release of the given tag: the github.com release download
     * path. Redirect targets after that are checked separately with [isAllowedUrl].
     */
    fun isReleaseDownloadUrl(url: String?, tag: String, name: String): Boolean {
        if (!isAllowedUrl(url)) return false
        val u = URI(url)
        return u.host.equals("github.com", ignoreCase = true) && u.rawQuery == null &&
            u.rawPath == "/$OWNER/$REPO/releases/download/$tag/$name"
    }

    data class Manifest(
        val buildNumber: Int,
        val versionCode: Int,
        val versionName: String,
        val apkName: String,
        val apkSize: Long,
        val apkSha256: String,
        val certSha256: String,
        val packageName: String,
    )

    data class Asset(val name: String, val size: Long, val apiUrl: String, val downloadUrl: String)
    data class Release(val tag: String, val name: String, val draft: Boolean, val prerelease: Boolean, val assets: List<Asset>)

    /** A release whose manifest has been read and validated, with the matching APK asset. */
    data class Candidate(val release: Release, val manifest: Manifest, val apk: Asset)

    private val HEX64 = Regex("[0-9a-f]{64}")
    private val APK_NAME = Regex("[A-Za-z0-9._-]{1,120}\\.apk")

    fun normalizeHex(value: String): String = value.replace(":", "").trim().lowercase(Locale.ROOT)

    /** Parses and validates a manifest. Returns null for anything malformed: such releases are ignored. */
    private fun JsonObject.str(key: String): String {
        val e = get(key)
        require(e != null && e.isJsonPrimitive && e.asJsonPrimitive.isString) { key }
        return e.asString
    }
    private fun JsonObject.num(key: String): Long {
        val e = get(key)
        require(e != null && e.isJsonPrimitive && e.asJsonPrimitive.isNumber) { key }
        val d = e.asBigDecimal
        return d.longValueExact()
    }
    private fun JsonObject.optStr(key: String): String = get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString ?: ""
    private fun JsonObject.optLong(key: String): Long = get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong ?: -1L
    private fun JsonObject.optBool(key: String): Boolean = get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: false

    fun parseManifest(text: String): Manifest? = try {
        val o = JsonParser.parseString(text).asJsonObject
        val code = o.num("versionCode")
        require(code in 1..Int.MAX_VALUE)
        val build = o.num("buildNumber")
        require(build in 1..Int.MAX_VALUE)
        val m = Manifest(
            buildNumber = build.toInt(),
            versionCode = code.toInt(),
            versionName = o.str("versionName"),
            apkName = o.str("apk"),
            apkSize = o.num("apkSize"),
            apkSha256 = normalizeHex(o.str("apkSha256")),
            certSha256 = normalizeHex(o.str("certSha256")),
            packageName = o.str("packageName"),
        )
        val formatOk = o.optLong("format") == MANIFEST_FORMAT.toLong()
        if (formatOk && m.versionCode > 0 && m.versionName.isNotBlank() && m.versionName.length <= 64 &&
            APK_NAME.matches(m.apkName) && m.apkSize in 1..MAX_APK_BYTES &&
            HEX64.matches(m.apkSha256) && HEX64.matches(m.certSha256)
        ) m else null
    } catch (_: Exception) { null }

    /** Throws on anything that is not a JSON array (the caller reports a malformed list). */
    fun parseReleases(text: String): List<Release> {
        val array = JsonParser.parseString(text).asJsonArray
        return array.mapNotNull { e: JsonElement ->
            val r = e.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val assets = r.get("assets")?.takeIf { it.isJsonArray }?.asJsonArray
            Release(
                tag = r.optStr("tag_name"),
                name = r.optStr("name"),
                draft = r.optBool("draft"),
                prerelease = r.optBool("prerelease"),
                assets = assets?.mapNotNull { ae ->
                    val a = ae.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                    Asset(a.optStr("name"), a.optLong("size"), a.optStr("url"), a.optStr("browser_download_url"))
                } ?: emptyList(),
            )
        }
    }

    /** Releases we consider at all: published (not draft), our tag scheme, with a manifest asset of this repo. */
    fun manifestAsset(release: Release): Asset? {
        if (release.draft || !release.tag.startsWith(TAG_PREFIX)) return null
        return release.assets.firstOrNull { it.name == MANIFEST_ASSET && it.size in 1..MAX_MANIFEST_BYTES && isReleaseDownloadUrl(it.downloadUrl, release.tag, it.name) }
    }

    /** Ties a manifest to its release: tag matches versionCode, package is ours, APK asset present with the declared size. */
    fun candidate(release: Release, manifest: Manifest?): Candidate? {
        if (manifest == null) return null
        if (manifest.packageName != PACKAGE_NAME) return null
        if (release.tag != TAG_PREFIX + manifest.buildNumber) return null
        if (manifest.versionCode.toLong() != UNIVERSAL_BASE + manifest.buildNumber) return null
        val apk = release.assets.firstOrNull { it.name == manifest.apkName } ?: return null
        if (apk.size != manifest.apkSize || !isReleaseDownloadUrl(apk.downloadUrl, release.tag, apk.name)) return null
        return Candidate(release, manifest, apk)
    }

    /** Universal APKs use versionCode = 100000 + build code (app/build.gradle). Both sides are compared as-is. */
    fun isNewer(candidateVersionCode: Long, installedVersionCode: Long): Boolean = candidateVersionCode > installedVersionCode

    /** Highest valid candidate that is newer than the installed version, or null when up to date. */
    fun best(candidates: List<Candidate>, installedVersionCode: Long): Candidate? =
        candidates.filter { isNewer(it.manifest.versionCode.toLong(), installedVersionCode) }.maxByOrNull { it.manifest.versionCode }

    enum class Failure { SIZE_MISMATCH, SHA256_MISMATCH, NOT_AN_APK, WRONG_PACKAGE, NOT_NEWER, MANIFEST_VERSION_MISMATCH, CERT_MISMATCH, CERT_UNREADABLE }

    /** Facts read from the downloaded file and from the running app; [verify] decides. */
    data class Facts(
        val fileSize: Long,
        val fileSha256: String,
        val apkPackage: String?,
        val apkVersionCode: Long?,
        val apkCertSha256: Set<String>,
        val installedVersionCode: Long,
        val installedCertSha256: Set<String>,
    )

    /** All checks must pass; the first failing one is reported. Certificates come from PackageManager, never from the manifest. */
    fun verify(manifest: Manifest, f: Facts): Failure? = when {
        f.fileSize != manifest.apkSize -> Failure.SIZE_MISMATCH
        normalizeHex(f.fileSha256) != manifest.apkSha256 -> Failure.SHA256_MISMATCH
        f.apkPackage == null || f.apkVersionCode == null -> Failure.NOT_AN_APK
        f.apkPackage != PACKAGE_NAME -> Failure.WRONG_PACKAGE
        !isNewer(f.apkVersionCode, f.installedVersionCode) -> Failure.NOT_NEWER
        f.apkVersionCode != manifest.versionCode.toLong() -> Failure.MANIFEST_VERSION_MISMATCH
        f.installedCertSha256.isEmpty() || f.apkCertSha256.isEmpty() -> Failure.CERT_UNREADABLE
        f.apkCertSha256.map(::normalizeHex).toSet() != f.installedCertSha256.map(::normalizeHex).toSet() -> Failure.CERT_MISMATCH
        else -> null
    }

    /**
     * Test-only transport rewrite. [fixtureOrigin] comes from BuildConfig and is empty in every build made
     * without the Gradle property updaterFixtureOrigin (the release script refuses such builds). All policy checks
     * run on the logical GitHub URL; only the socket destination changes, to origin + "/" + host + path.
     */
    fun transportUrl(logicalUrl: String, fixtureOrigin: String): String {
        if (fixtureOrigin.isEmpty()) return logicalUrl
        val u = URI(logicalUrl)
        return fixtureOrigin.trimEnd('/') + "/" + u.host.lowercase(Locale.ROOT) + (u.rawPath ?: "") + (u.rawQuery?.let { "?$it" } ?: "")
    }

    /** Resolves a redirect Location against the current logical URL; null when unusable. */
    fun resolveRedirect(currentLogicalUrl: String, location: String?): String? = try {
        if (location.isNullOrBlank()) null else URI(currentLogicalUrl).resolve(location.trim()).toString()
    } catch (_: Exception) { null }

    /** Host of a URL for error messages; never throws. */
    fun hostOf(url: String?): String = try { URI(url ?: "").host ?: (url ?: "") } catch (_: Exception) { url ?: "" }

    /** Build number from a tag such as build-59, or null. */
    fun tagBuildNumber(tag: String): Int? = if (tag.startsWith(TAG_PREFIX)) tag.removePrefix(TAG_PREFIX).toIntOrNull()?.takeIf { it > 0 } else null

    /** Releases ordered newest build first, limited to our tag scheme. */
    fun orderedReleases(releases: List<Release>): List<Release> =
        releases.filter { !it.draft && tagBuildNumber(it.tag) != null }.sortedByDescending { tagBuildNumber(it.tag) }

    const val MAX_MANIFESTS_PER_CHECK = 3

    /**
     * Signer set used for the certificate comparison: the APK's current signers (apkContentsSigners, API 28+),
     * falling back to the legacy signature list only when those are unavailable. Rotation lineage is never used.
     */
    fun currentSigners(apkContentsSigners: List<String>?, legacySignatures: List<String>?): Set<String> =
        (apkContentsSigners?.takeIf { it.isNotEmpty() } ?: legacySignatures ?: emptyList()).map(::normalizeHex).toSet()

    /** Once a day at most; a failed or rate-limited check waits at least this long too (WorkManager backoff on top). */
    const val CHECK_INTERVAL_HOURS = 24L
}

/** One update pass at a time (manual and automatic share it). */
class SinglePass {
    private val running = java.util.concurrent.atomic.AtomicBoolean(false)
    val busy: Boolean get() = running.get()
    /** Runs [block] unless a pass is already running; returns whether it ran. */
    fun tryRun(block: () -> Unit): Boolean {
        if (!running.compareAndSet(false, true)) return false
        try { block() } finally { running.set(false) }
        return true
    }
}
