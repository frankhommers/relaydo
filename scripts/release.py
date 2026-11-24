#!/usr/bin/env python3
"""Cut an updater release of Relaydo (debug test build) and, separately, publish it.

  python3 scripts/release.py build [--build-number N]     build, verify and stage into dist/build-N/
  python3 scripts/release.py publish --build-number N     upload dist/build-N/ as a GitHub PRE-RELEASE (gh CLI)
  python3 scripts/release.py verify-apk PATH              run the signer/package guard on any APK

The build mode never touches GitHub. Publishing is a separate, explicit step.

The universal APK's versionCode is 100000 + N (app/build.gradle). N defaults to the highest of
versions.gradle's build_versions.version_code and the newest build-* tag on the fork, plus one.

The build is REFUSED unless the signing certificate equals EXPECTED_CERT_SHA256: every existing install is
signed with that debug key, and Android silently generates a new debug key when the keystore is missing,
which would make every phone reject the update. The updater-fixture Gradle properties are refused as well.
"""
import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path
from typing import NoReturn

ROOT = Path(__file__).resolve().parents[1]
OWNER_REPO = 'frankhommers/relaydo'
PACKAGE = 'tools.franks.relaydo'
EXPECTED_CERT_SHA256 = '546f8d56f4f13beec65be2ff882cef169e87f03a6c7a715bdef79d060aeec123'
MANIFEST_NAME = 'smsforwarder-update.json'
TAG_PREFIX = 'build-'
UNIVERSAL_BASE = 100000
GRADLE = ['bash', './gradlew', ':app:assembleDebug', '--no-daemon', '--max-workers=4',
          '-PisNeedPackage=false', '-PisNeedClean=false', '-Dorg.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8']


def fail(message) -> NoReturn:
    sys.exit('RELEASE REFUSED: ' + message)


def sha256(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for block in iter(lambda: f.read(1 << 20), b''):
            h.update(block)
    return h.hexdigest()


def build_tools():
    sdk = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or '')
    candidates = sorted((sdk / 'build-tools').glob('*'), key=lambda p: [int(x) if x.isdigit() else 0 for x in p.name.split('.')])
    for tools in reversed(candidates):
        if (tools / 'apksigner').exists() and (tools / 'aapt').exists():
            return tools
    fail('Android build-tools with apksigner and aapt not found; set ANDROID_HOME.')


def default_build_number():
    text = (ROOT / 'versions.gradle').read_text()
    found = re.search(r'build_versions\.version_code\s*=\s*(\d+)', text)
    if not found:
        fail('build_versions.version_code not found in versions.gradle')
    base = int(found.group(1))
    newest = 0
    try:
        out = subprocess.run(['gh', 'release', 'list', '--repo', OWNER_REPO, '--limit', '100', '--json', 'tagName'],
                             capture_output=True, text=True, timeout=60)
        if out.returncode == 0:
            for item in json.loads(out.stdout or '[]'):
                tag = item.get('tagName', '')
                if tag.startswith(TAG_PREFIX) and tag[len(TAG_PREFIX):].isdigit():
                    newest = max(newest, int(tag[len(TAG_PREFIX):]))
    except (OSError, subprocess.TimeoutExpired, ValueError):
        print('note: could not list releases with gh; using versions.gradle only', file=sys.stderr)
    return max(base, newest) + 1


def inspect(apk, tools):
    signer = subprocess.run([str(tools / 'apksigner'), 'verify', '--print-certs', str(apk)], capture_output=True, text=True)
    if signer.returncode != 0:
        fail('apksigner verify failed:\n' + signer.stderr)
    certs = re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-f]{64})', signer.stdout)
    badging = subprocess.run([str(tools / 'aapt'), 'dump', 'badging', str(apk)], capture_output=True, text=True, check=True).stdout
    pkg = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
    if not pkg:
        fail('could not read package name and version from the APK')
    return certs, pkg.group(1), int(pkg.group(2)), pkg.group(3)


def check_identity(apk, expected_code=None):
    """The release guard: signer, package and (optionally) versionCode. Used by build, publish and verify-apk."""
    certs, pkg, code, name = inspect(apk, build_tools())
    if certs != [EXPECTED_CERT_SHA256]:
        fail(f'signing certificate {certs} is not the expected {EXPECTED_CERT_SHA256}. '
             'The debug keystore was probably regenerated; restore the original and build again. Nothing was staged.')
    if pkg != PACKAGE:
        fail(f'package {pkg} is not {PACKAGE}')
    if expected_code is not None and code != expected_code:
        fail(f'versionCode {code} is not {expected_code}')
    return code, name


def verify_apk(args):
    code, name = check_identity(args.apk)
    print(f'OK: {args.apk} package {PACKAGE}, versionCode {code}, versionName {name}, signer {EXPECTED_CERT_SHA256}')


