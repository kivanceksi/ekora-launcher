#!/bin/bash
# EkoraMC sunucu eklentileri duman testi: gerçek Paper 26.3 + LuckPerms (+ AuthMe varsa) ile açılış, konsol komutları,
# Paper koruma ayarlarının uygulanması ve İKİNCİ açılışta Paper'ın bu ayarları hatasız yüklemesi.
set -uo pipefail
cd "$(dirname "$0")"
OUT=$PWD/cikti; mkdir -p $OUT
S=$PWD/sunucu; rm -rf $S; mkdir -p $S/plugins
UA="EkoraMC-test/1.0 (github actions)"

# ---- Paper 26.3 (Fill v3 API, yoksa v2)
URL=$(curl -fsSL -A "$UA" "https://fill.papermc.io/v3/projects/paper/versions/26.3/builds/latest" | jq -r '.downloads["server:default"].url' 2>/dev/null)
if [ -z "$URL" ] || [ "$URL" = "null" ]; then
  B=$(curl -fsSL -A "$UA" "https://api.papermc.io/v2/projects/paper/versions/26.3/builds" | jq -r '.builds[-1].build')
  URL="https://api.papermc.io/v2/projects/paper/versions/26.3/builds/$B/downloads/paper-26.3-$B.jar"
fi
echo "Paper: $URL" | tee $OUT/surumler.txt
curl -fsSL -A "$UA" -o $S/paper.jar "$URL" || { echo "Paper indirilemedi"; exit 1; }

# ---- LuckPerms ve AuthMe (Modrinth)
modrinth() { # proje yükleyici
  curl -fsSL -A "$UA" "https://api.modrinth.com/v2/project/$1/version?loaders=%5B%22$2%22%5D" | jq -r '.[0].files[0].url'
}
LP=$(modrinth luckperms bukkit); echo "LuckPerms: $LP" | tee -a $OUT/surumler.txt
[ -n "$LP" ] && [ "$LP" != "null" ] && curl -fsSL -A "$UA" -o $S/plugins/LuckPerms.jar "$LP"
AM=""
for slug in authmereloaded authme authme-reloaded; do
  for ld in paper spigot bukkit; do
    [ -n "$AM" ] && [ "$AM" != "null" ] && break
    AM=$(curl -fsSL -A "$UA" "https://api.modrinth.com/v2/project/$slug/version?loaders=%5B%22$ld%22%5D" 2>/dev/null | jq -r '.[0].files[0].url' 2>/dev/null)
  done
done
if [ -z "$AM" ] || [ "$AM" = "null" ]; then
  curl -sSL -A "$UA" "https://api.github.com/repos/AuthMe/AuthMeReloaded/releases?per_page=5" > /tmp/am.json
  jq -r '.[].assets[].name' /tmp/am.json > $OUT/authme-dosyalari.txt 2>&1 || head -c 400 /tmp/am.json > $OUT/authme-dosyalari.txt
  AM=$(jq -r '[.[].assets[] | select(.name|test("(?i)\\.jar$")) | select(.name|test("(?i)(bungee|velocity|legacy|sources|javadoc)")|not)][0].browser_download_url' /tmp/am.json)
fi
echo "AuthMe: $AM" | tee -a $OUT/surumler.txt
[ -n "$AM" ] && [ "$AM" != "null" ] && curl -fsSL -A "$UA" -L -o $S/plugins/AuthMe.jar "$AM"
for v in viaversion viabackwards; do
  U=$(modrinth $v paper); echo "$v: $U" | tee -a $OUT/surumler.txt
  [ -n "$U" ] && [ "$U" != "null" ] && curl -fsSL -A "$UA" -o $S/plugins/$v.jar "$U"
