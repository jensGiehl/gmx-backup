# GMX Backup

GMX Backup archiviert ein GMX-Postfach über IMAPS. Es durchläuft alle E-Mail-Ordner einschließlich „Gesendet“, überspringt Spam- sowie Papierkorb-/Gelöscht-Ordner und löscht jede erfolgreich gespeicherte Nachricht sofort vom GMX-Server. Für jede Nachricht entstehen:

- eine bereinigte, offline lesbare HTML-Ansicht,
- die unveränderte Originalnachricht als `.eml`,
- alle Anhänge und eingebetteten Bilder,
- ein Eintrag mit den wichtigsten Metadaten in `metadata.json`.

Die generierte `index.html` bietet eine mobile-optimierte Bootstrap-Oberfläche mit Volltextsuche, Ordnerfilter und Sortierung. Sie funktioniert lokal per Doppelklick und benötigt keinen Webserver.

## Voraussetzungen

- Java 25 (LTS)
- Maven 3.9 oder neuer
- ein GMX-Konto mit aktiviertem POP3/IMAP-Zugriff

Vor dem ersten Start muss der IMAP-Zugriff im GMX-Postfach aktiviert werden:

1. Bei GMX im Browser anmelden.
2. **E-Mail-Einstellungen → E-Mail empfangen → POP3/IMAP** öffnen.
3. Den Schalter **POP3- und IMAP-Zugriff erlauben** aktivieren.
4. Die Änderung speichern beziehungsweise die angezeigte Sicherheitsprüfung abschließen.

