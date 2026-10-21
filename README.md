# Appium + Kotlin

Małe testy Androida w Page Object Model. Emulator: Pixel 7, udid `emulator-5554`.

Adres Appium, udid i timeouty są w `src/test/kotlin/config/AndroidConfig.kt`.

## Emulator

Sprawdź urządzenie:

```
D:\Android\Sdk\platform-tools\adb.exe devices
```

Ma być `emulator-5554    device`. Jak jest `offline`:

```
D:\Android\Sdk\platform-tools\adb.exe kill-server
D:\Android\Sdk\platform-tools\adb.exe start-server
D:\Android\Sdk\platform-tools\adb.exe devices
```

Jak proces emulatora nie żyje:

```
Start-Process D:\Android\Sdk\emulator\emulator.exe -ArgumentList '-avd','Pixel_7_API_36','-gpu','auto','-netdelay','none','-netspeed','full','-no-metrics'
```

Poczekaj, aż ta komenda zwróci `1`:

```
D:\Android\Sdk\platform-tools\adb.exe shell getprop sys.boot_completed
```

## Appium

Serwer ma działać na `http://127.0.0.1:4723` (Appium 3, base path `/`).

## Testy w IntelliJ

Otwórz ten katalog jako projekt Gradle.

Jeden emulator obsługuje jedną sesję Appium. Uruchom jedną klasę testów, poczekaj aż się skończy, i dopiero wtedy odpal następną. Nie klikaj Run przy Settings i Chrome naraz i nie wciskaj „Run all tests”, gdy poprzednia sesja jeszcze startuje — druga sesja na `emulator-5554` zabija instrumentację UiAutomator2, a ADB zgłasza potem `device offline`.

- Jedna klasa Ustawień: plik `src/test/kotlin/tests/SettingsTest.kt`.
- Jedna klasa Chrome: plik `src/test/kotlin/tests/ChromeTest.kt`, osobno, po zakończeniu Ustawień.

## Testy z konsoli

W katalogu projektu:

```
.\gradlew.bat test
```

Jeden test:

```
.\gradlew.bat test --tests tests.SettingsTest.settingsShowsPackageAndTitle
.\gradlew.bat test --tests tests.ChromeTest.addressBarShowsTypedQuery
```

Gradle trzyma `maxParallelForks = 1`, a JUnit ma `junit.jupiter.execution.parallel.enabled=false` (także w `src/test/resources/junit-platform.properties`). Testy idą po kolei, jedna sesja na emulator.

Gdy test padnie, zrzut emulatora i krótki opis (klasa, metoda, wyjątek, początek stosu) lądują w `build/failures/<klasa>/` jako para `.png` i `.txt`. Katalog `build/` jest w `.gitignore`.

## CI

Workflow jest w `.github/workflows/android-appium.yml`. Nie uruchamiasz go z laptopa ani z IntelliJ. GitHub sam go startuje, gdy wypchniesz commity (`git push`) do repozytorium.

W tym jednym zadaniu dzieje się po kolei:

1. Runner `ubuntu-latest` pobiera kod.
2. Instaluje Javę 17 i Node.
3. Instaluje Appium 3 oraz sterownik UiAutomator2.
4. Reguła udev (i `chmod` na istniejącym węźle) daje runnerowi zapis do `/dev/kvm`, żeby emulator użył akceleracji hosta zamiast TCG.
5. Akcja emulatora dociąga Android SDK command line tools i jeden obraz API 36 `google_apis` x86_64, tworzy profil Pixel 7 z 2048 MB RAM i partycją userdata 2048 MB (domyślne ~7,3 GB nie mieści się na dysku runnera) i włącza jeden emulator. Akcja ma `disable-linux-hw-accel: false`, więc nie dopisuje `-accel off`. Czeka na `sys.boot_completed=1` do 20 minut. Skrypt (Appium i Gradle) startuje dopiero po tym czekaniu i jeszcze raz sprawdza boot, do 12 minut.
6. Dopiero po `boot_completed=1` Appium startuje w tle na porcie 4723.
7. `./gradlew test --tests tests.SettingsTest` odpala tylko testy Ustawień. Job nie zakłada, że Chrome jest w obrazie.

Jedno zadanie = jeden emulator = jedna sesja Appium. Ta sama zasada co przy lokalnym `emulator-5554`.
