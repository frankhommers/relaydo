package cn.ppps.forwarder.utils.update

import org.junit.Assert.*
import org.junit.Test

class UpdatePolicyTest {
    private val cert = "546f8d56f4f13beec65be2ff882cef169e87f03a6c7a715bdef79d060aeec123"
    private val sha = "a".repeat(64)
    private fun manifestJson(build: Int = 59, code: Int = 100000 + build, pkg: String = "tools.franks.relaydo", apk: String = "SmsForwarder-$build-debug.apk", extra: String = "") =
        """{"format":1,"packageName":"$pkg","buildNumber":$build,"versionCode":$code,"versionName":"3.5.1-test.$build","apk":"$apk","apkSize":1234,"apkSha256":"$sha","certSha256":"$cert"$extra}"""

    private fun release(build: Int, manifest: Boolean = true, apkHost: String = "github.com", tag: String = "build-$build", draft: Boolean = false): String {
        val base = "https://$apkHost/frankhommers/relaydo/releases/download/$tag"
        val m = if (manifest) """,{"name":"smsforwarder-update.json","size":400,"url":"https://api.github.com/repos/frankhommers/relaydo/releases/assets/2","browser_download_url":"https://github.com/frankhommers/relaydo/releases/download/$tag/smsforwarder-update.json"}""" else ""
        return """{"tag_name":"$tag","name":"Test build $build","draft":$draft,"prerelease":true,"assets":[{"name":"SmsForwarder-$build-debug.apk","size":1234,"url":"https://api.github.com/repos/frankhommers/relaydo/releases/assets/1","browser_download_url":"$base/SmsForwarder-$build-debug.apk"}$m]}"""
    }

    @Test fun manifestParsing() {
        val m = UpdatePolicy.parseManifest(manifestJson())!!
        assertEquals(59, m.buildNumber); assertEquals(100059, m.versionCode); assertEquals(cert, m.certSha256)
        // Colon-separated, upper-case hex is normalised.
        val colon = cert.uppercase().chunked(2).joinToString(":")
        assertEquals(cert, UpdatePolicy.parseManifest(manifestJson().replace(cert, colon))!!.certSha256)
        assertNull(UpdatePolicy.parseManifest("not json"))
        assertNull(UpdatePolicy.parseManifest("[]"))
        assertNull(UpdatePolicy.parseManifest(manifestJson().replace("\"format\":1", "\"format\":2")))
        assertNull(UpdatePolicy.parseManifest(manifestJson().replace(sha, "abc")))
        assertNull(UpdatePolicy.parseManifest(manifestJson().replace("\"apkSize\":1234", "\"apkSize\":0")))
        assertNull(UpdatePolicy.parseManifest(manifestJson().replace("\"apkSize\":1234", "\"apkSize\":\"1234\"")))
        assertNull(UpdatePolicy.parseManifest(manifestJson().replace("\"versionCode\":100059", "\"versionCode\":100059.5")))
        assertNull(UpdatePolicy.parseManifest(manifestJson(apk = "../evil.apk")))
        assertNull(UpdatePolicy.parseManifest(manifestJson(apk = "SmsForwarder.zip")))
        assertNull(UpdatePolicy.parseManifest(manifestJson().replace(",\"certSha256\":\"$cert\"", "")))
    }

    @Test fun versionComparison() {
        assertTrue(UpdatePolicy.isNewer(100059, 100058))
        assertFalse(UpdatePolicy.isNewer(100058, 100058))
        assertFalse(UpdatePolicy.isNewer(100057, 100058))
        // Per-ABI builds (e.g. x86_64 = 500058) are never downgraded by a universal update.
        assertFalse(UpdatePolicy.isNewer(100059, 500058))
    }

