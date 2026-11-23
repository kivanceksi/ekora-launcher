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
AM=$(curl -fsSL -A "$UA" "https://api.github.com/repos/AuthMe/AuthMeReloaded/releases/latest" | jq -r '[.assets[] | select(.name|test("(?i)spigot|bukkit|authme.*jar"))][0].browser_download_url')
echo "AuthMe: $AM" | tee -a $OUT/surumler.txt
[ -n "$AM" ] && [ "$AM" != "null" ] && curl -fsSL -A "$UA" -L -o $S/plugins/AuthMe.jar "$AM"
cp eklentiler/*.jar $S/plugins/

cd $S
echo "eula=true" > eula.txt
printf "online-mode=false\nmotd=EkoraTest\nspawn-protection=0\nview-distance=4\nsimulation-distance=4\nlevel-seed=42\n" > server.properties

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
ls -R plugins/EkoraYetki > $OUT/ekorayetki-dosyalar.txt 2>/dev/null

# ---- özet: hata ve istisnalar
cd $OUT
{
  for t in tur1 tur2; do
    echo "===== $t"
    grep -E "Done \(|EkoraYetki|EkoraKalkan|Yetkili Bölgesi|Giriş adası|Giriş alanı|Paper koruma|ERROR|Exception|Caused by|SEVERE|Could not|Failed|ÇIKIŞ KODU" $t.log | grep -v "^\s*at " | head -150
  done
} > ozet.txt
cat ozet.txt
