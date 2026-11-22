'use strict';
const $ = id => document.getElementById(id);
let bridge = null, settings = {}, points = [], timer = null, remaining = 0, phase = 'ready', toastTimer, jobActive = false;
let profiles = [], selected = 'ekoramc', serverVersion = '26.3', downloadPage = '';
const icons = new Map(), iconWanted = new Set();
let iconTimer = null;
const store = {type: 'mod', query: '', sort: 'relevance', category: '', offset: 0, total: 0, hits: [], token: 0, profile: null, packVersion: ''};
const installed = new Map();   // profile -> Set(projectId)
const installing = new Set();
let content = {profile: null, folder: 'mods', data: null, updates: []};
let versionsLoaded = false, editing = null;

const nav = [['home', '⌂', 'Ana sayfa'], ['haberler', '✦', 'Haberler'], ['siralama', '♛', 'Sıralamalar'], ['account', '♙', 'Ekora hesabım'],
  ['profiles', '▤', 'Kurulumlar'], ['store', '◇', 'Mod Mağazası'], ['content', '▦', 'Modlarım'], ['map', '⌖', 'Harita & konumlar'],
  ['cezalar', '⚖', 'Cezalar'], ['settings', '⚙', 'Ayarlar'], ['privacy', '◈', 'Güvenlik & gizlilik'], ['logs', '≡', 'Günlükler']];
let hesap = {girisli: false}, lb = {meta: [], mod: null, olcu: null, rows: []}, newsLoaded = false;
const MODE_LABEL = {smp: 'SMP', bedwars: 'BedWars', skyblock: 'SkyBlock', kasaba: 'Kasaba', parkur: 'Parkur', pvp: 'PvP', manhunt: 'Manhunt',
  mini: 'Mini Oyunlar', genel: 'Genel', ctf: 'Bayrak Kapmaca', backrooms: 'Backrooms', boxpvp: 'BoxPvP', rpg: 'RPG', duel: 'Düello', duels: 'Düello'};
const METRIC_LABEL = {wins: 'Galibiyet', kills: 'Öldürme', final_kills: 'Final Öldürme', games: 'Oynanan Maç', playtime: 'Oynama Süresi',
  balance: 'Servet', level: 'Seviye', value: 'Ada Değeri', members: 'Üye Sayısı', towns: 'Kasaba Sayısı', best_time: 'En Hızlı Süre', score: 'Skor',
  losses: 'Mağlubiyet', deaths: 'Ölüm', beds: 'Kırılan Yatak', captures: 'Getirilen Bayrak', streak: 'Giriş Serisi', season_level: 'Sezon Seviyesi',
  prestige: 'Prestij', money: 'Para', elo: 'ELO'};
const PUN_LABEL = {ban: 'Yasaklama', mute: 'Susturma', uyari: 'Uyarı'};
const CATS = {
  mod: [['', 'Tüm kategoriler'], ['optimization', 'Performans'], ['utility', 'Yardımcı'], ['decoration', 'Dekorasyon'], ['adventure', 'Macera'],
    ['equipment', 'Ekipman'], ['library', 'Kütüphane'], ['management', 'Yönetim'], ['social', 'Sosyal'], ['storage', 'Depolama'], ['technology', 'Teknoloji']],
  resourcepack: [['', 'Tüm kategoriler'], ['16x', '16x'], ['32x', '32x'], ['vanilla-like', 'Vanilla benzeri'], ['realistic', 'Gerçekçi'],
    ['simplistic', 'Sade'], ['gui', 'Arayüz'], ['tweaks', 'İnce ayar'], ['combat', 'PvP']],
  shader: [['', 'Tüm kategoriler'], ['potato', 'Çok düşük'], ['low', 'Düşük'], ['medium', 'Orta'], ['high', 'Yüksek'], ['realistic', 'Gerçekçi'],
    ['vanilla-like', 'Vanilla benzeri'], ['fantasy', 'Fantastik']],
  modpack: [['', 'Tüm kategoriler'], ['optimization', 'Performans'], ['adventure', 'Macera'], ['technology', 'Teknoloji'], ['magic', 'Büyü'],
    ['quests', 'Görevler'], ['lightweight', 'Hafif'], ['multiplayer', 'Çok oyunculu']]
};
const LOADER_NAME = {vanilla: 'Vanilla', fabric: 'Fabric', quilt: 'Quilt'};

