(() => {
    'use strict';

    const catalog = window.GMX_BACKUP_DATA || { emails: [], folders: [], emailCount: 0 };
    const searchInput = document.querySelector('#searchInput');
    const clearSearch = document.querySelector('#clearSearch');
    const folderSelect = document.querySelector('#folderSelect');
    const sortSelect = document.querySelector('#sortSelect');
    const mailList = document.querySelector('#mailList');
    const emptyState = document.querySelector('#emptyState');
    const resultCount = document.querySelector('#resultCount');
    const archiveSummary = document.querySelector('#archiveSummary');

    archiveSummary.textContent = `${catalog.emailCount} E-Mails · ${catalog.folders.length} Ordner · ${formatArchiveDate(catalog.createdAt)}`;
    folderSelect.append(new Option('Alle Ordner', ''));
    catalog.folders.forEach(folder => folderSelect.append(new Option(folder, folder)));

    const normalizedEmails = catalog.emails.map(mail => ({
        ...mail,
        searchText: normalize([
            mail.subject,
            mail.folder,
            ...(mail.from || []),
            ...(mail.to || []),
            ...(mail.cc || []),
            mail.excerpt,
            ...(mail.attachments || []).map(file => file.filename)
        ].join(' '))
    }));

    function render() {
        const terms = normalize(searchInput.value).split(/\s+/).filter(Boolean);
        const selectedFolder = folderSelect.value;
        const matching = normalizedEmails
            .filter(mail => !selectedFolder || mail.folder === selectedFolder)
            .filter(mail => terms.every(term => mail.searchText.includes(term)))
            .sort(comparator(sortSelect.value));

        mailList.replaceChildren(...matching.map(mailCard));
        resultCount.textContent = `${matching.length} von ${catalog.emailCount}`;
        emptyState.classList.toggle('d-none', matching.length !== 0);
    }

    function mailCard(mail) {
        const link = document.createElement('a');
        link.className = 'mail-card';
        link.href = mail.htmlFile;

        const top = document.createElement('div');
        top.className = 'mail-card-top';
        const sender = document.createElement('strong');
        sender.className = 'mail-card-sender text-truncate';
        sender.textContent = (mail.from || []).join(', ') || 'Unbekannter Absender';
        const date = document.createElement('time');
        date.className = 'mail-card-date';
        date.dateTime = mail.sentAt || mail.receivedAt || '';
        date.textContent = formatDate(mail.sentAt || mail.receivedAt);
        top.append(sender, date);

        const subject = document.createElement('h3');
        subject.textContent = mail.subject || 'Ohne Betreff';

        const excerpt = document.createElement('p');
        excerpt.className = 'mail-card-excerpt';
        excerpt.textContent = mail.excerpt || 'Keine Vorschau verfügbar';

        const bottom = document.createElement('div');
        bottom.className = 'mail-card-bottom';
        const folder = document.createElement('span');
        folder.className = 'folder-pill';
        folder.textContent = mail.folder;
        const files = document.createElement('span');
        const fileCount = (mail.attachments || []).length;
        files.textContent = fileCount ? `📎 ${fileCount}` : '';
        bottom.append(folder, files);

        link.append(top, subject, excerpt, bottom);
        return link;
    }

    function comparator(mode) {
        if (mode === 'subject') {
            return (a, b) => (a.subject || '').localeCompare(b.subject || '', 'de');
        }
        const direction = mode === 'oldest' ? 1 : -1;
        return (a, b) => direction * dateValue(a).localeCompare(dateValue(b));
    }

    function dateValue(mail) {
        return mail.sentAt || mail.receivedAt || '';
    }

    function normalize(value) {
        return String(value || '').normalize('NFKD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase('de');
    }

    function formatDate(value) {
        if (!value) {
            return 'Ohne Datum';
        }
        return new Intl.DateTimeFormat('de-DE', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
    }

    function formatArchiveDate(value) {
        if (!value) {
            return '';
        }
        return `Stand ${new Intl.DateTimeFormat('de-DE', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))}`;
    }

    searchInput.addEventListener('input', render);
    folderSelect.addEventListener('change', render);
    sortSelect.addEventListener('change', render);
    clearSearch.addEventListener('click', () => {
        searchInput.value = '';
        searchInput.focus();
        render();
    });
    render();
})();
