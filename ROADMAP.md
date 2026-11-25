# SuperTube Cobalt — Roadmap

Güncelleme: 25 Kasım 2026  
Hedef: IŞINNET Hub ile yönetilen, kalıcı imzalı Android TV uygulaması.

Bu belge yaşayan görev listesidir. Kod tamamlandı, derleme tamamlandı, fiziksel kabul geçti ve production yayınlandı durumları birbirinden ayrılır. Kanıt olmadan PASS yazılmaz.

| Faz | Görev | Durum / kabul şartı |
| --- | --- | --- |
| 1 | Cobalt tabanlı SuperTube, paket kimliği net.isinnet.supertube ve markalama | Kaynak kod hazır; üretim APK doğrulaması ayrı kapıdır. |
| 2 | IŞINNET Hub OAuth Device Code, Agent SSO, güvenli token saklama, bootstrap ve abonelik kontrolü | Kaynak entegrasyonu hazır; fiziksel kabul bekliyor. Eski apph protokolü kullanılmaz. |
| 3 | Android ARM GOLD native derleme | Devam ediyor. Mevcut out/android-arm_gold korunur; native link ve Ninja çıkış kodu doğrulanır. |
| 4 | GOLD APK paketleme ve production imzalama | Bekliyor. Kalıcı onaylı sertifika, debuggable=false, paket kimliği, build/version, ABI ve SHA-256 doğrulanır. |
| 5 | Hub kataloğu, release, güvenilir signer, private R2 ve Agent ataması | Bekliyor. Yalnız doğrulanmış GOLD APK; önce test cihazına sınırlı atama. |
| 6 | Mi Box fiziksel kabul | Bekliyor. Kurulum/güncelleme, Agent SSO, aktif/sona ermiş/duraklatılmış/iptal edilmiş abonelik, yanlış imza ve yetkisiz cihaz kontrolleri. |
| 7 | Performans ve kararlılık | Bekliyor. Açılış, oynatma, kumanda, uzun kullanım, bellek, logcat, arka plan/geri dönüş. Mevcut davranış korunur. |
| 8 | Artımlı derleme ve sürüm otomasyonu | Koruma kuralları bu belgede ve AGENTS.md'de zorunlu. Ölçülen artımlı derleme, tekrar kullanılabilir build scripti, release manifesti ve güvenli geri dönüş testi bekliyor. |

## Güncel aday

- SuperTube 2.0.3 / build 203.
- Debug aday APK üretim için uygun değildir; yeniden imzalayıp GOLD diye yayınlama.
- 25 Kasım 2026 16:19 TRT son doğrulanmış native ilerleme: 27.691 / 36.155 Ninja adımı, süreç çalışıyor, stderr boş. Bu adım sayısı toplam APK tamamlanma yüzdesi değildir.
- GOLD APK tamamlanmış ve imzası doğrulanmış değildir. Derleme/son imzalama ayrıca doğrulanır.
- Hub 6694433 ve Agent 0.2.3/build 6 canlı/test cihazında hazır; gerçek çoklu uygulama kabulü bekliyor.

## Kalıcı derleme düzeni

- WSL dağıtımı: CobaltBuild.
- Kanonik kaynak: /home/cobalt/work/cobalt/src.
- Tek Android ARM GOLD çıktı dizini: /home/cobalt/work/cobalt/src/out/android-arm_gold.
- Aynı kaynak, aynı GOLD konfigürasyonu, aynı Ninja grafiği ve aynı çıktı dizini kullanılır. Mevcut nesneler, üretilmiş dosyalar, .ninja_log, .ninja_deps ve bağımlılık/SDK önbellekleri korunur.
- Bir derleme çalışırken ikinci Ninja, GN yeniden üretimi, kaynak checkout/reset, temizlik veya toolchain güncellemesi başlatılmaz.
- Değişiklikten önce Git durumu, çalışan süreçler, disk ve mevcut argümanlar incelenir. Derleme hatası önce logdan teşhis edilir. Gerekirse sadece ilgili hedef düzeltilir ve aynı dizinden devam edilir.
- Normal güncelleme: aynı GOLD dizininde Ninja incremental build. GN girdileri gerçekten değişirse, mevcut dizinde kontrollü yeniden üretim ve dry-run ile etki ölçülür. Yalnız gerekliyse değişen girdiler yeniden derlenir; eski veya hatalı nesneler zorla kullanılmaz.
- Derleme önbelleği silinmez; clean build ancak kanıtlanmış zorunluluk, etki analizi ve açık kullanıcı onayıyla yapılır.
- Bir sonraki release öncesi çalışan derlemenin tamamlandığı, kaynak commit, GN argümanları ve onaylı signer yapılandırması doğrulanır. İmzalama anahtarları/parolalar Git'e veya loga yazılmaz.

## Yayın ve devir

Her faz için commit/PR, test sonuçları, kullanılan kaynak SHA, build config, APK boyutu/hash, signer fingerprint, cihaz ve açık engeller kaydedilir. Çalışan ürün üzerinde gereksiz değişiklik yapılmaz. Main merge, production katalog ve R2 yayınları mevcut onay sürecine tabidir. Her tamamlanan kaynak değişikliği commit ve push ile saklanır.
