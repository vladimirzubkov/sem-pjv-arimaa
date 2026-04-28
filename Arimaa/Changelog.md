# Changelog

## 0.0.1

- Základní doménový model: `Board`, `Position`, `Piece` (včetně konstruktoru pro typ a stranu).
- `BoardConstants` (geometrie desky včetně pastí), `HomeTerritory` (domovské řady Gold / Silver, volitelné zrcadlení ranků).
- Přípravná fáze v `Game`: `startNewGame()`, rezervy 16 figurek dle oficiálního multisetu, ruční výběr z rezervy, umístění na domovské pole, zrušení výběru, vrácení figury z desky do rezervy; oprava `setPiece(..., null)` na desce (odvázání pozice u figury).
- Skripty `0_compile.bat`–`3_start.bat` pro Maven v adresáři modulu.
