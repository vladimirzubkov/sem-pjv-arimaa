# Changelog

## 0.2.1

- JavaFX rozhraní pro **rozestavení**: deska 8×8, rezerva s výběrem typu figury, umístění klikem na domovské pole, vrácení figury z desky do rezervy bez figury v ruce.
- Tlačítka: náhodné doplnění zbytku (`placeRemainingPiecesRandomly`), šachová rozestavení (`applyChessMappedSetup`), dokončení rozestavení (`tryCompleteSetup`), zrušení výběru z ruky, nová hra.
- `JavafxApp` spouští hru a propojí `Game` → `GameController` → `MainController`; drobná úprava popisu v `ArimaaApp`.
- Model: `Game.getSetupHand()` pro zobrazení figury čekající na umístění.

## 0.1.1

- Dokončení rozestavení: `tryCompleteSetup(PlayerSide)` — kontrola prázdné rezervy, přesně 16 vlastních figurek na domácím území, oficiální multiset a obsazení; přechody `SETUP_GOLD` → `SETUP_SILVER` → `PLAY` včetně nastavení `sideToMove`.
- `placeRemainingPiecesRandomly(PlayerSide)` — náhodné doplnění zbývajících figurek z rezervy na volná pole domova (lze kdykoli během rozestavení).
- `applyChessMappedSetup(PlayerSide)` — pevné „šachové“ rozložení (zadní řada podle mapování typů, přední řada osmi králíků; Gold řady 0–1, Silver 6–7).
- Rozšířené testy v `GameTest` (náhodné doplnění, šachová rozestavení, přechody a negativní případy).

## 0.0.1

- Základní doménový model: `Board`, `Position`, `Piece` (včetně konstruktoru pro typ a stranu).
- `BoardConstants` (geometrie desky včetně pastí), `HomeTerritory` (domovské řady Gold / Silver, volitelné zrcadlení ranků).
- Přípravná fáze v `Game`: `startNewGame()`, rezervy 16 figurek dle oficiálního multisetu, ruční výběr z rezervy, umístění na domovské pole, zrušení výběru, vrácení figury z desky do rezervy; oprava `setPiece(..., null)` na desce (odvázání pozice u figury).
- Skripty `0_compile.bat`–`3_start.bat` pro Maven v adresáři modulu.
- JUnit 5: `BoardTest`, `GameTest` (`mvn test`).