/* ---------- helpers ---------- */
function modeName(m) { return MODE_LABEL[m] || (m ? m[0].toUpperCase() + m.slice(1) : ''); }
function metricName(m) { return METRIC_LABEL[m] || m.replace(/_/g, ' '); }
function fmtVal(metric, v) { v = Number(v) || 0; if (metric === 'playtime') { const m = Math.floor(v / 60); return m >= 60 ? Math.floor(m / 60) + ' sa ' + (m % 60) + ' dk' : m + ' dk'; } if (metric.includes('time') || metric.includes('sure')) { const t = Math.round(v); return Math.floor(t / 60) + ':' + String(t % 60).padStart(2, '0'); } return Math.round(v).toLocaleString('tr-TR'); }
function tarih(x, saat) { if (!x) return ''; const d = new Date(x); if (isNaN(d)) return ''; return d.toLocaleDateString('tr-TR', {day: 'numeric', month: 'long', year: d.getFullYear() !== new Date().getFullYear() ? 'numeric' : undefined}) + (saat ? ' · ' + d.toLocaleTimeString('tr-TR', {hour: '2-digit', minute: '2-digit'}) : ''); }
function playName() { return (hesap.girisli && hesap.mcAd) || settings.username || ''; }
function site(yol) { send('site', {yol}); }
function h(tag, attrs, ...kids) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v === undefined || v === null || v === false) continue;
    if (k === 'class') el.className = v; else if (k === 'text') el.textContent = v;
    else if (k.startsWith('on')) el.addEventListener(k.slice(2), v); else el.setAttribute(k, v === true ? '' : v);
  }
  for (const c of kids.flat()) if (c !== null && c !== undefined && c !== false) el.append(c.nodeType ? c : document.createTextNode(String(c)));
  return el;
}
function toast(message) { $('toast').textContent = message; $('toast').style.display = 'block'; clearTimeout(toastTimer); toastTimer = setTimeout(() => $('toast').style.display = 'none', 6500); }
function send(action, data = {}) { if (!bridge) { toast('Bu bir tasarım önizlemesi. Bu işlem Windows uygulamasında çalışır.'); return; } bridge.request(JSON.stringify({action, data})); }
function fmt(n) { n = Number(n) || 0; if (n >= 1e6) return (n / 1e6).toFixed(n >= 1e7 ? 0 : 1).replace('.', ',') + ' Mn'; if (n >= 1e3) return Math.round(n / 1e3) + ' B'; return String(n); }
function size(n) { return n > 1048576 ? (n / 1048576).toFixed(1).replace('.', ',') + ' MB' : Math.max(1, Math.round(n / 1024)) + ' KB'; }
function empty(el, text) { el.append(h('div', {class: 'empty', text})); }
function profile(id) { return profiles.find(p => p.id === (id || selected)) || profiles[0] || {id: 'ekoramc', name: 'EkoraMC', version: serverVersion, loader: 'vanilla', official: true, joinServer: true, modCount: 0}; }
function ago(t) { if (!t) return 'Hiç oynanmadı'; const d = Math.floor(Date.now() / 1000) - t; if (d < 3600) return 'Az önce oynandı'; if (d < 86400) return Math.floor(d / 3600) + ' saat önce'; return Math.floor(d / 86400) + ' gün önce'; }
function iconImg(url, cls, fallback) {
  const img = h('div', {class: 'ico ' + (cls || '')});
  if (url) { img.dataset.icon = url; if (icons.has(url)) img.style.backgroundImage = 'url("' + icons.get(url) + '")'; else wantIcon(url); }
  if (!url || !icons.has(url)) img.textContent = (fallback || '◇').slice(0, 1).toUpperCase();
  return img;
}
function wantIcon(url) { if (!/^https:\/\/cdn\.modrinth\.com\//.test(url) || iconWanted.has(url)) return; iconWanted.add(url); clearTimeout(iconTimer); iconTimer = setTimeout(() => { const urls = [...iconWanted].filter(u => !icons.has(u)).slice(0, 60); if (urls.length) send('icons', {urls}); }, 60); }
function applyIcons(map) { for (const [u, d] of Object.entries(map)) icons.set(u, d); document.querySelectorAll('[data-icon]').forEach(el => { const d = icons.get(el.dataset.icon); if (d) { el.style.backgroundImage = 'url("' + d + '")'; el.textContent = ''; } }); }

/* ---------- navigation ---------- */
function navigate(id) {
  stopCountdown();
  document.querySelectorAll('.page').forEach(el => el.classList.toggle('active', el.id === id));
  document.querySelectorAll('nav button').forEach(el => el.classList.toggle('active', el.dataset.page === id));
  $('pageTitle').textContent = nav.find(x => x[0] === id)[2]; window.scrollTo(0, 0);
  if (id === 'store' && !store.hits.length) search(true);
  if (id === 'content') openContent(content.profile || selected);
  if (id === 'profiles') renderProfiles();
  if (id === 'haberler' && !newsLoaded) send('haberler');
  if (id === 'siralama') { if (!lb.meta.length) send('siralamaMeta'); }
  if (id === 'cezalar' && !$('punList').childElementCount) send('cezalar', {});
  if (id === 'account' && hesap.girisli) send('hesapDurum', {yenile: true});
}
nav.forEach(([id, icon, label]) => { const b = h('button', {'data-page': id, class: id === 'home' ? 'active' : '', onclick: () => navigate(id)}, h('span', {text: icon}), label); $('nav').append(b); });
document.querySelectorAll('[data-go]').forEach(b => b.onclick = () => navigate(b.dataset.go));
$('settingsShortcut').onclick = () => navigate('settings'); $('profileLink').onclick = () => navigate('account');

/* ---------- settings ---------- */
function populate(s) {
  settings = s; $('username').value = s.username || '';
  for (const k of ['ram', 'countdown', 'javaPath', 'theme', 'language']) $(k).value = s[k];
  for (const k of ['autoStart', 'externalMods', 'closeOnLaunch']) $(k).checked = !!s[k];
  renderSide(); $('autoLogin').checked = s.autoLogin !== false; $('serverPack').checked = s.serverPack !== false;
  $('ramStat').textContent = (s.ram / 1024) + ' GB'; document.body.classList.toggle('cyan-theme', s.theme === 'cyan'); renderHero();
}
function renderSide() {
  const n = playName(); $('sideName').textContent = n || 'Giriş yap';
  $('sideSub').textContent = hesap.girisli ? (hesap.mcAd ? 'Ekora hesabı · bağlı' : 'Ekora hesabı · bağlı değil') : (settings.username ? 'Yerel oyuncu adı' : 'Ekora hesabı');
  const av = $('sideAvatar'); av.replaceChildren();
  if (hesap.avatar) av.append(h('img', {src: hesap.avatar, alt: ''})); else av.textContent = (n || 'E')[0].toUpperCase();
}
function save() { stopCountdown(); const s = {...settings, username: $('username').value.trim(), autoLogin: $('autoLogin').checked, serverPack: $('serverPack').checked, ram: Number($('ram').value), countdown: Number($('countdown').value), javaPath: $('javaPath').value.trim(), theme: $('theme').value, autoStart: $('autoStart').checked, language: $('language').value, externalMods: $('externalMods').checked, closeOnLaunch: $('closeOnLaunch').checked}; send('save', s); }
$('saveProfile').onclick = save; $('saveSettings').onclick = save; $('savePrivacy').onclick = save;

/* ---------- home ---------- */
function renderHero() {
  const p = profile(); const sel = $('heroProfile'); sel.replaceChildren();
  profiles.forEach(x => sel.add(new Option((x.official ? '★ ' : '') + x.name + ' · ' + x.version + (x.loader !== 'vanilla' ? ' · ' + LOADER_NAME[x.loader] : ''), x.id)));
  sel.value = p.id; $('heroDot').className = 'dot c-' + (p.color || 'violet');
  $('heroProfileInfo').textContent = p.modCount ? p.modCount + ' mod' : '';
  $('versionStat').textContent = p.name + ' · ' + p.version; $('loaderStat').textContent = LOADER_NAME[p.loader] + (p.modCount ? ' · ' + p.modCount + ' mod' : '');
  const joins = p.joinServer; let hint;
  const nm = playName();
  if (!nm) hint = 'İlk adım: Ekora hesabınla giriş yap (Ekora hesabım).';
  else if (joins && p.version !== serverVersion) hint = '⚠ Bu kurulum ' + p.version + '. EkoraMC ' + serverVersion + ' istiyor; sunucuya giremeyebilirsin.';
  else hint = 'Oyuncu: ' + nm + (joins ? ' · play.ekoramc.fun’a bağlanır' + (hesap.girisli && hesap.mcAd && settings.autoLogin !== false ? ' · otomatik giriş açık ✔' : '') : ' · tek oyunculu / sunucu listesi');
  $('launchHint').textContent = hint;
  if (phase === 'ready') $('play').textContent = joins ? '▶  EKORA’YA KATIL' : '▶  OYNA · ' + p.name.toUpperCase();
}
$('heroProfile').onchange = e => { stopCountdown(); send('selectProfile', {id: e.target.value}); };
function stopCountdown() { if (timer) { clearInterval(timer); timer = null; $('cancel').hidden = true; $('statusText').textContent = 'Otomatik başlangıç durduruldu.'; renderHero(); } }
function startCountdown() { const p = profile(); if (!playName() || !settings.autoStart || !settings.onboardingDone || !p.joinServer) return; remaining = settings.countdown; $('cancel').hidden = false; const tick = () => { $('play').textContent = '▶  BAŞLATILIYOR · ' + remaining + ' sn'; $('statusText').textContent = 'EkoraMC’ye otomatik bağlantı. Bir yere tıklayarak veya İptal ile durdurabilirsin.'; if (remaining-- <= 0) { stopCountdown(); launch(); } }; timer = setInterval(tick, 1000); tick(); }
function launch(extra) { stopCountdown(); if (!playName()) { navigate('account'); $('accEmail').focus(); toast('Önce Ekora hesabınla giriş yap ya da bir oyuncu adı kaydet.'); return; } if (phase !== 'ready') return; send('launch', {profile: selected, ...(extra || {})}); }
$('play').onclick = () => launch();
$('cancel').onclick = () => { if (timer) stopCountdown(); else { send('cancel'); $('statusText').textContent = 'İptal bekleniyor; mevcut indirmenin bitmesi gerekebilir.'; } };
$('perfTile').onclick = () => { const p = profile(); confirmBox('FPS paketi', p.name + ' kurulumuna Sodium, Lithium, FerriteCore, Entity Culling, ImmediatelyFast, Dynamic FPS ve Mod Menu kurulsun mu? Kurulum Vanilla ise Fabric açılır.', 'Kur', () => send('perfPack', {profile: p.id})); };
$('refreshServer').onclick = () => send('api', {section: 'status'});
function updateState(s) {
  phase = s.state; $('play').disabled = phase !== 'ready' || jobActive; $('repair').disabled = phase !== 'ready'; $('cancel').hidden = phase !== 'installing';
  if (phase === 'running') $('play').textContent = 'MINECRAFT AÇIK'; else if (phase === 'installing') $('play').textContent = 'HAZIRLANIYOR…'; else renderHero();
  if (s.message) $('statusText').textContent = s.message; if (phase === 'ready') { $('progressBar').style.width = '0%'; $('progressPercent').textContent = ''; }
  document.body.classList.toggle('game-running', phase === 'running');
}

/* ---------- profiles ---------- */
function setProfiles(d) {
  profiles = d.profiles; selected = d.selected; renderHero(); renderProfiles(); fillProfileSelects();
  if ($('content').classList.contains('active')) openContent(content.profile && profile(content.profile).id === content.profile ? content.profile : selected);
}
function fillProfileSelects() {
  for (const id of ['storeProfile', 'contentProfile']) { const s = $(id); const cur = s.value; s.replaceChildren(); profiles.forEach(p => s.add(new Option(p.name + ' · ' + p.version + ' · ' + LOADER_NAME[p.loader], p.id))); s.value = profiles.some(p => p.id === cur) ? cur : selected; }
  if (!store.profile || !profiles.some(p => p.id === store.profile)) store.profile = selected;
  $('storeProfile').value = store.profile; storeHint();
}
function renderProfiles() {
  const g = $('profileGrid'); g.replaceChildren();
  profiles.forEach(p => {
    const warn = p.joinServer && p.version !== serverVersion;
    const card = h('div', {class: 'profile-card c-' + p.color + (p.id === selected ? ' selected' : '')},
      h('div', {class: 'pc-top'}, h('div', {class: 'pc-badge', text: p.name.slice(0, 1).toUpperCase()}),
        h('div', {class: 'pc-title'}, h('h3', {text: p.name}), h('small', {text: 'Minecraft ' + p.version + ' · ' + LOADER_NAME[p.loader]})),
        p.official ? h('span', {class: 'pill', text: 'RESMÎ'}) : p.source && p.source.type === 'modpack' ? h('span', {class: 'pill', text: 'MOD PAKETİ'}) : null),
      h('div', {class: 'pc-meta'}, h('span', {text: (p.modCount || 0) + ' mod'}), h('span', {text: p.joinServer ? 'EkoraMC’ye bağlanır' : 'Sunucuya otomatik bağlanmaz'}), h('span', {text: ago(p.lastPlayed)})),
      warn ? h('div', {class: 'pc-warn', text: 'Sunucu ' + serverVersion + ' istiyor; bu kurulum ' + p.version + '.'}) : null,
      h('div', {class: 'pc-actions'},
        h('button', {class: 'primary', text: p.id === selected ? '▶ Oyna' : 'Seç ve oyna', onclick: () => { if (p.id !== selected) send('selectProfile', {id: p.id}); selected = p.id; navigate('home'); setTimeout(() => launch(), 150); }}),
        h('button', {text: 'Modlar', onclick: () => { content.profile = p.id; navigate('content'); }}),
        h('button', {text: 'Klasör', onclick: () => send('folder', {name: 'game', profile: p.id})}),
        p.official ? null : h('button', {text: 'Düzenle', onclick: () => editProfile(p)}),
        p.official ? null : h('button', {class: 'danger', text: 'Sil', onclick: () => confirmBox('Kurulumu sil', p.dir ? '“' + p.name + '” ve içindeki dünyalar, modlar ve ayarlar kalıcı olarak silinecek. Emin misin?' : '“' + p.name + '” listeden kaldırılacak. Dosyaların eski oyun klasöründe kalır.', 'Sil', () => send('deleteProfile', {id: p.id}), true)})));
    card.addEventListener('dblclick', () => send('selectProfile', {id: p.id}));
    g.append(card);
  });
}
function editProfile(p) {
  editing = p || null; $('newProfile').hidden = false; $('profileFormTitle').textContent = p ? 'Kurulumu düzenle' : 'Yeni kurulum'; $('pfSave').textContent = p ? 'Kaydet' : 'Oluştur';
  $('pfName').value = p ? p.name : ''; $('pfLoader').value = p ? p.loader : 'fabric'; $('pfColor').value = p ? p.color : 'cyan'; $('pfJoin').checked = p ? p.joinServer : false;
  const v = $('pfVersion'); if (!v.options.length) { v.add(new Option(serverVersion + ' (EkoraMC)', serverVersion)); }
  if (p && ![...v.options].some(o => o.value === p.version)) v.add(new Option(p.version, p.version));
  v.value = p ? p.version : serverVersion; if (!versionsLoaded) send('versions'); $('pfName').focus(); $('newProfile').scrollIntoView({behavior: 'smooth', block: 'center'});
}
$('newProfileBtn').onclick = () => editProfile(null); $('pfCancel').onclick = () => { $('newProfile').hidden = true; editing = null; };
$('pfSnapshots').onclick = () => send('versions', {snapshots: true});
$('pfSave').onclick = () => { const d = {name: $('pfName').value.trim() || 'Yeni kurulum', version: $('pfVersion').value, loader: $('pfLoader').value, color: $('pfColor').value, joinServer: $('pfJoin').checked}; if (editing) send('updateProfile', {id: editing.id, changes: d}); else send('createProfile', d); $('newProfile').hidden = true; editing = null; };
$('goPacks').onclick = () => { setStoreType('modpack'); navigate('store'); };

/* ---------- store ---------- */
function storeProfile() { return profile(store.profile || selected); }
function storeHint() { const p = storeProfile(); $('storeHint').textContent = store.type === 'modpack' ? 'Mod paketi yeni bir kurulum olarak eklenir.' : 'Minecraft ' + p.version + ' · ' + (store.type === 'mod' ? (p.loader === 'vanilla' ? 'Fabric (kurunca açılır)' : LOADER_NAME[p.loader]) : store.type === 'shader' ? 'Iris ile' : 'Kaynak paketi') + ' için uyumlu sonuçlar'; $('storeProfile').disabled = store.type === 'modpack'; $('storePackVersion').hidden = store.type !== 'modpack'; }
function setStoreType(t) { store.type = t; document.querySelectorAll('#storeTabs button').forEach(b => b.classList.toggle('active', b.dataset.type === t)); const c = $('storeCategory'); c.replaceChildren(); CATS[t].forEach(([v, l]) => c.add(new Option(l, v))); store.category = ''; storeHint(); }
function search(reset) {
  if (reset) { store.offset = 0; store.hits = []; }
  const p = storeProfile(); store.token++;
  const version = store.type === 'modpack' ? (store.packVersion || null) : p.version;
  $('storeInfo').textContent = 'Modrinth’te aranıyor…'; if (reset) { $('storeResults').replaceChildren(); for (let i = 0; i < 6; i++) $('storeResults').append(h('div', {class: 'store-card skeleton'})); }
  send('search', {query: store.query, type: store.type, version, loader: p.loader === 'vanilla' ? 'fabric' : p.loader, offset: store.offset, sort: store.sort, category: store.category || null, token: store.token});
  if (store.type !== 'modpack') send('installedIds', {profile: p.id});
}
let searchTimer = null;
$('storeQuery').oninput = e => { store.query = e.target.value.trim(); clearTimeout(searchTimer); searchTimer = setTimeout(() => search(true), 350); };
$('storeSort').onchange = e => { store.sort = e.target.value; search(true); };
$('storeCategory').onchange = e => { store.category = e.target.value; search(true); };
$('storeProfile').onchange = e => { store.profile = e.target.value; storeHint(); search(true); };
$('storePackVersion').onchange = e => { store.packVersion = e.target.value; search(true); };
document.querySelectorAll('#storeTabs button').forEach(b => b.onclick = () => { setStoreType(b.dataset.type); search(true); });
$('storeMore').onclick = () => { store.offset = store.hits.length; search(false); };
function renderStore() {
  const box = $('storeResults'); box.replaceChildren(); const p = storeProfile(); const have = installed.get(p.id) || new Set();
  if (!store.hits.length) { empty(box, store.type === 'modpack' ? 'Bu filtrelerle mod paketi bulunamadı.' : 'Minecraft ' + p.version + ' için sonuç bulunamadı. Başka bir arama dene ya da farklı sürümde bir kurulum oluştur.'); }
  store.hits.forEach(m => {
    const isInstalled = have.has(m.id), busy = installing.has(p.id + ':' + m.id);
    let btn;
    if (store.type === 'modpack') btn = h('button', {class: 'primary', text: busy ? 'Kuruluyor…' : 'Kurulum olarak kur', disabled: busy || jobActive, onclick: () => confirmBox(m.title, '“' + m.title + '” yeni bir kurulum olarak indirilsin mi? Paketteki bütün modlar doğrulanarak kurulur.', 'Kur', () => { installing.add(p.id + ':' + m.id); renderStore(); send('installPack', {projectId: m.id, version: store.packVersion || null}); })});
    else btn = h('button', {class: isInstalled ? 'done' : 'primary', text: busy ? 'Kuruluyor…' : isInstalled ? '✓ Kurulu' : 'Kur', disabled: busy || isInstalled || jobActive, onclick: () => install(m)});
    box.append(h('div', {class: 'store-card'},
      h('div', {class: 'sc-head'}, iconImg(m.iconUrl, '', m.title), h('div', {class: 'sc-title'}, h('h3', {text: m.title, title: m.title}), h('small', {text: m.author ? 'yapan: ' + m.author : ''}))),
      h('p', {text: m.description || ''}),
      h('div', {class: 'sc-tags'}, (m.categories || []).map(c => h('span', {class: 'chip', text: c}))),
      h('div', {class: 'sc-foot'}, h('span', {class: 'muted', text: '⬇ ' + fmt(m.downloads) + '  ♥ ' + fmt(m.follows)}),
        h('button', {class: 'link', text: 'Modrinth ↗', onclick: () => send('openModrinth', {slug: m.slug, type: store.type})}), btn)));
  });
  $('storeMore').hidden = store.hits.length >= store.total || !store.hits.length;
}
function install(m) { const p = storeProfile(); installing.add(p.id + ':' + m.id); renderStore(); send('install', {profile: p.id, projectId: m.id, type: store.type, title: m.title, slug: m.slug, iconUrl: m.iconUrl}); }
$('mapStore').onclick = () => { setStoreType('mod'); $('storeQuery').value = store.query = 'xaero'; navigate('store'); search(true); };

/* ---------- content ---------- */
function openContent(pid) { content.profile = pid; $('contentProfile').value = pid; content.updates = []; $('contentUpdateAll').hidden = true; renderContent(); send('content', {profile: pid}); }
$('contentProfile').onchange = e => openContent(e.target.value);
document.querySelectorAll('#contentTabs button').forEach(b => b.onclick = () => { content.folder = b.dataset.folder; document.querySelectorAll('#contentTabs button').forEach(x => x.classList.toggle('active', x === b)); renderContent(); });
$('contentFilter').oninput = () => renderContent();
$('contentStore').onclick = () => { store.profile = content.profile; setStoreType({mods: 'mod', resourcepacks: 'resourcepack', shaderpacks: 'shader'}[content.folder]); fillProfileSelects(); navigate('store'); search(true); };
$('contentImport').onclick = () => send('importContent', {profile: content.profile, kind: content.folder});
$('contentUpdates').onclick = () => { $('contentUpdates').textContent = 'Denetleniyor…'; send('checkUpdates', {profile: content.profile}); };
$('contentUpdateAll').onclick = () => send('applyUpdates', {profile: content.profile, items: content.updates});
$('contentFix').onclick = () => send('launchCheck', {profile: content.profile});
$('contentFolder').onclick = () => send('folder', {name: content.folder, profile: content.profile});
function renderContent() {
  const box = $('contentList'); box.replaceChildren(); const p = profile(content.profile);
  $('contentHint').textContent = 'Minecraft ' + p.version + ' · ' + LOADER_NAME[p.loader];
  const d = content.data && content.data.profile === content.profile ? content.data : null;
  if (d) { $('cntMods').textContent = d.mods.length || ''; $('cntPacks').textContent = d.resourcepacks.length || ''; $('cntShaders').textContent = d.shaderpacks.length || ''; }
  if (!d) { empty(box, 'Yükleniyor…'); return; }
  const q = $('contentFilter').value.trim().toLowerCase();
  const list = d[content.folder].filter(x => !q || (x.title + ' ' + x.file).toLowerCase().includes(q));
  const ups = new Map(content.updates.filter(u => u.folder === content.folder).map(u => [u.file, u]));
  if (!list.length) { empty(box, content.folder === 'mods' ? 'Bu kurulumda mod yok. “Mağazadan ekle” ile başla.' : 'Burada henüz bir şey yok.'); return; }
  list.sort((a, b) => (a.dependency - b.dependency) || a.title.localeCompare(b.title, 'tr'));
  list.forEach(x => {
    const up = ups.get(x.file);
    const badge = x.source === 'modrinth' ? h('span', {class: 'chip ok', text: 'Modrinth'}) : x.source === 'pack' ? h('span', {class: 'chip', text: 'Paketten'}) : h('span', {class: 'chip warn', text: 'Elle eklendi'});
    box.append(h('div', {class: 'content-row' + (x.enabled ? '' : ' off')},
      iconImg(x.iconUrl, 'small', x.title),
      h('div', {class: 'cr-main'}, h('strong', {text: x.title}), h('small', {text: [x.version, x.file, size(x.size)].filter(Boolean).join(' · ')})),
      x.dependency ? h('span', {class: 'chip', text: 'gerekli'}) : null, badge,
      up ? h('button', {class: 'accent', text: '↑ ' + (up.new || 'Güncelle'), title: 'Güncelle', onclick: () => send('applyUpdates', {profile: content.profile, items: [up]})}) : null,
      h('label', {class: 'switch', title: x.enabled ? 'Devre dışı bırak' : 'Etkinleştir'}, h('input', {type: 'checkbox', checked: x.enabled, onchange: () => send('toggleContent', {profile: content.profile, folder: content.folder, file: x.file})}), h('i')),
      h('button', {class: 'icon-btn small danger', text: '🗑', title: 'Sil', onclick: () => confirmBox('Sil', '“' + x.title + '” bu kurulumdan silinsin mi?', 'Sil', () => send('removeContent', {profile: content.profile, folder: content.folder, file: x.file}), true)})));
  });
}

/* ---------- modal ---------- */
function modal(eyebrow, title, body, actions) {
  $('modalEyebrow').textContent = eyebrow || ''; $('modalTitle').textContent = title; const b = $('modalBody'); b.replaceChildren();
  (Array.isArray(body) ? body : [body]).forEach(x => b.append(typeof x === 'string' ? h('p', {text: x}) : x));
  const a = $('modalActions'); a.replaceChildren(); actions.forEach(([label, fn, cls]) => a.append(h('button', {class: cls || '', text: label, onclick: () => { closeModal(); if (fn) fn(); }})));
  $('modal').hidden = false;
}
function closeModal() { $('modal').hidden = true; }
$('modalX').onclick = closeModal; $('modal').addEventListener('click', e => { if (e.target === $('modal')) closeModal(); });
document.addEventListener('keydown', e => { if (e.key === 'Escape') closeModal(); });
function confirmBox(title, text, ok, fn, danger) { modal('ONAY', title, text, [[ok, fn, danger ? 'danger' : 'primary'], ['Vazgeç']]); }
function showIssues(d) {
  const p = profile(d.profile);
  const list = h('ul', {class: 'issue-list'}, d.issues.map(i => h('li', {class: 'it-' + i.type}, i.text)));
  modal('MOD KONTROLÜ · ' + p.name, d.issues.length + ' sorun bulundu', ['Oyun açılmadan önce modlarına baktım. Şunlar bu haliyle oyunu çökertir:', list,
    h('p', {class: 'muted', text: '“Otomatik düzelt”: Modrinth’te doğru sürüm varsa onu kurar, eksik modları ekler; çözülemeyenleri silmeden devre dışı bırakır.'})],
  [['Otomatik düzelt ve oyna', () => send('fixIssues', {profile: d.profile, relaunch: true}), 'primary'], ['Yalnız düzelt', () => send('fixIssues', {profile: d.profile})],
    ...switchVersionAction(d, p), ['Yine de başlat', () => launch({skipCheck: true})], ['Vazgeç']]);
}
function switchVersionAction(d, p) {
  if (p.official) return [];
  const wanted = new Set(d.issues.filter(i => i.type === 'wrongVersion' && /^\d+(\.\d+)+$/.test(i.needs || '')).map(i => i.needs));
  const other = d.issues.some(i => i.type === 'wrongVersion' && !/^\d+(\.\d+)+$/.test(i.needs || ''));
  if (wanted.size !== 1 || other) return [];
  const v = [...wanted][0];
  return [['Kurulumu ' + v + ' yap', () => send('crashAction', {profile: d.profile, action: {kind: 'switchVersion', version: v}})]];
}
function showCrash(d) {
  const p = profile(d.profile);
  const acts = (d.actions || []).map(a => [a.label, () => { if (a.kind === 'switchVersion' && p.official) { toast('EkoraMC kurulumunun sürümü sabit.'); return; } send('crashAction', {profile: d.profile, action: a}); }, 'primary']);
  if (p.official) for (let i = acts.length - 1; i >= 0; i--) if ((d.actions[i] || {}).kind === 'switchVersion') acts.splice(i, 1);
  modal('OYUN KAPANDI · ' + p.name, d.title, [...(d.details || []), h('p', {class: 'muted', text: 'Çıkış kodu: ' + d.code + '. Ayrıntılar Günlükler bölümünde.'})], [...acts, ['Günlüğü aç', () => { navigate('logs'); send('logs'); }], ['Kapat']]);
}

/* ---------- map / waypoints ---------- */
function renderPoints() { const box = $('waypointList'); box.replaceChildren(); if (!points.length) { empty(box, 'Henüz konum kaydetmedin. Koordinatlarını yukarıdan ekleyebilirsin.'); return; } const W = {overworld: 'Dünya', nether: 'Nether', end: 'End'}; points.forEach((w, index) => box.append(h('div', {class: 'panel row'}, h('div', {}, h('h3', {text: w.name}), h('p', {text: W[w.world] + ' · X ' + w.x + ' / Y ' + w.y + ' / Z ' + w.z})), h('button', {text: 'Sil', onclick: () => send('waypoints', points.filter((_, i) => i !== index))})))); }
$('addWaypoint').onclick = () => { const name = $('wpName').value.trim(); if (!name || ['wpX', 'wpY', 'wpZ'].some(k => $(k).value === '')) { toast('Konum adını ve üç koordinatı doldur.'); return; } const w = {name, world: $('wpWorld').value, x: Number($('wpX').value), y: Number($('wpY').value), z: Number($('wpZ').value)}; if (![w.x, w.y, w.z].every(Number.isInteger)) { toast('Koordinatlar tam sayı olmalı.'); return; } send('waypoints', [...points, w]); };

/* ---------- misc buttons ---------- */
$('chooseJava').onclick = () => send('java'); $('repair').onclick = () => { stopCountdown(); send('repair', {profile: selected}); };
$('gameFolder').onclick = () => send('folder', {name: 'game', profile: selected}); $('logFolder').onclick = () => send('folder', {name: 'logs'});
$('crashFolder').onclick = () => send('folder', {name: 'crashReports', profile: selected});
$('refreshLogs').onclick = () => send('logs'); $('diagnose').onclick = () => send('diagnostics');
for (const [id, name] of [['readEula', 'eula'], ['readGuidelines', 'guidelines'], ['readMicrosoftPrivacy', 'privacy'], ['readModrinth', 'modrinth']]) $(id).onclick = () => send('legal', {name});
$('updateGo').onclick = () => send('legal', {name: 'download'}); $('updatePill').onclick = () => send('legal', {name: 'download'});
$('welcomeStart').onclick = () => { if (!bridge) { $('welcome').hidden = true; return; } send('save', {...settings, onboardingDone: true}); navigate('account'); };
$('welcomePrivacy').onclick = () => { $('welcome').hidden = true; navigate('privacy'); };
document.addEventListener('mousedown', e => { if (timer && !e.target.closest('#play')) stopCountdown(); }, true);

/* ---------- events from the app ---------- */
function receive(raw) {
  let e; try { e = JSON.parse(raw); } catch { return; } const d = e.data;
  switch (e.kind) {
    case 'init': serverVersion = d.serverVersion || serverVersion; downloadPage = d.downloadPage; $('buildVersion').textContent = 'v' + d.version; populate(d.settings); setProfiles({profiles: d.profiles, selected: d.selected}); $('previewBanner').hidden = true; $('nativeBadge').textContent = 'WINDOWS'; if (d.warning) toast(d.warning); $('welcome').hidden = !!d.settings.onboardingDone; $('pfJoinHint').textContent = 'Sunucuya yalnız sunucunun sürümüyle (' + serverVersion + ') girilebilir.'; startCountdown(); send('hesapDurum', {}); send('haberler'); break;
    case 'saved': populate(d); $('welcome').hidden = !!d.onboardingDone; toast('Ayarlar kaydedildi.'); break;
    case 'profiles': setProfiles(d); break;
    case 'connection': { const el = $('serverStatus'); if (d.online) { el.textContent = 'Çevrimiçi · ' + d.players + '/' + d.max + ' oyuncu' + (d.latency ? ' · ' + d.latency + ' ms' : ''); el.className = 'online'; } else { el.textContent = 'Şu an ulaşılamıyor'; el.className = 'offline'; } break; }
    case 'api': if (d.section === 'status') { receive(JSON.stringify({kind: 'connection', data: {online: d.payload.online, players: d.payload.players.online, max: d.payload.players.max, latency: d.payload.latency}})); toast('Sunucu durumu güncellendi.'); } break;
    case 'versions': { versionsLoaded = true; const v = $('pfVersion'); const cur = v.value; v.replaceChildren(); d.forEach(x => v.add(new Option(x === serverVersion ? x + ' (EkoraMC)' : x, x))); if (cur && ![...v.options].some(o => o.value === cur)) v.add(new Option(cur, cur)); v.value = cur || serverVersion; const pv = $('storePackVersion'); const pc = pv.value; pv.replaceChildren(); pv.add(new Option('Tüm sürümler', '')); d.slice(0, 40).forEach(x => pv.add(new Option('Minecraft ' + x, x))); pv.value = pc; break; }
    case 'state': updateState(d); break;
    case 'job': jobActive = d.active; $('play').disabled = phase !== 'ready' || jobActive; document.body.classList.toggle('busy', jobActive); if (d.active) $('statusText').textContent = d.message; else { $('progressBar').style.width = '0%'; $('progressPercent').textContent = ''; if (phase === 'ready') $('statusText').textContent = 'Hazır'; installing.clear(); if (store.hits.length) renderStore(); } break;
    case 'progress': if (d.message) $('statusText').textContent = d.message; if (d.percent !== undefined) { $('progressBar').style.width = d.percent + '%'; $('progressPercent').textContent = d.percent + '%'; } break;
    case 'error': toast(d.message); $('statusText').textContent = d.message; installing.clear(); if (store.hits.length) renderStore(); $('contentUpdates').textContent = 'Güncellemeleri denetle'; break;
    case 'launchError': updateState({state: 'ready', message: d.message}); toast(d.message); break;
    case 'notice': toast(d.message); break;
    case 'hesap': hesap = d || {girisli: false}; $('accLoginBtn').disabled = false; renderHesap(); renderSide(); renderHero(); if (hesap.girisli && hesap.mcAd) send('oyuncu', {ad: hesap.mcAd}); if (hesap.hata && hesap.girisli) toast(hesap.hata); break;
    case 'hesapHata': $('accLoginBtn').disabled = false; $('accLoginBtn').textContent = 'Giriş yap'; $('accError').hidden = false; $('accError').textContent = d.message; break;
    case 'haberler': renderNews(d); break;
    case 'siralamaMeta': lbMeta(d); break;
    case 'siralama': lbRows(d); break;
    case 'oyuncu': renderOyuncu(d); break;
    case 'cezalar': renderPun($('punList'), d, true); break;
    case 'java': $('javaPath').value = d; break;
    case 'waypoints': points = d; renderPoints(); break;
    case 'logs': $('logText').textContent = d; break;
    case 'diagnostics': $('diagnosticResult').hidden = false; $('diagnosticResult').textContent = 'Boş disk: ' + d.freeGB + ' GB · Kurulum: ' + d.profile + ' (' + d.profileVersion + ', ' + LOADER_NAME[d.loader] + ') · İndirilmiş oyun sürümü: ' + d.installed.length + ' · Oyun dili: ' + (d.language === 'tr_tr' ? 'Türkçe' : 'İngilizce') + ' · Harici modlar: ' + (d.externalMods ? 'İzinli' : 'Kapalı'); break;
    case 'searchResults': if (d.request.token !== store.token) break; store.total = d.total; store.hits = store.offset ? store.hits.concat(d.hits) : d.hits; $('storeInfo').textContent = Number(d.total).toLocaleString('tr-TR') + ' sonuç' + (store.query ? ' · “' + store.query + '”' : ''); renderStore(); break;
    case 'installedIds': installed.set(d.profile, new Set(d.ids)); if (store.hits.length) renderStore(); break;
    case 'icons': applyIcons(d); break;
    case 'installed': installing.forEach(k => { if (k.startsWith(d.profile + ':')) installing.delete(k); }); break;
    case 'content': content.data = d; if (d.profile === content.profile) renderContent(); break;
    case 'updates': content.updates = d.items; $('contentUpdates').textContent = 'Güncellemeleri denetle'; $('contentUpdateAll').hidden = !d.items.length; $('contentUpdateAll').textContent = 'Hepsini güncelle (' + d.items.length + ')'; if (d.profile === content.profile) renderContent(); if (!d.items.length && document.querySelector('#content.active')) toast('Her şey güncel.'); break;
    case 'modIssues': showIssues(d); break;
    case 'noIssues': toast('Modlarda sorun bulunmadı.'); break;
    case 'fixed': modal('MOD KONTROLÜ', d.messages.length ? 'Düzeltildi' : 'Düzeltilecek bir şey yok', d.messages.length ? [h('ul', {class: 'issue-list ok'}, d.messages.map(m => h('li', {text: m})))] : ['Modlar bu sürümle uyumlu görünüyor.'], d.relaunch ? [['Şimdi oyna', () => launch(), 'primary'], ['Kapat']] : [['Tamam', null, 'primary']]); break;
    case 'crash': showCrash(d); break;
    case 'packInstalled': navigate('profiles'); break;
    case 'launcherUpdate': $('updateBanner').hidden = false; $('updatePill').hidden = false; $('updateText').textContent = 'Ekora Launcher ' + d.version + ' çıktı. Yeni özellikler ve düzeltmeler için güncelle.'; break;
  }
}

/* ---------- start ---------- */
setStoreType('mod');
populate({username: '', ram: 4096, countdown: 8, autoStart: true, javaPath: '', theme: 'violet', language: 'tr_tr', externalMods: false, closeOnLaunch: false, onboardingDone: false});
setProfiles({profiles: [{id: 'ekoramc', name: 'EkoraMC', version: '26.3', loader: 'vanilla', official: true, joinServer: true, color: 'violet', modCount: 0, lastPlayed: 0}], selected: 'ekoramc'});
renderPoints();
window.__ekoraReceive = receive;
if (window.__ekoraFakeBridge) { bridge = window.__ekoraFakeBridge; bridge.event.connect(receive); send('init'); }
else if (typeof QWebChannel !== 'undefined' && typeof qt !== 'undefined') { new QWebChannel(qt.webChannelTransport, channel => { bridge = channel.objects.ekora; bridge.event.connect(receive); send('init'); }); }


/* ---------- 0.7: Ekora hesabı ---------- */
function renderHesap() {
  const g = !!hesap.girisli; $('accLogin').hidden = g; $('accPanel').hidden = !g;
  $('accLoginBtn').textContent = 'Giriş yap';
  if (!g) return;
  $('accError').hidden = true; $('accPass').value = '';
  $('accName').textContent = hesap.mcAd || hesap.ad || 'Ekora oyuncusu';
  $('accMail').textContent = (hesap.ad && hesap.mcAd ? hesap.ad + ' · ' : '') + (hesap.eposta || '') + (hesap.bildirim ? ' · ' + hesap.bildirim + ' okunmamış bildirim' : '');
  const img = $('accAvatar'); if (hesap.avatar) img.src = hesap.avatar; else img.removeAttribute('src');
  const l = $('accLink'); l.replaceChildren();
  if (hesap.mcAd) l.append(h('span', {class: 'ok', text: '✔ Minecraft hesabı bağlı: ' + hesap.mcAd + ' · launcher bu adla oynar'}));
  else l.append(h('span', {class: 'warn', text: '⚠ Minecraft hesabın bağlı değil. Oyunda /webkod yaz, kodu sitede gir. '}), h('button', {class: 'link-btn', text: 'Hesap bağla ↗', onclick: () => site('hesap-bagla')}));
  if (!hesap.mcAd) { $('accStats').replaceChildren(); empty($('accStats'), 'Hesabını bağladığında istatistiklerin burada görünür.'); $('accPunish').replaceChildren(); }
}
$('accLoginBtn').onclick = () => { $('accError').hidden = true; $('accLoginBtn').disabled = true; $('accLoginBtn').textContent = 'Giriş yapılıyor…'; send('hesapGiris', {eposta: $('accEmail').value.trim(), sifre: $('accPass').value}); };
$('accPass').onkeydown = e => { if (e.key === 'Enter') $('accLoginBtn').click(); };
$('accRegister').onclick = () => site('kayit'); $('accForgot').onclick = () => site('sifre');
$('accLogout').onclick = () => confirmBox('Çıkış yap', 'Ekora hesabından çıkılsın mı? Oyuna girerken yeniden /login yazman gerekir.', 'Çıkış yap', () => send('hesapCikis'));
$('accRefresh').onclick = () => send('hesapDurum', {yenile: true}); $('accProfileSite').onclick = () => site('profil');
$('saveAccPrefs').onclick = save;
$('siteStore').onclick = () => site('magaza'); $('shopTile').onclick = () => site('magaza'); $('supportTile').onclick = () => site('destek');
$('newsSite').onclick = () => site('haberler'); $('eventsSite').onclick = () => site('etkinlikler'); $('lbSite').onclick = () => site('siralamalar'); $('appealSite').onclick = () => site('itiraz');
document.querySelectorAll('[data-go]').forEach(b => b.onclick = () => navigate(b.dataset.go));

function renderOyuncu(d) {
  if (!hesap.mcAd || d.ad !== hesap.mcAd) return;
  const box = $('accStats'); box.replaceChildren();
  if (d.hata) { empty(box, d.hata); return; }
  const byMode = {}; (d.istatistik || []).forEach(r => { (byMode[r.mod] = byMode[r.mod] || []).push(r); });
  const modes = Object.keys(byMode).sort((a, b) => (a === 'genel' ? -1 : b === 'genel' ? 1 : modeName(a).localeCompare(modeName(b), 'tr')));
  if (!modes.length) empty(box, 'Henüz sıralamalarda görünmüyorsun. Biraz oyna, 10 dakikada bir güncellenir.');
  modes.forEach(m => box.append(h('div', {class: 'stat-card'}, h('span', {class: 'eyebrow', text: modeName(m).toUpperCase()}),
    byMode[m].map(r => h('div', {class: 'stat-row'}, h('span', {text: metricName(r.olcu)}), h('b', {text: fmtVal(r.olcu, r.deger)}))))));
  renderPun($('accPunish'), {liste: d.cezalar || []}, false);
}
function renderPun(box, d, adGoster) {
  box.replaceChildren();
  if (d.hata) { empty(box, d.hata); return; }
  const list = d.liste || [];
  if (!list.length) { empty(box, adGoster ? (d.ad ? d.ad + ' için kayıtlı ceza yok.' : 'Kayıtlı ceza yok.') : 'Temiz sicil. Böyle devam! ✔'); return; }
  list.forEach(c => box.append(h('div', {class: 'pun'},
    h('span', {class: 'badge ' + (c.tur || ''), text: (PUN_LABEL[c.tur] || c.tur || '').toUpperCase()}),
    h('div', {}, h('strong', {text: (adGoster ? c.ad + ' · ' : '') + (c.aktif ? 'Aktif' : 'Bitti')}), h('p', {text: c.sebep})),
    h('small', {text: tarih(c.bas) + (c.bit ? ' → ' + tarih(c.bit) : ' · süresiz')}))));
}
$('punSearch').onclick = () => { const ad = $('punFind').value.trim(); if (ad && !/^[A-Za-z0-9_]{3,16}$/.test(ad)) { toast('Oyuncu adı 3–16 karakter olmalı.'); return; } send('cezalar', {ad}); };
$('punFind').onkeydown = e => { if (e.key === 'Enter') $('punSearch').click(); };

/* ---------- haberler ---------- */
function newsItem(n) {
  return h('button', {class: 'news-item', onclick: () => site('haber/' + n.slug)},
    h('div', {class: 'meta'}, h('span', {class: 'badge ' + n.tur, text: n.tur === 'duyuru' ? 'DUYURU' : 'HABER'}), n.sabit ? h('span', {class: 'badge sabit', text: 'SABİT'}) : null, h('span', {text: tarih(n.tarih)})),
    h('h4', {text: n.baslik}), n.ozet ? h('p', {text: n.ozet}) : null);
}
function eventItem(e) { return h('div', {class: 'event'}, h('span', {class: 'when', text: tarih(e.bas, true).toUpperCase()}), h('strong', {text: e.baslik}), h('small', {text: (e.mod ? modeName(e.mod) + ' · ' : '') + (e.kayit ? 'Kayıt açık' : (e.aciklama || '').slice(0, 90))})); }
function renderNews(d) {
  newsLoaded = !d.hata;
  const hn = $('homeNews'), nl = $('newsList'), he = $('homeEvents'), el = $('eventList');
  [hn, nl, he, el].forEach(x => x.replaceChildren());
  if (d.hata) { [hn, nl].forEach(x => empty(x, d.hata)); [he, el].forEach(x => empty(x, '—')); return; }
  const ns = d.haberler || [], es = d.etkinlikler || [];
  if (!ns.length) [hn, nl].forEach(x => empty(x, 'Henüz haber yok.'));
  ns.slice(0, 3).forEach(n => hn.append(newsItem(n))); ns.forEach(n => nl.append(newsItem(n)));
  if (!es.length) [he, el].forEach(x => empty(x, 'Planlanmış etkinlik yok.'));
  es.slice(0, 3).forEach(e => he.append(eventItem(e))); es.forEach(e => el.append(eventItem(e)));
}

/* ---------- sıralamalar ---------- */
function lbMeta(d) {
  const box = $('lbModes'); box.replaceChildren();
  if (d.hata) { $('lbTable').replaceChildren(); empty($('lbTable'), d.hata); return; }
  lb.meta = d.liste || [];
  const modes = [...new Set(lb.meta.map(m => m.mod))];
  if (!modes.length) { $('lbTable').replaceChildren(); empty($('lbTable'), 'Henüz sıralama verisi yok. Sunucu her 10 dakikada bir gönderir.'); return; }
  if (!lb.mod || !modes.includes(lb.mod)) lb.mod = modes[0];
  modes.forEach(m => box.append(h('button', {class: m === lb.mod ? 'active' : '', text: modeName(m), onclick: () => { lb.mod = m; lb.olcu = null; lbMeta({liste: lb.meta}); }})));
  const ms = lb.meta.filter(x => x.mod === lb.mod); const sel = $('lbMetric'); sel.replaceChildren();
  ms.forEach(x => sel.add(new Option(metricName(x.olcu) + ' · ' + x.oyuncu + ' oyuncu', x.olcu)));
  if (!lb.olcu || !ms.some(x => x.olcu === lb.olcu)) lb.olcu = ms[0] && ms[0].olcu;
  sel.value = lb.olcu; send('siralama', {mod: lb.mod, olcu: lb.olcu});
}
$('lbMetric').onchange = e => { lb.olcu = e.target.value; send('siralama', {mod: lb.mod, olcu: lb.olcu}); };
$('lbFind').oninput = () => lbDraw();
function lbRows(d) { if (d.mod !== lb.mod || d.olcu !== lb.olcu) return; lb.rows = d.satirlar || []; lb.hata = d.hata; lbDraw(); }
function lbDraw() {
  const pod = $('lbPodium'), tb = $('lbTable'); pod.replaceChildren(); tb.replaceChildren();
  if (lb.hata) { empty(tb, lb.hata); return; }
  const q = $('lbFind').value.trim().toLowerCase(), me = (playName() || '').toLowerCase();
  if (!q) lb.rows.slice(0, 3).forEach((r, i) => pod.append(h('div', {class: 'pod r' + (i + 1)},
    r.avatar ? h('img', {src: r.avatar, alt: ''}) : h('div', {class: 'ph', text: r.ad[0]}), h('span', {class: 'rank', text: '#' + (i + 1)}),
    h('div', {}, h('strong', {text: r.ad}), h('small', {text: fmtVal(lb.olcu, r.deger) + (r.ek ? ' · ' + r.ek : '')})))));
  const rows = lb.rows.map((r, i) => [i + 1, r]).filter(([, r]) => !q || r.ad.toLowerCase().includes(q));
  if (!rows.length) { empty(tb, 'Bu listede kimse yok.'); return; }
  rows.forEach(([i, r]) => tb.append(h('div', {class: 'lb-row' + (r.ad.toLowerCase() === me ? ' me' : '')}, h('span', {text: '#' + i}), h('span', {text: r.ad + (r.ek ? ' · ' + r.ek : '')}), h('b', {text: fmtVal(lb.olcu, r.deger)}))));
}
