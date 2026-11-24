# Relaydo

![Relaydo](branding/relaydo/relaydo-logo.png)

This fork of [pppscn/SmsForwarder](https://github.com/pppscn/SmsForwarder) monitors Android SMS, calls and application notifications and forwards them according to configured rules. Destinations include email, SMS, Telegram, Bark, Webhook, Socket, DingTalk, WeCom, Feishu, ServerChan and PushPlus. Remote-control client/server features support SMS, calls, contacts and battery queries. Automated tasks combine event conditions with actions.

Read [README_PRIVACY.md](README_PRIVACY.md) for removed telemetry, secure defaults, disabled native downloads and the owner-approved public-IP service. The application ID is `tools.franks.relaydo`. Debug builds are test artifacts, not production releases.

## Languages

English is the first-run default. Settings offers **English**, **Nederlands** and **Follow system**. Other system languages fall back to English. An explicitly stored legacy Chinese language choice migrates to English. Existing user messages and configuration data are not erased or translated.

## Workflow

1. Grant only the permissions needed for your forwarding sources.
2. Configure and test a forwarding channel.
3. Add a rule selecting messages and destinations.
4. Check the forwarding log and test with real messages.

For troubleshooting, verify permissions, test the selected channel, check the rule, then inspect the forwarding log.

The optional in-app updater checks this repository’s GitHub Releases for signed test builds. Native-library downloading remains disabled. Upstream screenshots and diagrams (including the partially translated chart) were removed because they contained Chinese labels.

## Build and verification

See [README_PRIVACY.md](README_PRIVACY.md) for the Android toolchain and build commands. Run `python scripts/check_privacy.py` and `python scripts/check_languages.py` for source regression gates. APK inspection requires the optional local Androguard environment described in the language evidence report. Test language switching and restart persistence on a device or emulator as well; static resource parity alone does not verify UI behavior.

The upstream Java version was archived to branch `v2.x` on 2022-06-06. Upstream documentation states that v3.x supports Android 4.4 through 13.0. Upstream releases and weekly preview builds are available at https://github.com/pppscn/SmsForwarder/releases and https://github.com/pppscn/SmsForwarder/actions/workflows/Weekly_Build.yml; those are not this privacy fork. Other upstream download locations are https://gitee.com/pp/SmsForwarder/releases and https://wws.lanzoui.com/b025yl86h (access password `pppscn`).

## Upstream notices and attribution

The following notices are retained in translation. The upstream repository includes a BSD `LICENSE` as well as noncommercial restrictions in its README and service agreement. This fork preserves those statements without claiming to resolve their conflict. The original wording remains in the upstream repository history.

- Upstream states that code/APKs are for testing, learning and research only, prohibits commercial use, and provides no guarantee of legality, accuracy, completeness or effectiveness. Users must assess suitability themselves.
- `pppscn` and other contributors disclaim responsibility for consequences, including privacy disclosure, arising from direct or indirect use or distribution, regardless of the applicable jurisdiction.
- Rights holders who believe code/APKs infringe their rights should provide notice, identity and ownership evidence; upstream states it will remove the relevant material after receiving verification.
- The historical privacy statement is translated in [PRIVACY](PRIVACY). Its analytics/update behavior has been removed in this fork.
- Upstream is free and open source. Donations are voluntary. The project does not guarantee paid-order rebate schemes; avoid such scams.
- The complete upstream service agreement is preserved in English at `app/src/main/assets/protocol/account_protocol.txt`.

## Help and feedback

Use issues or pull requests. Upstream documentation: https://github.com/pppscn/SmsForwarder/wiki and https://gitee.com/pp/SmsForwarder/wikis/pages . Upstream Telegram community: https://t.me/+QBZgnL_fxYM0NjE9 . Keep community discussion relevant to SmsForwarder. External documentation and services are not translated or controlled by this fork.

## Acknowledgements

Thanks to upstream author `pppscn`, all contributors and sponsors. [Upstream sponsor list](https://gitee.com/pp/SmsForwarder/wikis/pages?sort_id=4912193&doc_id=1821427).

- https://github.com/xiaoyuanhost/TranspondSms — original prototype
- https://github.com/xuexiangjys/XUI — UI framework
- https://github.com/xuexiangjys/XUpdate — upstream updater (removed in this fork)
- https://github.com/getActivity/XXPermissions — permission requests
- https://github.com/mainfunx/frpc_android — reverse tunneling
- https://github.com/gyf-dev/Cactus — keep-alive support
- https://github.com/yanzhenjie/AndServer — HTTP server
- https://github.com/jenly1314/Location — location support
- https://gitee.com/xuankaicat/kmnkt — socket communication
- https://jb.gg/OpenSourceSupport — JetBrains All Products Pack license support

## License

See the unchanged [BSD LICENSE](LICENSE) and the conflicting upstream restrictions noted above. Copyright and author attribution are retained. This fork starts from a reviewed English/Dutch source snapshot; earlier development history remains available at https://github.com/pppscn/SmsForwarder.