    @Test fun hostAllowList() {
        assertTrue(UpdatePolicy.isAllowedUrl("https://api.github.com/repos/frankhommers/relaydo/releases?per_page=10"))
        assertTrue(UpdatePolicy.isAllowedUrl("https://github.com/frankhommers/relaydo/releases/download/build-59/x.apk"))
        assertTrue(UpdatePolicy.isAllowedUrl("https://release-assets.githubusercontent.com/github-production-release-asset/1/2?sig=x"))
        assertTrue(UpdatePolicy.isAllowedUrl("https://objects.githubusercontent.com/x"))
        assertFalse(UpdatePolicy.isAllowedUrl("http://github.com/x"))
        assertFalse(UpdatePolicy.isAllowedUrl("https://github.com:8443/x"))
        assertFalse(UpdatePolicy.isAllowedUrl("https://user@github.com/x"))
        assertFalse(UpdatePolicy.isAllowedUrl("https://github.com.evil.example/x"))
        assertFalse(UpdatePolicy.isAllowedUrl("https://evilgithub.com/x"))
        assertFalse(UpdatePolicy.isAllowedUrl("https://githubusercontent.com.evil.example/x"))
        assertFalse(UpdatePolicy.isAllowedUrl("https://.githubusercontent.com/x"))
        assertFalse(UpdatePolicy.isAllowedUrl("https://evil.example/x"))
        assertFalse(UpdatePolicy.isAllowedUrl("https://10.0.2.2/x"))
        assertFalse(UpdatePolicy.isAllowedUrl("https://raw.github.io/x"))
        assertFalse(UpdatePolicy.isAllowedUrl("file:///sdcard/x.apk"))
        assertFalse(UpdatePolicy.isAllowedUrl(null))
        assertTrue(UpdatePolicy.isAllowedHost("GitHub.com."))
        // The release download path must be this repository and this tag exactly.
        assertTrue(UpdatePolicy.isReleaseDownloadUrl("https://github.com/frankhommers/relaydo/releases/download/build-59/a.apk", "build-59", "a.apk"))
        assertFalse(UpdatePolicy.isReleaseDownloadUrl("https://github.com/someone/relaydo/releases/download/build-59/a.apk", "build-59", "a.apk"))
        assertFalse(UpdatePolicy.isReleaseDownloadUrl("https://github.com/frankhommers/relaydo/releases/download/build-60/a.apk", "build-59", "a.apk"))
        assertFalse(UpdatePolicy.isReleaseDownloadUrl("https://objects.githubusercontent.com/frankhommers/relaydo/releases/download/build-59/a.apk", "build-59", "a.apk"))
    }

    @Test fun redirectResolutionAndTransport() {
        assertEquals("https://objects.githubusercontent.com/a?b=c", UpdatePolicy.resolveRedirect("https://github.com/x/y", "https://objects.githubusercontent.com/a?b=c"))
        assertEquals("https://github.com/x/z", UpdatePolicy.resolveRedirect("https://github.com/x/y", "z"))
        assertNull(UpdatePolicy.resolveRedirect("https://github.com/x/y", null))
        assertEquals("https://api.github.com/repos/a", UpdatePolicy.transportUrl("https://api.github.com/repos/a", ""))
        assertEquals("https://10.0.2.2:8443/api.github.com/repos/a?per_page=10", UpdatePolicy.transportUrl("https://api.github.com/repos/a?per_page=10", "https://10.0.2.2:8443/"))
    }

