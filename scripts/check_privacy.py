#!/usr/bin/env python3
"""Offline privacy regression gates. Optional APK scan does not replace traffic capture."""
from pathlib import Path
import argparse
import re
import subprocess
import unittest
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / 'app/src/main'
CODE = MAIN / 'kotlin/cn/ppps/forwarder'
DISALLOWED = (b'com.umeng', b'com/umeng', b'MobclickAgent', b'UMConfigure',
              b'xupdate.ppps.cn', b'cx.shouji.360.cn', b'api.ipify.org',
              b'api6.ipify.org', b'libumeng-spy', b'com/xuexiang/xupdate')


def forbidden(data):
    return [x.decode() for x in DISALLOWED if x.lower() in data.lower()]


UPDATE = CODE / 'utils/update'
UPDATER_HOSTS = {'api.github.com', 'github.com'}


def updater_sources():
    return {p.name: p.read_text() for p in sorted(UPDATE.glob('*.kt'))}


class UpdaterGateTests(unittest.TestCase):
    """The in-app updater may only reach the fork's GitHub endpoints and must verify the signer."""

    def test_updater_hosts_are_github_only(self):
        sources = updater_sources()
        self.assertTrue({'UpdatePolicy.kt', 'UpdateTransport.kt', 'UpdateManager.kt', 'UpdateInstaller.kt', 'UpdateScheduler.kt'} <= set(sources))
        for name, text in sources.items():
            for url in re.findall(r'https?://[^"\s)]*', text):
                host = re.match(r'https?://([^/:?#"]+)', url).group(1).lower()
                self.assertIn(host, UPDATER_HOSTS, f'{name}: {url}')
                self.assertTrue(url.startswith('https://'), f'{name}: {url}')
        policy = sources['UpdatePolicy.kt']
        self.assertIn('const val OWNER = "frankhommers"', policy)
        self.assertIn('const val REPO = "relaydo"', policy)
        self.assertIn('"https://api.github.com/repos/$OWNER/$REPO/releases?per_page=10"', policy)
        self.assertIn('private val EXACT_HOSTS = setOf("api.github.com", "github.com")', policy)
        self.assertIn('private const val ASSET_HOST_SUFFIX = ".githubusercontent.com"', policy)
        self.assertNotIn('/releases/latest', policy)

    def test_updater_transport_hygiene(self):
        transport = updater_sources()['UpdateTransport.kt']
        for needle in ('.followRedirects(false)', '.followSslRedirects(false)', 'UpdatePolicy.isAllowedUrl(url)',
                       '.connectTimeout(15, TimeUnit.SECONDS)', '.readTimeout(30, TimeUnit.SECONDS)', 'UpdatePolicy.MAX_APK_BYTES'):
            self.assertIn(needle, transport)
        joined = ''.join(updater_sources().values())
        for banned in ('ignoreHttpsCert', 'HostnameVerifier', 'checkServerTrusted', 'TrustAll', 'XHttp', 'addHeader(', 'User-Agent', 'Authorization', 'ANDROID_ID', 'getDeviceId'):
            self.assertNotIn(banned, joined)
        # The only request: a bare GET with no extra headers, no body, no cookies.
        self.assertEqual(1, transport.count('Request.Builder()'))
        self.assertIn('Request.Builder().url(UpdatePolicy.transportUrl(url, fixtureOrigin)).get().build()', transport)
        # The fixture redirection is build-time only and refused by the release script.
        self.assertIn('BuildConfig.UPDATER_FIXTURE_ORIGIN', transport)
        release = (ROOT / 'scripts/release.py').read_text()
        self.assertIn("String ' + field + r' = \"\";", release)
        self.assertIn('546f8d56f4f13beec65be2ff882cef169e87f03a6c7a715bdef79d060aeec123', release)
        gradle = (ROOT / 'app/build.gradle').read_text()
        self.assertIn("project.findProperty('updaterFixtureOrigin') ?: ''", gradle)

    def test_updater_verifies_certificate_against_running_app(self):
        sources = updater_sources()
        policy, manager = sources['UpdatePolicy.kt'], sources['UpdateManager.kt']
        self.assertIn('Failure.CERT_MISMATCH', policy)
        self.assertIn('f.apkCertSha256.map(::normalizeHex).toSet() != f.installedCertSha256.map(::normalizeHex).toSet()', policy)
        self.assertIn('Failure.SHA256_MISMATCH', policy)
        self.assertIn('Failure.WRONG_PACKAGE', policy)
        self.assertIn('Failure.NOT_NEWER', policy)
        self.assertIn('context.packageManager.getPackageInfo(context.packageName, flags())', manager)
        self.assertIn('installedCertSha256 = installedCertificates(context)', manager)
        self.assertIn('apkContentsSigners', manager)
        self.assertNotIn('signingCertificateHistory', manager.split('// Compare the CURRENT signer')[1])
        self.assertNotIn('installedCertSha256 = setOf(', manager)
        self.assertNotIn('certSha256 = m.certSha256', manager)
        self.assertIn('UpdatePolicy.verify(candidate.manifest, facts(context, file, size, sha))', manager)

    def test_updater_off_switch(self):
        scheduler = updater_sources()['UpdateScheduler.kt']
        self.assertIn('if (!SettingUtils.autoCheckUpdate) return Result.success()', scheduler)
        self.assertIn('wm.cancelUniqueWork(WORK_NAME)', scheduler)
        self.assertIn('NetworkType.CONNECTED', scheduler)
        about = (CODE / 'fragment/AboutFragment.kt').read_text()
        self.assertIn('SettingUtils.autoCheckUpdate = checked', about)
        self.assertIn('UpdateScheduler.apply(requireContext())', about)
        layout = (MAIN / 'res/layout/fragment_about.xml').read_text()
        self.assertIn('android:id="@+id/scb_auto_check_update"', layout)
        readme = (ROOT / 'README_PRIVACY.md').read_text()
        self.assertIn('api.github.com/repos/frankhommers/relaydo/releases', readme)