done
cp eklentiler/*.jar $S/plugins/

cd $S
echo "eula=true" > eula.txt
printf "online-mode=false\nwhite-list=false\nenforce-whitelist=false\nmotd=EkoraTest\nspawn-protection=0\nview-distance=4\nsimulation-distance=4\nlevel-seed=42\n" > server.properties

calistir() { # $1 = tur adı, $2 = komut dosyası
  local LOG=$OUT/$1.log
  rm -f giris; mkfifo giris
  (java -Xms1G -Xmx2G -jar paper.jar --nogui < giris > $LOG 2>&1; echo "ÇIKIŞ KODU $?" >> $LOG) &
  local PID=$!
  exec 3>giris
  for i in $(seq 1 360); do grep -q "Done (" $LOG && break; grep -q "ÇIKIŞ KODU" $LOG && break; sleep 1; done
  if ! grep -q "Done (" $LOG; then echo "SUNUCU AÇILMADI ($1)"; exec 3>&-; wait $PID; return 1; fi
  sleep 8
  while IFS= read -r c; do
    [ -z "$c" ] && continue
    if [ "$c" = "bot" ]; then timeout 300 node ../bot-test.js "$PWD/giris" "$OUT/bot.log" > $OUT/bot-stdout.log 2>&1; continue; fi
    if [[ "$c" == bekle* ]]; then sleep "${c#bekle }"; continue; fi
    echo ">>> $c" >> $LOG
    echo "$c" >&3
    sleep 2
  done < "$2"
  echo "stop" >&3
  exec 3>&-
  for i in $(seq 1 120); do kill -0 $PID 2>/dev/null || break; sleep 1; done
  kill -9 $PID 2>/dev/null
  return 0
}

cat > /tmp/tur1.txt <<'EOF'
plugins
lp creategroup rehber
lp group rehber setweight 60
lp creategroup medya
lp group medya meta setsuffix 50 " &fM"
ekorayetki
ekorayetki site
yetkililer
raporlar
kalkan
kalkan paper
kalkan lag
kalkan temizlik
kalkan paper uygula
kalkan acil
kalkan acil
yasakla YokBoyleBiri 1g deneme
gecmis YokBoyleBiri
bekle 3
EOF
calistir tur1 /tmp/tur1.txt || true
cp plugins/AuthMe/config.yml $OUT/authme-config.yml 2>/dev/null
cp config/paper-world-defaults.yml $OUT/ 2>/dev/null; cp config/paper-global.yml $OUT/ 2>/dev/null
find . -name "paper-world.yml" -path "*nether*" -exec cp {} $OUT/paper-world-nether.yml \; 2>/dev/null
find . -name "paper-world.yml" -path "*end*" -exec cp {} $OUT/paper-world-end.yml \; 2>/dev/null
ls config > $OUT/config-dosyalari.txt

cat > /tmp/tur2.txt <<'EOF'
kalkan paper
ekorayetki
bekle 3
EOF
calistir tur2 /tmp/tur2.txt || true
cp plugins/EkoraYetki/config.yml $OUT/ekorayetki-config.yml 2>/dev/null
cp plugins/EkoraKalkan/config.yml $OUT/ekorakalkan-config.yml 2>/dev/null

# ---- tur 3: iki botla oyuncu akışı (AuthMe ayarı OKU_BENI'deki gibi; test için IP başına kayıt sınırı gevşek)
if [ -f plugins/AuthMe/config.yml ]; then
  sed -i -E 's/^(\s*)noTeleport: false/\1noTeleport: true/; s/^(\s*)teleportUnAuthedToSpawn: true/\1teleportUnAuthedToSpawn: false/; s/^(\s*)maxRegPerIp: [0-9]+/\1maxRegPerIp: 10/; s/^(\s*)maxLoginPerIp: [0-9]+/\1maxLoginPerIp: 0/; s/^(\s*)maxJoinPerIp: [0-9]+/\1maxJoinPerIp: 0/; s/^(\s*)timeout: 30$/\1timeout: 0/' plugins/AuthMe/config.yml
  python3 - <<'PY'
import re
p='plugins/AuthMe/config.yml'; s=open(p).read()
s=re.sub(r'(loginTimeout|registerTimeout): \d+', r'\1: 0', s)
s=re.sub(r'(preJoin:\n(?:\s*#.*\n)*\s*enable:) true', r'\1 false', s)
s=re.sub(r'(postJoin:\n(?:\s*#.*\n)*\s*enable:) true', r'\1 false', s)
open(p,'w').write(s)
PY
  grep -nE -A0 "noTeleport|teleportUnAuthedToSpawn|maxRegPerIp|allowMovement|Timeout|enable:" plugins/AuthMe/config.yml > $OUT/authme-ayar.txt
fi
( cd .. && npm install --silent --no-audit --no-fund mineflayer@4.39.0 minecraft-data > $OUT/npm.log 2>&1 )
printf "whitelist off\nbekle 1\n" > /tmp/tur3.txt
echo "bot" >> /tmp/tur3.txt
echo "bekle 3" >> /tmp/tur3.txt
calistir tur3 /tmp/tur3.txt || true
ls -R plugins/EkoraYetki > $OUT/ekorayetki-dosyalar.txt 2>/dev/null

# ---- özet: hata ve istisnalar
cd $OUT
{
  for t in tur1 tur2 tur3; do
    echo "===== $t"
    grep -E "Done \(|EkoraYetki|EkoraKalkan|EkoraHub|AuthMe|Yetkili Bölgesi|Giriş adası|Giriş alanı|Paper koruma|KALKAN|EKORA|✔|ERROR|Exception|Caused by|SEVERE|Could not|Failed|ÇIKIŞ KODU" $t.log | grep -v "^\s*at " | head -150
  done
  echo "===== bot"
  cat bot.log 2>/dev/null | grep -vE "MESAJ" | head -120
} > ozet.txt
cat ozet.txt
