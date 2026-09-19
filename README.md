# GMX Backup

GMX Backup sichert ein GMX-Postfach ausschließlich lesend über IMAPS. Es durchläuft alle E-Mail-Ordner einschließlich „Gesendet“ und überspringt Spam- sowie Papierkorb-/Gelöscht-Ordner. Für jede Nachricht entstehen:

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

Spring Boot akzeptiert die Werte sowohl als Kommandozeilenargumente als auch in der üblichen Umgebungsvariablen-Schreibweise, etwa `BACKUP_GMX_HOST`.

## Ergebnisstruktur

Die Ordnerhierarchie des Postfachs bleibt unter `mail/` erhalten. Dateinamen werden lediglich so bereinigt, dass sie auch unter Windows gültig sind.

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
    │   ├── 2026-09-18_143000_123_Betreff.html
    │   ├── 2026-09-18_143000_123_Betreff.eml
    │   └── 2026-09-18_143000_123_Betreff_dateien/
    │       ├── bild.png
    │       └── rechnung.pdf
    ├── Gesendet/
    └── Archiv/
        └── 2025/
```

`metadata.json` enthält pro E-Mail unter anderem IMAP-UID, Ordner, Betreff, Absender, Reply-To, Empfänger (An/CC/BCC), Sende- und Empfangszeit, Message-ID, Flags, MIME-Typ, Größe, Vorschautext, Pfad zur `.html`- und `.eml`-Datei sowie Metadaten aller Anhänge.

`metadata.js` enthält dieselben Daten für die lokale Suchoberfläche. Diese zusätzliche Datei umgeht die Sicherheitsbeschränkung vieler Browser, nach der eine per `file://` geöffnete HTML-Datei `metadata.json` nicht mit `fetch` lesen darf.

## Sicherheits- und Backup-Verhalten

- IMAP-Ordner werden mit `Folder.READ_ONLY` geöffnet.
- Die IMAP-Peek-Option verhindert, dass Nachrichten durch das Sichern als gelesen markiert werden.
- Es wird ausschließlich IMAPS über TLS verwendet; SMTP ist nicht enthalten.
- Spam/Junk und Papierkorb/Gelöscht werden anhand der IMAP-Systemattribute sowie üblicher deutscher und englischer Ordnernamen ausgeschlossen.
- Aktive Inhalte wie Skripte, Formulare und eingebettete Frames werden aus der HTML-Ansicht entfernt.
- Externe Bilder werden nicht automatisch geladen, damit Tracking-Pixel beim Lesen des Archivs nicht aufgerufen werden.
- Die originale `.eml` bleibt zusätzlich erhalten und kann beispielsweise mit Thunderbird geöffnet werden.

Ein erneuter Lauf überschreibt Dateien derselben IMAP-UID deterministisch, löscht aber keine bereits vorhandenen Sicherungsdateien. Damit entfernt eine serverseitig gelöschte Nachricht nicht automatisch ihre frühere lokale Kopie.

Das Backup enthält vertrauliche Daten im Klartext. Das Zielverzeichnis sollte auf einem verschlüsselten Datenträger liegen und nur für den eigenen Benutzer lesbar sein.

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

## Verwendete Technik

- Spring Boot 4.1
- Java 25 LTS
- Eclipse Angus Mail / Jakarta Mail
- jsoup zur sicheren HTML-Aufbereitung
- Bootstrap 5 als WebJar; die benötigten Dateien werden für das Offline-Archiv extrahiert