GMX kann den POP3-/IMAP-Zugriff nach längerer Nichtbenutzung automatisch wieder deaktivieren. Bei einer `AuthenticationFailedException` sollte diese Einstellung daher erneut geprüft werden. Weitere Informationen stehen in der offiziellen [GMX-Anleitung zum Einschalten von POP3/IMAP](https://hilfe.gmx.net/email/einstellungen/pop3-imap-einschalten.html).

Bei aktivierter Zwei-Faktor-Authentifizierung wird außerdem ein [anwendungsspezifisches Passwort](https://hilfe.gmx.net/sicherheit/2fa/anwendungsspezifisches-passwort.html) benötigt. Das normale GMX-Passwort funktioniert in diesem Fall nicht für IMAP.

## Start mit Maven

### PowerShell

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--backup.gmx.email=max.mustermann@gmx.de --backup.gmx.password=MEIN_PASSWORT --backup.output-directory=./backup"
```

### Bash, Zsh oder Git Bash

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--backup.gmx.email=max.mustermann@gmx.de --backup.gmx.password=MEIN_PASSWORT --backup.output-directory=./backup"
```

Enthält das Passwort Leerzeichen oder Zeichen, die von der Shell ausgewertet werden, sind Umgebungsvariablen zuverlässiger und außerdem weniger leicht in der Shell-Historie sichtbar:

```powershell
$env:BACKUP_GMX_EMAIL = "max.mustermann@gmx.de"
$env:BACKUP_GMX_PASSWORD = "MEIN_PASSWORT"
$env:BACKUP_OUTPUT_DIRECTORY = "./backup"
mvn spring-boot:run
```

Unter Linux/macOS entsprechend:

```bash
BACKUP_GMX_EMAIL='max.mustermann@gmx.de' \
BACKUP_GMX_PASSWORD='MEIN_PASSWORT' \
BACKUP_OUTPUT_DIRECTORY='./backup' \
mvn spring-boot:run
```

Das Passwort wird nicht in das Backup geschrieben. Bei der Parameter-Variante kann es jedoch je nach Betriebssystem in der Prozessliste oder Shell-Historie erscheinen; für regelmäßige Sicherungen sind Umgebungsvariablen daher vorzuziehen.

Nach erfolgreichem Abschluss kann `backup/index.html` im Browser geöffnet werden.

## Konfiguration

| Parameter | Standard | Bedeutung |
|---|---:|---|
| `backup.gmx.email` | – | Vollständige GMX-E-Mail-Adresse, erforderlich |
| `backup.gmx.password` | – | GMX- oder anwendungsspezifisches Passwort, erforderlich |
| `backup.gmx.host` | `imap.gmx.net` | IMAP-Server |
| `backup.gmx.port` | `993` | IMAPS-Port |
| `backup.output-directory` | `backup` | Zielverzeichnis |
| `backup.batch-size` | `100` | Nachrichten pro Verarbeitungsblock |
| `backup.connection-timeout` | `30s` | Zeitlimit für den Verbindungsaufbau |
| `backup.read-timeout` | `60s` | Zeitlimit für IMAP-Lesevorgänge |
| `backup.delete-all` | `false` | Löscht statt einer Sicherung sämtliche E-Mails endgültig |
| `backup.delete-after-backup` | `true` | Löscht jede erfolgreich gesicherte E-Mail sofort und endgültig vom Server; mit `false` bleibt sie erhalten |

Spring Boot akzeptiert die Werte sowohl als Kommandozeilenargumente als auch in der üblichen Umgebungsvariablen-Schreibweise, etwa `BACKUP_GMX_HOST`.

## Ergebnisstruktur

Die Ordnerhierarchie des Postfachs bleibt unter `mail/` erhalten. Jede E-Mail erhält einen Basisnamen aus lokalem Datum, Zeitstempel mit Millisekunden und den ersten 16 Hexadezimalzeichen ihres SHA-256-Inhaltshashs. Derselbe Basisname wird für die `.html`-Datei, die `.eml`-Datei und den Ordner mit Anhängen verwendet.

```text
backup/
├── index.html
├── metadata.json
├── metadata.js
├── assets/
│   ├── archive.css
│   ├── archive.js
│   ├── bootstrap.min.css
│   └── bootstrap.bundle.min.js
└── mail/
    ├── INBOX/
    │   ├── 2026-09-18_143000_123_a1b2c3d4e5f60718.html
    │   ├── 2026-09-18_143000_123_a1b2c3d4e5f60718.eml
    │   └── 2026-09-18_143000_123_a1b2c3d4e5f60718/
    │       ├── bild.png
    │       └── rechnung.pdf
    ├── Gesendet/
    └── Archiv/
        └── 2025/
```

`metadata.json` enthält pro E-Mail unter anderem IMAP-UID, Ordner, Betreff, Absender, Reply-To, Empfänger (An/CC/BCC), Sende- und Empfangszeit, Message-ID, Flags, MIME-Typ, Größe, Vorschautext, Pfad zur `.html`- und `.eml`-Datei sowie Metadaten aller Anhänge.

`metadata.js` enthält dieselben Daten für die lokale Suchoberfläche. Diese zusätzliche Datei umgeht die Sicherheitsbeschränkung vieler Browser, nach der eine per `file://` geöffnete HTML-Datei `metadata.json` nicht mit `fetch` lesen darf.

## Sicherheits- und Backup-Verhalten

- IMAP-Ordner werden standardmäßig mit `Folder.READ_WRITE` geöffnet, damit erfolgreich archivierte Nachrichten sofort gelöscht werden können. Mit `backup.delete-after-backup=false` erfolgt der Zugriff über `Folder.READ_ONLY`.
- Die IMAP-Peek-Option verhindert, dass Nachrichten durch das Sichern als gelesen markiert werden.
- Es wird ausschließlich IMAPS über TLS verwendet; SMTP ist nicht enthalten.
- Spam/Junk und Papierkorb/Gelöscht werden anhand der IMAP-Systemattribute sowie üblicher deutscher und englischer Ordnernamen ausgeschlossen.
- Aktive Inhalte wie Skripte, Formulare und eingebettete Frames werden aus der HTML-Ansicht entfernt.
- Externe Bilder werden nicht automatisch geladen, damit Tracking-Pixel beim Lesen des Archivs nicht aufgerufen werden.
- Die originale `.eml` bleibt zusätzlich erhalten und kann beispielsweise mit Thunderbird geöffnet werden.
- Fehlerhafte MIME-Teile oder Anhänge werden protokolliert und übersprungen, ohne den gesamten Sicherungslauf abzubrechen. Die originale `.eml` enthält weiterhin die unveränderte Nachricht.
- Fehler einer einzelnen E-Mail oder eines einzelnen Ordners werden protokolliert; danach wird mit dem nächsten Element fortgefahren.

Ein erneuter Lauf derselben Nachricht verwendet aufgrund des Inhaltshashs wieder denselben Dateinamen, löscht aber keine anderen bereits vorhandenen Sicherungsdateien.

Das Backup enthält vertrauliche Daten im Klartext. Das Zielverzeichnis sollte auf einem verschlüsselten Datenträger liegen und nur für den eigenen Benutzer lesbar sein.

## Nach erfolgreicher Sicherung löschen

`backup.delete-after-backup` ist standardmäßig aktiviert. Jede E-Mail wird unmittelbar nach ihrer erfolgreichen lokalen Sicherung markiert und sofort endgültig vom GMX-Server entfernt. Es wird nicht bis zum Ende des Ordners oder des gesamten Laufs gewartet.

```cmd
set "BACKUP_GMX_EMAIL=max.mustermann@gmx.de"
set "BACKUP_GMX_PASSWORD=MEIN_PASSWORT"
mvn spring-boot:run
```

Dabei gelten folgende Sicherheitsregeln:

- Eine E-Mail wird nur gelöscht, wenn ihre `.eml`-Datei, HTML-Ansicht und Metadaten erfolgreich erzeugt wurden.
- Die endgültige Löschung erfolgt vor der Verarbeitung der nächsten E-Mail.
- Kann eine E-Mail nicht gesichert werden, bleibt sie auf dem Server erhalten und der Lauf fährt mit der nächsten Nachricht fort.
- Ein fehlerhafter einzelner Anhang wird in der HTML-Ansicht übersprungen. Da die unveränderte `.eml` bereits gesichert wurde und den ursprünglichen MIME-Inhalt enthält, gilt die E-Mail dennoch als vollständig archiviert.
- Spam und „Gelöscht“ werden wie beim normalen Backup nicht verarbeitet und daher auch nicht gelöscht.
- Kann ein Ordner nur lesend geöffnet werden, wird er gesichert, aber nicht gelöscht.

Bei `backup.delete-all=true` hat der vollständige Löschmodus Vorrang; in diesem Fall wird unabhängig von `backup.delete-after-backup` kein Backup erstellt.

Für einen rein lesenden Archivlauf muss die Löschung ausdrücklich deaktiviert werden:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--backup.delete-after-backup=false"
```

## Optionaler Löschmodus

Mit `--backup.delete-all=true` wechselt die Anwendung vom Sicherungs- in den Löschmodus. In diesem Modus wird **kein Backup erstellt**. Stattdessen werden alle E-Mails aus allen Ordnern einschließlich „Gesendet“, Spam und „Gelöscht“ über IMAP gelöscht und anschließend endgültig vom Server entfernt. Die Ordner selbst bleiben bestehen.

> **Achtung:** Dieser Vorgang ist nicht rückgängig zu machen. Vor dem Aufruf sollte geprüft werden, dass ein vollständiges und lesbares Backup vorhanden ist.

Aufruf mit Maven unter Windows `cmd.exe`:

```cmd
set "BACKUP_GMX_EMAIL=max.mustermann@gmx.de"
set "BACKUP_GMX_PASSWORD=MEIN_PASSWORT"
mvn spring-boot:run "-Dspring-boot.run.arguments=--backup.delete-all=true"
```

Alternativ kann auch `BACKUP_DELETE_ALL=true` als Umgebungsvariable gesetzt werden. Ohne die explizite Einstellung `true` arbeitet die Anwendung weiterhin ausschließlich im Sicherungsmodus.

## Bauen und testen

```bash
mvn clean test
mvn clean package
```

Die gebaute Anwendung kann alternativ direkt gestartet werden:

```bash
java -jar target/gmx-backup-1.0.0.jar \
  --backup.gmx.email=max.mustermann@gmx.de \
  --backup.gmx.password=MEIN_PASSWORT \
  --backup.output-directory=./backup
```

## Docker

Bei jedem Push auf `main` oder `master` baut die GitHub Action `.github/workflows/docker-image.yml` ein Image für `linux/amd64` und `linux/arm64`. Nach erfolgreichen Tests wird es als `ghcr.io/jensgiehl/gmx-backup:latest` sowie mit einem Commit-SHA-Tag in die GitHub Container Registry veröffentlicht. Die Action verwendet das automatisch bereitgestellte `GITHUB_TOKEN`; zusätzliche Registry-Zugangsdaten sind nicht notwendig.

Das folgende Beispiel entfernt zunächst einen eventuell vorhandenen Container und startet anschließend einen neuen Sicherungslauf:

```bash
docker rm -f gmx-backup 2>/dev/null

docker run -d \
  --name gmx-backup \
  --pull=always \
  -p 8089:8080 \
  -e BACKUP_GMX_EMAIL=max.mustermann@gmx.de \
  -e BACKUP_GMX_PASSWORD=MEIN_PASSWORT \
  -e BACKUP_DELETE_AFTER_BACKUP=true \
  -v gmx-backup-data:/app/backup \
  ghcr.io/jensgiehl/gmx-backup:latest
```

`8089` ist dabei der Port auf dem Host, der auf Port `8080` im Container abgebildet wird. GMX Backup ist aktuell eine Batch-Anwendung und stellt keinen HTTP-Dienst bereit; die Portfreigabe ist daher reserviert und kann bei Bedarf weggelassen werden.

Das Volume `gmx-backup-data` bindet das Ausgabeverzeichnis `/app/backup` dauerhaft ein. Dadurch bleiben `index.html`, `metadata.json`, E-Mails und Anhänge erhalten, nachdem der Container beendet oder ersetzt wurde.

Der Container beendet sich nach dem Sicherungslauf automatisch. Deshalb wird absichtlich kein `--restart unless-stopped` verwendet: Eine Restart-Policy würde den Sicherungslauf fortlaufend wiederholen und wäre besonders zusammen mit `BACKUP_DELETE_AFTER_BACKUP=true` riskant.

Fortschritt und Ergebnis lassen sich anzeigen mit:

```bash
docker logs -f gmx-backup
```

Das Archiv kann aus dem Volume in das aktuelle Verzeichnis kopiert werden:

```bash
docker cp gmx-backup:/app/backup ./backup
```

Für Passwörter mit Sonderzeichen ist eine Env-Datei empfehlenswert, damit die Shell den Wert nicht verändert und das Passwort nicht direkt in der Befehlszeile steht:

### Passwörter mit `+` und `$` unter Linux

Das Zeichen `+` muss in Bash und vergleichbaren Linux-Shells nicht maskiert werden. `$` leitet dagegen eine Variablenauflösung ein. Bei direkter Übergabe muss deshalb der vollständige Docker-Parameter in **einfache Anführungszeichen** gesetzt werden:

```bash
docker run --rm \
  -e 'BACKUP_GMX_EMAIL=max.mustermann@gmx.de' \
  -e 'BACKUP_GMX_PASSWORD=mein+pass$wort' \
  -v gmx-backup-data:/app/backup \
  ghcr.io/jensgiehl/gmx-backup:latest
```

Innerhalb einfacher Anführungszeichen behandelt die Shell sowohl `+` als auch `$` als normale Zeichen. Das Passwort ist bei dieser Variante allerdings möglicherweise über die Prozessliste oder Shell-Historie sichtbar.

Empfohlen wird deshalb eine Env-Datei. In dieser werden `+` und `$` unverändert und ohne Anführungszeichen eingetragen:

```dotenv
BACKUP_GMX_EMAIL=max.mustermann@gmx.de
BACKUP_GMX_PASSWORD=mein+pass$wort
BACKUP_DELETE_AFTER_BACKUP=true
```

Die Datei kann beispielsweise als `gmx-backup.env` gespeichert werden. Unter Linux sollten die Zugriffsrechte vor dem Start eingeschränkt werden:

```bash
chmod 600 gmx-backup.env
```

Anschließend wird sie so verwendet:

```bash
docker run --rm \
  --env-file gmx-backup.env \
  -v gmx-backup-data:/app/backup \
  ghcr.io/jensgiehl/gmx-backup:latest
```

## Verwendete Technik

- Spring Boot 4.1
- Java 25 LTS
- Eclipse Angus Mail / Jakarta Mail
- jsoup zur sicheren HTML-Aufbereitung
- Bootstrap 5 als WebJar; die benötigten Dateien werden für das Offline-Archiv extrahiert
