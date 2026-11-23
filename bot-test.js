// Oyuncu akışı testi: iki bot (Deneme1 = oyuncu, Yetkili1 = op) ile giriş adası, AuthMe kaydı, yetkili araçları,
// ceza menüsü, susturma, görünmezlik, dondurma, Sorgu Odası, Yetkili Bölgesi, yetkili modu, medya etkinliği, yasak.
const mineflayer = require('mineflayer')
const fs = require('fs')
const [FIFO, OUT] = process.argv.slice(2)
const log = (...a) => { const s = new Date().toISOString().slice(11, 19) + ' ' + a.join(' '); console.log(s); fs.appendFileSync(OUT, s + '\n') }
const konsol = c => { fs.writeFileSync(FIFO, c + '\n', { flag: 'a' }); log('KONSOL>', c) }
const bekle = ms => new Promise(r => setTimeout(r, ms))
const konum = b => b && b.entity ? `${b.entity.position.x.toFixed(1)},${b.entity.position.y.toFixed(1)},${b.entity.position.z.toFixed(1)}` : '?'
const sonuc = []
const kontrol = (ok, ad) => { sonuc.push([ok, ad]); log(ok ? '✔' : '✗', ad) }

function tikSonu (bot) { // 26.3: her tik sonunda tick_end (mineflayer 4.39 göndermiyor)
  const yaz = bot._client.write.bind(bot._client)
  const HAREKET = new Set(['position', 'position_look', 'look', 'flying'])
  let acik = false
  const destek = () => { try { return !!bot.registry && bot.registry.supportFeature('sendsClientTickEndPacket') } catch { return false } }
  const kapat = () => { if (acik && destek() && bot._client.state === 'play') { acik = false; try { yaz('tick_end', {}) } catch {} } }
  bot.on('physicsTick', kapat)
  bot._client.write = function (ad, veri) {
    if (ad === 'tick_end') acik = false
    if (HAREKET.has(ad)) { kapat(); acik = true }
    return yaz(ad, veri)
  }
}

const mesajlar = {}
function baglan (ad) {
  return new Promise((res, rej) => {
    const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: ad, version: '26.1', auth: 'offline' })
    tikSonu(bot)
    mesajlar[ad] = mesajlar[ad] || []
    bot.on('message', m => { const s = m.toString(); mesajlar[ad].push(s); log(ad, 'MESAJ', s.replace(/\n/g, ' ⏎ ')) })
    bot.on('title', (t, tur) => log(ad, 'BAŞLIK', tur, String(t).slice(0, 120)))
    bot.on('kicked', r => { const s = typeof r === 'string' ? r : JSON.stringify(r); mesajlar[ad].push('ATILDI ' + s); log(ad, 'ATILDI', s.slice(0, 400)) })
    bot.on('error', e => log(ad, 'HATA', e.message))
    bot.once('spawn', () => res(bot))
    bot.once('end', r => rej(new Error(ad + ' bağlantı kapandı: ' + r)))
    setTimeout(() => rej(new Error('bağlanamadı ' + ad)), 90000)
  })
}
const pencere = (b, ms = 6000) => new Promise((res, rej) => { const f = w => res(w); b.once('windowOpen', f); setTimeout(() => { b.removeListener('windowOpen', f); rej(new Error('pencere açılmadı')) }, ms) })
const ad = it => { try { return JSON.stringify(it.customName ?? it.displayName ?? it.name) } catch { return String(it && it.name) } }
const geldi = (kim, re, sonra = 0) => mesajlar[kim].slice(sonra).some(m => re.test(m))

