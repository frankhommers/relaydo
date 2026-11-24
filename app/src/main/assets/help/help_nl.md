# Relaydo – Hulp

Relaydo · franks.tools

Deze hulp zit in de app en werkt zonder internetverbinding.

## Aan de slag

1. **Bestemmingen** – voeg toe waar berichten naartoe gaan: Webhook, e-mail, Telegram, Gotify, SMS, Bark, Socket of URL Scheme. Met **Test** in de editor verstuurt u één echt testbericht.
2. **Regels** – voeg een regel toe op het tabblad SMS, Oproepen of Apps. Een regel bepaalt welke berichten overeenkomen (bijvoorbeeld een telefoonnummer of tekst) en welke bestemmingen ze ontvangen. **Alles doorsturen** komt met alles overeen.
3. **Instellingen** – zet het doorsturen van SMS, oproepgebeurtenissen of appmeldingen aan en verleen de machtigingen die Android vraagt.
4. **Logboek** – elk doorgestuurd bericht staat hier met het resultaat. Tik op een regel voor details of om opnieuw te verzenden.

Het tabblad **Extra's** bevat Taken, Server, Client en de applijst.

## Instellingen in het kort

- **Doorsturen** – wat wordt doorgestuurd: SMS, oproepgebeurtenissen, appmeldingen.
- **Filters en planning** – dubbele berichten, stille uren en meldingsfilters.
- **Apparaat en automatisering** – Bluetooth-detectie, locatie, SMS-opdrachten en de applijst voor taken.
- **Werking op de achtergrond** – starten na herstart, batterijoptimalisatie en actief houden. Stopt het doorsturen als de telefoon stilligt, sta dan achtergrondactiviteit toe in de batterijinstellingen van Android.
- **Aflevering** – pogingen en time-out als een bestemming niet bereikbaar is.
- **Identiteit en berichten** – apparaatnaam, SIM-labels en het berichtsjabloon.
- **Back-up en herstel** – de configuratie als bestand exporteren of importeren.
- **Hulp en info** (onderaan Instellingen) – hulp bij instellen, deze hulppagina en Info en updates.

## Taken

Een taak voert automatisch acties uit als aan de voorwaarden is voldaan, bijvoorbeeld bij een lege batterij, op een vast tijdstip (cron), bij een netwerkwijziging of als er een SMS binnenkomt. Acties zijn onder andere een SMS versturen, een bestemming melden, regels of bestemmingen aan- en uitzetten en Wake-on-LAN. Een taak wordt pas opgeslagen als u in de taakeditor op **Opslaan** drukt.

## Bediening op afstand: Server en Client

- **Server** (op de telefoon die doorstuurt) draait een kleine HTTP-server. Kies een poort en een beveiligingsmodus voordat u hem start. Laat zonder beveiliging **Alleen dit apparaat (loopback)** aan staan. Zet alleen de functies op afstand aan die u nodig hebt.
- **Client** (op een andere telefoon) maakt verbinding met die server. Gebruik dezelfde beveiligingsmodus en sleutel als de server en tik op **Verbinding testen** om de toegestane functies op te halen.
- **Tijdtolerantie** (handtekeningmodus) is hoeveel seconden een verzoek mag afwijken van de klok van de server.

## Back-up en overstappen naar een nieuwe telefoon

- **Offline**: Exporteren schrijft `SmsForwarder.json` naar de map Download. Kopieer dat bestand naar de nieuwe telefoon en gebruik daar Importeren. Android vraagt hiervoor één keer om "Toegang tot alle bestanden".
- **Netwerk**: met Server op de oude telefoon (RSA- of SM4-versleuteling, Configuratie klonen aan) en Client op de nieuwe telefoon gebruikt u Versturen of Ophalen.
- Importeren vervangt de configuratie en wist de geschiedenis. Geëxporteerde bestanden kunnen wachtwoorden en sleutels bevatten: bewaar ze veilig.

## Privacy

Gebruikstracking is verwijderd. Netwerkverkeer komt onder meer van ingestelde bestemmingen en bediening op afstand, updatecontroles (uit te zetten bij Instellingen → Info en updates), het opvragen van het openbare IP-adres voor netwerktaken en online pagina’s die u zelf opent. Deze hulppagina werkt offline. Meer informatie staat bij Instellingen → Info en updates → Privacybeleid.

## Problemen oplossen

- **Er wordt niets doorgestuurd** – controleer of doorsturen aan staat in Instellingen, of een regel overeenkomt en of de test van de bestemming werkt. Kijk in het Logboek naar de fout.
- **Stopt na een tijdje** – sta achtergrondactiviteit en automatisch starten toe in de batterijinstellingen van Android.
- **Server niet bereikbaar** – beide telefoons moeten op hetzelfde vertrouwde netwerk zitten; zet "Alleen dit apparaat" alleen uit samen met een beveiligingsmodus.