    @Test fun releaseSelection() {
        val list = "[" + listOf(release(61, manifest = false), release(60), release(59), release(62, draft = true), release(63, tag = "v63")).joinToString(",") + "]"
        val releases = UpdatePolicy.parseReleases(list)
        val ordered = UpdatePolicy.orderedReleases(releases)
        assertEquals(listOf("build-61", "build-60", "build-59"), ordered.map { it.tag })
        assertNull("release without manifest is ignored", UpdatePolicy.manifestAsset(ordered[0]))
        val r60 = ordered[1]
        assertNotNull(UpdatePolicy.manifestAsset(r60))
        val c60 = UpdatePolicy.candidate(r60, UpdatePolicy.parseManifest(manifestJson(60)))!!
        assertEquals(100060, c60.manifest.versionCode)
        assertNull("tag must match the manifest", UpdatePolicy.candidate(r60, UpdatePolicy.parseManifest(manifestJson(59))))
        assertNull("versionCode must be universal base + build", UpdatePolicy.candidate(r60, UpdatePolicy.parseManifest(manifestJson(60, code = 300060))))
        assertNull("package must be ours", UpdatePolicy.candidate(r60, UpdatePolicy.parseManifest(manifestJson(60, pkg = "cn.ppps.forwarder"))))
        assertNull("apk asset must exist", UpdatePolicy.candidate(r60, UpdatePolicy.parseManifest(manifestJson(60, apk = "Other.apk"))))
        val foreign = UpdatePolicy.parseReleases("[" + release(60, apkHost = "evil.example") + "]")[0]
        assertNull("apk download must be this repository's release", UpdatePolicy.candidate(foreign, UpdatePolicy.parseManifest(manifestJson(60))))
        val c59 = UpdatePolicy.candidate(ordered[2], UpdatePolicy.parseManifest(manifestJson(59)))!!
        assertEquals(c60, UpdatePolicy.best(listOf(c59, c60), 100058))
        assertNull("same version is not offered", UpdatePolicy.best(listOf(c59), 100059))
        assertNull("lower version is not offered", UpdatePolicy.best(listOf(c59), 100060))
        assertThrowsAny { UpdatePolicy.parseReleases("{\"message\":\"Not Found\"}") }
    }

    private fun assertThrowsAny(block: () -> Unit) { try { block() } catch (_: Exception) { return }; fail("expected exception") }

    private val m = UpdatePolicy.parseManifest(manifestJson())!!
    private fun facts(size: Long = 1234, sha256: String = sha, pkg: String? = "tools.franks.relaydo", code: Long? = 100059, apkCert: Set<String> = setOf(cert), installed: Long = 100058, installedCert: Set<String> = setOf(cert)) =
        UpdatePolicy.Facts(size, sha256, pkg, code, apkCert, installed, installedCert)

    @Test fun verificationDecisions() {
        assertNull(UpdatePolicy.verify(m, facts()))
        assertNull(UpdatePolicy.verify(m, facts(sha256 = sha.uppercase())))
        assertEquals(UpdatePolicy.Failure.SIZE_MISMATCH, UpdatePolicy.verify(m, facts(size = 1235)))
        assertEquals(UpdatePolicy.Failure.SHA256_MISMATCH, UpdatePolicy.verify(m, facts(sha256 = "b".repeat(64))))
        assertEquals(UpdatePolicy.Failure.NOT_AN_APK, UpdatePolicy.verify(m, facts(pkg = null, code = null)))
        assertEquals(UpdatePolicy.Failure.WRONG_PACKAGE, UpdatePolicy.verify(m, facts(pkg = "cn.ppps.forwarder")))
        assertEquals(UpdatePolicy.Failure.NOT_NEWER, UpdatePolicy.verify(m, facts(installed = 100059)))
        assertEquals(UpdatePolicy.Failure.NOT_NEWER, UpdatePolicy.verify(m, facts(code = 100057)))
        assertEquals(UpdatePolicy.Failure.MANIFEST_VERSION_MISMATCH, UpdatePolicy.verify(m, facts(code = 100060)))
        assertEquals(UpdatePolicy.Failure.CERT_MISMATCH, UpdatePolicy.verify(m, facts(apkCert = setOf("c".repeat(64)))))
        // The manifest's certificate value is never trusted: a manifest naming the attacker's cert still fails.
        val forged = UpdatePolicy.parseManifest(manifestJson().replace(cert, "c".repeat(64)))!!
        assertEquals(UpdatePolicy.Failure.CERT_MISMATCH, UpdatePolicy.verify(forged, facts(apkCert = setOf("c".repeat(64)))))
        assertEquals(UpdatePolicy.Failure.CERT_MISMATCH, UpdatePolicy.verify(m, facts(apkCert = setOf(cert, "c".repeat(64)))))
        assertEquals(UpdatePolicy.Failure.CERT_UNREADABLE, UpdatePolicy.verify(m, facts(apkCert = emptySet())))
        assertEquals(UpdatePolicy.Failure.CERT_UNREADABLE, UpdatePolicy.verify(m, facts(installedCert = emptySet())))
    }