;(async () => {
  let A, B
  try {
    A = await baglan('Deneme1')
    await bekle(4000)
    log('A doğdu', konum(A))
    kontrol(A.entity && Math.abs(A.entity.position.y - 101) < 2 && Math.abs(A.entity.position.z - 6.5) < 3, 'giriş yapmamış oyuncu giriş adasında (y≈101)')
    const giris0 = konum(A)
    A.chat('/register Sifre1234 Sifre1234')
    await bekle(5000)
    if (Math.abs(A.entity.position.y - 101) < 2) { A.chat('/login Sifre1234'); await bekle(4000) }
    log('A kayıt/giriş sonrası', konum(A))
    kontrol(konum(A) !== giris0 && Math.abs(A.entity.position.y - 101) >= 2, 'kayıttan sonra lobiye ışınlandı')

    // sohbet seli
    let n0 = mesajlar.Deneme1.length
    for (let i = 0; i < 8; i++) A.chat('sel deneme ' + i)
    await bekle(1500)
    kontrol(geldi('Deneme1', /hızlı yazıyorsun/i, n0), 'sohbet seli engellendi')
    await bekle(3500)

    konsol('op Yetkili1')
    await bekle(1500)
    B = await baglan('Yetkili1')
    await bekle(3000)
    B.chat('/register Sifre1234 Sifre1234')
    await bekle(5000)
    if (Math.abs(B.entity.position.y - 101) < 2) { B.chat('/login Sifre1234'); await bekle(4000) }
    log('B konum', konum(B))

    // Yetkili Paneli
    let p = pencere(B); B.chat('/yetkili'); let w = await p
    log('PANEL', w.title, w.slots.filter(Boolean).map(ad).filter(x => !/glass_pane|"bos"/.test(x)).slice(0, 12).join(' | '))
    kontrol(/Yetkili Paneli/.test(w.title), 'Yetkili Paneli açıldı')
    B.closeWindow(w); await bekle(800)

    // ceza menüsü: Küfür'e iki tık → susturma
    p = pencere(B); B.chat('/ceza Deneme1'); w = await p
    log('CEZA', w.title)
    let slot = w.slots.findIndex(s => s && /Küfür/.test(ad(s)))
    kontrol(slot >= 0, 'ceza menüsünde Küfür sebebi var')
    p = pencere(B); await B.clickWindow(slot, 0, 0); w = await p
    slot = w.slots.findIndex(s => s && /Küfür/.test(ad(s)))
    log('onay durumu', ad(w.slots[slot]))
    n0 = mesajlar.Deneme1.length
    await B.clickWindow(slot, 0, 0)
    await bekle(2000)
    kontrol(geldi('Deneme1', /Susturuldun/, n0), 'iki tıkla susturma verildi')
    n0 = mesajlar.Deneme1.length
    A.chat('merhaba susturulmus')
    await bekle(1200)
    kontrol(geldi('Deneme1', /Susturuldun/, n0), 'susturulan oyuncu yazamıyor')
    konsol('susturkaldir Deneme1')
    await bekle(1500)

    // görünmezlik
    B.chat('/v'); await bekle(2000)
    kontrol(!A.players.Yetkili1, 'görünmez yetkili oyuncunun listesinden çıktı')
    B.chat('/v'); await bekle(2000)
    kontrol(!!A.players.Yetkili1, 'görünmezlik kapanınca listede')

    // dondurma
    B.chat('/dondur Deneme1'); await bekle(2000)
    let p0 = A.entity.position.clone()
    A.setControlState('forward', true); await bekle(1500); A.setControlState('forward', false); await bekle(500)
    const yol = p0.distanceTo(A.entity.position)
    log('donmuşken yürüdüğü', yol.toFixed(2))
    kontrol(yol < 0.6, 'donmuş oyuncu yürüyemiyor')

    // Sorgu Odası
    B.chat('/sorgu Deneme1'); await bekle(4000)
    log('sorgu: A', konum(A), 'B', konum(B))
    kontrol(Math.abs(A.entity.position.x - 44.5) < 2 && Math.abs(A.entity.position.y - 65) < 2, 'oyuncu Sorgu Odası\'nda')
    B.chat('/sorgu Deneme1'); await bekle(4000)
    log('bırakıldı: A', konum(A))
    kontrol(Math.abs(A.entity.position.x - 44.5) > 5, 'sorgudan bırakıldı')
    p0 = A.entity.position.clone()
    A.setControlState('forward', true); await bekle(1200); A.setControlState('forward', false); await bekle(400)
    kontrol(p0.distanceTo(A.entity.position) > 0.6, 'bırakılınca çözüldü (yürüyebiliyor)')

    // Yetkili Bölgesi
    B.chat('/yb'); await bekle(5000)
    const yazilar = Object.values(B.entities).filter(e => /text_display/.test(e.name || '')).length
    const etkilesim = Object.values(B.entities).filter(e => /interaction/.test(e.name || '')).length
    log('B bölgede', konum(B), 'metin', yazilar, 'etkileşim', etkilesim)
    kontrol(Math.abs(B.entity.position.z - 15.5) < 3 && Math.abs(B.entity.position.y - 65) < 2, 'Yetkili Bölgesi doğma noktası')
    kontrol(yazilar >= 20 && etkilesim >= 20, 'bölgede paneller ve tıklama alanları var')
    // oyuncu duvarındaki ilk kafaya (Deneme1) tıkla → izleme
    const kafa = Object.values(B.entities).filter(e => /interaction/.test(e.name || '') && e.position.z < -15).sort((a, b) => b.position.y - a.position.y || a.position.x - b.position.x)[0]
    if (kafa) {
      konsol(`tp Yetkili1 ${kafa.position.x.toFixed(1)} 65 ${(kafa.position.z + 1.6).toFixed(1)} 180 0`)
      await bekle(2500)
      log('B duvar önünde', konum(B), 'kafa', kafa.position.toString())
      await B.lookAt(kafa.position.offset(0, 0.6, 0), true)
      B.activateEntity(kafa)
      await bekle(3000)
      log('kafaya tıkladıktan sonra B', konum(B), 'oyun modu', B.game.gameMode)
      kontrol(B.game.gameMode === 'spectator', 'oyuncu duvarındaki kafa izlemeyi başlattı')
      B.chat('/izle'); await bekle(3000)
      kontrol(B.game.gameMode !== 'spectator', '/izle ile izleme bitti, oyun modu geri geldi')
    } else kontrol(false, 'oyuncu duvarında tıklama alanı bulundu')

    // yetkili modu
    B.chat('/ymod'); await bekle(2000)
    const esyalar = B.inventory.items().map(i => i.name)
    log('ymod envanteri', esyalar.join(','))
    kontrol(esyalar.includes('packed_ice') && esyalar.includes('ender_eye'), 'yetkili modu araç çubuğu geldi')
    B.chat('/ymod'); await bekle(1500)

    // rapor
    n0 = mesajlar.Yetkili1.length
    A.chat('/rapor Yetkili1 deneme raporu'); await bekle(1500)
    kontrol(geldi('Yetkili1', /RAPOR/, n0), 'rapor yetkiliye ulaştı')

    // medya etkinliği: matematik
    konsol('lp creategroup medya'); await bekle(800)
    konsol('lp user Yetkili1 parent add medya'); await bekle(1500)
    n0 = mesajlar.Deneme1.length
    B.chat('/metkinlik matematik'); await bekle(2500)
    const soru = mesajlar.Deneme1.slice(n0).find(m => /= \?/.test(m))
    log('soru', soru)
    kontrol(!!soru, 'medya etkinliği herkese duyuruldu')
    if (soru) {
      const ifade = soru.match(/:\s*([0-9 ×÷+\-]+)=\s*\?/)[1].replace(/×/g, '*').replace(/÷/g, '/')
      const cevap = Math.trunc(Function('return (' + ifade + ')')())
      n0 = mesajlar.Deneme1.length
      A.chat(String(cevap)); await bekle(2000)
      kontrol(geldi('Deneme1', /30 dakika oynamış/, n0), 'doğru cevap tanındı (yeni hesap ödül alamaz)')
      B.chat('/metkinlik bitir'); await bekle(1000)
    }

    // yasak: atılır ve tekrar giremez
    n0 = mesajlar.Deneme1.length
    konsol('yasakla Deneme1 1g Test yasagi'); await bekle(2500)
    kontrol(geldi('Deneme1', /ATILDI.*(YASAKLANDIN|yasak)/i, n0), 'yasaklanan oyuncu atıldı')
    try { await baglan('Deneme1'); kontrol(false, 'yasaklı oyuncu tekrar giremedi') } catch (e) { kontrol(true, 'yasaklı oyuncu tekrar giremedi') }
  } catch (e) {
    log('TEST HATASI', e.stack || e.message)
    kontrol(false, 'test akışı tamamlandı: ' + e.message)
  }
  log('SONUÇ', sonuc.filter(x => x[0]).length + '/' + sonuc.length)
  try { A && A.quit() } catch {}
  try { B && B.quit() } catch {}
  await bekle(1000)
  process.exit(0)
})()
