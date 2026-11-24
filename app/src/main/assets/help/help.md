# Relaydo – Help

Relaydo · franks.tools

This help is part of the app and works without an internet connection.

## Getting started

1. **Targets** – add where messages should go: Webhook, Email, Telegram, Gotify, SMS, Bark, Socket or URL Scheme. Use **Test** in the editor to send one real test message.
2. **Rules** – add a rule on the SMS, Calls or Apps tab. A rule says which messages match (for example a phone number or text) and which targets receive them. **Forward All** matches everything.
3. **Settings** – turn on forwarding for SMS, call events or app notifications and grant the permissions Android asks for.
4. **Logs** – every forwarded message appears here with its result. Tap an entry to see details or resend it.

The **Extras** tab holds Tasks, Server, Client and the app list.

## Settings at a glance

- **Forwarding** – what is forwarded: SMS, call events, app notifications.
- **Filtering & schedule** – duplicate protection, quiet hours and notification filters.
- **Device & automation** – Bluetooth discovery, location, SMS commands and the installed-app list used by tasks.
- **Background operation** – start after reboot, battery optimisation and keep-alive. If forwarding stops while the phone is idle, allow background activity in Android's battery settings.
- **Delivery** – retries and timeout when a target cannot be reached.
- **Identity & messages** – device name, SIM labels and the message template.
- **Backup & restore** – export or import the configuration as a file.
- **Help & info** (bottom of Settings) – setup help, this Help page, and About & updates.

## Tasks

A task runs actions automatically when its conditions are met, for example when the battery is low, at a set time (cron), when the network changes or when an SMS arrives. Actions include sending an SMS, notifying a target, switching rules or targets on and off, and Wake-on-LAN. A task is saved only when you press **Save** in the task editor.

## Remote control: Server and Client

- **Server** (on the phone that forwards) runs a small HTTP server. Choose a port and a security mode before you start it. Without security, keep **This Device Only (Loopback)** on. Only turn on the remote features you need.
- **Client** (on another phone) connects to that server. Use the same security mode and key as the server, then tap **Test Connection** to load the features the server allows.
- **Time tolerance** (signature mode) is how many seconds a request may differ from the server's clock.

## Backup and moving to a new phone

- **Offline**: Export writes `SmsForwarder.json` to the Download folder. Copy that file to the new phone and use Import there. Android asks once for "All files access" for this.
- **Network**: with Server on the old phone (RSA or SM4 encryption, Clone Configuration on) and Client on the new phone, use Push or Pull.
- Import replaces the configuration and clears the history. Exported files can contain passwords and keys: keep them safe.

## Privacy

Analytics has been removed. Network access includes configured targets and remote-control features, update checks (Settings → About & updates, can be switched off), public IP lookups used by network tasks, and online pages you choose to open. This Help page works offline. See Settings → About & updates → Privacy Policy for details.

## Troubleshooting

- **Nothing is forwarded** – check that forwarding is on in Settings, that a rule matches and that the target test works. Look in Logs for the error.
- **Stops after a while** – allow background activity and auto start in Android's battery settings.
- **Server cannot be reached** – both phones must be on the same trusted network; turn off "This Device Only" only together with a security mode.
