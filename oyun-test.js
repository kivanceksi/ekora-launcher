// Alıştırma botu testi: tek gerçek oyuncu (Deneyen) saklambaç odasına girer; eksik 3 kişiyi botlar tamamlar ve oynar.
// Ardından sumo: 1 gerçek oyuncu + 1 bot. Bütün sohbet ve başlıklar oyun.log'a yazılır.
const mineflayer = require('mineflayer')
const fs = require('fs')
const [FIFO, OUT] = process.argv.slice(2)
const log = (...a) => { const s = new Date().toISOString().slice(11, 19) + ' ' + a.join(' '); console.log(s); fs.appendFileSync(OUT, s + '\n') }
const konsol = c => { fs.writeFileSync(FIFO, c + '\n', { flag: 'a' }); log('KONSOL>', c) }
const bekle = ms => new Promise(r => setTimeout(r, ms))
const konum = b => b && b.entity ? `${b.entity.position.x.toFixed(1)},${b.entity.position.y.toFixed(1)},${b.entity.position.z.toFixed(1)}` : '?'

function tikSonu (bot) {
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

const mesajlar = []
const geldi = (re, sonra = 0) => mesajlar.slice(sonra).some(m => re.test(m))
const bekleMesaj = async (re, ms, sonra = 0) => { const t = Date.now(); while (Date.now() - t < ms) { if (geldi(re, sonra)) return true; await bekle(500) } return false }

;(async () => {
  let A
  try {
    A = await new Promise((res, rej) => {
      const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Deneyen', version: '26.1', auth: 'offline' })
      tikSonu(bot)
      bot.on('message', m => { const s = m.toString(); mesajlar.push(s); log('MESAJ', s.replace(/\n/g, ' ⏎ ')) })
      bot.on('title', (t, tur) => log('BAŞLIK', tur, String(t).slice(0, 160)))
      bot.on('kicked', r => log('ATILDI', typeof r === 'string' ? r : JSON.stringify(r)))
      bot.on('error', e => log('HATA', e.message))
      bot.once('spawn', () => res(bot))
      bot.once('end', r => rej(new Error('bağlantı kapandı: ' + r)))
      setTimeout(() => rej(new Error('bağlanamadı')), 90000)
    })
    A.on('playerJoined', p => log('SEKMEYE GİRDİ', p.username))
    A.on('playerLeft', p => log('SEKMEDEN ÇIKTI', p.username))
    await bekle(4000)
    A.chat('/register Sifre1234 Sifre1234')
    await bekle(5000)
    A.chat('/login Sifre1234')
    await bekle(4000)
    log('giriş sonrası konum', konum(A))

    // ---------------- saklambaç
    let bas = mesajlar.length
    A.chat('/saklambac')
    await bekle(3000)
    log('saklambaç odası konumu', konum(A))
    const izle = setInterval(() => {
      const yakin = Object.values(A.entities).filter(e => e.type === 'player' && e !== A.entity).map(e => `${e.username}@${e.position.x.toFixed(1)},${e.position.y.toFixed(1)},${e.position.z.toFixed(1)}`)
      log('GÖZLEM ben', konum(A), 'oyun modu', A.game && A.game.gameMode, '| görünen oyuncular:', yakin.join(' ') || '-')
    }, 10000)
    const bitti = await bekleMesaj(/kazand|BİTTİ|bitti|Oyun sona|süre doldu|Süre doldu|Kazanan/i, 300000, bas)
    log('SAKLAMBAÇ bitti mi:', bitti)
    await bekle(12000)

    // ---------------- sumo
    bas = mesajlar.length
    A.chat('/hub')
    await bekle(3000)
    A.chat('/sumo')
    await bekle(3000)
    log('sumo odası konumu', konum(A))
    // gerçek oyuncu sumoda kıpırdamadan durur; bot onu itip düşürmeli
    const sumoBitti = await bekleMesaj(/kazand|Kazanan|bitti|düştü|elendi/i, 150000, bas)
    log('SUMO bitti mi:', sumoBitti)
    await bekle(8000)
    clearInterval(izle)
  } catch (e) {
    log('TEST HATASI', e.stack || e.message)
  }
  try { A && A.quit() } catch {}
  await bekle(2000)
  process.exit(0)
})()