    @Test fun currentSignerNotLineage() {
        val other = "d".repeat(64)
        // An APK now signed by another key, whose rotation lineage includes our certificate: its current signer is used.
        val apkSet = UpdatePolicy.currentSigners(listOf(other), listOf(cert, other))
        assertEquals(setOf(other), apkSet)
        assertEquals(UpdatePolicy.Failure.CERT_MISMATCH, UpdatePolicy.verify(m, facts(apkCert = apkSet)))
        // Legacy fallback only when current signers are unavailable (API < 28).
        assertEquals(setOf(cert), UpdatePolicy.currentSigners(null, listOf(cert.uppercase())))
        assertEquals(setOf(cert), UpdatePolicy.currentSigners(emptyList(), listOf(cert)))
        assertEquals(emptySet<String>(), UpdatePolicy.currentSigners(null, null))
        assertNull(UpdatePolicy.verify(m, facts(apkCert = UpdatePolicy.currentSigners(listOf(cert), null))))
    }

    @Test fun singlePassAtATime() {
        val lock = SinglePass()
        val gate = java.util.concurrent.CountDownLatch(1)
        val ran = java.util.concurrent.atomic.AtomicInteger()
        val first = Thread { lock.tryRun { ran.incrementAndGet(); gate.await() } }
        first.start()
        while (!lock.busy) Thread.sleep(5)
        assertFalse("second pass refused while the first runs", lock.tryRun { ran.incrementAndGet() })
        gate.countDown(); first.join()
        assertFalse(lock.busy)
        assertTrue(lock.tryRun { ran.incrementAndGet() })
        assertEquals(2, ran.get())
        // The lock is released when a pass throws.
        try { lock.tryRun { throw IllegalStateException() } } catch (_: IllegalStateException) {}
        assertFalse(lock.busy)
    }

    @Test fun dailyGate() {
        val h = 3_600_000L
        assertEquals(UpdateScheduler.Action.RUN, UpdateScheduler.decide(10 * h, 0, online = true, busy = false))
        assertEquals("never more than once in 24 hours", UpdateScheduler.Action.NOT_DUE, UpdateScheduler.decide(10 * h + 23 * h, 10 * h, true, false))
        assertEquals(UpdateScheduler.Action.NOT_DUE, UpdateScheduler.decide(34 * h - 1, 10 * h, true, false))
        assertEquals(UpdateScheduler.Action.RUN, UpdateScheduler.decide(34 * h, 10 * h, true, false))
        assertEquals("clock moved back a little: still throttled", UpdateScheduler.Action.NOT_DUE, UpdateScheduler.decide(5 * h, 10 * h, true, false))
        assertEquals("stamp far in the future: reset, do not run now", UpdateScheduler.Action.RESET_CLOCK, UpdateScheduler.decide(10 * h, 40 * h, true, false))
        assertEquals("offline: retry later, nothing sent", UpdateScheduler.Action.OFFLINE_RETRY, UpdateScheduler.decide(40 * h, 10 * h, online = false, busy = false))
        assertEquals("not due beats offline: no retry storm", UpdateScheduler.Action.NOT_DUE, UpdateScheduler.decide(11 * h, 10 * h, online = false, busy = false))
        assertEquals("manual check running: not counted as the automatic check", UpdateScheduler.Action.BUSY, UpdateScheduler.decide(40 * h, 10 * h, true, busy = true))
    }
}