class PrivacySourceTests(unittest.TestCase):
    def test_no_analytics_or_upstream_probe_code(self):
        paths = list((MAIN / 'kotlin').rglob('*.kt')) + list(ROOT.glob('*.gradle'))
        paths += list((ROOT / 'app').glob('*.gradle'))
        for path in paths:
            if not path.is_file():
                continue
            self.assertEqual([], forbidden(path.read_bytes()), str(path))

    def test_no_native_loader_or_downloader(self):
        app = (CODE / 'App.kt').read_text()
        main = (CODE / 'activity/MainActivity.kt').read_text()
        self.assertIn('val FrpclibInited = false', app)
        self.assertNotIn('installNativeLibraryPath', app)
        self.assertNotIn('downLoad(', main)
        self.assertNotIn('downloadFrpcLib', main)

    def test_local_tips_and_privacy(self):
        common = (CODE / 'utils/CommonUtils.kt').read_text()
        tips = (CODE / 'widget/GuideTipsDialog.kt').read_text()
        self.assertIn('context.assets.open(privacyUrl)', common)
        self.assertIn('context.assets.open(if (context.resources.configuration.locale.language == "nl") "tips_nl.json" else "tips.json")', tips)
        self.assertNotRegex((MAIN / 'assets/tips_nl.json').read_text(), r'https?://|<img')
        self.assertNotIn('XHttp', tips)
        markdown = (CODE / 'fragment/MarkdownFragment.kt').read_text()
        self.assertIn('settings.blockNetworkLoads = true', markdown)
        self.assertNotIn('loadMarkdownFromUrl', markdown)
        self.assertNotRegex((MAIN / 'assets/tips.json').read_text(), r'https?://|<img')

    def test_phone_and_geocoding_offline(self):
        phone = (CODE / 'utils/PhoneUtils.kt').read_text()
        self.assertRegex(phone, r'fun getPhoneArea\(phoneNumber: String\): String = getString\(R.string.unknown_area\)')
        self.assertNotIn('getFromLocation', (CODE / 'service/LocationService.kt').read_text())

    def test_approved_ip_endpoint_and_bounded_request(self):
        ip = (CODE / 'utils/PublicIpLookup.kt').read_text()
        self.assertIn('"https://ip.hommers.it/"', ip)
        self.assertIn('instanceFollowRedirects = false', ip)
        self.assertIn('connectTimeout = 5000', ip)
        self.assertIn('readTimeout = 5000', ip)
        worker = (CODE / 'workers/NetworkWorker.kt').read_text()
        self.assertEqual(1, worker.count('val publicIP = getPublicIP()'))

    def test_english_defaults_and_supported_locales(self):
        def strings(locale):
            return {e.attrib['name']: ''.join(e.itertext()) for e in ET.parse(MAIN / f'res/{locale}/strings.xml').getroot() if e.tag == 'string'}
        default, english = strings('values'), strings('values-en')
        self.assertEqual([], [k for k in english if default.get(k) != english[k]])
        untranslated = {k: v for k, v in default.items() if re.search('[\u2e80-\u9fff\uf900-\ufaff\U00020000-\U000323af]', v)}
        self.assertEqual({}, untranslated)
        self.assertEqual([], list((MAIN / 'res').glob('values-zh*')))
        self.assertEqual(set(default), set(strings('values-nl')))
        app = (CODE / 'App.kt').read_text()
        self.assertIn('MultiLanguages.setAppLanguage(this, Locale.ENGLISH)', app)
        self.assertIn('language_initialized', app)
        self.assertLess(app.index('MultiLanguages.setAppLanguage(this, Locale.ENGLISH)'), app.index('Core.init(this)'))
        self.assertIn('MultiLanguages.clearAppLanguage', (CODE / 'fragment/SettingsFragment.kt').read_text())

    def test_current_source_languages(self):
        from check_languages import source_gate
        self.assertEqual([], source_gate()['failures'])

    def test_distinct_application_id(self):
        self.assertIn('applicationId "tools.franks.relaydo"', (ROOT / 'app/build.gradle').read_text())
        self.assertIn('const val PACKAGE_NAME = cn.ppps.forwarder.BuildConfig.APPLICATION_ID', (CODE / 'utils/Constants.kt').read_text())

    def test_license_unchanged(self):
        expected = subprocess.check_output(['git', 'show', 'HEAD:LICENSE'], cwd=ROOT)
        self.assertEqual(expected, (ROOT / 'LICENSE').read_bytes())

    def test_scan_detects_regressions(self):
        for marker in DISALLOWED:
            self.assertTrue(forbidden(b'prefix ' + marker + b' suffix'))


def scan_apk(path):
    failures = []
    with zipfile.ZipFile(path) as apk:
        for name in apk.namelist():
            if 'libgojni.so' in name or 'umeng' in name.lower():
                failures.append(name)
            if name.endswith(('.dex', '.so', '.xml', '.html', '.js', '.css')):
                markers = forbidden(apk.read(name))
                if markers:
                    failures.append(f'{name}: {markers}')
    if failures:
        raise SystemExit('APK PRIVACY FAIL: ' + '; '.join(failures))
    print(f'APK privacy markers/native-library gate PASS: {path}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--apk', type=Path)
    args = parser.parse_args()
    suite = unittest.TestSuite([unittest.defaultTestLoader.loadTestsFromTestCase(c) for c in (PrivacySourceTests, UpdaterGateTests)])
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    if not result.wasSuccessful():
        raise SystemExit(1)
    if args.apk:
        scan_apk(args.apk)