def build(args):
    for prop in ('updaterFixtureOrigin', 'updaterFixtureCa', 'updaterTestRequireUserAction'):
        if any(prop in a for a in sys.argv) or ('ORG_GRADLE_PROJECT_' + prop) in os.environ:
            fail(f'{prop} is set; fixture builds must never be released.')
    n = args.build_number or default_build_number()
    if n < 1 or n >= UNIVERSAL_BASE:
        fail(f'build number {n} out of range')
    tag = TAG_PREFIX + str(n)
    out_dir = ROOT / 'app/build/outputs/apk/debug'
    if out_dir.exists():
        shutil.rmtree(out_dir)
    print(f'Building universal debug APK, build {n} (versionCode {UNIVERSAL_BASE + n}) ...', flush=True)
    subprocess.run(GRADLE + [f'-PbuildNumber={n}'], cwd=ROOT, check=True)
    universal = sorted(out_dir.glob(f'SmsF_*_{UNIVERSAL_BASE + n}_universal_debug.apk'))
    if len(universal) != 1:
        fail(f'expected exactly one universal APK in {out_dir}, found {[p.name for p in universal]}')
    apk = universal[0]
    config = next((ROOT / 'app/build/generated/source/buildConfig/debug').rglob('BuildConfig.java'), None)
    if config is None:
        fail('generated BuildConfig.java not found')
    for field in ('UPDATER_FIXTURE_ORIGIN', 'UPDATER_FIXTURE_CA'):
        if not re.search(r'String ' + field + r' = "";', config.read_text()):
            fail(f'{field} is not empty in this build (a Gradle property is set somewhere); fixture builds must never be released.')
    if not re.search(r'boolean UPDATER_TEST_REQUIRE_USER_ACTION = false;', config.read_text()):
        fail('UPDATER_TEST_REQUIRE_USER_ACTION is set; test builds must never be released.')
    code, name = check_identity(apk, UNIVERSAL_BASE + n)
    dist = ROOT / 'dist' / tag
    if dist.exists():
        shutil.rmtree(dist)
    dist.mkdir(parents=True)
    apk_name = f'SmsForwarder-{n}-debug.apk'
    staged = dist / apk_name
    shutil.copy2(apk, staged)
    digest = sha256(staged)
    manifest = {
        'format': 1,
        'packageName': PACKAGE,
        'buildNumber': n,
        'versionCode': code,
        'versionName': name,
        'apk': apk_name,
        'apkSize': staged.stat().st_size,
        'apkSha256': digest,
        'certSha256': EXPECTED_CERT_SHA256,
        'testBuild': True,
    }
    (dist / MANIFEST_NAME).write_text(json.dumps(manifest, indent=2) + '\n')
    (dist / (apk_name + '.sha256')).write_text(f'{digest}  {apk_name}\n')
    (dist / 'RELEASE_NOTES.md').write_text(
        f'Test build {n} (debug build, versionCode {code}, version {name}).\n\n'
        'This is a TEST BUILD signed with the debug key that existing installs use, so it installs over them.\n'
        'It is not a production release. The app verifies size, SHA-256, package name, version and signing\n'
        'certificate before installing.\n\n'
        f'SHA-256 of {apk_name}: `{digest}`\n')
    print(json.dumps(manifest, indent=2))
    print(f'Staged in {dist.relative_to(ROOT)}. Publish with: python3 scripts/release.py publish --build-number {n}')


def publish(args):
    n = args.build_number
    tag = TAG_PREFIX + str(n)
    dist = ROOT / 'dist' / tag
    manifest = json.loads((dist / MANIFEST_NAME).read_text())
    apk = dist / manifest['apk']
    if manifest['buildNumber'] != n or sha256(apk) != manifest['apkSha256'] or apk.stat().st_size != manifest['apkSize']:
        fail('staged files do not match the manifest; run build again')
    check_identity(apk, manifest['versionCode'])
    cmd = ['gh', 'release', 'create', tag, '--repo', OWNER_REPO, '--prerelease',
           '--title', f'Test build {n}', '--notes-file', str(dist / 'RELEASE_NOTES.md')]
    if args.target:
        cmd += ['--target', args.target]
    cmd += [str(apk), str(dist / MANIFEST_NAME), str(dist / (apk.name + '.sha256'))]
    print('+ ' + ' '.join(cmd))
    if not args.yes:
        if input('Publish this pre-release on GitHub? Type yes: ').strip() != 'yes':
            fail('not confirmed')
    subprocess.run(cmd, cwd=ROOT, check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest='mode', required=True)
    b = sub.add_parser('build', help='build, verify certificate, write manifest into dist/build-N/')
    b.add_argument('--build-number', type=int, help='override the build number (versionCode = 100000 + N)')
    p = sub.add_parser('publish', help='upload dist/build-N/ as a GitHub pre-release with gh')
    p.add_argument('--build-number', type=int, required=True)
    p.add_argument('--target', help='commit or branch the tag points at (default: repository default branch)')
    p.add_argument('--yes', action='store_true', help='do not ask for confirmation')
    v = sub.add_parser('verify-apk', help='run the release guard (signer, package) on an existing APK')
    v.add_argument('apk', type=Path)
    args = parser.parse_args()
    {'build': build, 'publish': publish, 'verify-apk': verify_apk}[args.mode](args)


if __name__ == '__main__':
    main()
